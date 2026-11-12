package br.com.calculacorrida

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.provider.Settings
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.view.Display
import java.util.concurrent.Executors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class OfferAccessibilityService : AccessibilityService() {
    // Uma etiqueta independente por aplicativo (Uber, 99 e inDrive).
    private val overlays = linkedMapOf<String, OfferTagView>()
    private val overlayParams = linkedMapOf<String, WindowManager.LayoutParams>()
    private val draggingTags = mutableSetOf<String>()
    private data class DisplayedOffer(val result: TripResult, val offer: OfferTextParser.ParsedOffer)
    private val displayedOffers = mutableMapOf<String,DisplayedOffer>()
    private data class MissingOffer(var since:Long,var samples:Int)
    private val missingOffers = mutableMapOf<String,MissingOffer>()
    private data class PendingOffer(val signature: String, val offer: OfferTextParser.ParsedOffer, var hits: Int, var lastSeen: Long)
    private val pendingOffers = mutableMapOf<String, PendingOffer>()
    private val acceptedSignatures = mutableMapOf<String, String>()
    // Evita atualizar uma oferta depois que o motorista toca em Aceitar.
    private val acceptedOfferSignatures = mutableMapOf<String, String>()
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleOwner: String? = null
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    // Android can deliver a pending screenshot callback after the service has
    // been destroyed. Still run that callback so it can release the buffer.
    private val screenshotCallbackExecutor = java.util.concurrent.Executor { task ->
        try { screenshotExecutor.execute(task) }
        catch (_: java.util.concurrent.RejectedExecutionException) { task.run() }
    }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    @Volatile private var screenshotInProgress = false
    @Volatile private var lastScreenshotAt = 0L
    private val readerHandler = Handler(Looper.getMainLooper())
    private data class DriverFrame(val id:Int,val bounds:Rect,val text:String,val fareBounds:Rect?,val score:Long)
    private val windowExecutor=Executors.newSingleThreadExecutor()
    private val windowRefreshPending=java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var driverFrames:Map<String,DriverFrame> = emptyMap()
    @Volatile private var driverCandidates:Map<String,List<DriverFrame>> = emptyMap()
    // Keep the window that actually produced each app's offer for card placement.
    private val detectedWindows=java.util.concurrent.ConcurrentHashMap<String,Int>()
    @Volatile private var foregroundSnapshot:String?=null
    @Volatile private var applicationSnapshots:Set<String> = emptySet()
    @Volatile private var snapshotAt=0L
    @Volatile private var foregroundPackage: String? = null
    // Treat a visible Nubank window as private even when a driver overlay has focus.
    @Volatile private var sensitiveWindowVisible = false
    @Volatile private var stopped = false
    private val appearanceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "voice_enabled") {
            readerHandler.post { if(!getSharedPreferences("settings",MODE_PRIVATE).getBoolean("voice_enabled",false))voice.shutdown() }
        } else if (key != null && (key.startsWith("label_") || key == "show_profit" || key == "show_time" || key == "show_rating" || key == "cost_per_km")) {
            readerHandler.post {
                displayedOffers.forEach { (pkg,shown) ->
                    overlays[pkg]?.takeIf{it.visibility==View.VISIBLE}?.let { renderTag(it,shown.result,shown.offer,pkg) }
                }
            }
        } else if (key != null && (key.startsWith("card_") || key == "overlay_scale" || key == "reader_paused")) {
            readerHandler.post {
                hideAllOverlays()
                if(key.startsWith("card_position_") || key == "card_reset_positions") {
                    val manager=getSystemService(WINDOW_SERVICE) as WindowManager
                    overlays.values.forEach { try { manager.removeView(it) } catch (_:Exception) {} }
                    overlays.clear();overlayParams.clear();draggingTags.clear()
                }
            }
        }
    }
    private val lastWindowSeen=mutableMapOf<String,Long>()
    private val lastOfferSeen = mutableMapOf<String, Long>()
    private val poll = object : Runnable {
        override fun run() {
            if (stopped) return
            try {
            val prefs=getSharedPreferences("settings", MODE_PRIVATE)
            if(android.os.SystemClock.elapsedRealtime()-lastHeartbeat>2000L) {
                lastHeartbeat=android.os.SystemClock.elapsedRealtime()
                prefs.edit().putLong("reader_heartbeat",System.currentTimeMillis()).apply()
            }
            requestWindowRefresh()
            updateForeground(foregroundSnapshot)
            if (readingEnabled()) visibleDriverPackages().forEach { readOffer(it) }
            overlays.keys.toList().forEach { reflowOverlay(it) }
            val now = android.os.SystemClock.elapsedRealtime()
            // A timeout is only a fallback when Android no longer supplies any
            // usable observation. Positive evidence keeps the offer alive.
            lastOfferSeen.filter { (pkg, seen) -> now - seen > 60_000L && pkg !in visibleDriverPackages() }.keys.toList().forEach(::forgetOffer)
            } catch (_: Exception) { readerStatus("Falha temporária da janela; tentando novamente") }
            finally { if(!stopped) readerHandler.postDelayed(this, 350L) }
        }
    }

    private val voice by lazy { OfferVoice(this) { pkg -> readingEnabled() && displayedOffers.containsKey(pkg) } }

    private var lastHeartbeat=0L
    private fun readingEnabled(): Boolean = NetworkGate.isOnline(this) && !sensitiveWindowVisible && foregroundPackage != "com.nu.production" && readerConfigured() && !getSharedPreferences("settings",MODE_PRIVATE).getBoolean("reader_paused",false)
    private fun readerConfigured(): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("reader_disclosure_accepted", false) &&
            (prefs.getBoolean("copilot_enabled", false) || prefs.getBoolean("traffic_enabled", false))
    }

    private fun readerStatus(message: String) {
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("reader_status", message).putLong("reader_seen_at", System.currentTimeMillis()).apply()
    }

    // Diagnostic state contains only status and image dimensions, never OCR text.
    private fun captureStatus(pkg:String,message:String) {
        val app=driverAppName(pkg) ?: return
        getSharedPreferences("settings",MODE_PRIVATE).edit().putString("reader_capture_status",message)
            .putString("reader_capture_$app",message).apply()
    }
    private fun recognitionStatus(pkg:String,message:String) {
        val app=driverAppName(pkg) ?: return
        getSharedPreferences("settings",MODE_PRIVATE).edit().putString("reader_ocr_status",message)
            .putString("reader_ocr_$app",message).apply()
    }

    private fun removeBubble() {
        bubble?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        bubble = null
        bubbleOwner = null
    }

    private fun updateForeground(pkg: String?) {
        foregroundPackage = pkg
        val visible = visibleDriverPackages()
        val now=android.os.SystemClock.elapsedRealtime()
        // A stale snapshot means Android is still querying windows; it is not
        // evidence that an offer disappeared during a resize or a slow scan.
        if(now-snapshotAt<=2000L) {
            visible.forEach{lastWindowSeen[it]=now}
            overlays.keys.filter { it !in visible && now-(lastWindowSeen[it]?:0)>2000L }.forEach(::forgetOffer)
        }
        if (!NetworkGate.isOnline(this)) {
            hideAllOverlays()
            removeBubble()
            readerStatus("Sem internet; leitura e cálculos pausados")
            return
        }
        if (!readerConfigured() || sensitiveWindowVisible || pkg == "com.nu.production") {
            hideAllOverlays()
            removeBubble()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            readerStatus("Falta permitir sobreposição de tela")
            return
        }
        bubble?.apply {
            val label=if(readingEnabled())"CC" else "Ⅱ"
            val description=if(readingEnabled())"Copiloto CC ativo em segundo plano" else "Copiloto CC pausado"
            if(text.toString()!=label)text=label
            if(contentDescription?.toString()!=description)contentDescription=description
        }
        if (bubbleOwner !in visible) bubbleOwner = visible.firstOrNull()
        if (bubble != null) {
            bubbleParams?.let { params ->
                bubbleOwner?.let { target ->
                    val pane = driverWindow(target)?.second
                    if (pane != null) {
                        if(pane.width()>=dp(160) && pane.height()>=dp(100)) {
                        val oldX=params.x;val oldY=params.y
                        params.x=params.x.coerceIn(pane.left,(pane.right-dp(48)).coerceAtLeast(pane.left))
                        params.y=params.y.coerceIn(pane.top,(pane.bottom-dp(48)).coerceAtLeast(pane.top))
                        if(oldX!=params.x || oldY!=params.y)try { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(bubble,params) } catch (_: Exception) {}
                        }
                    }
                }
            }
            return
        }
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(dp(48), dp(48), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = prefs.getInt("bubble_x", dp(8)).coerceIn(0, (resources.displayMetrics.widthPixels - dp(48)).coerceAtLeast(0))
                y = prefs.getInt("bubble_y", dp(170)).coerceIn(0, (resources.displayMetrics.heightPixels - dp(48)).coerceAtLeast(0))
            }
        val view = TextView(this).apply {
            text = "CC"; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 218, 108)); typeface = Typeface.DEFAULT_BOLD
            contentDescription = "Copiloto CC. Arraste para mover; toque para abrir os ajustes."
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(18,18,20)); setStroke(dp(2), Color.rgb(239,190,65)) }
        }
        var startX = 0; var startY = 0; var downX = 0f; var downY = 0f; var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX=params.x; startY=params.y; downX=event.rawX; downY=event.rawY; moved=false }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(event.rawX-downX).toInt(); val dy=(event.rawY-downY).toInt()
                    if (kotlin.math.abs(dx)>dp(5) || kotlin.math.abs(dy)>dp(5)) moved=true
                    if (moved) {
                        params.x=(startX+dx).coerceIn(0,(resources.displayMetrics.widthPixels-dp(48)).coerceAtLeast(0))
                        params.y=(startY+dy).coerceIn(0,(resources.displayMetrics.heightPixels-dp(48)).coerceAtLeast(0))
                        try { wm.updateViewLayout(view,params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) prefs.edit().putInt("bubble_x",params.x).putInt("bubble_y",params.y).apply()
                    else showReaderControls()
                }
            }
            true
        }
        try { wm.addView(view,params); bubble=view; bubbleParams=params } catch (_: Exception) { readerStatus("Não foi possível mostrar a bolinha CC") }
    }

    private fun showReaderControls() {
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        val paused=prefs.getBoolean("reader_paused",false)
        val dialog=android.app.AlertDialog.Builder(this)
            .setTitle("Copiloto CC")
            .setItems(arrayOf(if(paused)"Retomar leitura" else "Pausar leitura", "Abrir JHONDRIVER", "Desligar para usar banco")) { _, which ->
                when(which) {
                    0 -> { prefs.edit().putBoolean("reader_paused",!paused).apply();hideAllOverlays();readerStatus(if(!paused)"Leitura pausada pelo usuário" else "Leitura retomada") }
                    1 -> packageManager.getLaunchIntentForPackage(packageName)?.let { it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(it) }
                    // This explicit user action turns off the Android service, not
                    // only the in-app pause. Keep the driver's feature choices.
                    2 -> { stopReading() }
                }
            }.setNegativeButton("Fechar",null).create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            readerHandler.post { updateForeground(foregroundSnapshot) }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            readerHandler.post { updateForeground(foregroundSnapshot) }
            if (validated) readerHandler.post { if(readingEnabled())visibleDriverPackages().forEach{readOffer(it)} }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
        serviceInfo=serviceInfo.apply {
            flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        getSharedPreferences("settings", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(appearanceListener)
        NetworkGate.register(this, networkCallback)
        stopped = false
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        prefs.edit().putBoolean("reader_model_ready",false).putBoolean("reader_privacy_protected",false).apply()
        screenshotExecutor.execute {
            val warm=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(warm).apply {
                drawColor(Color.WHITE)
                val paint=android.graphics.Paint().apply{color=Color.BLACK;textSize=32f}
                listOf("JHONDRIVER","R$ 10,00","1 min (1 km)","2 min (2 km)","Nota 4,90","Aceitar").forEachIndexed {i,text->drawText(text,12f,60f+i*60f,paint)}
            }
            try {
                textRecognizer.process(InputImage.fromBitmap(warm,0)).addOnCompleteListener { task ->
                    warm.recycle()
                    if(!stopped)prefs.edit().putBoolean("reader_model_ready",task.isSuccessful).apply()
                }
            } catch (_:Exception) {warm.recycle()}
        }
        readerStatus("Serviço conectado; preparando leitor automático")
        readerHandler.removeCallbacks(poll)
        readerHandler.post(poll)
    }

    companion object {
        @Volatile private var active: OfferAccessibilityService? = null
        fun stopReading(): Boolean {
            val service = active ?: return false
            service.stopped = true
            service.readerHandler.removeCallbacks(service.poll)
            // The windows and their maps belong to the main thread. Calls from
            // instrumentation or another worker must not race the polling loop.
            val cleanup = {
                service.hideAllOverlays()
                service.removeBubble()
                service.disableSelf()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) cleanup()
            else service.readerHandler.post { cleanup() }
            return true
        }
    }

    private fun driverAppName(pkg: String): String? {
        return DriverPackages.name(pkg)
    }

    private var lastEventScan=0L
    // Small 99 floating cards must be eligible, while a 48 dp app bubble is not.
    private fun isOfferSized(bounds:Rect)=bounds.width()>=dp(120) && bounds.height()>=dp(80)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event==null || stopped)return
        val now=android.os.SystemClock.elapsedRealtime()
        if(event.eventType==AccessibilityEvent.TYPE_VIEW_CLICKED && driverAppName(event.packageName?.toString().orEmpty())!=null) {
            val copy=AccessibilityEvent.obtain(event)
            windowExecutor.execute {
                try {
                    val node=copy.source
                    val accepted=try {isAcceptActionNode(node)}finally{node?.recycle()}
                    val pkg=copy.packageName?.toString()
                    if(accepted && pkg!=null)readerHandler.post {
                        acceptedSignatures[pkg]?.let {acceptedOfferSignatures[pkg]=it}
                        hideOverlay(pkg)
                    }
                } finally {copy.recycle()}
            }
        }
        if(now-lastEventScan<100)return
        lastEventScan=now
        requestWindowRefresh()
    }

    private fun requestWindowRefresh() {
        if(stopped || !readerConfigured() || !windowRefreshPending.compareAndSet(false,true))return
        windowExecutor.execute {
            val started=android.os.SystemClock.elapsedRealtime()
            try {
                val candidates=linkedMapOf<String,MutableList<DriverFrame>>()
                val applications=linkedSetOf<String>()
                var activePkg:String?=null
                val activeRoot=rootInActiveWindow
                try {
                    activePkg=activeRoot?.packageName?.toString()
                    // Inspect only package identities first; never inspect the bank's
                    // node text. A driver window can coexist with the bank in split
                    // screen or as a floating card, even when it has input focus.
                    val windowPackages = windows.mapNotNull { window ->
                        if(window.type!=android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && window.type!=android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM)return@mapNotNull null
                        val root=window.root ?: return@mapNotNull null
                        try {
                            val pkg=root.packageName?.toString() ?: return@mapNotNull null
                            if(window.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION)applications.add(pkg)
                            window to pkg
                        } finally {root.recycle()}
                    }
                    val bankVisible = activePkg == "com.nu.production" || windowPackages.any { it.second == "com.nu.production" }
                    val wasBankVisible = sensitiveWindowVisible
                    sensitiveWindowVisible = bankVisible
                    if (bankVisible != wasBankVisible) {
                        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("reader_privacy_protected", bankVisible).apply()
                        readerStatus(if (bankVisible) "Leitura pausada: Nubank visível" else "Leitura retomada após sair do Nubank")
                    }
                    if (!bankVisible) for ((window, originalPackage) in windowPackages) {
                        if(driverAppName(originalPackage)==null)continue
                        val root=window.root ?: continue
                        try {
                            // The window can change between the two passes.
                            val pkg=root.packageName?.toString() ?: continue
                            if(pkg!=originalPackage)continue
                            val bounds=Rect().also{window.getBoundsInScreen(it)}
                            if(bounds.isEmpty)continue
                            val offerOverlay=window.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM && isOfferSized(bounds)
                            val text=StringBuilder()
                            if(readingEnabled() && isOfferSized(bounds))collectText(root,text)
                            val content=text.toString()
                            val complete=IncomingOfferPolicy.isIncoming(pkg,content) && OfferTextParser.parse(content,pkg)!=null
                            val score=(if(complete)5_000_000_000L else 0L)+(if(offerOverlay)1_000_000_000L else 0L)+bounds.width().toLong()*bounds.height()
                            candidates.getOrPut(pkg){mutableListOf()}.add(DriverFrame(window.id,bounds,content,if(text.isNotEmpty())findFareBounds(root)else null,score))
                        } finally {root.recycle()}
                    }
                    val pkg=activeRoot?.packageName?.toString()
                    if(!bankVisible && activeRoot!=null && pkg!=null && driverAppName(pkg)!=null && pkg !in candidates) {
                        val bounds=Rect().also{activeRoot.getBoundsInScreen(it)}
                        if(!bounds.isEmpty) {
                            val text=StringBuilder();if(readingEnabled())collectText(activeRoot,text)
                            candidates.getOrPut(pkg){mutableListOf()}.add(DriverFrame(activeRoot.windowId,bounds,text.toString(),null,0))
                        }
                    }
                } finally {activeRoot?.recycle()}
                val snapshot=candidates.mapValues{(_,frames)->frames.sortedByDescending{it.score}}
                driverCandidates=snapshot
                driverFrames=snapshot.mapValues{(_,frames)->frames.first()}
                applicationSnapshots=applications.toSet()
                foregroundSnapshot=activePkg
                snapshotAt=android.os.SystemClock.elapsedRealtime()
                getSharedPreferences("settings",MODE_PRIVATE).edit().putString("reader_window_status","${snapshot.size} apps • ${snapshot.values.sumOf{it.size}} janelas • consulta em ${snapshotAt-started} ms").apply()
                readerHandler.post {
                    if(!stopped) {
                        val available=snapshot.flatMap{(pkg,frames)->frames.map{pkg to it.id}}.toSet()
                        lastCaptureByWindow.keys.retainAll(available)
                        detectedWindows.keys.retainAll(snapshot.keys)
                        updateForeground(foregroundSnapshot)
                        if(readingEnabled())visibleDriverPackages().forEach{readOffer(it)}
                    }
                }
            } catch (_:Exception) {readerStatus("Janela atualizando; repetindo consulta automaticamente")}
            finally {windowRefreshPending.set(false)}
        }
    }

    private fun looksLikeOffer(pkg:String,text:String):Boolean {
        val n=normalizeActionText(text)
        val hasFare=Regex("""r\s*\$\s*[0-9]""").containsMatchIn(n)
        val hasAction=Regex("""\b(aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b""").containsMatchIn(n)
        val hasRoute=Regex("""\d+\s*min(?:uto)?s?\b""").containsMatchIn(n) &&
            Regex("""\d+(?:[,.]\d+)?\s*(?:km|m)\b""").containsMatchIn(n)
        return hasFare && (hasAction || hasRoute || (driverAppName(pkg)=="99" && IncomingOfferPolicy.hasNinetyNineOfferMarker(text)))
    }

    private fun discardPreviousOfferIfFareChanged(pkg:String,windowId:Int,text:String) {
        if(detectedWindows[pkg]!=windowId || !looksLikeOffer(pkg,text))return
        val fare=OfferTextParser.fareOrNull(text,pkg) ?: return
        val previous=acceptedSignatures[pkg]?.substringBefore('|')?.toDoubleOrNull() ?: return
        if(kotlin.math.abs(fare-previous)>0.005) {
            forgetOffer(pkg)
        }
    }

    private fun rememberVisibleOffer(pkg:String,windowId:Int) {
        detectedWindows[pkg]=windowId
        lastOfferSeen[pkg]=android.os.SystemClock.elapsedRealtime()
        missingOffers.remove(pkg)
    }

    // A full OCR frame can be incomplete even while an offer is visible. Keep
    // the card on partial offer evidence, and require repeated readable frames
    // with no offer before dismissing it. Never persist the recognized text.
    private fun observeFullFrame(pkg:String,windowId:Int,text:String):Boolean {
        if(detectedWindows[pkg]!=windowId || pkg !in acceptedSignatures)return false
        val normalized=normalizeActionText(text)
        if(looksLikeOffer(pkg,text)) {
            rememberVisibleOffer(pkg,windowId)
            return false
        }
        if(normalized.count{it.isLetterOrDigit()}<12)return false
        val now=android.os.SystemClock.elapsedRealtime()
        val evidence=missingOffers.getOrPut(pkg){MissingOffer(now,0)}
        evidence.samples++
        if(evidence.samples>=2 && now-evidence.since>=1200L)forgetOffer(pkg)
        return true
    }

    private fun forgetOffer(pkg:String) {
        draggingTags.remove(pkg)
        voice.stop()
        hideOverlay(pkg)
        lastOfferSeen.remove(pkg)
        missingOffers.remove(pkg)
        acceptedSignatures.remove(pkg)
        acceptedOfferSignatures.remove(pkg)
        detectedWindows.remove(pkg)
        displayedOffers.remove(pkg)
    }

    private fun readOffer(pkg: String) {
        if(!readingEnabled())return
        val frame=driverFrames[pkg] ?: return
        val screenText=frame.text
        if(!IncomingOfferPolicy.isIncoming(pkg,screenText)) {
            val n=normalizeActionText(screenText)
            if(listOf("encerrar viagem","finalizar viagem","em viagem","cheguei").any{n.contains(it)}) {
                forgetOffer(pkg);return
            }
            requestOfferScreenshot(pkg)
            return
        }
        val offer=OfferTextParser.parse(screenText,pkg) ?: run {
            discardPreviousOfferIfFareChanged(pkg,frame.id,screenText)
            readerStatus("Conferindo os trechos da oferta antes de calcular")
            requestOfferScreenshot(pkg);return
        }
        rememberVisibleOffer(pkg,frame.id)
        val signature=listOf(
            "%.2f".format(java.util.Locale.US,offer.fare),"%.2f".format(java.util.Locale.US,offer.pickupKm),
            "%.2f".format(java.util.Locale.US,offer.tripKm),"%.1f".format(java.util.Locale.US,offer.minutes),
            offer.passengerRating.orEmpty(),offer.pickupMinutes?.toString().orEmpty(),offer.pickupAddress.orEmpty(),offer.destinationAddress.orEmpty()).joinToString("|")
        if(acceptedOfferSignatures[pkg]==signature){hideOverlay(pkg);return}
        acceptedOfferSignatures.remove(pkg)
        if(acceptedSignatures[pkg]!=signature) {
            acceptedSignatures[pkg]=signature
            calculateAndShow(offer,frame.fareBounds,pkg)
        }
    }

    // Alguns cartões da Uber/99 são desenhados como imagem e não aparecem na árvore
    // de acessibilidade. Nesses casos, fazemos OCR da tela ANTES do aceite.
    private fun requestOfferScreenshot(pkg: String) {
        if (Build.VERSION.SDK_INT < 30) { readerStatus("Neste Android a leitura depende do texto acessível da oferta"); return }
        if (screenshotInProgress || pkg !in visibleDriverPackages() || !readingEnabled()) return
        // Try eligible windows in turn. A large map/utility overlay from one
        // driver must not permanently hide a smaller offer window from OCR.
        val target=driverCandidates[pkg].orEmpty().filter{isOfferSized(it.bounds)}
            .minByOrNull{lastCaptureByWindow[pkg to it.id]?:Long.MIN_VALUE}
            ?: run { readerStatus("${driverAppName(pkg)}: aguardando janela da oferta"); return }
        val windowId = target.id
        val bounds = Rect(target.bounds)
        if(!isOfferSized(bounds)) {
            readerStatus("${driverAppName(pkg)}: aguardando cartão da oferta; apenas a bolinha está visível")
            return
        }
        if (Build.VERSION.SDK_INT < 34 && (foregroundPackage != pkg ||
            bounds.width() < resources.displayMetrics.widthPixels * 0.9 ||
            bounds.height() < resources.displayMetrics.heightPixels * 0.85 ||
            applicationSnapshots.any { it!=pkg })) {
            readerStatus("Abra o app de motorista em tela cheia para usar OCR neste Android")
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastScreenshotAt < 700L) return
        lastScreenshotAt = now
        lastCaptureByPackage[pkg] = now
        lastCaptureByWindow[pkg to windowId] = now
        getSharedPreferences("settings",MODE_PRIVATE).edit().putString("reader_last_app",driverAppName(pkg)).putLong("reader_last_capture",System.currentTimeMillis()).apply()
        screenshotInProgress = true
        val requestTime=now
        captureStatus(pkg,"Solicitando imagem da janela ${bounds.width()}×${bounds.height()}")
        readerHandler.postDelayed({
            if(screenshotInProgress && lastScreenshotAt==requestTime) {
                screenshotInProgress=false
                readerStatus("Captura demorou; repetindo automaticamente")
            }
        },12000L)
        try {
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        if(lastScreenshotAt==requestTime)captureStatus(pkg,"Imagem recebida em ${android.os.SystemClock.elapsedRealtime()-requestTime} ms")
                        var bitmap: Bitmap? = null
                        try {
                            val hardwareBuffer = result.hardwareBuffer
                            bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Exception) {
                        }
                        try { result.hardwareBuffer.close() } catch (_: Exception) {}
                        val captured = bitmap
                        if (stopped || lastScreenshotAt!=requestTime || !captureStillValid(pkg, windowId, bounds)) {
                            captured?.recycle()
                            if(lastScreenshotAt==requestTime) screenshotInProgress = false
                            return
                        }
                        if (captured == null) {
                            readerStatus("Falha ao converter a imagem da janela")
                            if(lastScreenshotAt==requestTime) screenshotInProgress = false
                            return
                        }
                        readerHandler.post { recognizeCaptured(pkg,windowId,bounds,captured,requestTime) }
                    }

                    override fun onFailure(errorCode: Int) {
                        if(lastScreenshotAt==requestTime)captureStatus(pkg,"Falha da captura: código $errorCode")
                        if(lastScreenshotAt==requestTime) screenshotInProgress = false
                        readerStatus("Captura indisponível (código $errorCode). A leitura por texto continua ativa.")
                    }
                }
            if (Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(windowId, screenshotCallbackExecutor, callback)
            else takeScreenshot(Display.DEFAULT_DISPLAY, screenshotCallbackExecutor, callback)
        } catch (_: Exception) {
            if(lastScreenshotAt==requestTime) screenshotInProgress = false
            readerStatus("Não foi possível capturar a oferta; leitura por texto continua ativa")
        }
    }

    private fun prepareOfferRegion(pkg:String,bitmap:Bitmap):Bitmap {
        // Offer panels are normally below the map. Isolated floating cards stay whole.
        var image=bitmap
        if(bitmap.height>=dp(320)) {
            // Keep the 99 negotiation heading as well as both route legs.
            val fraction=if(driverAppName(pkg)=="99")0.75f else 0.72f
            val top=(bitmap.height*(1f-fraction)).toInt()
            image=Bitmap.createBitmap(bitmap,0,top,bitmap.width,bitmap.height-top)
        }
        // Keep more detail in dense tablet / floating-window offer cards.
        val factor=kotlin.math.sqrt(2_000_000.0/(image.width.toDouble()*image.height)).coerceAtMost(1.0)
        if(factor<1.0) {
            val scaled=Bitmap.createScaledBitmap(image,(image.width*factor).toInt().coerceAtLeast(1),(image.height*factor).toInt().coerceAtLeast(1),true)
            if(image!==bitmap && scaled!==image)image.recycle()
            image=scaled
        }
        return image
    }

    private fun recognizeCaptured(pkg:String,windowId:Int,bounds:Rect,captured:Bitmap,requestTime:Long,index:Int=0) {
        val angles=intArrayOf(0,0,90,270,180)
        val image=if(index==0)prepareOfferRegion(pkg,captured) else captured
        val ocrStarted=android.os.SystemClock.elapsedRealtime()
        var retry=false
        try {
                        textRecognizer.process(InputImage.fromBitmap(image, angles[index]))
                            .addOnSuccessListener { visionText ->
                                if (stopped || lastScreenshotAt!=requestTime || !captureStillValid(pkg, windowId, bounds)) return@addOnSuccessListener
                                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                                if (!prefs.getBoolean("copilot_enabled", false) && !prefs.getBoolean("traffic_enabled", false)) return@addOnSuccessListener
                                val recognized = visionText.text
                                val offer = if (IncomingOfferPolicy.isIncoming(pkg,recognized))
                                    OfferTextParser.parse(recognized,pkg) else null
                                recognitionStatus(pkg,"Imagem ${image.width}×${image.height} • rotação ${angles[index]} • ${recognized.length} caracteres • oferta=${offer!=null} • ${android.os.SystemClock.elapsedRealtime()-ocrStarted} ms")
                                if(offer==null) {
                                    discardPreviousOfferIfFareChanged(pkg,windowId,recognized)
                                    val fullUpright=index==1 || (index==0 && image===captured)
                                    // Once a readable full frame shows no offer,
                                    // release OCR for the other app immediately.
                                    // A later independent frame must confirm it.
                                    if(fullUpright && observeFullFrame(pkg,windowId,recognized))return@addOnSuccessListener
                                }
                                if(offer==null && index<angles.lastIndex) {
                                    retry=true
                                    recognizeCaptured(pkg,windowId,bounds,captured,requestTime,if(index==0 && image===captured)2 else index+1)
                                    return@addOnSuccessListener
                                }
                                if (offer == null) {
                                    readerStatus("Imagem lida; oferta sem valor, percurso ou tempo confiável")
                                    // A partial OCR frame must not erase a complete recent offer.
                                    // The per-window expiry removes stale cards if the offer disappears.
                                } else {
                                    rememberVisibleOffer(pkg,windowId)
                                    val signature = listOf(
                                        "%.2f".format(java.util.Locale.US, offer.fare),
                                        "%.2f".format(java.util.Locale.US, offer.pickupKm),
                                        "%.2f".format(java.util.Locale.US, offer.tripKm),
                                        "%.1f".format(java.util.Locale.US, offer.minutes),
                                        offer.passengerRating.orEmpty(),
            offer.pickupMinutes?.toString().orEmpty(),
                                        offer.pickupAddress.orEmpty(),
                                        offer.destinationAddress.orEmpty()
                                    ).joinToString("|")
                                    if (acceptedOfferSignatures[pkg] != signature && acceptedSignatures[pkg] != signature) {
                                        acceptedOfferSignatures.remove(pkg)
                                        acceptedSignatures[pkg] = signature
                                        calculateAndShow(offer, null, pkg)
                                    }
                                }
                            }
                            .addOnFailureListener { readerStatus("Falha ao reconhecer a imagem da oferta") }
                            .addOnCompleteListener {
                                if(image!==captured)image.recycle()
                                if(!retry) {
                                    captured.recycle()
                                    if(lastScreenshotAt==requestTime) screenshotInProgress = false
                                }
                            }
        } catch (_: Exception) {
            if(image!==captured)image.recycle()
            captured.recycle()
            if(lastScreenshotAt==requestTime)screenshotInProgress=false
            readerStatus("Reconhecimento indisponível; repetindo leitura")
        }
    }

    private fun isIncomingOfferScreen(root: AccessibilityNodeInfo, rawText: String): Boolean {
        val normalized = java.text.Normalizer.normalize(rawText, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(java.util.Locale.ROOT)

        // O cartão do Uber pode usar "Tenho interesse" em vez de "Aceitar".
        val hasAcceptAction = Regex("""\b(aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b""")
            .containsMatchIn(normalized)
        val hasFare = Regex("""r\s*\$\s*[0-9]""").containsMatchIn(normalized)
        val hasOfferMetrics = Regex("""[0-9]+(?:[,.][0-9]+)?\s*(?:min|km)\b""")
            .findAll(normalized).count() >= 2
        val isNavigationOrTrip = listOf(
            "encerrar viagem", "finalizar viagem", "em viagem", "cheguei"
        ).any { normalized.contains(it) }

        return IncomingOfferPolicy.isIncoming(root.packageName?.toString().orEmpty(),rawText) && hasFare && hasOfferMetrics && !isNavigationOrTrip
    }

    private fun normalizeActionText(value: CharSequence?): String {
        return java.text.Normalizer.normalize(value?.toString().orEmpty(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(java.util.Locale.ROOT)
            .trim()
    }

    private fun isAcceptActionNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val label = normalizeActionText(node.text).ifBlank { normalizeActionText(node.contentDescription) }
        return Regex("""\b(aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b""").containsMatchIn(label)
    }

    private fun hasVisibleAcceptAction(node: AccessibilityNodeInfo): Boolean {
        if (node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty && isAcceptActionNode(node)) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = hasVisibleAcceptAction(child)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun calculateAndShow(offer: OfferTextParser.ParsedOffer, anchor: Rect?, sourcePackage: String) {
        if(!readingEnabled())return
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val result = TripCalculator.calculate(
            offer.fare, offer.pickupKm, offer.tripKm, offer.minutes,
            TripCalculator.threshold(prefs.getFloat("bad_per_km", 1.50f)),
            TripCalculator.threshold(prefs.getFloat("min_per_km", 1.80f)),
            TripCalculator.threshold(prefs.getFloat("min_per_hour", 50f)),
            TripCalculator.threshold(prefs.getFloat("good_per_hour", 60f)),
            offer.passengerRating?.toDoubleOrNull(),
            TripCalculator.threshold(prefs.getFloat("min_rating", 4.80f)),
            TripCalculator.threshold(prefs.getFloat("good_rating", 4.90f))
        ) ?: return // Dados incompletos não apagam uma etiqueta válida já exibida.
        readerStatus("Oferta detectada em ${driverAppName(sourcePackage)}")
        prefs.edit().putLong("reader_last_detection",System.currentTimeMillis()).apply()
        // Salva cada oferta analisada no relatório da Jornada, sem duplicar eventos repetidos.
        JourneyStore.record(this, offer, result, sourcePackage)
        showOffer(result, offer, anchor, sourcePackage)
        val label = when(result.rating) {
            Rating.GOOD -> prefs.getString("label_good","Bora carai")
            Rating.REVIEW -> prefs.getString("label_review","Será?")
            Rating.BAD -> prefs.getString("label_bad","Vixe não dá")
        }
        val locale = java.util.Locale.forLanguageTag("pt-BR")
        val spoken = "${driverAppName(sourcePackage)}. $label. " +
            String.format(locale,"%.2f reais por quilômetro. %.2f reais por hora.",result.perKm,result.perHour)
        voice.announce(sourcePackage,spoken)
    }

    private fun driverWindow(pkg:String):Pair<Int,Rect>? {
        if(android.os.SystemClock.elapsedRealtime()-snapshotAt>2000L)return null
        val frame=driverCandidates[pkg].orEmpty().firstOrNull{it.id==detectedWindows[pkg]} ?: driverFrames[pkg] ?: return null
        return frame.id to Rect(frame.bounds)
    }
    private fun visibleDriverPackages():List<String> =
        if(android.os.SystemClock.elapsedRealtime()-snapshotAt>2000L)emptyList()
        else driverFrames.keys.sortedWith(compareBy<String> {
            // Confirm a readable missing-offer frame before starting another
            // app's OCR. Only the first confirmation gets priority, so an
            // unreadable/partial frame cannot starve the other visible apps.
            val missing = missingOffers[it]
            if (missing != null && missing.samples == 1 &&
                android.os.SystemClock.elapsedRealtime() - missing.since < 15_000L) 0 else 1
        }.thenBy { lastCaptureByPackage[it] ?: 0L })
    private val lastCaptureByPackage=mutableMapOf<String,Long>()
    private val lastCaptureByWindow=mutableMapOf<Pair<String,Int>,Long>()
    private fun captureStillValid(pkg:String,id:Int,bounds:Rect):Boolean {
        if(android.os.SystemClock.elapsedRealtime()-snapshotAt>2000L)return false
        // A per-window capture remains tied to this package and window even
        // if the driver resizes it while OCR is running. Full-display captures
        // on older Android versions still require the original bounds.
        return readingEnabled() && driverCandidates[pkg].orEmpty().any{it.id==id && (Build.VERSION.SDK_INT>=34 || it.bounds==bounds)}
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth:Int=0) {
        if (depth>60 || out.length>30000) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (node.isVisibleToUser && text.isNotEmpty()) out.append(text).append('\n')
        if (node.isVisibleToUser && description.isNotEmpty() && description != text) out.append(description).append('\n')
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, out, depth+1)
                child.recycle()
            }
        }
    }

    private fun findFareBounds(node: AccessibilityNodeInfo): Rect? {
        val value = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        if (value.contains("R$", true) || value.contains("Priority", true)) {
            return Rect().also { node.getBoundsInScreen(it) }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFareBounds(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun ensureOverlay(sourcePackage: String, anchor: Rect?): OfferTagView? {
        if (!Settings.canDrawOverlays(this)) return null
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val metrics = resources.displayMetrics
        val screenWidthPx = metrics.widthPixels
        val screenHeightPx = metrics.heightPixels
        val screenWidthDp = (screenWidthPx / metrics.density).toInt()
        val screenHeightDp = (screenHeightPx / metrics.density).toInt()
        val scale = prefs.getInt("overlay_scale", 100).coerceIn(60, 160) / 100f
        val overlayWidth = (310 * scale).toInt().coerceIn(180, (screenWidthDp - 16).coerceAtLeast(180))
        var view = overlays[sourcePackage]
        var params = overlayParams[sourcePackage]

        if (view == null || params == null) {
            view = OfferTagView(this).apply { elevation = dp(8).toFloat() }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            params = WindowManager.LayoutParams(
                dp(overlayWidth), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.LEFT
            val slot = when (driverAppName(sourcePackage)) {
                "UBER" -> 0
                "99" -> 1
                "INDRIVE" -> 2
                "IFOOD" -> 3
                else -> 4
            }
            val suffix = sourcePackage.hashCode().toString()+"_"+resources.configuration.orientation
            val landscape = screenWidthDp > screenHeightDp
            val landscapeColumns = ((screenWidthDp - 24) / (overlayWidth + 8)).coerceAtLeast(1)
            val useLandscapeRow = landscape && screenWidthDp >= 900
            val column = if (useLandscapeRow) slot % landscapeColumns else 0
            val row = if (useLandscapeRow) slot / landscapeColumns else slot
            val defaultX = if (useLandscapeRow) dp(12 + column * (overlayWidth + 8)) else dp(8)
            val defaultY = if (useLandscapeRow) dp(70 + row * 246) else dp(70 + slot * 246)
            val pane=Rect()
            driverWindow(sourcePackage)?.second?.let { pane.set(it) }
            val position=prefs.getInt("card_position_${driverAppName(sourcePackage)}",0)
            val chosenX=when(position){1->pane.left+(pane.width()-dp(overlayWidth))/2;2->pane.right-dp(overlayWidth)-dp(8);else->pane.left+dp(8)}
            params.x = prefs.getInt("offer_tag_x_$suffix", if(pane.isEmpty)defaultX else chosenX)
                .coerceIn(0, (screenWidthPx - dp(overlayWidth)).coerceAtLeast(0))
            params.y = prefs.getInt("offer_tag_y_$suffix", defaultY)
                .coerceIn(dp(24), (screenHeightPx - dp(238)).coerceAtLeast(dp(24)))

            var downX = 0f
            var downY = 0f
            var startX = params.x
            var startY = params.y
            var moved = false
            var closeTap = false
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        draggingTags.add(sourcePackage)
                        downX = event.rawX
                        downY = event.rawY
                        startX = params!!.x
                        startY = params.y
                        moved = false
                        closeTap = (v as? OfferTagView)?.isCloseHit(event.x,event.y) == true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                        if (moved) {
                            params!!.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - v.width).coerceAtLeast(0))
                            params.y = (startY + dy).coerceIn(dp(35), (resources.displayMetrics.heightPixels - v.height).coerceAtLeast(dp(35)))
                            try { wm.updateViewLayout(v, params) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        draggingTags.remove(sourcePackage)
                        if (moved) {
                            prefs.edit()
                                .putInt("offer_tag_x_$suffix", params!!.x)
                                .putInt("offer_tag_y_$suffix", params.y).apply()
                        } else if (closeTap) {
                            hideOverlay(sourcePackage)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        draggingTags.remove(sourcePackage)
                        reflowOverlay(sourcePackage)
                        true
                    }
                    else -> true
                }
            }
            try {
                wm.addView(view, params)
                overlays[sourcePackage] = view
                overlayParams[sourcePackage] = params
            } catch (_: Exception) {
                return null
            }
        }

        if (anchor != null && params != null) {
            // Mantém cada app na sua posição salva; a âncora só orienta a primeira exibição.
            val suffix = sourcePackage.hashCode().toString()+"_"+resources.configuration.orientation
            if (!prefs.contains("offer_tag_x_$suffix")) {
                params.y = (anchor.top - dp(242)).coerceIn(dp(45), (resources.displayMetrics.heightPixels - dp(238)).coerceAtLeast(dp(45)))
            }
            params.width = dp(overlayWidth)
            params.x = params.x.coerceIn(0, (screenWidthPx - dp(overlayWidth)).coerceAtLeast(0))
            params.y = params.y.coerceIn(dp(24), (screenHeightPx - dp(238)).coerceAtLeast(dp(24)))
            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        return view
    }

    private fun reflowOverlay(pkg: String) {
        val view = overlays[pkg] ?: return
        val params = overlayParams[pkg] ?: return
        if(pkg in draggingTags)return
        val driverPane = driverWindow(pkg)?.second ?: return
        if (driverPane.isEmpty || !isOfferSized(driverPane)) return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val suffix=pkg.hashCode().toString()+"_"+resources.configuration.orientation
        // A manually placed tag belongs to its app but may sit anywhere on screen.
        // Automatic tags still start inside their respective driver windows.
        val pane=if(prefs.contains("offer_tag_x_$suffix"))
            Rect(0,dp(24),resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels-dp(8))
            else driverPane
        val width = dp((310 * prefs.getInt("overlay_scale", 100).coerceIn(60, 160) / 100f).toInt()).coerceAtMost((pane.width()-dp(12)).coerceAtLeast(dp(100)))
        val oldWidth=params.width;val oldX=params.x;val oldY=params.y
        if(prefs.contains("offer_tag_x_$suffix")) {
            params.x=prefs.getInt("offer_tag_x_$suffix",params.x)
            params.y=prefs.getInt("offer_tag_y_$suffix",params.y)
        }
        params.width = width
        val maxX = (pane.right-width-dp(4)).coerceAtLeast(pane.left)
        params.x = params.x.coerceIn(pane.left, maxX)
        params.y = params.y.coerceIn(pane.top, (pane.bottom-view.height-dp(4)).coerceAtLeast(pane.top))
        if(oldWidth!=params.width || oldX!=params.x || oldY!=params.y)
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(view, params) } catch (_: Exception) {}
    }

    private fun renderTag(view:OfferTagView,r:TripResult,offer:OfferTextParser.ParsedOffer,sourcePackage:String) {
        val (label, color) = when (r.rating) {
            Rating.GOOD -> Pair("ACEITAR", Color.rgb(25, 145, 76))
            Rating.REVIEW -> Pair("ATENÇÃO", Color.rgb(205, 132, 0))
            Rating.BAD -> Pair("RECUSAR", Color.rgb(190, 40, 40))
        }
        val appName = driverAppName(sourcePackage) ?: "APP"
        view.setOffer(label, r.minutes, r.totalKm, r.perKm, r.perHour, offer.passengerRating, color, appName, r.fare, offer.pickupMinutes, offer.pickupKm.takeIf { offer.pickupMinutes != null || it > 0 }, r.reason)
    }

    private fun showOffer(r: TripResult, offer: OfferTextParser.ParsedOffer, anchor: Rect?, sourcePackage: String) {
        val view = ensureOverlay(sourcePackage, anchor) ?: return
        displayedOffers[sourcePackage]=DisplayedOffer(r,offer)
        renderTag(view,r,offer,sourcePackage)
        val generation = android.os.SystemClock.elapsedRealtimeNanos()
        view.tag = generation
        view.setAiInsight("Consultando IA; cálculos locais prontos")
        view.visibility = View.VISIBLE
        view.post { reflowOverlay(sourcePackage) }
        // IA complementa os indicadores matemáticos sem bloquear a leitura da oferta.
        Thread {
            val response = AiOfferClient.request(this, r)
            Handler(Looper.getMainLooper()).post {
                getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("ai_status",response.status).apply()
                if (overlays[sourcePackage] === view && view.visibility == View.VISIBLE && view.tag == generation) {
                    view.setAiInsight(response.insight ?: response.status)
                }
            }
        }.start()
        // Não há temporizador: a oferta fica fixa até o usuário fechar sua própria etiqueta.
    }

    private fun hideOverlay(sourcePackage: String) {
        overlays[sourcePackage]?.visibility = View.GONE
    }

    private fun hideAllOverlays() {
        draggingTags.clear()
        voice.stop()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlays.values.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        overlays.clear()
        overlayParams.clear()
        displayedOffers.clear()
        missingOffers.clear()
        acceptedSignatures.clear()
        lastOfferSeen.clear()
        detectedWindows.clear()
        lastCaptureByWindow.clear()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { hideAllOverlays(); removeBubble(); readerStatus("Leitura interrompida pelo Android") }

    override fun onDestroy() {
        stopped = true
        voice.shutdown()
        readerHandler.removeCallbacksAndMessages(null)
        active = null
        getSharedPreferences("settings", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(appearanceListener)
        readerStatus("Leitura desligada")
        NetworkGate.unregister(this, networkCallback)
        overlays.values.forEach { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        overlays.clear()
        overlayParams.clear()
        pendingOffers.clear()
        acceptedSignatures.clear()
        acceptedOfferSignatures.clear()
        bubble?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        bubble = null
        try { textRecognizer.close() } catch (_: Exception) {}
        windowExecutor.shutdownNow()
        screenshotExecutor.shutdown()
        super.onDestroy()
    }
}

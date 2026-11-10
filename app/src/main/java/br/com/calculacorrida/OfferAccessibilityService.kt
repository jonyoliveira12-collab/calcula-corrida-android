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
    private data class PendingOffer(val signature: String, val offer: OfferTextParser.ParsedOffer, var hits: Int, var lastSeen: Long)
    private val pendingOffers = mutableMapOf<String, PendingOffer>()
    private val acceptedSignatures = mutableMapOf<String, String>()
    // Evita atualizar uma oferta depois que o motorista toca em Aceitar.
    private val acceptedOfferSignatures = mutableMapOf<String, String>()
    private var bubble: TextView? = null
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    @Volatile private var screenshotInProgress = false
    @Volatile private var lastScreenshotAt = 0L
    private val readerHandler = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private var stopped = false
    private val lastOfferSeen = mutableMapOf<String, Long>()
    private val poll = object : Runnable {
        override fun run() {
            if (stopped) return
            val pkg = rootInActiveWindow?.let { root ->
                val name = root.packageName?.toString()
                root.recycle()
                name
            }
            updateForeground(pkg)
            if (pkg != null && driverAppName(pkg) != null && readingEnabled()) readOffer(pkg, null)
            val now = android.os.SystemClock.elapsedRealtime()
            lastOfferSeen.filterValues { now - it > 5000L }.keys.toList().forEach { expired ->
                hideOverlay(expired);lastOfferSeen.remove(expired);acceptedSignatures.remove(expired)
            }
            readerHandler.postDelayed(this, 1000L)
        }
    }

    private fun readingEnabled(): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("reader_disclosure_accepted", false) &&
            (prefs.getBoolean("copilot_enabled", false) || prefs.getBoolean("traffic_enabled", false))
    }

    private fun readerStatus(message: String) {
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("reader_status", message).putLong("reader_seen_at", System.currentTimeMillis()).apply()
    }

    private fun removeBubble() {
        bubble?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        bubble = null
    }

    private fun updateForeground(pkg: String?) {
        if (foregroundPackage != pkg) {
            foregroundPackage = pkg
            hideAllOverlays()
        }
        if (!readingEnabled() || pkg == null || (pkg != packageName && driverAppName(pkg) == null)) {
            hideAllOverlays()
            removeBubble()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            readerStatus("Falta permitir sobreposição de tela")
            return
        }
        if (bubble != null) return
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
                    else packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                    }
                }
            }
            true
        }
        try { wm.addView(view,params); bubble=view } catch (_: Exception) { readerStatus("Não foi possível mostrar a bolinha CC") }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            Handler(Looper.getMainLooper()).post { hideAllOverlays() }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!validated) Handler(Looper.getMainLooper()).post { hideAllOverlays() }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
        NetworkGate.register(this, networkCallback)
        stopped = false
        readerStatus("Serviço conectado; aguardando app de motorista")
        readerHandler.removeCallbacks(poll)
        readerHandler.post(poll)
    }

    companion object {
        private var active: OfferAccessibilityService? = null
        fun stopReading() { active?.apply { stopped = true; readerHandler.removeCallbacks(poll); hideAllOverlays(); removeBubble(); disableSelf() } }
    }

    private fun driverAppName(pkg: String): String? {
        return when (pkg.lowercase(java.util.Locale.ROOT)) {
            "com.ubercab.driver" -> "UBER"
            "com.taxis99", "com.didiglobal.driver" -> "99"
            "sinet.startup.indriver" -> "INDRIVE"
            "br.com.ifood.driver.app" -> "IFOOD"
            else -> null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: event.packageName?.toString()
        root?.recycle()
        updateForeground(pkg)
        if (pkg != null && driverAppName(pkg) != null && readingEnabled()) readOffer(pkg, event)
    }

    private fun readOffer(pkg: String, event: AccessibilityEvent?) {
        if (!readingEnabled() || foregroundPackage != pkg) return
        // A tela inteira via OCR pode misturar dois apps no tablet. Para evitar
        // ofertas híbridas, aceitamos somente os dados da árvore do próprio app.
        val root = findDriverWindowRoot(pkg) ?: rootInActiveWindow?.takeIf {
            it.packageName?.toString()?.equals(pkg, ignoreCase = true) == true
        } ?: run { requestOfferScreenshot(pkg); return }
        try {
        val text = StringBuilder()
        collectText(root, text)
        val screenText = text.toString()

        // Só existe oferta enquanto o próprio app do motorista exibe a ação de aceitar.
        // Na tela de navegação/viagem, distância e ETA mudam conforme o carro se aproxima;
        // esses dados NUNCA devem atualizar a análise da oferta já aceita.
        if (!isIncomingOfferScreen(root, screenText)) {
            val navigation = normalizeActionText(screenText)
            if (listOf("encerrar viagem", "finalizar viagem", "em viagem", "cheguei").any { navigation.contains(it) }) {
                hideOverlay(pkg)
                lastOfferSeen.remove(pkg)
                acceptedSignatures.remove(pkg)
                return
            }
            readerStatus("${driverAppName(pkg)} aberto; conferindo oferta por imagem")
            requestOfferScreenshot(pkg)
            return
        }

        val offer = OfferTextParser.parse(screenText)
        if (offer == null) {
            readerStatus("Texto incompleto; conferindo oferta por imagem")
            requestOfferScreenshot(pkg)
            return
        }
        lastOfferSeen[pkg] = android.os.SystemClock.elapsedRealtime()
        val clickedAccept = event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isAcceptActionNode(event.source)
        // Oferta completa: a assinatura impede alterações causadas pela navegação.
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
        if (clickedAccept) {
            acceptedOfferSignatures[pkg] = signature
            hideOverlay(pkg)
            return
        }
        if (acceptedOfferSignatures[pkg] == signature) {
            hideOverlay(pkg)
            return
        }
        acceptedOfferSignatures.remove(pkg)
        if (acceptedSignatures[pkg] != signature) {
            acceptedSignatures[pkg] = signature
            calculateAndShow(offer, findFareBounds(root), pkg)
        }
        } finally { root.recycle() }
    }

    // Alguns cartões da Uber/99 são desenhados como imagem e não aparecem na árvore
    // de acessibilidade. Nesses casos, fazemos OCR da tela ANTES do aceite.
    private fun requestOfferScreenshot(pkg: String) {
        if (Build.VERSION.SDK_INT < 30) { readerStatus("Neste Android a leitura depende do texto acessível da oferta"); return }
        if (screenshotInProgress || foregroundPackage != pkg || !readingEnabled()) return
        val root = findDriverWindowRoot(pkg) ?: return
        val windowId = root.windowId
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        root.recycle()
        if (Build.VERSION.SDK_INT < 34 && bounds.width() < resources.displayMetrics.widthPixels * 0.9) {
            readerStatus("Abra o app de motorista em tela cheia para usar OCR neste Android")
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastScreenshotAt < 700L) return
        lastScreenshotAt = now
        screenshotInProgress = true
        try {
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        var bitmap: Bitmap? = null
                        try {
                            val hardwareBuffer = result.hardwareBuffer
                            bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Exception) {
                        }
                        try { result.hardwareBuffer.close() } catch (_: Exception) {}
                        val captured = bitmap
                        val currentRoot = rootInActiveWindow
                        val currentPackage = currentRoot?.packageName?.toString()
                        currentRoot?.recycle()
                        if (stopped || foregroundPackage != pkg || currentPackage != pkg) {
                            captured?.recycle()
                            screenshotInProgress = false
                            return
                        }
                        if (captured == null) {
                            screenshotInProgress = false
                            return
                        }
                        textRecognizer.process(InputImage.fromBitmap(captured, 0))
                            .addOnSuccessListener { visionText ->
                                if (stopped || foregroundPackage != pkg) return@addOnSuccessListener
                                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                                if (!prefs.getBoolean("copilot_enabled", false) && !prefs.getBoolean("traffic_enabled", false)) return@addOnSuccessListener
                                val recognized = visionText.text
                                val normalized = java.text.Normalizer.normalize(recognized,
                                    java.text.Normalizer.Form.NFD)
                                    .replace(Regex("\\p{Mn}+"), "")
                                    .lowercase(java.util.Locale.ROOT)
                                val hasAccept = Regex("""\b(aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b""")
                                    .containsMatchIn(normalized)
                                val offer = if (hasAccept && Regex("""r\s*\$\s*[0-9]""").containsMatchIn(normalized))
                                    OfferTextParser.parse(recognized) else null
                                if (offer == null) {
                                    readerStatus("Imagem lida; oferta sem valor, percurso ou tempo confiável")
                                    hideOverlay(pkg)
                                    acceptedSignatures.remove(pkg)
                                    acceptedOfferSignatures.remove(pkg)
                                } else {
                                    lastOfferSeen[pkg] = android.os.SystemClock.elapsedRealtime()
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
                                captured.recycle()
                                screenshotInProgress = false
                            }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress = false
                        readerStatus("Captura indisponível (código $errorCode). A leitura por texto continua ativa.")
                    }
                }
            if (Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(windowId, screenshotExecutor, callback)
            else takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, callback)
        } catch (_: Exception) {
            screenshotInProgress = false
            readerStatus("Não foi possível capturar a oferta; leitura por texto continua ativa")
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

        return hasAcceptAction && hasVisibleAcceptAction(root) && hasFare && hasOfferMetrics && !isNavigationOrTrip
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
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val result = TripCalculator.calculate(
            offer.fare, offer.pickupKm, offer.tripKm, offer.minutes,
            prefs.getFloat("bad_per_km", 1.50f).toDouble(),
            prefs.getFloat("min_per_km", 1.80f).toDouble(),
            prefs.getFloat("min_per_hour", 50f).toDouble(),
            prefs.getFloat("good_per_hour", 60f).toDouble(),
            offer.passengerRating?.toDoubleOrNull(),
            prefs.getFloat("min_rating", 4.80f).toDouble(),
            prefs.getFloat("good_rating", 4.90f).toDouble()
        ) ?: return // Dados incompletos não apagam uma etiqueta válida já exibida.
        readerStatus("Oferta detectada em ${driverAppName(sourcePackage)}")
        // Salva cada oferta analisada no relatório da Jornada, sem duplicar eventos repetidos.
        JourneyStore.record(this, offer, result, sourcePackage)
        showOffer(result, offer, anchor, sourcePackage)
    }

    private fun findDriverWindowRoot(pkg: String): AccessibilityNodeInfo? {
        return try {
            windows.firstNotNullOfOrNull { window ->
                if (!window.isActive && !window.isFocused) return@firstNotNullOfOrNull null
                val candidate = window.root ?: return@firstNotNullOfOrNull null
                val rootPackage = candidate.packageName?.toString()?.lowercase().orEmpty()
                if (rootPackage == pkg.lowercase()) candidate
                else { candidate.recycle(); null }
            }
        } catch (_: Exception) { null }
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder) {
        if (!node.isVisibleToUser) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) out.append(text).append('\n')
        if (description.isNotEmpty() && description != text) out.append(description).append('\n')
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, out)
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
        val scale = prefs.getInt("overlay_scale", 85).coerceIn(65, 110) / 100f
        val overlayWidth = (340 * scale).toInt().coerceIn(220, (screenWidthDp - 16).coerceAtLeast(180))
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
            val suffix = sourcePackage.hashCode().toString()
            val landscape = screenWidthDp > screenHeightDp
            val landscapeColumns = ((screenWidthDp - 24) / (overlayWidth + 8)).coerceAtLeast(1)
            val useLandscapeRow = landscape && screenWidthDp >= 900
            val column = if (useLandscapeRow) slot % landscapeColumns else 0
            val row = if (useLandscapeRow) slot / landscapeColumns else slot
            val defaultX = if (useLandscapeRow) dp(12 + column * (overlayWidth + 8)) else dp(8)
            val defaultY = if (useLandscapeRow) dp(70 + row * 246) else dp(70 + slot * 246)
            params.x = prefs.getInt("offer_tag_x_$suffix", defaultX)
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
                        downX = event.rawX
                        downY = event.rawY
                        startX = params!!.x
                        startY = params.y
                        moved = false
                        closeTap = event.x >= v.width - dp(42) && event.y <= dp(42)
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
                        if (moved) {
                            prefs.edit()
                                .putInt("offer_tag_x_$suffix", params!!.x)
                                .putInt("offer_tag_y_$suffix", params.y).apply()
                        } else if (closeTap) {
                            hideOverlay(sourcePackage)
                        }
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
            val suffix = sourcePackage.hashCode().toString()
            if (!prefs.contains("offer_tag_x_$suffix")) {
                params.x = anchor.left.coerceIn(0, (resources.displayMetrics.widthPixels - dp(overlayWidth)).coerceAtLeast(0))
                params.y = (anchor.top - dp(242)).coerceIn(dp(45), (resources.displayMetrics.heightPixels - dp(238)).coerceAtLeast(dp(45)))
            }
            params.width = dp(overlayWidth)
            params.x = params.x.coerceIn(0, (screenWidthPx - dp(overlayWidth)).coerceAtLeast(0))
            params.y = params.y.coerceIn(dp(24), (screenHeightPx - dp(238)).coerceAtLeast(dp(24)))
            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        return view
    }

    private fun showOffer(r: TripResult, offer: OfferTextParser.ParsedOffer, anchor: Rect?, sourcePackage: String) {
        val (label, color) = when (r.rating) {
            Rating.GOOD -> Pair("BOA CORRIDA", Color.rgb(25, 145, 76))
            Rating.REVIEW -> Pair("ATENÇÃO", Color.rgb(205, 132, 0))
            Rating.BAD -> Pair("ABAIXO DOS LIMITES", Color.rgb(190, 40, 40))
        }
        val view = ensureOverlay(sourcePackage, anchor) ?: return
        val appName = driverAppName(sourcePackage) ?: "APP"
        view.setOffer(label, r.minutes, r.totalKm, r.perKm, r.perHour, offer.passengerRating, color, appName, r.fare, offer.pickupMinutes, offer.pickupKm.takeIf { offer.pickupMinutes != null || it > 0 })
        val generation = android.os.SystemClock.elapsedRealtimeNanos()
        view.tag = generation
        view.setAiInsight("Consultando IA; cálculos locais prontos")
        view.visibility = View.VISIBLE
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
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlays.values.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        overlays.clear()
        overlayParams.clear()
        acceptedSignatures.clear()
        lastOfferSeen.clear()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { hideAllOverlays(); removeBubble(); readerStatus("Leitura interrompida pelo Android") }

    override fun onDestroy() {
        stopped = true
        readerHandler.removeCallbacksAndMessages(null)
        active = null
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
        screenshotExecutor.shutdown()
        super.onDestroy()
    }
}

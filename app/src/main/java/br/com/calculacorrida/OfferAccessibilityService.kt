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
        NetworkGate.register(this, networkCallback)
        val featurePrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (Settings.canDrawOverlays(this) && bubble == null && featurePrefs.getBoolean("copilot_enabled", false)) {
            val v = TextView(this).apply {
                text = "CC"
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(218, 166, 67)); setStroke(dp(2), Color.WHITE) }
                isClickable = true
                contentDescription = "Botão CC. Arraste para mover ou toque para abrir o aplicativo."
            }
            val t = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val p = WindowManager.LayoutParams(
                dp(46), dp(46), t,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.LEFT
            p.x = prefs.getInt("bubble_x", dp(8)).coerceAtLeast(0)
            p.y = prefs.getInt("bubble_y", dp(220)).coerceAtLeast(0)
            var downX = 0f
            var downY = 0f
            var initialX = p.x
            var initialY = p.y
            var moved = false
            v.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        initialX = p.x; initialY = p.y; moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) moved = true
                        if (moved) {
                            p.x = (initialX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - dp(46)).coerceAtLeast(0))
                            p.y = (initialY + dy).coerceIn(0, (resources.displayMetrics.heightPixels - dp(46)).coerceAtLeast(0))
                            try { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(view, p) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            prefs.edit().putInt("bubble_x", p.x).putInt("bubble_y", p.y).apply()
                        } else {
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            if (intent != null) startActivity(intent)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, p); bubble = v } catch (_: Exception) { }
        }
    }

    private fun driverAppName(pkg: String): String? {
        val p = pkg.lowercase()
        return when {
            p.contains("uber") -> "UBER"
            p.contains("didiglobal") || p.contains("99") -> "99"
            p.contains("indrive") || p.contains("indriver") || p.contains("taxi") -> "INDRIVE"
            p.contains("ifood") -> "IFOOD"
            else -> null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val relevantTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        )
        if (event.eventType !in relevantTypes) return
        val pkg = event.packageName?.toString() ?: return
        if (!NetworkGate.isOnline(this)) {
            hideAllOverlays()
            return
        }
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val copilotEnabled = prefs.getBoolean("copilot_enabled", false)
        val trafficEnabled = prefs.getBoolean("traffic_enabled", false)

        if (!copilotEnabled) {
            bubble?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
            bubble = null
        } else if (bubble == null && Settings.canDrawOverlays(this)) {
            onServiceConnected()
        }
        if (!trafficEnabled) {
            hideAllOverlays()
            return
        }

        if (driverAppName(pkg) == null) return
        // A tela inteira via OCR pode misturar dois apps no tablet. Para evitar
        // ofertas híbridas, aceitamos somente os dados da árvore do próprio app.
        val root = findDriverWindowRoot(pkg) ?: rootInActiveWindow?.takeIf {
            it.packageName?.toString()?.equals(pkg, ignoreCase = true) == true
        } ?: return
        val text = StringBuilder()
        collectText(root, text)
        val screenText = text.toString()

        // Só existe oferta enquanto o próprio app do motorista exibe a ação de aceitar.
        // Na tela de navegação/viagem, distância e ETA mudam conforme o carro se aproxima;
        // esses dados NUNCA devem atualizar a análise da oferta já aceita.
        if (!isIncomingOfferScreen(root, screenText)) {
            hideOverlay(pkg)
            acceptedSignatures.remove(pkg)
            pendingOffers.remove(pkg)
            acceptedOfferSignatures.remove(pkg)
            return
        }

        val offer = OfferTextParser.parse(screenText)
        if (offer == null) {
            hideOverlay(pkg)
            acceptedSignatures.remove(pkg)
            return
        }
        val clickedAccept = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isAcceptActionNode(event.source)
        // Oferta completa: a assinatura impede alterações causadas pela navegação.
        val signature = listOf(
            "%.2f".format(java.util.Locale.US, offer.fare),
            "%.2f".format(java.util.Locale.US, offer.pickupKm),
            "%.2f".format(java.util.Locale.US, offer.tripKm),
            "%.1f".format(java.util.Locale.US, offer.minutes),
            offer.passengerRating.orEmpty()
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
    }

    private fun isIncomingOfferScreen(root: AccessibilityNodeInfo, rawText: String): Boolean {
        val normalized = java.text.Normalizer.normalize(rawText, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(java.util.Locale.ROOT)

        // O cartão do Uber pode usar "Tenho interesse" em vez de "Aceitar".
        val hasAcceptAction = Regex("""\b(aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b""")
            .containsMatchIn(normalized)
        val hasFare = Regex("""r\$\s*[0-9]""").containsMatchIn(normalized)
        val hasOfferMetrics = Regex("""[0-9]+(?:[,.][0-9]+)?\s*(?:min|km)\b""")
            .findAll(normalized).count() >= 2
        val isNavigationOrTrip = listOf(
            "navegar", "encerrar viagem", "finalizar viagem", "em viagem",
            "cheguei", "encontro com", "iniciar viagem"
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
        return Regex("""\\b(aceitar|aceite|accept|aceptar|tenho\\s+interesse|confirmar)\\b""").containsMatchIn(label)
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
        // Salva cada oferta analisada no relatório da Jornada, sem duplicar eventos repetidos.
        JourneyStore.record(this, offer, result, sourcePackage)
        showOffer(result, offer.passengerRating, anchor, sourcePackage)
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
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
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
        val overlayWidth = (310 * scale).toInt().coerceIn(220, (screenWidthDp - 16).coerceAtLeast(180))
        var view = overlays[sourcePackage]
        var params = overlayParams[sourcePackage]

        if (view == null || params == null) {
            view = OfferTagView(this).apply { elevation = dp(8).toFloat() }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            params = WindowManager.LayoutParams(
                dp(overlayWidth), dp(132), type,
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
            val defaultY = if (useLandscapeRow) dp(70 + row * 116) else dp(70 + slot * 116)
            params.x = prefs.getInt("offer_tag_x_$suffix", defaultX)
                .coerceIn(0, (screenWidthPx - dp(overlayWidth)).coerceAtLeast(0))
            params.y = prefs.getInt("offer_tag_y_$suffix", defaultY)
                .coerceIn(dp(24), (screenHeightPx - dp(116)).coerceAtLeast(dp(24)))

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
                params.y = (anchor.top - dp(118)).coerceIn(dp(45), (resources.displayMetrics.heightPixels - dp(120)).coerceAtLeast(dp(45)))
            }
            params.width = dp(overlayWidth)
            params.x = params.x.coerceIn(0, (screenWidthPx - dp(overlayWidth)).coerceAtLeast(0))
            params.y = params.y.coerceIn(dp(24), (screenHeightPx - dp(116)).coerceAtLeast(dp(24)))
            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        return view
    }

    private fun showOffer(r: TripResult, passengerRating: String?, anchor: Rect?, sourcePackage: String) {
        val (label, color) = when (r.rating) {
            Rating.GOOD -> Pair("ACEITAR", Color.rgb(25, 145, 76))
            Rating.REVIEW -> Pair("ATENÇÃO", Color.rgb(205, 132, 0))
            Rating.BAD -> Pair("REJEITAR", Color.rgb(190, 40, 40))
        }
        val view = ensureOverlay(sourcePackage, anchor) ?: return
        val appName = driverAppName(sourcePackage) ?: "APP"
        view.setOffer(label, r.minutes, r.totalKm, r.perKm, r.perHour, passengerRating, color, appName)
        view.setAiInsight("Consultando copiloto...")
        view.visibility = View.VISIBLE
        // IA complementa os indicadores matemáticos sem bloquear a leitura da oferta.
        Thread {
            val insight = AiOfferClient.analyze(this, r)
            if (!insight.isNullOrBlank()) Handler(Looper.getMainLooper()).post {
                if (overlays[sourcePackage] === view && view.visibility == View.VISIBLE) view.setAiInsight(insight)
            }
        }.start()
        // Não há temporizador: a oferta fica fixa até o usuário fechar sua própria etiqueta.
    }

    private fun hideOverlay(sourcePackage: String) {
        overlays[sourcePackage]?.visibility = View.GONE
    }

    private fun hideAllOverlays() {
        overlays.values.forEach { it.visibility = View.GONE }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { /* Mantém as etiquetas válidas durante interrupções breves. */ }

    override fun onDestroy() {
        NetworkGate.unregister(this, networkCallback)
        overlays.values.forEach { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        overlays.clear()
        overlayParams.clear()
        pendingOffers.clear()
        acceptedSignatures.clear()
        acceptedOfferSignatures.clear()
        bubble?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        bubble = null
        super.onDestroy()
    }
}

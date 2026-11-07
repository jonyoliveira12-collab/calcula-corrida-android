package br.com.calculacorrida

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.provider.Settings
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.MotionEvent
import java.util.Locale

class OfferAccessibilityService : AccessibilityService() {

    private var overlay: OverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isOverlayVisible = false
    private var tts: TextToSpeech? = null
    private var voiceEnabled = false

    // Bolha flutuante
    private var bubble: TextView? = null
    private var lastText = ""
    private var lastPackage = ""
    private var lastProcessTime = 0L
    private val THROTTLE_MS = 400L

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
            }
        }
        setupBubble()
    }

    // === BOLHA FLUTUANTE "CC" ===
    private fun setupBubble() {
        if (bubble != null) return
        if (!Settings.canDrawOverlays(this)) return

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val v = TextView(this).apply {
            text = "CC"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(0, 168, 89))
                setStroke(dp(2), Color.WHITE)
            }
            isClickable = true
            contentDescription = "Botão CC. Arraste para mover ou toque para abrir o aplicativo."
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            dp(46), dp(46), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, p)
            bubble = v
        } catch (_: Exception) {}
    }

    // === VERIFICA APP DE CORRIDA ===
    private fun isSupportedDriverApp(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("uber") || p.contains("didiglobal") || p.contains("99") || p.contains("indrive") || p.contains("taxi")
    }

    // === EVENTO DE ACESSIBILIDADE ===
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Verifica se o copilot está ligado (remove traffic_enabled que não existe)
        if (!prefs.getBoolean("copilot_enabled", true)) {
            if (isOverlayVisible) hideOverlay()
            return
        }

        voiceEnabled = prefs.getBoolean("voice_enabled", false)

        // Garante que a bolha existe
        if (bubble == null) setupBubble()

        // Se não for app de corrida, esconde a tag
        if (!isSupportedDriverApp(pkg)) {
            if (isOverlayVisible) hideOverlay()
            lastPackage = ""
            return
        }

        if (lastPackage != pkg) {
            lastPackage = pkg
            lastText = "" // reset ao trocar de app
        }

        // Busca a janela do app de corrida (suporte a tela dividida)
        val root = findDriverWindowRoot(pkg) ?: rootInActiveWindow ?: return

        // Coleta todo o texto da tela
        val sb = StringBuilder()
        collectText(root, sb)
        val screenText = sb.toString()

        if (screenText.isBlank()) return

        // === THROTTLE ===
        if (screenText == lastText) return
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < THROTTLE_MS) return
        lastProcessTime = now
        lastText = screenText

        // Faz o parse
        val offer = OfferTextParser.parse(screenText)
        if (offer != null) {
            calculateAndShow(offer, prefs)
        } else {
            // Se não achou por texto, não esconde imediatamente
            // (pode ser tela intermediária)
        }
    }

    // === CÁLCULO E EXIBIÇÃO ===
    private fun calculateAndShow(offer: OfferTextParser.ParsedOffer, prefs: android.content.SharedPreferences) {
        val minPerKm = prefs.getFloat("min_per_km", 1.80f).toDouble()
        val minPerHour = prefs.getFloat("min_per_hour", 35f).toDouble()
        val costPerKm = prefs.getFloat("cost_per_km", 0.65f).toDouble()

        val result = TripCalculator.calculate(
            offer.fare, offer.pickupKm, offer.tripKm, offer.minutes,
            minPerKm, minPerHour
        )

        if (result == null) {
            hideOverlay()
            return
        }

        // Status e cor (Rota Pro: verde, amarelo, vermelho)
        val (statusText, statusColor) = when (result.rating) {
            Rating.GOOD -> Pair("✓ ACEITAR", Color.rgb(0, 168, 89))
            Rating.REVIEW -> Pair("⚠ ATENÇÃO", Color.rgb(255, 193, 7))
            Rating.BAD -> Pair("✗ RUIM", Color.rgb(220, 53, 69))
        }

        // Calcula lucro se ativado
        var profitStr = ""
        if (prefs.getBoolean("show_profit", false) && costPerKm > 0) {
            val totalKm = result.totalKm
            val profit = ((offer.fare - (totalKm * costPerKm)) / offer.fare) * 100
            profitStr = "${if (profit.toInt() > 0) "+" else ""}${profit.toInt()}%"
        }

        // Voz (TTS)
        if (voiceEnabled) {
            val speech = "$statusText. ${"%.2f".format(result.perKm)} reais por km."
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "offer_${System.currentTimeMillis()}")
        }

        // Monta a nota do passageiro
        var ratingDisplay = ""
        if (!offer.passengerRating.isNullOrBlank()) {
            ratingDisplay = offer.passengerRating
            if (!offer.passengerTrips.isNullOrBlank()) {
                ratingDisplay += " (${offer.passengerTrips})"
            }
        }

        showOffer(
            status = statusText,
            statusColor = statusColor,
            fare = "R$ ${"%.2f".format(offer.fare)}",
            perKm = "R$ ${"%.2f".format(result.perKm)}/km",
            perHour = "R$ ${"%.2f".format(result.perHour)}/h",
            tripTime = "${"%.0f".format(offer.minutes)} min",
            rating = ratingDisplay,
            profit = profitStr,
            prefs = prefs
        )
    }

    // === MOSTRA A TAG NA TELA ===
    private fun showOffer(
        status: String, statusColor: Int, fare: String,
        perKm: String, perHour: String, tripTime: String,
        rating: String, profit: String,
        prefs: android.content.SharedPreferences
    ) {
        val view = ensureOverlay() ?: return

        view.status = status
        view.statusColor = statusColor
        view.fare = fare
        view.perKm = perKm
        view.perHour = perHour
        view.tripTime = tripTime
        view.passengerRating = if (prefs.getBoolean("show_rating", true)) rating else ""
        view.profitPercent = profit
        view.showProfit = prefs.getBoolean("show_profit", false)
        view.showTime = prefs.getBoolean("show_time", true)
        view.showRating = prefs.getBoolean("show_rating", true)
        view.onClose = { hideOverlay() }

        view.invalidate()
        view.requestLayout()
        view.visibility = View.VISIBLE
        isOverlayVisible = true
    }

    // === CRIA A TAG SE NÃO EXISTIR ===
    private fun ensureOverlay(): OverlayView? {
        if (overlay != null && overlay!!.isAttachedToWindow) return overlay

        if (overlay != null) {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(overlay) } catch (_: Exception) {}
            overlay = null
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val position = prefs.getString("overlay_position", "right") ?: "right"

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        if (position == "left") {
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.x = dp(8)
        } else {
            params.gravity = Gravity.TOP or Gravity.RIGHT
            params.x = dp(8)
        }
        params.y = dp(130)

        val v = OverlayView(this)
        v.onClose = { hideOverlay() }

        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, params)
            overlay = v
            overlayParams = params
        } catch (e: Exception) {
            return null
        }
        return overlay
    }

    // === ESCONDE A TAG ===
    private fun hideOverlay() {
        overlay?.visibility = View.GONE
        isOverlayVisible = false
        lastText = ""
    }

    // === BUSCA JANELA DO APP DE CORRIDA (SPLIT-SCREEN) ===
    private fun findDriverWindowRoot(pkg: String): AccessibilityNodeInfo? {
        return try {
            windows.firstNotNullOfOrNull { window ->
                val candidate = window.root ?: return@firstNotNullOfOrNull null
                val rootPackage = candidate.packageName?.toString()?.lowercase().orEmpty()
                if (rootPackage == pkg.lowercase() || isSupportedDriverApp(rootPackage)) candidate
                else { candidate.recycle(); null }
            }
        } catch (_: Exception) { null }
    }

    // === COLETA TEXTO DA TELA ===
    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder) {
        try {
            node.text?.let { out.append(it).append(' ') }
            node.contentDescription?.let { out.append(it).append(' ') }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectText(child, out)
                child.recycle()
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        overlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        bubble?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

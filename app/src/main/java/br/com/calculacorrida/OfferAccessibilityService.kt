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
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OfferAccessibilityService : AccessibilityService() {
    // Uma etiqueta independente por aplicativo (Uber, 99 e inDrive).
    private val overlays = linkedMapOf<String, OfferTagView>()
    private val overlayParams = linkedMapOf<String, WindowManager.LayoutParams>()
    private val lastTexts = mutableMapOf<String, String>()
    private var bubble: TextView? = null
    private var lastOcrAt = 0L
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onServiceConnected() {
        super.onServiceConnected()
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
            p.contains("indrive") || p.contains("taxi") -> "INDRIVE"
            else -> null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
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

        val appName = driverAppName(pkg) ?: return
        // Em tela dividida, usa a janela do app de motorista, não a do Maps.
        val root = findDriverWindowRoot(pkg) ?: rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text)
        val screenText = text.toString()
        if (screenText.isNotBlank() && screenText != lastTexts[pkg]) {
            lastTexts[pkg] = screenText
            val offer = OfferTextParser.parse(screenText)
            if (offer != null) {
                calculateAndShow(offer, findFareBounds(root), pkg)
                return
            }
        }
        readOfferByScreenshot(pkg)
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

    private fun readOfferByScreenshot(sourcePackage: String) {
        if (Build.VERSION.SDK_INT < 30) return
        val now = SystemClock.elapsedRealtime()
        val last = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getLong("ocr_last_$sourcePackage", 0L)
        if (now - last < 1800L) return
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putLong("ocr_last_$sourcePackage", now).apply()

        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bmp = try {
                    Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                } catch (_: Exception) { null }
                result.hardwareBuffer.close()
                if (bmp == null) return
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { vision ->
                        val parsed = OfferTextParser.parse(vision.text)
                        if (parsed != null) calculateAndShow(parsed, null, sourcePackage)
                    }
                    .addOnFailureListener { /* Falha temporária: preserva a última etiqueta válida. */ }
                    .addOnCompleteListener { bmp.recycle() }
            }
            override fun onFailure(errorCode: Int) { /* Não remove as etiquetas existentes. */ }
        })
    }

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

    private fun showDiagnostic(message: String) {
        val view = ensureOverlay(null) ?: return
        view.setDiagnostic(message)
        view.visibility = View.VISIBLE
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
        val scale = prefs.getInt("overlay_scale", 85).coerceIn(65, 110) / 100f
        val overlayWidth = (310 * scale).toInt().coerceIn(220, 340)
        var view = overlays[sourcePackage]
        var params = overlayParams[sourcePackage]

        if (view == null || params == null) {
            view = OfferTagView(this).apply { elevation = dp(8).toFloat() }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            params = WindowManager.LayoutParams(
                dp(overlayWidth), dp(112), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.LEFT
            val slot = when (driverAppName(sourcePackage)) {
                "UBER" -> 0
                "99" -> 1
                else -> 2
            }
            val suffix = sourcePackage.hashCode().toString()
            params.x = prefs.getInt("offer_tag_x_$suffix", dp(12)).coerceAtLeast(0)
            params.y = prefs.getInt("offer_tag_y_$suffix", dp(145 + slot * 120))
                .coerceIn(dp(45), (resources.displayMetrics.heightPixels - dp(120)).coerceAtLeast(dp(45)))

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
        view.visibility = View.VISIBLE
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
        overlays.values.forEach { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        overlays.clear()
        overlayParams.clear()
        bubble?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        bubble = null
        try { recognizer.close() } catch (_: Exception) { }
        super.onDestroy()
    }
}

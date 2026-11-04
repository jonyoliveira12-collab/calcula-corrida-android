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
    private var overlay: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lastText = ""
    private var lastPackage = ""
    private var bubble: TextView? = null
    private var lastOcrAt = 0L
    private val overlayHandler = Handler(Looper.getMainLooper())
    private var dismissOverlayTask: Runnable? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Settings.canDrawOverlays(this) && bubble == null) {
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

    private fun isSupportedDriverApp(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("uber") || p.contains("didiglobal") || p.contains("99") || p.contains("taxi")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("copilot_enabled", true) || !prefs.getBoolean("traffic_enabled", true)) {
            hideOverlay()
            return
        }
        if (!isSupportedDriverApp(pkg)) {
            if (lastPackage.isNotEmpty()) hideOverlay()
            lastPackage = ""
            return
        }
        lastPackage = pkg
        // In split-screen, rootInActiveWindow may belong to Maps. Search the driver's own window.
        val root = findDriverWindowRoot(pkg) ?: rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text)
        val screenText = text.toString()
        if (screenText.isNotBlank() && screenText != lastText) {
            lastText = screenText
            val offer = OfferTextParser.parse(screenText)
            if (offer != null) {
                calculateAndShow(offer, findFareBounds(root))
                return
            }
        }
        // Estar online no app não significa que uma oferta chegou.
        // Faz OCR em intervalos curtos e só mostra o semáforo quando encontrar dados reais.
        readOfferByScreenshot()
    }

    private fun calculateAndShow(offer: OfferTextParser.ParsedOffer, anchor: Rect?) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val result = TripCalculator.calculate(
            offer.fare, offer.pickupKm, offer.tripKm, offer.minutes,
            prefs.getFloat("min_per_km", 1.80f).toDouble(),
            prefs.getFloat("min_per_hour", 35f).toDouble()
        ) ?: run {
            hideOverlay()
            return
        }
        showOffer(result, offer.passengerRating, anchor)
    }

    private fun readOfferByScreenshot() {
        if (Build.VERSION.SDK_INT < 30) { hideOverlay(); return }
        val now = SystemClock.elapsedRealtime()
        if (now - lastOcrAt < 1500L) return
        lastOcrAt = now
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bmp = try { Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { null }
                result.hardwareBuffer.close()
                if (bmp == null) { showDiagnostic("FALHA AO CAPTURAR TELA"); return }
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { vision ->
                        val parsed = OfferTextParser.parse(vision.text)
                        if (parsed == null) { hideOverlay() }
                        else {
                            val pref = getSharedPreferences("settings", Context.MODE_PRIVATE)
                            calculateAndShow(parsed, null)
                        }
                    }
                    .addOnFailureListener { showDiagnostic("ERRO NO OCR DA OFERTA") }
                    .addOnCompleteListener { bmp.recycle() }
            }
            override fun onFailure(errorCode: Int) { showDiagnostic("CAPTURA NÃO AUTORIZADA: " + errorCode) }
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
        view.background = GradientDrawable().apply {
            setColor(Color.rgb(24, 24, 26))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(2), Color.rgb(218, 166, 67))
        }
        view.text = message
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

    private fun ensureOverlay(anchor: Rect?): TextView? {
        if (!Settings.canDrawOverlays(this)) return null
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val scale = getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("overlay_scale", 85).coerceIn(65, 110) / 100f
        val overlayWidth = (310 * scale).toInt().coerceIn(220, 340)
        if (overlay == null) {
            val view = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 13f * scale
                setTypeface(null, Typeface.BOLD)
                setPadding((13 * scale).toInt(), (10 * scale).toInt(), (13 * scale).toInt(), (10 * scale).toInt())
                maxLines = 5
                background = GradientDrawable().apply {
                    setColor(Color.rgb(18, 18, 20))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(2), Color.rgb(218, 166, 67))
                }
                elevation = dp(8).toFloat()
            }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                dp(overlayWidth), ViewGroup.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.x = getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("offer_tag_x", dp(12)).coerceAtLeast(0)
            params.y = getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("offer_tag_y", dp(150)).coerceAtLeast(0)
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
                        startX = params.x
                        startY = params.y
                        moved = false
                        closeTap = event.x >= v.width - dp(78) && event.y <= dp(44)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                        if (moved) {
                            params.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - v.width).coerceAtLeast(0))
                            params.y = (startY + dy).coerceIn(0, (resources.displayMetrics.heightPixels - v.height).coerceAtLeast(0))
                            try { wm.updateViewLayout(v, params) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                                .putInt("offer_tag_x", params.x).putInt("offer_tag_y", params.y).apply()
                        } else {
                            hideOverlay()
                        }
                        true
                    }
                    else -> true
                }
            }
            wm.addView(view, params)
            overlay = view
            overlayParams = params
        }
        val params = overlayParams
        if (anchor != null && params != null) {
            val screenWidth = resources.displayMetrics.widthPixels
            params.width = dp(overlayWidth)
            overlay?.textSize = 13f * scale
            overlay?.setPadding((13 * scale).toInt(), (10 * scale).toInt(), (13 * scale).toInt(), (10 * scale).toInt())
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (!prefs.contains("offer_tag_x")) {
                params.x = anchor.left.coerceIn(0, (screenWidth - dp(overlayWidth)).coerceAtLeast(0))
                params.y = (anchor.top - dp(118)).coerceAtLeast(dp(55))
            }
            try { wm.updateViewLayout(overlay, params) } catch (_: Exception) {}
        }
        return overlay
    }

    private fun showOffer(r: TripResult, passengerRating: String?, anchor: Rect?) {
        val (label, color) = when (r.rating) {
            Rating.GOOD -> Pair("VERDE  •  CORRIDA BOA", Color.rgb(25, 145, 76))
            Rating.REVIEW -> Pair("AMARELO  •  ANALISAR", Color.rgb(205, 132, 0))
            Rating.BAD -> Pair("VERMELHO  •  CORRIDA RUIM", Color.rgb(190, 40, 40))
        }
        val rating = passengerRating ?: "Não disponível"
        val view = ensureOverlay(anchor) ?: return
        val accent = GradientDrawable().apply {
            setColor(Color.rgb(18, 18, 20))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(3), color)
        }
        view.background = accent
        view.text = "× FECHAR   •   $label\nR$ %.2f  •  R$ %.2f/km\n%.0f min  •  R$ %.2f/h  •  Nota %s".format(
            r.fare, r.perKm, r.minutes, r.perHour, rating
        )
        view.visibility = View.VISIBLE
        dismissOverlayTask?.let { overlayHandler.removeCallbacks(it) }
        val dismiss = Runnable { hideOverlay() }
        dismissOverlayTask = dismiss
        overlayHandler.postDelayed(dismiss, 3000L)
    }

    private fun hideOverlay() {
        dismissOverlayTask?.let { overlayHandler.removeCallbacks(it) }
        dismissOverlayTask = null
        overlay?.visibility = View.GONE
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { hideOverlay() }

    override fun onDestroy() {
        overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        overlay = null
        overlayParams = null
        bubble?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        bubble = null
        try { recognizer.close() } catch (_: Exception) { }
        super.onDestroy()
    }
}

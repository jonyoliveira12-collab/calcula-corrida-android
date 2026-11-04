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
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect

class OfferAccessibilityService : AccessibilityService() {
    private var overlay: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lastText = ""
    private var lastPackage = ""
    private var bubble: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Settings.canDrawOverlays(this) && bubble == null) {
            val v = TextView(this).apply {
                text = "CC"
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(235, 91, 35)); setStroke(dp(2), Color.WHITE) }
            }
            val t = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val p = WindowManager.LayoutParams(dp(46), dp(46), t, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            p.gravity = Gravity.TOP or Gravity.RIGHT
            p.x = dp(8); p.y = dp(220)
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
        if (!isSupportedDriverApp(pkg)) {
            if (lastPackage.isNotEmpty()) hideOverlay()
            lastPackage = ""
            return
        }
        lastPackage = pkg
        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text)
        val screenText = text.toString()
        if (screenText.isBlank()) return
        if (screenText == lastText) return
        lastText = screenText

        val offer = OfferTextParser.parse(screenText)
        if (offer == null) {
            hideOverlay()
            return
        }
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val result = TripCalculator.calculate(
            offer.fare, offer.pickupKm, offer.tripKm, offer.minutes,
            prefs.getFloat("min_per_km", 1.80f).toDouble(),
            prefs.getFloat("min_per_hour", 35f).toDouble()
        ) ?: return
        val anchor = findFareBounds(root)
        showOffer(result, offer.passengerRating, anchor)
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
                    setStroke(dp(2), Color.rgb(235, 91, 35))
                }
                elevation = dp(8).toFloat()
            }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                dp(overlayWidth), ViewGroup.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.x = dp(12)
            params.y = dp(150)
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
            val desiredX = anchor.left.coerceIn(0, (screenWidth - dp(overlayWidth)).coerceAtLeast(0))
            params.x = desiredX
            params.y = (anchor.top - dp(118)).coerceAtLeast(dp(55))
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
        view.text = "$label\nR$ %.2f  •  R$ %.2f/km\n%.0f min  •  R$ %.2f/h  •  Nota %s".format(
            r.fare, r.perKm, r.minutes, r.perHour, rating
        )
        view.visibility = View.VISIBLE
    }

    private fun hideOverlay() { overlay?.visibility = View.GONE }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { hideOverlay() }

    override fun onDestroy() {
        overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        overlay = null
        overlayParams = null
        bubble?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        bubble = null
        super.onDestroy()
    }
}

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

class OfferAccessibilityService : AccessibilityService() {
    private var overlay: TextView? = null
    private var lastText = ""
    private var lastPackage = ""

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
        val root = rootInActiveWindow
        if (root == null) {
            showMessage("APP DETECTADO • AGUARDANDO OFERTA", Color.rgb(190, 90, 20))
            return
        }
        val text = StringBuilder()
        collectText(root, text)
        val screenText = text.toString()
        if (screenText.isBlank()) {
            showMessage("APP DETECTADO • TEXTO DA OFERTA NÃO DISPONÍVEL", Color.rgb(170, 75, 25))
            return
        }
        if (screenText == lastText) return
        lastText = screenText

        val offer = OfferTextParser.parse(screenText)
        if (offer == null) {
            showMessage("99/UBER DETECTADO • NÃO FOI POSSÍVEL LER TODOS OS DADOS", Color.rgb(170, 75, 25))
            return
        }
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val result = TripCalculator.calculate(
            offer.fare, offer.pickupKm, offer.tripKm, offer.minutes,
            prefs.getFloat("min_per_km", 1.80f).toDouble(),
            prefs.getFloat("min_per_hour", 35f).toDouble()
        ) ?: return
        showOffer(result, offer.passengerRating)
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

    private fun ensureOverlay(): TextView? {
        if (!Settings.canDrawOverlays(this)) return null
        overlay?.let { return it }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(24, 18, 24, 18)
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 90
        wm.addView(view, params)
        overlay = view
        return view
    }

    private fun showMessage(message: String, color: Int) {
        val view = ensureOverlay() ?: return
        view.setBackgroundColor(color)
        view.text = message
        view.visibility = View.VISIBLE
    }

    private fun showOffer(r: TripResult, passengerRating: String?) {
        val (label, color) = when (r.rating) {
            Rating.GOOD -> Pair("VERDE • CORRIDA BOA", Color.rgb(20, 130, 70))
            Rating.REVIEW -> Pair("AMARELO • ANALISAR", Color.rgb(205, 130, 0))
            Rating.BAD -> Pair("VERMELHO • CORRIDA RUIM", Color.rgb(190, 40, 40))
        }
        val ratingLine = passengerRating?.let { "Nota do passageiro: $it" } ?: "Nota do passageiro: não exibida na oferta"
        val view = ensureOverlay() ?: return
        view.setBackgroundColor(color)
        view.text = "$label\nValor: R$ %.2f  •  R$ %.2f/km\nTempo: %.0f min  •  R$ %.2f/h\n$ratingLine".format(r.fare, r.perKm, r.minutes, r.perHour)
        view.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        overlay?.visibility = View.GONE
    }

    override fun onInterrupt() { hideOverlay() }

    override fun onDestroy() {
        overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        overlay = null
        super.onDestroy()
    }
}

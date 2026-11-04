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
    private val allowedPackages = setOf("com.ubercab", "com.didiglobal.passenger", "com.app99")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in allowedPackages) { hideOverlay(); return }
        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root,text)
        val screenText = text.toString()
        if (screenText == lastText || screenText.isBlank()) return
        lastText = screenText
        val offer = OfferTextParser.parse(screenText) ?: run { hideOverlay(); return }
        val prefs = getSharedPreferences("settings",Context.MODE_PRIVATE)
        val result = TripCalculator.calculate(offer.fare,offer.pickupKm,offer.tripKm,offer.minutes,prefs.getFloat("min_per_km",1.80f).toDouble(),prefs.getFloat("min_per_hour",35f).toDouble()) ?: return
        showOverlay(result)
    }
    private fun collectText(node: AccessibilityNodeInfo,out: StringBuilder) {
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it,out); it.recycle() }
    }
    private fun showOverlay(r: TripResult) {
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val (label,color) = when(r.rating) {
            Rating.GOOD -> Pair("VERDE • CORRIDA BOA",Color.rgb(20,130,70))
            Rating.REVIEW -> Pair("AMARELO • ANALISAR",Color.rgb(190,125,0))
            Rating.BAD -> Pair("VERMELHO • CORRIDA RUIM",Color.rgb(190,40,40))
        }
        val view = overlay ?: TextView(this).also {
            it.setTextColor(Color.WHITE); it.textSize=14f; it.setTypeface(null,Typeface.BOLD); it.setPadding(24,18,24,18)
            val type = if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT)
            params.gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL; params.y=110
            wm.addView(it,params); overlay=it
        }
        view.setBackgroundColor(color)
        view.text = "$label\nR$ %.2f • %.2f km • %.0f min\nR$ %.2f/km • R$ %.2f/h".format(r.fare,r.totalKm,r.minutes,r.perKm,r.perHour)
        view.visibility=View.VISIBLE
    }
    private fun hideOverlay(){ overlay?.visibility=View.GONE }
    override fun onInterrupt(){ hideOverlay() }
    override fun onDestroy(){ overlay?.let{(getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)}; overlay=null; super.onDestroy() }
}

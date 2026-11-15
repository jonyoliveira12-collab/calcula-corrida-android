package br.com.calculacorrida

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View

/** App appearance is independent of floating-card appearance and reader preferences. */
object AppAppearance {
    data class Palette(val light: Boolean, val background: Int, val panel: Int, val input: Int,
        val text: Int, val muted: Int, val gold: Int, val border: Int, val green: Int, val red: Int)
    fun isLight(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("light",false)
    fun setLight(context: Context, value: Boolean) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("light",value).apply() }
    fun palette(context: Context): Palette = if (isLight(context)) Palette(true,
        Color.rgb(245,246,249),Color.WHITE,Color.rgb(236,239,244),Color.rgb(24,30,43),Color.rgb(88,97,114),
        Color.rgb(126,88,12),Color.rgb(216,202,166),Color.rgb(0,119,82),Color.rgb(180,39,61))
    else Palette(false,Color.rgb(10,12,17),Color.rgb(19,23,32),Color.rgb(30,35,46),Color.rgb(246,247,251),
        Color.rgb(160,171,190),Color.rgb(239,190,65),Color.rgb(104,87,49),Color.rgb(63,211,151),Color.rgb(244,108,126))
    fun apply(activity: Activity) {
        activity.setTheme(if(isLight(activity)) R.style.AppLightTheme else R.style.AppTheme)
    }
    @Suppress("DEPRECATION") fun styleWindow(activity: Activity) {
        val p = palette(activity)
        activity.window.statusBarColor=p.background;activity.window.navigationBarColor=p.background
        activity.window.decorView.systemUiVisibility=if(p.light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
    }
}

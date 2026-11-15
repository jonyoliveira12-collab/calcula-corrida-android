package br.com.calculacorrida

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View

/** App appearance is independent of floating-card appearance and reader preferences. */
object AppAppearance {
    data class Palette(val light: Boolean, val background: Int, val panel: Int, val input: Int,
        val text: Int, val muted: Int, val gold: Int, val border: Int, val green: Int, val red: Int, val theme:String="dark")
    fun holidayAvailable(now: java.util.Calendar=java.util.Calendar.getInstance()):Boolean {
        val end=java.util.Calendar.getInstance().apply {clear();set(2027,java.util.Calendar.JANUARY,7)}
        return now.timeInMillis<end.timeInMillis
    }
    fun theme(context:Context):String {
        val prefs=context.getSharedPreferences("appearance",Context.MODE_PRIVATE)
        val chosen=prefs.getString("theme",null) ?: if(prefs.getBoolean("light",false))"light" else "dark"
        if(chosen=="holiday" && !holidayAvailable()) {prefs.edit().putString("theme","gray").apply();return "gray"}
        return chosen.takeIf{it in listOf("dark","gray","light","holiday")} ?: "dark"
    }
    fun options():List<Pair<String,String>> = listOf("dark" to "Escuro","gray" to "Cinza","light" to "Claro") + if(holidayAvailable())listOf("holiday" to "Natal e Ano-Novo") else emptyList()
    fun setTheme(context:Context,value:String) {require(value in options().map{it.first});context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString("theme",value).putBoolean("light",value=="light").apply()}
    fun isLight(context:Context)=theme(context)=="light"
    fun setLight(context:Context,value:Boolean)=setTheme(context,if(value)"light" else "dark")
    fun palette(context:Context):Palette {
        val theme=theme(context)
        return when(theme) {
            "light" -> Palette(true,Color.rgb(245,246,249),Color.WHITE,Color.rgb(236,239,244),Color.rgb(24,30,43),Color.rgb(88,97,114),Color.rgb(126,88,12),Color.rgb(216,202,166),Color.rgb(0,119,82),Color.rgb(180,39,61),theme)
            "gray" -> Palette(false,Color.rgb(66,72,82),Color.rgb(80,87,99),Color.rgb(58,64,74),Color.rgb(252,252,255),Color.rgb(225,230,239),Color.rgb(255,218,118),Color.rgb(139,146,159),Color.rgb(130,245,188),Color.rgb(255,178,187),theme)
            "holiday" -> Palette(false,Color.rgb(35,42,41),Color.rgb(43,54,49),Color.rgb(57,43,46),Color.rgb(255,248,232),Color.rgb(222,225,206),Color.rgb(255,218,128),Color.rgb(160,126,58),Color.rgb(116,233,157),Color.rgb(255,162,173),theme)
            else -> Palette(false,Color.rgb(10,12,17),Color.rgb(19,23,32),Color.rgb(30,35,46),Color.rgb(246,247,251),Color.rgb(160,171,190),Color.rgb(239,190,65),Color.rgb(104,87,49),Color.rgb(63,211,151),Color.rgb(244,108,126),theme)
        }
    }
    fun apply(activity: Activity) {
        activity.setTheme(if(isLight(activity)) R.style.AppLightTheme else R.style.AppTheme)
    }
    @Suppress("DEPRECATION") fun styleWindow(activity: Activity) {
        val p = palette(activity)
        activity.window.statusBarColor=p.background;activity.window.navigationBarColor=p.background
        activity.window.decorView.systemUiVisibility=if(p.light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
    }
}

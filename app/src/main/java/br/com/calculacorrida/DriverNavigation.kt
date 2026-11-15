package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Same destinations and selection in the dashboard and the separate Friends screen. */
object DriverNavigation {
    const val DESTINATION = "driver_destination"
    fun fill(activity: Activity, bar: LinearLayout, selected: String, onDashboardTab: ((String)->Unit)? = null) {
        val colors=AppAppearance.palette(activity)
        fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
        bar.removeAllViews()
        bar.orientation=LinearLayout.HORIZONTAL
        bar.gravity=Gravity.CENTER
        bar.setPadding(dp(3),dp(5),dp(3),dp(5))
        bar.background=GradientDrawable().apply {setColor(colors.panel);cornerRadius=dp(19).toFloat();setStroke(dp(1),colors.border)}
        for((key,icon,title) in listOf(
            Triple("Início","⌂","Início"),Triple("Finanças","▥","Relatórios"),
            Triple("Semáforo","◎","Metas"),Triple("Histórico","↶","Histórico"),
            Triple("Amigos","♧","Amigos"),Triple("Perfil","⚙","Configurações")
        )) {
            val active=selected==key
            val cell=LinearLayout(activity).apply {
                orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(2),dp(4),dp(2),dp(4))
                isClickable=true;isFocusable=true;isSelected=active;contentDescription=title
                if(active) background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(255,232,139),Color.rgb(239,190,65),Color.rgb(213,151,18))).apply{cornerRadius=dp(16).toFloat()}
                setOnClickListener {
                    if(key=="Amigos") {
                        if(selected!="Amigos") activity.startActivity(Intent(activity,FriendsActivity::class.java))
                    } else if(onDashboardTab!=null) onDashboardTab(key)
                    else activity.startActivity(Intent(activity,MainActivity::class.java)
                        .putExtra(DESTINATION,key).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            }
            cell.addView(TextView(activity).apply{text=icon;textSize=24f;gravity=Gravity.CENTER;setTextColor(if(active)Color.BLACK else colors.gold)})
            cell.addView(TextView(activity).apply{
                text=title;gravity=Gravity.CENTER;maxLines=1;setTextColor(if(active)Color.BLACK else colors.text)
                typeface=if(active)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setAutoSizeTextTypeUniformWithConfiguration(7,if(activity.resources.configuration.screenWidthDp>=600)12 else 9,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            },LinearLayout.LayoutParams(-1,dp(19)))
            bar.addView(cell,LinearLayout.LayoutParams(0,-1,1f))
        }
    }
}

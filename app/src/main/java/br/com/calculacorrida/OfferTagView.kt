package br.com.calculacorrida

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Compact automatic metrics. AI never alters the local calculation. */
class OfferTagView(context: Context) : LinearLayout(context) {
    private val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
    private val banner=line(13f,Color.WHITE,true)
    private val body=LinearLayout(context)
    private val summary=line(12f,Color.BLACK,true)
    private val metrics=LinearLayout(context)
    private val details=line(11f,Color.DKGRAY)
    private val ai=line(11f,Color.DKGRAY)
    init { orientation=VERTICAL;clipToPadding=false; body.orientation=VERTICAL;body.setPadding(dp(9),dp(5),dp(9),dp(6));metrics.orientation=HORIZONTAL }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun line(size:Float,color:Int,bold:Boolean=false)=TextView(context).apply {
        textSize=size;setTextColor(color);if(bold) typeface=Typeface.DEFAULT_BOLD
    }
    private fun amount(n:Double)=String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n)
    private fun duration(n:Double)=if(n%1.0==0.0) "${n.toInt()} min" else String.format(Locale.forLanguageTag("pt-BR"),"%.1f min",n)
    private fun rounded(fill:Int,stroke:Int)=GradientDrawable().apply {setColor(fill);cornerRadius=dp(10).toFloat();setStroke(dp(1),stroke)}
    fun setOffer(label:String,minutes:Double,totalKm:Double,valuePerKm:Double,valuePerHour:Double,rating:String?,color:Int,source:String="APP",fareValue:Double?=null,pickupMinutes:Double?=null,pickupKm:Double?=null) {
        removeAllViews();body.removeAllViews();metrics.removeAllViews()
        val theme=prefs.getString("card_theme","light")
        val dark=theme=="dark" || theme=="colored"
        val fg=if(dark) Color.WHITE else Color.rgb(22,24,27)
        val muted=if(dark) Color.LTGRAY else Color.rgb(112,116,123)
        val fill=when(theme){"colored"->color;"dark"->Color.rgb(20,22,27);else->Color.WHITE}
        banner.text=label+(if(prefs.getBoolean("card_reason",true)) "  • "+when {
            pickupMinutes!=null && pickupMinutes>=10 -> "Embarque (${duration(pickupMinutes)})"
            label=="RECUSAR" -> "Abaixo dos seus limites"
            label=="ACEITAR" -> "Dentro dos seus limites"
            else -> "Confira os indicadores"
        } else "")
        banner.background=rounded(color,color);banner.setPadding(dp(10),dp(6),dp(8),dp(6))
        summary.text="$source  ${duration(minutes)} • ${amount(totalKm)} km"+(fareValue?.let{" • R$ ${amount(it)}"}?:"")
        summary.setTextColor(fg)
        val header=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(summary,LayoutParams(0,-2,1f))
        header.addView(line(19f,muted,true).apply{text="×";gravity=Gravity.CENTER;contentDescription="Fechar análise da oferta"},LayoutParams(dp(25),dp(25)))
        body.addView(header)
        fun metric(key:String,title:String,value:String) {
            if(!prefs.getBoolean(key,true)) return
            val col=LinearLayout(context).apply {orientation=VERTICAL;setPadding(dp(3),dp(3),dp(3),0)}
            col.addView(line(11f,muted).apply{text=title})
            col.addView(line(30f,fg,true).apply{text=value;maxLines=1;setAutoSizeTextTypeUniformWithConfiguration(16,30,1,android.util.TypedValue.COMPLEX_UNIT_SP)})
            col.addView(View(context).apply{setBackgroundColor(if(theme=="colored")Color.WHITE else color)},LayoutParams(-1,dp(2)))
            metrics.addView(col,LayoutParams(0,-2,1f))
        }
        metric("card_km","R$/Km",amount(valuePerKm))
        metric("card_hour","R$/Hora",amount(valuePerHour))
        metric("card_rating","Nota",rating?.replace('.',',')?:"—")
        body.addView(metrics)
        details.setTextColor(muted)
        details.text="Embarque: "+(pickupMinutes?.let(::duration)?:"—")+(pickupKm?.let{" • ${amount(it)} km"}?:"")
        if(prefs.getBoolean("card_details",false))body.addView(details)
        ai.setTextColor(muted);ai.maxLines=2
        if(prefs.getBoolean("card_ai",false))body.addView(ai)
        body.background=rounded(fill,color)
        val above=prefs.getString("card_banner_position","above")=="above"
        fun addBanner(){if(prefs.getBoolean("card_banner",true))addView(banner,LayoutParams(-1,-2).apply{bottomMargin=dp(4);topMargin=dp(4)})}
        if(above)addBanner();addView(body,LayoutParams(-1,-2));if(!above)addBanner()
        contentDescription="$source • $label. R$ ${fareValue?.let(::amount)}. Até passageiro: ${pickupMinutes?.let(::duration)}. Total: ${duration(minutes)} • ${amount(totalKm)} km. Nota ${rating?.replace('.',',')?:"não identificada"}. R$ ${amount(valuePerKm)}/km. R$ ${amount(valuePerHour)}/h"
    }
    fun isCloseHit(x:Float,y:Float):Boolean = x >= width-dp(36) && y >= body.top && y <= body.top+dp(30)
    fun setAiInsight(message:String){ai.text=message.take(140)}
    fun setDiagnostic(message:String){banner.text=message}
}

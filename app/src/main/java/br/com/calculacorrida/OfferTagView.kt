package br.com.calculacorrida

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Automatic offer card. Values come from the local calculator; AI cannot change them. */
class OfferTagView(context: Context) : LinearLayout(context) {
    private val gold=Color.rgb(255,218,108)
    private val title=line(12f,gold,true)
    private val fare=line(21f,Color.WHITE,true)
    private val arrival=line(13f,Color.WHITE)
    private val journey=line(13f,Color.WHITE)
    private val total=line(13f,Color.WHITE,true)
    private val rates=line(18f,gold,true)
    private val ai=line(11f,Color.LTGRAY)
    init {
        orientation=VERTICAL
        setPadding(dp(12),dp(8),dp(12),dp(8))
        val header=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        header.addView(title,LayoutParams(0,dp(30),1f))
        header.addView(line(24f,gold,true).apply { text="×";gravity=Gravity.CENTER;contentDescription="Fechar análise da oferta" },LayoutParams(dp(30),dp(30)))
        addView(header)
        listOf(fare,arrival,journey,total,rates,ai).forEach { addView(it,LayoutParams(-1,-2)) }
        ai.maxLines=2
        setCardColor(gold)
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun line(size:Float,color:Int,bold:Boolean=false)=TextView(context).apply {
        textSize=size;setTextColor(color)
        if(bold) typeface=Typeface.DEFAULT_BOLD
        setPadding(0,dp(2),0,dp(2))
    }
    private fun amount(n:Double)=String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n)
    private fun duration(n:Double)=if(n%1.0==0.0) "${n.toInt()} min" else String.format(Locale.forLanguageTag("pt-BR"),"%.1f min",n)
    private fun setCardColor(color:Int) {
        background=GradientDrawable().apply { setColor(Color.rgb(16,16,19));cornerRadius=dp(16).toFloat();setStroke(dp(2),color) }
    }
    fun setOffer(label:String,minutes:Double,totalKm:Double,valuePerKm:Double,valuePerHour:Double,rating:String?,color:Int,source:String="APP",fareValue:Double?=null,pickupMinutes:Double?=null,pickupKm:Double?=null) {
        title.text="$source • $label"
        fare.text=(fareValue?.let { "R$ ${amount(it)}" } ?: "Valor não identificado")+"  •  Nota "+(rating?.replace('.',',') ?: "não identificada")
        fare.textSize=if(rating==null) 16f else 19f
        arrival.text="Até passageiro: "+(pickupMinutes?.let(::duration) ?: "tempo não identificado")+(pickupKm?.let { " • ${amount(it)} km" } ?: "")
        journey.text="Viagem: "+(pickupMinutes?.let { duration((minutes-it).coerceAtLeast(0.0)) } ?: "tempo não separado")
        total.text="Total: ${duration(minutes)} • ${amount(totalKm)} km"
        rates.text="R$ ${amount(valuePerKm)}/km   R$ ${amount(valuePerHour)}/h"
        setCardColor(color)
        contentDescription=listOf(title.text,fare.text,arrival.text,journey.text,total.text,rates.text).joinToString(". ")
    }
    fun setAiInsight(message:String) { ai.text=message.take(140) }
    fun setDiagnostic(message:String) { title.text=message }
}

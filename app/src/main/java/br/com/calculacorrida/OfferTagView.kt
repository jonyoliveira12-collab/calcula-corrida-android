package br.com.calculacorrida

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
    private val cardScale get()=prefs.getInt("overlay_scale",100).coerceIn(60,160)/100f
    private val banner=line(13f,Color.WHITE,true)
    private val body=LinearLayout(context)
    private val header=LinearLayout(context)
    private val summary=line(12f,Color.BLACK,true)
    private val metrics=LinearLayout(context)
    private val details=line(11f,Color.DKGRAY)
    private val ai=line(11f,Color.DKGRAY)
    private val fireBorder = RealisticFireBorder(context)
    private var neonEnabled=true
    private var neonColor=Color.RED
    private var flameFrame=0
    private val flameTick=object:Runnable {
        override fun run() {
            if(!neonEnabled || !prefs.getBoolean("card_neon_animated",true) || !isAttachedToWindow || !isShown || windowVisibility!=View.VISIBLE)return
            val power=context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if(power?.isInteractive != false) {
                flameFrame=(flameFrame+1)%48
                postInvalidateOnAnimation()
                postDelayed(this,60L)
            } else postDelayed(this,2000L)
        }
    }
    init { orientation=VERTICAL;clipToPadding=false;clipChildren=false;setChildrenDrawingOrderEnabled(true);body.orientation=VERTICAL;metrics.orientation=HORIZONTAL }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun line(size:Float,color:Int,bold:Boolean=false)=TextView(context).apply {
        textSize=size*cardScale;setTextColor(color);if(bold) typeface=Typeface.DEFAULT_BOLD
    }
    private fun amount(n:Double)=String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n)
    private fun duration(n:Double)=if(n%1.0==0.0) "${n.toInt()} min" else String.format(Locale.forLanguageTag("pt-BR"),"%.1f min",n)
    private fun rounded(fill:Int,stroke:Int)=GradientDrawable().apply {setColor(fill);cornerRadius=dp(10).toFloat();setStroke(dp(1),stroke)}
    fun setOffer(label:String,minutes:Double,totalKm:Double,valuePerKm:Double,valuePerHour:Double,rating:String?,color:Int,source:String="APP",fareValue:Double?=null,pickupMinutes:Double?=null,pickupKm:Double?=null,reason:String?=null) {
        removeAllViews();body.removeAllViews();header.removeAllViews();metrics.removeAllViews()
        neonEnabled=prefs.getBoolean("card_neon_flames",true)
        neonColor=when(label) {
            "ACEITAR","BOA CORRIDA","BOA" -> Color.rgb(0,200,88)
            "ATENÇÃO","ANALISAR" -> Color.rgb(255,170,0)
            else -> Color.rgb(245,38,55)
        }
        // Reserve real space for the glow so it is not clipped by the overlay window.
        if(neonEnabled) setPadding(dp(14),dp(32),dp(14),dp(12)) else setPadding(0,0,0,0)
        val above=prefs.getString("card_banner_position","above")=="above"
        val hasBanner=prefs.getBoolean("card_banner",true)
        body.setPadding(dp(9),dp(if(neonEnabled && above && hasBanner)12 else 6),dp(9),dp(if(neonEnabled)28 else 6))
        val theme=prefs.getString("card_theme","light")
        val dark=theme=="dark" || theme=="colored"
        val fg=if(dark) Color.WHITE else Color.rgb(22,24,27)
        val muted=if(dark) Color.LTGRAY else Color.rgb(112,116,123)
        val fill=when(theme){"colored"->color;"dark"->Color.rgb(20,22,27);else->Color.WHITE}
        // The classification remains the canonical value; only its visible name
        // is editable. Colors, thresholds and calculations must never depend on it.
        val (nameKey,defaultName)=when(label) {
            "ACEITAR","BOA CORRIDA","BOA" -> "label_good" to "Bora carai"
            "ATENÇÃO","ANALISAR" -> "label_review" to "Será?"
            else -> "label_bad" to "Vixe não dá"
        }
        val shownLabel=prefs.getString(nameKey,defaultName)?.trim().takeUnless{it.isNullOrEmpty()} ?: defaultName
        banner.text=(if(neonEnabled) "" else "● ")+shownLabel+(if(prefs.getBoolean("card_compact_reason",false)) "  • "+(reason?.trim()?.trimEnd('.') ?: when {
            pickupMinutes!=null && pickupMinutes>=10 -> "Embarque (${duration(pickupMinutes)})"
            label=="RECUSAR" -> "Abaixo dos seus limites"
            label=="ACEITAR" -> "Dentro dos seus limites"
            else -> "Confira os indicadores"
        }) else "")
        val badgeColor=when(label){"ACEITAR","BOA CORRIDA","BOA"->Color.rgb(20,125,69);"ATENÇÃO","ANALISAR"->Color.rgb(243,182,66);else->Color.rgb(201,47,63)}
        val badgeText=if(label=="ATENÇÃO" || label=="ANALISAR")Color.rgb(30,24,8) else Color.WHITE
        banner.setTextColor(badgeText)
        banner.textSize=13f*cardScale
        banner.maxLines=if(prefs.getBoolean("card_compact_reason",false))2 else 1
        banner.background=if(neonEnabled) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(neonColor,badgeColor)).apply {
            cornerRadius=dp(18).toFloat();setStroke(dp(1),neonColor)
        } else rounded(badgeColor,badgeColor)
        val flame=if(neonEnabled) context.getDrawable(R.drawable.ic_flame_outline)?.mutate()?.apply {
            setTint(badgeText);setBounds(0,0,dp(17),dp(17))
        } else null
        banner.setCompoundDrawables(flame,null,null,null)
        banner.compoundDrawablePadding=if(neonEnabled)dp(5) else 0
        banner.setPadding(dp(9),dp(4),dp(10),dp(4))
        banner.elevation=if(neonEnabled)dp(3).toFloat() else 0f
        summary.text="$source  "+(if(prefs.getBoolean("show_time",true))"${duration(minutes)} • " else "")+"${amount(totalKm)} km"+(fareValue?.let{" • R$ ${amount(it)}"}?:"")
        summary.setTextColor(fg)
        summary.textSize=12f*cardScale
        details.textSize=11f*cardScale
        ai.textSize=11f*cardScale
        header.gravity=Gravity.CENTER_VERTICAL
        header.addView(summary,LayoutParams(0,-2,1f))
        header.addView(line(19f,muted,true).apply{text="×";gravity=Gravity.CENTER;contentDescription="Fechar análise da oferta"},LayoutParams(dp(25),dp(25)))
        body.addView(header)
        fun metric(key:String,title:String,value:String,indicator:Int) {
            if(!prefs.getBoolean(key,true) || (key=="card_rating" && !prefs.getBoolean("show_rating",true))) return
            val col=LinearLayout(context).apply {orientation=VERTICAL;setPadding(dp(3),dp(3),dp(3),0)}
            col.addView(line(11f,muted).apply{text=title})
            col.addView(line(30f,fg,true).apply{text=value;maxLines=1;setAutoSizeTextTypeUniformWithConfiguration((16*cardScale).toInt().coerceAtLeast(9),(30*cardScale).toInt().coerceAtLeast(10),1,android.util.TypedValue.COMPLEX_UNIT_SP)})
            col.addView(View(context).apply{setBackgroundColor(if(theme=="colored")Color.WHITE else indicator)},LayoutParams(-1,dp(2)))
            metrics.addView(col,LayoutParams(0,-2,1f))
        }
        fun indicator(value:Double?,low:String,high:String,lowDefault:Float,highDefault:Float):Int = when {
            value==null -> Color.GRAY
            value < TripCalculator.threshold(prefs.getFloat(low,lowDefault)) -> Color.rgb(209,48,57)
            value >= TripCalculator.threshold(prefs.getFloat(high,highDefault)) -> Color.rgb(0,146,99)
            else -> Color.rgb(217,152,22)
        }
        metric("card_km","R$/Km",amount(valuePerKm),indicator(valuePerKm,"bad_per_km","min_per_km",1.5f,1.8f))
        metric("card_hour","R$/Hora",amount(valuePerHour),indicator(valuePerHour,"min_per_hour","good_per_hour",50f,60f))
        metric("card_rating","Nota",rating?.replace('.',',')?:"—",indicator(rating?.replace(',','.')?.toDoubleOrNull(),"min_rating","good_rating",4.8f,4.9f))
        body.addView(metrics)
        var profitDescription = ""
        if (prefs.getBoolean("show_profit", false) && fareValue != null) {
            OfferProfit.estimate(fareValue, totalKm, TripCalculator.threshold(prefs.getFloat("cost_per_km", .65f)))?.let { estimate ->
                profitDescription = "Margem estimada: ${amount(estimate.percent)}% • R$ ${amount(estimate.amount)}"
                body.addView(line(12f, if (estimate.amount >= 0) fg else Color.rgb(209,48,57), true).apply {
                    text = profitDescription
                    setPadding(dp(3),dp(5),dp(3),0)
                })
            }
        }

        details.setTextColor(muted)
        details.text="Embarque: "+(pickupMinutes?.let(::duration)?:"—")+(pickupKm?.let{" • ${amount(it)} km"}?:"")
        if(prefs.getBoolean("card_details",false))body.addView(details)
        ai.setTextColor(muted);ai.maxLines=2
        if(prefs.getBoolean("card_ai",false))body.addView(ai)
        body.background=rounded(fill,if(neonEnabled)neonColor else color)
        fun addBanner(){if(hasBanner)addView(banner,LayoutParams(-2,-2).apply{gravity=Gravity.START;bottomMargin=if(neonEnabled && above)-dp(7) else dp(4);topMargin=dp(4)})}
        if(above)addBanner();addView(body,LayoutParams(-1,-2));if(!above)addBanner()
        contentDescription="$source • $shownLabel. R$ ${fareValue?.let(::amount)}. Até passageiro: ${pickupMinutes?.let(::duration)}. Total: ${duration(minutes)} • ${amount(totalKm)} km. Nota ${rating?.replace('.',',')?:"não identificada"}. R$ ${amount(valuePerKm)}/km. R$ ${amount(valuePerHour)}/h"
        if(profitDescription.isNotEmpty())contentDescription="$contentDescription. $profitDescription"
        updateFlameAnimation()
        invalidate()
    }

    override fun onAttachedToWindow(){super.onAttachedToWindow();updateFlameAnimation()}
    override fun onDetachedFromWindow(){removeCallbacks(flameTick);super.onDetachedFromWindow()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);updateFlameAnimation()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);updateFlameAnimation()}

    private fun updateFlameAnimation() {
        removeCallbacks(flameTick)
        // The explicit in-app switch controls this custom effect independently of
        // Android's transition/animator scale. Keep it off for a hidden card.
        if(neonEnabled && prefs.getBoolean("card_neon_animated",true) && isAttachedToWindow && isShown && windowVisibility==View.VISIBLE)post(flameTick)
    }

    /** The effect stays around the reserved edge and never covers the metrics. */
    override fun getChildDrawingOrder(childCount:Int,drawingPosition:Int):Int =
        if(childCount==2 && getChildAt(0)===banner) 1-drawingPosition else drawingPosition

    override fun dispatchDraw(canvas:Canvas) {
        if(neonEnabled && body.width>0 && body.height>0) drawNeonBehind(canvas)
        super.dispatchDraw(canvas)
        if(neonEnabled && body.width>0 && body.height>0) drawBottomFlames(canvas)
    }

    private fun drawNeonBehind(canvas: Canvas) = drawRealisticFire(canvas, bottomOnly = false)

    private fun drawBottomFlames(canvas: Canvas) = drawRealisticFire(canvas, bottomOnly = true)

    /** Nine patches leave the card center clear and keep the fire in its reserved border. */
    private fun drawRealisticFire(canvas: Canvas, bottomOnly: Boolean) {
        fireBorder.draw(canvas,
            RectF(body.left.toFloat(), body.top.toFloat(), body.right.toFloat(), body.bottom - dp(20).toFloat()),
            dp(14).toFloat(), dp(32).toFloat(), dp(32).toFloat(), flameFrame,
            green = neonColor == Color.rgb(0, 200, 88), bottomOnly = bottomOnly)
    }

    fun isCloseHit(x:Float,y:Float):Boolean = x >= body.right-dp(40) && x <= body.right+dp(4) && y >= body.top && y <= body.top+dp(35)
    fun setAiInsight(message:String){ai.text=message.take(140)}
    fun setDiagnostic(message:String){banner.text=message}
}

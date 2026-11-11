package br.com.calculacorrida

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private val banner=line(13f,Color.WHITE,true)
    private val body=LinearLayout(context)
    private val header=LinearLayout(context)
    private val summary=line(12f,Color.BLACK,true)
    private val metrics=LinearLayout(context)
    private val details=line(11f,Color.DKGRAY)
    private val ai=line(11f,Color.DKGRAY)
    private val neonPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeCap=Paint.Cap.ROUND
        strokeJoin=Paint.Join.ROUND
    }
    private var neonEnabled=true
    private var neonColor=Color.RED
    init { orientation=VERTICAL;clipToPadding=false;clipChildren=false;setChildrenDrawingOrderEnabled(true);body.orientation=VERTICAL;metrics.orientation=HORIZONTAL }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun line(size:Float,color:Int,bold:Boolean=false)=TextView(context).apply {
        textSize=size;setTextColor(color);if(bold) typeface=Typeface.DEFAULT_BOLD
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
        if(neonEnabled) setPadding(dp(8),dp(13),dp(8),dp(10)) else setPadding(0,0,0,0)
        val above=prefs.getString("card_banner_position","above")=="above"
        val hasBanner=prefs.getBoolean("card_banner",true)
        body.setPadding(dp(9),dp(if(neonEnabled && above && hasBanner)12 else 6),dp(9),dp(if(neonEnabled)15 else 6))
        val theme=prefs.getString("card_theme","light")
        val dark=theme=="dark" || theme=="colored"
        val fg=if(dark) Color.WHITE else Color.rgb(22,24,27)
        val muted=if(dark) Color.LTGRAY else Color.rgb(112,116,123)
        val fill=when(theme){"colored"->color;"dark"->Color.rgb(20,22,27);else->Color.WHITE}
        val shownLabel=when(label){"ACEITAR","BOA CORRIDA"->"BOA";"ATENÇÃO"->"ANALISAR";else->label}
        banner.text=(if(neonEnabled) "" else "● ")+shownLabel+(if(prefs.getBoolean("card_compact_reason",false)) "  • "+(reason?.trim()?.trimEnd('.') ?: when {
            pickupMinutes!=null && pickupMinutes>=10 -> "Embarque (${duration(pickupMinutes)})"
            label=="RECUSAR" -> "Abaixo dos seus limites"
            label=="ACEITAR" -> "Dentro dos seus limites"
            else -> "Confira os indicadores"
        }) else "")
        val badgeColor=when(label){"ACEITAR","BOA CORRIDA","BOA"->Color.rgb(20,125,69);"ATENÇÃO","ANALISAR"->Color.rgb(243,182,66);else->Color.rgb(201,47,63)}
        val badgeText=if(label=="ATENÇÃO" || label=="ANALISAR")Color.rgb(30,24,8) else Color.WHITE
        banner.setTextColor(badgeText)
        banner.textSize=13f
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
        summary.text="$source  ${duration(minutes)} • ${amount(totalKm)} km"+(fareValue?.let{" • R$ ${amount(it)}"}?:"")
        summary.setTextColor(fg)
        header.gravity=Gravity.CENTER_VERTICAL
        header.addView(summary,LayoutParams(0,-2,1f))
        header.addView(line(19f,muted,true).apply{text="×";gravity=Gravity.CENTER;contentDescription="Fechar análise da oferta"},LayoutParams(dp(25),dp(25)))
        body.addView(header)
        fun metric(key:String,title:String,value:String,indicator:Int) {
            if(!prefs.getBoolean(key,true)) return
            val col=LinearLayout(context).apply {orientation=VERTICAL;setPadding(dp(3),dp(3),dp(3),0)}
            col.addView(line(11f,muted).apply{text=title})
            col.addView(line(30f,fg,true).apply{text=value;maxLines=1;setAutoSizeTextTypeUniformWithConfiguration(16,30,1,android.util.TypedValue.COMPLEX_UNIT_SP)})
            col.addView(View(context).apply{setBackgroundColor(if(theme=="colored")Color.WHITE else indicator)},LayoutParams(-1,dp(2)))
            metrics.addView(col,LayoutParams(0,-2,1f))
        }
        fun indicator(value:Double?,low:String,high:String,lowDefault:Float,highDefault:Float):Int = when {
            value==null -> Color.GRAY
            value < prefs.getFloat(low,lowDefault) -> Color.rgb(209,48,57)
            value >= prefs.getFloat(high,highDefault) -> Color.rgb(0,146,99)
            else -> Color.rgb(217,152,22)
        }
        metric("card_km","R$/Km",amount(valuePerKm),indicator(valuePerKm,"bad_per_km","min_per_km",1.5f,1.8f))
        metric("card_hour","R$/Hora",amount(valuePerHour),indicator(valuePerHour,"min_per_hour","good_per_hour",50f,60f))
        metric("card_rating","Nota",rating?.replace('.',',')?:"—",indicator(rating?.replace(',','.')?.toDoubleOrNull(),"min_rating","good_rating",4.8f,4.9f))
        body.addView(metrics)
        details.setTextColor(muted)
        details.text="Embarque: "+(pickupMinutes?.let(::duration)?:"—")+(pickupKm?.let{" • ${amount(it)} km"}?:"")
        if(prefs.getBoolean("card_details",false))body.addView(details)
        ai.setTextColor(muted);ai.maxLines=2
        if(prefs.getBoolean("card_ai",false))body.addView(ai)
        body.background=rounded(fill,if(neonEnabled)neonColor else color)
        fun addBanner(){if(hasBanner)addView(banner,LayoutParams(-2,-2).apply{gravity=Gravity.START;bottomMargin=if(neonEnabled && above)-dp(7) else dp(4);topMargin=dp(4)})}
        if(above)addBanner();addView(body,LayoutParams(-1,-2));if(!above)addBanner()
        contentDescription="$source • $shownLabel. R$ ${fareValue?.let(::amount)}. Até passageiro: ${pickupMinutes?.let(::duration)}. Total: ${duration(minutes)} • ${amount(totalKm)} km. Nota ${rating?.replace('.',',')?:"não identificada"}. R$ ${amount(valuePerKm)}/km. R$ ${amount(valuePerHour)}/h"
        invalidate()
    }
    /** Static, low-cost outlines; the white information area remains unobstructed. */
    override fun getChildDrawingOrder(childCount:Int,drawingPosition:Int):Int =
        if(childCount==2 && getChildAt(0)===banner) 1-drawingPosition else drawingPosition

    override fun dispatchDraw(canvas:Canvas) {
        if(neonEnabled && body.width>0 && body.height>0) drawNeonBehind(canvas)
        super.dispatchDraw(canvas)
        if(neonEnabled && body.width>0 && body.height>0) drawBottomFlames(canvas)
    }

    private fun drawNeonBehind(canvas:Canvas) {
        val box=RectF(body.left.toFloat(),body.top.toFloat(),body.right.toFloat(),body.bottom.toFloat())
        for((width,alpha) in listOf(8 to 32,5 to 58,3 to 115)) {
            neonPaint.color=withAlpha(neonColor,alpha);neonPaint.strokeWidth=dp(width).toFloat()
            canvas.drawRoundRect(box,dp(10).toFloat(),dp(10).toFloat(),neonPaint)
        }
        val x=box.right-dp(8);val y=box.top
        val flame=Path().apply {
            moveTo(x-dp(57),y+dp(1))
            cubicTo(x-dp(44),y+dp(1),x-dp(36),y-dp(2),x-dp(33),y-dp(11))
            cubicTo(x-dp(29),y-dp(4),x-dp(26),y-dp(4),x-dp(22),y-dp(13))
            cubicTo(x-dp(18),y-dp(6),x-dp(13),y-dp(3),x-dp(15),y+dp(1))
            cubicTo(x-dp(10),y-dp(1),x-dp(6),y-dp(4),x-dp(5),y-dp(8))
            cubicTo(x-dp(1),y-dp(4),x,y-dp(1),x,y+dp(5))
        }
        drawFlameLine(canvas,flame)
    }

    private fun drawBottomFlames(canvas:Canvas) {
        val left=body.left+dp(8).toFloat();val right=body.right-dp(8).toFloat()
        val base=body.bottom-dp(3).toFloat();val step=dp(30).toFloat().coerceAtLeast(1f)
        val flame=Path().apply {
            moveTo(left,base)
            var x=left
            while(x<right) {
                val end=(x+step).coerceAtMost(right);val s=end-x
                cubicTo(x+s*.16f,base,x+s*.27f,base-dp(2),x+s*.43f,base-dp(8))
                cubicTo(x+s*.36f,base-dp(2),x+s*.49f,base-dp(1),x+s*.62f,base-dp(2))
                cubicTo(x+s*.79f,base-dp(3),x+s*.88f,base,end,base)
                x=end
            }
        }
        val saved=canvas.save()
        canvas.clipRect(body.left,body.bottom-dp(15),body.right,body.bottom+dp(8))
        drawFlameLine(canvas,flame)
        canvas.restoreToCount(saved)
    }

    private fun drawFlameLine(canvas:Canvas,path:Path) {
        for((width,alpha) in listOf(6 to 50,3 to 115,1 to 255)) {
            neonPaint.color=withAlpha(neonColor,alpha);neonPaint.strokeWidth=dp(width).toFloat().coerceAtLeast(1f)
            canvas.drawPath(path,neonPaint)
        }
    }

    private fun withAlpha(color:Int,alpha:Int)=Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color))
    fun isCloseHit(x:Float,y:Float):Boolean = x >= body.right-dp(40) && x <= body.right+dp(4) && y >= body.top && y <= body.top+dp(35)
    fun setAiInsight(message:String){ai.text=message.take(140)}
    fun setDiagnostic(message:String){banner.text=message}
}

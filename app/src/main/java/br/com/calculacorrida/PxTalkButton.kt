package br.com.calculacorrida

import android.content.Context
import android.graphics.*
import androidx.appcompat.widget.AppCompatButton

/** A real native hold-to-talk control; pending and transmitting have distinct feedback. */
class PxTalkButton(context:Context):AppCompatButton(context) {
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
    var transmitting=false
        set(value){field=value;invalidate()}
    var requesting=false
        set(value){field=value;invalidate()}
    init {background=null;backgroundTintList=null;minWidth=0;minHeight=0;setPadding(0,0,0,0);text="Segure para falar";contentDescription="Segure para falar no rádio PX e solte para parar"}
    override fun onDraw(canvas:Canvas){
        val p=AppAppearance.palette(context);val x=width/2f;val y=height/2f;val r=minOf(width,height)*.42f
        pen.alpha=if(isEnabled)255 else 105;pen.style=Paint.Style.STROKE;pen.strokeWidth=r*.015f;pen.color=if(transmitting)p.green else p.border;canvas.drawCircle(x,y,r*1.15f,pen)
        pen.style=Paint.Style.FILL;pen.shader=LinearGradient(x-r,y-r,x+r,y+r,intArrayOf(Color.rgb(255,230,155),Color.rgb(221,167,54),Color.rgb(244,204,106)),null,Shader.TileMode.CLAMP);canvas.drawCircle(x,y,if(isPressed)r*.96f else r,pen);pen.shader=null
        if(requesting){pen.style=Paint.Style.STROKE;pen.strokeWidth=r*.035f;pen.color=p.gold;val a=(android.os.SystemClock.uptimeMillis()%1200L)/1200f*360f;canvas.drawArc(RectF(x-r*1.14f,y-r*1.14f,x+r*1.14f,y+r*1.14f),a,85f,false,pen);postInvalidateOnAnimation()}
        canvas.save();canvas.translate(x,y);canvas.scale(r/60f,r/60f);pen.color=Color.rgb(35,27,12);pen.style=Paint.Style.FILL;canvas.drawRoundRect(RectF(-10f,-29f,10f,10f),10f,10f,pen)
        pen.style=Paint.Style.STROKE;pen.strokeWidth=4.2f;pen.strokeCap=Paint.Cap.ROUND;val path=Path().apply{moveTo(-20f,-1f);lineTo(-20f,5f);cubicTo(-20f,34f,20f,34f,20f,5f);lineTo(20f,-1f)};canvas.drawPath(path,pen);canvas.drawLine(0f,27f,0f,37f,pen);canvas.drawLine(-12f,37f,12f,37f,pen);canvas.restore();pen.alpha=255
    }
}

/** Voice activity indication, not a fabricated microphone-level measurement. */
class PxVoiceIndicator(context:Context):android.view.View(context){
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
    var active=false
        set(value){field=value;invalidate()}
    init {contentDescription="Atividade do rádio";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    override fun onDraw(canvas:Canvas){
        val d=resources.displayMetrics.density;val p=AppAppearance.palette(context);pen.color=if(active)p.gold else p.border;pen.strokeWidth=2*d;pen.strokeCap=Paint.Cap.ROUND
        val spacing=width/43f;val time=android.os.SystemClock.uptimeMillis()/160.0
        for(i in 1..41){val envelope=1f-kotlin.math.abs(i-21)/22f;val amplitude=if(active)(.2+kotlin.math.abs(kotlin.math.sin(time+i*.63))).toFloat()*envelope*height*.32f else d;val x=i*spacing;canvas.drawLine(x,height/2f-amplitude,x,height/2f+amplitude,pen)}
        if(active)postInvalidateOnAnimation()
    }
}

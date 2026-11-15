package br.com.calculacorrida

import android.content.Context
import android.graphics.*
import android.view.View

/** Static festive trim; no flashing or moving decoration near driving metrics. */
class SeasonalHeaderView(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val h=height.toFloat();val w=width.toFloat()
        paint.color=Color.rgb(224,184,83);paint.style=Paint.Style.STROKE;paint.strokeWidth=resources.displayMetrics.density
        val line=Path().apply {moveTo(0f,h*.25f);cubicTo(w*.3f,h*.8f,w*.7f,h*.8f,w,h*.25f)};canvas.drawPath(line,paint)
        paint.style=Paint.Style.FILL
        paint.color=Color.rgb(255,220,136);paint.textSize=h*.39f;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER
        canvas.drawText("✦  Natal e Ano-Novo  ✦",w*.5f,h*.65f,paint)
        for(side in listOf(0,1)) {
            val start=if(side==0)0f else w
            val direction=if(side==0)1 else -1
            paint.color=Color.rgb(83,130,91);paint.strokeWidth=h*.035f
            for(needle in 0..9) {
                val x=start+direction*needle*h*.28f
                canvas.drawLine(x,h*.15f,x+direction*h*.38f,h*.44f,paint)
                canvas.drawLine(x,h*.15f,x+direction*h*.12f,0f,paint)
            }
        }
        for(i in listOf(0,1,2,8,9,10)) {
            val x=w*i/10;val y=h*(.25f+.4f*kotlin.math.sin(Math.PI*i/10).toFloat())
            paint.color=if(i%3==0)Color.rgb(166,60,66)else Color.rgb(247,217,129)
            canvas.drawCircle(x,y,h*.09f,paint)
        }
    }
}

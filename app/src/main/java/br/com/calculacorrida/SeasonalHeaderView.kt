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
        for(i in 0..10) {
            val x=w*i/10;val y=h*(.25f+.4f*kotlin.math.sin(Math.PI*i/10).toFloat())
            paint.color=if(i%3==0)Color.rgb(166,60,66)else Color.rgb(247,217,129)
            canvas.drawCircle(x,y,h*.09f,paint)
        }
    }
}

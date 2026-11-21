package br.com.calculacorrida

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable

/** Subtle console texture drawn natively, without large image assets. */
class PxConsoleBackground(private val context:Context):Drawable(){
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas:Canvas){
        val p=AppAppearance.palette(context);val d=context.resources.displayMetrics.density;val box=RectF(bounds).apply{inset(d,d)}
        pen.shader=LinearGradient(0f,box.top,box.right,box.bottom,intArrayOf(p.input,p.panel,p.background),null,Shader.TileMode.CLAMP);pen.style=Paint.Style.FILL;canvas.drawRoundRect(box,20*d,20*d,pen);pen.shader=null
        pen.color=p.border;pen.style=Paint.Style.STROKE;pen.strokeWidth=d;canvas.drawRoundRect(box,20*d,20*d,pen)
        pen.style=Paint.Style.FILL;pen.color=if(p.light)0x12000000 else 0x70000000
        val center=box.top+box.height()*.51f
        for(side in 0..1)for(col in 0..2)for(row in -5..5){val x=if(side==0)box.left+(15+col*9)*d else box.right-(15+col*9)*d;canvas.drawCircle(x,center+row*11*d,1.8f*d,pen)}
        for(x in listOf(box.left+13*d,box.right-13*d))for(y in listOf(box.top+13*d,box.bottom-13*d)){pen.color=p.background;canvas.drawCircle(x,y,3.5f*d,pen);pen.color=p.border;pen.strokeWidth=d;canvas.drawLine(x-1.6f*d,y+1.6f*d,x+1.6f*d,y-1.6f*d,pen)}
    }
    override fun setAlpha(alpha:Int){};override fun setColorFilter(filter:ColorFilter?){};@Deprecated("Drawable API")override fun getOpacity()=PixelFormat.TRANSLUCENT
}

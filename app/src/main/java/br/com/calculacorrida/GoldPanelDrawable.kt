package br.com.calculacorrida

import android.graphics.*
import android.graphics.drawable.Drawable

/** Native panel with a restrained gold edge or a stronger offer highlight. */
class GoldPanelDrawable(private val density: Float, private val highlighted: Boolean = false): Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val inset=if(highlighted)6*density else density
        val rect=RectF(bounds.left+inset,bounds.top+inset,bounds.right-inset,bounds.bottom-inset)
        val radius=22*density
        paint.style=Paint.Style.FILL
        paint.shader=LinearGradient(0f,rect.top,0f,rect.bottom,intArrayOf(Color.rgb(24,24,25),Color.rgb(7,8,10)),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,paint)
        paint.shader=null;paint.style=Paint.Style.STROKE
        val layers=if(highlighted)listOf(11f to 20,6f to 65,2f to 255)else listOf(1f to 170)
        for((width,alpha) in layers) {
            paint.color=Color.argb(alpha,255,204,62);paint.strokeWidth=width*density
            canvas.drawRoundRect(rect,radius,radius,paint)
        }
    }
    override fun setAlpha(alpha:Int) { }
    override fun setColorFilter(filter:ColorFilter?) { }
    @Deprecated("Deprecated in Android") override fun getOpacity():Int=PixelFormat.TRANSLUCENT
}

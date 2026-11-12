package br.com.calculacorrida

import android.content.Context
import android.graphics.*

/** Shared transparent fire artwork, leaving the interactive center untouched. */
class RealisticFireBorder(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmap = synchronized(RealisticFireBorder::class.java) {
        sharedTexture ?: BitmapFactory.decodeResource(context.resources, R.drawable.card_realistic_fire,
            BitmapFactory.Options().apply { inSampleSize = 2 }).also { sharedTexture = it }
    }
    companion object { private var sharedTexture: Bitmap? = null }

    fun draw(canvas: Canvas, box: RectF, side: Float, topMargin: Float, bottomMargin: Float,
        frame: Int, green: Boolean = false, bottomOnly: Boolean? = null) {
        val image = bitmap ?: return
        val sx = intArrayOf(0, (image.width * .118f).toInt(), (image.width * .845f).toInt(), image.width)
        val sy = intArrayOf(0, (image.height * .36f).toInt(), (image.height * .824f).toInt(), image.height)
        val x = floatArrayOf(box.left-side, box.left, box.right, box.right+side)
        val y = floatArrayOf(box.top-topMargin, box.top, box.bottom, box.bottom+bottomMargin)
        paint.colorFilter = if (green) ColorMatrixColorFilter(floatArrayOf(
            0f,.7f,0f,0f,0f, 1f,0f,0f,0f,0f, 0f,.3f,0f,0f,0f, 0f,0f,0f,1f,0f)) else null
        for (row in 0..2) for (col in 0..2) {
            if (row == 1 && col == 1) continue
            if (bottomOnly != null && bottomOnly != (row == 2)) continue
            val sway = kotlin.math.sin(frame*Math.PI/12.0+col*1.7).toFloat()*topMargin*.06f
            val top = y[row]+if(row==0)sway else if(row==2)-sway else 0f
            canvas.drawBitmap(image, Rect(sx[col],sy[row],sx[col+1],sy[row+1]),
                RectF(x[col],top,x[col+1],y[row+1]),paint)
        }
        paint.colorFilter = null
    }
}

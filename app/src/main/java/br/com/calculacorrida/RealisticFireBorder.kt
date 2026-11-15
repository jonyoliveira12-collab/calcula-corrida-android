package br.com.calculacorrida

import android.content.Context
import android.graphics.*
import kotlin.math.sin

/** One continuous transparent flame texture: no nine-slice seams or square corner patches. */
class RealisticFireBorder(context:Context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val image=synchronized(RealisticFireBorder::class.java) {
        texture ?: BitmapFactory.decodeResource(context.resources,R.drawable.card_realistic_fire,BitmapFactory.Options().apply{inSampleSize=2}).also{texture=it}
    }
    companion object {private var texture:Bitmap?=null}
    private var lastColor:Int?=null
    private var verdictFilter:ColorFilter?=null
    private val cols=24;private val rows=20
    private val vertices=FloatArray((cols+1)*(rows+1)*2)
    private fun project(t:Float,a:Float,b:Float,start:Float,innerStart:Float,innerEnd:Float,end:Float):Float = when {
        t<a -> start+(innerStart-start)*(t/a)
        t>b -> innerEnd+(end-innerEnd)*((t-b)/(1-b))
        else -> innerStart+(innerEnd-innerStart)*((t-a)/(b-a))
    }
    fun draw(canvas:Canvas,box:RectF,side:Float,topMargin:Float,bottomMargin:Float,frame:Int,color:Int?=null,bottomOnly:Boolean?=null) {
        val bitmap=image ?: return
        val outer=RectF(box.left-side,box.top-topMargin,box.right+side,box.bottom+bottomMargin)
        val phase=frame*Math.PI/12
        var at=0
        for(y in 0..rows) for(x in 0..cols) {
            val u=x.toFloat()/cols;val v=y.toFloat()/rows
            val edge=(1f-kotlin.math.sin(Math.PI*v).toFloat()).coerceAtLeast(.15f)
            vertices[at++]=project(u,.118f,.845f,outer.left,box.left,box.right,outer.right)+sin(phase+v*11+u*6).toFloat()*side*.10f
            vertices[at++]=project(v,.36f,.824f,outer.top,box.top,box.bottom,outer.bottom)+sin(phase*1.4+u*17+v*3).toFloat()*topMargin*.075f*edge
        }
        // Recolor brightness, not alpha: preserve the flame texture and transparent edges.
        // A null color keeps the original amber texture on the login screen.
        if(color!=lastColor) {
            lastColor=color
            verdictFilter=color?.let { tint ->
                val r=Color.red(tint)/255f*1.6f
                val g=Color.green(tint)/255f*1.6f
                val b=Color.blue(tint)/255f*1.6f
                ColorMatrixColorFilter(floatArrayOf(
                    .299f*r,.587f*r,.114f*r,0f,0f,
                    .299f*g,.587f*g,.114f*g,0f,0f,
                    .299f*b,.587f*b,.114f*b,0f,0f,
                    0f,0f,0f,1f,0f))
            }
        }
        paint.colorFilter=verdictFilter
        val saved=canvas.save()
        if(bottomOnly==true)canvas.clipRect(outer.left,box.bottom,outer.right,outer.bottom+2)
        else if(bottomOnly==false)canvas.clipRect(outer.left,outer.top-2,outer.right,box.bottom)
        canvas.drawBitmapMesh(bitmap,cols,rows,vertices,0,null,0,paint)
        canvas.restoreToCount(saved);paint.colorFilter=null
    }
}

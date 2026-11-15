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
    private val cols=24;private val rows=20
    private val vertices=FloatArray((cols+1)*(rows+1)*2)
    private fun project(t:Float,a:Float,b:Float,start:Float,innerStart:Float,innerEnd:Float,end:Float):Float = when {
        t<a -> start+(innerStart-start)*(t/a)
        t>b -> innerEnd+(end-innerEnd)*((t-b)/(1-b))
        else -> innerStart+(innerEnd-innerStart)*((t-a)/(b-a))
    }
    fun draw(canvas:Canvas,box:RectF,side:Float,topMargin:Float,bottomMargin:Float,frame:Int,green:Boolean=false,bottomOnly:Boolean?=null) {
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
        // Keep a natural amber flame; the verdict remains on its colored badge/metrics.
        paint.colorFilter=null
        val saved=canvas.save()
        if(bottomOnly==true)canvas.clipRect(outer.left,box.bottom,outer.right,outer.bottom+2)
        else if(bottomOnly==false)canvas.clipRect(outer.left,outer.top-2,outer.right,box.bottom)
        canvas.drawBitmapMesh(bitmap,cols,rows,vertices,0,null,0,paint)
        canvas.restoreToCount(saved);paint.colorFilter=null
    }
}

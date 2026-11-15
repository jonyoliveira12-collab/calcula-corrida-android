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
    fun draw(canvas:Canvas,box:RectF,side:Float,topMargin:Float,bottomMargin:Float,frame:Int,green:Boolean=false,bottomOnly:Boolean?=null) {
        val bitmap=image ?: return
        val outer=RectF(box.left-side,box.top-topMargin,box.right+side,box.bottom+bottomMargin)
        val cols=20;val rows=10;val vertices=FloatArray((cols+1)*(rows+1)*2)
        val phase=frame*Math.PI/12
        var at=0
        for(y in 0..rows) for(x in 0..cols) {
            val u=x.toFloat()/cols;val v=y.toFloat()/rows
            val edge=(1f-kotlin.math.sin(Math.PI*v).toFloat()).coerceAtLeast(.15f)
            vertices[at++]=outer.left+outer.width()*u+sin(phase+v*11+u*6).toFloat()*side*.10f
            vertices[at++]=outer.top+outer.height()*v+sin(phase*1.4+u*17+v*3).toFloat()*topMargin*.075f*edge
        }
        paint.colorFilter=if(green)ColorMatrixColorFilter(floatArrayOf(0f,.7f,0f,0f,0f,1f,0f,0f,0f,0f,0f,.3f,0f,0f,0f,0f,0f,0f,1f,0f))else null
        val saved=canvas.save()
        if(bottomOnly==true)canvas.clipRect(outer.left,box.bottom,outer.right,outer.bottom+2)
        else if(bottomOnly==false)canvas.clipRect(outer.left,outer.top-2,outer.right,box.bottom)
        canvas.drawBitmapMesh(bitmap,cols,rows,vertices,0,null,0,paint)
        canvas.restoreToCount(saved);paint.colorFilter=null
    }
}

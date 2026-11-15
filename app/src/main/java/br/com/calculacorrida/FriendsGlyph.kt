package br.com.calculacorrida

import android.content.Context
import android.graphics.*
import android.view.View

/** Small vector illustrations; no bitmap scaling or font-dependent emoji. */
class FriendsGlyph(context:Context,private val kind:String,private val tint:Int,private val rings:Boolean=false):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val save=canvas.save()
        val size=minOf(width,height).toFloat()
        canvas.translate((width-size)/2,(height-size)/2);canvas.scale(size/100,size/100)
        fun stroke(w:Float=4f){paint.style=Paint.Style.STROKE;paint.strokeWidth=w;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.color=tint}
        fun fill(){paint.style=Paint.Style.FILL;paint.color=tint}
        if(rings) {
            for((radius,alpha) in listOf(47f to 35,38f to 85,29f to 150)) {
                stroke(1.5f);paint.alpha=alpha;canvas.drawCircle(50f,50f,radius,paint)
            }
            paint.alpha=255
            canvas.translate(24f,24f);canvas.scale(.52f,.52f)
        }
        when(kind) {
            "home" -> {
                stroke(5f);canvas.drawPath(Path().apply{moveTo(14f,45f);lineTo(50f,14f);lineTo(86f,45f);moveTo(24f,39f);lineTo(24f,83f);lineTo(76f,83f);lineTo(76f,39f);moveTo(42f,83f);lineTo(42f,58f);lineTo(58f,58f);lineTo(58f,83f)},paint)
            }
            "target" -> {
                stroke(5f);for(r in listOf(34f,22f,9f))canvas.drawCircle(47f,53f,r,paint)
                canvas.drawLine(48f,52f,84f,16f,paint);canvas.drawLine(72f,16f,84f,16f,paint);canvas.drawLine(84f,16f,84f,29f,paint)
            }
            "history","clock" -> {
                stroke(5f);canvas.drawArc(16f,16f,84f,84f,if(kind=="history")205f else 0f,if(kind=="history")300f else 360f,false,paint)
                canvas.drawLine(50f,29f,50f,50f,paint);canvas.drawLine(50f,50f,65f,59f,paint)
                if(kind=="history"){canvas.drawLine(13f,22f,13f,43f,paint);canvas.drawLine(13f,43f,32f,43f,paint)}
            }
            "settings" -> {
                stroke(5f);canvas.drawCircle(50f,50f,25f,paint);canvas.drawCircle(50f,50f,9f,paint)
                for(i in 0..7){val rad=i*Math.PI/4;canvas.drawLine(50f+25*kotlin.math.cos(rad).toFloat(),50f+25*kotlin.math.sin(rad).toFloat(),50f+36*kotlin.math.cos(rad).toFloat(),50f+36*kotlin.math.sin(rad).toFloat(),paint)}
            }
            "people","add" -> {
                fill();canvas.drawCircle(38f,29f,12f,paint);canvas.drawCircle(67f,34f,10f,paint)
                canvas.drawRoundRect(17f,47f,59f,77f,14f,14f,paint)
                canvas.drawRoundRect(59f,50f,83f,77f,10f,10f,paint)
                if(kind=="add"){stroke(5f);canvas.drawLine(77f,12f,77f,31f,paint);canvas.drawLine(68f,21f,86f,21f,paint)}
            }
            "shield" -> {
                stroke(5f);canvas.drawPath(Path().apply{moveTo(50f,12f);lineTo(80f,25f);lineTo(77f,53f);quadTo(73f,74f,50f,88f);quadTo(27f,74f,23f,53f);lineTo(20f,25f);close()},paint)
                canvas.drawPath(Path().apply{moveTo(34f,48f);lineTo(46f,60f);lineTo(67f,37f)},paint)
            }
            "code" -> {
                stroke(4f)
                for((x,y) in listOf(17f to 17f,59f to 17f,17f to 59f)){
                    canvas.drawRoundRect(x,y,x+24,y+24,2f,2f,paint)
                }
                fill();for((x,y) in listOf(25f to 25f,67f to 25f,25f to 67f,59f to 59f,73f to 73f,59f to 81f,81f to 59f))canvas.drawRect(x,y,x+8,y+8,paint)
            }
            "chat" -> {
                stroke(5f);canvas.drawRoundRect(15f,18f,85f,70f,20f,20f,paint)
                canvas.drawPath(Path().apply{moveTo(30f,70f);lineTo(24f,86f);lineTo(49f,70f)},paint)
                fill();for(x in listOf(34f,50f,66f))canvas.drawCircle(x,44f,3f,paint)
            }
            "pin" -> {
                stroke(5f);canvas.drawPath(Path().apply{moveTo(50f,88f);cubicTo(38f,70f,22f,50f,22f,35f);cubicTo(22f,0f,78f,0f,78f,35f);cubicTo(78f,50f,62f,70f,50f,88f);close()},paint)
                canvas.drawCircle(50f,34f,9f,paint)
            }
        }
        canvas.restoreToCount(save)
    }
}

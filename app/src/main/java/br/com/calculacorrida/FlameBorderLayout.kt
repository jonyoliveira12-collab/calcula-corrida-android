package br.com.calculacorrida

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlin.math.sin

/** Animated gold flames stay in the reserved margin around the login card. */
class FlameBorderLayout @JvmOverloads constructor(
    context:Context, attrs:AttributeSet?=null, defStyleAttr:Int=0
):FrameLayout(context,attrs,defStyleAttr) {
    private val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
    }
    private var flameFrame=0
    private val tick=object:Runnable {
        override fun run() {
            if(!isAttachedToWindow || !isShown || windowVisibility!=View.VISIBLE || !prefs.getBoolean("card_neon_animated",true))return
            val power=context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if(power?.isInteractive!=false) {
                flameFrame=(flameFrame+1)%24
                postInvalidateOnAnimation()
                postDelayed(this,80L)
            } else postDelayed(this,2000L)
        }
    }
    private fun dp(value:Int)=value*resources.displayMetrics.density
    private fun wave(index:Int)=sin((flameFrame+index*3)*Math.PI/12.0).toFloat()
    private fun updateAnimation() {
        removeCallbacks(tick)
        if(isAttachedToWindow && isShown && windowVisibility==View.VISIBLE && prefs.getBoolean("card_neon_animated",true))post(tick)
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();updateAnimation()}
    override fun onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);updateAnimation()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);updateAnimation()}

    override fun dispatchDraw(canvas:Canvas) {
        val child=if(childCount>0)getChildAt(0)else null
        if(child!=null && child.width>0 && child.height>0) {
            val box=RectF(child.left.toFloat(),child.top.toFloat(),child.right.toFloat(),child.bottom.toFloat())
            paint.style=Paint.Style.STROKE
            for((width,alpha) in listOf(14 to 40,8 to 95,4 to 170,2 to 235)) {
                paint.color=Color.argb(alpha,255,181,24);paint.strokeWidth=dp(width)
                canvas.drawRoundRect(box,dp(24),dp(24),paint)
            }
            horizontalFlames(canvas,box.left+dp(17),box.right-dp(17),box.top,-1f)
            horizontalFlames(canvas,box.left+dp(17),box.right-dp(17),box.bottom,1f)
            verticalFlames(canvas,box.left,box.top+dp(22),box.bottom-dp(22),-1f)
            verticalFlames(canvas,box.right,box.top+dp(22),box.bottom-dp(22),1f)
        }
        super.dispatchDraw(canvas)
    }

    private fun horizontalFlames(canvas:Canvas,left:Float,right:Float,base:Float,direction:Float) {
        val path=Path().apply {
            moveTo(left,base)
            var x=left;var index=0
            while(x<right) {
                val end=(x+dp(54)).coerceAtMost(right);val width=end-x
                val height=dp(11)+dp(4)*wave(index)
                val sway=dp(3)*wave(index+2)
                cubicTo(x+width*.17f,base,x+width*.25f,base+direction*dp(4),x+width*.43f+sway,base+direction*height)
                cubicTo(x+width*.34f,base+direction*dp(4),x+width*.50f,base+direction*dp(3),x+width*.62f,base+direction*dp(6))
                cubicTo(x+width*.77f,base+direction*dp(8),x+width*.86f,base+direction*dp(2),end,base)
                x=end;index++
            }
            lineTo(right,base);lineTo(left,base);close()
        }
        drawFlame(canvas,path)
    }

    private fun verticalFlames(canvas:Canvas,base:Float,top:Float,bottom:Float,direction:Float) {
        val path=Path().apply {
            moveTo(base,top)
            var y=top;var index=0
            while(y<bottom) {
                val end=(y+dp(60)).coerceAtMost(bottom);val height=end-y
                val spread=dp(10)+dp(4)*wave(index+1)
                val sway=dp(3)*wave(index+3)
                cubicTo(base,y+height*.17f,base+direction*dp(4),y+height*.25f,base+direction*spread,y+height*.43f+sway)
                cubicTo(base+direction*dp(4),y+height*.36f,base+direction*dp(3),y+height*.51f,base+direction*dp(6),y+height*.64f)
                cubicTo(base+direction*dp(8),y+height*.77f,base+direction*dp(2),y+height*.88f,base,end)
                y=end;index++
            }
            lineTo(base,bottom);lineTo(base,top);close()
        }
        drawFlame(canvas,path)
    }

    private fun drawFlame(canvas:Canvas,path:Path) {
        paint.style=Paint.Style.STROKE
        for((width,alpha) in listOf(8 to 55,5 to 100,2 to 190)) {
            paint.color=Color.argb(alpha,255,164,0);paint.strokeWidth=dp(width)
            canvas.drawPath(path,paint)
        }
        paint.style=Paint.Style.FILL;paint.color=Color.rgb(255,187,28);canvas.drawPath(path,paint)
        paint.style=Paint.Style.STROKE;paint.color=Color.rgb(255,240,174);paint.strokeWidth=dp(1)
        canvas.drawPath(path,paint)
    }
}

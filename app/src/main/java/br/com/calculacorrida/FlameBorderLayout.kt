package br.com.calculacorrida

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlin.math.sin

/** Taller realistic flames stay in the reserved margin around the login card. */
class FlameBorderLayout @JvmOverloads constructor(
    context:Context, attrs:AttributeSet?=null, defStyleAttr:Int=0
):FrameLayout(context,attrs,defStyleAttr) {
    private val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
    private val fireBorder = RealisticFireBorder(context)
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

    override fun dispatchDraw(canvas: Canvas) {
        val child = if (childCount > 0) getChildAt(0) else null
        if (child != null && child.width > 0 && child.height > 0) {
            fireBorder.draw(canvas,
                RectF(child.left.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat()),
                dp(24), dp(52), dp(28), flameFrame)
        }
        super.dispatchDraw(canvas)
    }
}

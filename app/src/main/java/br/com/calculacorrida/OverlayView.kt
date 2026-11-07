package br.com.calculacorrida

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout

class OverlayView(context: Context) : FrameLayout(context) {

    private var container: LinearLayout
    private var titleText: TextView
    private var priceKmText: TextView
    private var priceMinText: TextView
    private var semaforoView: View
    private var ratingText: TextView
    private var infoText: TextView

    private var fontSize: Float = 14f
    private var opacity: Float = 0.85f
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var layoutParams: WindowManager.LayoutParams? = null

    init {
        setBackgroundColor(Color.parseColor("#CC000000"))
        alpha = opacity
        setPadding(24, 16, 24, 16)

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        titleText = createTextView("JHONDRIVER", Typeface.BOLD, fontSize + 2f, Color.parseColor("#FFD700"))
        container.addView(titleText)

        // Semáforo
        semaforoView = View(context).apply {
            setBackgroundColor(Color.GRAY)
            val params = LinearLayout.LayoutParams(80, 12)
            params.setMargins(0, 6, 0, 6)
            layoutParams = params
        }
        container.addView(semaforoView)

        priceKmText = createTextView("R$/km: --", Typeface.NORMAL, fontSize, Color.parseColor("#FFD700"))
        container.addView(priceKmText)

        priceMinText = createTextView("R$/min: --", Typeface.NORMAL, fontSize, Color.parseColor("#FFD700"))
        container.addView(priceMinText)

        ratingText = createTextView("⭐ --", Typeface.NORMAL, fontSize, Color.WHITE)
        container.addView(ratingText)

        infoText = createTextView("Aguardando...", Typeface.NORMAL, fontSize - 1f, Color.parseColor("#CCCCCC"))
        container.addView(infoText)

        addView(container)
    }

    private fun createTextView(text: String, typeface: Int, size: Float, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            this.typeface = Typeface.create(Typeface.DEFAULT, typeface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 4, 0, 4)
            layoutParams = params
        }
    }

    fun updateData(
        priceKm: Double,
        priceMin: Double,
        rating: Double,
        semaforoColor: Int,
        additionalInfo: String
    ) {
        titleText.text = "JHONDRIVER"
        priceKmText.text = String.format("R$/km: %.2f", priceKm)
        priceMinText.text = String.format("R$/min: %.2f", priceMin)
        ratingText.text = if (rating > 0) String.format("⭐ %.1f", rating) else "⭐ --"
        semaforoView.setBackgroundColor(semaforoColor)
        infoText.text = additionalInfo
    }

    fun setFontSize(size: Float) {
        fontSize = size
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size + 2f)
        priceKmText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        priceMinText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        ratingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        infoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size - 1f)
    }

    fun setOpacityValue(value: Float) {
        opacity = value
        alpha = value
    }

    fun setLayoutParams(params: WindowManager.LayoutParams) {
        layoutParams = params
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams?.x ?: 0f
                initialY = layoutParams?.y ?: 0f
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                layoutParams?.let { params ->
                    params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.updateViewLayout(this, params)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        val COLOR_VERDE = Color.parseColor("#00C853")
        val COLOR_AMARELO = Color.parseColor("#FFD600")
        val COLOR_VERMELHO = Color.parseColor("#D50000")
    }
}

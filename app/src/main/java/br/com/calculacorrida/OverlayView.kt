package br.com.calculacorrida

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class OverlayView(context: Context) : LinearLayout(context) {

    // Cores Tema Preto e Dourado
    private val bg = Color.rgb(12, 12, 13)
    private val panel = Color.rgb(25, 24, 22)
    private val gold = Color.rgb(218, 170, 78)
    private val white = Color.rgb(247, 244, 237)
    private val muted = Color.rgb(160, 154, 143)
    private val green = Color.rgb(42, 166, 111)
    private val red = Color.rgb(202, 65, 65)
    private val amber = Color.rgb(204, 139, 43)

    var status: String = ""
    var statusColor: Int = gold
    var fare: String = ""
    var perKm: String = ""
    var perHour: String = ""
    var tripTime: String = ""
    var passengerRating: String = ""
    var profitPercent: String = ""
    var showProfit: Boolean = false
    var showTime: Boolean = true
    var showRating: Boolean = true
    var onClose: (() -> Unit)? = null

    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(2), gold)
        }
        elevation = dp(12).toFloat()

        setupSwipeToClose()
        buildUI()
    }

    private fun buildUI() {
        removeAllViews()

        // CABEÇALHO (Status + Valor)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statusView = TextView(context).apply {
            text = this@OverlayView.status
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(statusColor)
                cornerRadius = dp(12).toFloat()
            }
        }
        header.addView(statusView, LinearLayout.LayoutParams(-2, -2))

        val fareView = TextView(context).apply {
            text = fare
            setTextColor(gold)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        header.addView(fareView, LinearLayout.LayoutParams(-1, -2).apply { weight = 1f; marginStart = dp(12) })

        addView(header, LinearLayout.LayoutParams(-1, -2))

        // R$/km (DESTAQUE)
        addView(makeMetricLarge(perKm, gold), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // R$/hora + Tempo
        if (showTime) {
            val rowTime = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            rowTime.addView(makeMetricSmall(perHour, muted), LinearLayout.LayoutParams(0, -2).apply { weight = 1f })
            rowTime.addView(makeMetricSmall("⏱ $tripTime", muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            addView(rowTime, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        } else {
            addView(makeMetricSmall(perHour, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        // Nota do passageiro
        if (showRating && passengerRating.isNotEmpty()) {
            addView(makeMetricSmall("⭐ $passengerRating", muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        // Lucro %
        if (showProfit && profitPercent.isNotEmpty()) {
            val profitColor = if (profitPercent.startsWith("+")) green else red
            val profitView = TextView(context).apply {
                text = "Lucro: $profitPercent"
                setTextColor(profitColor)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }
            addView(profitView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun makeMetricLarge(text: String, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun makeMetricSmall(text: String, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = 15f
        }
    }

    private fun setupSwipeToClose() {
        setOnTouchListener(object : OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(event.rawX - startX)
                        val dy = Math.abs(event.rawY - startY)
                        if (dx > dp(10) || dy > dp(10)) {
                            moved = true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            onClose?.invoke()
                        }
                    }
                }
                return true
            }
        })
    }

    override fun invalidate() {
        super.invalidate()
        buildUI()
    }

    override fun requestLayout() {
        super.requestLayout()
        buildUI()
    }

    private fun dp(v: Int): Int = (v * density).toInt()
}

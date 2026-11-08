package br.com.calculacorrida

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

/** Faixa compacta do JHONDRIVER: classificação, tempo/distância e três indicadores. */
class OfferTagView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = RectF()
    private var status = "AGUARDANDO OFERTA"
    private var duration = "-- min"
    private var distance = "-- km"
    private var perKm = "--"
    private var perHour = "--"
    private var passengerRating = "--"
    private var accent = Color.rgb(120, 120, 120)
    private var diagnostic = false

    init {
        contentDescription = "Faixa JHONDRIVER com classificação da corrida, tempo, distância, valor por quilômetro, valor por hora e nota do passageiro."
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun dp(v: Float) = v * density

    fun setOffer(
        label: String,
        minutes: Double,
        totalKm: Double,
        valuePerKm: Double,
        valuePerHour: Double,
        rating: String?,
        color: Int,
        source: String = "APP"
    ) {
        status = "$source • ${label.uppercase()}"
        duration = String.format(java.util.Locale("pt", "BR"), "%.0f min", minutes)
        distance = String.format(java.util.Locale("pt", "BR"), "%.1f km", totalKm)
        perKm = String.format(java.util.Locale("pt", "BR"), "%.2f", valuePerKm)
        perHour = String.format(java.util.Locale("pt", "BR"), "%.0f", valuePerHour)
        passengerRating = rating?.replace('.', ',') ?: "--"
        accent = color
        diagnostic = false
        invalidate()
    }

    fun setDiagnostic(message: String) {
        status = message
        diagnostic = true
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(112f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        card.set(dp(1f), dp(1f), w - dp(1f), h - dp(1f))

        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        canvas.drawRoundRect(card, dp(13f), dp(13f), p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2f)
        p.color = accent
        canvas.drawRoundRect(card, dp(13f), dp(13f), p)
        p.style = Paint.Style.FILL

        // Faixa de classificação
        p.color = accent
        canvas.drawRoundRect(dp(10f), dp(8f), dp(116f), dp(32f), dp(7f), dp(7f), p)
        drawText(canvas, if (diagnostic) "!" else "●", dp(22f), dp(24f), 11f, Color.WHITE, true)
        drawText(canvas, status, dp(36f), dp(24f), 10f, Color.WHITE, true)
        drawText(canvas, "×", w - dp(22f), dp(24f), 19f, Color.DKGRAY, true, Paint.Align.CENTER)

        if (diagnostic) return

        drawText(canvas, "$duration  ·  $distance", dp(12f), dp(49f), 13f, Color.rgb(35, 39, 43), true)

        val left = dp(12f)
        val right = w - dp(12f)
        val col = (right - left) / 3f
        val labels = arrayOf("R$/Km", "R$/Hora", "Nota")
        val values = arrayOf("R$ $perKm", "R$ $perHour", passengerRating)
        for (i in 0..2) {
            val x = left + col * i
            if (i > 0) {
                p.color = Color.rgb(225, 228, 232)
                canvas.drawRect(x, dp(60f), x + dp(1f), dp(103f), p)
            }
            drawText(canvas, labels[i], x + dp(4f), dp(72f), 10f, Color.rgb(120, 126, 134), true)
            drawText(canvas, values[i], x + dp(4f), dp(96f), 17f, Color.rgb(20, 23, 27), true)
            p.color = accent
            canvas.drawRoundRect(x + dp(4f), dp(78f), x + dp(7f), dp(101f), dp(1.5f), dp(1.5f), p)
        }
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        sizeSp: Float,
        color: Int,
        bold: Boolean,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        p.color = color
        p.textSize = dp(sizeSp)
        p.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        p.textAlign = align
        canvas.drawText(value, x, y, p)
    }
}

package br.com.calculacorrida

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ComponentName
import android.provider.Settings
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var minKm: EditText
    private lateinit var minHour: EditText

    private val bg = Color.rgb(5, 5, 7)
    private val panel = Color.rgb(22, 20, 20)
    private val field = Color.rgb(13, 12, 12)
    private val white = Color.rgb(244, 248, 252)
    private val muted = Color.rgb(157, 174, 191)
    private val green = Color.rgb(255, 125, 25)
    private val red = Color.rgb(224, 50, 50)
    private val blue = Color.rgb(232, 91, 25)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(30))
            setBackgroundColor(bg)
        }

        val brand = TextView(this).apply {
            text = "CALCULA CORRIDA"
            textSize = 23f
            letterSpacing = .07f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }
        page.addView(brand)
        page.addView(TextView(this).apply {
            text = "INTELIGÊNCIA PARA CADA CORRIDA"
            textSize = 10f
            letterSpacing = .08f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(18))
        })

        val hero = card()
        hero.addView(TextView(this).apply {
            text = "SEU PAINEL DE CORRIDAS"
            textSize = 11f
            letterSpacing = .08f
            setTextColor(green)
            setTypeface(null, Typeface.BOLD)
        })
        hero.addView(TextView(this).apply {
            text = "Mais clareza. Mais controle."
            textSize = 27f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(4))
        })
        hero.addView(TextView(this).apply {
            text = "Veja quanto a corrida rende por quilômetro e por hora. Você decide; o app não aceita nem recusa ofertas."
            textSize = 13f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        page.addView(hero, marginParams(bottom = 14))

        val statusCard = card()
        statusCard.addView(TextView(this).apply {
            text = "STATUS DO APLICATIVO"
            textSize = 10f
            letterSpacing = .08f
            setTextColor(muted)
            setTypeface(null, Typeface.BOLD)
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(white)
            setPadding(0, dp(9), 0, 0)
        }
        statusCard.addView(status)
        overlayStatus = statusLine("Janela flutuante: verificando…")
        accessibilityStatus = statusLine("Leitura das ofertas: ative nas configurações")
        statusCard.addView(overlayStatus)
        statusCard.addView(accessibilityStatus)
        page.addView(statusCard, marginParams(bottom = 14))
        val sizeCard = card()
        sizeCard.addView(TextView(this).apply { text = "TAMANHO DA ETIQUETA"; textSize = 11f; letterSpacing = .08f; setTextColor(white); setTypeface(null, Typeface.BOLD) })
        val sizeValue = TextView(this).apply { textSize = 14f; setTextColor(green); setTypeface(null, Typeface.BOLD); setPadding(0, dp(7), 0, dp(2)) }
        sizeCard.addView(sizeValue)
        sizeCard.addView(TextView(this).apply { text = "Ajuste o tamanho da etiqueta sobre a oferta. O padrão é compacto."; textSize = 12f; setTextColor(muted) })
        val sizeSeek = SeekBar(this).apply { max = 45; progress = (prefs.getInt("overlay_scale", 85) - 65).coerceIn(0, 45); progressTintList = android.content.res.ColorStateList.valueOf(green); thumbTintList = android.content.res.ColorStateList.valueOf(green) }
        sizeValue.text = "Tamanho: ${prefs.getInt("overlay_scale", 85)}%"
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { val value = 65 + progress; sizeValue.text = "Tamanho: ${value}%"; if (fromUser) prefs.edit().putInt("overlay_scale", value).apply() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { Toast.makeText(this@MainActivity, "Tamanho salvo para a próxima oferta.", Toast.LENGTH_SHORT).show() }
        })
        sizeCard.addView(sizeSeek)
        page.addView(sizeCard, marginParams(bottom = 14))

        val limits = card()
        limits.addView(TextView(this).apply {
            text = "SEUS LIMITES"
            textSize = 11f
            letterSpacing = .08f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        })
        limits.addView(TextView(this).apply {
            text = "Defina quanto deseja receber, no mínimo."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(10))
        })
        minKm = numberField("Valor mínimo por quilômetro", prefs.getFloat("min_per_km", 1.80f).toString())
        minHour = numberField("Valor mínimo por hora", prefs.getFloat("min_per_hour", 35f).toString())
        limits.addView(fieldLabel("MÍNIMO POR KM"))
        limits.addView(minKm, marginParams(bottom = 10))
        limits.addView(fieldLabel("MÍNIMO POR HORA"))
        limits.addView(minHour)
        val save = actionButton("SALVAR MEUS LIMITES", green)
        limits.addView(save, marginParams(top = 14))
        save.setOnClickListener {
            val km = minKm.text.toString().replace(",", ".").toFloatOrNull()
            val hour = minHour.text.toString().replace(",", ".").toFloatOrNull()
            if (km == null || hour == null || km <= 0 || hour <= 0) {
                Toast.makeText(this, "Informe valores válidos.", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putFloat("min_per_km", km).putFloat("min_per_hour", hour).apply()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(minHour.windowToken, 0)
                Toast.makeText(this, "Limites salvos!", Toast.LENGTH_SHORT).show()
            }
        }
        page.addView(limits, marginParams(bottom = 14))

        val permissions = card()
        permissions.addView(TextView(this).apply {
            text = "CONFIGURAÇÃO INICIAL"
            textSize = 11f
            letterSpacing = .08f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        })
        permissions.addView(TextView(this).apply {
            text = "Libere as duas permissões para o indicador aparecer sobre o Uber ou a 99."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(12))
        })
        val overlayButton = actionButton("1  •  PERMITIR JANELA FLUTUANTE", blue)
        overlayButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Janela flutuante já autorizada.", Toast.LENGTH_SHORT).show()
            }
        }
        permissions.addView(overlayButton, marginParams(bottom = 9))
        val accessButton = actionButton("2  •  ATIVAR LEITURA DE OFERTAS", Color.rgb(184, 48, 38))
        accessButton.setOnClickListener {
            val component = ComponentName(this, OfferAccessibilityService::class.java)
            try {
                val detailIntent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME", component.flattenToString())
                startActivity(detailIntent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Toast.makeText(this, "Na lista, abra Calcula Corrida e ligue a chave.", Toast.LENGTH_LONG).show()
        }
        permissions.addView(accessButton)
        val notificationButton = actionButton("3  •  PERMITIR NOTIFICAÇÕES", Color.rgb(65, 65, 70))
        notificationButton.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 303)
            } else {
                Toast.makeText(this, "Notificações disponíveis nesta versão do Android.", Toast.LENGTH_SHORT).show()
            }
        }
        permissions.addView(notificationButton, marginParams(top = 9))
        page.addView(permissions, marginParams(bottom = 14))

        page.addView(TextView(this).apply {
            text = "IMPORTANTE  •  Os valores são estimativas brutas e não descontam combustível, manutenção, pedágios ou taxas. A leitura automática depende dos dados que Uber e 99 disponibilizam ao Android; ainda precisa ser validada em ofertas reais."
            textSize = 11f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(3), dp(2), dp(3), dp(12))
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(page)
        })
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(17), dp(17), dp(17))
        background = rounded(panel, 18)
    }

    private fun fieldLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 10f
        letterSpacing = .07f
        setTextColor(muted)
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(2), 0, 0, dp(6))
    }

    private fun numberField(label: String, value: String) = EditText(this).apply {
        hint = label
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        textSize = 17f
        setTextColor(white)
        setHintTextColor(Color.rgb(111, 130, 149))
        setPadding(dp(13), dp(12), dp(13), dp(12))
        setText(value.replace(".", ","))
        background = rounded(field, 11, Color.rgb(48, 67, 87))
    }

    private fun actionButton(label: String, color: Int) = Button(this).apply {
        text = label
        textSize = 12f
        letterSpacing = .02f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        isAllCaps = false
        background = rounded(color, 11)
        minHeight = dp(50)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        stateListAnimator = null
    }

    private fun statusLine(value: String) = TextView(this).apply {
        text = "•  $value"
        textSize = 12f
        setTextColor(muted)
        setPadding(0, dp(6), 0, 0)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun marginParams(bottom: Int = 0, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (bottom > 0) bottomMargin = dp(bottom)
            if (top > 0) topMargin = dp(top)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            val overlayAllowed = Settings.canDrawOverlays(this)
            status.text = if (overlayAllowed) "Permissões: janela flutuante autorizada" else "Permissões: falta liberar a janela flutuante"
            overlayStatus.text = if (overlayAllowed) "✓  Janela flutuante autorizada" else "○  Janela flutuante não autorizada"
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains(ComponentName(this, OfferAccessibilityService::class.java).flattenToString()) == true
            accessibilityStatus.text = if (enabled) "✓  Leitura de ofertas ativada" else "○  Leitura de ofertas ainda desativada"
            overlayStatus.setTextColor(if (overlayAllowed) green else muted)
            accessibilityStatus.setTextColor(if (enabled) green else muted)
        }
    }
}

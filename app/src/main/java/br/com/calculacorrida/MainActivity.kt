package br.com.calculacorrida

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 10, 10)
    private val panel = Color.rgb(22, 22, 20)
    private val gold = Color.rgb(212, 175, 55)
    private val textWhite = Color.rgb(245, 245, 245)
    private val textMuted = Color.rgb(160, 160, 160)
    private val green = Color.rgb(42, 166, 111)
    private val red = Color.rgb(202, 65, 65)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg

        val session = SessionManager(this)
        if (!session.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }

        // HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "JHONDRIVER"
            textSize = 24f
            setTextColor(textWhite)
            typeface = Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "  COPILOT"
            textSize = 12f
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        // STATUS ACCESSIBILITY
        checkAccessibility(root)

        // SCROLLVIEW para não cortar em telas pequenas
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // CARD PRINCIPAL
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(40, 40, 38))
            }
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        // SWITCH COPILOT
        val copilotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val copilotSwitch = Switch(this).apply {
            text = "Copilot Ativo"
            textSize = 16f
            setTextColor(textWhite)
            isChecked = prefs.getBoolean("copilot_enabled", true)
            setOnCheckedChangeListener { _, b ->
                prefs.edit().putBoolean("copilot_enabled", b).apply()
            }
        }
        copilotRow.addView(copilotSwitch, LinearLayout.LayoutParams(0, -2).apply { weight = 1f })

        // SINAL DE STATUS
        val statusDot = View(this).apply {
            background = GradientDrawable().apply {
                setColor(green)
                cornerRadius = dp(6).toFloat()
            }
        }
        copilotRow.addView(statusDot, LinearLayout.LayoutParams(dp(12), dp(12)))
        card.addView(copilotRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })

        // CONFIGURAÇÕES (Switches)
        addSettingRow(card, "Voz (TTS)", prefs.getBoolean("voice_enabled", false)) { v ->
            prefs.edit().putBoolean("voice_enabled", v).apply()
        }
        addSettingRow(card, "Mostrar lucro%", prefs.getBoolean("show_profit", false)) { v ->
            prefs.edit().putBoolean("show_profit", v).apply()
        }
        addSettingRow(card, "Mostrar nota", prefs.getBoolean("show_rating", true)) { v ->
            prefs.edit().putBoolean("show_rating", v).apply()
        }
        addSettingRow(card, "Mostrar tempo", prefs.getBoolean("show_time", true)) { v ->
            prefs.edit().putBoolean("show_time", v).apply()
        }

        // DIVISÓRIA
        card.addView(makeDivider())

        // CAMPOS DE VALORES
        card.addView(makeLabel("Mínimo por km (R$)"))
        val minKmInput = makeNumberInput("1.80", prefs.getFloat("min_per_km", 1.80f).toString())
        card.addView(minKmInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        card.addView(makeLabel("Mínimo por hora (R$)"))
        val minHourInput = makeNumberInput("28.00", prefs.getFloat("min_per_hour", 28f).toString())
        card.addView(minHourInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        card.addView(makeLabel("Custo por km (R$)"))
        val costKmInput = makeNumberInput("0.65", prefs.getFloat("cost_per_km", 0.65f).toString())
        card.addView(costKmInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })

        // BOTÃO SALVAR
        val btnSave = Button(this).apply {
            text = "SALVAR CONFIGURAÇÕES"
            textSize = 15f
            setTextColor(bg)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(gold)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                prefs.edit()
                    .putFloat("min_per_km", minKmInput.text.toString().toFloatOrNull() ?: 1.80f)
                    .putFloat("min_per_hour", minHourInput.text.toString().toFloatOrNull() ?: 28f)
                    .putFloat("cost_per_km", costKmInput.text.toString().toFloatOrNull() ?: 0.65f)
                    .apply()
                Toast.makeText(this@MainActivity, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(btnSave, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(4) })

        // BOTÃO PERMISSÃO OVERLAY
        val btnOverlay = Button(this).apply {
            text = "PERMITIR TAG NA TELA"
            textSize = 13f
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), gold)
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                } else {
                    Toast.makeText(this@MainActivity, "Permissão já concedida!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        card.addView(btnOverlay, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })

        container.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        // BOTÃO SAIR
        val btnLogout = Button(this).apply {
            text = "SAIR"
            textSize = 14f
            setTextColor(red)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            setOnClickListener {
                SessionManager(this@MainActivity).logout()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
        container.addView(btnLogout, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        scroll.addView(container)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun checkAccessibility(root: LinearLayout) {
        val enabled = isAccessibilityEnabled()
        val msg = if (enabled) "● Acessibilidade ATIVA" else "● Acessibilidade INATIVA"
        val color = if (enabled) green else red

        val txt = TextView(this).apply {
            text = msg
            textSize = 13f
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
        }

        if (!enabled) {
            txt.setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            txt.text = "$msg  (Toque para ativar)"
        }
        root.addView(txt, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val services = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.contains("br.com.calculacorrida/.OfferAccessibilityService")
    }

    private fun addSettingRow(
        parent: LinearLayout,
        label: String,
        default: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(textWhite)
        }, LinearLayout.LayoutParams(0, -2).apply { weight = 1f })

        val switch = Switch(this).apply {
            isChecked = default
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        row.addView(switch)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }

    private fun makeDivider(): View {
        return View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(40, 40, 38))
                cornerRadius = dp(1).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(16)
            }
        }
    }

    private fun makeLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(textMuted)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
    }

    private fun makeNumberInput(hint: String, value: String): EditText {
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            this.hint = hint
            textSize = 15f
            setTextColor(textWhite)
            setHintTextColor(textMuted)
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 18, 17))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(50, 50, 48))
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
           setText(value)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

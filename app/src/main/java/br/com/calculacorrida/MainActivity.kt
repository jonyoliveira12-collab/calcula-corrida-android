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
import android.content.Context
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private val bg = Color.rgb(12, 12, 13)
    private val panel = Color.rgb(25, 24, 22)
    private val gold = Color.rgb(218, 170, 78)
    private val white = Color.rgb(247, 244, 237)
    private val muted = Color.rgb(160, 154, 143)
    private val green = Color.rgb(42, 166, 111)
    private val red = Color.rgb(202, 65, 65)
    private val amber = Color.rgb(204, 139, 43)
    private lateinit var content: LinearLayout
    private lateinit var bottom: LinearLayout
    private var selectedTab = "Início"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(8), dp(12), dp(5))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(7), dp(5), dp(12))
        }
        header.addView(label("JHONDRIVER", 23f, white, true))
        header.addView(label("INTELIGÊNCIA PARA CADA CORRIDA", 10f, muted, true))
        root.addView(header)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), dp(2), dp(1), dp(14))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(20, 19, 18), 18, Color.rgb(91, 69, 37))
            setPadding(dp(3), dp(5), dp(3), dp(5))
        }
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(62)))
        setContentView(root)
        showTab("Início")
    }

    private fun showTab(tab: String) {
        selectedTab = tab
        content.removeAllViews()
        when (tab) {
            "Início" -> buildHome()
            "Histórico" -> buildHistory()
            "Finanças" -> buildFinances()
            else -> buildProfile()
        }
        bottom.removeAllViews()
        val tabs = listOf(Triple("Início", "⌂", "Início"), Triple("Histórico", "◷", "Histórico"),
            Triple("Finanças", "▤", "Finanças"), Triple("Perfil", "◎", "Perfil"))
        tabs.forEach { item ->
            val active = item.first == selectedTab
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                isClickable = true
                setOnClickListener { showTab(item.first) }
            }
            cell.addView(label(item.second, 21f, if (active) gold else muted, false))
            cell.addView(label(item.third, 10f, if (active) gold else muted, active))
            bottom.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun buildHome() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val welcome = card()
        val row = horizontal()
        row.addView(label("DIA DE TRABALHO", 11f, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(pill("● ONLINE", green))
        welcome.addView(row)
        welcome.addView(label("Seu painel de corridas", 22f, white, true, 12, 3))
        welcome.addView(label("Acompanhe seu desempenho em tempo real.", 12f, muted, false, 0, 12))
        val metrics = horizontal()
        metrics.addView(metric("GANHOS", "R$ 0,00"), weight())
        metrics.addView(metric("POR HORA", "R$ 0,00"), weight())
        metrics.addView(metric("CORRIDAS", "0"), weight())
        welcome.addView(metrics)
        content.addView(welcome, margin(0, 10))

        val copilot = card()
        val copilotRow = horizontal()
        copilotRow.addView(iconTile("✧"))
        copilotRow.addView(verticalText("Copiloto", "Análise automática de ofertas"), LinearLayout.LayoutParams(0, -2, 1f))
        val aiSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("copilot_enabled", true)
            buttonTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("copilot_enabled", checked).apply() }
        }
        copilotRow.addView(aiSwitch)
        copilot.addView(copilotRow)
        copilot.addView(label("O copiloto lê os dados visíveis da oferta e apresenta os cálculos. Não aceita nem recusa corridas.", 11f, muted, false, 0, 5))
        content.addView(copilot, margin(0, 10))

        val traffic = card()
        val trafficRow = horizontal()
        trafficRow.addView(iconTile("🚦"))
        trafficRow.addView(verticalText("Semáforo de Valores", "Uber • 99 • classificação por faixa"), LinearLayout.LayoutParams(0, -2, 1f))
        val trafficSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("traffic_enabled", true)
            buttonTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("traffic_enabled", checked).apply() }
        }
        trafficRow.addView(trafficSwitch)
        traffic.addView(trafficRow)
        traffic.addView(label("RUIM        ATENÇÃO        ACEITAR", 11f, gold, true, 8, 0))
        content.addView(traffic, margin(0, 10))

        val journey = card()
        val jrow = horizontal()
        jrow.addView(iconTile("◷"))
        jrow.addView(verticalText("Jornada", "Monitore seu tempo de trabalho."), LinearLayout.LayoutParams(0, -2, 1f))
        jrow.addView(label("›", 25f, muted, false))
        journey.addView(jrow)
        journey.addView(action("VER RELATÓRIO", false) { showTab("Histórico") })
        content.addView(journey, margin(0, 10))

        val bands = card()
        val bhead = horizontal()
        bhead.addView(label("Suas Faixas", 17f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        bhead.addView(action("⚙ AJUSTAR", false) { showTab("Perfil") })
        bands.addView(bhead)
        bands.addView(label("Confira os valores de cada faixa.", 12f, muted, false, 0, 10))
        val table = horizontal()
        table.addView(rangeColumn("RUIM", red, " < " + money(prefs.getFloat("min_per_km", 1.8f)), " < " + money(prefs.getFloat("min_per_hour", 35f))), weight())
        table.addView(rangeColumn("ATENÇÃO", amber, money(prefs.getFloat("min_per_km", 1.8f)) + " a " + money(prefs.getFloat("min_per_km", 1.8f) * 1.15f), "Meta próxima"), weight())
        table.addView(rangeColumn("ACEITAR", green, "≥ " + money(prefs.getFloat("min_per_km", 1.8f)), "≥ " + money(prefs.getFloat("min_per_hour", 35f))), weight())
        bands.addView(table)
        bands.addView(label("R$/km                         R$/hora", 10f, muted, false, 0, 0))
        content.addView(bands, margin(0, 10))
    }

    private fun buildHistory() {
        content.addView(label("Histórico", 24f, white, true, 5, 2))
        content.addView(label("Suas corridas analisadas", 13f, muted, false, 0, 14))
        val c = card()
        c.addView(label("Ainda não há corridas registradas.", 15f, white, true))
        c.addView(label("Quando uma oferta for lida e calculada, os registros aparecerão aqui.", 12f, muted, false, 0, 8))
        content.addView(c)
        content.addView(card().apply {
            addView(label("RESUMO DA JORNADA", 11f, gold, true))
            addView(label("Tempo trabalhado: —", 14f, white, false, 0, 8))
            addView(label("Ofertas analisadas: 0", 14f, white))
        }, margin(0, 10))
    }

    private fun buildFinances() {
        content.addView(label("Finanças", 24f, white, true, 5, 2))
        content.addView(label("Acompanhe seus resultados", 13f, muted, false, 0, 14))
        val c = card()
        c.addView(label("GANHOS DE HOJE", 11f, muted, true))
        c.addView(label("R$ 0,00", 30f, gold, true, 5, 12))
        val line = horizontal()
        line.addView(financeStat("POR KM", "—"), weight())
        line.addView(financeStat("POR HORA", "—"), weight())
        line.addView(financeStat("CORRIDAS", "0"), weight())
        c.addView(line)
        content.addView(c)
        content.addView(card().apply {
            addView(label("Relatórios financeiros", 16f, white, true))
            addView(label("Os totais serão preenchidos conforme as corridas forem registradas pelo leitor.", 12f, muted, false, 0, 8))
        }, margin(0, 10))
    }

    private fun buildProfile() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        content.addView(label("Perfil e ajustes", 24f, white, true, 5, 12))
        val limits = card()
        limits.addView(label("SUAS METAS", 11f, gold, true))
        val km = edit("Mínimo por KM", prefs.getFloat("min_per_km", 1.8f).toString())
        val hour = edit("Mínimo por hora", prefs.getFloat("min_per_hour", 35f).toString())
        limits.addView(label("MÍNIMO POR KM", 10f, muted, true, 0, 5))
        limits.addView(km)
        limits.addView(label("MÍNIMO POR HORA", 10f, muted, true, 10, 5))
        limits.addView(hour)
        limits.addView(action("SALVAR METAS", true) {
            val k = km.text.toString().replace(",", ".").toFloatOrNull()
            val h = hour.text.toString().replace(",", ".").toFloatOrNull()
            if (k == null || h == null || k <= 0 || h <= 0) toast("Informe metas válidas.")
            else {
                prefs.edit().putFloat("min_per_km", k).putFloat("min_per_hour", h).apply()
                toast("Metas salvas.")
            }
        })
        content.addView(limits)
        val permissions = card()
        permissions.addView(label("PERMISSÕES DO ANDROID", 11f, gold, true))
        permissions.addView(action("PERMITIR SOBREPOSIÇÃO", false) {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else toast("Sobreposição já autorizada.")
        })
        permissions.addView(action("ATIVAR LEITURA DE OFERTAS", false) {
            val component = ComponentName(this, OfferAccessibilityService::class.java)
            try {
                startActivity(Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME", component.flattenToString()))
            } catch (_: Exception) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        permissions.addView(action("AJUSTAR TAMANHO DA ETIQUETA", false) {
            val seek = SeekBar(this).apply {
                max = 45
                progress = (prefs.getInt("overlay_scale", 85) - 65).coerceIn(0, 45)
            }
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); addView(label("Tamanho da etiqueta", 13f, white, true)); addView(seek) }
            AlertDialogBuilder.show(this, box, "Salvar") {
                prefs.edit().putInt("overlay_scale", 65 + seek.progress).apply()
                toast("Tamanho salvo.")
            }
        })
        content.addView(permissions, margin(0, 10))
        content.addView(card().apply {
            addView(label("SOBRE O JHONDRIVER", 11f, gold, true))
            addView(label("Versão inicial • Análise de ofertas Uber e 99. Sem câmera.", 12f, muted, false, 0, 0))
        }, margin(0, 10))
    }

    private fun rangeColumn(title: String, color: Int, km: String, hour: String): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(Color.rgb(31, 29, 26), 12); setPadding(dp(5), dp(12), dp(5), dp(12)) }
        col.addView(label("●", 15f, color, true))
        col.addView(label(title, 10f, color, true))
        col.addView(label(km, 12f, white, true, 0, 8))
        col.addView(label(hour, 11f, muted, false))
        return col
    }

    private fun metric(title: String, value: String): LinearLayout {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.rgb(34, 32, 28), 12); setPadding(dp(9), dp(11), dp(7), dp(11)) }
        v.addView(label(title, 9f, muted, true))
        v.addView(label(value, 15f, gold, true, 5, 0))
        return v
    }
    private fun financeStat(title: String, value: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label(title, 9f, muted, true)); addView(label(value, 15f, gold, true, 4, 0)) }
    private fun verticalText(title: String, subtitle: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(5), 0); addView(label(title, 16f, white, true)); addView(label(subtitle, 11f, muted, false, 3, 0)) }
    private fun iconTile(symbol: String) = TextView(this).apply { text = symbol; textSize = 21f; gravity = Gravity.CENTER; setTextColor(gold); background = rounded(Color.rgb(39, 35, 28), 12); layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)) }
    private fun pill(text: String, color: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(color); setTypeface(null, Typeface.BOLD); setPadding(dp(9), dp(6), dp(9), dp(6)); background = rounded(Color.rgb(28, 44, 35), 20) }
    private fun edit(hintText: String, value: String) = EditText(this).apply {
        hint = hintText; setSingleLine(true); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(value.replace(".", ",")); textSize = 16f; setTextColor(white); setHintTextColor(muted)
        setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(Color.rgb(15, 15, 16), 10, Color.rgb(105, 81, 43))
    }
    private fun action(text: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 11f; isAllCaps = false; setTextColor(if (primary) Color.BLACK else gold)
        setTypeface(null, Typeface.BOLD); background = rounded(if (primary) gold else Color.rgb(37, 34, 28), 12)
        minHeight = dp(44); stateListAnimator = null; setOnClickListener { onClick() }
        layoutParams = margin(0, 7)
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(panel, 17, Color.rgb(55, 46, 32))
    }
    private fun label(text: String, size: Float, color: Int, bold: Boolean, top: Int = 0, bottom: Int = 0) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
        if (top != 0 || bottom != 0) setPadding(0, dp(top), 0, dp(bottom))
    }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) }
    private fun margin(bottom: Int = 0, top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom); topMargin = dp(top) }
    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke) }
    private fun money(v: Float) = String.format(java.util.Locale.US, "%.2f", v)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

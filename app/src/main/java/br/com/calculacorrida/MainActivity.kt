package br.com.calculacorrida

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.content.Context
import android.widget.*
import android.content.res.ColorStateList

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 12, 13)
    private val panel = Color.rgb(25, 24, 22)
    private val gold = Color.rgb(218, 170, 78)
    private val white = Color.rgb(247, 244, 237)
    private val muted = Color.rgb(160, 154, 143)
    private val green = Color.rgb(42, 166, 111)
    private val red = Color.rgb(202, 65, 65)
    private val amber = Color.rgb(204, 139, 43)
    private val border = Color.rgb(60, 56, 50)
    private val bgInput = Color.rgb(35, 33, 30)

    private lateinit var content: LinearLayout
    private lateinit var bottom: LinearLayout
    private var selectedTab = "Início"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verifica login - se não estiver logado, vai para tela de login
        if (!SessionManager(this).isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

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
        val tabs = listOf(
            Triple("Início", "⌂", "Início"),
            Triple("Histórico", "◷", "Histórico"),
            Triple("Finanças", "▤", "Finanças"),
            Triple("Perfil", "◎", "Perfil")
        )
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
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("copilot_enabled", checked).apply() }
        }
        copilotRow.addView(aiSwitch)
        copilot.addView(copilotRow)
        copilot.addView(label("O copiloto lê os dados visíveis da oferta e apresenta os cálculos. Não aceita nem recusa corridas.", 11f, muted, false, 0, 5))
        content.addView(copilot, margin(0, 10))

        // Semáforo
        val traffic = card()
        val trafficRow = horizontal()
        trafficRow.addView(iconTile("🚦"))
        trafficRow.addView(verticalText("Semáforo de Valores", "Uber • 99 • classificação por faixa"), LinearLayout.LayoutParams(0, -2, 1f))
        val trafficSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("traffic_enabled", true)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("traffic_enabled", checked).apply() }
        }
        trafficRow.addView(trafficSwitch)
        traffic.addView(trafficRow)
        traffic.addView(label("RUIM    ATENÇÃO    ACEITAR", 11f, gold, true, 8, 0))
        content.addView(traffic, margin(0, 10))

        // Jornada
        val journey = card()
        val jrow = horizontal()
        jrow.addView(iconTile("◷"))
        jrow.addView(verticalText("Jornada", "Monitore seu tempo de trabalho."), LinearLayout.LayoutParams(0, -2, 1f))
        jrow.addView(label("›", 25f, muted, false))
        journey.addView(jrow)
        journey.addView(action("VER RELATÓRIO", false) { showTab("Histórico") })
        content.addView(journey, margin(0, 10))

        // Faixas
        val bands = card()
        val bhead = horizontal()
        bhead.addView(label("Suas Faixas", 17f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        bhead.addView(action("⚙ AJUSTAR", false) { showTab("Perfil") })
        bands.addView(bhead)
        bands.addView(label("Confira os valores de cada faixa.", 12f, muted, false, 0, 10))
        val table = horizontal()
        table.addView(rangeColumn("RUIM", red, "< " + money(prefs.getFloat("min_per_km", 1.8f)), "< " + money(prefs.getFloat("min_per_hour", 35f))), weight())
        table.addView(rangeColumn("ATENÇÃO", amber, money(prefs.getFloat("min_per_km", 1.8f)) + " a " + money(prefs.getFloat("min_per_km", 1.8f) * 1.15f), "Meta próxima"), weight())
        table.addView(rangeColumn("ACEITAR", green, "≥ " + money(prefs.getFloat("min_per_km", 1.8f)), "≥ " + money(prefs.getFloat("min_per_hour", 35f))), weight())
        bands.addView(table)
        bands.addView(label("R$/km          R$/hora", 10f, muted, false, 0, 0))
        content.addView(bands, margin(0, 10))

        // Permissões
        val perms = card()
        val permsRow = horizontal()
        permsRow.addView(iconTile("🔐"))
        permsRow.addView(verticalText("Permissões", "Acessibilidade e Sobreposição"), LinearLayout.LayoutParams(0, -2, 1f))
        perms.addView(permsRow)

        val accOn = isAccessibilityEnabled()
        val overlayOn = Settings.canDrawOverlays(this)

        perms.addView(permissionRow("Acessibilidade", accOn) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        perms.addView(permissionRow("Sobreposição de tela", overlayOn) {
            if (!overlayOn) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        content.addView(perms, margin(0, 10))
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
            addView(label("Ofertas analisadas: 0", 14f, white, false))
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

        // Usuário
        val userCard = card()
        userCard.addView(label("CONTA", 11f, gold, true))
        userCard.addView(label(SessionManager(this).getEmail() ?: "usuario@email.com", 16f, white, true, 8, 0))
        content.addView(userCard, margin(0, 10))

        // Metas
        val limits = card()
        limits.addView(label("SUAS METAS", 11f, gold, true))

        limits.addView(settingRow("R$/km mínimo", money(prefs.getFloat("min_per_km", 1.8f))))
        limits.addView(settingRow("R$/hora mínimo", money(prefs.getFloat("min_per_hour", 35f))))
        limits.addView(settingRow("Custo R$/km (combustível)", money(prefs.getFloat("cost_per_km", 0.65f))))

        limits.addView(label("OPÇÕES DA TAG", 11f, gold, true, 14, 0))

        val showRatingSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("show_rating", true)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("show_rating", checked).apply() }
        }
        limits.addView(toggleRow("Mostrar nota do passageiro", showRatingSwitch))

        val showTimeSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("show_time", true)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("show_time", checked).apply() }
        }
        limits.addView(toggleRow("Mostrar tempo da corrida", showTimeSwitch))

        val showProfitSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("show_profit", false)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("show_profit", checked).apply() }
        }
        limits.addView(toggleRow("Mostrar lucro %", showProfitSwitch))

        val voiceSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("voice_enabled", false)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("voice_enabled", checked).apply() }
        }
        limits.addView(toggleRow("Voz (falar resultado)", voiceSwitch))

        content.addView(limits, margin(0, 10))

        // Posição da tag
        val posCard = card()
        posCard.addView(label("POSIÇÃO DA TAG", 11f, gold, true))
        val posRow = horizontal()
        posRow.addView(posButton("Esquerda", "left", prefs), weight())
        posRow.addView(posButton("Direita", "right", prefs), weight())
        posCard.addView(posRow)
        content.addView(posCard, margin(0, 10))

        // Logout
        val logoutBtn = Button(this).apply {
            text = "SAIR DA CONTA"
            setTextColor(red)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), red)
            }
            setOnClickListener {
                SessionManager(this@MainActivity).logout()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
        content.addView(logoutBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
    }

    // === HELPERS ===

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(panel, 20, gold)
        layoutParams = margin(0, 10)
    }

    private fun horizontal(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun weight(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)

    private fun margin(l: Int, t: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply {
        if (l >= 0) leftMargin = dp(l)
        if (t >= 0) topMargin = dp(t)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean, top: Int = 0, bottom: Int = 0): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (top > 0) setPadding(0, dp(top), 0, 0)
        if (bottom > 0) setPadding(0, if (top > 0) dp(top) else 0, 0, dp(bottom))
    }

    private fun pill(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.BLACK)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = rounded(color, 12, color)
    }

    private fun iconTile(icon: String): TextView = TextView(this).apply {
        this.text = icon
        textSize = 20f
        setTextColor(gold)
        gravity = Gravity.CENTER
        setPadding(0, 0, dp(12), 0)
    }

    private fun verticalText(title: String, sub: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title, 15f, white, true))
        addView(label(sub, 11f, muted, false, 2, 0))
    }

    private fun metric(title: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title, 10f, muted, true))
        addView(label(value, 18f, white, true, 4, 0))
    }

    private fun financeStat(title: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title, 10f, muted, true))
        addView(label(value, 15f, white, true, 2, 0))
    }

    private fun action(text: String, _ignored: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        setTextColor(gold)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(4))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun rangeColumn(title: String, color: Int, km: String, hour: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(label(title, 10f, color, true))
        addView(label(km, 12f, white, true, 4, 0))
        addView(label(hour, 10f, muted, false, 2, 0))
    }

    private fun settingRow(title: String, value: String): LinearLayout {
        val row = horizontal()
        row.addView(label(title, 14f, white, false), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(value, 14f, gold, true))
        row.setPadding(0, dp(10), 0, dp(10))
        return row
    }

    private fun toggleRow(title: String, sw: Switch): LinearLayout {
        val row = horizontal()
        row.addView(label(title, 14f, white, false), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(sw)
        row.setPadding(0, dp(10), 0, dp(10))
        return row
    }

    private fun posButton(text: String, key: String, prefs: android.content.SharedPreferences): TextView = TextView(this).apply {
        val current = prefs.getString("overlay_position", "right")
        val isSelected = key == current
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (isSelected) Color.BLACK else gold)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(if (isSelected) gold else panel, 10, if (isSelected) gold else border)
        isClickable = true
        setOnClickListener {
            prefs.edit().putString("overlay_position", key).apply()
            showTab("Perfil")
        }
    }

    private fun permissionRow(title: String, granted: Boolean, onClick: () -> Unit): LinearLayout {
        val row = horizontal()
        row.setPadding(0, dp(8), 0, dp(8))
        row.addView(label(title, 13f, white, false), LinearLayout.LayoutParams(0, -2, 1f))
        val status = label(if (granted) "✓ ON" else "✗ OFF", 12f, if (granted) green else red, true)
        row.addView(status)
        if (!granted) {
            val btn = label(" ATIVAR", 12f, gold, true)
            btn.isClickable = true
            btn.setOnClickListener { onClick() }
            row.addView(btn)
        }
        return row
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = componentName.flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(expected)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun money(v: Float): String = "R$ " + String.format("%.2f", v).replace(".", ",")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

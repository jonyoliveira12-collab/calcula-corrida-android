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
import android.view.accessibility.AccessibilityManager
import android.widget.*
import android.app.AlertDialog
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
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(7), dp(5), dp(12))
        }
        val brandRow = horizontal()
        brandRow.addView(label("JD", 19f, Color.BLACK, true).apply {
            gravity = Gravity.CENTER
            background = rounded(gold, 14, Color.rgb(245, 205, 112))
            setPadding(dp(11), dp(8), dp(11), dp(8))
        })
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            addView(label("JHONDRIVER", 21f, white, true))
            addView(label("INTELIGÊNCIA PARA CADA CORRIDA", 9f, muted, true))
        }
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        val avatar = ImageView(this).apply {
            val size = dp(44)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(55, 45, 28), 24, gold)
            clipToOutline = true
            val savedPhoto = getSharedPreferences("settings", MODE_PRIVATE).getString("profile_photo_uri", null)
            if (!savedPhoto.isNullOrBlank()) try { setImageURI(Uri.parse(savedPhoto)) } catch (_: Exception) {}
            if (drawable == null) setImageResource(android.R.drawable.ic_menu_camera)
            contentDescription = "Foto do motorista. Toque para alterar."
            setOnClickListener { chooseProfilePhoto() }
        }
        brandRow.addView(avatar)
        brandRow.addView(action("⚙", false) { showTab("Perfil") }.apply { textSize = 20f; setPadding(dp(12), dp(8), dp(4), dp(8)) })
        header.addView(brandRow)
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
            background = rounded(Color.rgb(18, 19, 20), 18, border)
            setPadding(dp(3), dp(5), dp(3), dp(5))
        }
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(72)))

        setContentView(root)
        showTab("Início")
    }

    override fun onResume() {
        super.onResume()
        // Atualiza o indicador assim que o usuário retorna das configurações do Android.
        if (::content.isInitialized && (selectedTab == "Perfil" || selectedTab == "Início")) {
            showTab(selectedTab)
        }
    }

    // Voltar envia o app para segundo plano sem encerrar a sessão.
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun showTab(tab: String) {
        selectedTab = tab
        content.removeAllViews()
        when (tab) {
            "Início" -> buildHome()
            "Histórico" -> buildHistory()
            "Finanças" -> buildFinances()
            "Semáforo" -> buildTrafficSettings()
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
        val online = isOperationalOnline()
        row.addView(pill(if (online) "● ONLINE" else "● OFF", if (online) green else red))
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
            isChecked = prefs.getBoolean("copilot_enabled", false)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked ->
                if (checked && !ensureRequiredPermissions()) {
                    isChecked = false
                    prefs.edit().putBoolean("copilot_enabled", false).apply()
                } else {
                    prefs.edit().putBoolean("copilot_enabled", checked).apply()
                    if (checked) requestAccessibilityIfNeeded()
                }
            }
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
            isChecked = prefs.getBoolean("traffic_enabled", false)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked ->
                if (checked && !ensureRequiredPermissions()) {
                    isChecked = false
                    prefs.edit().putBoolean("traffic_enabled", false).apply()
                } else {
                    prefs.edit().putBoolean("traffic_enabled", checked).apply()
                    if (checked) requestAccessibilityIfNeeded()
                }
            }
        }
        trafficRow.addView(trafficSwitch)
        trafficRow.addView(action("⚙", false) { showTab("Semáforo") })
        traffic.addView(trafficRow)
        traffic.addView(label(
            prefs.getString("label_bad", "Vixe não dá") + "    " +
            prefs.getString("label_review", "Hum, talvez") + "    " +
            prefs.getString("label_good", "Bora carai"),
            11f, gold, true, 8, 0
        ))
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
        table.addView(rangeColumn(prefs.getString("label_bad", "Vixe não dá") ?: "Vixe não dá", red, "< " + money(prefs.getFloat("bad_per_km", 1.5f)), "< " + money(prefs.getFloat("min_per_hour", 50f))), weight())
        table.addView(rangeColumn(prefs.getString("label_review", "Hum, talvez") ?: "Hum, talvez", amber, money(prefs.getFloat("bad_per_km", 1.5f)) + " a " + money(prefs.getFloat("min_per_km", 1.8f)), money(prefs.getFloat("min_per_hour", 50f)) + " a " + money(prefs.getFloat("good_per_hour", 60f))), weight())
        table.addView(rangeColumn(prefs.getString("label_good", "Bora carai") ?: "Bora carai", green, "≥ " + money(prefs.getFloat("min_per_km", 1.8f)), "≥ " + money(prefs.getFloat("good_per_hour", 60f))), weight())
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

    private fun chooseProfilePhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 4102)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4102 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putString("profile_photo_uri", uri.toString()).apply()
            showTab(selectedTab)
        }
    }

    private fun isOperationalOnline(): Boolean {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getBoolean("copilot_enabled", false) &&
            prefs.getBoolean("traffic_enabled", false) &&
            Settings.canDrawOverlays(this) && isAccessibilityEnabled()
    }

    private fun ensureRequiredPermissions(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Autorize aparecer sobre outros aplicativos e volte para ativar.", Toast.LENGTH_LONG).show()
            return false
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Ative a acessibilidade do JHONDRIVER para analisar as ofertas.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return false
        }
        return true
    }

    private fun requestAccessibilityIfNeeded() {
        if (!isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun buildTrafficSettings() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        content.addView(label("Semáforo de Valores", 24f, white, true, 5, 2))
        content.addView(label("Configure os limites. Cada oferta será classificada automaticamente.", 13f, muted, false, 0, 12))

        val names = card()
        names.addView(label("NOMES DAS FAIXAS", 11f, gold, true))
        fun editLabel(title: String, key: String, defaultValue: String) {
            val row = horizontal()
            row.addView(label(title, 14f, white, false), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(action(prefs.getString(key, defaultValue) ?: defaultValue, false) {
                val input = EditText(this).apply { setText(prefs.getString(key, defaultValue) ?: defaultValue); setSingleLine(true) }
                AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancelar", null)
                    .setPositiveButton("Salvar") { _, _ ->
                        val value = input.text.toString().trim()
                        if (value.isNotEmpty()) prefs.edit().putString(key, value).apply()
                        showTab("Semáforo")
                    }.show()
            })
            row.setPadding(0, dp(7), 0, dp(7))
            names.addView(row)
        }
        editLabel("Faixa vermelha", "label_bad", "Vixe não dá")
        editLabel("Faixa amarela", "label_review", "Hum, talvez")
        editLabel("Faixa verde", "label_good", "Bora carai")
        content.addView(names, margin(0, 8))

        content.addView(label("CLASSIFICAÇÃO AUTOMÁTICA", 12f, gold, true, 4, 2))
        content.addView(label("A faixa vermelha começa abaixo do primeiro limite; a verde começa ao atingir o segundo. Entre eles, a oferta fica amarela.", 11f, muted, false, 0, 6))

        fun rangeControl(title: String, lowKey: String, highKey: String, lowDefault: Float, highDefault: Float, scale: Int, suffix: String) {
            val box = card()
            val head = horizontal()
            head.addView(label(title, 15f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
            val values = label("", 12f, gold, true)
            head.addView(values)
            box.addView(head)

            fun format(v: Float): String = suffix + String.format(java.util.Locale("pt", "BR"), "%.2f", v)
            val low = (prefs.getFloat(lowKey, lowDefault) * scale).toInt().coerceIn(0, 10000)
            val high = (prefs.getFloat(highKey, highDefault) * scale).toInt().coerceIn(0, 10000)
            var highSeekValue = high.toFloat() / scale
            val lowText = label("● Ruim até " + format(low.toFloat() / scale), 11f, red, true, 6, 0)
            val lowSeek = SeekBar(this).apply {
                max = 1000
                progress = low.coerceIn(0, max)
                progressTintList = ColorStateList.valueOf(red)
                thumbTintList = ColorStateList.valueOf(red)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = progress.toFloat() / scale
                        lowText.text = "● Ruim até " + format(value)
                        values.text = format(value) + "  |  " + format(highSeekValue)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val value = (seekBar?.progress ?: low) .toFloat() / scale
                        prefs.edit().putFloat(lowKey, value).apply()
                    }
                })
            }
            val highText = label("● Bom a partir de " + format(highSeekValue), 11f, green, true, 3, 0)
            val highSeek = SeekBar(this).apply {
                max = 1000
                progress = high.coerceIn(0, max)
                progressTintList = ColorStateList.valueOf(green)
                thumbTintList = ColorStateList.valueOf(green)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        highSeekValue = progress.toFloat() / scale
                        highText.text = "● Bom a partir de " + format(highSeekValue)
                        values.text = format(lowSeek.progress.toFloat() / scale) + "  |  " + format(highSeekValue)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        prefs.edit().putFloat(highKey, highSeekValue).apply()
                    }
                })
            }
            values.text = format(low.toFloat() / scale) + "  |  " + format(highSeekValue)
            box.addView(lowText)
            box.addView(lowSeek)
            box.addView(highText)
            box.addView(highSeek)
            content.addView(box, margin(0, 7))
        }

        rangeControl("Ganho por Km", "bad_per_km", "min_per_km", 1.50f, 1.80f, 100, "R$ ")
        rangeControl("Ganho por Hora", "min_per_hour", "good_per_hour", 50f, 60f, 1, "R$ ")
        rangeControl("Nota do Passageiro", "min_rating", "good_rating", 4.80f, 4.90f, 100, "★ ")

        val minimums = card()
        minimums.addView(label("GANHOS MÍNIMOS", 11f, gold, true))
        fun editMinimum(title: String, key: String, defaultValue: Float) {
            val row = horizontal()
            row.addView(label(title, 14f, white, false), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(action(money(prefs.getFloat(key, defaultValue)), false) {
                val input = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(prefs.getFloat(key, defaultValue).toString())
                    setSingleLine(true)
                }
                AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancelar", null)
                    .setPositiveButton("Salvar") { _, _ ->
                        input.text.toString().replace(",", ".").toFloatOrNull()?.let { if (it >= 0f) prefs.edit().putFloat(key, it).apply() }
                        showTab("Semáforo")
                    }.show()
            })
            row.setPadding(0, dp(8), 0, dp(8))
            minimums.addView(row)
        }
        editMinimum("Valor mínimo da viagem", "min_fare", 10f)
        editMinimum("Lucro líquido mínimo", "min_profit", 0f)
        content.addView(minimums, margin(0, 7))
        content.addView(action("VOLTAR AO INÍCIO", false) { showTab("Início") })
    }

    private fun buildHistory() {
        val records = JourneyStore.getRecords(this)
        content.addView(label("Jornada • Relatório", 24f, white, true, 5, 2))
        content.addView(label("Cada oferta identificada pelo leitor fica salva automaticamente aqui.", 13f, muted, false, 0, 14))

        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = records.filter { it.optLong("timestamp") >= todayStart }
        val avgPerKm = if (today.isNotEmpty()) today.map { it.optDouble("perKm") }.average() else 0.0

        val summary = card()
        summary.addView(label("RESUMO DE HOJE", 11f, gold, true))
        val stats = horizontal()
        stats.addView(metric("OFERTAS LIDAS", today.size.toString()), weight())
        stats.addView(metric("MÉDIA POR KM", if (today.isNotEmpty()) money(avgPerKm.toFloat()) else "—"), weight())
        stats.addView(metric("TOTAL NO HISTÓRICO", records.size.toString()), weight())
        summary.addView(stats)
        if (today.size >= 2) {
            val first = today.minOf { it.optLong("timestamp") }
            val last = today.maxOf { it.optLong("timestamp") }
            val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR"))
            summary.addView(label("Período entre ofertas: ${fmt.format(java.util.Date(first))} – ${fmt.format(java.util.Date(last))}", 11f, muted, false, 10, 0))
        } else {
            summary.addView(label("As ofertas serão registradas automaticamente assim que forem identificadas.", 11f, muted, false, 8, 0))
        }
        content.addView(summary, margin(0, 8))

        content.addView(label("OFERTAS REGISTRADAS", 12f, gold, true, 4, 2))
        if (records.isEmpty()) {
            content.addView(card().apply {
                addView(label("Nenhuma oferta salva ainda.", 15f, white, true))
                addView(label("Ative o Semáforo e abra Uber, 99 ou outro app monitorado. Quando uma oferta aparecer e for calculada, ela será adicionada aqui automaticamente.", 12f, muted, false, 6, 0))
            }, margin(0, 7))
            return
        }

        records.forEach { item ->
            val fare = item.optDouble("fare")
            val km = item.optDouble("totalKm")
            val minutes = item.optDouble("minutes")
            val perKm = item.optDouble("perKm")
            val perHour = item.optDouble("perHour")
            val rating = item.optString("passengerRating")
            val app = item.optString("app", "Aplicativo")
            val timestamp = item.optLong("timestamp")
            val date = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR"))
                .format(java.util.Date(timestamp))
            val classification = item.optString("classification")
            val (status, statusColor) = when (classification) {
                "GOOD" -> Pair("ACEITAR", green)
                "REVIEW" -> Pair("ATENÇÃO", amber)
                else -> Pair("REJEITAR", red)
            }
            val entry = card()
            val top = horizontal()
            top.addView(label("$app • $date", 11f, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(pill(status, statusColor))
            entry.addView(top)
            entry.addView(label(money(fare.toFloat()), 25f, white, true, 7, 2))
            entry.addView(label(
                String.format(java.util.Locale("pt", "BR"), "%.1f km • %.0f min • R$ %.2f/km", km, minutes, perKm).replace(".", ","),
                12f, muted, false, 0, 8
            ))
            val metrics = horizontal()
            metrics.addView(financeStat("R$/HORA", money(perHour.toFloat())), weight())
            metrics.addView(financeStat("NOTA", if (rating.isBlank()) "—" else "★ $rating"), weight())
            entry.addView(metrics)
            content.addView(entry, margin(0, 7))
        }
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

        // Perfil do motorista: foto selecionável e identificação da conta.
        val userCard = card()
        val profileRow = horizontal()
        val profileAvatar = ImageView(this).apply {
            val size = dp(66)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(14) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(55, 45, 28), 36, gold)
            clipToOutline = true
            val savedPhoto = prefs.getString("profile_photo_uri", null)
            if (!savedPhoto.isNullOrBlank()) try { setImageURI(Uri.parse(savedPhoto)) } catch (_: Exception) {}
            if (drawable == null) setImageResource(android.R.drawable.ic_menu_camera)
            setOnClickListener { chooseProfilePhoto() }
            contentDescription = "Alterar foto do motorista"
        }
        profileRow.addView(profileAvatar)
        val profileInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("MEU PERFIL", 11f, gold, true))
            addView(label(SessionManager(this@MainActivity).getEmail() ?: "Motorista JHONDRIVER", 14f, white, true, 6, 2))
            addView(action("ALTERAR FOTO", false) { chooseProfilePhoto() })
        }
        profileRow.addView(profileInfo, LinearLayout.LayoutParams(0, -2, 1f))
        userCard.addView(profileRow)
        content.addView(userCard, margin(0, 10))

        // Metas
        val limits = card()
        limits.addView(label("SUAS METAS", 11f, gold, true))

        fun editableGoal(title: String, key: String, defaultValue: Float) {
            val row = horizontal()
            row.addView(label(title, 14f, white, false), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(action(money(prefs.getFloat(key, defaultValue)) + "  ✎", false) {
                val field = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(prefs.getFloat(key, defaultValue).toString())
                    setSingleLine(true)
                }
                AlertDialog.Builder(this).setTitle(title).setView(field)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Salvar") { _, _ ->
                        field.text.toString().replace(",", ".").toFloatOrNull()?.let { value ->
                            if (value >= 0f) prefs.edit().putFloat(key, value).apply()
                        }
                        showTab("Perfil")
                    }.show()
            })
            row.setPadding(0, dp(9), 0, dp(9))
            limits.addView(row)
        }
        editableGoal("R$/km mínimo", "min_per_km", 1.8f)
        editableGoal("R$/hora mínimo", "min_per_hour", 50f)
        editableGoal("Custo R$/km (combustível)", "cost_per_km", 0.65f)

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

        val configCard = card()
        configCard.addView(label("CENTRAL DE CONFIGURAÇÕES", 11f, gold, true))
        configCard.addView(label("Personalize as faixas e os critérios usados para analisar cada oferta.", 12f, muted, false, 5, 5))
        configCard.addView(action("CONFIGURAR SEMÁFORO  ›", false) { showTab("Semáforo") })
        content.addView(configCard, margin(0, 10))

        val backgroundCard = card()
        backgroundCard.addView(label("FUNCIONAMENTO EM SEGUNDO PLANO", 11f, gold, true))
        backgroundCard.addView(label("Mantenha a acessibilidade e a permissão de sobreposição ativadas para o leitor e a etiqueta funcionarem sobre Uber e 99.", 12f, muted, false, 5, 8))
        backgroundCard.addView(permissionRow("Acessibilidade", isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        backgroundCard.addView(permissionRow("Sobreposição de tela", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        content.addView(backgroundCard, margin(0, 10))

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
        background = rounded(panel, 20, border)
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
        return try {
            val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val service = info.resolveInfo?.serviceInfo
                    service?.packageName == packageName &&
                        service.name == OfferAccessibilityService::class.java.name
                }
        } catch (_: Exception) {
            val expected = android.content.ComponentName(this, OfferAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun money(v: Float): String = "R$ " + String.format("%.2f", v).replace(".", ",")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

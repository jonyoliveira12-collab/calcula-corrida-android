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
import android.app.AlertDialog
import android.content.res.ColorStateList

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 10, 12)
    private val panel = Color.rgb(23, 23, 26)
    private val gold = Color.rgb(239, 190, 65)
    private val white = Color.rgb(247, 244, 232)
    private val muted = Color.rgb(176, 172, 158)
    private val green = Color.rgb(20, 166, 123)
    private val red = Color.rgb(226, 75, 100)
    private val amber = Color.rgb(242, 157, 45)
    private val border = Color.rgb(132, 103, 42)
    private val bgInput = Color.rgb(35, 35, 39)

    private lateinit var content: LinearLayout
    private lateinit var bottom: LinearLayout
    private var selectedTab = "Início"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // O aplicativo exige internet validada para abrir e operar.
        if (!NetworkGate.isOnline(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "JHONDRIVER precisa de internet para funcionar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

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
            background = getDrawable(R.drawable.home_background)
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(16, 16, 19), Color.rgb(54, 42, 20), Color.rgb(17, 17, 20))
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(190, 145, 42))
            }
            elevation = dp(5).toFloat()
        }
        val brandRow = horizontal()
        brandRow.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setImageResource(R.drawable.jhondriver_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logo JHONDRIVER"
        })
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            addView(label("JHONDRIVER", 21f, Color.WHITE, true))
            addView(label("INTELIGÊNCIA PARA CADA PERCURSO", 9f, gold, true))
        }
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        val avatar = ImageView(this).apply {
            val size = dp(44)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 24, gold)
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
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(23, 23, 26), Color.rgb(18, 18, 21))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(132, 103, 42))
            }
            elevation = dp(8).toFloat()
            setPadding(dp(3), dp(5), dp(3), dp(5))
        }
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(72)))

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, dp(4) + bars.bottom)
            insets
        }
        setContentView(root)
        AppUpdates.check(this)
        showTab("Início")
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) AppUpdates.check(this)
        if (!NetworkGate.isOnline(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "Conexão perdida. Reconecte-se à internet para continuar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
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
                if (active) background = rounded(Color.rgb(65, 47, 12), 14, gold)
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
        val records = JourneyStore.getRecords(this)
        val wide = resources.configuration.screenWidthDp >= 600
        val analysis = card()
        analysis.addView(label("ANALISAR OFERTA", 20f, gold, true))
        analysis.addView(label("Escolha uma foto da oferta ou digite os valores. A imagem é lida no aparelho; confira os dados antes de calcular.", 13f, white, false, 8, 8))
        analysis.addView(action("ESCOLHER FOTO OU DIGITAR  ›", false) {
            startActivity(Intent(this, ManualOfferActivity::class.java))
        })
        content.addView(analysis, margin(0,10))
        val offerCard = card().apply { background = rounded(panel, 22, gold) }
        offerCard.addView(label("⚡ ÚLTIMA OFERTA", 21f, gold, true, 0, 18))
        val offer = records.firstOrNull()
        fun number(key: String): String = offer?.let { String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", it.optDouble(key)) } ?: "—"
        val totals = horizontal()
        totals.addView(metric("VALOR", if (offer == null) "—" else "R$ " + number("fare")), weight())
        totals.addView(metric("PERCURSO TOTAL", number("totalKm") + " km"), weight())
        totals.addView(metric("TEMPO TOTAL", number("minutes") + " min"), weight())
        offerCard.addView(totals)
        offerCard.addView(View(this).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(18); bottomMargin = dp(18) })
        val rates = horizontal()
        rates.addView(metric("R$/KM", if (offer == null) "—" else "R$ " + number("perKm")), weight())
        rates.addView(metric("R$/HORA", if (offer == null) "—" else "R$ " + number("perHour")), weight())
        offerCard.addView(rates)
        val classification = offer?.optString("classification")
        offerCard.addView(label(when (classification) { "GOOD" -> "✓ BOA CORRIDA"; "BAD" -> "ABAIXO DOS LIMITES"; "REVIEW" -> "AVALIE A CORRIDA"; else -> "AGUARDANDO UMA OFERTA" }, 16f, if (offer == null) muted else white, true, 18, 8).apply {
            gravity = Gravity.CENTER
            background = rounded(when (classification) { "GOOD" -> Color.rgb(16, 85, 36); "BAD" -> Color.rgb(99, 26, 26); "REVIEW" -> Color.rgb(87, 62, 14); else -> Color.rgb(31,31,34) }, 12, border)
        }, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(18) })
        offerCard.addView(label(if (offer == null) "Escolha uma foto ou digite os valores de uma oferta." else "Valores brutos • inclui chegada quando informada na oferta.", 12f, muted, false, 12, 0))

        val filters = card()
        filters.addView(label("☷ FILTRO DE GANHO", 16f, gold, true, 0, 14))
        filters.addView(label("Mínimo por km", 13f, muted, false))
        filters.addView(label(money(prefs.getFloat("min_per_km", 1.8f)), 23f, gold, true, 4, 16))
        filters.addView(label("Mínimo por hora", 13f, muted, false))
        filters.addView(label(money(prefs.getFloat("good_per_hour", 60f)), 23f, gold, true, 4, 8))
        filters.addView(action("AJUSTAR LIMITES  ›", false) { showTab("Semáforo") })
        if (wide) {
            val main = horizontal().apply { gravity = Gravity.TOP }
            main.addView(offerCard, LinearLayout.LayoutParams(0, -2, 2f).apply { topMargin = dp(14); rightMargin = dp(12) })
            main.addView(filters, LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(14) })
            content.addView(main)
        } else {
            content.addView(offerCard, margin(0, 14))
            content.addView(filters, margin(0, 12))
        }
        val summaries = horizontal()
        val count = card().apply { addView(metric("OFERTAS ANALISADAS", records.size.toString())) }
        val average = card().apply { addView(metric("MÉDIA R$/KM", if (records.isEmpty()) "—" else "R$ " + String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", records.map { it.optDouble("perKm") }.average()))) }
        summaries.addView(count, LinearLayout.LayoutParams(0,-2,1f).apply { topMargin=dp(12);rightMargin=dp(6) })
        summaries.addView(average, LinearLayout.LayoutParams(0,-2,1f).apply { topMargin=dp(12);leftMargin=dp(6) })
        content.addView(summaries)
        val settings = card()
        settings.addView(label("PRONTO PARA TRABALHAR?", 14f, white, true))
        settings.addView(label("Esta edição analisa somente os dados que você escolhe informar.", 12f, muted, false, 6, 2))
        settings.addView(action("MEUS AJUSTES  ›", false) { showTab("Perfil") })
        content.addView(settings, margin(0,12))
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
            addView(label("Ofertas analisadas não representam corridas realizadas. O histórico guarda suas análises.", 12f, muted, false, 0, 8))
        }, margin(0, 10))
    }

    private fun buildProfile() {
        content.addView(label("Perfil e ajustes", 24f, white, true, 5, 12))
        val privacy = card()
        privacy.addView(label("Análise por foto ou valores", 18f, gold, true))
        privacy.addView(label("Esta edição não usa acessibilidade, captura automática nem sobreposição em outros aplicativos. Fotos selecionadas são processadas no aparelho.", 13f, white, false, 8, 4))
        content.addView(privacy, margin(0,10))
        val limits = card()
        limits.addView(label("Limites de ganho",18f,gold,true))
        limits.addView(action("AJUSTAR R$/KM E R$/HORA",false) { showTab("Semáforo") })
        content.addView(limits,margin(0,10))
        val updates = card()
        updates.addView(label("Atualizações",18f,gold,true))
        updates.addView(action("VERIFICAR ATUALIZAÇÃO",false) { AppUpdates.check(this,manual=true) })
        content.addView(updates,margin(0,10))
        content.addView(card().apply {
            addView(label(SessionManager(this@MainActivity).getEmail() ?: "JHONDRIVER",14f,white,true))
            addView(action("SAIR DA CONTA",false) {
                SessionManager(this@MainActivity).logout()
                startActivity(Intent(this@MainActivity,LoginActivity::class.java))
                finish()
            })
        },margin(0,10))
    }

    // === HELPERS ===

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(23, 23, 26), Color.rgb(18, 18, 20), Color.rgb(10, 10, 12))
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), border)
        }
        elevation = dp(6).toFloat()
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
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        if (top > 0) setPadding(0, dp(top), 0, 0)
        if (bottom > 0) setPadding(0, if (top > 0) dp(top) else 0, 0, dp(bottom))
    }

    private fun pill(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
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
        addView(label(value, 20f, gold, true, 6, 0))
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

    private fun rounded(color: Int, radius: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun money(v: Float): String = "R$ " + String.format("%.2f", v).replace(".", ",")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

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

    private val bg = Color.rgb(10, 10, 12)
    private val panel = Color.rgb(25, 28, 34)
    private val gold = Color.rgb(239, 190, 65)
    private val white = Color.rgb(247, 244, 232)
    private val muted = Color.rgb(176, 172, 158)
    private val green = Color.rgb(20, 166, 123)
    private val red = Color.rgb(226, 75, 100)
    private val amber = Color.rgb(242, 157, 45)
    private val border = Color.rgb(59, 61, 67)
    private val bgInput = Color.rgb(35, 35, 39)

    private lateinit var content: LinearLayout
    private lateinit var bottom: LinearLayout
    private var selectedTab = "Início"
    private val liveOfferHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var observingOffers=false
    private val refreshLatestOffer=Runnable {
        if(observingOffers && !isFinishing && ::content.isInitialized && selectedTab=="Início") {
            val scroll=content.parent as? ScrollView
            val y=scroll?.scrollY ?: 0
            showTab("Início")
            scroll?.let {host->host.post {host.scrollTo(0,y)}}
        }
    }
    private val liveOfferListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key=="offers" && observingOffers) {
            liveOfferHandler.removeCallbacks(refreshLatestOffer)
            liveOfferHandler.postDelayed(refreshLatestOffer,100)
        }
    }
    override fun onStart() {
        super.onStart()
        observingOffers=true
        getSharedPreferences("journey",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(liveOfferListener)
    }
    override fun onStop() {
        observingOffers=false
        getSharedPreferences("journey",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(liveOfferListener)
        liveOfferHandler.removeCallbacks(refreshLatestOffer)
        super.onStop()
    }

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
            addView(label("Versão ${packageManager.getPackageInfo(packageName,0).versionName}", 9f, gold, false))
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
        val readerPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val pendingFeature = readerPrefs.getString("reader_pending_feature", null)
        if (pendingFeature != null && readerPrefs.getBoolean("reader_disclosure_accepted", false)) {
            if (Settings.canDrawOverlays(this) && isAccessibilityEnabled()) {
                readerPrefs.edit().putBoolean(pendingFeature, true).remove("reader_pending_feature").remove("reader_permission_step").apply()
            } else if (Settings.canDrawOverlays(this) && readerPrefs.getString("reader_permission_step", null) == "overlay") {
                readerPrefs.edit().putString("reader_permission_step", "accessibility").apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
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
        val enabledServices=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if(ReaderVersions.hasOtherEnabledService(enabledServices,packageName)) {
            val conflict=card()
            conflict.addView(label("Outra versão está lendo corridas",16f,gold,true))
            conflict.addView(label("Desligue a acessibilidade das versões antigas do JHONDRIVER e deixe ativa somente a desta versão. Seus dados antigos continuam guardados.",13f,white,false,4,8))
            conflict.addView(action("ABRIR AJUSTES DE ACESSIBILIDADE  ›",false) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            content.addView(conflict,margin(0,10))
        }
        val diagnostics = card()
        val serviceOn = isAccessibilityEnabled()
        diagnostics.addView(label("LEITURA DE OFERTAS", 12f, gold, true))
        diagnostics.addView(label(if (!serviceOn) "Acessibilidade desligada: ative nos ajustes" else prefs.getString("reader_status", "Serviço ativado; aguardando conexão") ?: "Aguardando oferta", 13f, white, false, 6, 2))
        val heartbeat=prefs.getLong("reader_heartbeat",0)
        val alive=serviceOn && System.currentTimeMillis()-heartbeat in 0..10000
        diagnostics.addView(label(if(alive) "Serviço respondendo em segundo plano" else "Serviço sem resposta recente; confira a acessibilidade",12f,if(alive)green else red,false))
        diagnostics.addView(action(if(prefs.getBoolean("reader_paused",false))"RETOMAR LEITURA" else "PAUSAR LEITURA",false) {
            prefs.edit().putBoolean("reader_paused",!prefs.getBoolean("reader_paused",false)).apply();showTab("Início")
        })
        diagnostics.addView(action("COPIAR DIAGNÓSTICO", false) {
            val report=listOf("JHONDRIVER ${packageManager.getPackageInfo(packageName,0).versionName}","Android ${android.os.Build.VERSION.RELEASE} • ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                "Acessibilidade: $serviceOn • Serviço respondendo: $alive • Sobreposição: ${Settings.canDrawOverlays(this)}",
                "Copiloto: ${prefs.getBoolean("copilot_enabled",false)} • Semáforo: ${prefs.getBoolean("traffic_enabled",false)} • Pausa: ${prefs.getBoolean("reader_paused",false)}",
                "Estado: ${prefs.getString("reader_status","Sem registro")}","Último app: ${prefs.getString("reader_last_app","—")}",
                "Última captura: ${prefs.getLong("reader_last_capture",0)} • Última detecção: ${prefs.getLong("reader_last_detection",0)}",
                "Janelas: ${prefs.getString("reader_window_status","Aguardando")}",
                "Captura: ${prefs.getString("reader_capture_status","Aguardando")}",
                "Reconhecimento: ${prefs.getString("reader_ocr_status","Aguardando")}",
                "Uber: captura ${prefs.getString("reader_capture_UBER","Aguardando")} • leitura ${prefs.getString("reader_ocr_UBER","Aguardando")}",
                "99: captura ${prefs.getString("reader_capture_99","Aguardando")} • leitura ${prefs.getString("reader_ocr_99","Aguardando")}",
                "Modelo de leitura pronto: ${prefs.getBoolean("reader_model_ready",false)}",
                "IA: ${prefs.getString("ai_status","Aguardando oferta")}").joinToString("\n")
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico JHONDRIVER",report))
            android.widget.Toast.makeText(this,"Diagnóstico copiado; não inclui imagem, endereço nem chave",android.widget.Toast.LENGTH_LONG).show()
        })
        diagnostics.addView(action("ABRIR AJUSTES DE LEITURA  ›", false) { showTab("Perfil") })
        diagnostics.addView(label(prefs.getString("ai_status", "IA: aguardando oferta") ?: "IA: aguardando oferta", 12f, muted, false, 4, 0))
        diagnostics.visibility = View.GONE
        val status = horizontal().apply { setPadding(dp(4), dp(12), dp(4), dp(4)) }
        status.addView(label("PAINEL DO MOTORISTA", 11f, gold, true), weight())
        val online = isOperationalOnline()
        status.addView(pill(if (online) "● LEITURA ATIVA" else "● LEITURA PAUSADA", if (online) green else muted))
        content.addView(label("Seu próximo percurso", 27f, white, true, 18, 3))
        content.addView(label("Seus números. Suas escolhas.", 14f, muted, false, 0, 12))
        content.addView(status)

        val controls = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        fun feature(title: String, key: String) {
            val tile = card()
            val row = horizontal()
            row.addView(label(title, 15f, white, true), weight())
            row.addView(Switch(this).apply {
                contentDescription = title
                isChecked = prefs.getBoolean(key, false) && prefs.getBoolean("reader_disclosure_accepted", false)
                thumbTintList = ColorStateList.valueOf(white)
                trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(gold, Color.rgb(80,80,85)))
                var adjusting = false
                setOnCheckedChangeListener { _, enabled ->
                    if (!adjusting) {
                        if (enabled) prefs.edit().putString("reader_pending_feature", key).apply()
                        if (enabled && !ensureRequiredPermissions()) {
                            adjusting = true
                            isChecked = false
                            adjusting = false
                            prefs.edit().putBoolean(key, false).apply()
                        } else {
                            prefs.edit().putBoolean(key, enabled).putBoolean("reader_paused", false).remove("reader_pending_feature").remove("reader_permission_step").apply()
                        }
                    }
                }
            })
            tile.addView(row)
            controls.addView(tile, if (wide) LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(10); rightMargin = dp(6) } else margin(0, 8))
        }
        feature("✧ Copiloto", "copilot_enabled")
        feature("● Semáforo", "traffic_enabled")
        content.addView(controls)

        val offerCard = card().apply { background = rounded(panel, 22, gold) }
        offerCard.addView(label("ÚLTIMA OPORTUNIDADE", 14f, gold, true, 0, 18))
        val offer = records.firstOrNull()
        fun number(key: String): String = offer?.let { String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", it.optDouble(key)) } ?: "—"
        val totals = horizontal()
        totals.addView(metric("VALOR", if (offer == null) "—" else "R$ " + number("fare")), weight())
        totals.addView(metric("PERCURSO TOTAL", number("totalKm") + " km"), weight())
        totals.addView(metric("TEMPO TOTAL", number("minutes") + " min"), weight())
        offerCard.addView(totals)
        offerCard.addView(View(this).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(18); bottomMargin = dp(18) })
        val arrival = offer?.optDouble("pickupMinutes", Double.NaN)
        offerCard.addView(label("Até o passageiro: " + if (arrival != null && arrival.isFinite()) String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f min", arrival) else "não identificado", 13f, white, false, 8, 8))
        offerCard.addView(label("Nota: " + (offer?.optString("passengerRating")?.takeIf { it.isNotBlank() } ?: "não identificada"), 13f, white, false, 0, 8))
        val rates = horizontal()
        rates.addView(metric("R$/KM", if (offer == null) "—" else "R$ " + number("perKm")), weight())
        rates.addView(metric("R$/HORA", if (offer == null) "—" else "R$ " + number("perHour")), weight())
        offerCard.addView(rates)
        val classification = offer?.optString("classification")
        offerCard.addView(label(when (classification) { "GOOD" -> "✓ BOA CORRIDA"; "BAD" -> "ABAIXO DOS LIMITES"; "REVIEW" -> "AVALIE A CORRIDA"; else -> "AGUARDANDO UMA OFERTA" }, 16f, if (offer == null) muted else white, true, 18, 8).apply {
            gravity = Gravity.CENTER
            background = rounded(when (classification) { "GOOD" -> Color.rgb(16, 85, 36); "BAD" -> Color.rgb(99, 26, 26); "REVIEW" -> Color.rgb(87, 62, 14); else -> Color.rgb(31,31,34) }, 12, border)
        }, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(18) })
        offerCard.addView(label(if (offer == null) "Ative a leitura e abra o aplicativo de motorista." else "Valores brutos • inclui chegada quando informada na oferta.", 12f, muted, false, 12, 0))

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
        settings.addView(label("Autorize a leitura e a sobreposição nos ajustes. Para usar bancos, desligue a leitura.", 12f, muted, false, 6, 2))
        settings.addView(action("PERMISSÕES E AJUSTES  ›", false) { showTab("Perfil") })
        content.addView(settings, margin(0,12))
        content.addView(action("DETALHES DA LEITURA  ›", false) {
            diagnostics.visibility = if(diagnostics.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        content.addView(diagnostics, margin(0,10))
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
        return !prefs.getBoolean("reader_paused",false) &&
            (prefs.getBoolean("copilot_enabled", false) || prefs.getBoolean("traffic_enabled", false)) &&
            Settings.canDrawOverlays(this) && isAccessibilityEnabled()
    }

    private fun ensureRequiredPermissions(): Boolean {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.getBoolean("reader_disclosure_accepted", false)) {
            AlertDialog.Builder(this).setTitle("Ativar leitura automática de ofertas?")
                .setMessage("O JHONDRIVER usa acessibilidade para ler texto e imagem das ofertas nos apps de motorista e mostrar os cálculos sobre a oferta. A IA na Supabase recebe somente os indicadores numéricos, não a imagem nem endereços. O app não aceita corridas por você. Alguns bancos podem impedir o uso enquanto esses acessos estiverem ativos. Você pode desligar a leitura nos ajustes.")
                .setNegativeButton("Agora não", null)
                .setPositiveButton("Concordo e quero configurar") { _, _ ->
                    prefs.edit().putBoolean("reader_disclosure_accepted", true).apply()
                    if (!Settings.canDrawOverlays(this)) {
                        prefs.edit().putString("reader_permission_step", "overlay").apply()
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } else if (!isAccessibilityEnabled()) {
                        prefs.edit().putString("reader_permission_step", "accessibility").apply()
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else {
                        prefs.getString("reader_pending_feature",null)?.let { pending ->
                            prefs.edit().putBoolean(pending,true).remove("reader_pending_feature").apply()
                            showTab(selectedTab)
                        }
                    }
                }.show()
            return false
        }
        if (!Settings.canDrawOverlays(this)) {
            prefs.edit().putString("reader_permission_step", "overlay").apply()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Autorize aparecer sobre outros aplicativos e volte para ativar.", Toast.LENGTH_LONG).show()
            return false
        }
        if (!isAccessibilityEnabled()) {
            prefs.edit().putString("reader_permission_step", "accessibility").apply()
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

    private fun openReaderAccessibilitySettings() {
        Toast.makeText(this, "Nos ajustes do Android, selecione JHONDRIVER e ative a acessibilidade.", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun stopReaderForBank() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "A acessibilidade do JHONDRIVER já está desligada.", Toast.LENGTH_LONG).show()
            showTab(selectedTab)
            return
        }
        if (OfferAccessibilityService.stopReading()) {
            Toast.makeText(this, "Leitura desligada. Reative a acessibilidade quando voltar a trabalhar.", Toast.LENGTH_LONG).show()
            val tab = selectedTab
            content.postDelayed({ if (!isFinishing && selectedTab == tab) showTab(tab) }, 1000)
        } else {
            Toast.makeText(this, "Nos ajustes do Android, desligue a acessibilidade do JHONDRIVER.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    fun createLabelEditor(title:String,key:String,defaultValue:String):AlertDialog {
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        val input=EditText(this).apply {
            setText(prefs.getString(key,defaultValue) ?: defaultValue)
            setSingleLine(true)
            filters=arrayOf(android.text.InputFilter.LengthFilter(32))
            setSelection(text.length)
        }
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value=input.text.toString().trim()
                if(value.isEmpty()) {input.error="Digite um nome para a faixa";return@setOnClickListener}
                prefs.edit().putString(key,value).apply()
                dialog.dismiss()
                showTab("Semáforo")
                Toast.makeText(this,"Nome da faixa atualizado",Toast.LENGTH_SHORT).show()
            }
        }
        return dialog
    }

    private fun buildTrafficSettings() {
        content.addView(action("Estilo do cartão • prévia e campos", true) { startActivity(android.content.Intent(this, CardStyleActivity::class.java)) })
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        content.addView(label("Semáforo de Valores", 24f, white, true, 5, 2))
        content.addView(label("Configure os limites. Cada oferta será classificada automaticamente.", 13f, muted, false, 0, 12))

        val names = card()
        names.addView(label("NOMES DAS FAIXAS", 11f, gold, true))
        fun editLabel(title: String, key: String, defaultValue: String) {
            val row = horizontal()
            row.addView(label(title, 14f, white, false), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(action(prefs.getString(key, defaultValue) ?: defaultValue, false) {
                createLabelEditor(title,key,defaultValue).show()
            })
            row.setPadding(0, dp(7), 0, dp(7))
            names.addView(row)
        }
        editLabel("Faixa vermelha", "label_bad", "Vixe não dá")
        editLabel("Faixa amarela", "label_review", "Será?")
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

    private fun openSupportContact(intent: Intent) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Nenhum aplicativo disponível. Você pode copiar o contato abaixo.", Toast.LENGTH_LONG).show()
            val contact = if (intent.action == Intent.ACTION_DIAL) "+55 41 99597-6826" else "gbrigante71@gmail.com"
            AlertDialog.Builder(this).setTitle("Contato do suporte").setMessage(contact)
                .setPositiveButton("Copiar") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Suporte JHONDRIVER", contact))
                }.setNegativeButton("Fechar", null).show()
        }
    }

    private fun buildProfile() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        content.addView(label("Do seu jeito", 28f, white, true, 18, 3))
        content.addView(label("Ajustes, metas e suporte em um só lugar.", 14f, muted, false, 0, 16))

        val privacy = card()
        privacy.addView(label("Usar banco com a leitura desligada", 18f, gold, true))
        privacy.addView(label("Desliga a acessibilidade e oculta o CC. Para voltar a ler corridas, você precisa reativá-la nos ajustes do Android. Suas opções serão mantidas.", 13f, muted, false))
        val serviceOn = isAccessibilityEnabled()
        privacy.addView(action(if (serviceOn) "DESLIGAR PARA USAR BANCO" else "REATIVAR ACESSIBILIDADE", true) {
            if (serviceOn) stopReaderForBank() else openReaderAccessibilitySettings()
        })
        content.addView(privacy, margin(0, 10))
        val updates = card()
        updates.addView(label("Atualizações", 18f, gold, true))
        updates.addView(label("Você será avisado ao abrir o app quando houver uma versão compatível publicada.", 13f, muted, false))
        updates.addView(action("VERIFICAR ATUALIZAÇÃO", false) { AppUpdates.check(this, manual = true) })
        content.addView(updates, margin(0, 10))

        val support = card()
        support.addView(label("Suporte", 18f, gold, true))
        support.addView(label("Precisa de ajuda com o JHONDRIVER? Entre em contato.", 13f, muted, false))
        support.addView(action("TELEFONE: (41) 99597-6826", false) {
            openSupportContact(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+5541995976826")))
        })
        support.addView(action("E-MAIL: gbrigante71@gmail.com", false) {
            openSupportContact(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:gbrigante71@gmail.com"))
                .putExtra(Intent.EXTRA_SUBJECT, "Suporte JHONDRIVER"))
        })
        content.addView(support, margin(0, 10))

        // Perfil do motorista: foto selecionável e identificação da conta.
        val userCard = card()
        val profileRow = horizontal()
        val profileAvatar = ImageView(this).apply {
            val size = dp(66)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(14) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 36, gold)
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
        editableGoal("Custo estimado por km", "cost_per_km", 0.65f)

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
        limits.addView(toggleRow("Mostrar lucro estimado (%)", showProfitSwitch))
        limits.addView(label("Margem estimada = valor da oferta menos o custo por km do percurso total. Outros custos só entram se você os incluir nesse valor.", 12f, muted, false, 2, 10))

        val voiceSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("voice_enabled", false)
            buttonTintList = ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("voice_enabled", checked).apply() }
        }
        limits.addView(toggleRow("Voz (falar resultado)", voiceSwitch))
        limits.addView(label("Quando ativada, anuncia novas ofertas pela voz do Android. Confira o volume de mídia e o idioma português do aparelho.", 12f, muted, false, 2, 8))
        prefs.getString("voice_status", null)?.let { limits.addView(label(it, 12f, gold, false, 2, 4)) }

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
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(30, 33, 40), Color.rgb(22, 24, 29), Color.rgb(19, 20, 24))
        ).apply {
            cornerRadius = dp(24).toFloat()
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

    private fun action(text: String, prominent: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        setTextColor(if(prominent) Color.rgb(30,24,13) else gold)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(48)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(Color.argb(45,255,220,120)),
            rounded(if(prominent)gold else Color.rgb(36,37,42),14,if(prominent)gold else border),null)
        layoutParams = LinearLayout.LayoutParams(-2,-2).apply {topMargin=dp(8)}
        isClickable = true
        isFocusable = true
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
        val labels = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        labels.addView(label(title, 14f, white, true))
        val state = label(if(sw.isChecked)"Ativado" else "Desativado",12f,if(sw.isChecked)green else muted,false,3,0)
        labels.addView(state)
        row.addView(labels, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(sw)
        row.setPadding(dp(12),dp(12),dp(8),dp(12))
        row.background = rounded(Color.rgb(32,34,40),16,border)
        row.layoutParams = LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(4)}
        sw.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(gold,Color.LTGRAY))
        sw.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(Color.rgb(133,105,39),Color.rgb(74,76,84)))
        // Update the visual state without replacing the switch's preference listener.
        sw.setOnClickListener {
            state.text=if(sw.isChecked)"Ativado" else "Desativado"
            state.setTextColor(if(sw.isChecked)green else muted)
        }
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

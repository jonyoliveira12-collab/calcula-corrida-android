package br.com.calculacorrida

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputFilter
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors

/** A separate screen; offer capture and calculations are unchanged. */
class FriendsActivity : AppCompatActivity() {
    private val gold = Color.rgb(235, 191, 77)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var client: FriendsClient
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var messages: TextView
    private lateinit var locationLabel: TextView
    private lateinit var map: WebView
    private lateinit var draft: EditText
    private lateinit var online: Button
    private var snapshot = JSONObject()
    private var peer: String? = null
    private var busy = false
    private var foreground = false
    private var mapReady = false
    private var pendingLocation: JSONObject? = null
    private var messageNonce = UUID.randomUUID().toString()
    private var selfId = ""
    private var authAtStart: String? = null
    private val refresh = object : Runnable {
        override fun run() { if (foreground) { reload(); main.postDelayed(this, 5000) } }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        authAtStart = SessionManager(this).getEmail()
        if (SessionManager(this).getAccessToken().isNullOrBlank()) {
            AlertDialog.Builder(this).setTitle("Entre para usar Amigos").setMessage("É necessária uma conta conectada. A leitura de corridas continua independente.")
                .setPositiveButton("Voltar") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            return
        }
        client = FriendsClient(this)
        val scroll = ScrollView(this); root = column(); scroll.addView(root); setContentView(scroll)
        root.addView(text("AMIGOS", 26f, gold))
        root.addView(text("Convites, conversa e localização compartilhada por você.", 14f))
        root.addView(button("‹ VOLTAR") { finish() })
        status = text("Conectando…", 14f); root.addView(status)
        online = button("FICAR ONLINE") {
            if (FriendsService.running) { FriendsService.stop(this); status.text = "Encerrando compartilhamento…" }
            else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 71)
            } else {
                ContextCompat.startForegroundService(this, Intent(this, FriendsService::class.java))
                status.text = "Conectando… toque novamente para ficar offline."
            }
        }; root.addView(online)
        root.addView(text("Online mostra sua presença. Localização só é enviada após você escolher um amigo e uma duração. Sem sinal, confira sempre a última atualização. Este recurso não substitui serviços de emergência.", 13f))
        root.addView(button("MEU NOME E CÓDIGO DE AMIGO") { showProfile() })
        root.addView(button("ADICIONAR AMIGO PELO CÓDIGO") { addFriend() })
        list = column(); root.addView(list)
        details = column(); root.addView(details); details.visibility = View.GONE
        locationLabel = text("", 14f); details.addView(locationLabel)
        map = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
                override fun onPageFinished(view: WebView?, url: String?) { mapReady = true; updateMap(pendingLocation) }
            }
        }
        details.addView(map, LinearLayout.LayoutParams(-1, dp(260)))
        val js = assets.open("friends/leaflet.js").bufferedReader().use { it.readText() }
        val css = assets.open("friends/leaflet.css").bufferedReader().use { it.readText() }
        map.loadDataWithBaseURL("https://jhondriver-map.invalid/", """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>$css html,body,#map{height:100%;margin:0;background:#171717}</style></head><body><div id="map"></div><script>$js
var map=L.map('map').setView([-15.8,-47.9],4);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);var point=null,area=null;
function clearPoint(){if(point){map.removeLayer(point);point=null;}if(area){map.removeLayer(area);area=null;}}
function showPoint(lat,lng,accuracy){clearPoint();point=L.circleMarker([lat,lng],{radius:9,color:'#ebbf4d',fillOpacity:1}).addTo(map);area=L.circle([lat,lng],{radius:accuracy,color:'#ebbf4d'}).addTo(map);map.invalidateSize();map.setView([lat,lng],15);}
</script></body></html>""", "text/html", "UTF-8", null)
        details.addView(button("COMPARTILHAR MINHA LOCALIZAÇÃO COM ESTE AMIGO") { chooseShare() })
        details.addView(button("PARAR DE COMPARTILHAR COM ESTE AMIGO") { share(false, 15) })
        details.addView(button("REMOVER AMIGO") { relationship("remove") })
        details.addView(button("BLOQUEAR AMIGO") { relationship("block") })
        messages = text("", 15f); details.addView(messages)
        draft = input("Escreva uma mensagem (até 1.000 caracteres)", 1000); details.addView(draft)
        details.addView(button("ENVIAR MENSAGEM") {
            val id = peer ?: return@button
            val body = draft.text.toString().trim(); if (body.isBlank()) return@button
            val nonce = messageNonce
            execute {
                client.call("send", JSONObject().put("peer", id).put("body", body).put("nonce", nonce))
            } then callback@{ result ->
                if (peer == id && result != null) {
                    if (draft.text.toString().trim() == body) draft.text.clear()
                    messageNonce = UUID.randomUUID().toString(); renderMessages(result)
                }
            }
        })
    }
    override fun onResume() {
        super.onResume(); foreground = true
        if (::client.isInitialized) { main.removeCallbacks(refresh); main.post(refresh) }
    }
    override fun onPause() { updateMap(null); foreground = false; main.removeCallbacks(refresh); super.onPause() }
    private fun validAccount() = !isFinishing && !isDestroyed && SessionManager(this).isLoggedIn() && SessionManager(this).getEmail() == authAtStart
    private fun reload() {
        if (busy || !validAccount()) return
        execute { client.call("snapshot") } then callback@{ result ->
            if (result == null) { updateMap(null); return@callback }
            snapshot = result; selfId = result.getJSONObject("me").optString("id")
            renderFriends()
            val selected = friend(peer)
            if (selected == null || selected.optString("state") != "accepted") {
                peer = null; details.visibility = View.GONE; updateMap(null)
            } else { renderLocation(selected); loadMessages(selected.optString("id")) }
        }
    }
    // Each screen has one in-flight request, preventing stale edits or overlapping refreshes.
    private fun execute(work: () -> JSONObject): ((JSONObject?) -> Unit) -> Unit = { done ->
        if (busy) { Toast.makeText(this, "Aguarde a operação atual.", Toast.LENGTH_SHORT).show() }
        else {
            busy = true
            io.execute {
                val result = runCatching { work() }
                main.post {
                    busy = false
                    if (validAccount()) {
                        result.exceptionOrNull()?.let { status.text = it.message ?: "Sem conexão. Tente novamente." }
                        done(result.getOrNull())
                    }
                }
            }
        }
    }
    private infix fun (((JSONObject?) -> Unit) -> Unit).then(done: (JSONObject?) -> Unit) = this(done)
    private fun renderFriends() {
        online.text = if (FriendsService.running) "FICAR OFFLINE E PARAR LOCALIZAÇÃO" else "FICAR ONLINE"
        status.text = when {
            FriendsService.sharing -> "● Online • compartilhamento ativo"
            FriendsService.connected -> "● Online • localização privada"
            FriendsService.running -> "Conectando ou sem sinal…"
            else -> "Offline • sua localização não está sendo enviada"
        }
        status.setTextColor(if (FriendsService.connected) Color.rgb(67, 211, 115) else gold)
        list.removeAllViews()
        val friends = snapshot.optJSONArray("friends") ?: return
        if (friends.length() == 0) list.addView(text("Ainda sem amigos. Compartilhe seu código ou envie um convite.", 15f))
        for (i in 0 until friends.length()) {
            val f = friends.getJSONObject(i); val id = f.optString("id"); val name = f.optString("name")
            val card = column(); card.background = GradientDrawable().apply { setColor(Color.rgb(22,22,22)); cornerRadius = dp(14).toFloat(); setStroke(dp(1), gold) }
            card.addView(text(name, 19f, gold))
            when (f.optString("state")) {
                "pending" -> {
                    card.addView(text(if (f.optBoolean("incoming")) "Convite recebido" else "Aguardando aceite", 14f))
                    if (f.optBoolean("incoming")) {
                        card.addView(button("ACEITAR") { respond(id, true) })
                        card.addView(button("RECUSAR") { respond(id, false) })
                    } else card.addView(button("CANCELAR CONVITE") { changeRelationship("remove", id) })
                }
                "blocked" -> card.addView(button("DESBLOQUEAR") { changeRelationship("unblock", id) })
                else -> {
                    card.addView(text(if (f.optBoolean("online")) "● Online" else "○ Offline", 15f, if (f.optBoolean("online")) Color.GREEN else Color.LTGRAY))
                    card.addView(button("ABRIR CONVERSA E MAPA • ${f.optInt("unread")} NÃO LIDAS") {
                        if (peer != id) { draft.text.clear(); messageNonce = UUID.randomUUID().toString(); messages.text = "" }
                        peer = id; details.visibility = View.VISIBLE; renderLocation(f); loadMessages(id)
                    })
                }
            }
            list.addView(card, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(8),0,dp(8)) })
        }
    }
    private fun friend(id: String?): JSONObject? {
        val items = snapshot.optJSONArray("friends") ?: return null
        for (i in 0 until items.length()) if (items.getJSONObject(i).optString("id") == id) return items.getJSONObject(i)
        return null
    }
    private fun renderLocation(friend: JSONObject) {
        val point = friend.optJSONObject("location")
        locationLabel.text = if (point == null) "${friend.optString("name")} • sem localização compartilhada recente"
            else "${friend.optString("name")} • posição de ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(point.optLong("time_ms")))} • precisão aproximada ${point.optDouble("accuracy").toInt()} m"
        updateMap(point)
    }
    private fun updateMap(point: JSONObject?) {
        pendingLocation = point
        if (!::map.isInitialized) return
        map.visibility = if (point == null) View.GONE else View.VISIBLE
        if (!mapReady) return
        val lat = point?.optDouble("lat") ?: Double.NaN
        val lng = point?.optDouble("lng") ?: Double.NaN
        val accuracy = point?.optDouble("accuracy")?.toFloat() ?: Float.NaN
        if (point != null && FriendLocationPolicy.valid(lat,lng,accuracy,0)) map.evaluateJavascript("showPoint($lat,$lng,$accuracy);", null)
        else map.evaluateJavascript("clearPoint();", null)
    }
    private fun loadMessages(id: String) {
        execute { client.call("read", JSONObject().put("peer", id)) } then callback@{ result ->
            if (peer == id && result != null) renderMessages(result)
        }
    }
    private fun renderMessages(json: JSONObject) {
        val items = json.optJSONArray("messages") ?: return
        messages.text = if (items.length() == 0) "Comece a conversa." else (0 until items.length()).joinToString("\n\n") {
            val m = items.getJSONObject(it)
            "${if (m.optString("sender") == selfId) "Você" else friend(peer)?.optString("name") ?: "Amigo"}: ${m.optString("body")}"
        }
    }
    private fun showProfile() {
        val me = snapshot.optJSONObject("me") ?: return
        val box = column(); val name = input("Seu nome público", 40); name.setText(me.optString("name")); box.addView(name)
        val code = me.optString("code"); box.addView(text("Seu código: $code", 19f, gold))
        box.addView(button("COPIAR CÓDIGO") { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Código de amigo",code)); Toast.makeText(this,"Código copiado",Toast.LENGTH_SHORT).show() })
        AlertDialog.Builder(this).setTitle("Meu perfil de Amigos").setView(box).setNegativeButton("Fechar",null)
            .setPositiveButton("Salvar nome") { _, _ -> execute { client.call("profile", JSONObject().put("name", name.text.toString())) } then { result -> if (result != null) { snapshot = result; renderFriends() } } }.show()
    }
    private fun addFriend() {
        val code = input("Código do amigo", 20)
        AlertDialog.Builder(this).setTitle("Adicionar amigo").setMessage("Peça o código à pessoa. Ela precisará aceitar o convite.").setView(code)
            .setNegativeButton("Cancelar",null).setPositiveButton("Enviar convite") { _, _ ->
                execute { client.call("request", JSONObject().put("code",code.text.toString())) } then { if (it != null) { status.text = "Convite enviado."; reload() } }
            }.show()
    }
    private fun respond(id: String, accept: Boolean) {
        execute { client.call("respond",JSONObject().put("peer",id).put("accept",accept)) } then { if (it != null) reload() }
    }
    private fun chooseShare() {
        if (!FriendsService.connected) { status.text = "Fique online antes de compartilhar."; return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this).setTitle("Permitir localização").setMessage("Sua posição será enviada apenas ao amigo escolhido, pelo período confirmado. Enquanto ativo, haverá uma notificação para interromper.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Permitir") { _, _ -> requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),72) }.show()
            return
        }
        val selected = friend(peer) ?: return
        AlertDialog.Builder(this).setTitle("Compartilhar com ${selected.optString("name")}")
            .setItems(arrayOf("15 minutos", "1 hora", "4 horas")) { _, index -> share(true, intArrayOf(15,60,240)[index]) }.setNegativeButton("Cancelar",null).show()
    }
    private fun share(enabled: Boolean, minutes: Int) {
        val id = peer ?: return
        if (enabled || FriendsService.running) {
            if (!FriendsService.running) return
            startService(Intent(this,FriendsService::class.java).setAction(FriendsService.SHARE).putExtra("peer",id).putExtra("enabled",enabled).putExtra("minutes",minutes))
            status.text = if (enabled) "Solicitando compartilhamento…" else "Interrompendo compartilhamento…"
        } else {
            execute { client.call("share",JSONObject().put("peer",id).put("enabled",false)) } then { if (it != null) reload() }
        }
    }
    private fun relationship(action: String) {
        val id = peer ?: return
        AlertDialog.Builder(this).setTitle(if (action == "block") "Bloquear este amigo?" else "Remover este amigo?")
            .setMessage("Conversa e acesso à localização serão interrompidos.").setNegativeButton("Cancelar",null)
            .setPositiveButton("Confirmar") { _, _ -> changeRelationship(action,id) }.show()
    }
    private fun changeRelationship(action: String,id: String) {
        execute { client.call(action, JSONObject().put("peer",id)) } then { if (it != null) { if (peer == id) { peer = null; details.visibility = View.GONE; updateMap(null) }; reload() } }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 72 && results.any { it == PackageManager.PERMISSION_GRANTED }) chooseShare()
        else if (::status.isInitialized) status.text = "Permissão atualizada. Toque na opção novamente para continuar."
    }
    override fun onDestroy() {
        foreground = false; main.removeCallbacksAndMessages(null); io.shutdownNow()
        if (::map.isInitialized) { map.stopLoading(); map.destroy() }
        super.onDestroy()
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(Color.rgb(12,12,12)) }
    private fun text(value: String,size: Float,color: Int = Color.WHITE) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0,dp(6),0,dp(6)) }
    private fun input(hint: String,max: Int) = EditText(this).apply { this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); filters = arrayOf(InputFilter.LengthFilter(max)); setPadding(dp(8),dp(10),dp(8),dp(10)) }
    private fun button(title: String,action: () -> Unit) = Button(this).apply { text = title; setTextColor(gold); setTypeface(null,Typeface.BOLD); setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

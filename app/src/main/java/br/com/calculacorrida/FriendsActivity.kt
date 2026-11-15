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
    private val colors by lazy { AppAppearance.palette(this) }
    private val gold get()=colors.gold
    private val accent = Color.rgb(239,190,65)
    private lateinit var stats: TextView
    private lateinit var conversationTitle: TextView
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var client: FriendsGateway
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var messages: LinearLayout
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
    private var nonceBody = ""
    private var selfId = ""
    private var authAtStart: String? = null
    private val refresh = object : Runnable {
        override fun run() { if (foreground) { reload(); main.postDelayed(this, 5000) } }
    }
    override fun onCreate(state: Bundle?) {
        AppAppearance.apply(this)
        super.onCreate(state)
        AppAppearance.styleWindow(this)
        authAtStart = SessionManager(this).getEmail()
        if (SessionManager(this).getAccessToken().isNullOrBlank()) {
            AlertDialog.Builder(this).setTitle("Entre para usar Amigos").setMessage("É necessária uma conta conectada. A leitura de corridas continua independente.")
                .setPositiveButton("Voltar") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            return
        }
        client = FriendsDependencies.gateway(this, null)
        peer=state?.getString("peer")
        messageNonce=state?.getString("message_nonce") ?: messageNonce
        nonceBody=state?.getString("nonce_body").orEmpty()
        val scroll = ScrollView(this).apply { isFillViewport=true; setBackgroundColor(colors.background) }
        root = column(20); scroll.addView(root)
        val page=column().apply {setBackgroundColor(colors.background)}
        page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val navigation=row()
        DriverNavigation.fill(this,navigation,"Amigos")
        page.addView(navigation,LinearLayout.LayoutParams(-1,dp(80)).apply {leftMargin=dp(12);rightMargin=dp(12);bottomMargin=dp(4)})
        setContentView(page)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { view,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        val nav=row()
        nav.addView(text("‹  Voltar",15f,gold).apply { minHeight=dp(48);gravity=android.view.Gravity.CENTER_VERTICAL;setOnClickListener { finish() } },LinearLayout.LayoutParams(0,-2,1f))
        nav.addView(button("◐  TEMA") { chooseTheme() },LinearLayout.LayoutParams(-2,-2))
        root.addView(nav)
        val hero=card()
        hero.addView(text("JHONDRIVER  /  AMIGOS",11f,gold))
        hero.addView(text("Juntos em cada percurso.",30f,colors.text).apply { typeface=Typeface.create("sans-serif",Typeface.BOLD);setLineSpacing(dp(2).toFloat(),1f) })
        hero.addView(text("Sua rede de confiança na estrada.",14f,colors.muted))
        stats=text("— amigos    ·    — online    ·    — convites",13f,gold);hero.addView(stats)
        root.addView(hero,section())
        val presence=card()
        presence.addView(text("SUA PRESENÇA",11f,gold))
        status = text("Conectando…",17f); presence.addView(status)
        online = button("FICAR ONLINE",true) {
            if (FriendsService.running) { FriendsService.stop(this); status.text = "Encerrando compartilhamento…" }
            else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 71)
            } else {
                ContextCompat.startForegroundService(this, Intent(this, FriendsService::class.java))
                status.text = "Conectando… toque novamente para ficar offline."
            }
        }; presence.addView(online)
        presence.addView(text("Fique online sem divulgar sua posição. Você escolhe com quem compartilhar.",13f,colors.muted))
        root.addView(presence,section())
        val actions=row()
        actions.addView(button("MEU CÓDIGO") { showProfile() },LinearLayout.LayoutParams(0,-2,1f).apply {rightMargin=dp(8)})
        actions.addView(button("+ ADICIONAR AMIGO",true) { addFriend() },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(actions,section())
        root.addView(text("Sua equipe",23f,colors.text).apply {typeface=Typeface.DEFAULT_BOLD})
        root.addView(text("Convites precisam ser aceitos. Localização sempre sob seu controle.",13f,colors.muted))
        list = column(); root.addView(list)
        details = card(); root.addView(details,section()); details.visibility = View.GONE
        conversationTitle=text("Conversa",22f,colors.text);details.addView(conversationTitle)
        details.addView(button("FECHAR CONVERSA") { peer=null;details.visibility=View.GONE;updateMap(null) })
        locationLabel = text("", 13f,colors.muted); details.addView(locationLabel)
        map = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
                override fun onPageFinished(view: WebView?, url: String?) { if (isFinishing || isDestroyed) return; mapReady = true; updateMap(pendingLocation) }
            }
        }
        details.addView(map, LinearLayout.LayoutParams(-1, dp(260)))
        val js = assets.open("friends/leaflet.js").bufferedReader().use { it.readText() }
        val css = assets.open("friends/leaflet.css").bufferedReader().use { it.readText() }
        map.loadDataWithBaseURL("https://jhondriver-map.invalid/", """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>$css html,body,#map{height:100%;margin:0;background:#171717}</style></head><body><div id="map"></div><script>$js
var map=L.map('map').setView([-15.8,-47.9],4);var tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'});var point=null,area=null;
function clearPoint(){if(point){map.removeLayer(point);point=null;}if(area){map.removeLayer(area);area=null;}}
function showPoint(lat,lng,accuracy){clearPoint();if(!map.hasLayer(tiles)){tiles.addTo(map);}point=L.circleMarker([lat,lng],{radius:9,color:'#ebbf4d',fillOpacity:1}).addTo(map);area=L.circle([lat,lng],{radius:accuracy,color:'#ebbf4d'}).addTo(map);map.invalidateSize();map.setView([lat,lng],15);}
</script></body></html>""", "text/html", "UTF-8", null)
        details.addView(button("CRÉDITOS DO MAPA • OPENSTREETMAP") {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.openstreetmap.org/copyright")))
        })
        details.addView(button("COMPARTILHAR MINHA LOCALIZAÇÃO COM ESTE AMIGO") { chooseShare() })
        details.addView(button("PARAR DE COMPARTILHAR COM ESTE AMIGO") { share(false, 15) })
        details.addView(button("GERENCIAR AMIZADE  ⋯") {
            AlertDialog.Builder(this).setTitle(friend(peer)?.optString("name") ?: "Amigo")
                .setItems(arrayOf("Remover amigo","Bloquear amigo")) { _,which -> relationship(if(which==0)"remove" else "block") }.setNegativeButton("Cancelar",null).show()
        })
        details.addView(text("CONVERSA PRIVADA",11f,gold))
        messages = column(); details.addView(messages)
        draft = input("Sua mensagem…", 1000); draft.setText(state?.getString("draft").orEmpty()); details.addView(draft)
        details.addView(button("ENVIAR MENSAGEM",true) {
            val id = peer ?: return@button
            val body = draft.text.toString().trim(); if (body.isBlank()) return@button
            if (nonceBody != body) { messageNonce = UUID.randomUUID().toString(); nonceBody = body }
            val nonce = messageNonce
            execute {
                client.call("send", JSONObject().put("peer", id).put("body", body).put("nonce", nonce))
            } then callback@{ result ->
                if (peer == id && result != null) {
                    if (draft.text.toString().trim() == body) draft.text.clear()
                    messageNonce = UUID.randomUUID().toString(); nonceBody = ""; renderMessages(result)
                }
            }
        })
    }
    private fun chooseTheme() {
        AlertDialog.Builder(this).setTitle("Aparência do aplicativo").setSingleChoiceItems(arrayOf("Escuro","Claro"),if(colors.light)1 else 0) { dialog,which ->
            val light=which==1;dialog.dismiss()
            if(light!=colors.light) {AppAppearance.setLight(this,light);recreate()}
        }.setNegativeButton("Fechar",null).show()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("peer",peer)
        out.putString("message_nonce",messageNonce);out.putString("nonce_body",nonceBody)
        if(::draft.isInitialized) out.putString("draft",draft.text.toString())
        super.onSaveInstanceState(out)
    }
    override fun onResume() {
        super.onResume(); foreground = true
        if(AppAppearance.isLight(this)!=colors.light) {recreate();return}
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
            } else { details.visibility=View.VISIBLE; renderLocation(selected); loadMessages(selected.optString("id")) }
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
            FriendsService.sharing -> "● Online • compartilhando"
            FriendsService.connected -> "● Online • posição privada"
            FriendsService.running -> "Conectando ou sem sinal…"
            else -> "○ Você está offline"
        }
        status.setTextColor(if(FriendsService.connected)colors.green else colors.text)
        list.removeAllViews()
        val friends=snapshot.optJSONArray("friends") ?: return
        val all=(0 until friends.length()).map {friends.getJSONObject(it)}
        stats.text="${all.count {it.optString("state")=="accepted"}} amigos    ·    ${all.count {it.optBoolean("online")}} online    ·    ${all.count {it.optString("state")=="pending"}} convites"
        if(all.isEmpty()) {
            val empty=card()
            empty.addView(text("◎",40f,gold).apply {gravity=android.view.Gravity.CENTER})
            empty.addView(text("Sua rede começa aqui",21f,colors.text).apply {gravity=android.view.Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD})
            empty.addView(text("Adicione alguém de confiança para conversar e compartilhar o percurso quando quiser.",14f,colors.muted).apply {gravity=android.view.Gravity.CENTER})
            empty.addView(button("+ ADICIONAR PRIMEIRO AMIGO",true) {addFriend()})
            list.addView(empty,section())
        }
        for(f in all) {
            val id=f.optString("id");val name=f.optString("name");val item=card();val heading=row()
            heading.addView(text(name.trim().take(2).uppercase(java.util.Locale.getDefault()),20f,gold).apply {
                gravity=android.view.Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD;background=rounded(colors.input,18,colors.border)
            },LinearLayout.LayoutParams(dp(48),dp(48)).apply {rightMargin=dp(12)})
            val names=column();names.addView(text(name,18f,colors.text).apply {typeface=Typeface.DEFAULT_BOLD})
            val state=f.optString("state")
            names.addView(text(when(state){"pending"->if(f.optBoolean("incoming"))"Convite recebido" else "Aguardando aceite";"blocked"->"Bloqueado";else->if(f.optBoolean("online"))"● Online" else "○ Offline"},12f,if(f.optBoolean("online"))colors.green else colors.muted))
            heading.addView(names,LinearLayout.LayoutParams(0,-2,1f));item.addView(heading)
            when(state) {
                "pending" -> if(f.optBoolean("incoming")) {
                    val actions=row()
                    actions.addView(button("ACEITAR",true) {respond(id,true)},LinearLayout.LayoutParams(0,-2,1f).apply {rightMargin=dp(8)})
                    actions.addView(button("RECUSAR") {respond(id,false)},LinearLayout.LayoutParams(0,-2,1f));item.addView(actions)
                } else item.addView(button("CANCELAR CONVITE") {changeRelationship("remove",id)})
                "blocked" -> item.addView(button("DESBLOQUEAR") {changeRelationship("unblock",id)})
                else -> {
                    if(f.optLong("i_share_until")>snapshot.optLong("now_ms")) item.addView(text("↗ Sua posição compartilhada até "+DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(f.optLong("i_share_until"))),12f,gold))
                    val unread=f.optInt("unread")
                    item.addView(button("CONVERSAR E VER MAPA  ›"+(if(unread>0)"  •  $unread novas" else "")) {
                        if(peer!=id) {draft.text.clear();messageNonce=UUID.randomUUID().toString();messages.removeAllViews()}
                        peer=id;details.visibility=View.VISIBLE;renderLocation(f);loadMessages(id)
                        details.post {(root.parent as? ScrollView)?.smoothScrollTo(0,details.top)}
                    })
                }
            }
            list.addView(item,section())
        }
    }
    private fun friend(id: String?): JSONObject? {
        val items = snapshot.optJSONArray("friends") ?: return null
        for (i in 0 until items.length()) if (items.getJSONObject(i).optString("id") == id) return items.getJSONObject(i)
        return null
    }
    private fun renderLocation(friend: JSONObject) {
        val point = friend.optJSONObject("location")
        conversationTitle.text=friend.optString("name")
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
        messages.removeAllViews()
        if(items.length()==0) messages.addView(text("Comece a conversa. Sua equipe está a uma mensagem de distância.",14f,colors.muted))
        for(i in 0 until items.length()) {
            val m=items.getJSONObject(i);val mine=m.optString("sender")==selfId
            val bubble=column(12)
            bubble.background=rounded(if(mine)colors.input else colors.background,14,colors.border)
            bubble.addView(text(if(mine)"Você" else friend(peer)?.optString("name") ?: "Amigo",11f,gold))
            bubble.addView(text(m.optString("body"),15f,colors.text))
            bubble.addView(text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.optLong("time_ms"))),10f,colors.muted))
            messages.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7);leftMargin=if(mine)dp(24) else 0;rightMargin=if(mine)0 else dp(24)})
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
        execute { client.call(action, JSONObject().put("peer",id)) } then { if (it != null) {
            if(FriendsService.running) startService(Intent(this,FriendsService::class.java).setAction(FriendsService.FORGET_PEER).putExtra("peer",id))
            if (peer == id) { peer = null; details.visibility = View.GONE; updateMap(null) }; reload() } }
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
    private fun column(padding: Int=0) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(padding),dp(padding),dp(padding),dp(padding)) }
    private fun row()=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
    private fun section()=LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12);bottomMargin=dp(5)}
    private fun rounded(fill:Int,radius:Int,stroke:Int)=GradientDrawable().apply {setColor(fill);cornerRadius=dp(radius).toFloat();setStroke(dp(1),stroke)}
    private fun card()=column(18).apply {background=rounded(colors.panel,22,colors.border);elevation=dp(2).toFloat()}
    private fun text(value: String,size: Float,color: Int = colors.text) = TextView(this).apply { text = value; textSize = size; setTextColor(color);setLineSpacing(dp(2).toFloat(),1f);setPadding(0,dp(5),0,dp(5)) }
    private fun input(hint: String,max: Int) = EditText(this).apply {
        this.hint=hint;setTextColor(colors.text);setHintTextColor(colors.muted);filters=arrayOf(InputFilter.LengthFilter(max));setPadding(dp(14),dp(14),dp(14),dp(14));textSize=15f
        background=rounded(colors.input,14,colors.border)
    }
    private fun button(title: String,prominent:Boolean=false,action: () -> Unit) = Button(this).apply {
        text=title;isAllCaps=false;textSize=12f;setTextColor(if(prominent)Color.rgb(29,23,11) else gold);setTypeface(null,Typeface.BOLD);minHeight=dp(50)
        backgroundTintList=null
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33EBC04D),
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,if(prominent)intArrayOf(Color.rgb(255,225,133),accent) else intArrayOf(colors.input,colors.panel)).apply {
                cornerRadius=dp(14).toFloat();setStroke(dp(1),if(prominent)accent else this@FriendsActivity.colors.border)
            },null)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply {setMargins(0,dp(6),0,dp(6))}
        setPadding(dp(12),dp(12),dp(12),dp(12));setOnClickListener {action()}
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

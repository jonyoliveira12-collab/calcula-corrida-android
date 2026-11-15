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
    private lateinit var contactsScroll:ScrollView
    private lateinit var conversationHost:FrameLayout
    private lateinit var welcome:TextView
    private lateinit var mapGroup:LinearLayout
    private lateinit var messageScroll:ScrollView
    private lateinit var audioState:TextView
    private lateinit var recordButton:Button
    private lateinit var sendAudioButton:Button
    private lateinit var discardAudioButton:Button
    private lateinit var previewAudioButton:Button
    private val wide get()=resources.configuration.screenWidthDp>=700
    private val mediaIo=Executors.newFixedThreadPool(2)
    private var contactQuery=""
    private var recorder:android.media.MediaRecorder?=null
    private var audioFile:java.io.File?=null
    private var recordingStarted=0L
    private var pendingRecording=false
    private var audioDuration=0L
    @Volatile private var uploadedAudio:String?=null
    private var audioNonce=UUID.randomUUID().toString()
    private var player:android.media.MediaPlayer?=null
    private var playbackFile:java.io.File?=null
    private var playbackGeneration=0
    private val avatars=android.util.LruCache<String,android.graphics.Bitmap>(100)
    private val loadingAvatars=mutableSetOf<String>()
    private var lastMessageIds=""
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
        AppAppearance.apply(this);super.onCreate(state);AppAppearance.styleWindow(this)
        authAtStart=SessionManager(this).getEmail()
        if(SessionManager(this).getAccessToken().isNullOrBlank()) {
            AlertDialog.Builder(this).setTitle("Entre para usar Amigos").setMessage("Use sua conta para conversar e compartilhar localização.")
                .setPositiveButton("Entrar"){_,_->SessionManager(this).logout();startActivity(Intent(this,LoginActivity::class.java));finish()}
                .setNegativeButton("Voltar"){_,_->finish()}.setOnCancelListener{finish()}.show();return
        }
        client=FriendsDependencies.gateway(this,null)
        peer=state?.getString("peer");messageNonce=state?.getString("message_nonce") ?: messageNonce;nonceBody=state?.getString("nonce_body").orEmpty()
        val page=column().apply{setBackgroundColor(colors.background)}
        val body=row()
        root=column(16)
        contactsScroll=ScrollView(this).apply{addView(root);isFillViewport=true}
        conversationHost=FrameLayout(this).apply {setPadding(dp(8),dp(8),dp(8),dp(8))}
        body.addView(contactsScroll,LinearLayout.LayoutParams(0,-1,if(wide).36f else 1f))
        body.addView(conversationHost,LinearLayout.LayoutParams(0,-1,if(wide).64f else 1f))
        page.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val navigation=row();DriverNavigation.fill(this,navigation,"Amigos")
        page.addView(navigation,LinearLayout.LayoutParams(-1,dp(74)).apply{leftMargin=dp(10);rightMargin=dp(10);bottomMargin=dp(4)})
        setContentView(page)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page){view,insets->val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets}
        val nav=row()
        nav.addView(text("‹ Voltar",14f,gold).apply{minHeight=dp(44);gravity=android.view.Gravity.CENTER_VERTICAL;setOnClickListener{finish()}},LinearLayout.LayoutParams(0,-2,1f))
        nav.addView(button("◐ TEMA"){chooseTheme()},LinearLayout.LayoutParams(-2,-2));root.addView(nav)
        root.addView(text("JHONDRIVER / AMIGOS",11f,gold))
        root.addView(text("Conversas e contatos",24f).apply{typeface=Typeface.DEFAULT_BOLD})
        stats=text("Seus amigos na estrada",12f,colors.muted);root.addView(stats)
        status=text("Conectando…",13f,colors.muted);root.addView(status)
        online=button("FICAR ONLINE • ESCOLHER STATUS",true){choosePresence()};root.addView(online)
        val actions=row()
        actions.addView(button("MEU CÓDIGO"){showProfile()},LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(6)})
        actions.addView(button("+ ADICIONAR AMIGO"){addFriend()},LinearLayout.LayoutParams(0,-2,1f));root.addView(actions)
        root.addView(text("Localização privada até você autorizar um amigo.",12f,colors.muted))
        val search=input("Buscar amigo…",40)
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(v:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(v:CharSequence?,start:Int,before:Int,count:Int){contactQuery=v.toString();if(snapshot.has("friends"))renderFriends()}
            override fun afterTextChanged(v:android.text.Editable?){}
        });root.addView(search)
        list=column();root.addView(list)
        welcome=text("Selecione um amigo\n\nConverse, envie áudios e acompanhe a localização compartilhada com você.",19f,colors.muted).apply{gravity=android.view.Gravity.CENTER;setPadding(dp(30),dp(24),dp(30),dp(24))}
        conversationHost.addView(welcome,FrameLayout.LayoutParams(-1,-1))
        details=column(12).apply{background=rounded(colors.panel,20,colors.border)}
        conversationHost.addView(details,FrameLayout.LayoutParams(-1,-1))
        val header=row()
        header.addView(button("‹ CONTATOS"){closeConversation()},LinearLayout.LayoutParams(-2,-2))
        conversationTitle=text("Conversa",18f).apply{typeface=Typeface.DEFAULT_BOLD};header.addView(conversationTitle,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(8)})
        header.addView(button("⋯"){conversationMenu()},LinearLayout.LayoutParams(dp(48),-2));details.addView(header)
        locationLabel=text("",11f,colors.muted);details.addView(locationLabel)
        val tools=row()
        tools.addView(button("LOCALIZAÇÃO"){mapGroup.visibility=if(mapGroup.visibility==View.VISIBLE)View.GONE else View.VISIBLE;updateMap(friend(peer)?.optJSONObject("location"))},LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(6)})
        tools.addView(button("COMPARTILHAR"){chooseShare()},LinearLayout.LayoutParams(0,-2,1f));details.addView(tools)
        mapGroup=column().apply{visibility=View.GONE};details.addView(mapGroup)
        buildMap()
        messages=column(8)
        messageScroll=ScrollView(this).apply{addView(messages);isFillViewport=true}
        details.addView(messageScroll,LinearLayout.LayoutParams(-1,0,1f))
        audioState=text("",12f,gold).apply{visibility=View.GONE};details.addView(audioState)
        val audioActions=row()
        previewAudioButton=button("OUVIR"){audioFile?.let{playLocal(it)}}.apply{visibility=View.GONE}
        sendAudioButton=button("ENVIAR ÁUDIO",true){sendAudio()}.apply{visibility=View.GONE}
        discardAudioButton=button("DESCARTAR"){if(!busy)discardAudio()}.apply{visibility=View.GONE}
        audioActions.addView(previewAudioButton,LinearLayout.LayoutParams(0,-2,1f));audioActions.addView(sendAudioButton,LinearLayout.LayoutParams(0,-2,1f));audioActions.addView(discardAudioButton,LinearLayout.LayoutParams(0,-2,1f));details.addView(audioActions)
        val composer=row()
        draft=input("Mensagem…",1000).apply{setText(state?.getString("draft").orEmpty());maxLines=4}
        composer.addView(draft,LinearLayout.LayoutParams(0,-2,1f))
        composer.addView(button("ENVIAR",true){sendText()},LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(6)})
        details.addView(composer)
        recordButton=button("GRAVAR ÁUDIO"){if(recorder!=null)finishRecording() else requestRecording()};details.addView(recordButton)
        showConversation()
    }
    private fun choosePresence() {
        AlertDialog.Builder(this).setTitle("Como você quer aparecer?").setItems(arrayOf("● Online","◐ Ausente","○ Offline")){_,index->
            if(index==2){FriendsService.stop(this);status.text="Encerrando compartilhamento…";return@setItems}
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),71);return@setItems}
            ContextCompat.startForegroundService(this,Intent(this,FriendsService::class.java).setAction(FriendsService.MODE).putExtra("mode",if(index==1)"away" else "online"))
            status.text="Atualizando status…"
        }.setNegativeButton("Cancelar",null).show()
    }
    private fun showConversation() {
        val selected=peer!=null
        details.visibility=if(selected)View.VISIBLE else View.GONE;welcome.visibility=if(selected)View.GONE else View.VISIBLE
        contactsScroll.visibility=if(wide || !selected)View.VISIBLE else View.GONE
        conversationHost.visibility=if(wide || selected)View.VISIBLE else View.GONE
    }
    private fun closeConversation(){stopPlayback();discardAudio();peer=null;updateMap(null);showConversation()}
    private fun openConversation(f:JSONObject) {
        if(busy){Toast.makeText(this,"Aguarde a operação atual.",Toast.LENGTH_SHORT).show();return}
        val id=f.optString("id")
        if(peer!=id){stopPlayback();discardAudio();draft.text.clear();messageNonce=UUID.randomUUID().toString();messages.removeAllViews();lastMessageIds=""}
        peer=id;showConversation();renderLocation(f);loadMessages(id)
    }
    private fun conversationMenu() {
        AlertDialog.Builder(this).setTitle(friend(peer)?.optString("name") ?: "Amigo")
            .setItems(arrayOf("Parar de compartilhar localização","Remover amigo","Bloquear amigo")){_,which->when(which){0->share(false,15);1->relationship("remove");else->relationship("block")}}.setNegativeButton("Cancelar",null).show()
    }
    private fun sendText() {
        val id=peer ?: return;val body=draft.text.toString().trim();if(body.isBlank())return
        if(nonceBody!=body){messageNonce=UUID.randomUUID().toString();nonceBody=body};val nonce=messageNonce
        execute{client.call("send",JSONObject().put("peer",id).put("body",body).put("nonce",nonce))} then {result->
            if(peer==id && result!=null){if(draft.text.toString().trim()==body)draft.text.clear();messageNonce=UUID.randomUUID().toString();nonceBody="";renderMessages(result)}
        }
    }
    private fun buildMap() {
        map=WebView(this).apply {
            settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false
            settings.cacheMode=android.webkit.WebSettings.LOAD_NO_CACHE;settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:android.webkit.WebResourceRequest?)=true
                override fun onPageFinished(view:WebView?,url:String?){if(isFinishing||isDestroyed)return;mapReady=true;updateMap(pendingLocation)}}
        }
        mapGroup.addView(map,LinearLayout.LayoutParams(-1,dp(if(wide)230 else 160)))
        val js=assets.open("friends/leaflet.js").bufferedReader().use{it.readText()};val css=assets.open("friends/leaflet.css").bufferedReader().use{it.readText()}
        map.loadDataWithBaseURL("https://jhondriver-map.invalid/","""<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>$css html,body,#map{height:100%;margin:0;background:#171717}</style></head><body><div id="map"></div><script>$js
var map=L.map('map').setView([-15.8,-47.9],4);var tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'});var point=null,area=null;
function clearPoint(){if(point){map.removeLayer(point);point=null;}if(area){map.removeLayer(area);area=null;}}
function showPoint(lat,lng,accuracy){clearPoint();if(!map.hasLayer(tiles)){tiles.addTo(map);}point=L.circleMarker([lat,lng],{radius:9,color:'#ebbf4d',fillOpacity:1}).addTo(map);area=L.circle([lat,lng],{radius:accuracy,color:'#ebbf4d'}).addTo(map);map.invalidateSize();map.setView([lat,lng],15);}
</script></body></html>""","text/html","UTF-8",null)
        val actions=row()
        actions.addView(button("GOOGLE MAPS"){openRoute(false)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(button("WAZE"){openRoute(true)},LinearLayout.LayoutParams(0,-2,1f));mapGroup.addView(actions)
        mapGroup.addView(text("Mapa: © OpenStreetMap. A rota usa a última posição recebida.",10f,colors.muted).apply{setOnClickListener{startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright")))}})
    }
    private fun openRoute(waze:Boolean) {
        val location=friend(peer)?.optJSONObject("location") ?: run {Toast.makeText(this,"Sem localização compartilhada recente.",Toast.LENGTH_SHORT).show();return}
        val lat=location.optDouble("lat");val lng=location.optDouble("lng")
        if(!FriendLocationPolicy.valid(lat,lng,location.optDouble("accuracy").toFloat(),0))return
        val url=if(waze)"https://waze.com/ul?ll=$lat,$lng&navigate=yes" else "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving"
        runCatching{startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))}.onFailure{Toast.makeText(this,"Não foi possível abrir a navegação.",Toast.LENGTH_SHORT).show()}
    }
    private fun chooseTheme() {
        val options=AppAppearance.options()
        AlertDialog.Builder(this).setTitle("Aparência do aplicativo").setSingleChoiceItems(options.map{it.second}.toTypedArray(),options.indexOfFirst{it.first==colors.theme}) {dialog,index ->
            dialog.dismiss();if(options[index].first!=colors.theme){AppAppearance.setTheme(this,options[index].first);recreate()}
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
        if(AppAppearance.theme(this)!=colors.theme) {recreate();return}
        if (::client.isInitialized) { main.removeCallbacks(refresh); main.post(refresh) }
        if(pendingRecording && ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {pendingRecording=false;main.post{startRecording()}}
    }
    override fun onPause() { stopPlayback();if(recorder!=null)discardAudio();updateMap(null); foreground = false; main.removeCallbacks(refresh); super.onPause() }
    private fun validAccount() = !isFinishing && !isDestroyed && SessionManager(this).isLoggedIn() && SessionManager(this).getEmail() == authAtStart
    private fun reload() {
        if (busy || !validAccount()) return
        execute { client.call("snapshot") } then callback@{ result ->
            if (result == null) { updateMap(null); return@callback }
            snapshot = result; selfId = result.getJSONObject("me").optString("id")
            renderFriends()
            val selected = friend(peer)
            if (selected == null || selected.optString("state") != "accepted") {
                peer = null; showConversation();stopPlayback();discardAudio();updateMap(null)
            } else { showConversation(); renderLocation(selected); loadMessages(selected.optString("id")) }
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
        online.text = if(FriendsService.running)(if(FriendsService.mode=="away")"◐ AUSENTE" else "● ONLINE")+" • ALTERAR STATUS" else "FICAR ONLINE • ESCOLHER STATUS"
        status.text = when {
            FriendsService.mode=="away" -> "◐ Ausente • disponível no chat"
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
        for(f in all.filter{it.optString("name").contains(contactQuery,true)}) {
            val id=f.optString("id");val name=f.optString("name");val item=card();val heading=row()
            val avatar=FrameLayout(this)
            avatar.addView(text(name.trim().take(2).uppercase(java.util.Locale.getDefault()),18f,gold).apply{gravity=android.view.Gravity.CENTER;background=rounded(colors.input,18,colors.border)},FrameLayout.LayoutParams(-1,-1))
            val picture=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;clipToOutline=true;background=rounded(colors.input,18,colors.border);visibility=View.GONE}
            avatar.addView(picture,FrameLayout.LayoutParams(-1,-1));loadAvatar(f.optString("avatar_path"),picture)
            heading.addView(avatar,LinearLayout.LayoutParams(dp(44),dp(44)).apply{rightMargin=dp(10)})
            val names=column();names.addView(text(name,18f,colors.text).apply {typeface=Typeface.DEFAULT_BOLD})
            val state=f.optString("state")
            names.addView(text(when(state){"pending"->if(f.optBoolean("incoming"))"Convite recebido" else "Aguardando aceite";"blocked"->"Bloqueado";else->if(f.optString("status")=="away")"◐ Ausente" else if(f.optBoolean("online"))"● Online" else "○ Offline"},12f,if(f.optBoolean("online"))colors.green else colors.muted))
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
                    item.addView(button("CONVERSAR E VER MAPA  ›"+(if(unread>0)"  •  $unread novas" else "")){openConversation(f)})
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
        val ids=(0 until items.length()).joinToString{items.getJSONObject(it).optString("id")}
        if(ids==lastMessageIds && messages.childCount>0)return
        lastMessageIds=ids
        messages.removeAllViews()
        if(items.length()==0) messages.addView(text("Comece a conversa. Sua equipe está a uma mensagem de distância.",14f,colors.muted))
        for(i in 0 until items.length()) {
            val m=items.getJSONObject(i);val mine=m.optString("sender")==selfId
            val bubble=column(12)
            bubble.background=rounded(if(mine)colors.input else colors.background,14,colors.border)
            bubble.addView(text(if(mine)"Você" else friend(peer)?.optString("name") ?: "Amigo",11f,gold))
            if(m.optString("kind")=="audio") bubble.addView(button("▶ ÁUDIO • "+(m.optLong("duration_ms")/1000)+" s"){playRemote(m.optString("audio_path"))})
            else bubble.addView(text(m.optString("body"),15f,colors.text))
            bubble.addView(text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.optLong("time_ms"))),10f,colors.muted))
            messages.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7);leftMargin=if(mine)dp(24) else 0;rightMargin=if(mine)0 else dp(24)})
        }
        messageScroll.post{messageScroll.fullScroll(View.FOCUS_DOWN)}
    }
    private fun showProfile() {
        val me = snapshot.optJSONObject("me") ?: return
        val box = column(); val name = input("Seu nome público", 40); name.setText(me.optString("name")); box.addView(name)
        box.addView(button("ALTERAR FOTO • VISÍVEL AOS AMIGOS") {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),81)})
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
            if (peer == id) { peer = null; showConversation();stopPlayback();discardAudio(); updateMap(null) }; reload() } }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if(requestCode==73){pendingRecording=results.any{it==PackageManager.PERMISSION_GRANTED};if(pendingRecording && foreground){pendingRecording=false;main.post{startRecording()}};return}
        if (requestCode == 72 && results.any { it == PackageManager.PERMISSION_GRANTED }) chooseShare()
        else if (::status.isInitialized) status.text = "Permissão atualizada. Toque na opção novamente para continuar."
    }
    private fun loadAvatar(path:String,picture:ImageView) {
        if(path.isBlank() || path=="null")return
        avatars.get(path)?.let{picture.setImageBitmap(it);picture.visibility=View.VISIBLE;return}
        if(!loadingAvatars.add(path))return
        picture.tag=path
        mediaIo.execute {
            val bitmap=runCatching {
                val bytes=FriendMediaStore(this).download(path)
                val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth>0 && bounds.outHeight>0)
                var sample=1;while(bounds.outWidth/sample>512 || bounds.outHeight/sample>512)sample*=2
                val decoded=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample}) ?: error("Foto inválida")
                val small=android.graphics.Bitmap.createScaledBitmap(decoded,128,128,true)
                if(small!==decoded)decoded.recycle();small
            }.getOrNull()
            main.post {
                loadingAvatars.remove(path)
                if(validAccount() && bitmap!=null) {avatars.put(path,bitmap);if(picture.tag==path){picture.setImageBitmap(bitmap);picture.visibility=View.VISIBLE}}
                else bitmap?.recycle()
            }
        }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==81 && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            getSharedPreferences("settings",MODE_PRIVATE).edit().putString("profile_photo_uri",uri.toString()).apply()
            execute{val path=FriendMediaStore(this).uploadPhoto(uri);client.call("profile",JSONObject().put("avatar_path",path))} then {if(it!=null){snapshot=it;renderFriends();status.text="Foto compartilhada com seus amigos aceitos."}}
        }
    }
    private fun requestRecording() {
        if(peer==null)return
        if(audioFile!=null){Toast.makeText(this,"Envie ou descarte o áudio gravado antes de gravar outro.",Toast.LENGTH_SHORT).show();return}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingRecording=true;requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),73);return}
        startRecording()
    }
    @Suppress("DEPRECATION") private fun startRecording() {
        if(!foreground || peer==null || recorder!=null)return
        stopPlayback()
        val file=java.io.File.createTempFile("jd-record-",".m4a",cacheDir)
        try {
            val capture=android.media.MediaRecorder();recorder=capture
            capture.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            capture.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            capture.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            capture.setAudioEncodingBitRate(64000);capture.setAudioSamplingRate(44100)
            capture.setMaxDuration(60000);capture.setMaxFileSize(FriendMediaStore.MAX.toLong())
            capture.setOutputFile(file.absolutePath)
            capture.setOnInfoListener{_,what,_->if(what==android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what==android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)finishRecording()}
            capture.prepare();capture.start();recorder=capture;audioFile=file;recordingStarted=SystemClock.elapsedRealtime()
            audioNonce=UUID.randomUUID().toString();uploadedAudio=null;audioDuration=0
            audioState.visibility=View.VISIBLE;audioState.text="● Gravando para ${friend(peer)?.optString("name") ?: "seu amigo"} • até 60 s"
            recordButton.text="PARAR GRAVAÇÃO";discardAudioButton.visibility=View.VISIBLE
        } catch(_:Exception){recorder?.release();recorder=null;file.delete();audioFile=null;status.text="Não foi possível iniciar o microfone. Confira a permissão e tente novamente."}
    }
    private fun finishRecording() {
        val capture=recorder ?: return
        recorder=null
        val elapsed=(SystemClock.elapsedRealtime()-recordingStarted).coerceAtMost(60000)
        val ok=runCatching{capture.stop()}.isSuccess
        capture.release()
        if(!ok || elapsed<300 || audioFile?.length()==0L){discardAudio();status.text="Áudio muito curto. Grave novamente.";return}
        audioDuration=elapsed
        audioState.text="Áudio pronto • ${(elapsed+999)/1000} s • confira antes de enviar"
        recordButton.text="GRAVAR ÁUDIO"
        previewAudioButton.visibility=View.VISIBLE;sendAudioButton.visibility=View.VISIBLE;discardAudioButton.visibility=View.VISIBLE
    }
    private fun discardAudio() {
        val capture=recorder;recorder=null
        if(capture!=null){runCatching{capture.stop()};runCatching{capture.release()}}
        audioFile?.delete();audioFile=null;uploadedAudio=null;audioDuration=0
        if(::recordButton.isInitialized){recordButton.text="GRAVAR ÁUDIO";audioState.visibility=View.GONE;previewAudioButton.visibility=View.GONE;sendAudioButton.visibility=View.GONE;discardAudioButton.visibility=View.GONE}
    }
    private fun sendAudio() {
        val file=audioFile ?: return;val id=peer ?: return
        if(recorder!=null || audioDuration<300 || busy)return
        stopPlayback()
        val duration=audioDuration;val nonce=audioNonce
        execute {
            val path=uploadedAudio ?: FriendMediaStore(this).uploadAudio(file).also{uploadedAudio=it}
            client.call("send",JSONObject().put("peer",id).put("kind","audio").put("audio_path",path).put("duration_ms",duration).put("nonce",nonce))
        } then {result->if(result!=null && peer==id){discardAudio();renderMessages(result)}}
    }
    private fun stopPlayback() {
        playbackGeneration++
        player?.let{runCatching{it.stop()};it.release()};player=null
        playbackFile?.delete();playbackFile=null
    }
    private fun playLocal(file:java.io.File) {
        stopPlayback()
        val playback=android.media.MediaPlayer();player=playback
        try {
            playback.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            playback.setDataSource(file.absolutePath)
            playback.setOnPreparedListener{if(foreground && player===it)it.start()else it.release()}
            playback.setOnCompletionListener{stopPlayback()}
            playback.setOnErrorListener{_,_,_->stopPlayback();status.text="Não foi possível reproduzir esse áudio.";true}
            playback.prepareAsync()
        }catch(_:Exception){stopPlayback();status.text="Não foi possível reproduzir esse áudio."}
    }
    private fun playRemote(path:String) {
        if(path.isBlank() || path=="null")return
        stopPlayback();val generation=playbackGeneration;val id=peer
        status.text="Carregando áudio…"
        mediaIo.execute {
            val file=runCatching{val bytes=FriendMediaStore(this).download(path);java.io.File.createTempFile("jd-play-",".m4a",cacheDir).apply{writeBytes(bytes)}}.getOrNull()
            main.post {
                if(!validAccount() || !foreground || peer!=id || generation!=playbackGeneration){file?.delete();return@post}
                if(file==null){status.text="Áudio indisponível. Confira a conexão e a amizade.";return@post}
                playLocal(file);playbackFile=file
            }
        }
    }
    override fun onDestroy() {
        stopPlayback();discardAudio();foreground = false; main.removeCallbacksAndMessages(null); io.shutdownNow();mediaIo.shutdownNow()
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

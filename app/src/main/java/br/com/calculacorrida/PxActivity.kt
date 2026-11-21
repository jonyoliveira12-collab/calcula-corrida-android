package br.com.calculacorrida

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.graphics.Color
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject

class PxActivity:AppCompatActivity(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var root:LinearLayout
    private lateinit var status:TextView
    private lateinit var talk:PxTalkButton
    private lateinit var channelName:TextView
    private lateinit var talkLabel:TextView
    private lateinit var connectionBadge:TextView
    private lateinit var muteButton:Button
    private lateinit var cards:LinearLayout
    private lateinit var contacts:LinearLayout
    private lateinit var privateTab:Button
    private lateinit var groupTab:Button
    private var renderedChannel=""
    private lateinit var client:PxGateway
    private var channels=listOf<JSONObject>()
    private var friends=listOf<JSONObject>()
    private var busy=false
    private var channelFilter="private"
    private var pending:JSONObject?=null
    private var bluetoothAsked=false
    private val colors get()=AppAppearance.palette(this)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun text(value:String,size:Float=15f,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size;setTextColor(colors.text);if(bold)typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(6),0,dp(6))}
    private fun surface(fill:Int=colors.panel,radius:Int=18)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(radius).toFloat();setStroke(dp(1),this@PxActivity.colors.border)}
    private fun button(label:String,action:()->Unit)=Button(this).apply{text=label;isAllCaps=false;textSize=13f;minHeight=dp(46);minimumHeight=dp(46);setPadding(dp(12),dp(3),dp(12),dp(3));setTextColor(colors.text);backgroundTintList=null;background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33efbe41),surface(colors.input,13),null);setOnClickListener{action()}}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    override fun onCreate(saved:Bundle?){AppAppearance.apply(this);super.onCreate(saved);AppAppearance.styleWindow(this);volumeControlStream=AudioManager.STREAM_MUSIC
        if(!SessionManager(this).isLoggedIn()){startActivity(Intent(this,LoginActivity::class.java));finish();return}
        client=PxDependencies.gateway(this)
        val page=column().apply{setBackgroundColor(colors.background)}
        root=column().apply{setPadding(dp(20),dp(14),dp(20),dp(20))}
        val heading=row();heading.addView(text("Rádio PX",28f,true),LinearLayout.LayoutParams(0,-2,1f));connectionBadge=text("● Desligado",12f,true).apply{setTextColor(colors.muted)};heading.addView(connectionBadge);heading.addView(button("⋯"){viewOptions()},LinearLayout.LayoutParams(dp(48),dp(44)).apply{leftMargin=dp(8)});root.addView(heading)
        val tabs=row();privateTab=button("Privado"){channelFilter="private";drawChannels()};tabs.addView(privateTab,LinearLayout.LayoutParams(0,dp(44),1f).apply{rightMargin=dp(5)});groupTab=button("Grupos"){channelFilter="group";drawChannels()};tabs.addView(groupTab,LinearLayout.LayoutParams(0,dp(44),1f).apply{leftMargin=dp(5)});root.addView(tabs,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
        val radio=column().apply{gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(20),dp(18),dp(20),dp(18));background=PxConsoleBackground(this@PxActivity)}
        radio.addView(text("EM CONTATO",10f,true).apply{letterSpacing=.22f;setTextColor(colors.muted);gravity=Gravity.CENTER})
        channelName=text("Escolha seu canal",28f,true).apply{gravity=Gravity.CENTER};radio.addView(channelName)
        status=text("Converse com seus amigos em tempo real",13f).apply{setTextColor(colors.muted);gravity=Gravity.CENTER};radio.addView(status)
        talk=PxTalkButton(this).apply{isEnabled=false;setOnTouchListener{v,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{v.isPressed=true;v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);PxService.press();true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{v.isPressed=false;PxService.release();if(e.actionMasked==MotionEvent.ACTION_UP)v.performClick();true};else->true}}};radio.addView(talk,LinearLayout.LayoutParams(dp(154),dp(154)).apply{topMargin=dp(10)})
        talkLabel=text("Segure para falar",16f,true).apply{setTextColor(colors.gold);gravity=Gravity.CENTER};radio.addView(talkLabel)
        radio.addView(text("Solte para encerrar o microfone",12f).apply{setTextColor(colors.muted);gravity=Gravity.CENTER})
        val controls=row();muteButton=button("Silenciar"){if(PxService.status.value.active)startService(Intent(this,PxService::class.java).setAction(PxService.MUTE))};controls.addView(muteButton,LinearLayout.LayoutParams(0,dp(46),1f).apply{rightMargin=dp(6)});controls.addView(button("Sair do canal"){if(PxService.status.value.active)startService(Intent(this,PxService::class.java).setAction(PxService.STOP))},LinearLayout.LayoutParams(0,dp(46),1f).apply{leftMargin=dp(6)});radio.addView(controls,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)});root.addView(radio,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)})
        contacts=row();root.addView(HorizontalScrollView(this).apply{addView(contacts);isHorizontalScrollBarEnabled=false},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)})
        val actions=row();actions.addView(button("+ Conversa privada"){chooseFriend{peer->work{val result=client.call("private",JSONObject().put("peer",peer.getString("id")));reload();channels.find{it.optString("id")==result.optString("channel")}?.let{channel->runOnUiThread{enter(channel)}}}}},LinearLayout.LayoutParams(0,dp(48),1f).apply{rightMargin=dp(6)})
        actions.addView(button("+ Criar grupo"){val input=EditText(this).apply{hint="Nome do grupo";filters=arrayOf(android.text.InputFilter.LengthFilter(40))};AlertDialog.Builder(this).setTitle("Novo grupo PX").setView(input).setPositiveButton("Criar"){_,_->val name=input.text.toString().trim();if(name.isNotEmpty())work{client.call("group",JSONObject().put("name",name));reload()}}.setNegativeButton("Cancelar",null).show()},LinearLayout.LayoutParams(0,dp(48),1f).apply{leftMargin=dp(6)});root.addView(actions,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(16);bottomMargin=dp(10)})
        cards=column();root.addView(cards)
        val scroll=ScrollView(this).apply{addView(root,android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL));isFillViewport=true;isVerticalScrollBarEnabled=false};scroll.addOnLayoutChangeListener{_,l,_,r,_,_,_,_,_->val target=minOf(r-l,dp(740));if(target>0&&root.layoutParams.width!=target)root.layoutParams=root.layoutParams.apply{width=target}};page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bar=LinearLayout(this);DriverNavigation.fill(this,bar,"PX");page.addView(bar,LinearLayout.LayoutParams(-1,dp(72)));setContentView(page)
        scope.launch{PxService.status.collect{value->
            channelName.text=if(value.active)value.name else "Escolha seu canal";status.text=if(value.active)value.status else "Entre em uma conversa para começar"
            connectionBadge.text=if(value.connected)"● Conectado"else if(value.active)"● Conectando"else "● Desligado";connectionBadge.setTextColor(if(value.connected)colors.green else colors.muted)
            talk.isEnabled=value.connected;talk.transmitting=value.talking;talk.requesting=value.requesting;talk.text=if(value.talking)"Transmitindo • solte para parar"else "Segure para falar"
            talkLabel.text=when{value.talking->"Falando • solte para parar";value.requesting->"Preparando sua fala…";!value.connected->"Entre em um canal";else->"Segure para falar"};talkLabel.setTextColor(if(value.talking)colors.green else colors.gold);muteButton.text=if(value.muted)"Voltar a ouvir"else "Silenciar";muteButton.isEnabled=value.connected
            val renderKey=value.channel+value.connected;if(renderedChannel!=renderKey){renderedChannel=renderKey;drawChannels()}
        }}
        work{reload()}
    }
    private fun viewOptions(){val labels=arrayOf("Bolinha fora do aplicativo","Atualizar canais","Desligar PX");AlertDialog.Builder(this).setTitle("Opções do PX").setItems(labels){_,which->when(which){0->startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));1->work{reload()};2->if(PxService.status.value.active)startService(Intent(this,PxService::class.java).setAction(PxService.STOP))}}.setNegativeButton("Fechar",null).show()}
    private fun work(block:()->Unit){if(busy)return;busy=true;scope.launch{try{withContext(Dispatchers.IO){block()}}catch(error:Exception){Toast.makeText(this@PxActivity,error.message?:"Não foi possível conectar.",Toast.LENGTH_LONG).show()}finally{busy=false;if(!isFinishing)drawChannels()}}}
    private fun reload(){val data=client.call("list").optJSONArray("channels");channels=if(data==null)emptyList()else(0 until data.length()).map{data.getJSONObject(it)};val list=FriendsDependencies.gateway(this,null).call("snapshot").optJSONArray("friends");friends=if(list==null)emptyList()else(0 until list.length()).map{list.getJSONObject(it)}.filter{it.optString("state")=="accepted"}}
    private fun chooseFriend(action:(JSONObject)->Unit){if(friends.isEmpty()){Toast.makeText(this,"Adicione um amigo na aba Amigos e aguarde o aceite.",Toast.LENGTH_LONG).show();return};AlertDialog.Builder(this).setTitle("Escolha um amigo").setItems(friends.map{it.optString("name","Motorista")}.toTypedArray()){_,i->action(friends[i])}.setNegativeButton("Cancelar",null).show()}
    private fun change(action:String,channel:JSONObject,peer:String?=null){work{client.call(action,JSONObject().put("channel",channel.getString("id")).apply{if(peer!=null)put("peer",peer)});reload()}}
    private fun drawChannels(){
        listOf(privateTab to "private",groupTab to "group").forEach{(tab,kind)->val selected=channelFilter==kind;tab.background=surface(if(selected)colors.gold else colors.input,22);tab.setTextColor(if(selected)Color.rgb(28,24,16)else colors.text)}
        contacts.removeAllViews();for(peer in friends.take(10)){val name=peer.optString("name","Amigo");val avatar=text(name.take(1).uppercase()+"\n"+name.substringBefore(" "),12f,true).apply{gravity=Gravity.CENTER;background=surface(colors.panel,18);setPadding(dp(8),dp(8),dp(8),dp(8))};contacts.addView(avatar,LinearLayout.LayoutParams(dp(76),dp(64)).apply{rightMargin=dp(9)})}
        cards.removeAllViews();cards.addView(text(if(channelFilter=="group")"Grupos PX"else "Conversas privadas",19f,true));if(channels.none{it.optString("kind")==channelFilter})cards.addView(text("Sua próxima conversa começa aqui. Adicione um amigo ou crie um grupo privado.",14f))
        for(channel in channels.filter{it.optString("kind")==channelFilter}){val panel=column().apply{setPadding(dp(14),dp(10),dp(14),dp(10));background=GradientDrawable().apply{setColor(this@PxActivity.colors.panel);cornerRadius=dp(14).toFloat();setStroke(dp(1),this@PxActivity.colors.border)}};panel.addView(text(channel.optString("name","Conversa PX"),19f,true));panel.addView(text(if(channel.optString("kind")=="group")"Grupo privado • só entra quem aceitar o convite"else"Conversa privada entre amigos",13f))
            if(channel.optBoolean("accepted")){val current=PxService.status.value.connected&&PxService.status.value.channel==channel.optString("id");panel.addView(button(if(current)"Canal atual"else "Entrar no canal"){enter(channel)}.apply{isEnabled=!current});if(channel.optBoolean("owner")&&channel.optString("kind")=="group"){panel.addView(button("Convidar amigo"){chooseFriend{change("invite",channel,it.getString("id"))}});panel.addView(button("Remover participante"){val members=channel.optJSONArray("members");val others=if(members==null)emptyList()else(0 until members.length()).map{members.getJSONObject(it)}.filter{it.optString("id")!=PxClient.identity(SessionManager(this).getAccessToken())?.substringBefore(":")};AlertDialog.Builder(this).setTitle("Remover do grupo").setItems(others.map{it.optString("name")}.toTypedArray()){_,i->change("remove",channel,others[i].getString("id"))}.setNegativeButton("Cancelar",null).show()});panel.addView(button("Encerrar grupo"){AlertDialog.Builder(this).setMessage("Encerrar este grupo e desconectar os participantes?").setPositiveButton("Encerrar"){_,_->change("close",channel)}.setNegativeButton("Cancelar",null).show()})}else if(channel.optString("kind")=="group")panel.addView(button("Sair do grupo"){change("exit",channel)})}
            else{panel.addView(text("Convite recebido. O áudio só fica disponível depois de aceitar e entrar.",14f));panel.addView(button("Aceitar convite"){change("accept",channel)});panel.addView(button("Recusar convite"){change("exit",channel)})}
            cards.addView(panel,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
        }
    }
    private fun enter(channel:JSONObject){if(PxService.status.value.active){Toast.makeText(this,"Saia do canal atual antes de entrar em outro.",Toast.LENGTH_LONG).show();return};pending=channel
        val permissions=mutableListOf<String>();if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if(Build.VERSION.SDK_INT>=31&&!bluetoothAsked&&ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){bluetoothAsked=true;permissions.add(Manifest.permission.BLUETOOTH_CONNECT)}
        if(permissions.isNotEmpty()){ActivityCompat.requestPermissions(this,permissions.toTypedArray(),901);return};pending=null
        ContextCompat.startForegroundService(this,Intent(this,PxService::class.java).setAction(PxService.JOIN).putExtra("channel",channel.getString("id")).putExtra("name",channel.optString("name")))
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==901){val channel=pending;pending=null;if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&channel!=null)enter(channel)else Toast.makeText(this,"Permita microfone e notificações para entrar no rádio.",Toast.LENGTH_LONG).show()}}
    override fun onResume(){super.onResume();PxService.setUiVisible(true)}
    override fun onPause(){PxService.release();PxService.setUiVisible(false);super.onPause()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}

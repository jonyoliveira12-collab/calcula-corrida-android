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
    private lateinit var talk:Button
    private lateinit var cards:LinearLayout
    private lateinit var client:PxGateway
    private var channels=listOf<JSONObject>()
    private var friends=listOf<JSONObject>()
    private var busy=false
    private var pending:JSONObject?=null
    private var bluetoothAsked=false
    private val colors get()=AppAppearance.palette(this)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun text(value:String,size:Float=15f,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size;setTextColor(colors.text);if(bold)typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(6),0,dp(6))}
    private fun button(label:String,action:()->Unit)=Button(this).apply{text=label;isAllCaps=false;textSize=14f;setOnClickListener{action()}}
    override fun onCreate(saved:Bundle?){AppAppearance.apply(this);super.onCreate(saved);AppAppearance.styleWindow(this);volumeControlStream=AudioManager.STREAM_MUSIC
        if(!SessionManager(this).isLoggedIn()){startActivity(Intent(this,LoginActivity::class.java));finish();return}
        client=PxDependencies.gateway(this)
        val page=column().apply{setBackgroundColor(colors.background)}
        root=column().apply{setPadding(dp(18),dp(16),dp(18),dp(20))}
        root.addView(text("Rádio PX",30f,true));root.addView(text("Converse ao vivo com amigos e grupos privados. Entre no canal para ouvir; segure para falar.",15f))
        status=text("PX desligado",17f,true);root.addView(status)
        root.addView(text("O PX tem uma bolinha própria com o nome do canal. A bolinha JD continua independente.",13f))
        talk=button("Segure para falar"){}.apply{contentDescription="Segure para transmitir no PX e solte para encerrar";isEnabled=false;setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{PxService.press();true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{PxService.release();true};else->true}}};root.addView(talk)
        root.addView(button("Sair / desligar PX"){startService(Intent(this,PxService::class.java).setAction(PxService.STOP))})
        root.addView(button("Permitir bolinha PX fora do aplicativo"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))})
        root.addView(button("Conversa privada com amigo"){chooseFriend{peer->work{val result=client.call("private",JSONObject().put("peer",peer.getString("id")));reload();channels.find{it.optString("id")==result.optString("channel")}?.let{channel->runOnUiThread{enter(channel)}}}}})
        root.addView(button("Criar grupo privado"){val input=EditText(this).apply{hint="Nome do grupo";filters=arrayOf(android.text.InputFilter.LengthFilter(40))};AlertDialog.Builder(this).setTitle("Novo grupo PX").setView(input).setPositiveButton("Criar"){_,_->val name=input.text.toString().trim();if(name.isNotEmpty())work{client.call("group",JSONObject().put("name",name));reload()}}.setNegativeButton("Cancelar",null).show()})
        root.addView(button("Atualizar canais"){work{reload()}})
        cards=column();root.addView(cards)
        val scroll=ScrollView(this).apply{addView(root);isFillViewport=true};page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bar=LinearLayout(this);DriverNavigation.fill(this,bar,"PX");page.addView(bar,LinearLayout.LayoutParams(-1,dp(72)));setContentView(page)
        scope.launch{PxService.status.collect{value->status.text=if(value.active)"${value.name}\n${value.status}"else value.status;talk.isEnabled=value.connected;talk.text=if(value.talking)"Transmitindo • solte para parar"else "Segure para falar";talk.setTextColor(if(value.talking)android.graphics.Color.rgb(0,120,65)else colors.gold)}}
        work{reload()}
    }
    private fun work(block:()->Unit){if(busy)return;busy=true;scope.launch{try{withContext(Dispatchers.IO){block()}}catch(error:Exception){Toast.makeText(this@PxActivity,error.message?:"Não foi possível conectar.",Toast.LENGTH_LONG).show()}finally{busy=false;if(!isFinishing)drawChannels()}}}
    private fun reload(){val data=client.call("list").optJSONArray("channels");channels=if(data==null)emptyList()else(0 until data.length()).map{data.getJSONObject(it)};val list=FriendsDependencies.gateway(this,null).call("snapshot").optJSONArray("friends");friends=if(list==null)emptyList()else(0 until list.length()).map{list.getJSONObject(it)}.filter{it.optString("state")=="accepted"}}
    private fun chooseFriend(action:(JSONObject)->Unit){if(friends.isEmpty()){Toast.makeText(this,"Adicione um amigo na aba Amigos e aguarde o aceite.",Toast.LENGTH_LONG).show();return};AlertDialog.Builder(this).setTitle("Escolha um amigo").setItems(friends.map{it.optString("name","Motorista")}.toTypedArray()){_,i->action(friends[i])}.setNegativeButton("Cancelar",null).show()}
    private fun change(action:String,channel:JSONObject,peer:String?=null){work{client.call(action,JSONObject().put("channel",channel.getString("id")).apply{if(peer!=null)put("peer",peer)});reload()}}
    private fun drawChannels(){cards.removeAllViews();cards.addView(text("Seus canais",21f,true));if(channels.isEmpty())cards.addView(text("Ainda não há canais. Escolha um amigo ou crie um grupo.",14f))
        for(channel in channels){val panel=column().apply{setPadding(dp(14),dp(10),dp(14),dp(10));background=GradientDrawable().apply{setColor(this@PxActivity.colors.panel);cornerRadius=dp(14).toFloat();setStroke(dp(1),this@PxActivity.colors.border)}};panel.addView(text(channel.optString("name","Conversa PX"),19f,true));panel.addView(text(if(channel.optString("kind")=="group")"Grupo privado • só entra quem aceitar o convite"else"Conversa privada entre amigos",13f))
            if(channel.optBoolean("accepted")){panel.addView(button("Entrar no canal"){enter(channel)});if(channel.optBoolean("owner")&&channel.optString("kind")=="group"){panel.addView(button("Convidar amigo"){chooseFriend{change("invite",channel,it.getString("id"))}});panel.addView(button("Remover participante"){val members=channel.optJSONArray("members");val others=if(members==null)emptyList()else(0 until members.length()).map{members.getJSONObject(it)}.filter{it.optString("id")!=PxClient.identity(SessionManager(this).getAccessToken())?.substringBefore(":")};AlertDialog.Builder(this).setTitle("Remover do grupo").setItems(others.map{it.optString("name")}.toTypedArray()){_,i->change("remove",channel,others[i].getString("id"))}.setNegativeButton("Cancelar",null).show()});panel.addView(button("Encerrar grupo"){AlertDialog.Builder(this).setMessage("Encerrar este grupo e desconectar os participantes?").setPositiveButton("Encerrar"){_,_->change("close",channel)}.setNegativeButton("Cancelar",null).show()})}else if(channel.optString("kind")=="group")panel.addView(button("Sair do grupo"){change("exit",channel)})}
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
    override fun onPause(){PxService.release();super.onPause()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}

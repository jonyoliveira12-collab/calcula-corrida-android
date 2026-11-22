package br.com.calculacorrida

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** Separate, opt-in voice service. Never handles or recreates the JD reader bubble. */
class PxService:Service() {
    data class State(val active:Boolean=false,val connected:Boolean=false,val channel:String="",val name:String="",val status:String="PX desligado",val talking:Boolean=false,val speaker:String="",val muted:Boolean=false,val requesting:Boolean=false,val audioInterrupted:Boolean=false,val tapToTalk:Boolean=false)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val wire=Mutex()
    private lateinit var client:PxGateway
    private var room:PxVoice?=null
    private var radioSounds:PxSounds?=null
    private var connection=""
    private var activeChannel=""
    private var held=false
    private var requesting=false
    private var talkJob:Job?=null
    private var talkGeneration=0L
    private var deadline=0L
    private var directVoice=false
    private var connectedUntil=0L
    private var started=0L
    private var stopping=false
    private var wakeLock:PowerManager.WakeLock?=null
    private fun tapMode()=getSharedPreferences("px_audio",MODE_PRIVATE).getBoolean("tap_to_talk",false)
    private fun readyStatus()=if(state.value.audioInterrupted)"Áudio em uso por outro aplicativo" else if(tapMode())"Conectado • toque para falar" else "Conectado • segure para falar"
    private fun toggleTalk(){if(held||requesting||state.value.talking)releaseTalk()else pressTalk()}
    private var bubble:TextView?=null
    private var params:WindowManager.LayoutParams?=null
    private val main=Handler(Looper.getMainLooper())
    private lateinit var window:WindowManager
    private val privacy get()=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("reader_privacy_protected",false)
    private val prefsListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->
        if(::client.isInitialized&&!client.currentAccount())shutdown("Sua sessão foi encerrada.")
        else if(privacy){releaseTalk();removeBubble()}
    }
    private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action==Intent.ACTION_SCREEN_OFF){releaseTalk();removeBubble()};if(intent.action==android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)releaseTalk()}}
    override fun onCreate(){super.onCreate();radioSounds=runCatching{PxSounds(this)}.getOrNull();window=getSystemService(WINDOW_SERVICE)as WindowManager;client=PxDependencies.gateway(this);getSharedPreferences("user_session",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener);getSharedPreferences("settings",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener);ContextCompat.registerReceiver(this,receiver,IntentFilter().apply{addAction(Intent.ACTION_SCREEN_OFF);addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)},ContextCompat.RECEIVER_NOT_EXPORTED)}
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action){
            STOP->shutdown("PX desligado")
            RESUME_AUDIO->{if(state.value.active)room?.resumeAudio()}
            END_TALK->releaseTalk()
            MUTE->{if(!state.value.active){stopSelf();return START_NOT_STICKY};state.value=state.value.copy(muted=!state.value.muted);applyVolumes();notice();paintBubble()}
            JOIN->{
                if(state.value.active)return START_NOT_STICKY
                if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED||!client.currentAccount()){shutdown("Permita o microfone para entrar no PX.");return START_NOT_STICKY}
                val channel=intent.getStringExtra("channel").orEmpty();if(!Regex("[a-fA-F0-9-]{36}").matches(channel)){stopSelf();return START_NOT_STICKY}
                activeChannel=channel
                state.value=State(active=true,channel=channel,name=intent.getStringExtra("name")?.take(40).orEmpty(),status="Conectando ao canal...")
                try{foreground()}catch(_:Exception){shutdown("Não foi possível iniciar o rádio. Abra o aplicativo e tente novamente.");return START_NOT_STICKY}
                wakeLock=(getSystemService(POWER_SERVICE)as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"JHONDRIVER:PX").apply{acquire()}
                state.value=state.value.copy(tapToTalk=tapMode())
                join()
            }
            else->if(!state.value.active)stopSelf()
        }
        return START_NOT_STICKY
    }
    private suspend fun request(action:String,extra:JSONObject=JSONObject()):JSONObject=wire.withLock {withContext(Dispatchers.IO){client.call(action,extra.put("channel",state.value.channel).put("connection",connection))}}
    private fun join(){connection=UUID.randomUUID().toString();scope.launch {
        try {
            val data=request("connect");if(stopping||!client.currentAccount())return@launch
            directVoice=data.optBoolean("direct_ptt");connectedUntil=SystemClock.elapsedRealtime()+10000
            launch{while(isActive&&!stopping){delay(2500);try{val tick=request("tick",JSONObject().put("talking",held&&(requesting||state.value.talking)));connectedUntil=SystemClock.elapsedRealtime()+10000;if(state.value.talking){if(directVoice)deadline=connectedUntil else if(!tick.optBoolean("can_talk"))releaseTalk()else deadline=SystemClock.elapsedRealtime()+(tick.optLong("lease_ms")-2000).coerceIn(0,5000)};if(!directVoice)state.value=state.value.copy(speaker=tick.optString("speaker").takeIf{it!="null"}.orEmpty());paintBubble()}catch(_:Exception){shutdown("A conexão falhou. Entre novamente no PX.")}}}
            val voice=PxVoiceDependencies.create(applicationContext,scope){event,value->when(event){
                "disconnected"->if(!stopping)shutdown("Conexão de voz encerrada. Entre novamente.")
                "focus-lost"->{state.value=state.value.copy(audioInterrupted=true);releaseTalk();applyVolumes();notice();paintBubble()}
                "focus-gained"->{state.value=state.value.copy(audioInterrupted=false);state.value=state.value.copy(status=readyStatus());applyVolumes();notice();paintBubble()}
                "reconnecting"->{releaseTalk();state.value=state.value.copy(connected=false,status="Reconectando ao canal…");notice();paintBubble()}
                "reconnected"->{state.value=state.value.copy(connected=true,status=readyStatus());notice();paintBubble()}
                "capture-interrupted"->{releaseTalk();state.value=state.value.copy(status="Microfone em uso por outro aplicativo. Toque novamente quando terminar.");notice();paintBubble()}
                "speaker"->{state.value=state.value.copy(speaker=value);paintBubble()}
                "track"->applyVolumes()
            }};room=voice
            voice.connect(data.getString("url"),data.getString("token"));voice.microphone(false)
            if(stopping||!client.currentAccount())return@launch
            state.value=state.value.copy(connected=true,name=data.optString("name",state.value.name),status=readyStatus())
            notice();showBubble()

            launch{while(isActive&&!stopping){delay(100);if(state.value.talking&&(SystemClock.elapsedRealtime()>=deadline||SystemClock.elapsedRealtime()-started>=29000||privacy||room?.mayPublish!=true))releaseTalk();if(!client.currentAccount())shutdown("Sua sessão mudou.");if(directVoice&&SystemClock.elapsedRealtime()>=connectedUntil)shutdown("A conexão expirou. Entre novamente no PX.");if(uiVisible||privacy||!Settings.canDrawOverlays(this@PxService))removeBubble()else showBubble()}}
        }catch(error:Exception){if(!stopping)shutdown(error.message?:"Não foi possível entrar no rádio.")}
    }}
    fun pressTalk(){if(held||stopping||!state.value.connected||state.value.audioInterrupted||privacy)return;held=true;val generation=++talkGeneration;radioSounds?.opening();requesting=true;state.value=state.value.copy(requesting=true,status="Preparando sua fala…");paintBubble();talkJob=scope.launch{
        try{val answer=if(directVoice)JSONObject().put("can_talk",SystemClock.elapsedRealtime()<connectedUntil)else request("claim");if(!held||stopping||!client.currentAccount())return@launch
            if(!answer.optBoolean("can_talk")){held=false;state.value=state.value.copy(status="Canal ocupado. Aguarde a outra pessoa terminar.");return@launch}
            val voice=room?:return@launch
            val permitted=withTimeoutOrNull(1800){while(!voice.mayPublish){delay(40)};true}==true
            if(!held||stopping||!permitted){releaseTalk();return@launch}
            deadline=if(directVoice)connectedUntil else SystemClock.elapsedRealtime()+(answer.optLong("lease_ms")-2500).coerceIn(0,4500);started=SystemClock.elapsedRealtime()
            if(deadline<=started||!voice.microphone(true)){releaseTalk();return@launch}
            if(!held||stopping){voice.microphone(false);return@launch}
            state.value=state.value.copy(talking=true,status=if(tapMode())"Transmitindo • toque para parar"else"Transmitindo • solte para parar");notice();paintBubble()
        }catch(error:Exception){if(error is CancellationException)throw error;held=false;state.value=state.value.copy(status=error.message?:"Não foi possível falar.");scope.launch{room?.microphone(false)}}finally{if(talkGeneration==generation){requesting=false;state.value=state.value.copy(requesting=false);paintBubble()}}
    }}
    fun releaseTalk(){val ended=held||state.value.talking;talkGeneration++;held=false;talkJob?.cancel();talkJob=null;requesting=false;deadline=0;val was=state.value.talking;state.value=state.value.copy(talking=false,requesting=false,status=if(state.value.connected)readyStatus()else state.value.status);scope.launch{try{room?.microphone(false)}catch(_:Exception){};if(ended&&!stopping)radioSounds?.closing();if(!stopping&&!directVoice)runCatching{request("release")}};if(was){notice();paintBubble()}}
    private fun applyVolumes(){room?.volumes(state.value.muted||state.value.audioInterrupted)}
    private fun foreground(){val manager=getSystemService(NOTIFICATION_SERVICE)as NotificationManager;manager.createNotificationChannel(NotificationChannel(CHANNEL,"Rádio PX",NotificationManager.IMPORTANCE_LOW));val notification=notification();if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)else startForeground(NOTICE,notification)}
    private fun notification():Notification {val open=PendingIntent.getActivity(this,0,Intent(this,PxActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);fun action(value:String,code:Int)=PendingIntent.getService(this,code,Intent(this,PxService::class.java).setAction(value),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_offer_notification).setContentTitle("PX • "+state.value.name).setContentText(if(state.value.talking)if(tapMode())"Microfone transmitindo • toque para parar"else"Microfone transmitindo • solte para parar"else if(state.value.muted)"Escuta silenciada"else state.value.status).setContentIntent(open).setOngoing(true).setSilent(true).addAction(0,if(state.value.muted)"Ouvir"else"Silenciar",action(MUTE,1)).apply{if(state.value.talking||state.value.requesting)addAction(0,"Parar fala",action(END_TALK,3))}.addAction(0,"Sair do PX",action(STOP,2)).build()}
    private fun notice(){if(state.value.active&&!stopping)runCatching{(getSystemService(NOTIFICATION_SERVICE)as NotificationManager).notify(NOTICE,notification())}}
    private fun showBubble(){if(uiVisible||bubble!=null||!state.value.connected||privacy||!(getSystemService(POWER_SERVICE)as PowerManager).isInteractive||!Settings.canDrawOverlays(this))return
        val density=resources.displayMetrics.density;fun dp(n:Int)=(n*density).toInt()
        val settings=getSharedPreferences("px_bubble",MODE_PRIVATE)
        val view=TextView(this).apply{gravity=Gravity.CENTER;setTextColor(Color.WHITE);textSize=12f;typeface=Typeface.DEFAULT_BOLD;setPadding(dp(8),dp(5),dp(8),dp(5));maxLines=3;elevation=dp(6).toFloat();contentDescription=if(tapMode())"Rádio PX, toque para iniciar ou parar"else"Rádio PX, segure para falar"}
        val layout=WindowManager.LayoutParams(dp(142),dp(68),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=settings.getInt("x",resources.displayMetrics.widthPixels-dp(150)).coerceIn(0,(resources.displayMetrics.widthPixels-dp(142)).coerceAtLeast(0));y=settings.getInt("y",dp(230)).coerceIn(0,(resources.displayMetrics.heightPixels-dp(100)).coerceAtLeast(0))}
        var x=0f;var y=0f;var startX=0;var startY=0;var moved=false
        view.setOnTouchListener{_,event->when(event.actionMasked){MotionEvent.ACTION_DOWN->{x=event.rawX;y=event.rawY;startX=layout.x;startY=layout.y;moved=false;view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);if(!tapMode())pressTalk();true};MotionEvent.ACTION_MOVE->{if(abs(event.rawX-x)>dp(8)||abs(event.rawY-y)>dp(8)){if(!moved){moved=true;releaseTalk()};layout.x=(startX+event.rawX.toInt()-x.toInt()).coerceIn(0,(resources.displayMetrics.widthPixels-dp(142)).coerceAtLeast(0));layout.y=(startY+event.rawY.toInt()-y.toInt()).coerceIn(0,(resources.displayMetrics.heightPixels-dp(100)).coerceAtLeast(0));runCatching{window.updateViewLayout(view,layout)}};true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(event.actionMasked==MotionEvent.ACTION_CANCEL)releaseTalk()else{if(tapMode()){if(!moved)toggleTalk()}else releaseTalk();view.performClick()};settings.edit().putInt("x",layout.x).putInt("y",layout.y).apply();true};else->false}}
        try{window.addView(view,layout);bubble=view;params=layout;paintBubble()}catch(_:Exception){bubble=null}
    }
    private fun paintBubble(){bubble?.apply{text="PX • ${state.value.name}\n"+when{state.value.talking->"FALANDO";requesting->"AGUARDE";state.value.muted->"SILENCIADO";state.value.speaker.isNotBlank()->"${state.value.speaker} fala";state.value.audioInterrupted->"ÁUDIO EM USO";else->if(tapMode())"Toque para falar"else"Segure para falar"};background=GradientDrawable().apply{setColor(if(PxService.state.value.talking)Color.rgb(10,120,75)else Color.rgb(27,36,51));cornerRadius=24f;setStroke(2,Color.rgb(226,188,85))}}}
    private fun removeBubble(){if(bubble!=null&&held)releaseTalk();main.removeCallbacksAndMessages(null);bubble?.let{runCatching{window.removeView(it)}};bubble=null;params=null}
    private fun shutdown(message:String){if(stopping)return;stopping=true;wakeLock?.let{if(it.isHeld)it.release()};wakeLock=null;held=false;state.value=State(status=message);removeBubble();room?.close();room=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onTaskRemoved(rootIntent:Intent?){shutdown("PX desligado ao fechar o aplicativo.")}
    override fun onConfigurationChanged(config:android.content.res.Configuration){super.onConfigurationChanged(config);releaseTalk();removeBubble();showBubble()}
    override fun onDestroy(){wakeLock?.let{if(it.isHeld)it.release()};wakeLock=null;radioSounds?.close();radioSounds=null;stopping=true;held=false;removeBubble();room?.close();room=null;scope.cancel();getSharedPreferences("user_session",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener);getSharedPreferences("settings",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener);runCatching{unregisterReceiver(receiver)};val channel=activeChannel;val conn=connection;state.value=State(status=state.value.status);if(channel.isNotBlank()&&conn.isNotBlank())Thread{runCatching{client.call("leave",JSONObject().put("channel",channel).put("connection",conn))}}.start();super.onDestroy();instance=null}
    companion object{
        const val RESUME_AUDIO="br.com.calculacorrida.PX_RESUME_AUDIO";const val END_TALK="br.com.calculacorrida.PX_END_TALK";const val JOIN="br.com.calculacorrida.PX_JOIN";const val STOP="br.com.calculacorrida.PX_STOP";const val MUTE="br.com.calculacorrida.PX_MUTE";private const val CHANNEL="jd_px_voice";private const val NOTICE=904
        private val state=MutableStateFlow(State());val status=state.asStateFlow()
        @Volatile private var instance:PxService?=null
        @Volatile private var uiVisible=false
        fun setUiVisible(value:Boolean){uiVisible=value;if(value)instance?.removeBubble()else instance?.showBubble()}
        internal fun overlayRectForTest():android.graphics.Rect? {val view=instance?.bubble?:return null;val xy=IntArray(2);view.getLocationOnScreen(xy);return android.graphics.Rect(xy[0],xy[1],xy[0]+view.width,xy[1]+view.height)}
        internal fun stoppedForTest()=instance==null
        internal fun overlayVisibleForTest()=instance?.bubble!=null
        fun press(){instance?.pressTalk()};fun release(){instance?.releaseTalk()}
        fun toggle(){instance?.toggleTalk()}
        fun setTapMode(context:Context,value:Boolean){release();context.getSharedPreferences("px_audio",MODE_PRIVATE).edit().putBoolean("tap_to_talk",value).apply();state.value=state.value.copy(tapToTalk=value);instance?.let{state.value=state.value.copy(status=it.readyStatus());it.paintBubble()}}
    }
    init{instance=this}
}

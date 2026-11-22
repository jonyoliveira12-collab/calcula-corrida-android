package br.com.calculacorrida

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.*
import livekit.org.webrtc.AudioTrackSink
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Android WebRTC -> LiveKit -> Android PCM. Only ephemeral synthetic accounts enter this test. */
@RunWith(AndroidJUnit4::class)
class PxLiveNetworkTest {
    @Test fun liveAudioAndRevocation() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext;val file=File(context.filesDir,"px-qa.json");assumeTrue("Live fixture is supplied only by trusted CI",file.isFile)
        val fixture=JSONObject(file.readText());file.delete();val password=fixture.getString("password");val users=fixture.getJSONArray("users");val tokens=mutableListOf<String>();val http=OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build()
        fun login(i:Int):String {val request=Request.Builder().url("https://hyoaomusfpeccdchgoik.supabase.co/auth/v1/token?grant_type=password").header("apikey","sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX").post(JSONObject().put("email",users.getJSONObject(i).getString("email")).put("password",password).toString().toRequestBody("application/json".toMediaType())).build();return http.newCall(request).execute().use{assertTrue("Synthetic account login",it.isSuccessful);JSONObject(it.body!!.string()).getString("access_token")}}
        fun shell(command:String){android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use{it.readBytes()}}
        val pref=context.getSharedPreferences("user_session",0);val original=pref.all.toMap();var activity:Activity?=null;val old=PxDependencies.gateway
        val deviceAudio=context.getSystemService(android.media.AudioManager::class.java);val originalAudioMode=deviceAudio.mode
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val a=LivePxVoice(context,scope){_,_->};val b=LiveKit.create(context);var clients=listOf<PxClient>();var channel="";val ids=listOf(UUID.randomUUID().toString(),UUID.randomUUID().toString());var speaking=false
        try{tokens.add(login(0));tokens.add(login(1));val social=tokens.map{FriendsClient(context,it)};val profiles=social.mapIndexed{i,c->c.call("profile",JSONObject().put("name","PX áudio QA $i"))};val peer=users.getJSONObject(1).getString("id")
            var current=social[0].call("snapshot").getJSONArray("friends");if((0 until current.length()).any{current.getJSONObject(it).optString("id")==peer&&current.getJSONObject(it).optString("state")=="blocked"}){social[0].call("unblock",JSONObject().put("peer",peer));current=social[0].call("snapshot").getJSONArray("friends")};if((0 until current.length()).none{current.getJSONObject(it).optString("id")==peer&&current.getJSONObject(it).optString("state")=="accepted"}){social[0].call("request",JSONObject().put("code",profiles[1].getJSONObject("me").getString("code")));social[1].call("respond",JSONObject().put("peer",users.getJSONObject(0).getString("id")).put("accept",true))}
            clients=tokens.map{PxClient(context,it)};channel=clients[0].call("private",JSONObject().put("peer",peer)).getString("channel")
            inst.runOnMainSync{SessionManager(context).saveUser("px-audio-qa@example.invalid",tokens[0],null)};PxDependencies.gateway={object:PxGateway{override fun currentAccount()=true;override fun call(action:String,data:JSONObject)=JSONObject().put("channels",org.json.JSONArray())}}
            shell("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
            activity=inst.startActivitySync(Intent(context,PxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync()
            val joins=clients.mapIndexed{i,c->c.call("connect",JSONObject().put("channel",channel).put("connection",ids[i]))};val received=CountDownLatch(5)
            scope.launch{b.events.collect{if(it is RoomEvent.TrackSubscribed){val track=it.track;if(track is RemoteAudioTrack)track.addSink(AudioTrackSink{bytes,_,_,_,frames,_->if(bytes.remaining()>0&&frames>0)received.countDown()})}}}
            val pulse=scope.launch{while(isActive){delay(2000);for(i in 0..1)withContext(Dispatchers.IO){clients[i].call("tick",JSONObject().put("channel",channel).put("connection",ids[i]).put("talking",i==0&&speaking))}}}
            runBlocking{withTimeout(60000){a.connect(joins[0].getString("url"),joins[0].getString("token"));b.connect(joins[1].getString("url"),joins[1].getString("token"))}}
            val audio=context.getSystemService(android.media.AudioManager::class.java);assertTrue("Microphone must remain absent on join",audio.activeRecordingConfigurations.isEmpty())

            assertTrue("Channel explicitly authorizes direct PTT",joins[0].getBoolean("direct_ptt"))
            speaking=true
            runBlocking{assertTrue("SDK received channel microphone authorization",withTimeoutOrNull(10000){while(!a.mayPublish)delay(40);true}==true);assertTrue(a.microphone(true))}
            assertTrue("Real remote audio PCM arrived through LiveKit",received.await(15,TimeUnit.SECONDS))
            runBlocking{a.microphone(false);withTimeout(6000){while(audio.activeRecordingConfigurations.isNotEmpty())delay(100)}};speaking=false
            assertTrue("Joining permission persists while the microphone is fully stopped",a.mayPublish)
            // A second hold must restart the actual recorder after unpublishing.
            speaking=true;runBlocking{assertTrue(a.microphone(true));withTimeout(6000){while(audio.activeRecordingConfigurations.isEmpty())delay(100)};a.microphone(false);withTimeout(6000){while(audio.activeRecordingConfigurations.isNotEmpty())delay(100)}};speaking=false
            runBlocking{pulse.cancelAndJoin()};social[0].call("block",JSONObject().put("peer",peer));runBlocking{withTimeout(20000){while(b.state.name!="DISCONNECTED")delay(100)}};assertTrue("Removed friend cannot rejoin",runCatching{clients[1].call("connect",JSONObject().put("channel",channel).put("connection",UUID.randomUUID().toString()))}.isFailure);pulse.cancel()
        }finally{b.disconnect();b.release();a.close();scope.cancel();deviceAudio.mode=originalAudioMode;for(i in clients.indices)runCatching{clients[i].call("leave",JSONObject().put("channel",channel).put("connection",ids[i]))};PxDependencies.gateway=old;inst.runOnMainSync{activity?.finish();pref.edit().clear().apply();val e=pref.edit();for((k,v)in original)when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v)};e.apply()}}
    }
}

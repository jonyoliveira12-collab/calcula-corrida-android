package br.com.calculacorrida

import android.Manifest
import android.content.Intent
import android.media.*
import androidx.test.platform.app.InstrumentationRegistry
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.*
import livekit.org.webrtc.AudioTrackSink
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Disposable local SFU and synthetic credentials only; never connects to production. */
class PxLocalMediaTest {
    @Test fun playbackDoesNotReserveCallModeAndExternalRecordingWins() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val input=File(context.filesDir,"px-local.json");assumeTrue("Local SFU supplied by CI",input.isFile)
        val data=JSONObject(input.readText());input.delete()
        fun shell(command:String){android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use{it.readBytes()}}
        val session=context.getSharedPreferences("user_session",0);val original=session.all.toMap()
        val oldGateway=PxDependencies.gateway;val oldFriends=FriendsDependencies.gateway
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val audio=context.getSystemService(AudioManager::class.java);val oldMode=audio.mode
        var activity:android.app.Activity?=null;var recorder:AudioRecord?=null
        val sender=LivePxVoice(context,scope){_,_->};val receiver=LiveKit.create(context)
        receiver.audioSwitchHandler?.manageAudioFocus=false
        receiver.audioSwitchHandler?.audioMode=AudioManager.MODE_NORMAL
        val first=CountDownLatch(5);val second=CountDownLatch(5);var phase=0
        try {
            PxDependencies.gateway={object:PxGateway{override fun currentAccount()=true;override fun call(action:String,data:JSONObject)=JSONObject().put("channels",JSONArray())}}
            FriendsDependencies.gateway={_,_->object:FriendsGateway{override fun call(action:String,data:JSONObject)=JSONObject().put("friends",JSONArray())}}
            inst.runOnMainSync{SessionManager(context).saveUser("local-px@example.invalid","synthetic",null)}
            shell("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
            activity=inst.startActivitySync(Intent(context,PxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            scope.launch{receiver.events.collect{if(it is RoomEvent.TrackSubscribed)(it.track as? RemoteAudioTrack)?.addSink(AudioTrackSink{bytes,_,_,_,frames,_->if(bytes.remaining()>0&&frames>0){if(phase==1)first.countDown();if(phase==2)second.countDown()}})}}
            runBlocking{withTimeout(45000){sender.connect(data.getString("url"),data.getString("a"));receiver.connect(data.getString("url"),data.getString("b"));while(!sender.mayPublish)delay(50)}}
            assertEquals("Listening radio does not hold call mode",AudioManager.MODE_NORMAL,audio.mode)
            assertTrue("Joining never starts recorder",audio.activeRecordingConfigurations.isEmpty())
            phase=1;runBlocking{assertTrue(sender.microphone(true))};assertTrue("Remote receives actual PCM",first.await(15,TimeUnit.SECONDS))
            runBlocking{sender.microphone(false);withTimeout(8000){while(audio.activeRecordingConfigurations.isNotEmpty())delay(100)}}
            val size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
            recorder=AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size*2)
            assertEquals(AudioRecord.STATE_INITIALIZED,recorder.state);recorder.startRecording()
            runBlocking{withTimeout(6000){while(audio.activeRecordingConfigurations.isEmpty())delay(100)}}
            val blocked=runBlocking{runCatching{sender.microphone(true)}}
            assertTrue("PX cannot take microphone from another recorder",blocked.isFailure)
            assertEquals("External recording continues",AudioRecord.RECORDSTATE_RECORDING,recorder.recordingState)
            assertTrue("External recorder produces samples",recorder.read(ShortArray(320),0,320)>0)
            recorder.stop();recorder.release();recorder=null
            runBlocking{withTimeout(6000){while(audio.activeRecordingConfigurations.isNotEmpty())delay(100)}}
            assertEquals("Receiver stayed in the same room",io.livekit.android.room.Room.State.CONNECTED,receiver.state)
            phase=2;runBlocking{assertTrue(sender.microphone(true))};assertTrue("Audio resumes without rejoining",second.await(15,TimeUnit.SECONDS));runBlocking{sender.microphone(false)}
        } finally {
            runCatching{recorder?.stop()};recorder?.release();sender.close();receiver.disconnect();receiver.release();scope.cancel();audio.mode=oldMode
            PxDependencies.gateway=oldGateway;FriendsDependencies.gateway=oldFriends
            inst.runOnMainSync{activity?.finish();session.edit().clear().apply();val edit=session.edit();original.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v)}};edit.apply()}
        }
    }
}

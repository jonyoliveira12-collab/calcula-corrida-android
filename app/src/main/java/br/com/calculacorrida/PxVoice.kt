package br.com.calculacorrida

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.LocalAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface PxVoice {
    val mayPublish:Boolean
    suspend fun connect(url:String,token:String)
    suspend fun microphone(enabled:Boolean):Boolean
    fun resumeAudio() {}
    fun volumes(muted:Boolean)
    fun close()
}
internal object PxVoiceDependencies {
    @Volatile var create:(Context,CoroutineScope,(String,String)->Unit)->PxVoice={context,scope,event->LivePxVoice(context,scope,event)}
}
internal class LivePxVoice(context:Context,scope:CoroutineScope,event:(String,String)->Unit):PxVoice {
    private val audio=context.getSystemService(Context.AUDIO_SERVICE)as android.media.AudioManager
    private val room=LiveKit.create(context.applicationContext)
    private var microphoneTrack:LocalAudioTrack?=null
    private val microphoneLock=Mutex()
    private val recordingCallback=object:android.media.AudioManager.AudioRecordingCallback(){
        override fun onRecordingConfigChanged(configs:MutableList<android.media.AudioRecordingConfiguration>){
            if(android.os.Build.VERSION.SDK_INT>=29 && microphoneTrack!=null && configs.any{it.isClientSilenced})
                scope.launch{event("capture-interrupted","")}
        }
    }
    override val mayPublish get()=room.localParticipant.permissions?.canPublish==true
    init {
        audio.registerAudioRecordingCallback(recordingCallback,android.os.Handler(android.os.Looper.getMainLooper()))
        room.audioSwitchHandler?.loggingEnabled=false
        // A listening radio is media playback, not a continuous phone call.
        room.audioSwitchHandler?.audioMode=android.media.AudioManager.MODE_NORMAL
        room.audioSwitchHandler?.audioAttributeUsageType=android.media.AudioAttributes.USAGE_MEDIA
        room.audioSwitchHandler?.registerOnAudioFocusChangeListener{change->scope.launch{event(if(change==android.media.AudioManager.AUDIOFOCUS_GAIN)"focus-gained"else"focus-lost","")}}
        scope.launch{room.events.collect { when(it){
            is RoomEvent.Disconnected->event("disconnected","")
            is RoomEvent.Reconnecting->event("reconnecting","")
            is RoomEvent.Reconnected->event("reconnected","")
            is RoomEvent.ActiveSpeakersChanged->event("speaker",it.speakers.firstOrNull()?.name.orEmpty())
            is RoomEvent.TrackSubscribed->event("track","")
            else->{}
        }}}
    }
    override suspend fun connect(url:String,token:String){room.connect(url,token)}
    override suspend fun microphone(enabled:Boolean):Boolean = microphoneLock.withLock {
        if(enabled){if(audio.activeRecordingConfigurations.isNotEmpty())error("Microfone em uso por outro aplicativo. Termine o áudio e tente novamente.");microphoneTrack=room.localParticipant.getOrCreateDefaultAudioTrack();return@withLock room.localParticipant.setMicrophoneEnabled(true)}
        // Muting a WebRTC publication alone can keep AudioRecord alive. Stop capture too.
        microphoneTrack?.stopPrewarm();microphoneTrack?.stop()
        room.localParticipant.trackPublications.values.toList().forEach{publication->(publication.track as? LocalAudioTrack)?.let{it.stopPrewarm();room.localParticipant.unpublishTrack(it,true)}}
        microphoneTrack=null;true
    }
    override fun resumeAudio(){room.audioSwitchHandler?.stop();room.audioSwitchHandler?.start()}
    override fun volumes(muted:Boolean){room.remoteParticipants.values.forEach{p->p.trackPublications.values.forEach{publication->(publication.track as? RemoteAudioTrack)?.setVolume(if(muted)0.0 else 1.0)}}}
    override fun close(){audio.unregisterAudioRecordingCallback(recordingCallback);runCatching{microphoneTrack?.stopPrewarm()};runCatching{microphoneTrack?.stop()};microphoneTrack=null;room.disconnect();room.release()}
}

package br.com.calculacorrida

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.LocalAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal interface PxVoice {
    val mayPublish:Boolean
    suspend fun connect(url:String,token:String)
    suspend fun microphone(enabled:Boolean):Boolean
    fun volumes(muted:Boolean)
    fun close()
}
internal object PxVoiceDependencies {
    @Volatile var create:(Context,CoroutineScope,(String,String)->Unit)->PxVoice={context,scope,event->LivePxVoice(context,scope,event)}
}
internal class LivePxVoice(context:Context,scope:CoroutineScope,event:(String,String)->Unit):PxVoice {
    private val room=LiveKit.create(context.applicationContext)
    private var microphoneTrack:LocalAudioTrack?=null
    override val mayPublish get()=room.localParticipant.permissions?.canPublish==true
    init {
        room.audioSwitchHandler?.loggingEnabled=false
        room.audioSwitchHandler?.registerOnAudioFocusChangeListener{change->if(change<=0)scope.launch{event("focus-lost","")}}
        scope.launch{room.events.collect { when(it){
            is RoomEvent.Disconnected->event("disconnected","")
            is RoomEvent.Reconnecting->event("disconnected","")
            is RoomEvent.ActiveSpeakersChanged->event("speaker",it.speakers.firstOrNull()?.name.orEmpty())
            is RoomEvent.TrackSubscribed->event("track","")
            else->{}
        }}}
    }
    override suspend fun connect(url:String,token:String){room.connect(url,token)}
    override suspend fun microphone(enabled:Boolean):Boolean {
        if(enabled){microphoneTrack=room.localParticipant.getOrCreateDefaultAudioTrack();return room.localParticipant.setMicrophoneEnabled(true)}
        // Muting a WebRTC publication alone can keep AudioRecord alive. Stop capture too.
        microphoneTrack?.stopPrewarm();microphoneTrack?.stop()
        room.localParticipant.trackPublications.values.toList().forEach{publication->(publication.track as? LocalAudioTrack)?.let{it.stopPrewarm();room.localParticipant.unpublishTrack(it,true)}}
        microphoneTrack=null;return true
    }
    override fun volumes(muted:Boolean){room.remoteParticipants.values.forEach{p->p.trackPublications.values.forEach{publication->(publication.track as? RemoteAudioTrack)?.setVolume(if(muted)0.0 else 1.0)}}}
    override fun close(){microphoneTrack?.stopPrewarm();microphoneTrack?.stop();microphoneTrack=null;room.disconnect();room.release()}
}

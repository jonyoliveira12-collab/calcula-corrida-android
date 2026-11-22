package br.com.calculacorrida

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Preloaded local effects. Feedback never delays capture and is not injected into the voice stream. */
class PxSounds(context:Context):AutoCloseable {
    private val prefs=context.applicationContext.getSharedPreferences("px_audio",Context.MODE_PRIVATE)
    private val ready=java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val pool=SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private var sounds=emptyMap<String,Pair<Int,Int>>()
    @Volatile private var closed=false
    @Volatile private var preview=false
    init {
        pool.setOnLoadCompleteListener{_,id,status->if(status==0&&!closed){ready.add(id);checkPreview()}}
        sounds=mapOf("traditional" to (pool.load(context,R.raw.px_open,1) to pool.load(context,R.raw.px_close,1)),"digital" to (pool.load(context,R.raw.px_digital_open,1) to pool.load(context,R.raw.px_digital_close,1)))
    }
    private fun play(open:Boolean):Boolean {
        if(closed)return false
        val pair=sounds[prefs.getString("sound","traditional")?:"traditional"]?:return false
        val id=if(open)pair.first else pair.second
        if(!ready.contains(id))return false
        return runCatching{pool.play(id,.65f,.65f,1,0,1f)!=0}.getOrDefault(false)
    }
    fun opening(){play(true)}
    fun closing(){play(false)}
    fun previewWhenReady(){preview=true;checkPreview()}
    private fun checkPreview(){if(preview&&play(true))preview=false}
    override fun close(){closed=true;preview=false;pool.release()}
}

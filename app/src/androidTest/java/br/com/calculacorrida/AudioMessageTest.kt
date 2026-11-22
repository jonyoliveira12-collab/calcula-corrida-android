package br.com.calculacorrida

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AudioMessageTest {
    companion object {
        private fun displayCommand(command:String) {
            val inst=InstrumentationRegistry.getInstrumentation()
            ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        @JvmStatic @org.junit.BeforeClass fun prepareDisplay() {
            displayCommand("wm user-rotation lock 0")
            displayCommand("wm size 1280x800")
            displayCommand("wm density 160")
            val inst=InstrumentationRegistry.getInstrumentation()
            val end=SystemClock.elapsedRealtime()+12000
            var stable=0
            while(stable<3 && SystemClock.elapsedRealtime()<end) {
                val image=inst.uiAutomation.takeScreenshot()
                val ready=image!=null && image.width==1280 && image.height==800 && inst.targetContext.resources.displayMetrics.densityDpi==160
                image?.recycle()
                stable=if(ready)stable+1 else 0
                Thread.sleep(150)
            }
            assertEquals("Stable chat fixture display before opening any Activity",3,stable)
            inst.waitForIdleSync()
        }
        @JvmStatic @org.junit.AfterClass fun restoreDisplay() {
            displayCommand("wm size reset")
            displayCommand("wm density reset")
            displayCommand("wm user-rotation free")
        }
    }
    private class Chat {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        val session=context.getSharedPreferences("user_session",0)
        val saved=session.all.toMap()
        val oldTheme=AppAppearance.theme(context)
        val oldGateway=FriendsDependencies.gateway
        val oldMedia=FriendMediaDependencies.create
        lateinit var activity:Activity
        val uploads=AtomicInteger();val downloads=AtomicInteger()
        val failDownload=AtomicBoolean();val failSend=AtomicBoolean()
        val attempts=CopyOnWriteArrayList<JSONObject>()
        val stored=ConcurrentHashMap<String,JSONObject>()
        val files=ConcurrentHashMap<String,ByteArray>()
        val self="00000000-0000-0000-0000-000000000011"
        val peer="00000000-0000-0000-0000-000000000012"
        val incoming="$peer/audio/00000000-0000-0000-0000-000000000013.m4a"
        val outgoing="$self/audio/00000000-0000-0000-0000-000000000014.m4a"
        fun snapshot():JSONObject {
            val messages=JSONArray().put(JSONObject().put("id","incoming-audio").put("sender",peer).put("kind","audio").put("audio_path",incoming).put("duration_ms",2000).put("time_ms",System.currentTimeMillis()))
            stored.values.forEach{messages.put(it)}
            return JSONObject().put("ok",true).put("now_ms",System.currentTimeMillis())
                .put("me",JSONObject().put("id",self).put("name","Motorista de exemplo").put("code","EXEMPLO12345"))
                .put("friends",JSONArray().put(JSONObject().put("id",peer).put("name","Amigo de exemplo").put("state","accepted").put("online",false)))
                .put("messages",messages)
        }
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}
        fun start() {
            files[incoming]=inst.context.assets.open("chat-tone.m4a").use{it.readBytes()}
            FriendsDependencies.gateway={_,_->object:FriendsGateway {
                override fun call(action:String,data:JSONObject):JSONObject {
                    if(action=="send") {
                        attempts.add(JSONObject(data.toString()))
                        assertEquals("audio",data.optString("kind"))
                        assertTrue("Audio send never submits the text draft",data.optString("body").isEmpty())
                        val nonce=data.getString("nonce")
                        stored.putIfAbsent(nonce,JSONObject(data.toString()).put("id",nonce).put("sender",self).put("time_ms",System.currentTimeMillis()))
                        if(failSend.getAndSet(false))throw IOException("Simulated lost send response")
                    }
                    return snapshot()
                }
            }}
            FriendMediaDependencies.create={object:FriendMediaGateway {
                override fun uploadPhoto(uri:Uri):String=error("Not used")
                override fun uploadAudio(file:File):String {
                    uploads.incrementAndGet()
                    val probe=MediaMetadataRetriever()
                    try {probe.setDataSource(file.absolutePath);assertTrue("Recorder produces an actual decodable MP4",probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()>=300)} finally {probe.release()}
                    files[outgoing]=file.readBytes();return outgoing
                }
                override fun download(path:String):ByteArray {
                    downloads.incrementAndGet()
                    if(failDownload.getAndSet(false))throw IOException("Simulated offline media request")
                    return files[path] ?: error("Unknown private test file")
                }
            }}
            shell("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
            AppAppearance.setTheme(context,"gray")
            SessionManager(context).saveUser("audio-flow@example.invalid","synthetic-token",null)
            activity=inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await{has("CONVERSAR E VER MAPA")};Thread.sleep(100);click("CONVERSAR E VER MAPA")
            await{has("▶ OUVIR ÁUDIO")}
        }
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
        fun labels()=views(activity.window.decorView).filterIsInstance<TextView>()
        fun has(prefix:String):Boolean {var found=false;inst.runOnMainSync{found=labels().any{it.text.toString().startsWith(prefix) && it.isShown}};return found}
        fun click(prefix:String,last:Boolean=false) {await{var ready=false;inst.runOnMainSync{ready=labels().filterIsInstance<Button>().any{it.isShown && it.isEnabled && it.text.toString().startsWith(prefix)}};ready};inst.runOnMainSync{val items=labels().filterIsInstance<Button>().filter{it.isShown && it.isEnabled && it.text.toString().startsWith(prefix)}; (if(last)items.last() else items.first()).performClick()};inst.waitForIdleSync()}
        fun await(condition:()->Boolean) {val end=SystemClock.elapsedRealtime()+12000;var matched=condition();while(!matched && SystemClock.elapsedRealtime()<end){Thread.sleep(80);matched=condition()};assertTrue("Audio flow reaches the expected state",matched)}
        @Suppress("UNCHECKED_CAST") fun <T> field(name:String):T? {var result:Any?=null;inst.runOnMainSync{result=FriendsActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)};return result as T?}
        fun playing():Boolean {var value=false;inst.runOnMainSync{val player=FriendsActivity::class.java.getDeclaredField("player").apply{isAccessible=true}.get(activity) as? MediaPlayer;value=runCatching{player?.isPlaying==true && player.currentPosition>100}.getOrDefault(false)};return value}
        fun screen(name:String){inst.waitForIdleSync();val image=inst.uiAutomation.takeScreenshot()!!;val folder=File(context.filesDir,"screenshots").apply{mkdirs()};File(folder,name).outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        fun close() {
            if(::activity.isInitialized)inst.runOnMainSync{activity.finish()}
            inst.waitForIdleSync()
            FriendsDependencies.gateway=oldGateway;FriendMediaDependencies.create=oldMedia;AppAppearance.setTheme(context,oldTheme)
            val edit=session.edit().clear();for((key,value)in saved)when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value);is Float->edit.putFloat(key,value)};edit.commit()
        }
    }
    private fun withChat(block:(Chat)->Unit){val chat=Chat();try{chat.start();block(chat)}finally{chat.close()}}

    @Test fun recordingCanBeSentDirectlyAndTheActualEncodedFilePlays()=withChat { chat->
        chat.click("GRAVAR ÁUDIO");chat.await{chat.has("ENVIAR ÁUDIO")}
        assertNotNull(chat.field<Any>("recorder"))
        chat.inst.runOnMainSync{assertFalse("Text send is hidden during capture",chat.labels().any{it.text.toString()=="ENVIAR" && it.isShown})}
        Thread.sleep(1400)
        chat.inst.runOnMainSync {
            val send=chat.labels().filterIsInstance<Button>().first{it.text.toString()=="ENVIAR ÁUDIO" && it.isShown}
            send.performClick();send.performClick()
        }
        chat.await{chat.has("GRAVAR ÁUDIO") && chat.stored.size==1}
        assertEquals(1,chat.uploads.get());assertEquals(1,chat.attempts.size);assertNull(chat.field<Any>("recorder"))
        chat.click("▶ OUVIR ÁUDIO",last=true);chat.await{chat.playing()}
        val privateFile=chat.field<File>("playbackFile")!!
        assertTrue(privateFile.exists());assertTrue(chat.has("Reproduzindo"))
        chat.screen("audio-sent-playing.png")
        chat.click("■ PARAR ÁUDIO");chat.await{chat.field<MediaPlayer>("player")==null};assertFalse(privateFile.exists())
        // The incoming sample is a known non-silent AAC/MP4 tone; verify decoding and completion too.
        chat.click("▶ OUVIR ÁUDIO");chat.await{chat.playing()};chat.await{chat.has("Áudio finalizado")}
        assertEquals(2,chat.downloads.get())
    }
    @Test fun playbackFailureIsVisibleInsideChatAndCanBeRetried()=withChat {chat->
        chat.failDownload.set(true);chat.click("▶ OUVIR ÁUDIO")
        chat.await{chat.has("Áudio indisponível")}
        val notice=chat.field<TextView>("playbackState")!!
        chat.inst.runOnMainSync{val bounds=android.graphics.Rect();assertTrue("Playback errors remain visible with the left intro collapsed",notice.getGlobalVisibleRect(bounds) && bounds.height()==notice.height)}
        chat.click("▶ OUVIR ÁUDIO");chat.await{chat.playing()}
        val file=chat.field<File>("playbackFile")!!
        chat.inst.runOnMainSync{chat.activity.finish()};chat.inst.waitForIdleSync()
        // finish() schedules lifecycle callbacks; wait for onPause/onDestroy,
        // rather than treating an idle queue as a completed Activity teardown.
        chat.await{chat.field<MediaPlayer>("player")==null && !file.exists()}
        assertNull(chat.field<MediaPlayer>("player"));assertFalse("Leaving deletes private playback bytes",file.exists())
    }
    @Test fun changingAccountCannotPlayAnOldUnsentRecording()=withChat {chat->
        chat.click("GRAVAR ÁUDIO");Thread.sleep(1400);chat.click("PARAR GRAVAÇÃO")
        val oldFile=chat.field<File>("audioFile")!!
        SessionManager(chat.context).saveUser("other-account@example.invalid","another-synthetic-token",null)
        chat.click("OUVIR / PARAR");chat.await{chat.has("Sua sessão mudou")}
        assertNull(chat.field<MediaPlayer>("player"));assertNull(chat.field<File>("audioFile"))
        assertFalse(oldFile.exists());assertEquals(0,chat.uploads.get())
    }
    @Test fun lostSendResponseKeepsAudioAndRetryDoesNotUploadOrDeliverTwice()=withChat {chat->
        chat.failSend.set(true);chat.click("GRAVAR ÁUDIO");Thread.sleep(1400);chat.click("ENVIAR ÁUDIO")
        chat.await{chat.has("Áudio não enviado")}
        assertNotNull(chat.field<File>("audioFile"));assertEquals(1,chat.uploads.get())
        chat.click("ENVIAR ÁUDIO");chat.await{chat.has("GRAVAR ÁUDIO")}
        assertEquals(1,chat.uploads.get());assertEquals(1,chat.stored.size);assertEquals(2,chat.attempts.size)
        assertEquals(chat.attempts[0].getString("nonce"),chat.attempts[1].getString("nonce"))
        assertEquals(chat.attempts[0].getString("audio_path"),chat.attempts[1].getString("audio_path"))
    }
}

package br.com.calculacorrida

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriverRefreshTest {
    @Test fun accountSwitchCannotUploadOrReadAnotherDriversMedia() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("user_session",0);val previous=prefs.all.toMap()
        fun token(user:String):String {
            val claims=JSONObject().put("sub",user).put("session_id",java.util.UUID.randomUUID().toString()).toString()
            return "test."+android.util.Base64.encodeToString(claims.toByteArray(),android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)+".invalid"
        }
        try {
            val a=java.util.UUID.randomUUID().toString();val b=java.util.UUID.randomUUID().toString()
            SessionManager(context).saveUser("media-owner-a@example.invalid",token(a),null)
            val store=FriendMediaStore(context)
            SessionManager(context).saveUser("media-owner-b@example.invalid",token(b),null)
            var refused=false
            try {store.uploadPhoto(android.net.Uri.EMPTY)}catch(error:IllegalStateException){refused=error.message?.contains("conta foi alterada")==true}
            assertTrue("Queued photo work is bound to its original account before reading/uploading",refused)
            refused=false
            try {store.download(a+"/avatars/"+java.util.UUID.randomUUID()+".jpg")}catch(error:IllegalStateException){refused=error.message?.contains("conta foi alterada")==true}
            assertTrue("Queued downloads cannot use a later account session",refused)
        } finally {
            val edit=prefs.edit().clear();for((key,value)in previous)when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value);is Float->edit.putFloat(key,value)};edit.commit()
        }
    }

    @Test fun optionalAudioIsRecordedReviewedAndDiscardedWithoutSending() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val oldFactory=FriendsDependencies.gateway;val previousTheme=AppAppearance.theme(context)
        val prefs=context.getSharedPreferences("user_session",0);val previous=prefs.all.toMap()
        var activity:Activity?=null
        val self="00000000-0000-0000-0000-000000000001";val peer="00000000-0000-0000-0000-000000000002"
        val sent=java.util.concurrent.atomic.AtomicInteger(0)
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
        fun labels()=views(activity!!.window.decorView).filterIsInstance<TextView>()
        fun click(title:String){inst.runOnMainSync{labels().first{it.text.toString().startsWith(title)}.performClick()};inst.waitForIdleSync()}
        fun await(condition:()->Boolean){val end=SystemClock.elapsedRealtime()+10000;var matched=condition();while(!matched&&SystemClock.elapsedRealtime()<end){Thread.sleep(100);matched=condition()};assertTrue(matched)}
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
        fun screen(name:String){inst.waitForIdleSync();Thread.sleep(250);inst.uiAutomation.takeScreenshot()?.let{image->val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()};java.io.File(dir,name).outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
        fun has(value:String):Boolean {var found=false;inst.runOnMainSync{found=labels().any{it.text.toString().startsWith(value)}};return found}
        try {
            SessionManager(context).saveUser("chat-audio-test@example.invalid","synthetic-token",null)
            FriendsDependencies.gateway={_,_->object:FriendsGateway {
                override fun call(action:String,data:JSONObject):JSONObject {
                    if(action=="send")sent.incrementAndGet()
                    return JSONObject().put("ok",true).put("now_ms",System.currentTimeMillis())
                        .put("me",JSONObject().put("id",self).put("name","Motorista de exemplo").put("code","ABCDEF123456"))
                        .put("friends",JSONArray().put(JSONObject().put("id",peer).put("name","Amigo de exemplo").put("state","accepted").put("online",true).put("status","away")))
                        .put("messages",JSONArray().put(JSONObject().put("id","demo-text").put("sender",peer).put("body","Boa viagem! Esta conversa usa dados de exemplo.").put("kind","text").put("time_ms",System.currentTimeMillis())))
                }
            }}
            AppAppearance.setTheme(context,"gray")
            ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")).use{it.readBytes()}
            activity=inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync()
            await{has("CONVERSAR E VER MAPA")}
            Thread.sleep(150);click("CONVERSAR E VER MAPA")
            await{has("Boa viagem!")}
            assertTrue(has("GRAVAR ÁUDIO"))
            click("GRAVAR ÁUDIO");await{has("PARAR GRAVAÇÃO")}
            Thread.sleep(1000);click("PARAR GRAVAÇÃO");await{has("Áudio pronto")}
            assertEquals("No upload/send before explicit send",0,sent.get())
            screen("chat-audio-review.png")
            click("OUVIR")
            await{var playing=false;inst.runOnMainSync{val field=FriendsActivity::class.java.getDeclaredField("player").apply{isAccessible=true};playing=(field.get(activity) as? android.media.MediaPlayer)?.isPlaying==true};playing}
            click("DESCARTAR");
            inst.runOnMainSync{val field=FriendsActivity::class.java.getDeclaredField("player").apply{isAccessible=true};assertNull("Discard stops playback",field.get(activity))}
            assertTrue(has("GRAVAR ÁUDIO"));assertEquals(0,sent.get())
            click("GRAVAR ÁUDIO");await{has("PARAR GRAVAÇÃO")}
            // Finishing the visible activity cancels microphone capture, never sends.
            inst.runOnMainSync{activity!!.finish()};inst.waitForIdleSync()
            assertEquals(0,sent.get())
            await{activity!!.isDestroyed}
            shell("wm user-rotation lock 0")
            shell("wm size 1280x800");shell("wm density 160")
            var stableFrames=0
            await {
                val image=inst.uiAutomation.takeScreenshot()
                val ready=image!=null && image.width==1280 && image.height==800 && context.resources.displayMetrics.densityDpi==160
                image?.recycle()
                stableFrames=if(ready)stableFrames+1 else 0
                stableFrames>=3
            }
            activity=inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync()
            await{has("CONVERSAR E VER MAPA")};Thread.sleep(150);click("CONVERSAR E VER MAPA");await{has("Boa viagem!")}
            screen("friends-tablet-gray.png")
            val old=activity
            inst.runOnMainSync{AppAppearance.setTheme(context,"holiday");activity!!.recreate()}
            await{var next:Activity?=null;inst.runOnMainSync{next=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull{it is FriendsActivity && it!==old}};if(next!=null)activity=next;next!=null}
            await{has("Boa viagem!")};screen("friends-tablet-holiday.png")
            inst.runOnMainSync{activity!!.finish()}
            activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();screen("dashboard-holiday.png")
        } finally {
            inst.runOnMainSync{activity?.finish()};shell("wm size reset");shell("wm density reset");shell("wm user-rotation free");FriendsDependencies.gateway=oldFactory;AppAppearance.setTheme(context,previousTheme)
            val edit=prefs.edit().clear();for((key,value)in previous)when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value);is Float->edit.putFloat(key,value)};edit.commit()
        }
    }
}

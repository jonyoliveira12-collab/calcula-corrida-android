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
        fun await(condition:()->Boolean){val end=SystemClock.elapsedRealtime()+10000;while(!condition()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);assertTrue(condition())}
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
            click("DESCARTAR");assertTrue(has("GRAVAR ÁUDIO"));assertEquals(0,sent.get())
            click("GRAVAR ÁUDIO");await{has("PARAR GRAVAÇÃO")}
            // Finishing the visible activity cancels microphone capture, never sends.
            inst.runOnMainSync{activity!!.finish()};inst.waitForIdleSync()
            assertEquals(0,sent.get())
        } finally {
            inst.runOnMainSync{activity?.finish()};FriendsDependencies.gateway=oldFactory;AppAppearance.setTheme(context,previousTheme)
            val edit=prefs.edit().clear();for((key,value)in previous)when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value);is Float->edit.putFloat(key,value)};edit.commit()
        }
    }
}

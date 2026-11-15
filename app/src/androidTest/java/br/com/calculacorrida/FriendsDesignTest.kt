package br.com.calculacorrida

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
class FriendsDesignTest {
    @Test fun friendsReferenceLayoutHasWorkingConversationsAcrossThemesAndSizes() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val oldFactory=FriendsDependencies.gateway;val oldTheme=AppAppearance.theme(context)
        val prefs=context.getSharedPreferences("user_session",0);val oldSession=prefs.all.toMap()
        var activity:Activity?=null
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
        fun labels()=views(activity!!.window.decorView).filterIsInstance<TextView>()
        fun awaitText(value:String){val end=SystemClock.elapsedRealtime()+10000;var found=false;while(!found && SystemClock.elapsedRealtime()<end){inst.runOnMainSync{found=labels().any{it.text.toString().startsWith(value)}};Thread.sleep(100)};assertTrue(value,found)}
        fun screen(name:String){inst.waitForIdleSync();Thread.sleep(250);val image=inst.uiAutomation.takeScreenshot()!!;val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()};java.io.File(dir,name).outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        try {
            SessionManager(context).saveUser("design-preview@example.invalid","synthetic-token",null)
            FriendsDependencies.gateway={_,_->object:FriendsGateway{
                override fun call(action:String,data:JSONObject)=JSONObject().put("ok",true).put("now_ms",System.currentTimeMillis())
                    .put("me",JSONObject().put("id","preview-self").put("name","Motorista de exemplo").put("code","EXEMPLO12345"))
                    .put("friends",JSONArray().put(JSONObject().put("id","preview-friend").put("name","Amigo de exemplo").put("state","accepted").put("online",true).put("status","online")))
                    .put("messages",JSONArray().put(JSONObject().put("id","preview-message").put("sender","preview-friend").put("body","Boa viagem! Me avisa quando chegar.").put("kind","text").put("time_ms",System.currentTimeMillis())))
            }}
            shell("wm size 864x1920");shell("wm density 320")
            for(theme in listOf("dark","light")) {
                AppAppearance.setTheme(context,theme)
                activity=inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                awaitText("1 amigos")
                inst.runOnMainSync {
                    assertTrue(labels().any{it.text.toString()=="Meu nome e código"})
                    assertTrue(labels().any{it.text.toString()=="Adicionar amigo por código"})
                    assertFalse("Opening the redesigned screen must not enable sharing",FriendsService.sharing)
                }
                screen("friends-design-phone-$theme.png")
                inst.runOnMainSync{activity!!.finish()};inst.waitForIdleSync()
            }
            shell("wm size 1280x800");shell("wm density 160");AppAppearance.setTheme(context,"dark")
            activity=inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitText("CONVERSAR E VER MAPA")
            inst.runOnMainSync{labels().first{it.text.toString().startsWith("CONVERSAR E VER MAPA")}.performClick()}
            awaitText("Boa viagem!");screen("friends-design-tablet-chat.png")
        } finally {
            inst.runOnMainSync{activity?.finish()};shell("wm size reset");shell("wm density reset")
            FriendsDependencies.gateway=oldFactory;AppAppearance.setTheme(context,oldTheme)
            val edit=prefs.edit().clear();for((key,value)in oldSession)when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value);is Float->edit.putFloat(key,value)};edit.commit()
        }
    }
}

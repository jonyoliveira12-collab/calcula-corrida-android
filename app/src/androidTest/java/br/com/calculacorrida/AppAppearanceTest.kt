package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppAppearanceTest {
    @Test fun themeChoicePersistsAcrossScreensWithoutChangingReaderOrStartingLocation() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val originalTheme=AppAppearance.isLight(context)
        val prefs=context.getSharedPreferences("user_session",0);val originalSession=prefs.all.toMap()
        val reader=context.getSharedPreferences("settings",0)
        val cardTheme=reader.getString("card_theme",null);val scale=reader.getInt("overlay_scale",100)
        val originalFactory=FriendsDependencies.gateway
        var activity:Activity?=null
        fun descendants(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{descendants(view.getChildAt(it))}else emptyList()
        fun texts(view:View)=descendants(view).filterIsInstance<TextView>()
        fun screen(name:String) {
            inst.waitForIdleSync()
            val image=inst.uiAutomation.takeScreenshot() ?: return
            val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()}
            java.io.File(dir,name).outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
        fun await(condition:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+10000
            while(!condition()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100)
            assertTrue("Screen state becomes available",condition())
        }
        try {
            AppAppearance.setLight(context,false)
            SessionManager(context).saveUser("appearance-test@example.invalid","synthetic-token",null)
            FriendsDependencies.gateway={_,_->object:FriendsGateway {
                override fun call(action:String,data:JSONObject):JSONObject = JSONObject().put("ok",true).put("now_ms",System.currentTimeMillis())
                    .put("me",JSONObject().put("id","00000000-0000-0000-0000-000000000001").put("name","Motorista de exemplo").put("code","ABCDEF123456"))
                    .put("friends",JSONArray().put(JSONObject().put("id","00000000-0000-0000-0000-000000000002").put("name","Carlos • Exemplo").put("state","accepted").put("online",true).put("unread",2))
                        .put(JSONObject().put("id","00000000-0000-0000-0000-000000000003").put("name","Bruno • Exemplo").put("state","accepted").put("online",false)))
            }}
            activity=inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { var found=false;inst.runOnMainSync {found=texts(activity!!.window.decorView).any{it.text.toString()=="Carlos • Exemplo"}};found }
            screen("friends-dark.png")
            inst.runOnMainSync {texts(activity!!.window.decorView).first{it.text.toString().contains("TEMA")}.performClick()}
            inst.waitForIdleSync()
            val choice=inst.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Claro").firstOrNull{it.text?.toString()=="Claro"}
            assertNotNull("Light theme is a real selectable option",choice)
            assertTrue(choice!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val previousActivity=activity
            await { var next:Activity?=null;inst.runOnMainSync {next=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull{it is FriendsActivity && it!==previousActivity}};if(next!=null)activity=next;next!=null }
            assertTrue(AppAppearance.isLight(context))
            inst.waitForIdleSync();screen("friends-light.png")
            assertFalse(FriendsService.running);assertFalse(FriendsService.sharing)
            inst.runOnMainSync {activity!!.finish()}
            activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            inst.waitForIdleSync()
            inst.runOnMainSync {
                val settings=texts(activity!!.window.decorView).first{it.text.toString()=="Configurações"}
                (settings.parent as View).performClick()
            }
            inst.waitForIdleSync()
            inst.runOnMainSync {
                val labels=texts(activity!!.window.decorView)
                assertTrue(labels.any{it.text.toString()=="Um tema para cada momento"})
                assertTrue(labels.any{it.text.toString().contains("Mensal  •  Trimestral  •  Anual")})
                assertTrue(labels.any{it.text.toString().contains("EM BREVE")})
                assertTrue("Light text is dark enough on white panels",labels.first{it.text.toString()=="Do seu jeito"}.currentTextColor==AppAppearance.palette(context).text)
            }
            screen("settings-light.png")
            assertEquals(cardTheme,reader.getString("card_theme",null));assertEquals(scale,reader.getInt("overlay_scale",100))
            inst.runOnMainSync {activity!!.finish()}
            AppAppearance.setLight(context,false)
            activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            inst.waitForIdleSync()
            inst.runOnMainSync {(texts(activity!!.window.decorView).first{it.text.toString()=="Configurações"}.parent as View).performClick()}
            inst.waitForIdleSync();screen("settings-dark.png")
        } finally {
            inst.runOnMainSync {activity?.finish()}
            AppAppearance.setLight(context,originalTheme);FriendsDependencies.gateway=originalFactory
            val edit=prefs.edit().clear()
            for((key,value)in originalSession)when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value);is Float->edit.putFloat(key,value)}
            edit.commit()
        }
    }
}

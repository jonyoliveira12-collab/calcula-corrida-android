package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.os.ParcelFileDescriptor
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

/** Preview only: example offers stay in the disposable emulator and all preferences are restored. */
@RunWith(AndroidJUnit4::class)
class UnifiedDesignTest {
    @Test fun allPrimaryScreensKeepTheirControlsAndReadableThemes() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val prefNames=listOf("user_session","settings","journey","appearance","login_preferences")
        val backup=prefNames.associateWith{context.getSharedPreferences(it,0).all.toMap()}
        var activity:Activity?=null
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
        fun labels()=views(activity!!.window.decorView).filterIsInstance<TextView>()
        fun launch(type:Class<out Activity>){activity=inst.startActivitySync(Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync()}
        fun close(){inst.runOnMainSync{activity?.finish()};inst.waitForIdleSync();activity=null}
        fun tab(name:String){inst.runOnMainSync{views(activity!!.window.decorView).first{it.contentDescription?.toString()==name && it.isClickable}.performClick()};inst.waitForIdleSync()}
        fun screen(name:String){inst.waitForIdleSync();Thread.sleep(350);val image=inst.uiAutomation.takeScreenshot()!!;val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()};java.io.File(dir,name).outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        try {
            context.getSharedPreferences("settings",0).edit().putBoolean("notification_permission_asked",true).putBoolean("card_neon_animated",false).commit()
            val now=System.currentTimeMillis()
            val examples=JSONArray()
            for((index,fare)in listOf(32.4,18.4,10.0).withIndex()) {
                val km=if(index==0)12.0 else 12.6;val minutes=if(index==0)24.0 else 28.0
                examples.put(JSONObject().put("id","example-$index").put("timestamp",now-index*60000L).put("app",if(index==1)"99" else "Uber")
                    .put("fare",fare).put("totalKm",km).put("minutes",minutes).put("perKm",fare/km).put("perHour",fare*60/minutes)
                    .put("pickupMinutes",4.0).put("passengerRating","4,96").put("classification",if(index==0)"GOOD" else "BAD"))
            }
            context.getSharedPreferences("journey",0).edit().putString("offers",examples.toString()).commit()
            shell("wm size 864x1920");shell("wm density 320")
            for(theme in listOf("light","dark")) {
                AppAppearance.setTheme(context,theme);SessionManager(context).saveUser("motorista.exemplo@teste.invalid","synthetic-token",null)
                launch(MainActivity::class.java)
                inst.runOnMainSync{assertTrue(labels().any{it.text.toString()=="R$ 32,40"})}
                screen("app-home-$theme.png")
                tab("Configurações")
                inst.runOnMainSync{assertTrue(labels().any{it.text.toString()=="VERIFICAR ATUALIZAÇÃO" && it.isClickable});assertTrue(labels().any{it.text.toString()=="Um tema para cada momento"})}
                screen("app-settings-$theme.png")
                tab("Metas");screen("app-limits-$theme.png")
                tab("Histórico");screen("app-history-$theme.png");close()
                launch(CardStyleActivity::class.java);screen("app-tag-$theme.png");close()
                launch(HelpSupportActivity::class.java);screen("app-support-$theme.png");close()
                // No auth request: clear only the disposable local test session to show the entry screen.
                context.getSharedPreferences("user_session",0).edit().clear().commit()
                launch(LoginActivity::class.java);screen("app-login-$theme.png");close()
            }
            shell("wm size 1280x800");shell("wm density 160")
            for(theme in listOf("light","dark")) {
                AppAppearance.setTheme(context,theme);SessionManager(context).saveUser("motorista.exemplo@teste.invalid","synthetic-token",null)
                launch(MainActivity::class.java)
                screen("app-tablet-$theme.png")
                inst.runOnMainSync {
                    for(label in listOf("AJUSTAR LIMITES", "✓ BOA CORRIDA")) {
                        val view=labels().first{it.text.toString().startsWith(label)}
                        val rect=android.graphics.Rect()
                        val visible=view.getGlobalVisibleRect(rect)
                        assertTrue("Tablet keeps $label fully visible above navigation (${rect.height()}/${view.height})",visible && rect.height()==view.height)
                    }
                }
                close()
            }
        } finally {
            close();shell("wm size reset");shell("wm density reset")
            for((name,original)in backup){val edit=context.getSharedPreferences(name,0).edit().clear();for((k,v)in original)when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Long->edit.putLong(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v)};edit.commit()}
        }
    }
}

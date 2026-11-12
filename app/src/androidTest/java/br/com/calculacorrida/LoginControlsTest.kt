package br.com.calculacorrida

import android.content.Intent
import android.graphics.Color
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginControlsTest {
    @Test fun goldLoginFlamesAnimateOutsideTheForm() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val hadAnimation=prefs.contains("card_neon_animated")
        val originalAnimation=prefs.getBoolean("card_neon_animated",true)
        var activity:android.app.Activity?=null
        try {
            prefs.edit().putBoolean("card_neon_animated",true).commit()
            SessionManager(context).logout()
            activity=instrumentation.startActivitySync(Intent(context,LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.waitForIdleSync()
            var frame:FlameBorderLayout?=null
            instrumentation.runOnMainSync {
                frame=activity!!.findViewById(R.id.loginFlameFrame)
                assertTrue(frame!!.isAttachedToWindow && frame!!.width>0 && frame!!.height>0)
                val form=frame!!.getChildAt(0)
                assertTrue("Flames have their own margin beside the form",form.left>0 && form.right<frame!!.width)
                assertNotNull(activity!!.findViewById<EditText>(R.id.emailInput))
                assertNotNull(activity!!.findViewById<EditText>(R.id.passwordInput))
            }
            val shot=instrumentation.uiAutomation.takeScreenshot()
            if(shot!=null) {
                val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()}
                java.io.File(dir,"login-fire.png").outputStream().use{shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                shot.recycle()
            }
            fun snapshot():IntArray {
                var data=IntArray(0)
                instrumentation.runOnMainSync {
                    val view=frame!!
                    val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(Color.rgb(12,12,14));view.draw(canvas)
                    data=IntArray(bitmap.width*bitmap.height)
                    bitmap.getPixels(data,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    bitmap.recycle()
                }
                return data
            }
            val first=snapshot();Thread.sleep(480);val second=snapshot()
            assertTrue("Login flames visibly change their shape",first.indices.count{first[it]!=second[it]}>100)
            instrumentation.runOnMainSync {
                val email=activity!!.findViewById<EditText>(R.id.emailInput)
                email.setText("motorista@example.invalid")
                assertEquals("motorista@example.invalid",email.text.toString())
                assertTrue(activity!!.findViewById<Button>(R.id.loginButton).isClickable)
            }
        } finally {
            activity?.let{instrumentation.runOnMainSync{it.finish()}}
            val edit=prefs.edit()
            if(hadAnimation)edit.putBoolean("card_neon_animated",originalAnimation)else edit.remove("card_neon_animated")
            edit.commit()
        }
    }

    @Test fun passwordVisibilityAndRegistrationContrast() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        SessionManager(context).logout()
        val activity=instrumentation.startActivitySync(Intent(context,LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.runOnMainSync {
            val version=activity.findViewById<android.widget.TextView>(R.id.appVersionLabel)
            assertEquals("Versão ${context.packageManager.getPackageInfo(context.packageName,0).versionName}",version.text.toString())
            val password=activity.findViewById<EditText>(R.id.passwordInput)
            val eye=activity.findViewById<ImageButton>(R.id.togglePasswordButton)
            password.setText("senha-de-teste")
            assertTrue(password.transformationMethod is PasswordTransformationMethod)
            eye.performClick()
            assertNull(password.transformationMethod)
            assertEquals("senha-de-teste",password.text.toString())
            assertEquals("Esconder senha",eye.contentDescription.toString())
            eye.performClick()
            assertTrue(password.transformationMethod is PasswordTransformationMethod)
            val register=activity.findViewById<Button>(R.id.registerButton)
            assertEquals(Color.rgb(255,224,125),register.currentTextColor)
            assertEquals(1f,register.alpha)
            assertEquals("CRIAR CONTA",register.text.toString())
            activity.finish()
        }
    }
}

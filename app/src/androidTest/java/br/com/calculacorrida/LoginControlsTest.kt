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

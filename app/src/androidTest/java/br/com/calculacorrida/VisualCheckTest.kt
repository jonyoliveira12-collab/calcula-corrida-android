package br.com.calculacorrida

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class VisualCheckTest {
    @Test fun captureLoginAndDashboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val size = if (context.resources.configuration.screenWidthDp >= 600) "tablet" else "phone"
        val session = SessionManager(context)
        session.logout()
        fun launch(type: Class<*>) {
            instrumentation.startActivitySync(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
        }
        fun capture(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(bitmap)
            val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            File(dir, "$name-$size.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        launch(LoginActivity::class.java)
        capture("login")
        // Local UI fixture only; no real account or backend request is created.
        try {
            assertTrue("Emulator must have validated internet", NetworkGate.isOnline(context))
            session.saveUser("ui-test@example.invalid")
            launch(MainActivity::class.java)
            capture("dashboard")
        } finally {
            session.logout()
        }
    }
}

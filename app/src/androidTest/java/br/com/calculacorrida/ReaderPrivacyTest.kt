package br.com.calculacorrida

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a synthetic secure window on a disposable emulator; never the real banking app. */
@RunWith(AndroidJUnit4::class)
class ReaderPrivacyTest {
    @Test fun pausesForVisibleBankEvenWithFloatingDriverOfferAndResumesAfterwards() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        fun hasCc(): Boolean = ui.windows.any { window ->
            val root = window.root ?: return@any false
            try { root.findAccessibilityNodeInfosByText("CC").any { it.text?.toString() == "CC" } }
            finally { root.recycle() }
        }
        fun await(label: String, timeout: Long = 15000, predicate: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + timeout
            while (!predicate() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            assertTrue(label, predicate())
        }
        val settings = context.getSharedPreferences("settings", 0)
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        var focusWindow: android.widget.TextView? = null
        val oldServices = shell("settings get secure enabled_accessibility_services")
        val oldEnabled = shell("settings get secure accessibility_enabled")
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            shell("appops set com.ubercab.driver SYSTEM_ALERT_WINDOW allow")
            settings.edit().putBoolean("copilot_enabled", true).putBoolean("traffic_enabled", false)
                .putBoolean("reader_paused", false).putBoolean("reader_disclosure_accepted", true)
                .putBoolean("reader_privacy_protected", false).commit()
            shell("settings put secure enabled_accessibility_services ${context.packageName}/br.com.calculacorrida.OfferAccessibilityService")
            shell("settings put secure accessibility_enabled 1")
            await("OCR model initialized", 30000) { settings.getBoolean("reader_model_ready", false) }
            val beforeOffer = System.currentTimeMillis()
            shell("am start -W -n com.ubercab.driver/br.com.calculacorrida.fixture.OfferFixtureActivity --es layout floating")
            await("Floating offer read before bank", 25000) { settings.getLong("reader_last_detection", 0) >= beforeOffer }
            await("CC visible before bank") { hasCc() }

            shell("am start -W -n com.nu.production/br.com.calculacorrida.fixture.OfferFixtureActivity")
            await("Sensitive window identified") { settings.getBoolean("reader_privacy_protected", false) }
            await("CC removed while bank visible") { !hasCc() }
            instrumentation.runOnMainSync {
                focusWindow = android.widget.TextView(context).apply {
                    text = "Foreground controls"; isFocusableInTouchMode = true
                    setBackgroundColor(android.graphics.Color.BLUE)
                }
                val params = android.view.WindowManager.LayoutParams(180, 90,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    android.graphics.PixelFormat.TRANSLUCENT)
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.RIGHT
                wm.addView(focusWindow, params)
                focusWindow!!.requestFocus()
            }
            await("Bank window remains visible without focus") {
                ui.windows.any { window ->
                    val root = window.root ?: return@any false
                    try { root.packageName?.toString() == "com.nu.production" && !window.isFocused }
                    finally { root.recycle() }
                }
            }
            val lastCapture = settings.getLong("reader_last_capture", 0)
            val lastDetection = settings.getLong("reader_last_detection", 0)
            Thread.sleep(1600)
            assertTrue("Protection remains active when bank loses focus", settings.getBoolean("reader_privacy_protected", false))
            assertFalse("CC remains hidden when bank loses focus", hasCc())
            assertEquals("No driver screenshot while bank is visible", lastCapture, settings.getLong("reader_last_capture", 0))
            assertEquals("No driver offer processed while bank is visible", lastDetection, settings.getLong("reader_last_detection", 0))

            shell("am force-stop com.nu.production")
            await("Protection lifted after bank closes") { !settings.getBoolean("reader_privacy_protected", true) }
            await("CC returns after bank closes") { hasCc() }
            await("Offer reading resumes", 25000) { settings.getLong("reader_last_detection", 0) > lastDetection }
        } finally {
            instrumentation.runOnMainSync { focusWindow?.let { wm.removeView(it) } }
            listOf("com.nu.production", "com.ubercab.driver", "br.com.calculacorrida.fixture.backdrop").forEach { shell("am force-stop $it") }
            settings.edit().putBoolean("copilot_enabled", false).putBoolean("traffic_enabled", false).commit()
            OfferAccessibilityService.stopReading()
            if (oldServices == "null" || oldServices.isBlank()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services '$oldServices'")
            if (oldEnabled == "null" || oldEnabled.isBlank()) shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $oldEnabled")
        }
    }
}

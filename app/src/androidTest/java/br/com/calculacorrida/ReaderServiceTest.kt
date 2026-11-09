package br.com.calculacorrida

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderServiceTest {
    @Test fun ccAppearsAndLeavesUnrelatedApps() {
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
        fun await(expected: Boolean) {
            repeat(30) { if (hasCc() == expected) return; Thread.sleep(250) }
            assertEquals("CC visibility", expected, hasCc())
        }
        val oldServices = shell("settings get secure enabled_accessibility_services")
        val oldEnabled = shell("settings get secure accessibility_enabled")
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            context.getSharedPreferences("settings",0).edit().putBoolean("copilot_enabled",true).commit()
            SessionManager(context).saveUser("reader-test@example.invalid")
            shell("settings put secure enabled_accessibility_services ${context.packageName}/br.com.calculacorrida.OfferAccessibilityService")
            shell("settings put secure accessibility_enabled 1")
            instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.waitForIdleSync()
            await(true)
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await(false)
        } finally {
            context.getSharedPreferences("settings",0).edit().putBoolean("copilot_enabled",false).commit()
            OfferAccessibilityService.stopReading()
            SessionManager(context).logout()
            if (oldServices == "null" || oldServices.isBlank()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services '$oldServices'")
            if (oldEnabled == "null" || oldEnabled.isBlank()) shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $oldEnabled")
        }
    }
}

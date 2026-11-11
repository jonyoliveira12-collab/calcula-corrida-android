package br.com.calculacorrida

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderServiceTest {
    @Test fun ccPersistsInBackgroundAndStopsWhenDisabled() {
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
            context.getSharedPreferences("settings",0).edit().putBoolean("copilot_enabled",true).putBoolean("reader_disclosure_accepted",true).commit()
            SessionManager(context).saveUser("reader-test@example.invalid")
            shell("settings put secure enabled_accessibility_services ${context.packageName}/br.com.calculacorrida.OfferAccessibilityService")
            shell("settings put secure accessibility_enabled 1")
            instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.waitForIdleSync()
            await(true)
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await(true)
            context.getSharedPreferences("settings",0).edit().putBoolean("copilot_enabled",false).putBoolean("traffic_enabled",false).commit()
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

    @Test fun userCanDisableForBankAndRestoreReaderWithoutLosingChoices() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        fun await(label: String, check: () -> Boolean) {
            repeat(100) { if (check()) return; Thread.sleep(100) }
            assertTrue(label, check())
        }
        fun findText(view: View, label: String): TextView? {
            if (view is TextView && view.text.toString() == label) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                findText(view.getChildAt(i), label)?.let { return it }
            }
            return null
        }
        fun hasCc(): Boolean = ui.windows.any { window ->
            val root = window.root ?: return@any false
            try { root.findAccessibilityNodeInfosByText("CC").any { it.text?.toString() == "CC" } }
            finally { root.recycle() }
        }
        val oldServices = shell("settings get secure enabled_accessibility_services")
        val oldEnabled = shell("settings get secure accessibility_enabled")
        val service = "${context.packageName}/br.com.calculacorrida.OfferAccessibilityService"
        val settings = context.getSharedPreferences("settings", 0)
        var activity: android.app.Activity? = null
        try {
            shell("settings delete secure enabled_accessibility_services")
            shell("settings put secure accessibility_enabled 0")
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            settings.edit().putBoolean("copilot_enabled", true).putBoolean("traffic_enabled", true)
                .putBoolean("reader_paused", false).putBoolean("reader_disclosure_accepted", true).commit()
            SessionManager(context).saveUser("bank-mode-test@example.invalid")
            shell("settings put secure enabled_accessibility_services $service")
            shell("settings put secure accessibility_enabled 1")
            await("CC starts") { hasCc() }
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.runOnMainSync {
                val button = findText(activity!!.window.decorView, "DESLIGAR PARA USAR BANCO")
                assertNotNull("Bank control is available", button)
                button!!.performClick()
            }
            await("Android accessibility service turned off") { !shell("settings get secure enabled_accessibility_services").contains(context.packageName) }
            await("CC removed") { !hasCc() }
            await("Reactivation control is available") {
                var found = false
                instrumentation.runOnMainSync { found = findText(activity!!.window.decorView, "REATIVAR ACESSIBILIDADE") != null }
                found
            }
            assertTrue("Copilot choice preserved", settings.getBoolean("copilot_enabled", false))
            assertTrue("Traffic-light choice preserved", settings.getBoolean("traffic_enabled", false))

            // Simulates the user explicitly enabling the service again in Android settings.
            shell("settings put secure enabled_accessibility_services $service")
            shell("settings put secure accessibility_enabled 1")
            await("CC returns after user enables accessibility") { hasCc() }
            assertTrue("Copilot still configured", settings.getBoolean("copilot_enabled", false))
            assertTrue("Traffic-light setting still configured", settings.getBoolean("traffic_enabled", false))
        } finally {
            activity?.let { screen -> instrumentation.runOnMainSync { screen.finish() } }
            settings.edit().putBoolean("copilot_enabled", false).putBoolean("traffic_enabled", false).commit()
            OfferAccessibilityService.stopReading()
            SessionManager(context).logout()
            if (oldServices == "null" || oldServices.isBlank()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services '$oldServices'")
            if (oldEnabled == "null" || oldEnabled.isBlank()) shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $oldEnabled")
        }
    }
}

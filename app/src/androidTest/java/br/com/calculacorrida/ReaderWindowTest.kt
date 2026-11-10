package br.com.calculacorrida

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderWindowTest {
    @Test fun landscapePixelsAreRecognizedWithoutWaitingForAi() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val ui=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        ui.serviceInfo=ui.serviceInfo.apply {flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(cmd)).bufferedReader().use{it.readText().trim()}
        val oldServices=shell("settings get secure enabled_accessibility_services")
        val oldEnabled=shell("settings get secure accessibility_enabled")
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            context.getSharedPreferences("settings",0).edit().putBoolean("copilot_enabled",true).putBoolean("reader_disclosure_accepted",true).commit()
            shell("settings put secure enabled_accessibility_services ${context.packageName}/br.com.calculacorrida.OfferAccessibilityService")
            shell("settings put secure accessibility_enabled 1")
            shell("wm user-rotation lock 1")
            shell("am start -W -n com.ubercab.driver/br.com.calculacorrida.fixture.OfferFixtureActivity")
            val start=android.os.SystemClock.elapsedRealtime()
            var found=false
            for(attempt in 0..59){
                found=ui.windows.any { window-> val root=window.root?:return@any false
                    try {root.findAccessibilityNodeInfosByText("55,76").isNotEmpty()}finally{root.recycle()}}
                if(found)break
                Thread.sleep(250)
            }
            assertTrue("OCR landscape: "+context.getSharedPreferences("settings",0).getString("reader_status",""),found)
            assertTrue(android.os.SystemClock.elapsedRealtime()-start<15000)
        } finally {
            shell("am force-stop com.ubercab.driver")
            shell("wm user-rotation free")
            OfferAccessibilityService.stopReading()
            if(oldServices=="null"||oldServices.isBlank())shell("settings delete secure enabled_accessibility_services")else shell("settings put secure enabled_accessibility_services '$oldServices'")
            if(oldEnabled=="null"||oldEnabled.isBlank())shell("settings delete secure accessibility_enabled")else shell("settings put secure accessibility_enabled $oldEnabled")
        }
    }
}

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
        var focusWindow:android.widget.TextView?=null
        val wm=context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            context.getSharedPreferences("settings",0).edit().putBoolean("copilot_enabled",true).putBoolean("reader_disclosure_accepted",true).commit()
            shell("settings put secure enabled_accessibility_services ${context.packageName}/br.com.calculacorrida.OfferAccessibilityService")
            shell("settings put secure accessibility_enabled 1")
            shell("wm user-rotation lock 1")
            shell("am start -W -n com.ubercab.driver/br.com.calculacorrida.fixture.OfferFixtureActivity")
            instrumentation.runOnMainSync {
                focusWindow=android.widget.TextView(context).apply {text="Foreground controls";setBackgroundColor(android.graphics.Color.BLUE);isFocusableInTouchMode=true}
                val params=android.view.WindowManager.LayoutParams(180,90,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT)
                params.gravity=android.view.Gravity.TOP or android.view.Gravity.RIGHT
                wm.addView(focusWindow,params);focusWindow!!.requestFocus()
            }
            Thread.sleep(500)
            val driverWindow=ui.windows.first {w->val root=w.root;try{root?.packageName?.toString()=="com.ubercab.driver"}finally{root?.recycle()}}
            assertFalse("Visible driver window has lost input focus",driverWindow.isFocused)
            // Reset the card after focus moves: an earlier focused-window read cannot satisfy the test.
            context.getSharedPreferences("settings",0).edit().putBoolean("card_details",true).commit()
            Thread.sleep(500)
            val start=android.os.SystemClock.elapsedRealtime()
            var found=false
            for(attempt in 0..59){
                found=ui.windows.any { window-> val root=window.root?:return@any false
                    try {root.findAccessibilityNodeInfosByText("55,76").isNotEmpty()}finally{root.recycle()}}
                if(found)break
                Thread.sleep(250)
            }
            assertTrue("OCR landscape: "+context.getSharedPreferences("settings",0).getString("reader_status",""),found)
            val elapsed=android.os.SystemClock.elapsedRealtime()-start
            println("LANDSCAPE_UNFOCUSED_OCR_MS=$elapsed")
            assertTrue(elapsed<15000)
        } finally {
            instrumentation.runOnMainSync { focusWindow?.let {wm.removeView(it)} }
            context.getSharedPreferences("settings",0).edit().putBoolean("card_details",false).commit()
            shell("am force-stop com.ubercab.driver")
            shell("wm user-rotation free")
            OfferAccessibilityService.stopReading()
            if(oldServices=="null"||oldServices.isBlank())shell("settings delete secure enabled_accessibility_services")else shell("settings put secure enabled_accessibility_services '$oldServices'")
            if(oldEnabled=="null"||oldEnabled.isBlank())shell("settings delete secure accessibility_enabled")else shell("settings put secure accessibility_enabled $oldEnabled")
        }
    }
}

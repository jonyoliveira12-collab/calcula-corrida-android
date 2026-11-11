package br.com.calculacorrida

import android.app.UiAutomation
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the pixels shown by a real floating window, with another app in front. */
@RunWith(AndroidJUnit4::class)
class OfferOverlayAnimationTest {
    @Test fun floatingFlamesMoveWithSystemAnimationsOffAndCanBeStopped() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val ui=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use{it.readText().trim()}
        val oldScale=shell("settings get global animator_duration_scale")
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.all
        val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
        var overlay:OfferTagView?=null
        try {
            shell("settings put global animator_duration_scale 0")
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            prefs.edit().putBoolean("card_neon_flames",true).putBoolean("card_neon_animated",true)
                .putBoolean("card_banner",true).putString("card_banner_position","above")
                .putString("card_theme","light").putBoolean("card_details",false)
                .putBoolean("card_ai",false).putBoolean("card_compact_reason",false).commit()
            shell("am start -W -n br.com.calculacorrida.fixture.backdrop/br.com.calculacorrida.fixture.OfferFixtureActivity")
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                overlay=OfferTagView(context).apply {
                    setOffer("RECUSAR",20.0,10.0,1.0,30.0,"4.70",Color.RED,"UBER",10.0)
                }
                val params=WindowManager.LayoutParams(dp(310),WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.LEFT;x=dp(10);y=dp(80)}
                wm.addView(overlay,params)
            }
            Thread.sleep(600)
            var bounds=android.graphics.Rect()
            instrumentation.runOnMainSync {
                val view=overlay!!
                assertTrue("Floating card is attached",view.isAttachedToWindow && view.isShown)
                val body=view.getChildAt(1)
                val at=IntArray(2);view.getLocationOnScreen(at)
                // Only the opaque lower flame band: no status bar, map, or unrelated app content.
                bounds=android.graphics.Rect(at[0]+body.left+dp(10),at[1]+body.bottom-dp(22),
                    at[0]+body.right-dp(10),at[1]+body.bottom-dp(3))
            }
            val flameFrame=OfferTagView::class.java.getDeclaredField("flameFrame").apply{isAccessible=true}
            fun frame():Int {
                var result=0
                instrumentation.runOnMainSync {result=flameFrame.getInt(overlay)}
                return result
            }
            fun pixels(name:String?=null):IntArray {
                val shot=ui.takeScreenshot() ?: throw AssertionError("Screen capture unavailable")
                try {
                    assertTrue("Flame band is on screen",bounds.left>=0 && bounds.top>=0 && bounds.right<=shot.width && bounds.bottom<=shot.height)
                    if(name!=null) {
                        val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()}
                        java.io.File(dir,name).outputStream().use{shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    }
                    val data=IntArray(bounds.width()*bounds.height())
                    shot.getPixels(data,0,bounds.width(),bounds.left,bounds.top,bounds.width(),bounds.height())
                    return data
                } finally {shot.recycle()}
            }
            fun different(a:IntArray,b:IntArray)=a.indices.count{a[it]!=b[it]}
            val frames=mutableListOf<IntArray>()
            val phases=mutableListOf<Int>()
            repeat(5){index->frames.add(pixels(if(index==0)"overlay-diagnostic.png" else null));phases.add(frame());Thread.sleep(250)}
            val redPixels=frames.first().count{Color.red(it)>160 && Color.red(it)>Color.green(it)*1.35 && Color.red(it)>Color.blue(it)*1.35}
            var visibility=""
            instrumentation.runOnMainSync {visibility="attached=${overlay!!.isAttachedToWindow}, shown=${overlay!!.isShown}, window=${overlay!!.windowVisibility}"}
            val interactive=(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
            val movingPairs=frames.zipWithNext().count{(a,b)->different(a,b)>50}
            assertTrue("The captured band contains the red card (red pixels: $redPixels, bounds=$bounds)",redPixels>50)
            assertTrue("Visible floating flames keep moving over another app (changed pairs: $movingPairs, phases=$phases, $visibility, interactive=$interactive)",movingPairs>=3)

            prefs.edit().putBoolean("card_neon_animated",false).commit()
            instrumentation.runOnMainSync {overlay!!.setOffer("RECUSAR",20.0,10.0,1.0,30.0,"4.70",Color.RED,"UBER",10.0)}
            Thread.sleep(250)
            val still=pixels();Thread.sleep(450)
            assertTrue("The in-app option stops floating flames",still.contentEquals(pixels()))
        } finally {
            overlay?.let{view->instrumentation.runOnMainSync{wm.removeView(view)}}
            shell("am force-stop br.com.calculacorrida.fixture.backdrop")
            if(oldScale.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))shell("settings put global animator_duration_scale $oldScale")
            else shell("settings delete global animator_duration_scale")
            val edit=prefs.edit().clear()
            original.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v);is Long->edit.putLong(k,v)}}
            edit.commit()
        }
    }
}

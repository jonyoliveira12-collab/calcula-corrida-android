package br.com.calculacorrida

import android.accessibilityservice.AccessibilityService
import java.util.concurrent.Executor

/** Injectable transport for native tests that reproduce missing Android callbacks. */
internal object OfferScreenshotTransport {
    var windowOverride:((AccessibilityService,String,Int,Executor,AccessibilityService.TakeScreenshotCallback)->Unit)?=null
    fun window(service:AccessibilityService,pkg:String,id:Int,executor:Executor,callback:AccessibilityService.TakeScreenshotCallback) {
        val override=windowOverride
        if(override!=null)override(service,pkg,id,executor,callback)
        else if(android.os.Build.VERSION.SDK_INT>=34)service.takeScreenshotOfWindow(id,executor,callback)
    }
}

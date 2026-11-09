package br.com.calculacorrida

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualOfferTest {
    @Test fun manualCalculationWithoutSensitivePermissions() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        val permissions=info.requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(permissions.contains("android.permission.READ_MEDIA_IMAGES"))
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(info.services.orEmpty().any { it.permission=="android.permission.BIND_ACCESSIBILITY_SERVICE" })
        assertEquals("JHONDRIVER",context.applicationInfo.loadLabel(context.packageManager).toString())
        val activity=instrumentation.startActivitySync(Intent(context,ManualOfferActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            activity.findViewById<EditText>(R.id.manualFare).setText("18,40")
            activity.findViewById<EditText>(R.id.manualPickup).setText("1,40")
            activity.findViewById<EditText>(R.id.manualTrip).setText("11,20")
            activity.findViewById<EditText>(R.id.manualMinutes).setText("28")
            activity.findViewById<android.view.View>(R.id.manualCalculate).performClick()
            val text=activity.findViewById<TextView>(R.id.manualResult).text.toString()
            assertTrue(text,text.contains("R$ 1,46/km"))
            assertTrue(text,text.contains("R$ 39,43/hora"))
            assertEquals("Manual",JourneyStore.getRecords(context).first().optString("app"))
            activity.findViewById<EditText>(R.id.manualMinutes).setText("0")
            activity.findViewById<android.view.View>(R.id.manualCalculate).performClick()
            assertTrue(activity.findViewById<TextView>(R.id.manualResult).text.toString().contains("maiores que zero"))
            JourneyStore.clear(context)
            activity.finish()
        }
    }
}

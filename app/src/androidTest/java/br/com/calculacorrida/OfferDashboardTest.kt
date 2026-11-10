package br.com.calculacorrida

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfferDashboardTest {
    @Test fun dashboardRefreshesWhenTheBackgroundReaderRecordsANewOffer() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("journey",0)
        val original=prefs.getString("offers",null)
        SessionManager(context).saveUser("dashboard-test@example.invalid")
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun text(v:View):List<String> = if(v is ViewGroup)(0 until v.childCount).flatMap{text(v.getChildAt(it))} else if(v is TextView)listOf(v.text.toString())else emptyList()
        fun awaitText(expected:String) {
            repeat(50) {
                var found=false
                instrumentation.runOnMainSync {found=text(activity.window.decorView).contains(expected)}
                if(found)return
                Thread.sleep(100)
            }
            fail("Dashboard did not update to $expected")
        }
        try {
            for((fare,pickup,rate) in listOf(Triple(12.77,5.0,"R$ 40,33"),Triple(12.02,4.4,"R$ 37,96"))) {
                val offer=OfferTextParser.ParsedOffer(fare,pickup,7.0,19.0,"4.99",pickupMinutes=8.0)
                val result=TripCalculator.calculate(fare,pickup,7.0,19.0)!!
                JourneyStore.record(context,offer,result,"com.ubercab.driver")
                awaitText(rate)
                awaitText(if(fare==12.77)"R$ 12,77" else "R$ 12,02")
                awaitText("19,00 min")
            }
        } finally {
            instrumentation.runOnMainSync {activity.finish()}
            prefs.edit().apply{if(original==null)remove("offers")else putString("offers",original)}.commit()
            SessionManager(context).logout()
        }
    }
}

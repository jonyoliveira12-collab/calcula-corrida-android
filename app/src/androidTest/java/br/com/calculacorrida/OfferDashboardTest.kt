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
    @Test fun nameEditedInSettingsIsSavedAndRenderedOnTheCard() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val previous=prefs.getString("label_bad",null)
        prefs.edit().remove("label_bad").commit()
        SessionManager(context).saveUser("labels-test@example.invalid")
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
        try {
            instrumentation.runOnMainSync {
                views(activity.window.decorView).filterIsInstance<TextView>().first{it.text.toString().startsWith("AJUSTAR LIMITES")}.performClick()
                assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any{it.text.toString()=="Vixe não dá" && it.isClickable})
                val dialog=(activity as MainActivity).createLabelEditor("Faixa vermelha","label_bad","Vixe não dá")
                dialog.show()
                val input=views(dialog.window!!.decorView).filterIsInstance<android.widget.EditText>().single()
                input.setText("Vixe, sai fora")
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            assertEquals("Vixe, sai fora",prefs.getString("label_bad",null))
            instrumentation.runOnMainSync {
                val card=OfferTagView(context)
                card.setOffer("RECUSAR",20.0,10.0,1.0,30.0,"4.70",android.graphics.Color.RED)
                assertTrue(card.contentDescription.toString().contains("Vixe, sai fora"))
            }
        } finally {
            instrumentation.runOnMainSync {activity.finish()}
            prefs.edit().apply {if(previous==null)remove("label_bad") else putString("label_bad",previous)}.commit()
            SessionManager(context).logout()
        }
    }

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

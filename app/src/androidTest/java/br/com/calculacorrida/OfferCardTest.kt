package br.com.calculacorrida

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfferCardTest {
    @Test fun displaysAutomaticMetricsAndArrival() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val offer=OfferTextParser.parse("R$ 18,40 3 min (1,4 km) 25 min (11,2 km) Nota 4,95 Aceitar")!!
        val result=TripCalculator.calculate(offer.fare,offer.pickupKm,offer.tripKm,offer.minutes)!!
        instrumentation.runOnMainSync {
            val view=OfferTagView(instrumentation.targetContext)
            view.setOffer("ATENÇÃO",result.minutes,result.totalKm,result.perKm,result.perHour,offer.passengerRating,Color.YELLOW,"UBER",result.fare,offer.pickupMinutes,offer.pickupKm)
            val text=view.contentDescription.toString()
            for(expected in listOf("R$ 18,40","3 min","4,95","R$ 1,46/km","R$ 39,43/h","28 min")) assertTrue(text,text.contains(expected))
        }
    }
}

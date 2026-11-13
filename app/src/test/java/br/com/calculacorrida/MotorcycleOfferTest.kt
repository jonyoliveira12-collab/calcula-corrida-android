package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

/** Synthetic offer formats; real driver-app layouts still require device validation. */
class MotorcycleOfferTest {
    @Test fun motorcycleAndDeliveryLabelsDoNotChangeRouteCalculations() {
        for ((pkg, title) in listOf(
            "com.ubercab.driver" to "Uber Moto",
            "com.app99.driver" to "99Moto",
            "sinet.startup.inDriver" to "Moto",
            "br.com.ifood.driver.app" to "Entrega de moto"
        )) {
            val text = "$title\nR$ 18,00\n3 min (1,0 km)\n15 min (5,0 km)\nAceitar"
            assertTrue("Incoming offer from $title", IncomingOfferPolicy.isIncoming(pkg, text))
            val offer = OfferTextParser.parse(text, pkg)!!
            assertEquals(18.0, offer.minutes, 0.000001)
            assertEquals(6.0, offer.pickupKm + offer.tripKm, 0.000001)
            assertNull("Delivery offers do not invent a passenger rating", offer.passengerRating)
            val result = TripCalculator.calculate(offer.fare, offer.pickupKm, offer.tripKm, offer.minutes)!!
            assertEquals(3.0, result.perKm, 0.000001)
            assertEquals(60.0, result.perHour, 0.000001)
        }
    }
    @Test fun motorcycleChooseButtonAndBareRouteLegsAreRecognized() {
        for(pkg in listOf("com.ubercab.driver","com.app99.driver","sinet.startup.inDriver")) {
            val text="Moto\nR$ 18,00\n2 min 600 m\n16 min 6,7 km\nEscolher"
            assertTrue(IncomingOfferPolicy.isIncoming(pkg,text))
            val offer=OfferTextParser.parse(text,pkg)!!
            assertEquals(18.0,offer.minutes,.000001)
            assertEquals(7.3,offer.pickupKm+offer.tripKm,.000001)
            assertEquals(2.0,offer.pickupMinutes!!,.000001)
        }
    }
    @Test fun explicitlyLabeledDeliveryTotalsDoNotInventAPickupOrRating() {
        val text="Entrega de moto\nR$ 12,50\nDistância total: 5,0 km\nTempo aproximado de rota: 20 min\nEscolher"
        val pkg="br.com.ifood.driver.app"
        assertTrue(IncomingOfferPolicy.isIncoming(pkg,text))
        val offer=OfferTextParser.parse(text,pkg)!!
        assertEquals(5.0,offer.pickupKm+offer.tripKm,.000001)
        assertEquals(20.0,offer.minutes,.000001)
        assertNull(offer.pickupMinutes)
        assertNull(offer.passengerRating)
        assertNull(OfferTextParser.parse(text,"com.ubercab.driver"))
        assertNull(OfferTextParser.parse(text.replace("Tempo aproximado de rota: 20 min", ""),pkg))
        assertNull(OfferTextParser.parse(text+"\nDistância total: 8 km",pkg))
        assertNull(OfferTextParser.parse(text+"\nR$ 18,00",pkg))
        assertNull(OfferTextParser.parse(text+"\n25 min • 8 km",pkg))
    }
    @Test fun aSingleMotorcyclePickupIsStillIncomplete() {
        assertNull(OfferTextParser.parse("Uber Moto R$ 18,00 2 min 600 m Escolher","com.ubercab.driver"))
    }

}

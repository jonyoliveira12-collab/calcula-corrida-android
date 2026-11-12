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
}

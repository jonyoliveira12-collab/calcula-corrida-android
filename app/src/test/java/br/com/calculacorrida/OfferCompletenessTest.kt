package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

/** Synthetic examples: a pickup must not be treated as the whole ride. */
class OfferCompletenessTest {
    private val uber="com.ubercab.driver"

    @Test fun aPickupThatLooksLikeASummaryIsNotEnough() {
        assertNull(OfferTextParser.parse("R$ 24,00 10 min • 4,0 km 4,90 (100) Tenho interesse",uber))
        assertNull(OfferTextParser.parse("R$ 24,00 10 min • 4,0 km 10 min (4,0 km) Aceitar",uber))
        assertNull(OfferTextParser.parse("R$ 24,00 10 min (4,0 km) Tenho interesse",uber))
    }

    @Test fun explicitlyEstimatedTotalWorksEvenWhenOneLegIsMissing() {
        val text="R$ 24,00 30 min • 12,0 km • Est. de R$ 2,00/km 10 min (4,0 km) Tenho interesse"
        val offer=OfferTextParser.parse(text,uber)!!
        val result=TripCalculator.calculate(offer.fare,offer.pickupKm,offer.tripKm,offer.minutes)!!
        assertEquals(30.0,result.minutes,0.000001)
        assertEquals(12.0,result.totalKm,0.000001)
        assertEquals(2.0,result.perKm,0.000001)
        assertEquals(48.0,result.perHour,0.000001)
    }

    @Test fun twoLegsOrAnExplicitTotalCanConfirmTheWholeTrip() {
        val both=OfferTextParser.parse("R$ 24,00 30 min • 12,0 km 10 min (4,0 km) 20 min (8,0 km) Aceitar",uber)!!
        val labeled=OfferTextParser.parse("R$ 24,00 Total: 30 min • 12,0 km Aceitar",uber)!!
        assertEquals(30.0,both.minutes,0.000001)
        assertEquals(12.0,both.pickupKm+both.tripKm,0.000001)
        assertEquals(30.0,labeled.minutes,0.000001)
        assertEquals(12.0,labeled.pickupKm+labeled.tripKm,0.000001)
    }

    @Test fun estimatedTotalWinsOverAnEarlierPickupLikePair() {
        val text="R$ 24,00 10 min • 4,0 km 30 min • 12,0 km • Est. de R$ 2,00/km Tenho interesse"
        val offer=OfferTextParser.parse(text,uber)!!
        assertEquals(30.0,offer.minutes,0.000001)
        assertEquals(12.0,offer.pickupKm+offer.tripKm,0.000001)
    }
}

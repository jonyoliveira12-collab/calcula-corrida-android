package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

/** Invented offers: no device screenshots, addresses or customer data. */
class NinetyNineOfferParsingTest {
    private val app="com.app99.driver"
    private val offer="""Negocia · Pgto. no app
R$ 16,80
4,89 · 120 corridas · Cartão verif.
7min (2,4km)
Rua Exemplo A
17min (8,8km)
Rua Exemplo B
R$ 17,50   R$ 18,00   R$ 18,50"""

    @Test fun readsNegotiationWhenOcrMissesTheAcceptButton() {
        assertTrue(IncomingOfferPolicy.isIncoming(app,offer))
        val parsed=OfferTextParser.parse(offer,app)!!
        val result=TripCalculator.calculate(parsed.fare,parsed.pickupKm,parsed.tripKm,parsed.minutes)!!
        assertEquals(16.8,parsed.fare,0.000001)
        assertEquals(7.0,parsed.pickupMinutes!!,0.000001)
        assertEquals(24.0,result.minutes,0.000001)
        assertEquals(11.2,result.totalKm,0.000001)
        assertEquals(1.5,result.perKm,0.000001)
        assertEquals(42.0,result.perHour,0.000001)
        assertEquals("4.89",parsed.passengerRating)
    }

    @Test fun suggestionsCanAppearBeforeTheAcceptButtonInOcrOrder() {
        val parsed=OfferTextParser.parse(offer+"\nAceitar por R$ 16,80",app)!!
        assertEquals(16.8,parsed.fare,0.000001)
    }

    @Test fun acceptsVerifiedCardMarkerEvenWhenHeaderIsCropped() {
        val withoutHeader=offer.substringAfter('\n')
        assertTrue(IncomingOfferPolicy.isIncoming(app,withoutHeader))
        assertEquals(16.8,OfferTextParser.parse(withoutHeader,app)!!.fare,0.000001)
    }

    @Test fun incompleteOrConflictingOffersAreNotGuessed() {
        assertNull(OfferTextParser.parse(offer.replace("R$ 16,80\n", "R$ 16,80  R$ 15,00\n"),app))
        assertNull(OfferTextParser.parse(offer+"\nAceitar por R$ 15,00",app))
        assertNull(OfferTextParser.parse(offer.substringBefore("17min"),app))
        assertNull(OfferTextParser.parse(offer.replace("Negocia · Pgto. no app", "").replace("Cartão verif.", ""),app))
        assertFalse(IncomingOfferPolicy.isIncoming(app,offer+"\nEm viagem"))
        assertFalse(IncomingOfferPolicy.isIncoming("com.taxis99",offer))
        assertFalse(IncomingOfferPolicy.isIncoming("com.ubercab.driver",offer))
    }
}

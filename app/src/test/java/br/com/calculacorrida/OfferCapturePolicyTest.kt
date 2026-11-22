package br.com.calculacorrida
import org.junit.Assert.*
import org.junit.Test
class OfferCapturePolicyTest {
    @Test fun readableIncompleteRouteDoesNotStarveOtherAppsWithRotations() {
        assertFalse(OfferCapturePolicy.needsRotationRetry("Moto R$ 8,91 32 min 19.1 km Aceitar"))
        assertFalse(OfferCapturePolicy.needsRotationRetry("R$ 24,00 2 min (1 km)"))
        assertTrue(OfferCapturePolicy.needsRotationRetry("8,91"))
        assertTrue(OfferCapturePolicy.needsRotationRetry("x1 / 8 -"))
        assertFalse(OfferCapturePolicy.needsRotationRetry("Mapa aberto sem chamada ativa"))
        assertFalse(OfferCapturePolicy.needsRotationRetry("Você está disponível procurando novas rotas"))
    }
    @Test fun ninetyNineMotoAndDeliveryWithoutTextButtonNeedBothLegs() {
        for(category in listOf("99Pop","99 Pop","99Plus","99 Comfort","99Taxi","99Negocia","99Moto","99 Moto","99Entrega")) {
            val full="$category R$ 12,00 2 min (1 km) 15 min (6 km)"
            assertTrue(IncomingOfferPolicy.isIncoming("com.app99.driver",full))
            assertNotNull(OfferTextParser.parse(full,"com.app99.driver"))
            assertFalse(IncomingOfferPolicy.isIncoming("com.app99.driver","$category R$ 12,00 2 min (1 km)"))
            assertFalse(IncomingOfferPolicy.isIncoming("com.app99.driver","$full Em viagem"))
        }
    }
    @Test fun ninetyNineAccessibilityNonBreakingSpacesDoNotHideAnOffer() {
        for(space in listOf("\u00a0","\u202f","\u2007")) {
            val offer="99${space}Pop\nR$${space}20,00\nDinheiro\n2${space}min (600${space}m)\n16${space}min (6,7${space}km)"
            assertTrue(IncomingOfferPolicy.isIncoming("com.app99.driver",offer))
            val parsed=OfferTextParser.parse(offer,"com.app99.driver")!!
            assertEquals(20.0,parsed.fare,.00001)
            assertEquals(18.0,parsed.minutes,.00001)
            assertEquals(7.3,parsed.pickupKm+parsed.tripKm,.00001)
            assertFalse(IncomingOfferPolicy.isIncoming("com.app99.driver",offer+" Em viagem"))
            assertFalse(IncomingOfferPolicy.isIncoming("com.taxis99",offer))
        }
    }
    @Test fun explicitNewIfoodRouteCanBeReadWithoutATextAcceptButton() {
        val pkg="br.com.ifood.driver.app"
        val text="Nova rota\nR$ 12,50\nPercurso total: 5,0 km\nTempo estimado da rota: 20 min"
        assertTrue(IncomingOfferPolicy.isIncoming(pkg,text))
        val offer=OfferTextParser.parse(text,pkg)!!
        assertEquals(5.0,offer.tripKm,.00001)
        assertEquals(20.0,offer.minutes,.00001)
        assertFalse(IncomingOfferPolicy.isIncoming(pkg,text+"\nEm rota"))
        assertFalse(IncomingOfferPolicy.isIncoming(pkg,"R$ 12,50 Percurso total: 5 km Tempo estimado da rota: 20 min"))
        assertNull(OfferTextParser.parse(text+"\nPercurso total: 8 km",pkg))
    }

    @org.junit.Test fun analysisOverlaysCannotBecomeFreshOffers() {
        org.junit.Assert.assertTrue(OfferCapturePolicy.isReaderCard("99 30 min • 15,00 km • R$30,00 R$/Km 2,00 R$/Hora 60,00 Nota 4,90"))
        org.junit.Assert.assertTrue(OfferCapturePolicy.isReaderCard("R S / Km 2,00 R S / Hora 60,00 Nota"))
        org.junit.Assert.assertFalse(OfferCapturePolicy.isReaderCard("Priority R$19,09 10 min • 7.0 km Est. de R$2,73/km 4,87 (114)"))
        org.junit.Assert.assertFalse(OfferCapturePolicy.isReaderCard("99Pop R$21,00 Dinheiro 2min (600m) 16min (6,7km)"))
    }
}

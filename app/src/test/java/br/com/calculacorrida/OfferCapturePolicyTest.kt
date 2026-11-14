package br.com.calculacorrida
import org.junit.Assert.*
import org.junit.Test
class OfferCapturePolicyTest {
    @Test fun readableIncompleteRouteDoesNotStarveOtherAppsWithRotations() {
        assertFalse(OfferCapturePolicy.needsRotationRetry("Moto R$ 8,91 32 min 19.1 km Aceitar"))
        assertFalse(OfferCapturePolicy.needsRotationRetry("R$ 24,00 2 min (1 km)"))
        assertTrue(OfferCapturePolicy.needsRotationRetry("8,91"))
        assertTrue(OfferCapturePolicy.needsRotationRetry("Texto invertido sem métricas"))
    }
    @Test fun ninetyNineMotoAndDeliveryWithoutTextButtonNeedBothLegs() {
        for(category in listOf("99Moto","99 Moto","99Entrega")) {
            val full="$category R$ 12,00 2 min (1 km) 15 min (6 km)"
            assertTrue(IncomingOfferPolicy.isIncoming("com.app99.driver",full))
            assertNotNull(OfferTextParser.parse(full,"com.app99.driver"))
            assertFalse(IncomingOfferPolicy.isIncoming("com.app99.driver","$category R$ 12,00 2 min (1 km)"))
            assertFalse(IncomingOfferPolicy.isIncoming("com.app99.driver","$full Em viagem"))
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
}

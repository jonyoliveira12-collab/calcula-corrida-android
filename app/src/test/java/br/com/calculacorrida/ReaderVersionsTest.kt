package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

class ReaderVersionsTest {
    @Test fun warnsOnlyForAnotherJhonDriverReader() {
        val current="br.com.calculacorrida.avaliacao049"
        assertFalse(ReaderVersions.hasOtherEnabledService(null,current))
        assertFalse(ReaderVersions.hasOtherEnabledService("$current/.OfferAccessibilityService:com.example/.Reader",current))
        assertTrue(ReaderVersions.hasOtherEnabledService("$current/.OfferAccessibilityService:br.com.calculacorrida.avaliacao047/.OfferAccessibilityService",current))
        assertTrue(ReaderVersions.hasOtherEnabledService("$current/.OfferAccessibilityService:br.com.calculacorrida.avaliacao048/.OfferAccessibilityService",current))
        assertTrue(ReaderVersions.hasOtherEnabledService("br.com.calculacorrida/.OfferAccessibilityService",current))
        assertFalse(ReaderVersions.hasOtherEnabledService("br.com.calculacorridaoutro/.Reader",current))
    }
}

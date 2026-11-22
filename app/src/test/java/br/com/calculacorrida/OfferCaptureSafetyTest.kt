package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

class OfferCaptureSafetyTest {
    private val target=CaptureSurface(10,"com.app99.driver",CaptureArea(300,100,600,900),2,3)
    private val owned=CaptureArea(320,600,590,850)
    private fun allowed(vararg covers:CaptureSurface,hidden:Set<CaptureArea> = emptySet())=
        OfferCaptureSafety.allows(target,listOf(target)+covers,"br.com.calculacorrida",hidden)
    @Test fun independentDriverAndMapPanesDoNotBlockTheOffer() {
        assertTrue(allowed(CaptureSurface(11,"com.ubercab.driver",CaptureArea(0,100,280,900),3,3),
            CaptureSurface(12,"com.app99.driver",CaptureArea(620,100,900,950),4,3)))
    }
    @Test fun higherUnknownPrivateOrOtherDriverWindowsBlockDisplayCapture() {
        for(owner in listOf(null,"com.nu.production","private.chat","com.ubercab.driver"))
            assertFalse(allowed(CaptureSurface(20,owner,owned,5,3)))
    }
    @Test fun onlyTheReadersOwnHiddenOverlayCanBeExcluded() {
        assertFalse(allowed(CaptureSurface(20,"br.com.calculacorrida",owned,5,3)))
        assertTrue(allowed(CaptureSurface(20,"br.com.calculacorrida",owned,5,3),hidden=setOf(owned)))
        assertFalse(allowed(CaptureSurface(20,"private.chat",owned,5,3),hidden=setOf(owned)))
        assertFalse(allowed(CaptureSurface(20,"br.com.calculacorrida",owned,5,1),hidden=setOf(owned)))
    }
    @Test fun resizedClosedAndUnknownOwnerTargetsAreRejected() {
        assertFalse(OfferCaptureSafety.allows(target,emptyList(),"reader",emptySet()))
        assertFalse(OfferCaptureSafety.allows(target,listOf(target.copy(area=CaptureArea(300,100,550,900))),"reader",emptySet()))
        assertFalse(OfferCaptureSafety.allows(target.copy(owner="private.chat"),listOf(target.copy(owner="private.chat")),"reader",emptySet()))
    }
    @Test fun windowsBehindTheDriverDoNotContributePixels() {
        assertTrue(allowed(CaptureSurface(20,"private.chat",CaptureArea(0,0,900,1000),1,1)))
    }
}

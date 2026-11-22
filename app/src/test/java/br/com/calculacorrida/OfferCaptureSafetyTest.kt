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
    @Test fun transparentEdgesCannotExposeAnotherAppsPixelsBehindTheOffer() {
        assertFalse(allowed(CaptureSurface(20,"private.chat",CaptureArea(0,0,900,1000),1,1)))
        assertTrue(allowed(CaptureSurface(20,"com.app99.driver",CaptureArea(0,0,900,1000),1,1)))
    }
    @Test fun aContainedCropCanExcludeStatusAndNavigationPixels() {
        val full=target.copy(area=CaptureArea(300,0,600,1000))
        val bars=listOf(CaptureSurface(30,"com.android.systemui",CaptureArea(0,0,900,24),10,3),
            CaptureSurface(31,"com.android.systemui",CaptureArea(0,960,900,1000),10,3))
        assertFalse(OfferCaptureSafety.allows(full,listOf(full)+bars,"reader",emptySet()))
        assertTrue(OfferCaptureSafety.allows(full.copy(area=CaptureArea(300,24,600,960)),listOf(full)+bars,"reader",emptySet()))
    }
    @Test fun legacyDisplayReadingStillRejectsOverlyingPrivateWindows() {
        val below=CaptureSurface(20,"private.chat",owned,1,1)
        assertTrue(OfferCaptureSafety.allows(target,listOf(target,below),"reader",emptySet(),false))
        assertFalse(OfferCaptureSafety.allows(target,listOf(target,below.copy(layer=5)),"reader",emptySet(),false))
    }
    @Test fun foreignCornerControlsAreRemovedInsteadOfBeingRead() {
        val cover=CaptureSurface(20,"private.controls",CaptureArea(500,100,600,180),5,3)
        val surfaces=listOf(target,cover)
        val crop=OfferCaptureSafety.uncoveredArea(target,surfaces,"reader",emptySet())!!
        assertFalse(crop.overlaps(cover.area))
        assertTrue(target.area.contains(crop))
        assertTrue(OfferCaptureSafety.allows(target.copy(area=crop),surfaces,"reader",emptySet()))
    }
    @Test fun fullyCoveredOrForeignBackgroundWindowsHaveNoSafeCrop() {
        val cover=CaptureSurface(20,"private.chat",target.area,5,1)
        assertNull(OfferCaptureSafety.uncoveredArea(target,listOf(target,cover),"reader",emptySet()))
        assertNull(OfferCaptureSafety.uncoveredArea(target,listOf(target,cover.copy(layer=1)),"reader",emptySet()))
    }
    @Test fun anotherDisplaysCoordinatesCannotAuthorizeACapture() {
        val external=target.copy(display=1)
        assertFalse(OfferCaptureSafety.allows(external,listOf(external),"reader",emptySet()))
        assertTrue(allowed(CaptureSurface(20,"private.chat",owned,5,3,1)))
    }
    @Test fun emptyWindowsCannotOccludeAnOffer() {
        assertTrue(allowed(CaptureSurface(20,null,CaptureArea(400,300,400,500),5,3)))
    }
}

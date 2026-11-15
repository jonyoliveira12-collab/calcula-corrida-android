package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

class FriendLocationPolicyTest {
    @Test fun rejectsStaleAndFutureSensorFixes() {
        assertTrue(FriendLocationPolicy.valid(-25.4,-49.2,25f,45000))
        assertFalse(FriendLocationPolicy.valid(-25.4,-49.2,25f,45001))
        assertFalse(FriendLocationPolicy.valid(-25.4,-49.2,25f,-1))
    }
    @Test fun rejectsMalformedCoordinatesAndAccuracy() {
        assertFalse(FriendLocationPolicy.valid(Double.NaN,0.0,1f,0))
        assertFalse(FriendLocationPolicy.valid(0.0,Double.POSITIVE_INFINITY,1f,0))
        assertFalse(FriendLocationPolicy.valid(91.0,0.0,1f,0))
        assertFalse(FriendLocationPolicy.valid(0.0,-181.0,1f,0))
        assertFalse(FriendLocationPolicy.valid(0.0,0.0,Float.NaN,0))
        assertFalse(FriendLocationPolicy.valid(0.0,0.0,-1f,0))
    }
    @Test fun consentExpiryUsesServerTimeAndNeverExtendsBeyondFourHours() {
        assertEquals(0L,FriendLocationPolicy.remaining(999,1000))
        assertEquals(900000L,FriendLocationPolicy.remaining(901000,1000))
        assertEquals(14400000L,FriendLocationPolicy.remaining(999999999,1000))
    }
}

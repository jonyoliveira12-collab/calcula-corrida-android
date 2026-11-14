package br.com.calculacorrida

import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfferNotificationTest {
    @Test fun savedOfferStaysInHistoryAndNotificationWithoutAnOverlay() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val manager=context.getSystemService(NotificationManager::class.java)
        JourneyStore.clear(context)
        manager.cancel(OfferNotifications.ID)
        val offer=OfferTextParser.parse("Envios Moto\nR$ 8,91\n32 min • 19.1 km\nAceitar","com.ubercab.driver")!!
        val result=TripCalculator.calculate(offer.fare,offer.pickupKm,offer.tripKm,offer.minutes)!!
        JourneyStore.record(context,offer,result,"com.ubercab.driver")
        assertEquals(1,JourneyStore.getRecords(context).size)
        val deadline=android.os.SystemClock.elapsedRealtime()+3000
        while(manager.activeNotifications.none{it.id==OfferNotifications.ID && it.notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString().contains("19,1 km")} && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50)
        val notice=manager.activeNotifications.first{it.id==OfferNotifications.ID}.notification
        assertEquals("UBER • oferta salva",notice.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertTrue(notice.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString().contains("19,1 km"))
        JourneyStore.record(context,offer,result,"com.ubercab.driver")
        assertEquals(1,JourneyStore.getRecords(context).size)
        manager.cancel(OfferNotifications.ID)
        JourneyStore.clear(context)
    }
}

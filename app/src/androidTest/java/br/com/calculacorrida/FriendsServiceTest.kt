package br.com.calculacorrida

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FriendsServiceTest {
    @Suppress("DEPRECATION")
    @Test fun foregroundSharingStartsOnlyAfterConsentAndStopsOnOffline() {
        val inst = InstrumentationRegistry.getInstrumentation(); val context = inst.targetContext
        val originalFactory = FriendsDependencies.gateway
        val prefs = context.getSharedPreferences("user_session",0); val original = prefs.all.toMap()
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val peer = UUID.randomUUID().toString()
        val fake = object : FriendsGateway {
            @Volatile var granted = false
            override fun call(action: String, data: JSONObject): JSONObject {
                calls.add(action)
                if (action == "share") granted = data.optBoolean("enabled")
                if (action == "offline") granted = false
                val shares = JSONArray()
                if (granted) shares.put(JSONObject().put("peer",peer).put("until_ms",System.currentTimeMillis()+900000))
                return JSONObject().put("ok",true).put("now_ms",System.currentTimeMillis()).put("shares",shares)
                    .put("me",JSONObject().put("id",UUID.randomUUID().toString()).put("name","Teste").put("code","000000000000"))
                    .put("friends",JSONArray())
            }
        }
        fun shell(command: String) { inst.uiAutomation.executeShellCommand(command).use { fd -> java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } } }
        fun await(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime()+10000
            while (!condition() && SystemClock.elapsedRealtime()<end) Thread.sleep(100)
            assertTrue(message,condition())
        }
        var activity: Activity? = null
        val manager = context.getSystemService(LocationManager::class.java)
        try {
            FriendsService.stop(context)
            FriendsDependencies.gateway = { _, _ -> fake }
            SessionManager(context).saveUser("friends-service-test@example.invalid","synthetic-token",null)
            shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_COARSE_LOCATION}")
            shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
            shell("appops set ${context.packageName} android:mock_location allow")
            activity = inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            inst.waitForIdleSync()
            inst.runOnMainSync { ContextCompat.startForegroundService(context,Intent(context,FriendsService::class.java)) }
            await("Foreground messaging service connects",{ FriendsService.connected })
            assertFalse("Being online never starts location",FriendsService.sharing)
            assertFalse(calls.contains("location"))
            manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,android.location.Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            inst.runOnMainSync { context.startService(Intent(context,FriendsService::class.java).setAction(FriendsService.SHARE)
                .putExtra("peer",peer).putExtra("enabled",true).putExtra("minutes",15)) }
            await("Explicit share starts location",{ FriendsService.sharing })
            Thread.sleep(500)
            // Synthetic coordinates from Android's mock provider, never a physical driver's position.
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                latitude=0.0;longitude=0.0;accuracy=20f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
            })
            await("Recent GPS fix is sent after consent",{ calls.contains("location") })
            inst.runOnMainSync { FriendsService.stop(context) }
            await("Offline stops sensors and foreground service",{ !FriendsService.running && !FriendsService.sharing })
            await("Server receives session-scoped offline",{ calls.contains("offline") })
            val count = synchronized(calls) { calls.count { it=="location" } }
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                latitude=1.0;longitude=1.0;accuracy=20f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
            })
            Thread.sleep(400)
            assertEquals("No position uploaded after offline",count,synchronized(calls) { calls.count { it=="location" } })
        } finally {
            inst.runOnMainSync { FriendsService.stop(context);activity?.finish() }
            runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            shell("appops set ${context.packageName} android:mock_location deny")
            FriendsDependencies.gateway = originalFactory
            val edit = prefs.edit().clear()
            for ((key,value) in original) when(value) {
                is String -> edit.putString(key,value)
                is Boolean -> edit.putBoolean(key,value)
                is Long -> edit.putLong(key,value)
                is Int -> edit.putInt(key,value)
                is Float -> edit.putFloat(key,value)
            }
            edit.commit()
        }
    }
}

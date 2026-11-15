package br.com.calculacorrida

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendsPrivacyTest {
    @Test fun openingFriendsNeverStartsLocationOrMarksDriverOnline() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val prefs = context.getSharedPreferences("user_session",0)
        val original = prefs.all.toMap()
        var activity: Activity? = null
        try {
            SessionManager(context).logout()
            SessionManager(context).saveUser("friends-device-test@example.invalid","not-a-real-token",null)
            activity = inst.startActivitySync(Intent(context,FriendsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            inst.waitForIdleSync()
            inst.runOnMainSync {
                fun labels(view: View): List<String> = when (view) {
                    is TextView -> listOf(view.text.toString())
                    is ViewGroup -> (0 until view.childCount).flatMap { labels(view.getChildAt(it)) }
                    else -> emptyList()
                }
                val text = labels(activity!!.window.decorView).joinToString("\n")
                assertTrue(text.contains("FICAR ONLINE"))
                assertTrue(text.contains("Adicionar amigo por código"))
                assertFalse(FriendsService.running)
                assertFalse(FriendsService.sharing)
                assertNull(FriendsService.sessionId)
            }
            val info = context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS)
            assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            val dir = java.io.File(context.filesDir,"screenshots").apply { mkdirs() }
            inst.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(dir,"friends-offline.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        } finally {
            inst.runOnMainSync { activity?.finish() }
            val edit = prefs.edit().clear()
            for ((key,value) in original) when (value) {
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

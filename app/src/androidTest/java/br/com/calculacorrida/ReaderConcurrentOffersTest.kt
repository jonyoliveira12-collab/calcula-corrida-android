package br.com.calculacorrida

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Two CI-only driver apps draw different offers simultaneously in separate windows. */
@RunWith(AndroidJUnit4::class)
class ReaderConcurrentOffersTest {
    @Test fun uberAndNinetyNineKeepTheirOwnConcurrentTags() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        fun nodeText(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int = 0): List<String> {
            if (depth > 12) return emptyList()
            val result = mutableListOf<String>()
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { result.addAll(nodeText(child, depth + 1)) } finally { child.recycle() }
            }
            return result
        }
        fun describeWindows(): String = ui.windows.joinToString(" / ") { window ->
            val root = window.root ?: return@joinToString "no-root"
            try {
                val pkg = root.packageName?.toString().orEmpty()
                val bounds = Rect().also { window.getBoundsInScreen(it) }
                val texts = if (pkg == context.packageName) nodeText(root).distinct().joinToString(" | ").take(1000) else ""
                "$pkg ${window.type} $bounds [$texts]"
            } finally { root.recycle() }
        }
        fun describeSyntheticNinetyNineOcr(): String {
            // Only this CI fixture is captured; it contains invented offers and no user data.
            return try {
                context.getSharedPreferences("settings", 0).edit().putBoolean("reader_paused", true).commit()
                Thread.sleep(750)
                val bounds = ui.windows.mapNotNull { window ->
                    val root = window.root ?: return@mapNotNull null
                    try {
                        if (root.packageName?.toString() != "com.app99.driver" ||
                            window.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@mapNotNull null
                        Rect().also { window.getBoundsInScreen(it) }.takeIf { it.width() >= 160 && it.height() >= 100 }
                    } finally { root.recycle() }
                }.maxByOrNull { it.width().toLong() * it.height() } ?: return "fixture window unavailable"
                val screenshot = ui.takeScreenshot() ?: return "fixture screenshot unavailable"
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                )
                try {
                    if (!bounds.intersect(0, 0, screenshot.width, screenshot.height)) return "fixture bounds unavailable"
                    val crop = android.graphics.Bitmap.createBitmap(screenshot, bounds.left, bounds.top, bounds.width(), bounds.height())
                    try {
                        com.google.android.gms.tasks.Tasks.await(
                            recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(crop, 0)),
                            10, java.util.concurrent.TimeUnit.SECONDS
                        ).text.replace("\n", " | ")
                    } finally { crop.recycle() }
                } finally { recognizer.close(); screenshot.recycle() }
            } catch (e: Exception) { "fixture OCR unavailable: ${e.javaClass.simpleName}" }
        }
        fun await(label: String, timeoutMs: Long = 45000, check: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
            while (!check() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            val prefs = context.getSharedPreferences("settings", 0)
            val diagnostic = listOf("reader_status", "reader_window_status", "reader_capture_status", "reader_ocr_status")
                .joinToString(" / ") { prefs.getString(it, "").orEmpty() }
            if (!check()) {
                // The emulator fixtures contain synthetic offers only.
                val records = JourneyStore.getRecords(context).take(5).joinToString(" | ") {
                    listOf(it.optString("app"), it.optDouble("fare"), it.optDouble("totalKm"), it.optDouble("minutes"), it.optDouble("perKm"), it.optDouble("perHour"), it.optString("passengerRating")).joinToString(";")
                }
                val windows = describeWindows()
                val fixtureOcr = if (label.startsWith("Distinct")) describeSyntheticNinetyNineOcr() else ""
                val crashes=if(label.startsWith("Tall")) shell("logcat -d -b crash -v brief -t 100").takeLast(6000) else ""
                fail("$label: $diagnostic / records=$records / windows=$windows / synthetic99=$fixtureOcr / fixture crashes=$crashes")
            }
        }
        fun findTag(source: String, summary: List<String>, metrics: List<String>): Rect? {
            for (window in ui.windows) {
                val root = window.root ?: continue
                try {
                    if (root.packageName?.toString() != context.packageName) continue
                    // The card exposes an accessibility description for screen readers;
                    // Android may collapse its child text nodes into that description.
                    val texts = nodeText(root)
                    val titleFound = texts.any { line ->
                        line.startsWith("$source ") && summary.all { line.contains(it) }
                    }
                    val metricsFound = metrics.all { value ->
                        texts.any { line -> line == value ||
                            (line.startsWith("$source •") && line.contains(value)) }
                    }
                    if (titleFound && metricsFound) return Rect().also { window.getBoundsInScreen(it) }
                } finally { root.recycle() }
            }
            return null
        }
        fun driverWindow(pkg: String): Rect? = ui.windows.mapNotNull { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@mapNotNull null
            val root = window.root ?: return@mapNotNull null
            try {
                if (root.packageName?.toString() != pkg) return@mapNotNull null
                Rect().also { window.getBoundsInScreen(it) }.takeIf { it.width() >= 160 && it.height() >= 100 }
            } finally { root.recycle() }
        }.maxByOrNull { it.width().toLong() * it.height() }

        val oldServices = shell("settings get secure enabled_accessibility_services")
        val oldEnabled = shell("settings get secure accessibility_enabled")
        val prefs = context.getSharedPreferences("settings", 0)
        val originalBad=prefs.getString("label_bad",null)
        val originalGood=prefs.getString("label_good",null)
        val originalJourney = context.getSharedPreferences("journey", 0).getString("offers", null)
        try {
            shell("settings delete secure enabled_accessibility_services")
            shell("settings put secure accessibility_enabled 0")
            listOf("com.ubercab.driver", "com.app99.driver", "br.com.calculacorrida.fixture.backdrop").forEach { shell("am force-stop $it") }
            shell("wm user-rotation lock 1")
            listOf(context.packageName, "com.ubercab.driver", "com.app99.driver").forEach { shell("appops set $it SYSTEM_ALERT_WINDOW allow") }
            prefs.edit().putBoolean("copilot_enabled", true).putBoolean("traffic_enabled", false)
                .putBoolean("reader_paused", false).putBoolean("reader_disclosure_accepted", true)
                .putBoolean("reader_model_ready", false).putBoolean("card_km", true)
                .putBoolean("card_hour", true).putBoolean("card_rating", true)
                .putInt("card_hold_seconds", 120).commit()
            shell("settings put secure enabled_accessibility_services ${context.packageName}/br.com.calculacorrida.OfferAccessibilityService")
            shell("settings put secure accessibility_enabled 1")
            await("OCR initialized", 30000) { prefs.getBoolean("reader_model_ready", false) }

            val startedAt = System.currentTimeMillis()
            shell("am start -W -n com.ubercab.driver/br.com.calculacorrida.fixture.OfferFixtureActivity --es layout dual")
            shell("am start -W -n com.app99.driver/br.com.calculacorrida.fixture.OfferFixtureActivity --es layout dual")
            await("Both driver windows are simultaneously visible") {
                driverWindow("com.ubercab.driver") != null && driverWindow("com.app99.driver") != null
            }
            val uberSummary = listOf("20 min", "8,00 km", "R$ 12,00")
            val ninetyNineSummary = listOf("30 min", "15,00 km", "R$ 30,00")
            fun uberTag() = findTag("UBER", uberSummary, listOf("1,50", "36,00", "4,80"))
            fun ninetyNineTag() = findTag("99", ninetyNineSummary, listOf("2,00", "60,00", "4,90"))
            await("Distinct Uber and 99 tags with the correct metrics") { uberTag() != null && ninetyNineTag() != null }

            // Changing a label must refresh an already-visible card too.
            prefs.edit().putString("label_bad","Vixe, sai fora").putString("label_good","Bora, carai!").commit()
            fun labelVisible(source:String,label:String):Boolean = ui.windows.any { window ->
                val root=window.root ?: return@any false
                try {root.packageName?.toString()==context.packageName && nodeText(root).any{it.startsWith("$source •") && it.contains(label)}}
                finally {root.recycle()}
            }
            await("Custom names refresh the two visible tags",12000) {
                labelVisible("UBER","Vixe, sai fora") && labelVisible("99","Bora, carai!")
            }

            val uber = uberTag()!!
            val ninetyNine = ninetyNineTag()!!
            val uberPane = driverWindow("com.ubercab.driver")!!
            val ninetyNinePane = driverWindow("com.app99.driver")!!
            assertTrue("Uber tag is inside the Uber window", uberPane.contains(uber.centerX(), uber.centerY()))
            assertTrue("99 tag is inside the 99 window", ninetyNinePane.contains(ninetyNine.centerX(), ninetyNine.centerY()))
            assertFalse("The tags do not overlap", Rect.intersects(uber, ninetyNine))
            val records = JourneyStore.getRecords(context).filter { it.optLong("timestamp") >= startedAt }
            assertTrue("Uber offer recorded", records.any { it.optString("app") == "Uber" && kotlin.math.abs(it.optDouble("fare")-12.0)<0.001 })
            assertTrue("99 offer recorded", records.any { it.optString("app") == "99" && kotlin.math.abs(it.optDouble("fare")-30.0)<0.001 })

            // The expiry setting used by older builds must no longer remove a
            // valid offer. Both fixture windows remain visible during this time.
            prefs.edit().putInt("card_hold_seconds",10).commit()
            Thread.sleep(11500)
            assertNotNull("Uber tag remains while its offer is visible",uberTag())
            assertNotNull("99 tag remains while its offer is visible",ninetyNineTag())

            // Remove only the offer pixels, leaving the Uber window open.
            shell("am broadcast -a br.com.calculacorrida.fixture.HIDE_OFFER -p com.ubercab.driver")
            await("Uber tag leaves after its offer disappears, 99 remains",20000) {
                driverWindow("com.ubercab.driver")!=null && uberTag()==null && ninetyNineTag()!=null
            }
            shell("am broadcast -a br.com.calculacorrida.fixture.SHOW_OFFER -p com.ubercab.driver")
            await("Uber tag returns for a new visible offer",30000) {uberTag()!=null && ninetyNineTag()!=null}

            shell("am force-stop com.app99.driver")
            await("Closing 99 removes only its tag", 10000) { ninetyNineTag() == null && uberTag() != null }

            // A second scenario resembles a tall 99 negotiation window. A
            // larger map window from the same app must not starve its OCR.
            shell("am force-stop com.ubercab.driver")
            await("Previous tags leave after the apps close", 12000) { uberTag()==null && ninetyNineTag()==null }
            // Clear the fake app's old task: Android otherwise resumes the
            // backdrop without creating the new fixture or its offer window.
            val uberStart=shell("am start -W -S -f 0x10008000 -n com.ubercab.driver/br.com.calculacorrida.fixture.OfferFixtureActivity --es layout dual_realistic")
            val ninetyNineStart=shell("am start -W -S -f 0x10008000 -n com.app99.driver/br.com.calculacorrida.fixture.OfferFixtureActivity --es layout dual_realistic")
            instrumentation.sendStatus(0,android.os.Bundle().apply{putString("stream","Fixture start: Uber=$uberStart / 99=$ninetyNineStart\n")})
            assertFalse("Uber fixture must be recreated",uberStart.contains("Activity not started"))
            assertFalse("99 fixture must be recreated",ninetyNineStart.contains("Activity not started"))
            val realisticSummary=listOf("24 min","11,20 km","R$ 16,80")
            fun realistic99Tag()=findTag("99",realisticSummary,listOf("1,50","42,00","4,89"))
            fun middle99Pane():Rect? = ui.windows.mapNotNull { window ->
                if(window.type!=AccessibilityWindowInfo.TYPE_SYSTEM)return@mapNotNull null
                val root=window.root ?: return@mapNotNull null
                try {
                    if(root.packageName?.toString()!="com.app99.driver")return@mapNotNull null
                    Rect().also{window.getBoundsInScreen(it)}.takeIf{it.width()>=160 && it.height()>=100}
                } finally {root.recycle()}
            }.minByOrNull{it.left}
            await("Tall negotiated 99 offer and Uber each have an independent tag",60000) {
                uberTag()!=null && realistic99Tag()!=null && middle99Pane()!=null
            }
            assertTrue("99 tag stays with its offer instead of the larger map window",middle99Pane()!!.contains(realistic99Tag()!!.centerX(),realistic99Tag()!!.centerY()))
            assertFalse("Both independent tags remain separate",Rect.intersects(uberTag()!!,realistic99Tag()!!))
            shell("am force-stop com.ubercab.driver")
            await("Closing Uber keeps the 99 tag",12000) {uberTag()==null && realistic99Tag()!=null}
        } finally {
            listOf("com.ubercab.driver", "com.app99.driver", "br.com.calculacorrida.fixture.backdrop").forEach { shell("am force-stop $it") }
            shell("wm user-rotation free")
            prefs.edit().putBoolean("copilot_enabled", false).putBoolean("traffic_enabled", false).remove("card_hold_seconds").apply {
                if(originalBad==null)remove("label_bad") else putString("label_bad",originalBad)
                if(originalGood==null)remove("label_good") else putString("label_good",originalGood)
            }.commit()
            context.getSharedPreferences("journey", 0).edit().apply {
                if (originalJourney == null) remove("offers") else putString("offers", originalJourney)
            }.commit()
            instrumentation.runOnMainSync { OfferAccessibilityService.stopReading() }
            if (oldServices == "null" || oldServices.isBlank()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services '$oldServices'")
            if (oldEnabled == "null" || oldEnabled.isBlank()) shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $oldEnabled")
        }
    }
}

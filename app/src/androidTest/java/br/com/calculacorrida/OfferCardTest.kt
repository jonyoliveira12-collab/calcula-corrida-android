package br.com.calculacorrida

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfferCardTest {
    @Test fun displaysAutomaticMetricsAndArrival() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val offer=OfferTextParser.parse("R$ 18,40 3 min (1,4 km) 25 min (11,2 km) Nota 4,95 Aceitar")!!
        val result=TripCalculator.calculate(offer.fare,offer.pickupKm,offer.tripKm,offer.minutes)!!
        instrumentation.runOnMainSync {
            val view=OfferTagView(instrumentation.targetContext)
            view.setOffer("ATENÇÃO",result.minutes,result.totalKm,result.perKm,result.perHour,offer.passengerRating,Color.YELLOW,"UBER",result.fare,offer.pickupMinutes,offer.pickupKm)
            val text=view.contentDescription.toString()
            for(expected in listOf("R$ 18,40","3 min","4,95","R$ 1,46/km","R$ 39,43/h","28 min")) assertTrue(text,text.contains(expected))
        }
    }
    @Test fun compactThemesAndOptionalFieldsRenderWithoutOverflow() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.all
        try {
            for(theme in listOf("light","dark","colored")) {
                prefs.edit().putString("card_theme",theme).putBoolean("card_details",false).putBoolean("card_ai",false).commit()
                instrumentation.runOnMainSync {
                    val view=OfferTagView(context)
                    view.setOffer("RECUSAR",31.0,19.1,1.51,55.76,"4.76",Color.rgb(209,48,57),"UBER",28.81,6.0,2.2)
                    val width=(310*context.resources.displayMetrics.density).toInt()
                    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
                    view.layout(0,0,width,view.measuredHeight)
                    assertTrue("Compact card height",view.height<200*context.resources.displayMetrics.density)
                    val bitmap=android.graphics.Bitmap.createBitmap(width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()}
                    java.io.File(dir,"card-$theme.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                }
            }
            prefs.edit().putBoolean("card_km",false).putBoolean("card_hour",false).putBoolean("card_rating",false).commit()
            instrumentation.runOnMainSync {
                val view=OfferTagView(context)
                view.setOffer("ATENÇÃO",31.0,19.1,1.51,55.76,"4.76",Color.YELLOW)
                fun texts(v:android.view.View):List<String> = if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))} else if(v is android.widget.TextView)listOf(v.text.toString())else emptyList()
                val rendered=texts(view)
                assertFalse(rendered.contains("R$/Km"));assertFalse(rendered.contains("R$/Hora"));assertFalse(rendered.contains("Nota"))
            }
        } finally {
            val edit=prefs.edit().clear()
            original.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v);is Long->edit.putLong(k,v)}}
            edit.commit()
        }
    }
    @Test fun consecutiveOffersAndAppearanceChangesReuseTheCardWithoutCrashing() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.getString("card_theme","light")
        try {
            instrumentation.runOnMainSync {
                val view=OfferTagView(context)
                view.setOffer("RECUSAR",14.0,10.8,1.02,47.27,"4.92",Color.RED,"UBER",11.03)
                assertTrue(view.contentDescription.toString().contains("11,03"))
                view.setOffer("RECUSAR",26.0,16.4,1.06,40.08,"4.98",Color.RED,"UBER",17.37)
                assertTrue(view.contentDescription.toString().contains("17,37"))
                assertFalse(view.contentDescription.toString().contains("11,03"))
                prefs.edit().putString("card_theme","dark").commit()
                view.setOffer("ATENÇÃO",22.0,10.4,1.14,32.45,"4.79",Color.YELLOW,"99",11.90)
                assertTrue(view.contentDescription.toString().contains("32,45"))
            }
        } finally {prefs.edit().putString("card_theme",original).commit()}
    }
    @Test fun verdictIsACompactFilledBadgeForEveryClassification() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.all
        try {
            prefs.edit().putBoolean("card_banner",true).putString("card_banner_position","above")
                .putBoolean("card_neon_flames",true).putBoolean("card_compact_reason",false)
                .remove("label_bad").remove("label_review").remove("label_good").commit()
            instrumentation.runOnMainSync {
                val view=OfferTagView(context)
                for((label,shown) in listOf("RECUSAR" to "Vixe não dá","ATENÇÃO" to "Hum, talvez","ACEITAR" to "Bora carai")) {
                    view.setOffer(label,19.0,12.0,1.0641666667,40.3263157895,"4.99",Color.RED,"UBER",12.77)
                    val badge=view.getChildAt(0) as android.widget.TextView
                    assertEquals(shown,badge.text.toString())
                    assertNotNull("Neon badge has a flame icon",badge.compoundDrawables[0])
                    assertNotNull(badge.background)
                    assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,badge.layoutParams.width)
                    val width=(310*context.resources.displayMetrics.density).toInt()
                    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
                    view.layout(0,0,width,view.measuredHeight)
                    assertTrue(badge.measuredWidth<width)
                    val body=view.getChildAt(1)
                    assertTrue("Close control remains tappable",view.isCloseHit((body.right-20).toFloat(),(body.top+15).toFloat()))
                    assertTrue(view.contentDescription.toString().contains("R$ 1,06/km"))
                    assertTrue(view.contentDescription.toString().contains("R$ 40,33/h"))
                }
            }
        } finally {
            val edit=prefs.edit().clear()
            original.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v);is Long->edit.putLong(k,v)}}
            edit.commit()
        }
    }

    @Test fun customNamesAppearForEachClassificationWithoutChangingMetrics() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.all
        try {
            prefs.edit().putString("label_bad","Vixe, sai fora")
                .putString("label_review","Será?").putString("label_good","Bora, carai!")
                .putBoolean("card_banner",true).putBoolean("card_neon_flames",true)
                .putString("card_banner_position","above").putBoolean("card_compact_reason",false).commit()
            instrumentation.runOnMainSync {
                val view=OfferTagView(context)
                for((classification,name) in listOf("RECUSAR" to "Vixe, sai fora","ATENÇÃO" to "Será?","ACEITAR" to "Bora, carai!")) {
                    view.setOffer(classification,20.0,10.0,1.5,45.0,"4.90",Color.RED,"UBER",15.0)
                    assertEquals(name,(view.getChildAt(0) as android.widget.TextView).text.toString())
                    assertTrue(view.contentDescription.toString().contains("R$ 1,50/km"))
                    assertTrue(view.contentDescription.toString().contains("R$ 45,00/h"))
                }
            }
        } finally {
            val edit=prefs.edit().clear()
            original.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v);is Long->edit.putLong(k,v)}}
            edit.commit()
        }
    }

    @Test fun neonFlamesRenderForAllVerdictsAndCanBeTurnedOff() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.all
        try {
            prefs.edit().remove("card_neon_flames").putString("card_theme","light").putBoolean("card_banner",true)
                .putBoolean("card_km",true).putBoolean("card_hour",true).putBoolean("card_rating",true)
                .putBoolean("card_compact_reason",false).putBoolean("card_details",false).putBoolean("card_ai",false)
                .putString("card_banner_position","above").remove("label_bad").commit()
            instrumentation.runOnMainSync {
                val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()}
                for((label,name,color) in listOf(
                    Triple("RECUSAR","red",Color.rgb(209,48,57)),
                    Triple("ATENÇÃO","amber",Color.rgb(204,137,22)),
                    Triple("ACEITAR","green",Color.rgb(0,146,99))
                )) {
                    val view=OfferTagView(context)
                    val (fare,km,minutes,rating)=when(label) {
                        "RECUSAR" -> listOf(10.0,10.0,20.0,4.70)
                        "ATENÇÃO" -> listOf(25.0,15.0,30.0,4.85)
                        else -> listOf(36.0,18.0,30.0,4.96)
                    }
                    view.setOffer(label,minutes,km,fare/km,fare*60/minutes,String.format(java.util.Locale.US,"%.2f",rating),color,
                        if(label=="ACEITAR")"99" else "UBER",fare,6.0,4.0)
                    val width=(310*context.resources.displayMetrics.density).toInt()
                    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
                    view.layout(0,0,width,view.measuredHeight)
                    assertTrue("Compact neon card height",view.height<200*context.resources.displayMetrics.density)
                    val badge=view.getChildAt(0) as android.widget.TextView
                    assertNotNull("Flames are on by default",badge.compoundDrawables[0])
                    for((backgroundName,background) in listOf("dark" to Color.rgb(28,29,32),"light" to Color.rgb(235,241,249))) {
                        val bitmap=android.graphics.Bitmap.createBitmap(width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas=android.graphics.Canvas(bitmap)
                        canvas.drawColor(background)
                        view.draw(canvas)
                        java.io.File(dir,"card-fire-$name-$backgroundName.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                        if(backgroundName=="light") {
                            val body=view.getChildAt(1)
                            val density=context.resources.displayMetrics.density
                            var visibleFirePixels=0
                            for(y in (body.bottom-(22*density).toInt()).coerceAtLeast(0) until (body.bottom-(3*density).toInt()).coerceAtMost(bitmap.height)) {
                                for(x in (body.left+(8*density).toInt()).coerceAtLeast(0) until (body.right-(8*density).toInt()).coerceAtMost(bitmap.width)) {
                                    val p=bitmap.getPixel(x,y)
                                    val red=Color.red(p);val green=Color.green(p);val blue=Color.blue(p)
                                    val isFire=when(name) {
                                        "red" -> red>160 && red>green*1.35 && red>blue*1.35
                                        "amber" -> red>170 && green>75 && blue<120
                                        else -> green>110 && green>red*1.3 && green>blue*1.1
                                    }
                                    if(isFire)visibleFirePixels++
                                }
                            }
                            assertTrue("Visible $name flames against a light map",visibleFirePixels>50)
                        }
                        bitmap.recycle()
                    }
                }
                prefs.edit().putBoolean("card_neon_flames",false).commit()
                val plain=OfferTagView(context)
                plain.setOffer("RECUSAR",20.0,10.0,1.0,30.0,"4.70",Color.RED,"UBER",10.0)
                val badge=plain.getChildAt(0) as android.widget.TextView
                assertEquals("● Vixe não dá",badge.text.toString())
                assertNull("Flame icon is removed when disabled",badge.compoundDrawables[0])
            }
        } finally {
            val edit=prefs.edit().clear()
            original.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v);is Long->edit.putLong(k,v)}}
            edit.commit()
        }
    }

    @Test fun cardStylePreviewShowsCalculatedExamplesForEachVerdict() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.all
        var activity:android.app.Activity?=null
        try {
            prefs.edit().putBoolean("card_neon_flames",true).putBoolean("card_banner",true)
                .putString("card_banner_position","above").putBoolean("card_compact_reason",false)
                .remove("label_bad").remove("label_review").remove("label_good").commit()
            activity=instrumentation.startActivitySync(android.content.Intent(context,CardStyleActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.runOnMainSync {
                fun views(v:android.view.View):List<android.view.View> = listOf(v)+
                    if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
                val all=views(activity!!.window.decorView)
                val preview=all.filterIsInstance<OfferTagView>().single()
                fun select(label:String){all.filterIsInstance<android.widget.Button>().single{it.text.toString()==label}.performClick()}
                fun containsAll(vararg values:String){for(value in values)assertTrue(preview.contentDescription.toString(),preview.contentDescription.toString().contains(value))}
                containsAll("Vixe não dá","R$ 1,00/km","R$ 30,00/h","4,70")
                select("Analisar")
                containsAll("Hum, talvez","R$ 1,67/km","R$ 50,00/h","4,85")
                select("Boa")
                containsAll("Bora carai","R$ 2,00/km","R$ 72,00/h","4,96")
                val toggle=all.filterIsInstance<android.widget.Switch>().single{it.text.toString()=="Chamas neon"}
                assertTrue(toggle.isChecked)
                toggle.performClick()
                assertNull((preview.getChildAt(0) as android.widget.TextView).compoundDrawables[0])
            }
        } finally {
            activity?.let{instrumentation.runOnMainSync{it.finish()}}
            val edit=prefs.edit().clear()
            original.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v);is Long->edit.putLong(k,v)}}
            edit.commit()
        }
    }

    @Test fun flamesAnimateWhileVisibleAndStopWhenDisabled() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val ui=instrumentation.uiAutomation
        fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use{it.readText().trim()}
        val oldScale=shell("settings get global animator_duration_scale")
        val prefs=context.getSharedPreferences("settings",0)
        val original=prefs.all
        var activity:android.app.Activity?=null
        try {
            shell("settings put global animator_duration_scale 1")
            prefs.edit().putBoolean("card_neon_flames",true).putBoolean("card_neon_animated",true)
                .putBoolean("card_banner",true).putString("card_banner_position","above").commit()
            activity=instrumentation.startActivitySync(android.content.Intent(context,CardStyleActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.waitForIdleSync()
            repeat(30){if(!android.animation.ValueAnimator.areAnimatorsEnabled())Thread.sleep(100)}
            assertTrue("System animations enabled for this test",android.animation.ValueAnimator.areAnimatorsEnabled())
            var preview:OfferTagView?=null
            var toggle:android.widget.Switch?=null
            instrumentation.runOnMainSync {
                fun views(v:android.view.View):List<android.view.View> = listOf(v)+
                    if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
                val all=views(activity!!.window.decorView)
                preview=all.filterIsInstance<OfferTagView>().single()
                toggle=all.filterIsInstance<android.widget.Switch>().single{it.text.toString()=="Animar chamas"}
                assertTrue(preview!!.isAttachedToWindow)
                assertTrue(toggle!!.isChecked)
            }
            fun snapshot():IntArray {
                var pixels=IntArray(0)
                instrumentation.runOnMainSync {
                    val v=preview!!
                    assertTrue(v.width>0 && v.height>0)
                    val bitmap=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(Color.rgb(235,241,249));v.draw(canvas)
                    pixels=IntArray(bitmap.width*bitmap.height)
                    bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    bitmap.recycle()
                }
                return pixels
            }
            val first=snapshot()
            Thread.sleep(720)
            assertFalse("Visible flames change over time",first.contentEquals(snapshot()))
            instrumentation.runOnMainSync {toggle!!.performClick()}
            Thread.sleep(200)
            val stopped=snapshot()
            Thread.sleep(450)
            assertTrue("Disabled flames stay still",stopped.contentEquals(snapshot()))
        } finally {
            activity?.let{instrumentation.runOnMainSync{it.finish()}}
            if(oldScale.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))shell("settings put global animator_duration_scale $oldScale")
            else shell("settings delete global animator_duration_scale")
            val edit=prefs.edit().clear()
            original.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v);is Long->edit.putLong(k,v)}}
            edit.commit()
        }
    }
}

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
        instrumentation.runOnMainSync {
            val view=OfferTagView(context)
            for((label,shown) in listOf("RECUSAR" to "● RECUSAR","ATENÇÃO" to "● ANALISAR","ACEITAR" to "● BOA")) {
                view.setOffer(label,19.0,12.0,1.0641666667,40.3263157895,"4.99",Color.RED,"UBER",12.77)
                val badge=view.getChildAt(0) as android.widget.TextView
                assertEquals(shown,badge.text.toString())
                assertNotNull(badge.background)
                assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,badge.layoutParams.width)
                val width=(310*context.resources.displayMetrics.density).toInt()
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
                assertTrue(badge.measuredWidth<width)
                assertTrue(view.contentDescription.toString().contains("R$ 1,06/km"))
                assertTrue(view.contentDescription.toString().contains("R$ 40,33/h"))
            }
        }
    }
}

package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

class OfferCalculationTest {
    @Test fun onlyTheOfficialNinetyNineDriverPackageIsRecognized() {
        assertEquals("99", DriverPackages.name("com.app99.driver"))
        assertNull(DriverPackages.name("com.taxis99"))
        assertFalse(IncomingOfferPolicy.isIncoming("com.taxis99", "R$11,59 9min (3km) 22min (8,2km) Aceitar"))
    }
    @Test fun ninetyNineNegotiationUsesAcceptPriceNotSuggestedCounteroffers() {
        val text="""Negocia · Pgto. no app
R$11,59
4,96 · 152 corridas · Perfil Essencial
9min (3km)
22min (8,2km)
Aceitar por R$11,59
R$12,17   R$12,75   R$13,33"""
        assertTrue(IncomingOfferPolicy.isIncoming("com.app99.driver", text))
        val offer = OfferTextParser.parse(text, "com.app99.driver")!!
        assertEquals(11.59, offer.fare, 0.000001)
        assertEquals(9.0, offer.pickupMinutes!!, 0.000001)
        assertEquals(31.0, offer.minutes, 0.000001)
        assertEquals(11.2, offer.pickupKm + offer.tripKm, 0.000001)
        assertEquals("4.96", offer.passengerRating)
        val result = TripCalculator.calculate(offer.fare, offer.pickupKm, offer.tripKm, offer.minutes)!!
        assertEquals(11.59 / 11.2, result.perKm, 0.000001)
        assertEquals(11.59 * 60 / 31, result.perHour, 0.000001)
        assertEquals(11.59, OfferTextParser.fareOrNull(text, "com.app99.driver")!!, 0.000001)
        assertEquals(11.59, OfferTextParser.parse(text, "com.didiglobal.driver")!!.fare, 0.000001)
        val ocrMissingComma = text.replace("4,96 · 152 corridas", "496. 152 corridas")
        assertEquals("4.96", OfferTextParser.parse(ocrMissingComma, "com.app99.driver")!!.passengerRating)
        assertNull("Do not apply the 99-only rule to another app", OfferTextParser.parse(text, "com.ubercab.driver"))
    }
    @Test fun ninetyNineAmbiguousNegotiationNeverInventsFare() {
        val route="9min (3km) 22min (8,2km)"
        assertNull(OfferTextParser.parse("R$11,59 $route Aceitar R$12,17 R$12,75", "com.app99.driver"))
        assertNull(OfferTextParser.parse("R$10,59 $route Aceitar por R$11,59 R$12,17", "com.app99.driver"))
        assertNull(OfferTextParser.parse("R$11,59 $route Aceitar por R$11,59 Aceitar por R$12,17", "com.app99.driver"))
        assertNull(OfferTextParser.fareOrNull("R$11,59 R$12,17", "com.app99.driver"))
    }
    @Test fun referenceOffer() {
        val result = TripCalculator.calculate(18.40, 1.4, 11.2, 28.0)!!
        assertEquals(12.6, result.totalKm, 0.000001)
        assertEquals(1.46031746, result.perKm, 0.000001)
        assertEquals(39.42857143, result.perHour, 0.000001)
    }
    @Test fun bothLegsWithoutParentheses() {
        val offer = OfferTextParser.parse("R$ 18,40 3 min 600 m 25 min 12 km Aceitar")!!
        assertEquals(0.6, offer.pickupKm, 0.000001)
        assertEquals(12.0, offer.tripKm, 0.000001)
        assertEquals(28.0, offer.minutes, 0.000001)
    }
    @Test fun summaryIsNotCountedTwice() {
        val offer = OfferTextParser.parse("R$ 18,40 28 min • 12,6 km 3 min (600 m) 25 min (12 km) Aceitar")!!
        assertEquals(12.6, offer.pickupKm + offer.tripKm, 0.000001)
        assertEquals(28.0, offer.minutes, 0.000001)
    }
    @Test fun decimalDotAndBrazilianThousands() {
        assertEquals(18.40, OfferTextParser.parse("R$ 18.40 28 min • 12,6 km")!!.fare, 0.000001)
        assertEquals(1234.56, OfferTextParser.parse("R$ 1.234,56 28 min • 12,6 km")!!.fare, 0.000001)
    }
    @Test fun ambiguousOrPartialCardsAreRejected() {
        assertNull(OfferTextParser.parse("R$ 18,40 R$ 3,00 28 min • 12,6 km"))
        assertNull(OfferTextParser.parse("R$ 18,40 3 min 1 km 12 km"))
    }
    @Test fun fareIsNotConfusedWithRateOrBonus() {
        assertEquals(18.40, OfferTextParser.parse("R$ 18,40 R$ 1,46/km 28 min • 12,6 km")!!.fare, 0.000001)
        assertEquals(18.40, OfferTextParser.parse("R $ 18,40 + R$ 3,00 28 min • 12,6 km")!!.fare, 0.000001)
    }
    @Test fun updateRequiresSamePackageSignerAndNewerVersion() {
        assertTrue(UpdatePolicy.isCompatibleUpdate("app",4,setOf("abc"),"app",5,"ABC"))
        assertFalse(UpdatePolicy.isCompatibleUpdate("app.teste",4,setOf("abc"),"app",5,"abc"))
        assertFalse(UpdatePolicy.isCompatibleUpdate("app",4,setOf("abc"),"app",5,"other"))
        assertFalse(UpdatePolicy.isCompatibleUpdate("app",4,setOf("abc"),"app",4,"abc"))
    }
    @Test fun arrivalTimeAndRatingAreReadWithoutInventingMissingData() {
        val detailed = OfferTextParser.parse("R$ 18,40 3 min (1,4 km) 25 min (11,2 km) Nota 4,95 Aceitar")!!
        assertEquals(3.0, detailed.pickupMinutes!!, 0.000001)
        assertEquals(28.0, detailed.minutes, 0.000001)
        assertEquals("4.95", detailed.passengerRating)
        val summary = OfferTextParser.parse("R$ 18,40 28 min • 12,6 km Aceitar")!!
        assertNull(summary.pickupMinutes)
        assertNull(summary.passengerRating)
    }
    @Test fun invalidInputsAreRejected() {
        assertNull(TripCalculator.calculate(Double.NaN, 0.0, 12.6, 28.0))
        assertNull(TripCalculator.calculate(18.4, 0.0, 12.6, Double.POSITIVE_INFINITY))
        assertNull(TripCalculator.calculate(18.4, 0.0, 0.0, 28.0))
    }
    @Test fun landscapeUberOfferWithIncludedBonusesAndIconlessRating() {
        val offer=OfferTextParser.parse("""Priority
R$ 28,81
31 min • 19.1 km • Est. de R$1,51/km
4,76 (164) Verificado
+R$ 6,00 incluído
+ R$ 3,57 incluída para retirada prioritária
6 min (2.2 km)
Rua Angela Ganz
25 minutos (16.9 km)
Rua Vicente Alves Padilha
Tenho interesse""")!!
        assertEquals("4.76",offer.passengerRating)
        assertEquals(28.81,offer.fare,0.000001)
        assertEquals(31.0,offer.minutes,0.000001)
        assertEquals(19.1,offer.pickupKm+offer.tripKm,0.000001)
        val r=TripCalculator.calculate(offer.fare,offer.pickupKm,offer.tripKm,offer.minutes)!!
        assertEquals(55.76129,r.perHour,0.00001)
    }
    @Test fun narrowSplitPaneOfferWithWrappedRate() {
        val offer=OfferTextParser.parse("""R$ 8,96
18 min • 6.6 km • Est. de R$1,36/
km
4,90 (177) Verificado
4 min (1.3 km)
1 parada
14 minutos (5.3 km)
Aceitar""")!!
        assertEquals("4.90",offer.passengerRating)
        assertEquals(18.0,offer.minutes,0.000001)
        assertEquals(6.6,offer.pickupKm+offer.tripKm,0.000001)
        assertNull(OfferTextParser.parse("R$ 8,96 R$3,89 18 min • 6.6 km Aceitar"))
    }
    @Test fun fullScreen99WithoutAcceptAndWithDynamicBaseFare() {
        val text="""Não afeta a TA
R$11,90 1,2x
R$2,95 Tarifa base dinâmica inicial.
4,79 · +999 corridas · Perfil Premium
10min (4,4km)
Rua de teste A
12min (6km)
Rua de teste B"""
        assertTrue(IncomingOfferPolicy.isIncoming("com.app99.driver",text))
        assertFalse(IncomingOfferPolicy.isIncoming("com.ubercab.driver",text))
        val o=OfferTextParser.parse(text)!!
        assertEquals(11.90,o.fare,0.000001);assertEquals(22.0,o.minutes,0.000001)
        assertEquals(10.4,o.pickupKm+o.tripKm,0.000001);assertEquals("4.79",o.passengerRating)
        assertFalse(IncomingOfferPolicy.isIncoming("com.app99.driver",text+" Em viagem"))
    }
    @Test fun floatingUberAndSmallWindowReferences() {
        for((fare,summary,legs,note) in listOf(
            listOf("17,37","26 min · 16.4 km","12 min (9.3 km) 14 minutos (7.1 km)","4,98 (303)"),
            listOf("11,03","14 min · 10.8 km","4 min (2.9 km) 10 minutos (7.9 km)","4,92 (120)"))) {
            val text="R$ $fare $summary Est. de R$1,02/km $note $legs Tenho interesse"
            assertTrue(IncomingOfferPolicy.isIncoming("com.ubercab.driver",text))
            assertNotNull(OfferTextParser.parse(text))
        }
    }
    @Test fun pickupAloneCannotBecomeTheWholeTrip() {
        for(partial in listOf("R$ 12,77 8 min (5.0 km) Nota 4,99 Tenho interesse", "R$ 12,77 8 min 5.0 km Nota 4,99 Aceitar")) {
            assertEquals(12.77,OfferTextParser.fareOrNull(partial)!!,0.000001)
            assertNull("Pickup alone must not produce a GOOD trip",OfferTextParser.parse(partial))
        }
    }
    @Test fun screenshotTotalsAreUsedForBothOffers() {
        val first=OfferTextParser.parse("R$ 12,77 19 min • 12.0 km Est. de R$1,06/km 4,99 (1763) 8 min (5.0 km) 11 minutos (7.0 km) Tenho interesse")!!
        val r=TripCalculator.calculate(first.fare,first.pickupKm,first.tripKm,first.minutes)!!
        assertEquals(19.0,r.minutes,0.000001);assertEquals(12.0,r.totalKm,0.000001)
        assertEquals(1.0641666667,r.perKm,0.000001);assertEquals(40.3263157895,r.perHour,0.000001)
        assertEquals(Rating.BAD,r.rating)
        val second=OfferTextParser.parse("R$ 12,02 19 min • 11.4 km 8 min (4.4 km) 11 minutos (7.0 km) Aceitar")!!
        val s=TripCalculator.calculate(second.fare,second.pickupKm,second.tripKm,second.minutes)!!
        assertEquals(1.0543859649,s.perKm,0.000001);assertEquals(37.9578947368,s.perHour,0.000001)
    }
    @Test fun summaryWithoutSeparatorsAndWithOneDetailStillKeepsTheTotal() {
        val offer=OfferTextParser.parse("R$ 12,77 19 min 12.0 km Est. de R$1,06/km 8 min (5.0 km) Tenho interesse")!!
        assertEquals(19.0,offer.minutes,0.000001)
        assertEquals(12.0,offer.pickupKm+offer.tripKm,0.000001)
        assertNull("The lone detail could be pickup or destination",offer.pickupMinutes)
        assertNull(OfferTextParser.parse("R$ 12,77 8 min • 5.0 km 8 min (5.0 km) 11 min (7.0 km) Aceitar"))
    }
    @Test fun changingClassificationNeverChangesTheCalculation() {
        val bad=TripCalculator.calculate(12.77,5.0,7.0,19.0,1.5,1.8,50.0,60.0,4.99)!!
        val review=TripCalculator.calculate(12.77,5.0,7.0,19.0,1.0,1.2,30.0,60.0,4.99)!!
        val good=TripCalculator.calculate(12.77,5.0,7.0,19.0,0.5,1.0,20.0,40.0,4.99)!!
        assertEquals(listOf(Rating.BAD,Rating.REVIEW,Rating.GOOD),listOf(bad,review,good).map{it.rating})
        listOf(review,good).forEach {
            assertEquals(bad.totalKm,it.totalKm,0.0);assertEquals(bad.minutes,it.minutes,0.0)
            assertEquals(bad.perKm,it.perKm,0.0);assertEquals(bad.perHour,it.perHour,0.0)
        }
    }
}

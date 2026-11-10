package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

class OfferCalculationTest {
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
        assertEquals(18.40, OfferTextParser.parse("R$ 18.40 28 min 12,6 km")!!.fare, 0.000001)
        assertEquals(1234.56, OfferTextParser.parse("R$ 1.234,56 28 min 12,6 km")!!.fare, 0.000001)
    }
    @Test fun ambiguousOrPartialCardsAreRejected() {
        assertNull(OfferTextParser.parse("R$ 18,40 R$ 3,00 28 min 12,6 km"))
        assertNull(OfferTextParser.parse("R$ 18,40 3 min 1 km 12 km"))
    }
    @Test fun fareIsNotConfusedWithRateOrBonus() {
        assertEquals(18.40, OfferTextParser.parse("R$ 18,40 R$ 1,46/km 28 min 12,6 km")!!.fare, 0.000001)
        assertEquals(18.40, OfferTextParser.parse("R $ 18,40 + R$ 3,00 28 min 12,6 km")!!.fare, 0.000001)
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
}

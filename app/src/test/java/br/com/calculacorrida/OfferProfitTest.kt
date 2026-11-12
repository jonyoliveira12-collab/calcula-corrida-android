package br.com.calculacorrida

import org.junit.Assert.*
import org.junit.Test

class OfferProfitTest {
    @Test fun includesTheWholeRouteAndKeepsNegativeMarginsVisible() {
        val profit = OfferProfit.estimate(20.0, 10.0, .65)!!
        assertEquals(13.5, profit.amount, .000001)
        assertEquals(67.5, profit.percent, .000001)
        val loss = OfferProfit.estimate(5.0, 10.0, .65)!!
        assertEquals(-1.5, loss.amount, .000001)
        assertEquals(-30.0, loss.percent, .000001)
        assertEquals(100.0, OfferProfit.estimate(20.0, 10.0, 0.0)!!.percent, .000001)
    }
    @Test fun invalidInputsDoNotProduceAnEstimate() {
        assertNull(OfferProfit.estimate(0.0,10.0,.65))
        assertNull(OfferProfit.estimate(20.0,10.0,-1.0))
        assertNull(OfferProfit.estimate(Double.NaN,10.0,.65))
        assertNull(OfferProfit.estimate(20.0,Double.POSITIVE_INFINITY,.65))
    }
}

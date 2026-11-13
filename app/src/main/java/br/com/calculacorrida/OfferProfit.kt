package br.com.calculacorrida

/** Estimate after the configured distance cost, before any unconfigured expenses. */
object OfferProfit {
    data class Estimate(val amount: Double, val percent: Double)
    fun estimate(fare: Double, totalKm: Double, costPerKm: Double): Estimate? {
        if (!fare.isFinite() || !totalKm.isFinite() || !costPerKm.isFinite() || fare <= 0 || totalKm < 0 || costPerKm < 0) return null
        val amount = fare - totalKm * costPerKm
        val percent = amount / fare * 100
        return if (amount.isFinite() && percent.isFinite()) Estimate(amount, percent) else null
    }
}

package br.com.calculacorrida

enum class Rating { BAD, REVIEW, GOOD }
data class TripResult(val fare: Double, val pickupKm: Double, val tripKm: Double, val minutes: Double, val totalKm: Double, val perKm: Double, val perHour: Double, val rating: Rating, val reason: String)
object TripCalculator {
    fun calculate(fare: Double, pickupKm: Double, tripKm: Double, minutes: Double, minPerKm: Double, minPerHour: Double): TripResult? {
        if (fare <= 0 || pickupKm < 0 || tripKm <= 0 || minutes <= 0) return null
        val km = pickupKm + tripKm
        if (km <= 0) return null
        val perKm = fare / km
        val perHour = fare * 60.0 / minutes
        val rating = when {
            perKm >= minPerKm && perHour >= minPerHour -> Rating.GOOD
            perKm < minPerKm * 0.85 || perHour < minPerHour * 0.85 -> Rating.BAD
            else -> Rating.REVIEW
        }
        val reason = when (rating) {
            Rating.GOOD -> "Atinge os mínimos de valor por km e por hora."
            Rating.REVIEW -> "Está perto dos limites definidos. Avalie as condições."
            Rating.BAD -> "Fica abaixo de pelo menos um dos limites definidos."
        }
        return TripResult(fare,pickupKm,tripKm,minutes,km,perKm,perHour,rating,reason)
    }
}

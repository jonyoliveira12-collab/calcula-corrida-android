package br.com.calculacorrida

enum class Rating { BAD, REVIEW, GOOD }

data class TripResult(
    val fare: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val minutes: Double,
    val totalKm: Double,
    val perKm: Double,
    val perHour: Double,
    val rating: Rating,
    val reason: String,
    val passengerRating: Double? = null
)

object TripCalculator {
    fun calculate(
        fare: Double,
        pickupKm: Double,
        tripKm: Double,
        minutes: Double,
        minPerKm: Double,
        minPerHour: Double,
        passengerRating: Double? = null,
        minPassengerRating: Double = 4.80,
        goodPassengerRating: Double = 4.90
    ): TripResult? {
        if (fare <= 0 || pickupKm < 0 || tripKm <= 0 || minutes <= 0) return null
        val totalKm = pickupKm + tripKm
        if (totalKm <= 0) return null

        // Os dois indicadores usam a rota e o tempo completos, incluindo o embarque.
        val perKm = fare / totalKm
        val perHour = fare * 60.0 / minutes

        val lowRating = passengerRating != null && passengerRating < minPassengerRating
        val goodRating = passengerRating == null || passengerRating >= goodPassengerRating

        val classification = when {
            perKm < minPerKm * 0.85 ||
                perHour < minPerHour * 0.85 ||
                lowRating -> Rating.BAD

            perKm >= minPerKm &&
                perHour >= minPerHour &&
                goodRating -> Rating.GOOD

            else -> Rating.REVIEW
        }

        val reason = when {
            lowRating -> "Nota do passageiro abaixo do mínimo configurado."
            passengerRating != null && passengerRating < goodPassengerRating ->
                "Nota do passageiro na faixa de atenção."
            passengerRating == null ->
                "Nota do passageiro não identificada; classificação pelos valores e tempo."
            classification == Rating.GOOD ->
                "Atinge os mínimos de valor por km, por hora e nota."
            classification == Rating.REVIEW ->
                "Está perto dos limites definidos. Avalie as condições."
            else -> "Fica abaixo de pelo menos um dos limites definidos."
        }

        return TripResult(
            fare, pickupKm, tripKm, minutes, totalKm,
            perKm, perHour, classification, reason, passengerRating
        )
    }
}

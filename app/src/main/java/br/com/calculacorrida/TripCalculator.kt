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
        badPerKm: Double = 1.50,
        goodPerKm: Double = 1.80,
        badPerHour: Double = 50.0,
        goodPerHour: Double = 60.0,
        passengerRating: Double? = null,
        minPassengerRating: Double = 4.80,
        goodPassengerRating: Double = 4.90
    ): TripResult? {
        if (listOf(fare, pickupKm, tripKm, minutes, badPerKm, goodPerKm, badPerHour, goodPerHour).any { !it.isFinite() }) return null
        if (passengerRating != null && (!passengerRating.isFinite() || passengerRating !in 0.0..5.0)) return null
        if (fare <= 0 || pickupKm < 0 || tripKm <= 0 || minutes <= 0) return null
        val totalKm = pickupKm + tripKm

        // Indicadores da faixa usam o percurso total exibido na oferta:
        // distância de chegada ao passageiro + distância da viagem.
        val perKm = fare / totalKm
        val perHour = fare * 60.0 / minutes

        val lowRating = passengerRating != null && passengerRating < minPassengerRating
        val ratingIsGood = passengerRating == null || passengerRating >= goodPassengerRating

        val classification = when {
            perKm < badPerKm || perHour < badPerHour || lowRating -> Rating.BAD
            perKm >= goodPerKm && perHour >= goodPerHour && ratingIsGood -> Rating.GOOD
            else -> Rating.REVIEW
        }

        val reason = when {
            lowRating -> "Nota abaixo de " + String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", minPassengerRating) + "."
            perKm < badPerKm -> "Ganho por km abaixo do seu limite."
            perHour < badPerHour -> "Ganho por hora abaixo do seu limite."
            passengerRating != null && passengerRating < goodPassengerRating ->
                "Nota do passageiro na faixa de atenção."
            passengerRating == null ->
                "Nota não identificada; classificação pelos valores e tempo."
            classification == Rating.GOOD ->
                "Atinge os mínimos de km, hora e nota."
            classification == Rating.REVIEW ->
                "Está na faixa de atenção. Avalie as condições."
            else -> "Abaixo de pelo menos um dos limites definidos."
        }

        return TripResult(
            fare, pickupKm, tripKm, minutes, totalKm,
            perKm, perHour, classification, reason, passengerRating
        )
    }
}

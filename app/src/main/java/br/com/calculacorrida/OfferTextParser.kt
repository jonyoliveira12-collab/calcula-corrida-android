package br.com.calculacorrida

object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?,
        val passengerTrips: String?
    )

    fun parse(raw: String): ParsedOffer? {
        val text = raw.lowercase().replace('\u00a0', ' ').replace(',', '.')
        val fare = Regex("""r\$\s*([0-9]+(?:\.[0-9]{1,2})?)""")
            .find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null

        val ratingMatch = Regex("""(?:★|⭐)\s*([0-5]\.[0-9]{1,2})\s*\(?\s*([0-9]{1,6})?\s*\)?""")
            .find(text) ?: Regex("""(?:nota|avaliação|rating)\s*[:：]?\s*([0-5]\.[0-9]{1,2})\s*\(?\s*([0-9]{1,6})?\s*\)?""").find(text)
        val rating = ratingMatch?.groupValues?.get(1)
        val trips = ratingMatch?.groupValues?.get(2)?.takeIf { it.isNotBlank() }

        val kmValues = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:km|quil[oô]metros?)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }.filter { it > 0 }.toList()
        if (kmValues.size < 2) return null

        val minuteValues = Regex("""([0-9]+)\s*(?:min|minuto|minutos)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }.filter { it > 0 }.toList()
        if (minuteValues.isEmpty()) return null

        val pickup = kmValues[0]
        val trip = kmValues[1]
        val totalMinutes = minuteValues.first()
        if (pickup > 100 || trip > 300 || totalMinutes > 600) return null
        return ParsedOffer(fare, pickup, trip, totalMinutes, rating, trips)
    }
}

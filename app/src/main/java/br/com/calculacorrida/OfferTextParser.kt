package br.com.calculacorrida

object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?
    )

    // Fail closed: do not calculate when the offer's required fields are ambiguous.
    fun parse(raw: String): ParsedOffer? {
        val text = raw.lowercase().replace('\u00a0', ' ').replace(',', '.')

        val fare = Regex("""r\$\s*([0-9]+(?:\.[0-9]{1,2})?)""")
            .find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it > 0 } ?: return null

        val kmValues = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:km|quil[oô]metros?)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it > 0 }.toList()
        if (kmValues.size < 2) return null

        val minuteValues = Regex("""([0-9]+)\s*(?:min|minuto|minutos)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it > 0 }.toList()
        if (minuteValues.isEmpty()) return null

        // First two distances are pickup and trip; first duration is the offer's total duration.
        // Never sum all OCR matches: Android accessibility/OCR can repeat the same card.
        val pickup = kmValues[0]
        val trip = kmValues[1]
        val totalMinutes = minuteValues.first()
        if (pickup > 100 || trip > 300 || totalMinutes > 600) return null

        val rating = Regex("""(?:★|⭐)\s*([0-5]\.[0-9]{1,2})""")
            .find(text)?.groupValues?.get(1)
            ?: Regex("""(?:nota|avaliação|rating)\s*[:：]?\s*([0-5]\.[0-9]{1,2})""")
                .find(text)?.groupValues?.get(1)

        return ParsedOffer(fare, pickup, trip, totalMinutes, rating)
    }
}

package br.com.calculacorrida

object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?
    )

    /**
     * Conservative parser: only returns an offer when fare, pickup distance,
     * trip distance and total duration can be read without guessing.
     * OCR may repeat text, so duplicate numeric values are removed in order.
     */
    fun parse(raw: String): ParsedOffer? {
        val text = raw.lowercase()
            .replace('\u00a0', ' ')
            .replace(',', '.')

        val fareMatches = Regex("""r\\$\\s*([0-9]+(?:\\.[0-9]{1,2})?)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it > 0 }.toList()
        val fare = fareMatches.firstOrNull() ?: return null

        val kmValues = Regex("""([0-9]+(?:\\.[0-9]+)?)\\s*(?:km|quil[oô]metros?)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it > 0 }.toList()
        // One distance alone cannot tell us whether pickup distance is included.
        if (kmValues.size < 2) return null

        val minuteValues = Regex("""([0-9]+)\\s*(?:min|minuto|minutos)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it > 0 }.toList()
        if (minuteValues.isEmpty()) return null

        // Do not sum every OCR match: accessibility/OCR frequently repeats the same card.
        // Use first pickup + first trip distance and first explicit duration as the conservative total.
        val pickup = kmValues[0]
        val trip = kmValues[1]
        if (pickup > 100 || trip > 300) return null
        val totalMinutes = minuteValues.first()
        if (totalMinutes > 600) return null

        val rating = Regex("""(?:★|⭐)\\s*([0-5]\\.[0-9]{1,2})""")
            .find(text)?.groupValues?.get(1)
            ?: Regex("""(?:nota|avaliação|rating)\\s*[:：]?\\s*([0-5]\\.[0-9]{1,2})""")
                .find(text)?.groupValues?.get(1)

        return ParsedOffer(fare, pickup, trip, totalMinutes, rating)
    }
}

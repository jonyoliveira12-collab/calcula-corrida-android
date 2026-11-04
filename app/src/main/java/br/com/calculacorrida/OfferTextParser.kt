package br.com.calculacorrida

object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?
    )

    fun parse(raw: String): ParsedOffer? {
        val text = raw.lowercase()
            .replace('\u00a0', ' ')
            .replace("，", ",")

        val fare = Regex("""(?:r\$|\$)\s*([0-9]+(?:[.,][0-9]{1,2})?)""")
            .find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: return null

        val km = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(?:km|quil[oô]metros?)""")
            .findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .toList()
        if (km.isEmpty()) return null

        val minuteValues = Regex("""([0-9]+)\s*(?:min|minuto|minutos)""")
            .findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()
        if (minuteValues.isEmpty()) return null

        // In the offer card, pickup and trip legs are listed separately; sum them.
        val pickup = if (km.size >= 2) km.first() else 0.0
        val trip = if (km.size >= 2) km.drop(1).sum() else km.first()
        val totalMinutes = minuteValues.sum()

        val rating = Regex("""(?:★|⭐)\s*([0-5][.,][0-9]{1,2})""")
            .find(text)?.groupValues?.get(1)?.replace(",", ".")
            ?: Regex("""(?:nota|avaliação|rating)\s*[:：]?\s*([0-5][.,][0-9]{1,2})""")
                .find(text)?.groupValues?.get(1)?.replace(",", ".")

        return ParsedOffer(fare, pickup, trip, totalMinutes, rating)
    }
}

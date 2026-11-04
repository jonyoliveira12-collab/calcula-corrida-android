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
            .replace("，", ",")
            .replace("\\u00a0", " ")

        val fareMatch = Regex("""(?:r\\$|\\$)\\s*([0-9]+(?:[.,][0-9]{1,2})?)""")
            .find(text) ?: return null
        val fare = fareMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null

        val km = Regex("""([0-9]+(?:[.,][0-9]+)?)\\s*(?:km|quil[oô]metros?)""")
            .findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .toList()

        val minutes = Regex("""([0-9]+)\\s*(?:min|minuto|minutos)""")
            .findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .firstOrNull() ?: return null

        if (km.isEmpty()) return null
        val pickup = if (km.size >= 2) km[0] else 0.0
        val trip = if (km.size >= 2) km[1] else km[0]
        val rating = Regex("""(?:nota|avalia[cç][aã]o|rating)\\s*[:：]?\\s*([0-5][.,][0-9]{1,2})""")
            .find(text)?.groupValues?.get(1)?.replace(",", ".")
        return ParsedOffer(fare, pickup, trip, minutes, rating)
    }
}

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
        val text = raw.lowercase().replace('\u00a0', ' ').replace("，", ",")
        val fare = Regex("""(?:r\$|\$)\s*([0-9]+(?:[.,][0-9]{1,2})?)""")
            .find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: return null

        val km = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(?:km|quil[oô]metros?)""")
            .findAll(text).mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }.toList()
        if (km.isEmpty()) return null

        val minuteValues = Regex("""([0-9]+)\s*(?:min|minuto|minutos)""")
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        if (minuteValues.isEmpty()) return null

        // Algumas telas mostram o total primeiro e depois os trechos separados.
        val pickup: Double
        val trip: Double
        if (km.size >= 3 && kotlin.math.abs(km.first() - km.drop(1).sum()) < 0.25) {
            pickup = km[1]
            trip = km.drop(2).sum()
        } else if (km.size >= 2) {
            pickup = km[0]
            trip = km.drop(1).sum()
        } else {
            pickup = 0.0
            trip = km[0]
        }

        // O primeiro tempo é o total exibido no resumo da oferta; os demais são etapas.
        val totalMinutes = minuteValues.first()
        val rating = Regex("""(?:★|⭐)\s*([0-5][.,][0-9]{1,2})""")
            .find(text)?.groupValues?.get(1)?.replace(",", ".")
            ?: Regex("""(?:nota|avaliação|rating)\s*[:：]?\s*([0-5][.,][0-9]{1,2})""")
                .find(text)?.groupValues?.get(1)?.replace(",", ".")

        return ParsedOffer(fare, pickup, trip, totalMinutes, rating)
    }
}

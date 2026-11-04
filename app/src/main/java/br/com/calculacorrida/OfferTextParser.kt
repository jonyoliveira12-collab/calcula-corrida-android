package br.com.calculacorrida

object OfferTextParser {
    data class ParsedOffer(val fare: Double, val pickupKm: Double, val tripKm: Double, val minutes: Double)
    fun parse(raw: String): ParsedOffer? {
        val text = raw.lowercase()
        val fare = Regex("""r\$\s*([0-9]+(?:[.,][0-9]{1,2})?)""").find(text)?.groupValues?.get(1)?.replace(",",".")?.toDoubleOrNull() ?: return null
        val km = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*km""").findAll(text).mapNotNull { it.groupValues[1].replace(",",".").toDoubleOrNull() }.toList()
        val mins = Regex("""([0-9]+)\s*(?:min|minuto|minutos)""").findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        if (km.isEmpty() || mins.isEmpty()) return null
        val pickup = if (km.size >= 2) km[0] else 0.0
        val trip = if (km.size >= 2) km[1] else km[0]
        return ParsedOffer(fare,pickup,trip,mins.first())
    }
}

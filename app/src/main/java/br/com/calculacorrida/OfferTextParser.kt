package br.com.calculacorrida

import java.text.Normalizer

/** Extrai preço, distância, duração e nota do texto da oferta (acessibilidade/OCR). */
object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?
    )

    private val moneyRegex = Regex("""R\$\s*([0-9.]+(?:,[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""([0-9]+(?:[,.][0-9]+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val minuteRegex = Regex("""([0-9]+)\s*(?:min(?:uto)?s?)\b""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): ParsedOffer? {
        if (rawText.isBlank()) return null
        val text = Normalizer.normalize(rawText.replace('\n', ' '), Normalizer.Form.NFKC)
        val fare = moneyRegex.find(text)?.groupValues?.get(1)?.let(::toNumber) ?: return null
        val distances = distanceRegex.findAll(text).mapNotNull { toNumber(it.groupValues[1]) }.toList()
        if (distances.isEmpty()) return null
        val minutes = minuteRegex.findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        if (minutes <= 0.0) return null

        val pickupKm = if (distances.size >= 2) distances[0] else 0.0
        val tripKm = if (distances.size >= 2) distances[1] else distances[0]
        if (tripKm <= 0.0) return null

        val rating = Regex("""(?:★|⭐|nota)\s*([0-5](?:[,.][0-9]+)?)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.replace(',', '.')
        return ParsedOffer(fare, pickupKm, tripKm, minutes, rating)
    }

    private fun toNumber(value: String): Double? {
        val normalized = when {
            value.contains(',') && value.contains('.') -> value.replace(".", "").replace(',', '.')
            value.contains(',') -> value.replace(',', '.')
            else -> value
        }
        return normalized.toDoubleOrNull()
    }
}

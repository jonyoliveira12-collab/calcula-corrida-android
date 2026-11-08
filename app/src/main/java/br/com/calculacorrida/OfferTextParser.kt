package br.com.calculacorrida

import java.text.Normalizer

/** Lê valores, trechos, duração e nota nas ofertas dos aplicativos de motorista. */
object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?
    )

    private val moneyRegex = Regex("""R\$\s*([0-9.]+(?:,[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""([0-9]+(?:[,.][0-9]+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val minuteRegex = Regex("""([0-9]+(?:[,.][0-9]+)?)\s*(?:min(?:uto)?s?)\b""", RegexOption.IGNORE_CASE)
    private val legRegex = Regex(
        """([0-9]+(?:[,.][0-9]+)?)\s*min(?:uto)?s?\s*[·•]?\s*(\()\s*([0-9]+(?:[,.][0-9]+)?)\s*(km|m)\b""",
        RegexOption.IGNORE_CASE
    )
    private val summaryRegex = Regex(
        """([0-9]+(?:[,.][0-9]+)?)\s*min(?:uto)?s?\s*[·•]\s*([0-9]+(?:[,.][0-9]+)?)\s*km\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): ParsedOffer? {
        if (rawText.isBlank()) return null
        val text = Normalizer.normalize(rawText.replace('\u00A0', ' '), Normalizer.Form.NFKC)
        val fare = moneyRegex.find(text)?.groupValues?.get(1)?.let(::toNumber) ?: return null

        fun km(value: String, unit: String): Double? {
            val n = toNumber(value) ?: return null
            return if (unit.equals("m", true)) n / 1000.0 else n
        }

        val summary = summaryRegex.find(text)?.let {
            val mins = toNumber(it.groupValues[1])
            val dist = toNumber(it.groupValues[2])
            if (mins != null && dist != null) mins to dist else null
        }
        val parenthesizedLegs = legRegex.findAll(text).mapNotNull {
            val mins = toNumber(it.groupValues[1]) ?: return@mapNotNull null
            val dist = km(it.groupValues[3], it.groupValues[4]) ?: return@mapNotNull null
            if (mins > 0 && dist > 0) mins to dist else null
        }.toList()

        val pickupKm: Double
        val tripKm: Double
        val totalMinutes: Double

        if (summary != null) {
            // O resumo do cartão representa a viagem; o trecho entre parênteses
            // representa a chegada ao passageiro. Não confundir o primeiro trecho com o resumo.
            pickupKm = parenthesizedLegs.firstOrNull()?.second ?: 0.0
            tripKm = summary.second
            totalMinutes = summary.first + (parenthesizedLegs.firstOrNull()?.first ?: 0.0)
        } else if (parenthesizedLegs.size >= 2) {
            pickupKm = parenthesizedLegs.first().second
            tripKm = parenthesizedLegs.drop(1).sumOf { it.second }
            totalMinutes = parenthesizedLegs.sumOf { it.first }
        } else if (parenthesizedLegs.size == 1) {
            pickupKm = 0.0
            tripKm = parenthesizedLegs.first().second
            totalMinutes = parenthesizedLegs.first().first
        } else {
            val distances = distanceRegex.findAll(text).mapNotNull {
                km(it.groupValues[1], it.groupValues[2])
            }.toList()
            val durations = minuteRegex.findAll(text).mapNotNull {
                toNumber(it.groupValues[1])
            }.toList()
            if (distances.isEmpty() || durations.isEmpty()) return null
            pickupKm = 0.0
            tripKm = distances.first()
            totalMinutes = durations.first()
        }

        if (pickupKm + tripKm <= 0.0 || totalMinutes <= 0.0) return null
        return ParsedOffer(fare, pickupKm, tripKm, totalMinutes, extractPassengerRating(text))
    }

    private fun extractPassengerRating(text: String): String? {
        val labeled = Regex(
            """(?:★|⭐|nota(?:\s+do\s+passageiro)?)\s*[:·-]?\s*([0-5](?:[,.][0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)
        if (labeled != null) return normalizeRating(labeled)
        val besideTrips = Regex(
            """([0-5][,.][0-9]{1,2})\s*(?:[·•|]\s*)?[0-9]+\s*corridas?""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)
        if (besideTrips != null) return normalizeRating(besideTrips)
        return null
    }

    private fun normalizeRating(value: String): String? {
        val number = toNumber(value) ?: return null
        return if (number in 0.0..5.0) String.format(java.util.Locale.US, "%.2f", number) else null
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

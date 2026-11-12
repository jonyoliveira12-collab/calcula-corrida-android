package br.com.calculacorrida

import java.text.Normalizer

/** Lê valores, trechos, duração e nota nas ofertas dos aplicativos de motorista. */
object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?,
        val pickupAddress: String? = null,
        val destinationAddress: String? = null
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

        val ifoodDistance = Regex("""(?i)dist[aâ]ncia\s+total\s*[:\n ]*([0-9]+(?:[,.][0-9]+)?)\s*km""").find(text)?.groupValues?.get(1)?.let(::toNumber)
        val ifoodMinutes = Regex("""(?i)tempo\s+aproximado\s+de\s+rota\s*[:\n ]*([0-9]+(?:[,.][0-9]+)?)\s*min""").find(text)?.groupValues?.get(1)?.let(::toNumber)
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

        if (ifoodDistance != null && ifoodMinutes != null) {
            pickupKm = 0.0
            tripKm = ifoodDistance
            totalMinutes = ifoodMinutes
        } else if (summary != null && parenthesizedLegs.size >= 2) {
            // No cartão do Uber mostrado: o resumo (8 min • 3,5 km) já é o TOTAL.
            // As linhas detalhadas separam chegada (3 min/1,4 km) e viagem (5 min/2,1 km).
            // Somar o resumo novamente duplicaria a distância e o tempo de chegada.
            pickupKm = parenthesizedLegs.first().second
            tripKm = parenthesizedLegs.drop(1).sumOf { it.second }
            totalMinutes = parenthesizedLegs.sumOf { it.first }
        } else if (summary != null && parenthesizedLegs.size == 1) {
            pickupKm = parenthesizedLegs.first().second
            tripKm = (summary.second - pickupKm).coerceAtLeast(0.0)
            totalMinutes = summary.first
        } else if (summary != null) {
            pickupKm = 0.0
            tripKm = summary.second
            totalMinutes = summary.first
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
        val legMatches = legRegex.findAll(text).toList()
        val pickupAddress = if (legMatches.size >= 2) {
            extractAddress(text.substring(legMatches[0].range.last + 1, legMatches[1].range.first))
        } else null
        val destinationAddress = if (legMatches.size >= 2) {
            extractAddress(text.substring(legMatches[1].range.last + 1))
        } else null
        return ParsedOffer(fare, pickupKm, tripKm, totalMinutes, extractPassengerRating(text), pickupAddress, destinationAddress)
    }

    private fun extractAddress(segment: String): String? {
        val start = Regex("""(?i)\b(?:rua|r\.|avenida|av\.|alameda|travessa|praça|praca|rodovia|estrada|largo)\b""").find(segment) ?: return null
        val candidate = segment.substring(start.range.first)
            .replace(Regex("""(?i)\b(?:aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b.*$"""), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '·', '•', '-', ':')
        return candidate.takeIf { it.length in 8..140 }
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

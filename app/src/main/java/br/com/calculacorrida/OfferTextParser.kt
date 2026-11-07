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

    private val moneyRegex = Regex("""R\\$\\s*([0-9.]+(?:,[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""([0-9]+(?:[,.][0-9]+)?)\\s*(km|m)\\b""", RegexOption.IGNORE_CASE)
    private val minuteRegex = Regex("""([0-9]+(?:[,.][0-9]+)?)\\s*(?:min(?:uto)?s?)\\b""", RegexOption.IGNORE_CASE)

    // Detecta pares como "1 min (60 m)" e "6 min (3 km)".
    // Em algumas telas do Uber existe um resumo fora dos parênteses seguido
    // dos trechos detalhados entre parênteses; nesse caso, usa só o resumo.
    private val legRegex = Regex(
        """([0-9]+(?:[,.][0-9]+)?)\\s*min(?:uto)?s?\\s*[·•]?\\s*(\\()?\\s*([0-9]+(?:[,.][0-9]+)?)\\s*(km|m)\\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): ParsedOffer? {
        if (rawText.isBlank()) return null
        val text = Normalizer.normalize(rawText.replace('\\u00A0', ' '), Normalizer.Form.NFKC)
        val fare = moneyRegex.find(text)?.groupValues?.get(1)?.let(::toNumber) ?: return null

        val legs = legRegex.findAll(text).mapNotNull { match ->
            val minutes = toNumber(match.groupValues[1]) ?: return@mapNotNull null
            val distance = toNumber(match.groupValues[3]) ?: return@mapNotNull null
            val unit = match.groupValues[4].lowercase()
            val km = if (unit == "m") distance / 1000.0 else distance
            if (minutes <= 0 || km < 0) null
            else Triple(minutes, km, match.groupValues[2].isNotEmpty())
        }.toList()

        val pickupKm: Double
        val tripKm: Double
        val totalMinutes: Double

        if (legs.isNotEmpty()) {
            val summary = legs.firstOrNull { !it.third }
            if (summary != null) {
                // Resumo total da rota: não somar novamente os trechos.
                pickupKm = 0.0
                tripKm = summary.second
                totalMinutes = summary.first
            } else {
                // Cartões como o da 99: embarque + viagem aparecem em linhas separadas.
                if (legs.size == 1) {
                    pickupKm = 0.0
                    tripKm = legs.first().second
                    totalMinutes = legs.first().first
                } else {
                    pickupKm = legs.first().second
                    tripKm = legs.drop(1).sumOf { it.second }
                    totalMinutes = legs.sumOf { it.first }
                }
            }
        } else {
            val distances = distanceRegex.findAll(text).mapNotNull {
                val value = toNumber(it.groupValues[1]) ?: return@mapNotNull null
                if (it.groupValues[2].equals("m", true)) value / 1000.0 else value
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

        val rating = extractPassengerRating(text)
        return ParsedOffer(
            fare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            minutes = totalMinutes,
            passengerRating = rating
        )
    }

    private fun extractPassengerRating(text: String): String? {
        // Formato com estrela ou rótulo explícito.
        val labeled = Regex(
            """(?:★|⭐|nota(?:\\s+do\\s+passageiro)?)\\s*[:·-]?\\s*([0-5](?:[,.][0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)
        if (labeled != null) return normalizeRating(labeled)

        // Formato comum na 99: "4,97 · 498 corridas", sem a palavra Nota.
        val besideTrips = Regex(
            """([0-5][,.][0-9]{1,2})\\s*(?:[·•|]\\s*)?[0-9]+\\s*corridas?""",
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

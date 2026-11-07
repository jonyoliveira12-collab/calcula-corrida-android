package br.com.calculacorrida

import java.text.Normalizer

/** Extrai os dados principais do cartão de oferta (acessibilidade/OCR). */
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
        val distances = distanceRegex.findAll(text)
            .mapNotNull { toNumber(it.groupValues[1]) }
            .toList()
        val durations = minuteRegex.findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()

        if (distances.isEmpty() || durations.isEmpty()) return null

        /*
         * No cartão do Uber, a primeira linha-resumo apresenta a distância e
         * duração totais (ex.: "16 min · 9,9 km"). As linhas abaixo detalham
         * os trechos (ex.: "11 min (7,7 km)" + "5 min (2,2 km)").
         * Usar a primeira distância e duração evita somar o resumo novamente
         * com os trechos, o que inflava o denominador e o tempo.
         */
        val totalKm = distances.first()
        val totalMinutes = durations.first()
        if (totalKm <= 0.0 || totalMinutes <= 0.0) return null

        val rating = Regex("""(?:★|⭐|nota)\s*([0-5](?:[,.][0-9]+)?)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.replace(',', '.')

        // Representa a distância completa em um único campo para o calculador
        // não somar novamente retirada + viagem.
        return ParsedOffer(
            fare = fare,
            pickupKm = 0.0,
            tripKm = totalKm,
            minutes = totalMinutes,
            passengerRating = rating
        )
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

package br.com.calculacorrida

object OfferTextParser {
    data class ParsedOffer(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val minutes: Double,
        val passengerRating: String?,
        val passengerTrips: String?
    )

    fun parse(raw: String): ParsedOffer? {
        val text = raw.lowercase().replace('\u00a0', ' ')

        // 1. VALOR DA CORRIDA - aceita "R$ 15,50" ou "R$ 1.234,56"
        val fareRegex = Regex("""r\$\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d{1,4},\d{2}|\d+\.\d{1,2})""")
        val fareMatch = fareRegex.find(text) ?: return null
        val fareStr = fareMatch.groupValues[1]
            .replace(".", "") // tira separador de milhar
            .replace(",", ".") // vírgula vira ponto decimal
        val fare = fareStr.toDoubleOrNull()?.takeIf { it > 0 } ?: return null

        if (fare < 3.0 || fare > 999.0) return null

        // 2. NOTA E CORRIDAS DO PASSAGEIRO
        val ratingMatch = Regex("""(?:★|⭐)\s*([0-5]\.[0-9]{1,2})\s*\(?\s*([0-9]{1,6})?\s*\)?""")
            .find(text)
            ?: Regex("""(?:nota|avaliação|rating)\s*[:：]?\s*([0-5]\.[0-9]{1,2})\s*\(?\s*([0-9]{1,6})?\s*\)?""")
                .find(text)
        val rating = ratingMatch?.groupValues?.get(1)
        val trips = ratingMatch?.groupValues?.get(2)?.takeIf { it.isNotBlank() }

        // 3. DISTÂNCIAS - agora aceita 1 ou mais km (antes exigia 2)
        val kmRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(?:km|quil[oô]metros?)""")
        val kmText = text // não usar o com vírgula trocada, pegar do original
        val kmValues = kmRegex.findAll(kmText)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .filter { it > 0 }
            .toList()

        if (kmValues.isEmpty()) return null

        // Se tem 2+ km: primeiro = busca, segundo = corrida
        // Se tem só 1 km: é a corrida, busca = 0
        val pickup = if (kmValues.size >= 2) kmValues[0] else 0.0
        val trip = if (kmValues.size >= 2) kmValues[1] else kmValues[0]

        if (trip <= 0) return null

        // 4. TEMPO
        val minRegex = Regex("""([0-9]+)\s*(?:min|minuto|minutos)""")
        val minMatch = minRegex.find(text)
        var minutes = 0.0

        if (minMatch != null) {
            minutes = minMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        } else {
            // Tenta formato "Xh" (ex: "2h" = 120 min)
            val hourRegex = Regex("""(\d{1,2})\s*h\b""")
            val hourMatch = hourRegex.find(text)
            if (hourMatch != null) {
                val h = hourMatch.groupValues[1].toIntOrNull() ?: 0
                minutes = (h * 60).toDouble()
            }
        }

        if (minutes <= 0) minutes = 10.0 // Fallback mínimo

        // Validação de valores irreais
        if (pickup > 100 || trip > 300 || minutes > 600) return null

        return ParsedOffer(fare, pickup, trip, minutes, rating, trips)
    }
}

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
        val destinationAddress: String? = null,
        val pickupMinutes: Double? = null
    )

    /** OCR can confuse the currency glyph with S-cedilla; only repair currency at a line start. */
    fun normalizeOcrText(text:String):String = text.replace(
        Regex("""(?im)(^|\n)([ \t]*)r[ŞşŜŝ][ \t]*(?=[0-9])""")
    ) { it.groupValues[1]+it.groupValues[2]+"R$ " }.replace(
        // Only a standalone decimal price line, never a road such as RS 118.
        Regex("""(?im)(^|\n)([ \t]*)r[ \t]*s[ \t]*(?=[0-9]+[,.][0-9]{2}[ \t]*$)""")
    ) { it.groupValues[1]+it.groupValues[2]+"R$ " }.replace(
        Regex("""(?i)([0-9]+(?:[,.][0-9]+)?)\s*kmn\b""")
    ) { it.groupValues[1]+" km" }

    private val compactCategoryRegex=Regex("""(?im)\b(?:envios(?:\s+moto)?|uber\s+moto|entregas?(?:\s+moto)?)\b|^\s*moto\s*$""")
    private val compactSummaryRegex=Regex("""([0-9]+(?:[,.][0-9]+)?)\s*min(?:uto)?s?\s*[·•]?\s*([0-9]+(?:[,.][0-9]+)?)\s*km\b""",RegexOption.IGNORE_CASE)

    private val moneyRegex = Regex("""R\s*\$\s*((?:[0-9]{1,3}(?:\.[0-9]{3})+|[0-9]+)(?:,[0-9]{1,2})?|[0-9]+\.[0-9]{1,2})(?![0-9.,])""", RegexOption.IGNORE_CASE)
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

    /** A fare alone does not certify that all route legs have been read. */
    fun fareOrNull(rawText:String, sourcePackage:String?=null):Double? =
        parseFare(Normalizer.normalize(rawText.replace('\u00A0',' '),Normalizer.Form.NFKC), sourcePackage)

    private fun parseFare(text:String, sourcePackage:String?):Double? {
        val matches = moneyRegex.findAll(text).filter { match ->
            val after = text.substring(match.range.last + 1).trimStart()
            val before = text.substring(0, match.range.first).trimEnd()
            // A rate or a separately labelled bonus is not the total fare.
            !Regex("""^tarifa\s+base\s+din[aâ]mica""", RegexOption.IGNORE_CASE).containsMatchIn(after) &&
            !Regex("""^(?:/\s*|por\s+)(?:km|h(?:ora)?s?|min)\b""", RegexOption.IGNORE_CASE).containsMatchIn(after) &&
                !Regex("""(?:\+|b[oô]nus\s*[:+]?|adicional\s*[:+]?)$""", RegexOption.IGNORE_CASE).containsMatchIn(before)
        }.mapNotNull { match ->
            val value = toNumber(match.groupValues[1])
            if (value != null && value.isFinite() && value > 0) match to value else null
        }.toList()

        // A 99 negotiation card may also show counteroffer prices. The
        // headline comes before the two route legs; suggested prices after
        // those legs must not replace it or be mistaken for a conflict.
        if (DriverPackages.name(sourcePackage.orEmpty()) == "99") {
            val acceptPrefix = Regex("""\baceitar\s+(?:a\s+corrida\s+)?por\s*[:\-]?\s*$""", RegexOption.IGNORE_CASE)
            val buttons = matches.filter { (match, _) ->
                acceptPrefix.containsMatchIn(text.substring(0, match.range.first).takeLast(80))
            }
            val buttonValues = buttons.map { it.second }.distinct()
            if (buttonValues.size > 1) return null
            val hasAccept=Regex("""\b(?:aceitar|aceite|accept|aceptar|escolher|confirmar)\b""",RegexOption.IGNORE_CASE).containsMatchIn(text)
            // An unqualified action next to several different prices is
            // ambiguous; do not infer which value the driver would receive.
            if(buttonValues.isEmpty() && hasAccept && matches.map{it.second}.distinct().size>1)return null
            val legs = legRegex.findAll(text).toList()
            val firstRoute = (legs.firstOrNull()?.range?.first ?: minuteRegex.find(text)?.range?.first)
            val beforeRoute = if(firstRoute!=null) matches.filter { it.first.range.first < firstRoute }.map { it.second }.distinct() else emptyList()
            if (buttonValues.size == 1) {
                val value = buttonValues.single()
                val candidates = if(firstRoute!=null) beforeRoute else {
                    val buttonStart = buttons.first().first.range.first
                    matches.filter { it.first.range.first < buttonStart }.map { it.second }.distinct()
                }
                if (candidates.any { kotlin.math.abs(it-value)>0.005 }) return null
                return value
            }
            // A price that only appears after the route can be a suggested
            // counteroffer, never assume it is the missing headline fare.
            if(firstRoute!=null && beforeRoute.isEmpty())return null
            // Some cards omit the action from accessibility or OCR. Only
            // recover the headline when a 99-specific offer marker and both
            // route legs are present and exactly one pre-route price is clear.
            val times=minuteRegex.findAll(text).count()
            val distances=distanceRegex.findAll(text).count()
            val offerMarker=IncomingOfferPolicy.hasNinetyNineOfferMarker(text) || IncomingOfferPolicy.hasNinetyNineNegotiationPrices(text)
            if(offerMarker && times>=2 && distances>=2 && beforeRoute.size==1)
                return beforeRoute.single()
            if(times>=2 && distances>=2 && !hasAccept && !offerMarker)return null
        }
        return matches.map { it.second }.distinct().singleOrNull()
    }

    private val estimatedSummaryRegex=Regex(
        """([0-9]+(?:[,.][0-9]+)?)\s*min(?:uto)?s?\s*[·•]?\s*([0-9]+(?:[,.][0-9]+)?)\s*km(?=\s*(?:[·•]\s*)?Est\.?\s*(?:de\s*)?R\s*\$)""",
        RegexOption.IGNORE_CASE
    )
    private val displayedKmRateRegex=Regex("""^\s*(?:[·•]\s*)?Est\.?\s*(?:de\s*)?R\s*\$\s*([0-9]+(?:[,.][0-9]{1,2})?)\s*/\s*km\b""",RegexOption.IGNORE_CASE)
    private val explicitTotalPrefixRegex=Regex(
        """(?:total(?:\s+d[ao]\s+(?:trajeto|viagem|percurso))?|percurso\s+total)\s*[:\-]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String, sourcePackage:String?=null): ParsedOffer? {
        if (rawText.isBlank()) return null
        val text = Normalizer.normalize(rawText.replace('\u00A0', ' '), Normalizer.Form.NFKC)
        val fare = parseFare(text, sourcePackage) ?: return null

        fun km(value: String, unit: String): Double? {
            val n = toNumber(value) ?: return null
            return if (unit.equals("m", true)) n / 1000.0 else n
        }

        // Prefer the Uber total next to its per-km estimate. Accessibility or
        // OCR can expose the pickup as "minutes • km", which looks like a
        // total but is only one leg of the trip.
        // iFood names the complete route explicitly. These totals are only
        // trusted for its allowlisted driver app, never for a lone pickup row.
        val deliveryDistance = Regex("""(?:dist[aâ]ncia|percurso)\s+total\s*[:\-]?\s*([0-9]+(?:[,.][0-9]+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE).findAll(text).toList()
        val deliveryTime = Regex("""tempo\s+(?:aproximado|estimado)\s+d[ae]\s+rota\s*[:\-]?\s*([0-9]+(?:[,.][0-9]+)?)\s*min(?:uto)?s?\b""", RegexOption.IGNORE_CASE).findAll(text).toList()
        val hasDeliveryLabels = DriverPackages.name(sourcePackage.orEmpty()) == "IFOOD" &&
            (deliveryDistance.isNotEmpty() || deliveryTime.isNotEmpty())
        val deliverySummary = if(hasDeliveryLabels) {
            if(deliveryDistance.size != 1 || deliveryTime.size != 1)return null
            val distance=km(deliveryDistance.single().groupValues[1],deliveryDistance.single().groupValues[2]) ?: return null
            val minutes=toNumber(deliveryTime.single().groupValues[1]) ?: return null
            if(distance<=0 || minutes<=0)return null
            minutes to distance
        } else null

        val estimatedSummary = estimatedSummaryRegex.find(text)
        // A header total may lose its bullet in OCR. When two detailed legs
        // exist, retain the separate header so mismatched digits cannot inflate earnings.
        val detailedRows=legRegex.findAll(text).toList()
        val plainHeaderTotal=if(detailedRows.size==2)compactSummaryRegex.findAll(text)
            .filter{candidate->detailedRows.none{candidate.range.first in it.range}}.singleOrNull() else null
        val compactCategory=DriverPackages.name(sourcePackage.orEmpty())=="UBER" && compactCategoryRegex.containsMatchIn(text)
        val summaryMatch = estimatedSummary ?: summaryRegex.find(text) ?: plainHeaderTotal ?: if(compactCategory && minuteRegex.findAll(text).count()==1 && distanceRegex.findAll(text).count()==1)compactSummaryRegex.find(text) else null
        val displayedSummary = summaryMatch?.let {
            val mins = toNumber(it.groupValues[1])
            var dist = toNumber(it.groupValues[2])
            if(DriverPackages.name(sourcePackage.orEmpty())=="UBER" && it==estimatedSummary && dist!=null && dist>0) {
                val rateText=displayedKmRateRegex.find(text.substring(it.range.last+1))?.groupValues?.get(1)
                val rate=rateText?.let(::toNumber)
                if(rate!=null && rate>0 && rateText!=null) {
                    val observed=dist
                    fun precision(raw:String):Double {val separator=raw.indexOfLast{c->c=='.'||c==','};return if(separator<0)1.0 else Math.pow(10.0,-(raw.length-separator-1).toDouble())}
                    val rateHalf=precision(rateText)/2
                    val low=(fare-.005)/(rate+rateHalf)
                    val high=(fare+.005)/(rate-rateHalf).coerceAtLeast(.001)
                    fun consistent(km:Double,unit:Double)=km+unit/2+.002>=low && km-unit/2-.002<=high
                    if(!consistent(observed,precision(it.groupValues[2]))) {
                        // Repair only a lost separator, never infer arbitrary distance.
                        // The displayed R$/km independently corroborates one candidate;
                        // the detailed route rows below must agree as well when present.
                        val digits=it.groupValues[2]
                        val candidates=if(precision(rateText)<=.01 && digits.none{c->c=='.'||c==','})listOf(10.0,100.0).map{factor->observed/factor to 1/factor}.filter{(km,unit)->km>0&&consistent(km,unit)}.map{it.first}else emptyList()
                        dist=candidates.singleOrNull()?:return null
                    }
                }
            }
            if (mins != null && dist != null) mins to dist else null
        }
        if(deliverySummary!=null && displayedSummary!=null &&
            (kotlin.math.abs(deliverySummary.first-displayedSummary.first)>1.0 ||
             kotlin.math.abs(deliverySummary.second-displayedSummary.second)>maxOf(.15,deliverySummary.second*.02)))return null
        val summary = deliverySummary ?: displayedSummary
        val parenthesizedLegs = legRegex.findAll(text).mapNotNull {
            val mins = toNumber(it.groupValues[1]) ?: return@mapNotNull null
            val dist = km(it.groupValues[3], it.groupValues[4]) ?: return@mapNotNull null
            if (mins > 0 && dist > 0) mins to dist else null
        }.toList()

        // Uber Envios/Moto floating cards show a single complete route, without pickup legs.
        // Trust only a named category, an incoming action and exactly one route pair.
        val compactUberSummary = DriverPackages.name(sourcePackage.orEmpty()) == "UBER" &&
            summaryMatch != null && parenthesizedLegs.isEmpty() &&
            IncomingOfferPolicy.isIncoming(sourcePackage.orEmpty(), text) &&
            compactCategoryRegex.containsMatchIn(text.substring(0, summaryMatch.range.first)) &&
            !Regex("""(?i)(?:at[eé]|para)\s+(?:a\s+)?(?:coleta|retirada|embarque)|buscar|chegada""").containsMatchIn(text.substring(0,summaryMatch.range.first)) &&
            compactSummaryRegex.findAll(text).count() == 1 &&
            minuteRegex.findAll(text).count() == 1 && distanceRegex.findAll(text).count() == 1

        if (DriverPackages.name(sourcePackage.orEmpty()) == "UBER" && summaryMatch != null && estimatedSummary == null && parenthesizedLegs.size < 2) {
            val prefix=text.substring(0,summaryMatch.range.first).takeLast(64)
            if(!explicitTotalPrefixRegex.containsMatchIn(prefix) && !compactUberSummary) return null
        }

        val pickupKm: Double
        val tripKm: Double
        val totalMinutes: Double
        var pickupMinutes: Double? = null

        if (summary != null) {
            // The explicit offer summary is the TOTAL, never an additional leg.
            // Detailed rows identify pickup only when both rows are available.
            totalMinutes=summary.first
            if(parenthesizedLegs.size==2) {
                val detailMinutes=parenthesizedLegs.sumOf{it.first}
                val detailKm=parenthesizedLegs.sumOf{it.second}
                if(kotlin.math.abs(detailMinutes-summary.first)>1.0 ||
                    kotlin.math.abs(detailKm-summary.second)>maxOf(0.15,summary.second*0.02))return null
            }
            val first=parenthesizedLegs.firstOrNull()
            if(parenthesizedLegs.size>=2 && first!=null && first.first<summary.first && first.second<summary.second) {
                pickupMinutes=first.first
                pickupKm=first.second
                tripKm=summary.second-first.second
            } else {
                pickupKm=0.0
                tripKm=summary.second
            }
        } else if(parenthesizedLegs.size==2) {
            pickupMinutes=parenthesizedLegs[0].first
            pickupKm=parenthesizedLegs[0].second
            tripKm=parenthesizedLegs[1].second
            totalMinutes=parenthesizedLegs.sumOf{it.first}
        } else if(parenthesizedLegs.isNotEmpty()) {
            // A lone pickup row caused the false GOOD result reported on the tablet.
            // Never promote a partial row to the whole trip; request a complete frame.
            return null
        } else {
            val distances=distanceRegex.findAll(text).mapNotNull{km(it.groupValues[1],it.groupValues[2])}.toList()
            val durations=minuteRegex.findAll(text).mapNotNull{toNumber(it.groupValues[1])}.toList()
            // One unlabelled pair may also be only pickup. Two legs are required.
            if(distances.size!=2 || durations.size!=2)return null
            pickupMinutes=durations[0]
            pickupKm=distances[0]
            tripKm=distances[1]
            totalMinutes=durations.sum()
        }

        if (!pickupKm.isFinite() || !tripKm.isFinite() || !totalMinutes.isFinite() || pickupKm + tripKm <= 0.0 || totalMinutes <= 0.0) return null
        val legMatches = legRegex.findAll(text).toList()
        val pickupAddress = if (legMatches.size >= 2) {
            extractAddress(text.substring(legMatches[0].range.last + 1, legMatches[1].range.first))
        } else null
        val destinationAddress = if (legMatches.size >= 2) {
            extractAddress(text.substring(legMatches[1].range.last + 1))
        } else null
        return ParsedOffer(fare, pickupKm, tripKm, totalMinutes, extractPassengerRating(text,sourcePackage), pickupAddress, destinationAddress, pickupMinutes?.takeIf { it <= totalMinutes })
    }

    private fun extractAddress(segment: String): String? {
        val start = Regex("""(?i)\b(?:rua|r\.|avenida|av\.|alameda|travessa|praça|praca|rodovia|estrada|largo)\b""").find(segment) ?: return null
        val candidate = segment.substring(start.range.first)
            .replace(Regex("""(?i)\b(?:aceitar|aceite|accept|aceptar|escolher|tenho\s+interesse|confirmar)\b.*$"""), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '·', '•', '-', ':')
        return candidate.takeIf { it.length in 8..140 }
    }

    private fun extractPassengerRating(text: String,sourcePackage:String?): String? {
        val labeled = Regex(
            """(?:★|⭐|nota(?:\s+do\s+passageiro)?)\s*[:·-]?\s*([0-5](?:[,.][0-9]{1,2})?)(?![0-9.,])""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)
        if (labeled != null) return normalizeRating(labeled)
        val besideTrips = Regex(
            """(?<![0-9.,])([0-5][,.][0-9]{1,2})\s*(?:[·•|.:\-–—]\s*)?\+?[0-9]+\s*corridas?""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)
        if (besideTrips != null) return normalizeRating(besideTrips)
        // Some OCR engines omit the decimal separator in the compact 99 rating.
        // Only recover it when the digits are immediately tied to a trip count.
        val compactBesideTrips = Regex(
            """(?<![0-9.,])([45])([0-9]{2})\s*[·•|.,]?\s*\+?[0-9]+\s*corridas?\b""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (compactBesideTrips != null) {
            val rating = compactBesideTrips.groupValues[1] + "." + compactBesideTrips.groupValues[2]
            return normalizeRating(rating)
        }
        val withCount = Regex("""(?<![0-9.,])([0-5][,.][0-9]{2})\s*\(\s*[0-9]+\s*\)""").find(text)?.groupValues?.get(1)
        if(withCount!=null)return normalizeRating(withCount)
        if(DriverPackages.name(sourcePackage.orEmpty())=="UBER") {
            val header=text.substring(0,legRegex.find(text)?.range?.first?:text.length)
            val compact=Regex("""(?m)(?:^|[★⭐])\s*([45])([0-9]{2})\s*\(\s*[0-9]{1,6}\s*\)""").find(header)
            if(compact!=null)return normalizeRating(compact.groupValues[1]+"."+compact.groupValues[2])
        }
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

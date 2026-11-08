package br.com.calculacorrida

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Histórico local das ofertas lidas pelo JHONDRIVER.
 * Registra somente dados da oferta e métricas calculadas; não guarda endereços.
 */
object JourneyStore {
    private const val PREFS = "journey"
    private const val KEY_RECORDS = "offers"
    private const val MAX_RECORDS = 500
    private const val DUPLICATE_WINDOW_MS = 90_000L

    @Synchronized
    fun record(
        context: Context,
        offer: OfferTextParser.ParsedOffer,
        result: TripResult,
        driverPackage: String
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val records = readArray(prefs.getString(KEY_RECORDS, null))
        val now = System.currentTimeMillis()
        val signature = listOf(
            offer.fare, offer.pickupKm, offer.tripKm, offer.minutes,
            offer.passengerRating.orEmpty(), offer.pickupAddress.orEmpty(),
            offer.destinationAddress.orEmpty(), driverPackage
        ).joinToString("|")
        if (records.length() > 0) {
            val last = records.optJSONObject(records.length() - 1)
            if (last != null &&
                last.optString("signature") == signature &&
                now - last.optLong("timestamp") < DUPLICATE_WINDOW_MS
            ) return
        }

        val item = JSONObject().apply {
            put("id", now.toString() + "-" + records.length())
            put("timestamp", now)
            put("signature", signature)
            put("app", when {
                driverPackage.contains("uber", true) -> "Uber"
                driverPackage.contains("99") -> "99"
                driverPackage.contains("didi", true) -> "DiDi"
                else -> "Aplicativo"
            })
            put("fare", result.fare)
            put("pickupKm", result.pickupKm)
            put("tripKm", result.tripKm)
            put("totalKm", result.totalKm)
            put("minutes", result.minutes)
            put("perKm", result.perKm)
            put("perHour", result.perHour)
            put("passengerRating", offer.passengerRating ?: "")
            put("pickupAddress", offer.pickupAddress ?: "")
            put("destinationAddress", offer.destinationAddress ?: "")
            put("classification", result.rating.name)
            put("reason", result.reason)
        }
        records.put(item)
        val trimmed = JSONArray()
        val start = (records.length() - MAX_RECORDS).coerceAtLeast(0)
        for (i in start until records.length()) trimmed.put(records.getJSONObject(i))
        prefs.edit().putString(KEY_RECORDS, trimmed.toString()).apply()
    }

    fun getRecords(context: Context): List<JSONObject> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = readArray(prefs.getString(KEY_RECORDS, null))
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .sortedByDescending { it.optLong("timestamp") }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_RECORDS).apply()
    }

    private fun readArray(raw: String?): JSONArray =
        try { if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw) }
        catch (_: Exception) { JSONArray() }
}

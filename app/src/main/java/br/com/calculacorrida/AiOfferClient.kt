package br.com.calculacorrida

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiOfferClient {
    private const val SUPABASE_URL = "https://hyoaomusfpeccdchgoik.supabase.co"
    private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX"
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    fun analyze(context: Context, result: TripResult): String? {
        if (!NetworkGate.isOnline(context)) return null
        val token = SessionManager(context).getAccessToken()?.takeIf { it.isNotBlank() } ?: return null
        val payload = JSONObject().apply {
            put("fare", result.fare)
            put("totalKm", result.totalKm)
            put("minutes", result.minutes)
            put("perKm", result.perKm)
            put("perHour", result.perHour)
            put("passengerRating", result.passengerRating ?: JSONObject.NULL)
            put("classification", result.rating.name)
        }
        val request = Request.Builder()
            .url("$SUPABASE_URL/functions/v1/analyze-offer")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                if (response.isSuccessful) json.optString("insight").takeIf { it.isNotBlank() }
                else null
            }
        } catch (_: Exception) { null }
    }
}
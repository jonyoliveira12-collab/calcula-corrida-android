package br.com.calculacorrida

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiOfferClient {
    private const val SUPABASE_URL="https://hyoaomusfpeccdchgoik.supabase.co"
    // Public client key. Gemini's private key stays exclusively in the Edge Function secrets.
    private const val SUPABASE_PUBLISHABLE_KEY="sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX"
    private val http=OkHttpClient.Builder().connectTimeout(6,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).build()
    data class Analysis(val insight:String?,val status:String)
    fun request(context:Context,result:TripResult):Analysis {
        if(!NetworkGate.isOnline(context)) return Analysis(null,"Sem internet; leitura e cálculos pausados")
        val session=SessionManager(context)
        var token=session.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: return Analysis(null,"Entre na conta para ativar a IA")
        val payload=JSONObject().apply {
            put("fare",result.fare);put("totalKm",result.totalKm);put("minutes",result.minutes)
            put("perKm",result.perKm);put("perHour",result.perHour)
            put("passengerRating",result.passengerRating ?: JSONObject.NULL);put("classification",result.rating.name)
        }
        return try {
            for(attempt in 0..1) {
                val request=Request.Builder().url("$SUPABASE_URL/functions/v1/analyze-offer")
                    .header("apikey",SUPABASE_PUBLISHABLE_KEY).header("Authorization","Bearer $token")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                http.newCall(request).execute().use { response ->
                    if(response.code==401 && attempt==0) {
                        val refreshed=session.refreshAccessToken()
                        if(refreshed!=null) { token=refreshed;return@use }
                    }
                    if(response.isSuccessful) {
                        val json=JSONObject(response.body?.string().orEmpty())
                        val insight=json.optString("insight").trim().takeIf { it.isNotEmpty() }
                        return Analysis(insight,if(insight!=null) "IA conectada" else "IA sem resposta; cálculos locais ativos")
                    }
                    return Analysis(null,when(response.code) {
                        401,403 -> "Entre novamente na conta para a IA"
                        404 -> "Função de IA ainda não publicada na Supabase"
                        503 -> "IA ainda não configurada no servidor"
                        429 -> "IA ocupada; cálculos locais ativos"
                        else -> "IA indisponível; cálculos locais ativos"
                    })
                }
            }
            Analysis(null,"Entre novamente na conta para a IA")
        } catch (_:Exception) { Analysis(null,"IA indisponível; cálculos locais ativos") }
    }
}

package br.com.calculacorrida

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Uses the signed-in driver's session only. No administrator key is distributed. */
class FriendsClient(context: Context, private val capturedToken: String? = null) {
    private val session = SessionManager(context.applicationContext)
    private val identity = identityOf(session.getAccessToken())
    private fun identityOf(token: String?): String? = runCatching {
        val claims = JSONObject(String(android.util.Base64.decode(token!!.split('.')[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)))
        claims.getString("sub") + ":" + claims.getString("session_id")
    }.getOrNull()
    private fun checkAccount(token: String) {
        if (capturedToken == null && (identity == null || identityOf(token) != identity)) error("Entre novamente para usar Amigos.")
    }
    fun call(action: String, data: JSONObject = JSONObject()): JSONObject {
        var token = capturedToken ?: session.getAccessToken() ?: error("Entre novamente para usar Amigos.")
        repeat(2) { attempt ->
            checkAccount(token)
            val request = Request.Builder().url("https://hyoaomusfpeccdchgoik.supabase.co/rest/v1/rpc/jd_friends")
                .header("apikey", "sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX")
                .header("Authorization", "Bearer $token")
                .post(JSONObject().put("p_action", action).put("p_data", data).toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401 && attempt == 0 && capturedToken == null) {
                    token = session.refreshAccessToken() ?: error("Entre novamente para usar Amigos.")
                } else {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    if (!response.isSuccessful) error(json?.optString("message")?.takeIf { it.isNotBlank() } ?: "Não foi possível conectar. Tente novamente.")
                    return json ?: error("Resposta indisponível. Tente novamente.")
                }
            }
        }
        error("Entre novamente para usar Amigos.")
    }
    companion object {
        private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    }
}

/** Pure validation shared with device-independent tests. All time comparisons use age, not phone clock. */
object FriendLocationPolicy {
    fun valid(lat: Double, lng: Double, accuracy: Float, ageMs: Long): Boolean =
        lat.isFinite() && lng.isFinite() && accuracy.isFinite() && lat in -90.0..90.0 &&
            lng in -180.0..180.0 && accuracy in 0f..100000f && ageMs in 0..45000
    fun remaining(untilMs: Long, serverNow: Long): Long = (untilMs - serverNow).coerceIn(0, 4 * 60 * 60 * 1000L)
}

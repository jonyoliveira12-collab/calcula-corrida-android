package br.com.calculacorrida

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SessionManager(context:Context) {
    private val prefs:SharedPreferences=context.getSharedPreferences("user_session",Context.MODE_PRIVATE)
    fun saveUser(email:String,accessToken:String?=null,refreshToken:String?=null) {
        prefs.edit().putBoolean("logged_in",true).putString("email",email)
            .putString("access_token",accessToken).putString("refresh_token",refreshToken).apply()
    }
    fun getAccessToken():String?=prefs.getString("access_token",null)
    fun isLoggedIn():Boolean=prefs.getBoolean("logged_in",false)
    fun getEmail():String?=prefs.getString("email",null)
    fun logout() { synchronized(refreshLock) { prefs.edit().clear().apply() } }
    /** Called only on an IO thread, after a 401. Refresh rotation is serialized process-wide. */
    fun refreshAccessToken():String? = synchronized(refreshLock) {
        val refresh=prefs.getString("refresh_token",null)?.takeIf { it.isNotBlank() } ?: return@synchronized null
        val expectedToken=getAccessToken()
        try {
            val request=Request.Builder().url("https://hyoaomusfpeccdchgoik.supabase.co/auth/v1/token?grant_type=refresh_token")
                .header("apikey","sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX")
                .post(JSONObject().put("refresh_token",refresh).toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use { response ->
                if(!response.isSuccessful) return@synchronized null
                val json=JSONObject(response.body?.string().orEmpty())
                val access=json.optString("access_token").takeIf { it.isNotBlank() } ?: return@synchronized null
                val nextRefresh=json.optString("refresh_token").takeIf { it.isNotBlank() } ?: return@synchronized null
                if(!isLoggedIn() || getAccessToken()!=expectedToken) return@synchronized null
                prefs.edit().putString("access_token",access).putString("refresh_token",nextRefresh).apply()
                access
            }
        } catch (_:Exception) { null }
    }
    companion object {
        private val refreshLock=Any()
        private val http=OkHttpClient.Builder().callTimeout(10,TimeUnit.SECONDS).build()
    }
}

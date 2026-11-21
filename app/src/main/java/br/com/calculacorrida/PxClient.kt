package br.com.calculacorrida

import android.content.Context
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal interface PxGateway { fun call(action:String,data:JSONObject=JSONObject()):JSONObject; fun currentAccount():Boolean }
internal object PxDependencies { @Volatile var gateway:(Context)->PxGateway={PxClient(it)} }
internal class PxClient(context:Context):PxGateway {
    private val session=SessionManager(context.applicationContext)
    private val account=identity(session.getAccessToken())
    override fun currentAccount()=account!=null&&session.isLoggedIn()&&identity(session.getAccessToken())==account
    override fun call(action:String,data:JSONObject):JSONObject {
        if(!currentAccount())error("Entre novamente para usar PX.")
        var token=session.getAccessToken() ?: error("Entre novamente.")
        repeat(2){attempt->
            if(!currentAccount())error("Sua conta mudou. Entre novamente no canal.")
            val request=Request.Builder().url("https://hyoaomusfpeccdchgoik.supabase.co/functions/v1/jd-px")
                .header("apikey","sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX").header("Authorization","Bearer $token")
                .post(JSONObject(data.toString()).put("action",action).toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use { response->
                val json=runCatching{JSONObject(response.body?.string().orEmpty())}.getOrNull()
                if(response.code==401&&attempt==0){token=session.refreshAccessToken() ?: error("Entre novamente para usar PX.")}
                else {if(!response.isSuccessful)error(json?.optString("error")?.takeIf{it.isNotBlank()} ?: "Rádio indisponível. Tente novamente.");return json ?: error("Resposta inválida do rádio.")}
            }
        }
        error("Entre novamente para usar PX.")
    }
    companion object {
        fun identity(token:String?):String?=runCatching {val json=JSONObject(String(Base64.decode(token!!.split('.')[1],Base64.URL_SAFE or Base64.NO_WRAP)));json.getString("sub")+":"+json.getString("session_id")}.getOrNull()
        private val http=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS).build()
    }
}

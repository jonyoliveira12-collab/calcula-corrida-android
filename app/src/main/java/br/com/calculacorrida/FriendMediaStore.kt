package br.com.calculacorrida

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

internal interface FriendMediaGateway {
    fun uploadPhoto(uri:Uri):String
    fun uploadAudio(file:java.io.File):String
    fun download(path:String):ByteArray
}
internal object FriendMediaDependencies {
    @Volatile var create:(Context)->FriendMediaGateway = { FriendMediaStore(it) }
}

/** Private authenticated Storage; no public or reusable signed media URLs. Call only on IO threads. */
internal class FriendMediaStore(context:Context):FriendMediaGateway {
    private val app=context.applicationContext
    private val session=SessionManager(app)
    private val expected=identity(session.getAccessToken())
    private fun identity(token:String?):String?=runCatching {
        val jwt=JSONObject(String(Base64.decode(token!!.split('.')[1],Base64.URL_SAFE or Base64.NO_WRAP)))
        UUID.fromString(jwt.getString("sub")).toString()+":"+UUID.fromString(jwt.getString("session_id")).toString()
    }.getOrNull()
    private fun check():String {
        val token=session.getAccessToken() ?: error("Entre novamente para usar fotos e áudios.")
        check(expected!=null && identity(token)==expected){"A conta foi alterada. Entre novamente."}
        return token
    }
    private fun request(path:String,bytes:ByteArray?=null,mime:String?=null):ByteArray {
        require(PATH.matches(path)){"Arquivo inválido."}
        repeat(2){attempt->
            val token=check()
            val url=if(bytes==null)"https://hyoaomusfpeccdchgoik.supabase.co/functions/v1/jd-friend-media" else BASE+"object/$BUCKET/$path"
            val builder=Request.Builder().url(url).header("apikey",KEY).header("Authorization","Bearer $token")
            if(bytes!=null) {require(bytes.size in 1..MAX);builder.header("x-upsert","false").post(bytes.toRequestBody(mime!!.toMediaType()))}
            else builder.post(JSONObject().put("path",path).toString().toRequestBody("application/json".toMediaType()))
            http.newCall(builder.build()).execute().use {response->
                if(response.code==401 && attempt==0) {session.refreshAccessToken() ?: error("Entre novamente para acessar o arquivo.")}
                else {
                    check(response.isSuccessful){"Arquivo indisponível. Confira a amizade e a conexão."}
                    val body=response.body ?: error("Arquivo vazio.")
                    require(body.contentLength()<=MAX){"Arquivo muito grande."}
                    val result=ByteArrayOutputStream()
                    body.byteStream().use {input->val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(result.size()+n<=MAX);result.write(buffer,0,n)}}
                    check();return result.toByteArray()
                }
            }
        }
        error("Entre novamente para acessar o arquivo.")
    }
    override fun uploadPhoto(uri:Uri):String {
        check()
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        app.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0 && bounds.outHeight>0){"Escolha uma foto válida."}
        var sample=1;while(bounds.outWidth/sample>1024 || bounds.outHeight/sample>1024)sample*=2
        val bitmap=app.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})} ?: error("Não foi possível abrir a foto.")
        val ratio=minOf(512f/bitmap.width,512f/bitmap.height,1f)
        val resized=Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)
        val output=ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,86,output)
        if(resized!==bitmap)resized.recycle();bitmap.recycle()
        val path=expected!!.substringBefore(':')+"/avatars/"+UUID.randomUUID()+".jpg"
        request(path,output.toByteArray(),"image/jpeg")
        FriendsClient(app).call("profile",JSONObject().put("avatar_path",path))
        return path
    }
    override fun uploadAudio(file:java.io.File):String {
        check();require(file.length() in 1..MAX.toLong())
        val path=expected!!.substringBefore(':')+"/audio/"+UUID.randomUUID()+".m4a"
        request(path,file.readBytes(),"audio/mp4");return path
    }
    override fun download(path:String):ByteArray=request(path)
    companion object {
        const val MAX=2097152
        private const val BUCKET="jd-friend-media"
        private const val BASE="https://hyoaomusfpeccdchgoik.supabase.co/storage/v1/"
        private const val KEY="sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX"
        private val PATH=Regex("^[a-f0-9-]{36}/(?:avatars/[a-f0-9-]{36}\\.jpg|audio/[a-f0-9-]{36}\\.m4a)$")
        private val http=OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).build()
    }
}

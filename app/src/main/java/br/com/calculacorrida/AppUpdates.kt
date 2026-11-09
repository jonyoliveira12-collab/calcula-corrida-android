package br.com.calculacorrida

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Checks public release metadata. Download and installation always require user interaction. */
object AppUpdates {
    private const val REPO = "jonyoliveira12-collab/calcula-corrida-android"
    private const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    private val checking = AtomicBoolean(false)
    private var checkedAt = 0L
    private val client = OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).build()

    fun check(activity: Activity, manual: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if ((!manual && checkedAt != 0L && now - checkedAt < 3_600_000) || !checking.compareAndSet(false,true)) return
        checkedAt = now
        Thread {
            var found: Pair<String,String>? = null
            var failed = false
            try {
                val info = if (Build.VERSION.SDK_INT >= 28) activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    else activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES)
                val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
                val certificates = signatures.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
                val releases = JSONArray(get("https://api.github.com/repos/$REPO/releases?per_page=10"))
                var bestVersion = version
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.optBoolean("draft")) continue
                    val assets = release.optJSONArray("assets") ?: continue
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        if (asset.optString("name") != "update.json") continue
                        val manifestUrl = asset.optString("browser_download_url")
                        if (!manifestUrl.startsWith(DOWNLOAD_PREFIX)) continue
                        val manifest = JSONObject(get(manifestUrl))
                        val offeredVersion = manifest.optLong("versionCode",0L)
                        val apkUrl = manifest.optString("apkUrl")
                        if (manifest.optString("channel") != "stable" || !apkUrl.startsWith(DOWNLOAD_PREFIX) || !apkUrl.endsWith(".apk")) continue
                        if (UpdatePolicy.isCompatibleUpdate(activity.packageName, bestVersion, certificates,
                                manifest.optString("packageName"), offeredVersion, manifest.optString("certificateSha256"))) {
                            bestVersion = offeredVersion
                            found = manifest.optString("versionName",offeredVersion.toString()) to apkUrl
                        }
                    }
                }
            } catch (_: Exception) { failed = true }
            finally { checking.set(false) }
            val update = found
            Handler(Looper.getMainLooper()).post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (update != null) {
                    AlertDialog.Builder(activity).setTitle("Atualização disponível")
                        .setMessage("A versão ${update.first} do JHONDRIVER está disponível. Baixe o APK e confirme a atualização no Android.")
                        .setNegativeButton("Depois",null)
                        .setPositiveButton("Atualizar") { _, _ ->
                            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.second))) }
                            catch (_: Exception) { android.widget.Toast.makeText(activity,"Não foi possível abrir o download",android.widget.Toast.LENGTH_LONG).show() }
                        }.show()
                } else if (manual) {
                    android.widget.Toast.makeText(activity, if (failed) "Não foi possível consultar atualizações. Tente novamente com internet." else "Nenhuma atualização compatível publicada para esta versão do app.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun get(url: String): String {
        client.newCall(Request.Builder().url(url).header("Accept","application/vnd.github+json").build()).execute().use {
            check(it.isSuccessful)
            val text = it.body?.string() ?: error("Missing response")
            check(text.length < 1_000_000)
            return text
        }
    }
}

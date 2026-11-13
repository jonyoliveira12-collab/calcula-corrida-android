package br.com.calculacorrida

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Android speech only; no microphone and no offer data sent by this app to a speech API. */
class OfferVoice(context: Context, private val isCurrent: (String) -> Boolean) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var generation = 0
    private val pending = linkedMapOf<String, String>()

    fun announce(pkg: String, text: String) {
        if (!prefs.getBoolean("voice_enabled", false) || !isCurrent(pkg)) return
        pending[pkg] = text
        if (ready) flush()
        else if (engine == null) {
            val requestGeneration = generation
            status("Preparando voz do Android")
            engine = TextToSpeech(app) { result -> handler.post {
                if (requestGeneration != generation) return@post
                val language = if (result == TextToSpeech.SUCCESS) engine?.setLanguage(Locale.forLanguageTag("pt-BR")) else null
                ready = language != null && language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
                if (ready) {
                    engine?.setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    status("Voz pronta • confira o volume de mídia")
                    flush()
                } else {
                    pending.clear()
                    engine?.shutdown(); engine = null
                    status("Voz indisponível. Confira o mecanismo e o idioma português nos ajustes de voz do Android.")
                }
            } }
        }
    }

    private fun flush() {
        val messages = pending.toMap()
        pending.clear()
        for ((pkg, text) in messages) {
            if (!prefs.getBoolean("voice_enabled", false) || !isCurrent(pkg)) continue
            if (engine?.speak(text, TextToSpeech.QUEUE_ADD, null, "offer-$pkg") == TextToSpeech.ERROR)
                status("Não foi possível reproduzir a voz. Confira os ajustes de voz do Android.")
        }
    }

    fun stop() { pending.clear(); engine?.stop() }
    fun shutdown() {
        generation++
        stop(); engine?.shutdown(); engine = null; ready = false
    }
    private fun status(value: String) { prefs.edit().putString("voice_status", value).apply() }
}

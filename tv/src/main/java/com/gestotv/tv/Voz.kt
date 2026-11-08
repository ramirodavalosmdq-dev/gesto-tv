package com.gestotv.tv

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * La voz del TV ("Enlace gestual activado").
 * Usa el motor de voz que ya trae el TV (Google TV trae "Servicios de voz de Google"), sin internet.
 * Sale por el parlante del TV como si fuera una película (canal multimedia), así que respeta el volumen
 * que tenga puesto el TV — y si está en silencio, no se escucha.
 * Elige una voz femenina en español: primero las que Google marca como femeninas, si no la de español
 * de Estados Unidos (latina, la más parecida a la nuestra), si no cualquier español.
 */
object Voz {
    private var tts: TextToSpeech? = null
    @Volatile private var lista = false
    private var pendiente: String? = null

    // Voces femeninas conocidas del motor de Google (español latino / España)
    private val FEMENINAS = listOf("es-us-x-sfb", "es-us-x-esf", "es-es-x-eea", "es-es-x-eec")

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st != TextToSpeech.SUCCESS) { Status.log("Voz: el TV no tiene motor de voz"); return@TextToSpeech }
            val t = tts ?: return@TextToSpeech
            t.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            elegirVoz(t)
            t.setSpeechRate(0.95f)   // un poco más pausada: suena más "de sistema"
            t.setPitch(1.05f)
            lista = true
            pendiente?.let { pendiente = null; decir(it) }
        }
    }

    private fun elegirVoz(t: TextToSpeech) {
        val voces: Set<Voice> = try { t.voices ?: emptySet() } catch (_: Exception) { emptySet() }
        val locales = voces.filter { it.locale.language == "es" && !it.isNetworkConnectionRequired }
        val elegida = FEMENINAS.firstNotNullOfOrNull { pref -> locales.firstOrNull { it.name.startsWith(pref) } }
            ?: locales.firstOrNull { it.name.contains("female", true) }
            ?: locales.firstOrNull { it.locale.country == "US" }
            ?: locales.firstOrNull()
        if (elegida != null) { t.voice = elegida; Status.log("Voz: ${elegida.name}") }
        else {
            val r = t.setLanguage(Locale("es", "US"))
            if (r < 0) t.setLanguage(Locale("es"))
            Status.log("Voz: español por defecto")
        }
    }

    fun decir(texto: String) {
        val t = tts
        if (t == null || !lista) { pendiente = texto; return }
        t.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "covi")
    }

    fun cerrar() { tts?.shutdown(); tts = null; lista = false }
}

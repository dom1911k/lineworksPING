package com.dominic.lineworksping

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech

/**
 * Small wrapper around TextToSpeech that speaks the alert content aloud. Init is
 * asynchronous, so a request made before the engine is ready is queued and spoken
 * once initialisation completes.
 */
class TtsSpeaker(context: Context) {

    private var ready = false
    private var pending: String? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pending?.let { speakNow(it) }
            pending = null
        }
    }.apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "ping")
    }

    fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
            // ignore
        }
    }
}

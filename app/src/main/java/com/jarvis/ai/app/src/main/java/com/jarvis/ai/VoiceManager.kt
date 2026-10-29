package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Listening (speech to text) and speaking (text to speech), using Android's built-in services. */
class VoiceManager(
    private val context: Context,
    private val onSpeakingDone: () -> Unit,
    private val onLevel: (Float) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    var speechRate: Float = 1.0f

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun canListen(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Returns false if nothing could be spoken. */
    fun speak(text: String): Boolean {
        val engine = tts
        if (engine == null || !ttsReady) return false

        val spoken = clean(text).take(3500)
        if (spoken.isBlank()) return false

        val hasHindi = spoken.any { it in '\u0900'..'\u097F' }
        val locale = if (hasHindi) Locale("hi", "IN") else Locale("en", "IN")
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.setSpeechRate(speechRate)

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { onSpeakingDone() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { onSpeakingDone() } }
        })
        val r = engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        return r == TextToSpeech.SUCCESS
    }

    /** Removes emoji and markdown symbols so they are not read aloud. */
    private fun clean(text: String): String =
        text.replace(Regex("[*_#`>~|]"), " ")
            .replace(Regex("[\\p{So}\\p{Cs}\\u2600-\\u27BF]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun stopSpeaking() {
        tts?.stop()
    }

    /** languageTag example: "hi-IN" or "en-IN". Call only after mic permission is granted. */
    fun startListening(
        languageTag: String,
        onPartial: (String) -> Unit,
        onText: (String) -> Unit,
        onFail: (String, Boolean) -> Unit
    ) {
        if (!canListen()) {
            onFail("Speech recognition is not available on this phone. Please type instead.", false)
            return
        }
        stopListening()

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onRmsChanged(rmsdB: Float) {
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onEndOfSpeech() {
                onLevel(0f)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!t.isNullOrBlank()) onPartial(t)
            }

            override fun onError(error: Int) {
                onLevel(0f)
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                onFail(messageFor(error), quiet)
            }

            override fun onResults(results: Bundle?) {
                onLevel(0f)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) onFail("I didn't catch that. Please try again.", true)
                else onText(text)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        }
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.destroy()
        recognizer = null
        onLevel(0f)
    }

    fun shutdown() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Tap the orb and try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition needs internet on this phone. You can type instead."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice service is busy. Try again in a moment."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone problem. Please try again."
        else -> "Voice recognition failed. You can type instead."
    }
}

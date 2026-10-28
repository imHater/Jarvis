package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Listening (speech to text) and speaking (text to speech), using Android's built-in services. */
class VoiceManager(
    private val context: Context,
    private val onSpeakingDone: () -> Unit
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun canListen(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Returns false if the phone's voice engine is not ready. */
    fun speak(text: String): Boolean {
        val engine = tts
        if (engine == null || !ttsReady) return false

        val hasHindi = text.any { it in '\u0900'..'\u097F' }
        val locale = if (hasHindi) Locale("hi", "IN") else Locale("en", "IN")
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onSpeakingDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onSpeakingDone() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        return true
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    /** languageTag example: "hi-IN" or "en-IN". Call only after mic permission is granted. */
    fun startListening(
        languageTag: String,
        onText: (String) -> Unit,
        onFail: (String) -> Unit
    ) {
        if (!canListen()) {
            onFail("Speech recognition is not available on this phone. Please type instead.")
            return
        }
        stopListening()

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                onFail(messageFor(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) onFail("I didn't catch that. Please try again.")
                else onText(text)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.destroy()
        recognizer = null
    }

    fun shutdown() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Tap the mic and try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition needs internet on this phone. You can type instead."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice service is busy. Try again in a moment."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone problem. Please try again."
        else -> "Voice recognition failed. You can type instead."
    }
}

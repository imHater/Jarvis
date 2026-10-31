package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale

/**
 * Listening (speech to text) and speaking.
 * Speaking uses your Inworld cloned voice if key + voice ID are set in Settings,
 * otherwise (or if Inworld fails) the phone's own text-to-speech.
 */
class VoiceManager(
    private val context: Context,
    private val onSpeakingDone: () -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onNotice: (String) -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private val settings = SecureSettings(context)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var player: MediaPlayer? = null
    private var speakToken = 0
    private var listenToken = 0
    private val mutedStreams = mutableListOf<Int>()

    var speechRate: Float = 1.0f

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun canListen(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    // ---------- Silence the "listening" beeps ----------

    private fun muteBeeps() {
        if (mutedStreams.isNotEmpty()) return
        val streams = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        )
        for (s in streams) {
            try {
                if (!audio.isStreamMute(s)) {
                    audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                    mutedStreams.add(s)
                }
            } catch (e: Exception) {
                // Some phones don't allow muting every stream. That's fine.
            }
        }
    }

    private fun unmuteBeeps() {
        listenToken++
        for (s in mutedStreams) {
            try {
                audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: Exception) {
            }
        }
        mutedStreams.clear()
    }

    // ---------- Speaking ----------

    /** Returns false if nothing could be spoken. */
    fun speak(text: String): Boolean {
        val spoken = clean(text).take(1900)
        if (spoken.isBlank()) return false
        stopSpeaking()

        val key = settings.inworldKey
        val voiceId = settings.inworldVoiceId
        if (key.isNotBlank() && voiceId.isNotBlank()) {
            speakInworld(spoken, key, voiceId)
            return true
        }
        return speakDevice(spoken)
    }

    private fun speakInworld(text: String, key: String, voiceId: String) {
        val token = ++speakToken
        val model = settings.inworldModel.ifBlank { "inworld-tts-2" }
        val hasHindi = text.any { it in '\u0900'..'\u097F' }

        Thread {
            var audioBytes: ByteArray? = null
            var error: String? = null
            try {
                val conn = URL("https://api.inworld.ai/tts/v1/voice").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 40000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Basic " + key.removePrefix("Basic ").trim())

                val bodyJson = JSONObject()
                    .put("text", text)
                    .put("voiceId", voiceId)
                    .put("modelId", model)
                    .put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
                if (hasHindi) bodyJson.put("language", "hi")
                conn.outputStream.use { it.write(bodyJson.toString().toByteArray()) }

                val code = conn.responseCode
                if (code == 200) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(json)
                    var b64 = obj.optString("audioContent")
                    if (b64.isBlank()) {
                        b64 = obj.optJSONObject("result")?.optString("audioContent") ?: ""
                    }
                    if (b64.isBlank()) error = "Inworld sent no audio."
                    else audioBytes = Base64.decode(b64, Base64.DEFAULT)
                } else {
                    val detail = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(160) ?: ""
                    } catch (e: Exception) { "" }
                    error = when (code) {
                        401, 403 -> "Inworld key not accepted ($code). Check the key in Settings."
                        429 -> "Inworld limit reached (429)."
                        else -> "Inworld error ($code). Check voice ID and model name. $detail"
                    }
                }
            } catch (e: UnknownHostException) {
                error = "No internet for Inworld voice."
            } catch (e: Exception) {
                error = "Inworld voice failed: ${e.message}"
            }
            main.post { onInworldResult(token, text, audioBytes, error) }
        }.start()
    }

    private fun onInworldResult(token: Int, text: String, audioBytes: ByteArray?, error: String?) {
        if (token != speakToken) return // cancelled meanwhile
        if (audioBytes == null) {
            fallbackToDevice(text, error ?: "Inworld voice failed.")
            return
        }
        try {
            val f = File(context.cacheDir, "kabir_voice.mp3")
            f.writeBytes(audioBytes)
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                try { it.release() } catch (e: Exception) {}
                if (player === it) player = null
                if (token == speakToken) onSpeakingDone()
            }
            mp.setOnErrorListener { p, _, _ ->
                try { p.release() } catch (e: Exception) {}
                if (player === p) player = null
                if (token == speakToken) fallbackToDevice(text, "Could not play the Inworld audio.")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            fallbackToDevice(text, "Could not play the Inworld audio.")
        }
    }

    private fun fallbackToDevice(text: String, reason: String) {
        onNotice("$reason Using the phone voice instead.")
        if (!speakDevice(text)) onSpeakingDone()
    }

    private fun speakDevice(spoken: String): Boolean {
        val engine = tts
        if (engine == null || !ttsReady) return false

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
        val r = engine.speak(spoken.take(3500), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        return r == TextToSpeech.SUCCESS
    }

    /** Removes emoji and markdown symbols so they are not read aloud. */
    private fun clean(text: String): String =
        text.replace(Regex("[*_#`>~|]"), " ")
            .replace(Regex("[\\p{So}\\p{Cs}\\u2600-\\u27BF]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun stopSpeaking() {
        speakToken++
        tts?.stop()
        try { player?.release() } catch (e: Exception) {}
        player = null
    }

    // ---------- Listening ----------

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
                unmuteBeeps()
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                onFail(messageFor(error), quiet)
            }

            override fun onResults(results: Bundle?) {
                onLevel(0f)
                unmuteBeeps()
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

        // Mute the system "listening" beeps; they come back as soon as we have a result.
        muteBeeps()
        val my = ++listenToken
        main.postDelayed({ if (listenToken == my) unmuteBeeps() }, 20000)

        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.destroy()
        recognizer = null
        unmuteBeeps()
        onLevel(0f)
    }

    fun shutdown() {
        stopListening()
        stopSpeaking()
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

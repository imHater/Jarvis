package com.jarvis.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Our own wake word: "Jarvis" is detected on the phone with the open-source Vosk engine.
 * The voice model (about 40 MB) is downloaded once; after that it works offline.
 */
class VoskWake(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onWakeWord: () -> Unit,
    private val onFailed: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private var ready = false
    private var wanted = false
    private var loading = false
    private var triggered = false
    private var loadToken = 0

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) { heard(hypothesis) }
        override fun onResult(hypothesis: String?) { heard(hypothesis) }
        override fun onFinalResult(hypothesis: String?) { }
        override fun onError(exception: Exception?) {
            onStatus("Wake word error: ${exception?.message}")
        }
        override fun onTimeout() { }
    }

    private fun heard(json: String?) {
        if (json.isNullOrBlank() || triggered || !wanted) return
        val text = try {
            val o = JSONObject(json)
            val t = o.optString("text")
            if (t.isNotBlank()) t else o.optString("partial")
        } catch (e: Exception) {
            ""
        }
        val t = text.lowercase()
        if (WAKE_WORDS.any { t.contains(it) }) {
            triggered = true
            main.post { onWakeWord() }
        }
    }

    /** Starts listening for the wake word (downloads the voice model the first time). */
    fun start() {
        wanted = true
        if (service != null) return
        if (ready) {
            beginListening()
            return
        }
        if (loading) return
        loading = true
        val my = ++loadToken

        // If loading takes too long, tell the user instead of staying stuck.
        main.postDelayed({
            if (loading && my == loadToken) {
                loading = false
                onFailed("The voice model is taking too long. Turn the wake word off and on again.")
            }
        }, 150000)

        Thread {
            var err: String? = null
            var loadedModel: Model? = null
            var loadedRec: Recognizer? = null
            try {
                val dir = File(context.filesDir, "vosk")
                val modelDir = File(dir, MODEL_NAME)
                val marker = File(dir, ".ok_$MODEL_NAME")

                if (!marker.exists()) {
                    modelDir.deleteRecursively()
                    err = downloadModel(dir, marker)
                }
                if (err == null) {
                    post("Loading voice model...")
                    try {
                        loadedModel = Model(modelDir.absolutePath)
                    } catch (t: Throwable) {
                        // Broken or incomplete files: remove them so the next start downloads again.
                        marker.delete()
                        modelDir.deleteRecursively()
                        throw t
                    }
                    post("Preparing wake word...")
                    loadedRec = Recognizer(loadedModel, 16000.0f)
                }
            } catch (t: Throwable) {
                err = "Wake word setup failed: ${t.javaClass.simpleName}: ${t.message}"
            }
            main.post {
                if (my != loadToken) return@post
                loading = false
                if (err == null && loadedModel != null && loadedRec != null) {
                    model = loadedModel
                    recognizer = loadedRec
                    ready = true
                    if (wanted) beginListening()
                } else {
                    onFailed(err ?: "Voice model failed.")
                }
            }
        }.start()
    }

    private fun post(text: String) {
        main.post { onStatus(text) }
    }

    private fun beginListening() {
        val r = recognizer ?: return
        try {
            triggered = false
            val s = SpeechService(r, 16000.0f)
            service = s
            s.startListening(listener)
            onStatus("Listening for \"Jarvis\"")
        } catch (t: Throwable) {
            service = null
            onFailed("Wake word could not start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Stops listening and frees the microphone. */
    fun pause() {
        wanted = false
        val s = service
        service = null
        if (s != null) {
            try { s.stop() } catch (t: Throwable) { }
            try { s.shutdown() } catch (t: Throwable) { }
        }
    }

    fun release() {
        loadToken++ // cancels any loading in progress
        loading = false
        pause()
        try { (recognizer as? java.io.Closeable)?.close() } catch (t: Throwable) { }
        recognizer = null
        try { (model as? java.io.Closeable)?.close() } catch (t: Throwable) { }
        model = null
        ready = false
    }

    private fun downloadModel(dir: File, marker: File): String? {
        return try {
            dir.mkdirs()
            val zip = File(dir, "model.zip")
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            if (conn.responseCode != 200) {
                return "Voice model download failed (${conn.responseCode})."
            }
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n = input.read(buf)
                    while (n > 0) {
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct && pct % 5 == 0) {
                                lastPct = pct
                                post("Downloading voice model $pct%")
                            }
                        }
                        n = input.read(buf)
                    }
                }
            }
            post("Unpacking voice model...")
            unzip(zip, dir)
            zip.delete()
            if (!File(dir, MODEL_NAME).exists()) {
                return "Voice model folder not found after unpacking."
            }
            marker.writeText("ok")
            null
        } catch (t: Throwable) {
            "Voice model download failed: ${t.message}"
        }
    }

    private fun unzip(zip: File, dest: File) {
        val root = dest.canonicalPath + File.separator
        ZipInputStream(FileInputStream(zip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(root)) {
                    throw SecurityException("Unsafe file in the voice model zip")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    companion object {
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        private val WAKE_WORDS = listOf("jarvis", "jar vis", "jarvish", "jarvas")
    }
}

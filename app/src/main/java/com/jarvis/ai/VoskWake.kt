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
    private var wanted = false
    private var loading = false
    private var triggered = false

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
        if (model != null) {
            begin()
            return
        }
        if (loading) return
        loading = true
        Thread {
            val dir = File(context.filesDir, "vosk")
            val modelDir = File(dir, MODEL_NAME)
            val marker = File(dir, ".ok_$MODEL_NAME")
            var err: String? = null
            if (!marker.exists()) {
                err = downloadModel(dir, marker)
            }
            var loaded: Model? = null
            if (err == null) {
                try {
                    main.post { onStatus("Loading voice model...") }
                    loaded = Model(modelDir.absolutePath)
                } catch (e: Exception) {
                    marker.delete() // download again next time
                    err = "Could not load the voice model: ${e.message}"
                }
            }
            main.post {
                loading = false
                if (loaded != null) {
                    model = loaded
                    if (wanted) begin()
                } else {
                    onFailed(err ?: "Voice model failed.")
                }
            }
        }.start()
    }

    private fun begin() {
        val m = model ?: return
        try {
            val r = recognizer ?: Recognizer(m, 16000.0f, GRAMMAR).also { recognizer = it }
            triggered = false
            val s = SpeechService(r, 16000.0f)
            service = s
            s.startListening(listener)
            onStatus("Listening for \"Jarvis\"")
        } catch (e: Exception) {
            service = null
            onFailed("Wake word could not start: ${e.message}")
        }
    }

    /** Stops listening and frees the microphone. */
    fun pause() {
        wanted = false
        val s = service
        service = null
        if (s != null) {
            try { s.stop() } catch (e: Exception) { }
            try { s.shutdown() } catch (e: Exception) { }
        }
    }

    fun release() {
        pause()
        try { (recognizer as? java.io.Closeable)?.close() } catch (e: Exception) { }
        recognizer = null
        try { (model as? java.io.Closeable)?.close() } catch (e: Exception) { }
        model = null
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
                                main.post { onStatus("Downloading voice model $pct%") }
                            }
                        }
                        n = input.read(buf)
                    }
                }
            }
            main.post { onStatus("Unpacking voice model...") }
            unzip(zip, dir)
            zip.delete()
            if (!File(dir, MODEL_NAME).exists()) {
                return "Voice model folder not found after unpacking."
            }
            marker.writeText("ok")
            null
        } catch (e: Exception) {
            "Voice model download failed: ${e.message}"
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
        private const val GRAMMAR = "[\"jarvis\", \"jar vis\", \"[unk]\"]"
        private val WAKE_WORDS = listOf("jarvis", "jar vis")
    }
}

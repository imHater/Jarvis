package com.jarvis.ai

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Holds a command that needs the app screen (set by the wake service, used by the app). */
object PendingCommand {
    @Volatile
    var text: String? = null

    fun take(): String? {
        val t = text
        text = null
        return t
    }
}

/**
 * Listens for the wake word "Jarvis" (on the phone, with Picovoice), then listens to one
 * request, answers by voice, and goes back to waiting.
 */
class WakeService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var settings: SecureSettings
    private lateinit var memory: MemoryStore
    private lateinit var router: CommandRouter
    private lateinit var ai: AiClient
    private lateinit var phone: PhoneActions
    private lateinit var voice: VoiceManager

    private var porcupine: PorcupineManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeListening = false
    private var busy = false
    private var appOpen = false
    private var session = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        appOpen = visibleFlag
        settings = SecureSettings(this)
        memory = MemoryStore(this)
        router = CommandRouter(this, memory)
        ai = AiClient(settings)
        phone = PhoneActions(this)
        voice = VoiceManager(this, onSpeakingDone = { resume() }, onLevel = { })
        voice.speechRate = getSharedPreferences("jarvis_ui", 0).getFloat("speed", 1.0f)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.wakeEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification("Starting..."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        acquireWakeLock()
        startPorcupine()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        try {
            porcupine?.stop()
            porcupine?.delete()
        } catch (e: Exception) {
        }
        porcupine = null
        try { voice.shutdown() } catch (e: Exception) { }
        scope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------- Wake word ----------

    private fun startPorcupine() {
        if (wakeListening) return
        if (appOpen) {
            updateStatus("Paused while the app is open")
            return
        }
        val key = settings.picoKey
        if (key.isBlank()) {
            failed("Add your Picovoice AccessKey in Settings.")
            return
        }
        try {
            if (porcupine == null) {
                porcupine = PorcupineManager.Builder()
                    .setAccessKey(key)
                    .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                    .build(applicationContext, PorcupineManagerCallback { main.post { onWake() } })
            }
            porcupine?.start()
            wakeListening = true
            updateStatus("Listening for \"Jarvis\"")
        } catch (e: Exception) {
            failed("Wake word could not start: ${e.message}")
        }
    }

    private fun pauseWake() {
        try { porcupine?.stop() } catch (e: Exception) { }
        wakeListening = false
        updateStatus("Paused while the app is open")
    }

    private fun setAppVisible(v: Boolean) {
        main.post {
            appOpen = v
            if (!busy) {
                if (v) pauseWake() else startPorcupine()
            }
        }
    }

    private fun onWake() {
        if (busy || appOpen) return
        busy = true
        val my = ++session
        try { porcupine?.stop() } catch (e: Exception) { }
        wakeListening = false
        updateStatus("Listening to you...")
        beep()
        main.postDelayed({ if (busy && session == my) listenNow() }, 400)
        // Safety: never stay stuck.
        main.postDelayed({
            if (busy && session == my) {
                voice.stopListening()
                voice.stopSpeaking()
                resume()
            }
        }, 60000)
    }

    private fun listenNow() {
        val lang = getSharedPreferences("jarvis_ui", 0).getString("lang", "en-IN") ?: "en-IN"
        voice.startListening(
            lang,
            onPartial = { },
            onText = { handle(it) },
            onFail = { _, _ -> resume() }
        )
    }

    private fun resume() {
        if (!busy) return
        session++
        busy = false
        main.postDelayed({ if (!busy) startPorcupine() }, 400)
    }

    // ---------- Handling the request ----------

    private fun handle(text: String) {
        val t = text.lowercase()
        if (NEEDS_APP.containsMatchIn(t)) {
            handoff(text)
            return
        }
        when (val r = router.handle(text)) {
            is CommandResult.Reply -> say(r.text)
            is CommandResult.Confirm -> handoff(text)
            CommandResult.NotACommand -> askAi(text)
        }
    }

    private fun askAi(text: String) {
        updateStatus("Thinking...")
        scope.launch {
            when (val r = ai.ask(listOf(ChatMessage(text, true)), memory.getAll())) {
                is AiResult.Success -> {
                    if (phone.fromAiText(r.text) != null) handoff(text) else say(r.text)
                }
                is AiResult.Failure -> say("Sorry, I could not get an answer right now.")
            }
        }
    }

    private fun say(text: String) {
        updateStatus("Speaking...")
        if (!voice.speak(text)) resume()
    }

    /** Android won't let a background service open apps. So we put a button in the notification. */
    private fun handoff(text: String) {
        PendingCommand.text = text
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH_INFO)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Kabir needs the app for this")
            .setContentText("Tap to run: $text")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_HANDOFF, n)
        say("Android needs the app screen for that. I put a button in your notifications. Please tap it.")
    }

    // ---------- Helpers ----------

    private fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            main.postDelayed({
                try { tg.release() } catch (e: Exception) { }
            }, 400)
        } catch (e: Exception) {
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kabir:wake").also { it.acquire() }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_WAKE, "Wake word", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Kabir alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, WakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH_WAKE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Kabir")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateStatus(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun failed(msg: String) {
        settings.wakeEnabled = false
        val n = NotificationCompat.Builder(this, CH_INFO)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Kabir wake word is off")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_INFO, n)
        stopSelf()
    }

    companion object {
        private const val CH_WAKE = "kabir_wake"
        private const val CH_INFO = "kabir_info"
        private const val NOTIF_ID = 11
        private const val NOTIF_ID_INFO = 12
        private const val NOTIF_ID_HANDOFF = 13
        const val ACTION_STOP = "com.jarvis.ai.STOP_WAKE"

        // Things Android only lets us do with the app on screen.
        private val NEEDS_APP = Regex(
            "\\b(open|launch|kholo|khol|call|dial|sms|youtube|yt|google|search|dhundo|navigate|" +
                "rasta|map|maps|wifi|wi-fi|hotspot|bluetooth|alarm|timer|reminder|screenshot|" +
                "settings|brightness|airplane)\\b"
        )

        @Volatile private var instance: WakeService? = null
        @Volatile private var visibleFlag = false

        /** Called when the app screen appears or disappears (wake listening pauses while visible). */
        fun appVisible(visible: Boolean) {
            visibleFlag = visible
            instance?.setAppVisible(visible)
        }

        fun setEnabled(ctx: Context, on: Boolean) {
            val i = Intent(ctx, WakeService::class.java)
            if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
        }
    }
}

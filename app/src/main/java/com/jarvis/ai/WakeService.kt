package com.jarvis.ai

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
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
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
 * Listens for the wake word "Jarvis" (on the phone, with open-source Vosk), then listens to one
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

    private var vosk: VoskWake? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeListening = false
    private var busy = false
    private var appOpen = false
    private var session = 0

    // A yes/no question we asked by voice (alarms, notes...).
    private var pendingConfirm: CommandResult.Confirm? = null
    private var pendingOriginal: String = ""

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
        startWake()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        try { vosk?.release() } catch (e: Exception) { }
        vosk = null
        try { voice.shutdown() } catch (e: Exception) { }
        scope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------- Wake word ----------

    private fun startWake() {
        if (wakeListening) return
        if (appOpen) {
            updateStatus("Paused while the app is open")
            return
        }
        if (vosk == null) {
            vosk = VoskWake(
                this,
                onStatus = { updateStatus(it) },
                onWakeWord = { onWake() },
                onFailed = { failed(it) }
            )
        }
        wakeListening = true
        updateStatus("Starting...")
        vosk?.start()
    }

    private fun pauseWake() {
        try { vosk?.pause() } catch (e: Exception) { }
        wakeListening = false
        updateStatus("Paused while the app is open")
    }

    private fun setAppVisible(v: Boolean) {
        main.post {
            appOpen = v
            if (!busy) {
                if (v) pauseWake() else startWake()
            }
        }
    }

    private fun onWake() {
        if (busy || appOpen) return
        busy = true
        pendingConfirm = null
        val my = ++session
        try { vosk?.pause() } catch (e: Exception) { }
        wakeListening = false
        updateStatus("Listening to you...")
        beep()
        main.postDelayed({ if (busy && session == my) listenNow() }, 500)
        // Safety: never stay stuck.
        main.postDelayed({
            if (busy && session == my) {
                pendingConfirm = null
                voice.stopListening()
                voice.stopSpeaking()
                resume()
            }
        }, 60000)
    }

    private fun listenNow() {
        updateStatus("Listening to you...")
        val lang = getSharedPreferences("jarvis_ui", 0).getString("lang", "en-IN") ?: "en-IN"
        voice.startListening(
            lang,
            onPartial = { },
            onText = { handle(it) },
            onFail = { _, _ ->
                pendingConfirm = null
                resume()
            }
        )
    }

    private fun resume() {
        if (!busy) return
        // Waiting for a yes/no: listen again instead of going back to sleep.
        if (pendingConfirm != null) {
            main.postDelayed({ if (busy && pendingConfirm != null) listenNow() }, 300)
            return
        }
        session++
        busy = false
        main.postDelayed({ if (!busy) startWake() }, 500)
    }

    // ---------- Handling the request ----------

    private fun handle(text: String) {
        val t = text.lowercase().trim().trimEnd('.', '!', '?')

        // An answer to a yes/no question from the last turn
        val c = pendingConfirm
        if (c != null) {
            pendingConfirm = null
            when {
                t in YES_WORDS -> runAuto(c, pendingOriginal)
                t in NO_WORDS -> say("Okay, cancelled.")
                else -> say("I did not hear a clear yes, so I cancelled.")
            }
            return
        }

        val canLaunch = Settings.canDrawOverlays(this)
        if (!canLaunch && NEEDS_APP.containsMatchIn(t)) {
            handoff(text)
            return
        }
        when (val r = router.handle(text)) {
            is CommandResult.Reply -> say(r.text)
            is CommandResult.Confirm -> if (r.auto) runAuto(r, text) else ask(r, text)
            CommandResult.NotACommand -> askAi(text)
        }
    }

    private fun ask(c: CommandResult.Confirm, original: String) {
        pendingConfirm = c
        pendingOriginal = original
        say(c.prompt)
    }

    /** Runs the action now. If a permission is missing, hand over to the app. */
    private fun runAuto(c: CommandResult.Confirm, original: String) {
        if (c.permissions.all { phone.has(it) }) say(c.onYes()) else handoff(original)
    }

    private fun askAi(text: String) {
        updateStatus("Thinking...")
        scope.launch {
            when (val r = ai.ask(listOf(ChatMessage(text, true)), memory.getAll())) {
                is AiResult.Success -> {
                    val c = phone.fromAiText(r.text)
                    when {
                        c == null -> say(r.text)
                        Settings.canDrawOverlays(this@WakeService) ->
                            if (c.auto) runAuto(c, text) else ask(c, text)
                        else -> handoff(text)
                    }
                }
                is AiResult.Failure -> say("Sorry, I could not get an answer right now.")
            }
        }
    }

    private fun say(text: String) {
        updateStatus("Speaking...")
        if (!voice.speak(text)) resume()
    }

    /**
     * Without the "Display over other apps" permission, Android won't let a background service
     * open apps. So we put a button in the notification, plus a shortcut to allow it.
     */
    private fun handoff(text: String) {
        PendingCommand.text = text
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val overlay = PendingIntent.getActivity(
            this, 3,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH_INFO)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Kabir needs the app for this")
            .setContentText("Tap to run: $text")
            .setContentIntent(open)
            .addAction(0, "Allow auto-open", overlay)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_HANDOFF, n)
        say("Please tap the notification to run that.")
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

        // Things Android only lets us do with the app on screen (unless auto-open is allowed).
        private val NEEDS_APP = Regex(
            "\\b(open|launch|kholo|khol|call|dial|sms|youtube|yt|google|search|dhundo|navigate|" +
                "rasta|map|maps|wifi|wi-fi|hotspot|bluetooth|alarm|timer|reminder|screenshot|" +
                "settings|brightness|airplane)\\b"
        )

        private val YES_WORDS = setOf(
            "yes", "yeah", "yep", "ok", "okay", "sure", "haan", "han", "ha", "haa",
            "kar do", "theek hai", "thik hai", "हाँ", "हां", "हा", "जी", "ठीक है", "कर दो"
        )
        private val NO_WORDS = setOf(
            "no", "nope", "nahi", "nahin", "na", "cancel", "mat karo", "ruko", "stop",
            "नहीं", "ना", "रुको", "मत करो"
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

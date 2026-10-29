package com.jarvis.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class JarvisState { IDLE, LISTENING, THINKING, SPEAKING }

class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SecureSettings(app)
    private val memoryStore = MemoryStore(app)
    private val ai = AiClient(settings)
    private val router = CommandRouter(app, memoryStore)
    private val uiPrefs = app.getSharedPreferences("jarvis_ui", 0)

    val messages = mutableStateListOf<ChatMessage>()

    var state by mutableStateOf(JarvisState.IDLE)
        private set
    var pendingConfirm by mutableStateOf<CommandResult.Confirm?>(null)
        private set
    var memories by mutableStateOf(memoryStore.getAll())
        private set
    var assistantName by mutableStateOf(settings.assistantName)
        private set
    var voiceLanguage by mutableStateOf(uiPrefs.getString("lang", "en-IN") ?: "en-IN")
        private set
    var speakReplies by mutableStateOf(uiPrefs.getBoolean("speak", true))
        private set
    var handsFree by mutableStateOf(uiPrefs.getBoolean("handsfree", false))
        private set
    var speechSpeed by mutableStateOf(uiPrefs.getFloat("speed", 1.0f))
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var heardText by mutableStateOf("")
        private set

    private var conversationActive = false
    private var silentRetries = 0

    private val voice = VoiceManager(
        app,
        onSpeakingDone = { onSpeechFinished() },
                onLevel = { level = it },
        onNotice = { showNotice(it) }
    )

    init {
        voice.speechRate = speechSpeed
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || state == JarvisState.THINKING) return
        voice.stopSpeaking()
        heardText = ""
        messages.add(ChatMessage(text, true))

        if (pendingConfirm != null) {
            val w = text.lowercase().trim().trimEnd('.', '!', '?')
            if (w in YES_WORDS) { resolveConfirm(true); return }
            if (w in NO_WORDS) { resolveConfirm(false); return }
            pendingConfirm = null
        }

        when (val r = router.handle(text)) {
            is CommandResult.Reply -> reply(r.text)
            is CommandResult.Confirm -> {
                pendingConfirm = r
                reply(r.prompt)
            }
            CommandResult.NotACommand -> askAi()
        }
    }

    fun resolveConfirm(yes: Boolean) {
        val p = pendingConfirm ?: return
        pendingConfirm = null
        if (yes) {
            val out = p.onYes()
            memories = memoryStore.getAll()
            reply(out)
        } else {
            reply("Okay, cancelled.")
        }
    }

    private fun askAi() {
        state = JarvisState.THINKING
        viewModelScope.launch {
            val history = messages.filter { !it.text.startsWith("⚠") }
            when (val result = ai.ask(history, memoryStore.getAll())) {
                is AiResult.Success -> reply(result.text)
                is AiResult.Failure -> {
                    conversationActive = false
                    messages.add(ChatMessage("⚠️ ${result.message}", false))
                    state = JarvisState.IDLE
                }
            }
        }
    }

    private fun reply(text: String) {
        messages.add(ChatMessage(text, false))
        if (speakReplies && voice.speak(text)) {
            state = JarvisState.SPEAKING
        } else {
            state = JarvisState.IDLE
            if (conversationActive) listenNow()
        }
    }

    private fun onSpeechFinished() {
        if (state != JarvisState.SPEAKING) return
        state = JarvisState.IDLE
        if (conversationActive) listenNow()
    }

    fun showNotice(text: String) {
        messages.add(ChatMessage("⚠️ $text", false))
    }

    /** Called when the user taps the orb or mic. */
    fun startListening() {
        conversationActive = handsFree
        silentRetries = 0
        listenNow()
    }

    private fun listenNow() {
        voice.stopSpeaking()
        heardText = ""
        state = JarvisState.LISTENING
        voice.startListening(
            voiceLanguage,
            onPartial = { heardText = it },
            onText = {
                silentRetries = 0
                heardText = ""
                state = JarvisState.IDLE
                submit(it)
            },
            onFail = { msg, quiet -> handleListenFail(msg, quiet) }
        )
    }

    private fun handleListenFail(msg: String, quiet: Boolean) {
        heardText = ""
        // In hands-free mode, listen again once or twice, then stop by itself.
        if (quiet && conversationActive && silentRetries < 2) {
            silentRetries++
            viewModelScope.launch {
                delay(300)
                if (conversationActive) listenNow()
            }
            return
        }
        val wasConversation = conversationActive
        conversationActive = false
        state = JarvisState.IDLE
        if (quiet && wasConversation) {
            showNotice("I stopped listening because I heard nothing. Tap the orb to talk again.")
        } else {
            showNotice(msg)
        }
    }

    /** Stops listening, speaking and hands-free mode. */
    fun stopAll() {
        conversationActive = false
        voice.stopListening()
        voice.stopSpeaking()
        if (state != JarvisState.THINKING) state = JarvisState.IDLE
    }

    fun saveSettings(
        key: String, model: String, name: String, personality: String,
        language: String, speak: Boolean, hands: Boolean, speed: Float
    ) {
        settings.apiKey = key
        if (model.isNotBlank()) settings.model = model
        if (name.isNotBlank()) {
            settings.assistantName = name
            assistantName = settings.assistantName
        }
        settings.personality = personality
        voiceLanguage = language
        speakReplies = speak
        handsFree = hands
        speechSpeed = speed
        voice.speechRate = speed
        uiPrefs.edit()
            .putString("lang", language)
            .putBoolean("speak", speak)
            .putBoolean("handsfree", hands)
            .putFloat("speed", speed)
            .apply()
        if (!speak) voice.stopSpeaking()
    }

    fun addMemory(text: String) {
        memoryStore.add(text)
        memories = memoryStore.getAll()
    }

    fun deleteMemory(id: Long) {
        memoryStore.delete(id)
        memories = memoryStore.getAll()
    }

    fun clearMemories() {
        memoryStore.clear()
        memories = memoryStore.getAll()
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }

    private companion object {
        val YES_WORDS = setOf(
            "yes", "yeah", "yep", "ok", "okay", "sure",
            "haan", "han", "ha", "haa", "kar do", "theek hai", "thik hai"
        )
        val NO_WORDS = setOf(
            "no", "nope", "nahi", "nahin", "na", "cancel", "mat karo", "ruko", "stop"
        )
    }
}

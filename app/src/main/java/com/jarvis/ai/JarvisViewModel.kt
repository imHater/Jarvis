package com.jarvis.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val voice = VoiceManager(app) {
        if (state == JarvisState.SPEAKING) state = JarvisState.IDLE
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || state == JarvisState.THINKING) return
        voice.stopSpeaking()
        pendingConfirm = null
        messages.add(ChatMessage(text, true))

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
                    messages.add(ChatMessage("⚠️ ${result.message}", false))
                    state = JarvisState.IDLE
                }
            }
        }
    }

    private fun reply(text: String) {
        messages.add(ChatMessage(text, false))
        state = if (speakReplies && voice.speak(text)) JarvisState.SPEAKING else JarvisState.IDLE
    }

    fun showNotice(text: String) {
        messages.add(ChatMessage("⚠️ $text", false))
    }

    fun startListening() {
        voice.stopSpeaking()
        state = JarvisState.LISTENING
        voice.startListening(
            voiceLanguage,
            onText = {
                state = JarvisState.IDLE
                submit(it)
            },
            onFail = {
                state = JarvisState.IDLE
                showNotice(it)
            }
        )
    }

    fun stopListening() {
        voice.stopListening()
        state = JarvisState.IDLE
    }

    fun saveSettings(
        key: String, model: String, name: String,
        personality: String, language: String, speak: Boolean
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
        uiPrefs.edit().putString("lang", language).putBoolean("speak", speak).apply()
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
}

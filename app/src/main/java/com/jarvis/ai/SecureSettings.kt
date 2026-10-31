package com.jarvis.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Saves your keys and settings on this phone only (encrypted). */
class SecureSettings(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "jarvis_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback: still private to this app
        context.getSharedPreferences("jarvis_private", Context.MODE_PRIVATE)
    }

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit().putString("user_name", v.trim()).apply()

    var inworldKey: String
        get() = prefs.getString("inworld_key", "") ?: ""
        set(v) = prefs.edit().putString("inworld_key", v.trim()).apply()

    var inworldVoiceId: String
        get() = prefs.getString("inworld_voice", "") ?: ""
        set(v) = prefs.edit().putString("inworld_voice", v.trim()).apply()

    var inworldModel: String
        get() = prefs.getString("inworld_model", "inworld-tts-2") ?: "inworld-tts-2"
        set(v) = prefs.edit().putString("inworld_model", v.trim()).apply()

    var picoKey: String
        get() = prefs.getString("pico_key", "") ?: ""
        set(v) = prefs.edit().putString("pico_key", v.trim()).apply()

    var wakeEnabled: Boolean
        get() = prefs.getBoolean("wake_on", false)
        set(v) = prefs.edit().putBoolean("wake_on", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) = prefs.edit().putString("model", v.trim()).apply()

    var assistantName: String
        get() = prefs.getString("name", "JARVIS") ?: "JARVIS"
        set(v) = prefs.edit().putString("name", v.trim()).apply()

    var personality: String
        get() = prefs.getString(
            "personality",
            "Friendly, intelligent and professional. Short, clear, helpful answers."
        ) ?: ""
        set(v) = prefs.edit().putString("personality", v.trim()).apply()
}

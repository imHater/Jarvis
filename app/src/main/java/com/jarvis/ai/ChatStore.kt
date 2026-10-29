package com.jarvis.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saves chat history on this phone only. You can delete it anytime. */
class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_history", Context.MODE_PRIVATE)

    fun getAll(): List<ChatMessage> {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ChatMessage(o.getString("t"), o.getBoolean("u"))
        }
    }

    fun add(m: ChatMessage) {
        val all = (getAll() + m).takeLast(300)
        val arr = JSONArray()
        all.forEach { arr.put(JSONObject().put("t", it.text).put("u", it.fromUser)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun clear() = prefs.edit().remove("items").apply()
}

package com.jarvis.ai

import android.content.Context
import org.json.JSONArray

data class MemoryItem(val id: Long, val text: String)

/** Notes saved only when YOU approve them. Stored locally on this phone. */
class MemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    fun getAll(): List<MemoryItem> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            MemoryItem(o.getLong("id"), o.getString("text"))
        }
    }

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        save(getAll() + MemoryItem(System.currentTimeMillis(), clean))
    }

    fun delete(id: Long) = save(getAll().filter { it.id != id })

    fun clear() = save(emptyList())

    private fun save(items: List<MemoryItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(org.json.JSONObject().put("id", it.id).put("text", it.text))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}

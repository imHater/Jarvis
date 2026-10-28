package com.jarvis.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ChatMessage(val text: String, val fromUser: Boolean)

sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Failure(val message: String) : AiResult()
}

/** Talks to Google Gemini. Your API key is sent only to Google. */
class AiClient(private val settings: SecureSettings) {

    suspend fun ask(history: List<ChatMessage>, memories: List<MemoryItem>): AiResult =
        withContext(Dispatchers.IO) {
            val key = settings.apiKey
            if (key.isBlank()) {
                return@withContext AiResult.Failure(
                    "API key not set. Open Settings and paste your Gemini key."
                )
            }
            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "${settings.model}:generateContent"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", key)

                conn.outputStream.use { it.write(buildBody(history, memories).toByteArray()) }

                val code = conn.responseCode
                if (code == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    parse(text)
                } else {
                    conn.errorStream?.close()
                    AiResult.Failure(errorFor(code))
                }
            } catch (e: UnknownHostException) {
                AiResult.Failure("No internet connection. Offline commands still work.")
            } catch (e: SocketTimeoutException) {
                AiResult.Failure("The AI took too long to answer. Please try again.")
            } catch (e: Exception) {
                AiResult.Failure("Something went wrong: ${e.message}")
            }
        }

    private fun buildBody(history: List<ChatMessage>, memories: List<MemoryItem>): String {
        val system = StringBuilder()
        system.append("You are ${settings.assistantName}, a personal voice assistant. ")
        system.append("Personality: ${settings.personality} ")
        system.append("Reply in the same language the user uses (Hindi, Hinglish or English). ")
        system.append("Keep answers short because they are spoken aloud. Do not use markdown symbols. ")
        system.append("You cannot control the phone directly; never claim you performed phone actions. ")
        if (memories.isNotEmpty()) {
            system.append("Notes the user approved you to remember: ")
            memories.forEach { system.append("- ${it.text} ") }
        }

        val contents = JSONArray()
        history.takeLast(20).forEach {
            contents.put(
                JSONObject()
                    .put("role", if (it.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
            )
        }
        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.toString())))
            )
            .put("contents", contents)
            .toString()
    }

    private fun parse(json: String): AiResult {
        return try {
            val parts = JSONObject(json)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            if (sb.isBlank()) AiResult.Failure("The AI sent an empty answer. Try rephrasing.")
            else AiResult.Success(sb.toString().trim())
        } catch (e: Exception) {
            AiResult.Failure("Could not read the AI answer. Try rephrasing.")
        }
    }

    private fun errorFor(code: Int) = when (code) {
        400 -> "Request rejected (400). Check your API key and model name in Settings."
        401, 403 -> "API key not accepted ($code). Check the key in Settings."
        404 -> "Model name not found (404). Change the model name in Settings."
        429 -> "Free limit reached (429). Wait a minute or try again tomorrow."
        in 500..599 -> "Google's server has a problem ($code). Try again shortly."
        else -> "Unexpected error ($code)."
    }
}

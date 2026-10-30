package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import java.util.Calendar
import java.util.Locale

/** Everyday phone actions that Android allows normal apps to do. */
class SystemActions(private val context: Context) {

    fun handle(original: String, t: String): CommandResult? {
        val words = t.split(Regex("\\s+"))

        // Things Android does not allow: say so honestly.
        if (words.any { it == "unlock" }) {
            return CommandResult.Reply("I can't unlock the phone. Android doesn't allow apps to do that, for your safety.")
        }
        if (t.contains("screenshot") || t.contains("screen shot")) {
            return CommandResult.Reply("I can't take screenshots yet. For now press power and volume down together.")
        }

        // Timer
        if (words.contains("timer")) {
            val secs = parseSeconds(t)
            return if (secs > 0) CommandResult.Reply(setTimer(secs))
            else CommandResult.Reply("Tell me how long, like: timer 10 minute.")
        }

        // Alarm
        if (words.any { it == "alarm" || it == "reminder" }) {
            val time = if (words.contains("alarm")) parseAlarm(t) else null
            if (time != null) {
                val h = time.first
                val m = time.second
                val label = format12(h, m)
                return CommandResult.Confirm("Set an alarm for $label? Say yes or tap Yes.") {
                    setAlarm(h, m, label)
                }
            }
            val ok = launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            return CommandResult.Reply(if (ok) "Opening your clock app." else "I couldn't open the clock app.")
        }

        // Ringer mode
        if (words.any { it == "silent" || it == "mute" }) {
            return setRinger(AudioManager.RINGER_MODE_SILENT, "Silent mode is on.")
        }
        if (words.contains("vibrate")) {
            return setRinger(AudioManager.RINGER_MODE_VIBRATE, "Vibrate mode is on.")
        }
        if (listOf("ring mode", "normal mode", "sound on", "ringer on", "awaaz on", "sound chalu").any { t.contains(it) }) {
            return setRinger(AudioManager.RINGER_MODE_NORMAL, "Sound is back on.")
        }

        // Volume
        if (words.any { it == "volume" || it == "awaaz" || it == "aawaz" || it == "avaaz" }) {
            val up = words.any { it in setOf("up", "badhao", "badha", "zyada", "increase", "louder", "tez") }
            val down = words.any { it in setOf("down", "kam", "ghatao", "ghata", "decrease", "low", "dheere") }
            val max = words.any { it in setOf("full", "max", "maximum") }
            val min = words.any { it in setOf("min", "minimum", "zero") }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return when {
                max -> {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
                    CommandResult.Reply("Volume at maximum.")
                }
                min -> {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, AudioManager.FLAG_SHOW_UI)
                    CommandResult.Reply("Volume is low.")
                }
                up -> {
                    repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
                    CommandResult.Reply("Volume up.")
                }
                down -> {
                    repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
                    CommandResult.Reply("Volume down.")
                }
                else -> null
            }
        }

        // Wi-Fi, hotspot, data, Bluetooth...: Android does not let apps flip these directly,
        // so we open the right panel and you tap the switch.
        val toggleWords = setOf(
            "on", "off", "chalu", "band", "kholo", "khol", "open", "enable", "disable",
            "turn", "karo", "kar", "start", "stop", "jalao", "bandh"
        )
        if (words.any { it in toggleWords }) {
            when {
                words.any { it == "wifi" || it == "wi-fi" } -> {
                    val ok = if (Build.VERSION.SDK_INT >= 29) launch(Intent(Settings.Panel.ACTION_WIFI))
                    else launch(Intent(Settings.ACTION_WIFI_SETTINGS))
                    return CommandResult.Reply(
                        if (ok) "I opened the Wi-Fi panel. Android doesn't let apps switch Wi-Fi directly, so please tap the switch."
                        else "I couldn't open the Wi-Fi settings."
                    )
                }
                words.contains("hotspot") || t.contains("hot spot") || words.contains("tethering") -> {
                    val ok = launch(Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")) ||
                        launch(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    return CommandResult.Reply(
                        if (ok) "I opened the hotspot settings. Please switch it on there."
                        else "I couldn't open the hotspot settings."
                    )
                }
                t.contains("mobile data") || words.contains("internet") -> {
                    val ok = if (Build.VERSION.SDK_INT >= 29) launch(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    else launch(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    return CommandResult.Reply(
                        if (ok) "I opened the internet panel. Please tap the switch you need."
                        else "I couldn't open the internet settings."
                    )
                }
                words.contains("bluetooth") -> {
                    val ok = launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    return CommandResult.Reply(if (ok) "I opened Bluetooth settings. Please tap the switch." else "I couldn't open Bluetooth settings.")
                }
                t.contains("airplane") || t.contains("flight mode") -> {
                    val ok = launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                    return CommandResult.Reply(if (ok) "I opened the airplane mode settings." else "I couldn't open the settings.")
                }
                words.contains("brightness") || words.contains("display") -> {
                    val ok = launch(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                    return CommandResult.Reply(if (ok) "I opened the display settings." else "I couldn't open the settings.")
                }
                t.contains("battery saver") -> {
                    val ok = launch(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    return CommandResult.Reply(if (ok) "I opened battery saver." else "I couldn't open the settings.")
                }
                words.contains("location") || words.contains("gps") -> {
                    val ok = launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    return CommandResult.Reply(if (ok) "I opened the location settings." else "I couldn't open the settings.")
                }
            }
        }

        // YouTube search
        val yt = Regex("^(?:play|search|find)\\s+(.+?)\\s+(?:on|in)\\s+(?:youtube|yt)$").find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+(?:youtube|yt)\\s+(?:par|pe|per|me|mein)\\s+(?:search|dhundo|chalao|play|lagao|dikhao)(?:\\s+(?:karo|kar do|kar))?$")
                .find(t)?.groupValues?.get(1)
            ?: Regex("^(?:youtube|yt)\\s+(?:(?:par|pe|per|me|mein)\\s+)?(?:search\\s+)?(.+)$")
                .find(t)?.groupValues?.get(1)
        if (yt != null) {
            val q = clean(yt)
            if (q.isNotBlank() && q !in OPEN_WORDS) {
                val ok = openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(q))
                return CommandResult.Reply(if (ok) "Searching YouTube for $q." else "I couldn't open YouTube.")
            }
        }

        // Maps
        val place = Regex("^(?:navigate to|directions to|take me to|route to)\\s+(.+)$").find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+(?:ka rasta|ka route|tak ka rasta|par le chalo|ko le chalo|le chalo|navigate karo|ka map)(?:\\s+(?:batao|dikhao|dikha do))?$")
                .find(t)?.groupValues?.get(1)
        if (place != null && place.isNotBlank()) {
            val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
                .setPackage("com.google.android.apps.maps")
            val ok = launch(nav) ||
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
            return CommandResult.Reply(if (ok) "Opening maps for $place." else "I couldn't open maps.")
        }

        // Google search
        val g = Regex("^(?:google|search for|search)\\s+(.+)$").find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+(?:google\\s+(?:par|pe|per)\\s+)?(?:search|dhundo|dhoondo)(?:\\s+(?:karo|kar do|kar))?$")
                .find(t)?.groupValues?.get(1)
        if (g != null) {
            val q = clean(g)
            if (q.isNotBlank() && q !in OPEN_WORDS) {
                val ok = openUrl("https://www.google.com/search?q=" + Uri.encode(q))
                return CommandResult.Reply(if (ok) "Searching for $q." else "I couldn't open the browser.")
            }
        }

        return null
    }

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun openUrl(url: String): Boolean = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun clean(q: String): String {
        var s = q.trim()
        var prev: String
        do {
            prev = s
            s = s.replace(TRAIL, "").trim()
        } while (s != prev)
        return s
    }

    private fun setRinger(mode: Int, done: String): CommandResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = mode
            CommandResult.Reply(done)
        } catch (e: SecurityException) {
            launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            CommandResult.Reply("For silent mode, please allow JARVIS under Do Not Disturb access, then ask me again.")
        } catch (e: Exception) {
            CommandResult.Reply("I couldn't change the sound mode.")
        }
    }

    private fun parseSeconds(t: String): Int {
        var total = 0
        Regex("(\\d+)\\s*(hour|hours|hr|hrs|ghante|ghanta|minute|minutes|min|mins|mint|second|seconds|sec|secs)")
            .findAll(t).forEach {
                val n = it.groupValues[1].toIntOrNull() ?: 0
                val u = it.groupValues[2]
                total += when {
                    u.startsWith("h") || u.startsWith("g") -> n * 3600
                    u.startsWith("m") -> n * 60
                    else -> n
                }
            }
        return total.coerceAtMost(86400)
    }

    private fun setTimer(secs: Int): String {
        val base = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, secs)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Kabir")
        val silent = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val txt = if (secs % 60 == 0) "${secs / 60} minutes" else "$secs seconds"
        return when {
            launch(silent) -> "Timer set for $txt."
            launch(base) -> "I opened the clock app with the timer. Please start it there."
            else -> "I couldn't set the timer."
        }
    }

    private fun parseAlarm(t: String): Pair<Int, Int>? {
        val m = Regex("\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?").find(t) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = (m.groupValues[2].ifEmpty { "0" }).toIntOrNull() ?: return null
        if (h > 23 || min > 59) return null
        val marker = m.groupValues[3].replace(".", "")
        val pmWords = listOf("shaam", "sham", "raat", "night", "evening", "dopahar", "afternoon")
        val amWords = listOf("subah", "savere", "morning", "sabah")
        val isPm = marker == "pm" || (marker.isEmpty() && pmWords.any { t.contains(it) })
        val isAm = marker == "am" || (marker.isEmpty() && amWords.any { t.contains(it) })
        when {
            h > 12 || h == 0 -> { }
            isPm -> if (h < 12) h += 12
            isAm -> if (h == 12) h = 0
            else -> {
                // 1 to 12 with no am/pm: pick whichever comes next
                val now = Calendar.getInstance()
                val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val amH = if (h == 12) 0 else h
                val pmH = if (h == 12) 12 else h + 12
                val waitAm = (amH * 60 + min - nowMin + 1440) % 1440
                val waitPm = (pmH * 60 + min - nowMin + 1440) % 1440
                h = if (waitAm <= waitPm) amH else pmH
            }
        }
        return Pair(h, min)
    }

    private fun format12(h: Int, m: Int): String =
        String.format(Locale.US, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")

    private fun setAlarm(h: Int, m: Int, label: String): String {
        val base = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Kabir")
        val silent = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return when {
            launch(silent) -> "Alarm set for $label."
            launch(base) -> "I opened the clock app with the alarm. Please confirm it there."
            else -> "I couldn't set the alarm."
        }
    }

    private companion object {
        val OPEN_WORDS = setOf("kholo", "khol", "khol do", "open", "open karo", "start", "chalu", "chalu karo", "launch")
        val TRAIL = Regex("\\s+(?:search|dhundo|dhoondo|chalao|play|lagao|dikhao|dikha do)(?:\\s+(?:karo|kar do|kar|do))?$")
    }
}

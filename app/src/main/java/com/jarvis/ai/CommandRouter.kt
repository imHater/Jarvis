package com.jarvis.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CommandResult {
    object NotACommand : CommandResult()
    data class Reply(val text: String) : CommandResult()

    /** Needs the user's YES first. Permissions (if any) are requested after the yes. */
    data class Confirm(
        val prompt: String,
        val permissions: List<String> = emptyList(),
        val onYes: () -> String
    ) : CommandResult()
}

/** Simple phone commands that work without internet and without AI. */
class CommandRouter(
    private val context: Context,
    private val memory: MemoryStore
) {
    private val phone = PhoneActions(context)
    private val system = SystemActions(context)

    fun handle(input: String): CommandResult {
        val original = input.trim()
        val t = original.lowercase(Locale.getDefault())
        val words = t.split(Regex("\\s+"))

        if (t == "help" || t == "madad" || t.contains("what can you do") || t.contains("kya kar sakte")) {
            return CommandResult.Reply(HELP)
        }

        // Remember a note (needs confirmation)
        for (p in listOf("remember ", "yaad rakho ", "yaad rakh ", "note karo ")) {
            if (t.startsWith(p) && original.length > p.length) {
                val note = original.substring(p.length).trim()
                return CommandResult.Confirm("Save this note on your phone: \"$note\"?") {
                    memory.add(note)
                    "Saved."
                }
            }
        }

        // Time and date
        if (listOf("what time", "current time", "time kya", "kitne baje", "samay kya", "abhi time").any { t.contains(it) }) {
            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return CommandResult.Reply("It is $now.")
        }
        if (listOf("what date", "today's date", "todays date", "aaj ki date", "aaj ki tareekh", "tarikh").any { t.contains(it) }) {
            val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
            return CommandResult.Reply("Today is $d.")
        }

        // Flashlight
        if (words.any { it == "torch" || it == "flashlight" || it == "flash" }) {
            val off = words.any { it in setOf("off", "band", "bujha", "bujhao", "stop") }
            val on = words.any { it in setOf("on", "chalu", "jalao", "jala", "start") }
            return when {
                off -> CommandResult.Reply(setTorch(false))
                on -> CommandResult.Reply(setTorch(true))
                else -> CommandResult.Reply("Say \"torch on\" or \"torch off\".")
            }
        }

        // Calls: always asks first. Names are looked up in your contacts.
        val callTarget = Regex("^(?:call|dial|phone)\\s+(.+)$").find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+ko\\s+(?:call|phone|fone)(?:\\s+(?:karo|kar do|kar|lagao|laga do|lagana))?$")
                .find(t)?.groupValues?.get(1)
        if (callTarget != null) {
            return phone.callConfirm(callTarget.removePrefix("to ").trim())
        }

        // Alarms, timers, silent, volume, Wi-Fi panels, YouTube / Google / Maps search
        system.handle(original, t)?.let { return it }

        // Open apps / settings
        val openName = Regex("^(?:open|launch|start)\\s+(.+)$").find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+(?:kholo|khol do|khol|open karo|open kar do)$").find(t)?.groupValues?.get(1)
        if (openName != null) {
            val name = openName.removePrefix("the ").removeSuffix(" app").trim()
            if (name == "settings" || name == "setting") {
                val ok = launch(Intent(Settings.ACTION_SETTINGS))
                return CommandResult.Reply(if (ok) "Opening settings." else "I couldn't open settings.")
            }
            return CommandResult.Reply(openApp(name))
        }

        return CommandResult.NotACommand
    }

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }

    private fun openApp(name: String): String {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcher, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.getDefault()).contains(name)
        } ?: return "I couldn't find an app called \"$name\" on this phone."

        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "I found \"$name\" but couldn't open it."
        return if (launch(intent)) "Opening ${match.loadLabel(pm)}." else "I couldn't open \"$name\"."
    }

    private fun setTorch(on: Boolean): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                val c = cm.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return "This phone has no flashlight I can use."
            cm.setTorchMode(id, on)
            if (on) "Flashlight on." else "Flashlight off."
        } catch (e: Exception) {
            "I couldn't control the flashlight: ${e.message}"
        }
    }

    companion object {
        const val HELP = "I can: open apps, search YouTube or Google, start navigation, " +
            "set alarms and timers, silent or vibrate mode, volume, torch, open Wi-Fi, hotspot " +
            "and Bluetooth panels, call a contact, prepare an SMS, tell time and date, " +
            "and save notes. I always ask before calling or sending. " +
            "For everything else I use online AI, which needs internet."
    }
}

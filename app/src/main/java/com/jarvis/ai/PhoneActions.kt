package com.jarvis.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/** Calls and SMS drafts. Uses only permissions you give. */
class PhoneActions(private val context: Context) {

    private data class Contact(val name: String, val number: String)
    private class Resolved(val number: String?, val label: String, val error: String?)

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** If the AI reply is a call/SMS request, returns the action to run. Otherwise null. */
    fun fromAiText(raw: String): CommandResult.Confirm? {
        val m = TAG.find(raw) ?: return null
        val parts = m.groupValues[1].split("|").map { it.trim() }
        return when (parts.firstOrNull()?.uppercase()) {
            "CALL" -> {
                val who = parts.getOrNull(1).orEmpty()
                if (who.isBlank()) null else callConfirm(who)
            }
            "SMS" -> {
                val who = parts.getOrNull(1).orEmpty()
                val msg = parts.drop(2).joinToString("|").trim()
                if (who.isBlank() || msg.isBlank()) null else smsConfirm(who, msg)
            }
            else -> null
        }
    }

    fun callConfirm(who: String): CommandResult.Confirm {
        val byName = !isNumber(who)
        val perms = mutableListOf(Manifest.permission.CALL_PHONE)
        if (byName) perms.add(Manifest.permission.READ_CONTACTS)
        // A saved contact (by name) is called right away. A spoken/typed number still asks first.
        return CommandResult.Confirm("Call $who? Say yes or tap Yes.", perms, auto = byName) {
            val r = resolve(who)
            if (r.error != null) r.error else placeCall(r.number ?: "", r.label)
        }
    }

    fun smsConfirm(who: String, msg: String): CommandResult.Confirm {
        val perms = mutableListOf<String>()
        if (!isNumber(who)) perms.add(Manifest.permission.READ_CONTACTS)
        return CommandResult.Confirm(
            "Open the SMS app for $who with this message? You will press Send yourself. \"$msg\"",
            perms
        ) {
            val r = resolve(who)
            if (r.error != null) r.error else openSms(r.number ?: "", r.label, msg)
        }
    }

    private fun isNumber(s: String): Boolean =
        Regex("^\\+?[\\d\\s-]{6,}$").matches(s.trim())

    private fun resolve(who: String): Resolved {
        if (isNumber(who)) {
            val n = who.replace(Regex("[\\s-]"), "")
            return Resolved(n, n, null)
        }
        val matches = findContacts(who)
        if (matches.isEmpty()) {
            return Resolved(
                null, who,
                "I couldn't find \"$who\" in your contacts. Try the full name, or give a number."
            )
        }
        val exact = matches.filter { it.name.equals(who, ignoreCase = true) }
        val pool = if (exact.isNotEmpty()) exact else matches
        val names = pool.map { it.name.lowercase() }.distinct()
        if (names.size > 1) {
            val list = pool.map { it.name }.distinct().take(3).joinToString(", ")
            return Resolved(null, who, "I found several contacts: $list. Please say the full name.")
        }
        val c = pool.first()
        return Resolved(c.number, c.name, null)
    }

    private fun findContacts(query: String): List<Contact> {
        val out = mutableListOf<Contact>()
        val nameCol = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val numCol = ContactsContract.CommonDataKinds.Phone.NUMBER
        val cursor = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(nameCol, numCol),
                "$nameCol LIKE ?",
                arrayOf("%$query%"),
                null
            )
        } catch (e: Exception) {
            null
        } ?: return out
        cursor.use {
            val iName = it.getColumnIndex(nameCol)
            val iNum = it.getColumnIndex(numCol)
            while (it.moveToNext() && out.size < 20) {
                val n = it.getString(iName) ?: continue
                val num = it.getString(iNum) ?: continue
                out.add(Contact(n, num))
            }
        }
        return out
    }

    private fun placeCall(number: String, label: String): String {
        val digits = number.filter { it.isDigit() }
        if (digits in EMERGENCY) {
            return "I don't dial emergency numbers by myself. Please dial it yourself."
        }
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
            "Calling $label."
        } catch (e: SecurityException) {
            "Call permission is missing."
        } catch (e: Exception) {
            try {
                val i = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                "Calling $label."
            } catch (e2: Exception) {
                "I couldn't place the call."
            }
        }
    }

    /** Opens the SMS app with the number and text filled in. You press Send. */
    private fun openSms(number: String, label: String, text: String): String {
        return try {
            val clean = number.replace(Regex("[^+\\d]"), "")
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean"))
            i.putExtra("sms_body", text)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            "The message for $label is ready. Please press Send."
        } catch (e: Exception) {
            "I couldn't open the SMS app."
        }
    }

    private companion object {
        val TAG = Regex("\\[\\[(.+?)\\]\\]", RegexOption.DOT_MATCHES_ALL)
        val EMERGENCY = setOf("100", "101", "102", "108", "112", "1091", "1098", "911", "999")
    }
}

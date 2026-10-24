package com.callmony.lib

import com.callmony.model.contactOwnerId
import com.callmony.platform.Platform
import com.callmony.ui.kit.Toasts
import com.callmony.i18n.t

/* placeCall — the shared call flow (port of src/native/calls.ts, v1.6.13).
   100% the RN edition's ImmediateCall chain:
     CALL_PHONE granted  → ACTION_CALL — the DEFAULT DIALER app places the
                           call instantly (no keypad re-confirmation); the
                           number is never dropped into the keypad.
     permission missing  → runtime dialog once; on grant → instant call,
                           on deny → ACTION_DIAL pre-filled keypad.
     ACTION_DIAL always  → hands the number to the phone's default dialer
                           (pre-filled keypad) — the seamless transition.
   The attempt is logged in the app recents; the system call log picks up the
   real call once the dialer places it. Blocked check runs before everything. */

object Calls {
    fun isBlocked(number: String): Boolean {
        val norm = normalizePhone(number, AppStore.state.settings.countryCode)
        return AppStore.state.settings.blockedNumbers.contains(norm)
    }

    fun placeCall(number: String): String {
        if (number.isEmpty()) return "failed"
        if (isBlocked(number)) return "blocked"
        val ownerId = AppStore.contactByPhone(number)?.let { contactOwnerId(it.recordID) }
        AppStore.addCall(number, ownerId, "outgoing", via = "tel")
        return try {
            if (Platform.calls.canCall()) {
                Platform.calls.callNow(number)
                "placed"
            } else {
                // RN fallback chain: ask for CALL_PHONE once → instant call on
                // grant, pre-filled dialer keypad on deny.
                val asked = Platform.calls.requestPermission { granted ->
                    try {
                        if (granted) Platform.calls.callNow(number)
                        else Platform.calls.dial(number)
                    } catch (_: Exception) { Platform.calls.dial(number) }
                }
                if (!asked) {
                    // platform can't ask (iOS / no activity) → straight to the dialer
                    Platform.calls.dial(number)
                    "dialer"
                } else {
                    "dialer" // dialog up; grant → instant call, deny → keypad
                }
            }
        } catch (_: Exception) {
            try { Platform.calls.dial(number) } catch (_: Exception) {}
            "failed"
        }
    }

    /** placeCall + toast feedback (used by every call surface). */
    fun placeCallWithToast(number: String) {
        when (placeCall(number)) {
            "blocked" -> Toasts.toast(t("cd.blocked"), "error")
            "denied", "dialer" -> Toasts.toast(t("cl.continuedInDialer"), "info")
            "failed" -> Toasts.toast(t("act.call") + " ✕", "error")
        }
    }
}

/* WhatsApp / SMS / Telegram / mail openers (shared surfaces). */
object Messengers {
    fun waLink(number: String, text: String? = null) {
        val digits = normalizePhone(number, AppStore.state.settings.countryCode)
        val url = if (text != null) "https://wa.me/$digits?text=${urlEncode(text)}" else "https://wa.me/$digits"
        Platform.share.openUrl(url)
        AppStore.contactByPhone(number)?.let { AppStore.logMessage(contactOwnerId(it.recordID), "whatsapp", text ?: "") }
    }

    fun smsLink(number: String, text: String? = null) {
        // ACTION_SENDTO / smsto: — hands the conversation to the phone's
        // DEFAULT messaging app, pre-filled (the smooth transition the user
        // asked for; sms: via ACTION_VIEW only hits apps that declare it).
        Platform.share.openSms(number, text)
    }

    fun tgLink(handle: String) {
        Platform.share.openUrl(if (handle.startsWith("@") || handle.startsWith("http")) "https://t.me/${handle.removePrefix("@")}" else "https://t.me/+${handle}")
    }

    fun mailTo(address: String, subject: String? = null, body: String? = null) {
        val url = buildString {
            append("mailto:").append(address)
            val parts = mutableListOf<String>()
            if (!subject.isNullOrEmpty()) parts.add("subject=${urlEncode(subject)}")
            if (!body.isNullOrEmpty()) parts.add("body=${urlEncode(body)}")
            if (parts.isNotEmpty()) append("?").append(parts.joinToString("&"))
        }
        Platform.share.openUrl(url)
    }

    fun urlEncode(s: String): String = buildString {
        for (ch in s) {
            when {
                ch.isLetterOrDigit() || ch in "-_.~" -> append(ch)
                ch == ' ' -> append("%20")
                else -> {
                    for (b in ch.toString().encodeToByteArray()) {
                        append('%')
                        append(((b.toInt() shr 4) and 0xf).toString(16).uppercase())
                        append((b.toInt() and 0xf).toString(16).uppercase())
                    }
                }
            }
        }
    }
}

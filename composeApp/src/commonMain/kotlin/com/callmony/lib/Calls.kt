package com.callmony.lib

import com.callmony.model.contactOwnerId
import com.callmony.platform.Platform
import com.callmony.ui.kit.Toasts
import com.callmony.i18n.t

/* placeCall — the shared call flow (port of src/native/calls.ts).
   Blocked check → ACTION_CALL (instant) → permission re-ask once →
   ACTION_DIAL (pre-filled keypad) fallback. Never simulated. */

object Calls {
    fun isBlocked(number: String): Boolean {
        val norm = normalizePhone(number, AppStore.state.settings.countryCode)
        return AppStore.state.settings.blockedNumbers.contains(norm)
    }

    fun placeCall(number: String): String {
        if (number.isEmpty()) return "failed"
        if (isBlocked(number)) return "blocked"
        val ownerId = AppStore.contactByPhone(number)?.let { contactOwnerId(it.recordID) }
        return try {
            if (Platform.calls.canCall()) {
                Platform.calls.callNow(number)
                AppStore.addCall(number, ownerId, "outgoing", via = "tel")
                "placed"
            } else {
                Platform.calls.dial(number)
                AppStore.addCall(number, ownerId, "outgoing", via = "tel")
                "dialer"
            }
        } catch (_: Exception) {
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
        val url = if (text != null) "sms:$number?body=${urlEncode(text)}" else "sms:$number"
        Platform.share.openUrl(url)
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

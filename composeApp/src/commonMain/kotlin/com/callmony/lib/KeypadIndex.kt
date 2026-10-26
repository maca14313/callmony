package com.callmony.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.callmony.model.DeviceContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/* v1.6.16 — KeypadIndex: the keypad's live-match contact index, built ONCE
   per (contact-book, country-code) on a BACKGROUND dispatcher and shared
   through a Compose state.

   Why: the index normalizes every phone of every contact (several regex
   passes per number). With a real book that's thousands of regex operations,
   and it used to run synchronously inside KeypadSheet's first composition —
   the "keypad takes long to open" + typing jank report. The user typed
   faster than the main thread could recompose, and on low-RAM phones the
   sustained jank let the system kill the app mid-typing.

   Now: opening the keypad does ZERO index work (instant), the index builds
   on Dispatchers.Default (typically ready in <100 ms, long before the user
   has typed the 2 digits needed for matching), and every re-open reuses the
   cached entries. */

object KeypadIndex {

    class Entry(
        val c: DeviceContact,
        val nameLower: String,
        val t9: String,
        val phones: Array<Triple<String, String, String>>,
    ) {
        var score: Double = -1.0

        /** T9 name match — `keys` passed in explicitly (never a stale capture). */
        fun t9Match(keys: String): Boolean {
            if (keys.isEmpty() || t9.isEmpty()) return false
            if (t9.length < keys.length) return false
            for (i in 0..t9.length - keys.length) {
                var ok = true
                for (j in keys.indices) {
                    val lettersFor = T9_MAP[keys[j]] ?: continue
                    if (!lettersFor.contains(t9[i + j])) { ok = false; break }
                }
                if (ok) return true
            }
            return false
        }
    }

    /** The published index — a Compose state so the keypad re-renders once
     *  when a background (re)build lands. */
    var entries: List<Entry> by mutableStateOf(emptyList())
        private set

    // No @Volatile (JVM-only): ensure() is the single writer and runs on the
    // main/composition thread; the background builder only READS these for
    // its publish guard. Worst case on a stale read is one redundant rebuild.
    private var keyContacts: List<DeviceContact>? = null
    private var keyCc: String? = null
    private var job: Job? = null

    /** Cheap identity check + background (re)build. Safe to call from any
     *  composition — the hot path is a single reference comparison. */
    fun ensure(contacts: List<DeviceContact>, countryCode: String, scope: CoroutineScope) {
        if (keyContacts === contacts && keyCc == countryCode) {
            // already current (or a build for exactly this key is in flight)
            if (contacts.isEmpty()) entries = emptyList()
            return
        }
        keyContacts = contacts
        keyCc = countryCode
        if (contacts.isEmpty()) {
            entries = emptyList()
            return
        }
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            val built = build(contacts, countryCode)
            if (keyContacts === contacts && keyCc == countryCode) entries = built
        }
    }

    private val NON_DIGIT = Regex("\\D")
    private val NON_ALPHA = Regex("[^a-z]")

    fun build(contacts: List<DeviceContact>, countryCode: String): List<Entry> =
        contacts.map { c ->
            Entry(
                c = c,
                nameLower = displayName(c).lowercase(),
                t9 = displayName(c).lowercase().replace(NON_ALPHA, ""),
                phones = c.phones.map { p ->
                    Triple(
                        p.number.replace(NON_DIGIT, ""),
                        normalizePhone(p.number, countryCode).replace(NON_DIGIT, ""),
                        nationalDigits(p.number, countryCode),
                    )
                }.toTypedArray(),
            )
        }
}

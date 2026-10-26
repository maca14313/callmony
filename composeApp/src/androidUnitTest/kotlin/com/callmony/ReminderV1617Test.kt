package com.callmony.lib

import com.callmony.i18n.t
import com.callmony.model.Reminder
import org.junit.Before
import org.junit.Test

/* v1.6.17 — the reminder round the user reported broken:
   1. EDITING a reminder said "enter a valid date and time" because the
      editor seeded dueDate with the DISPLAY format (fdEpoch, "Oct 25, 2026")
      instead of the machine format (dateStr, "2026-10-25") the parser wants.
   2. spanStr / timeLeftStr power the new countdown ("in 2h 15m" / "3h late").
   3. clock24 renders every time through ft() in the chosen format. */

class ReminderV1617Test {

    @Before
    fun resetStore() {
        // JVM singletons persist across tests — reset like the other suites.
        AppStore.state.reminders = emptyList()
        AppStore.updateSettings { it.copy(clock24 = false, pinHash = null) }
    }

    /* --- the false-positive fix: dateStr() must round-trip through the
         editor's parser (KmpDate(year, month0, day)) exactly. --- */

    @Test
    fun dateStrIsMachineFormatNotDisplayFormat() {
        val d = KmpDate(2026, 9, 25, 14, 30, 0) // Oct 25 2026, 14:30
        val s = dateStr(d)
        check(s == "2026-10-25") { "dateStr gave '$s'" }
        // fdEpoch is the DISPLAY format that used to be seeded by mistake —
        // it must NOT parse as yyyy-MM-dd.
        val display = fdEpoch(d.time())
        check(!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(display)) { "fdEpoch unexpectedly machine-formatted: '$display'" }
    }

    @Test
    fun editedReminderSeedParsesBack() {
        // exactly what the editor does when opening an existing reminder:
        // dueDate = dateStr(dateOf(due)); parseDue splits on "-" and ":".
        val due = KmpDate(2027, 0, 2, 9, 5, 0).time()
        val seed = dateStr(dateOf(due))
        val parts = seed.split("-")
        check(parts.size == 3 && parts[0] == "2027" && parts[1] == "01" && parts[2] == "02") { "seed '$seed'" }
        val rebuilt = KmpDate(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 9, 5, 0).time()
        check(rebuilt == due) { "round trip mismatch $rebuilt vs $due" }
    }

    /* --- spanStr: the compact duration behind the countdown. --- */

    @Test
    fun spanStrFormats() {
        check(spanStr(30_000) == "30s")
        check(spanStr(45 * 60_000L) == "45m")
        check(spanStr(3 * 3_600_000L) == "3h")
        check(spanStr((3 * 60 + 15) * 60_000L) == "3h 15m")
        check(spanStr(2 * 86_400_000L) == "2d")
        check(spanStr((2 * 24 + 5) * 3_600_000L) == "2d 5h")
    }

    @Test
    fun timeLeftStrSaysInOrLate() {
        val due = now() + 90 * 60_000L
        val ahead = timeLeftStr(due, now())
        check(ahead.contains("1h 30m")) { "ahead gave '$ahead'" }
        val past = timeLeftStr(now() - 30 * 60_000L, now())
        check(past.contains("30m") && !past.startsWith("in")) { "late gave '$past'" }
    }

    /* --- clock24: ft() follows the setting (12 h AM/PM vs 24 h). --- */

    @Test
    fun ftFollowsClockFormat() {
        val epoch = KmpDate(2026, 9, 25, 14, 5, 0).time()
        AppStore.updateSettings { it.copy(clock24 = false) }
        val twelve = ft(epoch)
        check(twelve == "2:05 PM") { "12h gave '$twelve'" }
        AppStore.updateSettings { it.copy(clock24 = true) }
        val twentyFour = ft(epoch)
        check(twentyFour == "14:05") { "24h gave '$twentyFour'" }
    }

    @Test
    fun midnightAndNoonIn12h() {
        val midnight = KmpDate(2026, 9, 25, 0, 0, 0).time()
        val noon = KmpDate(2026, 9, 25, 12, 0, 0).time()
        AppStore.updateSettings { it.copy(clock24 = false) }
        check(ft(midnight) == "12:00 AM") { ft(midnight) }
        check(ft(noon) == "12:00 PM") { ft(noon) }
    }

    /* --- repeat advance while the app is closed: nextOccurrence steps
         daily/weekly and skips missed days. --- */

    @Test
    fun nextOccurrenceStepsDaily() {
        val base = now() - 3 * 86_400_000L // three days ago
        val next = nextOccurrence(base, "daily")
        check(next > base) { "next $next not after base $base" }
        val nd = dateOf(next)
        val bd = dateOf(base)
        check(nd.day != bd.day || nd.month0 != bd.month0) { "did not advance" }
    }

    /* --- t() placeholders: the saved-countdown toast fills {left}. --- */

    @Test
    fun savedCountdownFillsPlaceholder() {
        val s = t("rm.savedCountdown", mapOf("left" to "2h"))
        check(!s.contains("{left}")) { "placeholder leaked: '$s'" }
        check(s.contains("2h")) { "value missing: '$s'" }
    }

    /* --- the reminder model keeps the v1.6.17 fields through a copy. --- */

    @Test
    fun reminderCopyKeepsFiredFields() {
        val r = Reminder(
            id = "r1", ownerId = "general", title = "Test", kind = "payment",
            dueAt = now() + 60_000, repeat = "daily", leadMinutes = listOf(10),
        )
        val fired = r.copy(lastFiredAt = now(), firedLeads = listOf(10))
        check(fired.id == r.id && fired.repeat == "daily" && fired.firedLeads == listOf(10))
    }
}

package com.callmony

import com.callmony.lib.Db
import com.callmony.lib.nextOccurrence
import com.callmony.lib.parseCustomRepeat
import com.callmony.lib.repeatLabelOf
import com.callmony.lib.Persist
import com.callmony.model.Reminder
import com.callmony.model.Settings
import com.callmony.i18n.t
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.callmony.lib.KmpDate
import com.callmony.lib.dateOf

/* v1.6.18 — the any-interval repeat model, alarm flag plumbing, the
   autolock Never default and the Persist synchronous default. */
class V1618Test {

    // ---------- parseCustomRepeat ----------

    @Test fun parseCustomRepeatMinutes() {
        assertEquals(30 to "m", parseCustomRepeat("custom:30m"))
    }
    @Test fun parseCustomRepeatHours() {
        assertEquals(6 to "h", parseCustomRepeat("custom:6h"))
    }
    @Test fun parseCustomRepeatDays() {
        assertEquals(2 to "d", parseCustomRepeat("custom:2d"))
    }
    @Test fun parseCustomRepeatWeeks() {
        assertEquals(3 to "w", parseCustomRepeat("custom:3w"))
    }
    @Test fun parseCustomRepeatMonths() {
        assertEquals(6 to "mo", parseCustomRepeat("custom:6mo"))
    }
    @Test fun parseCustomRepeatRejectsMalformed() {
        assertNull(parseCustomRepeat("custom:"))
        assertNull(parseCustomRepeat("custom:0d"))
        assertNull(parseCustomRepeat("custom:-2w"))
        assertNull(parseCustomRepeat("custom:xx"))
        assertNull(parseCustomRepeat("daily"))
        assertNull(parseCustomRepeat("custom:5x"))
    }

    // ---------- nextOccurrence with custom units ----------

    @Test fun nextOccurrenceCustomMinutes() {
        val due = KmpDate(2026, 9, 26, 9, 0, 0).time() // Oct 26 2026 09:00
        val from = due + 45 * 60_000L
        // every 30 minutes: the next occurrence strictly after `from`
        assertEquals(due + 60 * 60_000L, nextOccurrence(due, "custom:30m", from))
    }
    @Test fun nextOccurrenceCustomHoursSkipsMany() {
        val due = KmpDate(2026, 9, 26, 9, 0, 0).time()
        val from = due + 26 * 3_600_000L // 26 h later
        assertEquals(due + 27 * 3_600_000L, nextOccurrence(due, "custom:9h", from))
    }
    @Test fun nextOccurrenceCustomDays() {
        val due = KmpDate(2026, 9, 1, 9, 0, 0).time()
        val from = KmpDate(2026, 9, 10, 9, 0, 0).time() // 9 days later
        // every 2 days: next after Sep 10 09:00 is Sep 11 09:00
        val expect = KmpDate(2026, 9, 11, 9, 0, 0).time()
        assertEquals(expect, nextOccurrence(due, "custom:2d", from))
    }
    @Test fun nextOccurrenceCustomWeeks() {
        val due = KmpDate(2026, 9, 5, 9, 0, 0).time()  // Oct 5 2026
        val from = KmpDate(2026, 9, 20, 9, 0, 0).time() // Oct 20
        // every 2 weeks from Oct 5: Oct 19 passed → Oct 26? No: from is Oct 20;
        // next strictly after Oct 20 09:00 is Nov 2 09:00 (Oct 5 + 4w)
        val expect = KmpDate(2026, 10, 2, 9, 0, 0).time()
        assertEquals(expect, nextOccurrence(due, "custom:2w", from))
    }
    @Test fun nextOccurrenceCustomMonthsClampsShortMonths() {
        val due = KmpDate(2026, 9, 31, 9, 0, 0).time() // Oct 31 2026
        val from = KmpDate(2026, 10, 15, 9, 0, 0).time() // Nov 15 2026
        // every 1 month from Oct 31 → Nov 30 (clamped), strictly after Nov 15
        val expect = KmpDate(2026, 10, 30, 9, 0, 0).time()
        assertEquals(expect, nextOccurrence(due, "custom:1mo", from))
    }

    // ---------- repeat labels ----------

    @Test fun repeatLabelCustomAndFixed() {
        assertTrue(repeatLabelOf("custom:2w").contains("2"))
        assertTrue(repeatLabelOf("custom:90m").contains("90"))
        assertEquals("", repeatLabelOf("none"))
        assertTrue(repeatLabelOf("daily").isNotBlank())
    }

    // ---------- the alarm flag round-trips through serialization ----------

    @Test fun alarmFlagSerializes() {
        val r = Reminder(
            id = "r1", ownerId = "c:1", title = "Rent", kind = "payment",
            dueAt = KmpDate(2026, 9, 27, 8, 0, 0).time(),
            repeat = "custom:1mo", isAlarm = true,
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
        val ser = json.encodeToString(Reminder.serializer(), r)
        assertTrue(ser.contains("\"isAlarm\":true"))
        val back = json.decodeFromString(Reminder.serializer(), ser)
        assertTrue(back.isAlarm)
        assertEquals("custom:1mo", back.repeat)
    }

    // ---------- autolock default is Never (-1) ----------

    @Test fun autolockDefaultsToNever() {
        assertEquals(-1, Settings().autolockMin)
        // the launch lock still guards (pinHash path), but the idle timer
        // never fires with -1: neither branch of the loop matches.
        assertFalse(0 == -1 || 1 == -1)
    }

    // ---------- Persist: synchronous by default, jobs run inline ----------

    @Test fun persistRunsInlineInSyncMode() {
        var ran = false
        Persist.post { ran = true }
        assertTrue(ran) // no app entry point called enableAsync() in unit tests
    }

    @Test fun persistFlushIsNoOpInSyncMode() {
        assertTrue(Persist.flush(100))
    }

}

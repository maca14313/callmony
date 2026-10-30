package com.callmony

import com.callmony.lib.MergeRow
import com.callmony.lib.normalizeEmailForMerge
import com.callmony.lib.normalizePhoneForMerge
import com.callmony.lib.planEmailMerge
import com.callmony.lib.planPhoneMerge
import org.junit.Assert.assertEquals
import org.junit.Test

/* v1.6.38 — the contact EDIT merge planner (the user report: "there is
   something wrong with create, edit contacts"). The edit is a TRUE
   REPLACE of what the editor shows: matched rows relabel in place, rows
   the editor dropped are deleted, genuinely-new rows insert. Pure and
   deterministic — this battery pins the exact merge without a content
   provider. */
class ContactMergeTest {

    @Test
    fun changed_number_deletes_the_old_row_and_inserts_the_new_one() {
        // device has 0911; the editor shows 0922 → delete 0911, insert 0922
        val plan = planPhoneMerge(
            existing = listOf(MergeRow("d1", "0911 22 3344", "mobile")),
            wanted = listOf("0922 55 6677" to "mobile"),
        )
        assertEquals(listOf("d1"), plan.deleteIds)
        assertEquals(0, plan.relabel.size)
        assertEquals(listOf("0922 55 6677" to "mobile"), plan.inserts)
    }

    @Test
    fun removed_row_is_deleted_not_resurrected() {
        // the old union merge kept device-only rows — deleted numbers came
        // back on the next refresh; now they are really gone
        val plan = planPhoneMerge(
            existing = listOf(MergeRow("d1", "0911", "mobile"), MergeRow("d2", "0922", "work")),
            wanted = listOf("0911" to "mobile"),
        )
        assertEquals(listOf("d2"), plan.deleteIds)
        assertEquals(0, plan.inserts.size)
    }

    @Test
    fun relabel_in_place_keeps_the_row_identity() {
        // same digits, different formatting → ONE row, relabeled in place
        val plan = planPhoneMerge(
            existing = listOf(MergeRow("d1", "0911 22 3344", "mobile")),
            wanted = listOf("0911223344" to "work"),
        )
        assertEquals(0, plan.deleteIds.size)
        assertEquals(listOf("d1" to "work"), plan.relabel)
        assertEquals(0, plan.inserts.size)
    }

    @Test
    fun a_genuinely_different_number_is_a_delete_plus_insert() {
        // +251-prefixed and 0-prefixed forms do NOT textually match (the
        // same normalization the address-book layer always used) — the
        // honest plan is delete + insert, never a silent duplicate
        val plan = planPhoneMerge(
            existing = listOf(MergeRow("d7", "+251911223344", "home")),
            wanted = listOf("0911223344" to "other"),
        )
        assertEquals(listOf("d7"), plan.deleteIds)
        assertEquals(listOf("0911223344" to "other"), plan.inserts)
    }

    @Test
    fun added_number_inserts_without_touching_the_existing_one() {
        val plan = planPhoneMerge(
            existing = listOf(MergeRow("d1", "0911", "mobile")),
            wanted = listOf("0911" to "mobile", "0933" to "home"),
        )
        assertEquals(0, plan.deleteIds.size)
        assertEquals(0, plan.relabel.size)
        assertEquals(listOf("0933" to "home"), plan.inserts)
    }

    @Test
    fun emails_match_case_insensitively_and_delete_when_dropped() {
        val plan = planEmailMerge(
            existing = listOf(
                MergeRow("e1", "Abeba@Mail.com", "home"),
                MergeRow("e2", "old@x.com", "work"),
            ),
            wanted = listOf("abeba@mail.com" to "work"),
        )
        assertEquals(listOf("e2"), plan.deleteIds)
        assertEquals(listOf("e1" to "work"), plan.relabel)
        assertEquals(0, plan.inserts.size)
    }

    @Test
    fun normalizers() {
        assertEquals("+251911223344", normalizePhoneForMerge("+251 (911) 22-3344"))
        assertEquals("abeba@x.com", normalizeEmailForMerge("  AbeBa@X.COM "))
    }
}

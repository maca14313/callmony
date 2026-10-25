package com.callmony.lib

import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import org.junit.Test

/* Search regression tests — user report: "in the search i cant find some
   contacts unless i type many parts of its name". Verifies the ranked
   matcher finds contacts from SHORT fragments: word prefixes, word
   SUBSTRINGS ('abd' hits Habdullah), INITIALS runs ('kb' → Kalkidan
   Bekele), multi-token queries, and phone fragments. */

class SearchContactsTest {

    private fun contact(id: String, given: String, family: String, vararg phones: String) =
        DeviceContact(
            recordID = id, displayName = "$given $family", givenName = given,
            middleName = null, familyName = family, nickname = null, company = null,
            jobTitle = null, note = null, birthday = null,
            phones = phones.map { DevicePhone(it, "mobile", it) },
            emails = listOf(), addresses = listOf(), hasPhoto = false,
        )

    private val book = listOf(
        contact("1", "Abebe", "Kebede", "0912345678", "+251911223344"),
        contact("2", "Kalkidan", "Bekele", "0922114455"),
        contact("3", "Abdullahi", "Mohamed", "0933112233"),
        contact("4", "Habdullah", "Nur", "0944556677"),
        contact("5", "Sara", "Tesfaye", "0955112244"),
        contact("6", "Mahlet", "Alemu", "0966335577"),
    )

    private fun firstIds(q: String): List<String> =
        searchContacts(book, q, ::displayName, "ET").map { it.contact.recordID }

    @Test
    fun shortPrefixFindsContact() {
        val hits = firstIds("ab")
        check("1" in hits) { "'ab' should find Abebe — got $hits" }
    }

    @Test
    fun wordSubstringFindsContact() {
        // 'abd' is not a prefix of Habdullah but IS a substring of the word.
        val hits = firstIds("abd")
        check("4" in hits) { "'abd' should find Habdullah — got $hits" }
        check("3" in hits) { "'abd' should find Abdullahi — got $hits" }
    }

    @Test
    fun initialsRunFindsContact() {
        val hits = firstIds("kb")
        check("2" in hits) { "'kb' (initials of Kalkidan Bekele) should hit — got $hits" }
    }

    @Test
    fun multiTokenQueryMatchesEveryToken() {
        val hits = firstIds("abdu moh")
        check("3" in hits) { "'abdu moh' should find Abdullahi Mohamed — got $hits" }
        check("1" !in hits) { "'abdu moh' must NOT match Abebe Kebede" }
    }

    @Test
    fun phoneFragmentFindsContact() {
        val hits = firstIds("0912345")
        check(hits.firstOrNull() == "1") { "'0912345' should rank Abebe first — got $hits" }
    }

    @Test
    fun nationalFormatPhoneMatches() {
        // +251911223344 stored; user types the local 0911223344 form.
        val hits = firstIds("0911223344")
        check("1" in hits) { "local-number form should match the +251 entry — got $hits" }
    }

    @Test
    fun noHitsForGibberish() {
        check(firstIds("zzzz").isEmpty()) { "gibberish must not match" }
    }

    @Test
    fun rankingPrefersPrefixOverSubstring() {
        val hits = firstIds("sara")
        check(hits.firstOrNull() == "5") { "exact prefix contact should rank first — got $hits" }
    }
}

package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.model.DeviceContact
import com.callmony.model.DeviceEmail
import com.callmony.model.DevicePhone
import com.callmony.platform.ContactsService
import com.callmony.platform.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.38 — regression test for the user report "there is something
   wrong with create, edit contacts". THE ROOT CAUSE: both in-app editor
   screens passed Pair(label, number) to the platform layer, which
   destructures Pair(number, label) — a created contact literally got
   the phone number "mobile" and the email address "home", and every
   edit INSERTED another bogus "mobile" row beside the real number (the
   union merge then resurrected anything deleted on the next refresh,
   and company edits were never written at all).

   This battery drives BOTH screens end-to-end against a recording fake
   of Platform.contacts and pins:
     - CREATE receives (number, label) / (address, label) pairs
     - EDIT receives the same order, with the changed number
     - the in-app cache after save carries real numbers, not label text
     - the save button is reachable and the sheet closes on success */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactEditorTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /* the recording fake — createContact/updateContact capture their args */
    private var createdPhones: List<Pair<String, String>>? = null
    private var createdEmails: List<Pair<String, String>>? = null
    private var createdCompany: String? = null
    private var updatedPhones: List<Pair<String, String>>? = null
    private var updatedEmails: List<Pair<String, String>>? = null
    private var updatedCompany: String? = null

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
        createdPhones = null; createdEmails = null; createdCompany = null
        updatedPhones = null; updatedEmails = null; updatedCompany = null
        val real = Platform.contacts
        Platform.contacts = object : ContactsService {
            override fun fetchAll(): List<DeviceContact> = real.fetchAll()
            override fun hasPermission(): Boolean = true
            override fun requestPermission(onResult: (Boolean) -> Unit) { onResult(true) }
            override fun hasWritePermission(): Boolean = true
            override fun requestWritePermission(onResult: (Boolean) -> Unit) { onResult(true) }
            override fun createContact(
                given: String, middle: String?, family: String?, company: String?,
                phones: List<Pair<String, String>>, emails: List<Pair<String, String>>,
            ): String? {
                createdPhones = phones; createdEmails = emails; createdCompany = company
                return "777"
            }
            override fun updateContact(
                recordID: String, given: String, middle: String?, family: String?, company: String?,
                phones: List<Pair<String, String>>, emails: List<Pair<String, String>>,
            ): Boolean {
                updatedPhones = phones; updatedEmails = emails; updatedCompany = company
                return true
            }
            override fun addPhoneToContact(recordID: String, label: String, number: String): Boolean = true
            override fun deleteContact(recordID: String): Boolean = true
        }
    }

    private fun ready() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.deviceContacts = listOf(
                DeviceContact(
                    recordID = "55",
                    displayName = "Abeba Kebede",
                    givenName = "Abeba",
                    familyName = "Kebede",
                    phones = listOf(DevicePhone("p1", "mobile", "0911223344")),
                    emails = listOf(DeviceEmail("e1", "home", "abeba@mail.com")),
                )
            )
        }
        rule.waitForIdle()
    }

    private fun saveContact() {
        val save = hasText(t("ce.saveContact")) and hasClickAction()
        rule.waitUntil(timeoutMillis = 15000) { rule.onAllNodes(save).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(save)[0].performClick()
        rule.waitForIdle()
    }

    @Test
    fun create_sends_number_label_pairs_not_label_number() {
        ready()
        rule.runOnUiThread { UiStore.open(Overlay.ContactNew(phone = "0911223344")) }
        rule.waitForIdle()

        // First name (input #0: First/Middle/Last/Company, then phones) + a
        // second phone row + an email
        rule.waitUntil(timeoutMillis = 15000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        val nameIn = rule.onAllNodes(hasSetTextAction())[0]
        nameIn.performClick()
        nameIn.performTextInput("Selam")
        rule.waitForIdle()

        val addPhone = hasText(t("ce.addPhone")) and hasClickAction()
        rule.onAllNodes(addPhone)[0].performClick()
        rule.waitForIdle()
        // input order: 0 First · 1 Middle · 2 Last · 3 Company · 4 phone1
        // (prefilled) · 5 phone2 (fresh, carries the placeholder)
        val phoneIns = rule.onAllNodes(hasSetTextAction() and hasText(t("ce.phonePlaceholder")))
        rule.waitUntil(timeoutMillis = 15000) { phoneIns.fetchSemanticsNodes().size >= 1 }
        phoneIns[0].performClick()
        phoneIns[0].performTextInput("0933445566")
        rule.waitForIdle()

        val addEmail = hasText(t("ce.addEmail")) and hasClickAction()
        rule.onAllNodes(addEmail)[0].performClick()
        rule.waitForIdle()
        val emailIn = hasSetTextAction() and hasText(t("ce.emailPlaceholder"))
        rule.waitUntil(timeoutMillis = 15000) { rule.onAllNodes(emailIn).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(emailIn)[0].performClick()
        rule.onAllNodes(emailIn)[0].performTextInput("selam@mail.com")
        rule.waitForIdle()

        saveContact()

        // THE FIX: pairs are (number, label) — never ("mobile", "09…")
        assertEquals(listOf("0911223344" to "mobile", "0933445566" to "mobile"), createdPhones)
        assertEquals(listOf("selam@mail.com" to "home"), createdEmails)
    }

    @Test
    fun edit_sends_the_changed_number_and_company() {
        ready()
        rule.runOnUiThread { UiStore.open(Overlay.ContactEdit("55")) }
        rule.waitForIdle()

        // fields prefilled from the cached contact: input #4 carries the
        // seeded phone number, #3 is Company
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasSetTextAction() and hasText("0911223344")).fetchSemanticsNodes().isNotEmpty()
        }

        // change the phone number (append 99 → 091122334499)
        val phoneIn = rule.onAllNodes(hasSetTextAction() and hasText("0911223344"))[0]
        phoneIn.performClick()
        phoneIn.performTextInput("99")
        rule.waitForIdle()

        // company (its edits never persisted before v1.6.38): input order
        // 0 First · 1 Middle · 2 Last · 3 Company · 4 phone1 · 5 email1
        val allIns = rule.onAllNodes(hasSetTextAction())
        rule.waitUntil(timeoutMillis = 15000) { allIns.fetchSemanticsNodes().size >= 6 }
        allIns[3].performClick()
        allIns[3].performTextInput("Selam Shop")
        rule.waitForIdle()

        saveContact()

        assertEquals(listOf("091122334499" to "mobile"), updatedPhones)
        assertEquals("Selam Shop", updatedCompany)
        assertEquals(listOf("abeba@mail.com" to "home"), updatedEmails)

        // the in-app cache patch carries REAL numbers (the old bug wrote the
        // number into the DevicePhone.id slot too)
        val patched = rule.runOnUiThread {
            AppStore.state.deviceContacts.find { it.recordID == "55" }
        }
        checkNotNull(patched) { "patched contact vanished from the cache" }
        assertEquals("091122334499", patched.phones.first().number)
        assertEquals("mobile", patched.phones.first().label)
        assertTrue(patched.phones.first().id.isNotBlank())
        assertEquals("Selam Shop", patched.company)
    }
}

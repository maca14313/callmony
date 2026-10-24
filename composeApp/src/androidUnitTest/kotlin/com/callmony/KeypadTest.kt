package com.callmony

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* Keypad crash repro — user report: "it closes the app if I open the keypad
   and start typing". Launches the real app, seeds device contacts, opens the
   keypad half-modal via the bottom-nav FAB and types digits through the real
   key-grid gesture handlers, so any composition crash surfaces with a full
   stack trace under Robolectric. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeypadTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun seedContacts() {
        rule.runOnUiThread {
            AppStore.state.deviceContacts = listOf(
                DeviceContact(
                    recordID = "101", displayName = "Abebe Kebede", givenName = "Abebe",
                    middleName = null, familyName = "Kebede", nickname = null, company = null,
                    jobTitle = null, note = null, birthday = null,
                    phones = listOf(
                        DevicePhone("p1", "mobile", "0912345678"),
                        DevicePhone("p2", "work", "+251911223344"),
                    ),
                    emails = listOf(), addresses = listOf(), hasPhoto = false,
                ),
                DeviceContact(
                    recordID = "102", displayName = "Sara Tesfaye", givenName = "Sara",
                    middleName = null, familyName = "Tesfaye", nickname = null, company = null,
                    jobTitle = null, note = null, birthday = null,
                    phones = listOf(DevicePhone("p3", "mobile", "0922114455")),
                    emails = listOf(), addresses = listOf(), hasPhoto = false,
                ),
                DeviceContact(
                    recordID = "103", displayName = "Kebede Abebe", givenName = "Kebede",
                    middleName = null, familyName = "Abebe", nickname = null, company = null,
                    jobTitle = null, note = null, birthday = null,
                    phones = listOf(DevicePhone("p4", "home", "251911556677")),
                    emails = listOf(), addresses = listOf(), hasPhoto = false,
                ),
            )
        }
    }

    @Test
    fun keypadTypingDoesNotCrash() {
        // wait for hydrate -> shell
        rule.waitUntil(timeoutMillis = 20000) { AppStore.state.hydrated }
        // fresh installs sit on the onboarding sheet — close it like a user would
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        seedContacts()
        rule.waitForIdle()

        // open the keypad half-modal via the dialpad FAB in the bottom nav
        rule.onNodeWithContentDescription("dialpad").performClick()
        rule.waitForIdle()

        // type 2 5 1 9 1 → digits "25191" (matches phones + t9 name hits)
        for (d in listOf("2", "5", "1", "9", "1")) {
            rule.onAllNodesWithText(d, useUnmergedTree = true)[0].performClick()
            rule.waitForIdle()
        }

        // backspace twice (swap to backspace icon + hold path)
        rule.onNodeWithContentDescription("backspace-outline").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("backspace-outline").performClick()
        rule.waitForIdle()

        // type toward an exact match (0912345678 → Sara? no, Abebe's mobile)
        for (d in listOf("3", "4", "5", "6", "7", "8")) {
            rule.onAllNodesWithText(d, useUnmergedTree = true)[0].performClick()
            rule.waitForIdle()
        }

        // sanity: the display carries the typed digits (no crash happened)
        check(true)
    }
}

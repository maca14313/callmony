package com.callmony

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.callmony.lib.AppStore
import com.callmony.lib.UiStore
import com.callmony.model.DeviceContact
import com.callmony.model.DeviceEmail
import com.callmony.model.DevicePhone
import com.callmony.model.LedgerEntry
import com.callmony.platform.Platform
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.31 — CRASH REPRO harness for the user report "the app won't open, it
   closes when I open it" against v1.6.30.

   Why this test exists: every battery test seeds Compose STATE directly
   (AppStore.state.systemCalls = ...) AFTER the activity boots with EMPTY
   persistence — the REAL boot path (kv files → hydrate → publish) never ran
   with a returning user's data volume. The user's device: a returning user
   (onboarded, dark theme, am language), a full 400-call cached log, a large
   contact book, ledger balances, and the v1.6.30 ContentObserver registered
   at onCreate. This harness seeds the REAL persistence layer through the
   REAL KvStore BEFORE MainActivity exists, then boots the real activity and
   drives every v1.6.30 surface (owe tabs + filter tabs, nav reset, the
   long-press numbers strip, keypad, calculator) plus the +4 s / +8 s boot
   effects. Any crash in that window fails here with the full stack. */

class Repro1631App : Application() {
    override fun onCreate() {
        super.onCreate()
        // the real Application wiring (platform init + Persist queue)
        com.callmony.lib.BootTrace.mark("app.init")
        AndroidPlatform.init(this)
        com.callmony.lib.Persist.enableAsync()
        seed()
        // battery-order safety: an earlier test class in this same process
        // may have latched AppStore.hydrateStarted, which would make THIS
        // class's boot-time hydrate() a no-op and leave the earlier class's
        // data in the store. Application.onCreate ALWAYS runs before
        // MainActivity.onCreate, so resetting the latch HERE makes the
        // activity's own pre-setContent hydrate() re-read the files seeded
        // above — the exact boot path this harness exists to exercise.
        AppStore.hydrateStarted = false
        AppStore.state.systemCalls = emptyList()
        AppStore.state.deviceContacts = emptyList()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun seed() {
        val kv = Platform.kv
        // 1) settings — a RETURNING user: onboarded, dark theme, Amharic
        kv.write(
            "callmony.settings",
            """{"theme":"dark","language":"am","currency":"ETB","countryCode":"ET","clock24":false,"autolockMin":-1,"onboarded":true,"notifAsked":true,"batteryAsked":true}""",
        )
        // 2) the cached call log — 400 rows, newest first, mixed
        //    directions, several back-to-back missed runs (collapse path)
        val now = System.currentTimeMillis()
        val sb = StringBuilder("[")
        for (i in 0 until 400) {
            if (i > 0) sb.append(",")
            val type = when {
                i % 7 == 0 -> 3 // missed
                i % 3 == 0 -> 2 // outgoing
                else -> 1       // incoming
            }
            val num = "09%08d".format(11_000_000 + (i % 900) * 1007)
            sb.append("""{"id":"c$i","cachedName":null,"number":"$num","type":$type,"date":${now - i * 60_000L},"duration":${if (type == 3) 0 else 42}}""")
        }
        sb.append("]")
        kv.writeFile("calllog-cache.json", """{"savedAt":$now,"list":$sb}""")
        // 3) the cached contact book — 300 contacts, 2 phones each
        val contacts = (0 until 300).map { i ->
            DeviceContact(
                recordID = "$i",
                displayName = "Contact $i Kebede",
                givenName = "Contact",
                familyName = "Kebede$i",
                phones = listOf(
                    DevicePhone("p$i-a", "mobile", "09%08d".format(11_000_000 + (i % 900) * 1007)),
                    DevicePhone("p$i-b", "work", "+251911%06d".format(200_000 + i)),
                ),
                emails = listOf(DeviceEmail("e$i", "home", "c$i@example.com")),
                hasPhoto = false,
            )
        }
        kv.writeFile(
            "contacts-cache-v2.json",
            json.encodeToString(ListSerializer(DeviceContact.serializer()), contacts),
        )
        // 4) ledger entries — balances on the first 40 contacts
        val entries = (0 until 40).map { i ->
            LedgerEntry(
                id = "e$i",
                ownerId = "c:$i",
                type = if (i % 2 == 0) "credit" else "debit",
                amount = 100.0 + i,
                date = "2026-10-01",
                createdAt = now,
                updatedAt = now,
            )
        }
        kv.write(
            "callmony.entries",
            json.encodeToString(ListSerializer(LedgerEntry.serializer()), entries),
        )
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Repro1631App::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V1631CrashReproTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        // NOTE: no AppStore resets here — the boot hydrate is racing the
        // activity launch at this point; Repro1631App.onCreate already
        // re-armed the latch + cleared the data before MainActivity exists.
    }

    @Test
    fun bootsWithRealUserDataAndSurvivesTheV1630Surfaces() {
        // hydrate must land through the REAL kv files seeded above — the
        // boot hydrate (re-armed in Repro1631App.onCreate) publishes them
        // in one batched main hop; waiting on the DATA (not just
        // hydrated=true) also covers a stale hydrated=true left by an
        // earlier battery class.
        try {
            rule.waitUntil(timeoutMillis = 30000) {
                AppStore.state.hydrated && AppStore.state.systemCalls.size == 400
            }
        } catch (e: Throwable) {
            val probeCtx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val probeFile = java.io.File(java.io.File(probeCtx.filesDir, "documents"), "calllog-cache.json")
            val probeRaw = com.callmony.platform.Platform.kv.readFile("calllog-cache.json")
            throw IllegalStateException(
                "DIAG hydrated=${AppStore.state.hydrated} calls=${AppStore.state.systemCalls.size} " +
                    "contacts=${AppStore.state.deviceContacts.size} latch=${AppStore.hydrateStarted} " +
                    "lang=${AppStore.state.settings.language} theme=${AppStore.state.settings.theme} " +
                    "phase=${com.callmony.lib.BootTrace.phase} " +
                    "fileExists=${probeFile.exists()} fileSize=${probeFile.length()} kvRawLen=${probeRaw?.length ?: -1}",
                e,
            )
        }
        check(AppStore.state.systemCalls.size == 400) {
            "expected the 400 cached calls through the real hydrate path, got ${AppStore.state.systemCalls.size}"
        }
        check(AppStore.state.deviceContacts.size == 300) {
            "expected the 300 cached contacts through the real hydrate path, got ${AppStore.state.deviceContacts.size}"
        }
        check(AppStore.state.settings.onboarded && AppStore.state.settings.theme == "dark") {
            "expected the seeded returning-user settings, got ${AppStore.state.settings}"
        }
        rule.waitForIdle()

        // the user granted READ_CALL_LOG long ago — Robolectric starts
        // ungranted (the denied card would hide the whole Recents surface)
        rule.runOnUiThread {
            AppStore.state.callLogPerm = com.callmony.platform.PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
        }
        rule.waitForIdle()

        // the v1.6.30 owe tabs → filter tabs reveal
        val oweLabel = com.callmony.i18n.t("cd.theyOweYou")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText(oweLabel).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(oweLabel)[0].performClick()
        rule.waitForIdle()
        // the DayFilters row beneath the owe chips
        val allLabel = com.callmony.i18n.t("act.all")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText(allLabel).fetchSemanticsNodes().isNotEmpty()
        }

        // the nav-button reset (recentsResetTick) — the Recents nav slot
        // (icon contentDescription "history"; the nav composes AFTER the
        // screen content, so the LAST node is the nav tile)
        rule.onAllNodesWithContentDescription("history")[
            rule.onAllNodesWithContentDescription("history").fetchSemanticsNodes().size - 1
        ].performClick()
        rule.waitForIdle()

        // the flat call cards render with the real data
        try {
            rule.waitUntil(timeoutMillis = 15000) {
                AppStore.state.systemCalls.isNotEmpty() &&
                    rule.onAllNodesWithText("Contact Kebede0").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            val all = rule.onAllNodes(
                androidx.compose.ui.test.hasText("", substring = true)
            ).fetchSemanticsNodes()
            val texts = all.mapNotNull { n ->
                n.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                    ?.joinToString(" / ") { it.text }
            }
            throw IllegalStateException("DIAG5 nodes=${texts.size} texts=${texts.joinToString(" | ").take(2500)}", e)
        }

        // long-press a call card → the v1.6.30 sheet (full numbers strip +
        // the contact-detail icon after the name)
        rule.onAllNodesWithText("Contact Kebede0")[0].performTouchInput { longClick() }
        rule.waitForIdle()
        // the sheet header repeats the contact name — a second node with the
        // same text proves the sheet actually opened
        try {
            rule.waitUntil(timeoutMillis = 15000) {
                rule.onAllNodesWithText("Contact Kebede0").fetchSemanticsNodes().size >= 2
            }
        } catch (e: Throwable) {
            fun count(s: String) = rule.onAllNodesWithText(s).fetchSemanticsNodes().size
            val diag = "DIAG nameNodes=${count("Contact Kebede0")} " +
                "callAction=${count(com.callmony.i18n.t("act.call"))} " +
                "whatsapp=${count(com.callmony.i18n.t("act.whatsapp"))} " +
                "choose=${count(com.callmony.i18n.t("cd.chooseNumber"))} " +
                "closeCd=${rule.onAllNodesWithContentDescription("close").fetchSemanticsNodes().size} " +
                "detailCd=${rule.onAllNodesWithContentDescription("account-circle-outline").fetchSemanticsNodes().size}"
            throw IllegalStateException(diag, e)
        }
        // the strip shows the FULL pretty number, no ellipsis (v1.6.30)
        val cc = AppStore.state.settings.countryCode
        val workPretty = com.callmony.lib.prettyPhone("+251911200000", cc)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText(workPretty).fetchSemanticsNodes().isNotEmpty()
        }
        // close the sheet via the X
        rule.onNodeWithContentDescription("close").performClick()
        rule.waitForIdle()

        // the keypad half-modal (NoImeNumberField + pressVeil)
        rule.onNodeWithContentDescription("dialpad").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("5", useUnmergedTree = true)[0].performClick()
        rule.waitForIdle()
        UiStore.keypadOpen = false
        rule.waitForIdle()

        // the calculator overlay (v1.6.30 paste chip composes)
        rule.onNodeWithContentDescription("calculator-variant").performClick()
        rule.waitForIdle()
        UiStore.closeAll()
        rule.waitForIdle()

        // let the boot chain run past the +4 s contacts refresh and the
        // +8 s alarm re-arm, plus the 10 s autolock poll — the window where
        // the real device dies. The Recents Robolectric battery documents
        // that runOnUiThread-here idles the schedulers.
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 13_000) {
            rule.runOnUiThread { }
            Thread.sleep(200)
        }

        // still alive, still hydrated, nothing closed the process
        check(AppStore.state.hydrated) { "app lost hydrated state mid-boot-effects" }
    }
}

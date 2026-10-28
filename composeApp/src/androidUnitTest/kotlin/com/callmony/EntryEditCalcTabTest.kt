package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.35 — regression test for the CALCULATOR / EXPRESSION-NOTE tab
   (user spec: a tab chip at the FAR LEFT, before Just amount — the
   annotated calculator: write math as a sentence with labels before or
   after every number, the arithmetic is extracted and totaled live, and
   the annotations stay editable after saving).

   Pinned end-to-end, in ONE boot (same harness notes as every compose
   harness here: hydrated is deliberately NOT reset in @Before; onboarded
   flips AFTER waitUntil(hydrated) so the boot's empty publish can never
   overwrite it):
     1. the tab strip carries the Calculator chip (first position)
     2. typing the user's own example live-totals to 500 Br with the
        extracted expression visible
     3. Save persists amount 500 AND the raw annotated text on exprNote
        (Store.saveEntry merges the field through — the original bug kept
        the first-ever exprNote forever)
     4. reopening that entry lands on the Calculator tab with the text
        prefilled and the total still live — editing it re-totals and the
        round trip works (amount AND exprNote) */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcTabTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
        // NOTE: state.hydrated is deliberately NOT reset — see class docs.
    }

    private fun ready() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()
    }

    @Test
    fun calcTabParsesLiveSavesAndRoundTrips() {
        ready()

        // open the NEW-ENTRY editor directly (the same overlay the ＋ opens)
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()

        // the editor is up: the classic Just amount chip is there…
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.tabJustAmount")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        // …and the new Calculator chip exists alongside it (far left)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.tabCalc")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }

        // switch to the CALCULATOR tab — the annotated input + its empty
        // result card appear (placeholder merges into the editable node,
        // exactly like every Field here)
        rule.onAllNodes(hasText(t("ee.tabCalc")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val calcInput = hasSetTextAction() and hasText(t("ee.calcPlaceholder"))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(calcInput).fetchSemanticsNodes().isNotEmpty()
        }

        // the user's own example, typed as one sentence
        rule.onAllNodes(calcInput)[0].performClick()
        rule.onAllNodes(calcInput)[0].performTextInput("ball 100 x 2 price + 300 transport")
        rule.waitForIdle()

        // the extraction card shows the math it found and the live total
        // (money() renders the ETB currency as "Br")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("100 × 2 + 300")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // Save persists the entry: amount follows the parsed total, the
        // raw annotated text rides along on exprNote
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val savedId = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { e ->
                e.amount == 500.0 && e.exprNote == "ball 100 x 2 price + 300 transport"
            }?.id
        }
        checkNotNull(savedId) {
            "annotated-calc entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }

        // REOPEN the saved entry — it lands on the Calculator tab with the
        // annotations prefilled and the total still live (only the ACTIVE
        // tab renders its form, so the field being there IS the assertion)
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(savedId, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("ball 100 x 2 price + 300 transport")).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // edit the annotation — the total follows live (300 → 350: 550).
        // TextREPLACEMENT, not append: the cursor's resting position after
        // a click is not deterministic on a filled field
        val editInput = hasSetTextAction() and hasText("ball 100 x 2 price + 300 transport")
        rule.onAllNodes(editInput)[0].performClick()
        rule.onAllNodes(editInput)[0].performTextReplacement("ball 100 x 2 price + 350 transport")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("550 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val updated = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { it.id == savedId }
        }
        checkNotNull(updated) { "edited entry vanished" }
        check(updated.amount == 550.0) { "edit did not re-total: ${updated.amount}" }
        check(updated.exprNote == "ball 100 x 2 price + 350 transport") { "exprNote not round-tripped: ${updated.exprNote}" }
    }
}

package com.callmony

import com.callmony.lib.CalcNote
import com.callmony.lib.CalcToken
import com.callmony.lib.calcLedgerTotal
import com.callmony.lib.calcSteps
import com.callmony.lib.evalCalcTokens
import com.callmony.lib.parseCalcLedger
import com.callmony.lib.parseCalcNote
import com.callmony.lib.quoteTextNumbers
import com.callmony.lib.serializeCalcLedger
import com.callmony.lib.serializeCalcNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/* v1.6.36 — the ANNOTATED CALCULATOR v2 engine (the chip model behind the
   entry editor's Calculator tab, per the user's updated spec: interlocking
   "Text + Calculator" rows — a label slot before/after every number and
   around the result — with live step-by-step intermediate results).

   This battery pins:
     - parseCalcNote: free text (the v1.6.35 grammar, incl. every old note
       ever saved) → structured chips, labels stuck to their numbers
     - serializeCalcNote: chips → the same free text, byte-stable
     - evalCalcTokens: the live total with the parser's exact semantics
       (precedence, dangling ops, ÷0, implicit +)
     - calcSteps: the step-by-step folds — the user's own example is
       "Step 1: 100 × 2 = 200 Br · Step 2: 200 + 300 = 500 Br" */
class CalcNoteTest {

    @Test
    fun the_user_example_round_trips_through_the_chips() {
        val n = parseCalcNote("ball 100 × 2 price + 300 transport")
        assertEquals(3, n.tokens.size)
        assertEquals(CalcToken(before = "ball", number = "100", op = "×"), n.tokens[0])
        assertEquals(CalcToken(number = "2", after = "price", op = "+"), n.tokens[1])
        assertEquals(CalcToken(number = "300", after = "transport"), n.tokens[2])
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        // serialize is the SAME text — old notes reopen as chips, new chips
        // stay plain readable text on the entry
        assertEquals("ball 100 × 2 price + 300 transport", serializeCalcNote(n))
    }

    @Test
    fun result_labels_live_around_the_total() {
        val n = parseCalcNote("ball 100 × 2 price + 300 transport = total 500 birr")
        assertEquals("total", n.resultBefore)
        assertEquals("birr", n.resultAfter)
        // the math never reads the result zone
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals(
            "ball 100 × 2 price + 300 transport = total 500 birr",
            serializeCalcNote(n),
        )
    }

    @Test
    fun keypad_built_chips_serialize_cleanly() {
        val n = CalcNote(
            tokens = listOf(
                CalcToken(number = "100", op = "×"),
                CalcToken(number = "2", op = "+"),
                CalcToken(number = "300"),
            ),
        )
        assertEquals("100 × 2 + 300", serializeCalcNote(n))
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        // a result label zone is only written when the user annotated the
        // total — never by default
        assertEquals(
            "100 × 2 + 300 = total 500",
            serializeCalcNote(n.copy(resultBefore = "total")),
        )
    }

    @Test
    fun the_users_step_by_step_example_folds_exactly_as_drawn() {
        val n = parseCalcNote("ball 100 × 2 price + 300 transport")
        val steps = calcSteps(n.tokens)
        assertEquals(2, steps.size)
        assertEquals("100 × 2", steps[0].expr)
        assertEquals(200.0, steps[0].value, 0.0)
        assertEquals("200 + 300", steps[1].expr)
        assertEquals(500.0, steps[1].value, 0.0)
    }

    @Test
    fun multiplication_folds_before_addition_in_the_steps() {
        val steps = calcSteps(parseCalcNote("100 + 200 × 3").tokens)
        assertEquals(700.0, evalCalcTokens(parseCalcNote("100 + 200 × 3").tokens)!!, 0.0)
        assertEquals(2, steps.size)
        assertEquals("200 × 3", steps[0].expr)
        assertEquals(600.0, steps[0].value, 0.0)
        assertEquals("100 + 600", steps[1].expr)
        assertEquals(700.0, steps[1].value, 0.0)
    }

    @Test
    fun single_number_has_no_intermediate_steps_but_a_total() {
        val n = parseCalcNote("rent 1200 deposit")
        assertEquals(1, n.tokens.size)
        assertTrue(calcSteps(n.tokens).isEmpty())
        assertEquals(1200.0, evalCalcTokens(n.tokens)!!, 0.0)
    }

    @Test
    fun glued_operators_and_labels_parse() {
        val n = parseCalcNote("fee 100+300")
        assertEquals(400.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("fee 100 + 300", serializeCalcNote(n))
        assertEquals(4.0, evalCalcTokens(parseCalcNote("2x2").tokens)!!, 0.0)
        assertEquals(42.0, evalCalcTokens(parseCalcNote("6×7").tokens)!!, 0.0)
    }

    @Test
    fun adjacent_numbers_gain_an_explicit_shown_plus() {
        val n = parseCalcNote("100 200")
        assertEquals(300.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("100 + 200", serializeCalcNote(n))
    }

    @Test
    fun dangling_operator_is_kept_but_never_evaluated() {
        val n = parseCalcNote("ball 100 +")
        assertEquals(1, n.tokens.size)
        assertEquals("+", n.tokens[0].op) // kept: the chain is mid-typing
        assertEquals(100.0, evalCalcTokens(n.tokens)!!, 0.0) // but ignored
        assertEquals("ball 100 +", serializeCalcNote(n))
    }

    @Test
    fun division_by_zero_drops_the_operator() {
        val n = parseCalcNote("100 ÷ 0")
        assertEquals(100.0, evalCalcTokens(n.tokens)!!, 0.0)
        val steps = calcSteps(n.tokens)
        assertEquals(1, steps.size)
        assertEquals("100 ÷ 0", steps[0].expr)
        assertEquals(100.0, steps[0].value, 0.0)
    }

    @Test
    fun decimals_commas_and_arabic_digits() {
        assertEquals(3.25, evalCalcTokens(parseCalcNote("1,5 x 2 + 0.25").tokens)!!, 0.0)
        assertEquals(150.0, evalCalcTokens(parseCalcNote("شحن ١٠٠ + ٥٠").tokens)!!, 0.0)
        // the Arabic label survives the round trip, the digits normalize
        assertEquals("شحن 100 + 50", serializeCalcNote(parseCalcNote("شحن ١٠٠ + ٥٠")))
    }

    @Test
    fun words_without_numbers_are_just_labels() {
        assertTrue(parseCalcNote("hello world").tokens.isEmpty())
        assertTrue(parseCalcNote("").tokens.isEmpty())
        assertNull(evalCalcTokens(emptyList()))
    }

    @Test
    fun labels_survive_edits_and_deletion() {
        val base = parseCalcNote("ball 100 × 2 price")
        // editing a label (chip → text) and writing it back
        val edited = base.copy(
            tokens = base.tokens.mapIndexed { i, tk ->
                if (i == 1) tk.copy(after = "price in birr") else tk
            },
        )
        assertEquals("ball 100 × 2 price in birr", serializeCalcNote(edited))
        // deleting a label (empty submit) removes it from the text
        val deleted = edited.copy(
            tokens = edited.tokens.mapIndexed { i, tk ->
                if (i == 0) tk.copy(before = "") else tk
            },
        )
        assertEquals("100 × 2 price in birr", serializeCalcNote(deleted))
    }

    @Test
    fun multi_word_labels_stay_one_label() {
        val n = parseCalcNote("ball 100 × 2 price in birr + 300 transport cost")
        assertEquals("price in birr", n.tokens[1].after)
        assertEquals("transport cost", n.tokens[2].after)
        assertEquals("ball 100 × 2 price in birr + 300 transport cost", serializeCalcNote(n))
    }

    /* ---------------- v1.6.37 — the stacked ledger ---------------- */

    @Test
    fun ledger_parses_multi_line_notes_into_lines() {
        val lines = parseCalcLedger("ball 100 × 2\n50 + 25\n")
        assertEquals(2, lines.size)
        assertEquals("ball", lines[0].tokens[0].before)
        assertEquals(200.0, evalCalcTokens(lines[0].tokens)!!, 0.0)
        assertEquals(75.0, evalCalcTokens(lines[1].tokens)!!, 0.0)
        // a legacy single-line note is a one-line ledger
        assertEquals(1, parseCalcLedger("ball 100 × 2 price + 300 transport").size)
        // blank input is an empty ledger
        assertTrue(parseCalcLedger("").isEmpty())
        assertTrue(parseCalcLedger(" \n  ").isEmpty())
    }

    @Test
    fun ledger_round_trips_through_the_save() {
        // what the editor serializes on Save: committed lines + the active
        // line, "\n"-separated, each in the same free-text grammar
        val lines = listOf(
            parseCalcNote("ball 100 × 2"),
            parseCalcNote("50 + 25"),
        )
        assertEquals("ball 100 × 2\n50 + 25", serializeCalcLedger(lines))
        // the round trip is stable
        assertEquals(lines, parseCalcLedger(serializeCalcLedger(lines)))
        // a line whose chain was emptied drops out entirely
        assertEquals("50 + 25", serializeCalcLedger(listOf(CalcNote(), parseCalcNote("50 + 25"))))
    }

    @Test
    fun ledger_total_sums_every_line() {
        val lines = parseCalcLedger("ball 1 × 200\n2 × 150\n25")
        assertEquals(525.0, calcLedgerTotal(lines)!!, 0.0)
        // nothing that computes → null (no bar)
        assertNull(calcLedgerTotal(emptyList()))
        assertNull(calcLedgerTotal(listOf(CalcNote())))
    }

    /* ---------------- v1.6.38 — the % key (phone-calculator semantics) ---------------- */

    @Test
    fun percent_after_plus_is_percent_of_the_running_total() {
        // the user's market math: 200 + 10% = 220
        val n = parseCalcNote("200 + 10%")
        assertEquals(2, n.tokens.size)
        assertTrue(n.tokens[1].pct)
        assertEquals(220.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("200 + 10%", serializeCalcNote(n))
    }

    @Test
    fun percent_after_minus_discounts() {
        val n = parseCalcNote("500 - 5%")
        assertEquals(475.0, evalCalcTokens(n.tokens)!!, 0.0)
        // a SPACED "%" glues onto the number before it, same thing
        assertEquals(475.0, evalCalcTokens(parseCalcNote("500 - 5 %").tokens)!!, 0.0)
    }

    @Test
    fun percent_after_multiply_is_the_plain_fraction() {
        val n = parseCalcNote("200 × 10%")
        assertEquals(20.0, evalCalcTokens(n.tokens)!!, 0.0)
    }

    @Test
    fun chained_percent_uses_the_running_total() {
        // 100 + 50 = 150, then 10% of 150 = 15 → 165 (the classic behaviour)
        assertEquals(165.0, evalCalcTokens(parseCalcNote("100 + 50 + 10%").tokens)!!, 0.0)
    }

    @Test
    fun percent_steps_fold_at_the_resolved_value() {
        val steps = calcSteps(parseCalcNote("200 + 10%").tokens)
        assertEquals(1, steps.size)
        assertEquals(220.0, steps[0].value, 0.0)
    }

    @Test
    fun percent_survives_the_full_round_trip_and_mixed_labels() {
        val n = parseCalcNote("ball 200 + 10% tax = total 220 birr")
        assertEquals("ball 200 + 10% tax = total 220 birr", serializeCalcNote(n))
        assertEquals(220.0, evalCalcTokens(n.tokens)!!, 0.0)
    }
    /* ---------------- v1.6.40 — TEXT-MODE numbers never calculate ---------------- */

    @Test
    fun text_mode_numbers_are_quoted_into_words() {
        // "2 rooms" typed entirely on the QWERTY: the 2 is an annotation
        val marked = quoteTextNumbers("2 rooms", setOf(0))
        assertEquals("\"2\" rooms", marked)
        val n = parseCalcNote(marked)
        assertTrue("the quoted 2 must not be a token", n.tokens.isEmpty())
        assertEquals("\"2\" rooms", n.text)
        assertNull(evalCalcTokens(n.tokens))
    }

    @Test
    fun keypad_numbers_still_calculate() {
        // nothing typed on the QWERTY → the marker changes nothing
        assertEquals("ball 200", quoteTextNumbers("ball 200", emptySet()))
        assertEquals(200.0, evalCalcTokens(parseCalcNote("ball 200").tokens)!!, 0.0)
    }

    @Test
    fun quoted_marker_survives_the_round_trip() {
        val n = parseCalcNote("\"2\" rooms 500")
        // the 500 (keypad) is the ONLY math; the quoted 2 rides its label
        assertEquals(1, n.tokens.size)
        assertEquals("\"2\" rooms", n.tokens[0].before)
        assertEquals("500", n.tokens[0].number)
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("\"2\" rooms 500", serializeCalcNote(n))
    }

    @Test
    fun a_line_of_only_text_is_a_pure_annotation() {
        val n = parseCalcNote("just words no math")
        assertTrue(n.tokens.isEmpty())
        assertEquals("just words no math", n.text)
        assertEquals("just words no math", serializeCalcNote(n))
        // its total is nothing — it never enters the ledger sum
        assertNull(calcLedgerTotal(listOf(n)))
    }

    @Test
    fun the_ledger_keeps_annotation_lines_without_totalling_them() {
        val lines = parseCalcLedger("note only\n100 + 200")
        assertEquals(2, lines.size)
        assertEquals(300.0, calcLedgerTotal(lines)!!, 0.0)
        assertEquals("note only\n100 + 200", serializeCalcLedger(lines))
    }

    @Test
    fun old_unquoted_notes_still_calculate_exactly_as_before() {
        // back-compat: a v1.6.39 note has no markers — every number counts
        val n = parseCalcNote("ball 100 × 2 price + 300 transport")
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("ball 100 × 2 price + 300 transport", serializeCalcNote(n))
    }

    @Test
    fun quoted_number_after_an_operator_is_a_label_not_an_operand() {
        // keypad 100, QWERTY "+ 5": the 5 annotates, the math stays 100
        val marked = quoteTextNumbers("100 + 5", setOf(6))
        val n = parseCalcNote(marked)
        assertEquals(100.0, evalCalcTokens(n.tokens)!!, 0.0)
    }
    /* ---------------- v1.6.41 — the text-mode colour spans ---------------- */

    @Test
    fun quoted_numbers_carry_the_textmode_colour_span() {
        val s = com.callmony.ui.overlays.coloredLockedLine(
            "\"2\" rooms 500", androidx.compose.ui.graphics.Color.Gray, com.callmony.ui.overlays.TEXTMODE_COLOR,
        )
        assertEquals(2, s.spanStyles.size)
        // the quoted token "2" is the TEXTMODE colour; the whole line is the base
        val quoted = s.spanStyles.first { it.item.color == com.callmony.ui.overlays.TEXTMODE_COLOR }
        assertEquals(0, quoted.start)
        assertEquals(3, quoted.end) // "2" with its quotes
    }
}

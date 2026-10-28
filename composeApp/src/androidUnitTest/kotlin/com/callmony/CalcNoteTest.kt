package com.callmony

import com.callmony.lib.CalcNote
import com.callmony.lib.CalcToken
import com.callmony.lib.calcSteps
import com.callmony.lib.evalCalcTokens
import com.callmony.lib.parseCalcNote
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
}

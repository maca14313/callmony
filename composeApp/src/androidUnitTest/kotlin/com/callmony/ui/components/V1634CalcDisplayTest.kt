package com.callmony.ui.components

import org.junit.Test

/* v1.6.34 regression — the calculator DISPLAY is a real editable field now
   (the keypad's NoImeNumberField: native long-press text menu with
   copy/paste/cut + selection handles — "PASTE should be what someone can
   do in a normal input"). Whatever the native menu lands in the field is
   mapped through mapDisplayExpr: ASCII operator spellings and the comma
   decimal become the display glyphs, anything the calculator cannot
   evaluate is dropped, and the length is capped so the autosizing font
   stays readable. */
class V1634CalcDisplayTest {

    @Test
    fun asciiOperatorsAndCommaDecimalMapToDisplayGlyphs() {
        check(mapDisplayExpr("5-3") == "5−3") { mapDisplayExpr("5-3") }
        check(mapDisplayExpr("12*3/4") == "12×3÷4") { mapDisplayExpr("12*3/4") }
        check(mapDisplayExpr("1,5+2") == "1.5+2") { mapDisplayExpr("1,5+2") }
    }

    @Test
    fun foreignCharactersAreDropped() {
        check(mapDisplayExpr("abc 12 def 3") == "123") { mapDisplayExpr("abc 12 def 3") }
        check(mapDisplayExpr("50%") == "50") { mapDisplayExpr("50%") }
        check(mapDisplayExpr("+251 91 234 5678") == "+251912345678") { mapDisplayExpr("+251 91 234 5678") }
    }

    @Test
    fun lengthIsCapped() {
        val long = "1".repeat(200)
        check(mapDisplayExpr(long).length == 80) { mapDisplayExpr(long).length }
    }

    @Test
    fun mappedResultsEvaluate() {
        check(evalExpression(mapDisplayExpr("12*3/4")) == 9.0)
        check(evalExpression(mapDisplayExpr("1,5+2")) == 3.5)
    }
}

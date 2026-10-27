package com.callmony.ui.components

import org.junit.Test

/* v1.6.30 regression — the calculator paste (user report: "in the calculator
   i should copy and paste in the display just like other calculators and
   normal inputs"). sanitizePastedExpr turns arbitrary clipboard text into a
   valid display expression (or ""), mapping ASCII operator spellings and the
   comma decimal to the display glyphs, collapsing operator runs, trimming
   dangling operators, and falling back to the first plain number when the
   kept text doesn't evaluate (copied phone numbers, prose, prices). */
class V1630Test {

    @Test
    fun asciiOperatorsMapToDisplayGlyphs() {
        check(sanitizePastedExpr("5-3") == "5−3") { sanitizePastedExpr("5-3") }
        check(sanitizePastedExpr("12*3/4") == "12×3÷4") { sanitizePastedExpr("12*3/4") }
        // every sanitized result must actually evaluate
        check(evalExpression(sanitizePastedExpr("12*3/4")) == 9.0)
        check(evalExpression(sanitizePastedExpr("5-3")) == 2.0)
    }

    @Test
    fun commaDecimalAndSpaces() {
        check(sanitizePastedExpr("1,5 + 2") == "1.5+2") { sanitizePastedExpr("1,5 + 2") }
        check(evalExpression(sanitizePastedExpr("1,5 + 2")) == 3.5)
    }

    @Test
    fun operatorRunsCollapseAndDanglingOpsTrim() {
        check(sanitizePastedExpr("5++3") == "5+3") { sanitizePastedExpr("5++3") }
        check(sanitizePastedExpr("12+") == "12") { sanitizePastedExpr("12+") }
        check(sanitizePastedExpr("×7") == "7") { sanitizePastedExpr("×7") }
    }

    @Test
    fun nonEvaluatingTextFallsBackToFirstNumber() {
        // a copied phone number: the leading "+" can't evaluate → the number
        check(sanitizePastedExpr("+251 911 223 344") == "251911223344") {
            sanitizePastedExpr("+251 911 223 344")
        }
        // prose with a price
        check(sanitizePastedExpr("total: 45.50 USD") == "45.50") {
            sanitizePastedExpr("total: 45.50 USD")
        }
        // double-dot number → first well-formed number
        check(sanitizePastedExpr("1.2.3") == "1.2") { sanitizePastedExpr("1.2.3") }
    }

    @Test
    fun garbageAndEmptyYieldNothing() {
        check(sanitizePastedExpr("") == "")
        check(sanitizePastedExpr("hello world") == "")
        check(sanitizePastedExpr(",,,") == "")
        // a valid expression survives untouched when it already evaluates
        check(sanitizePastedExpr("2×2") == "2×2")
        check(evalExpression(sanitizePastedExpr("2×2")) == 4.0)
    }
}

package com.callmony.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.UiStore
import com.callmony.lib.fmtSig
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.theme.ThemeColors
import com.callmony.ui.kit.C
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.vibrate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/* Calculator — full expression in the main display, backspace with
   hold-to-repeat (450 ms → every 110 ms), memory keys, %, ±, history
   (last 30) and the "use value" flow for amount fields.
   Port of src/components/Calculator.tsx. */

private val OP_CHARS = setOf('+', '−', '×', '÷')

/* v1.6.13 FIX: the shunting-yard normalizes '−'→'-', '×'→'*', '÷'→'/' BEFORE
   tokenizing, so the evaluator feeds ASCII operators here — but this when()
   only matched the UNICODE glyphs, so every −, ×, ÷ expression fell into
   `else -> b` and just returned the LAST OPERAND ("2 × 2 = 2.0000000001" —
   the user's exact report). Match BOTH spellings. */
private fun applyOp(op: Char, a: Double, b: Double): Double = when (op) {
    '+' -> a + b
    '-', '−' -> a - b
    '*', '×' -> a * b
    '/', '÷' -> if (b == 0.0) Double.NaN else a / b
    else -> b
}

/** Safe expression evaluator — shunting-yard, never eval. */
fun evalExpression(src: String): Double? {
    val normalized = src.replace('×', '*').replace('÷', '/').replace('−', '-')
    val tokens = Regex("(\\d+\\.?\\d*|\\.\\d+|[+\\-*/])").findAll(normalized).map { it.value }.toList()
    if (tokens.isEmpty()) return null
    val prec = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)
    val out = mutableListOf<String>()
    val ops = mutableListOf<String>()
    for (tk in tokens) {
        if (tk.length == 1 && tk[0] in "+-*/") {
            while (ops.isNotEmpty() && (prec[ops.last()] ?: 0) >= (prec[tk] ?: 0)) out.add(ops.removeAt(ops.size - 1))
            ops.add(tk)
        } else out.add(tk)
    }
    while (ops.isNotEmpty()) out.add(ops.removeAt(ops.size - 1))
    val stack = mutableListOf<Double>()
    for (tk in out) {
        if (tk.length == 1 && tk[0] in "+-*/") {
            if (stack.size < 2) return null
            val b = stack.removeAt(stack.size - 1)
            val a = stack.removeAt(stack.size - 1)
            stack.add(applyOp(tk[0], a, b))
        } else {
            val n = tk.toDoubleOrNull() ?: return null
            stack.add(n)
        }
    }
    if (stack.size != 1) return null
    val res = kotlin.math.round(stack[0] * 1e10) / 1e10
    return if (res.isNaN() || res.isInfinite()) null else res
}

fun fmtResult(n: Double): String {
    if (n.isNaN() || n.isInfinite()) return n.toString()
    // ≥1e12 → exponential (spec, same as the RN edition)
    if (kotlin.math.abs(n) >= 1e12) return n.toString()
    // integers render exactly (2 × 2 → "4", never "4.000000001")
    val rounded = kotlin.math.round(n * 1e10) / 1e10
    if (rounded == kotlin.math.floor(rounded) && !rounded.isInfinite() && kotlin.math.abs(rounded) < 1e15)
        return rounded.toLong().toString()
    // decimals: 12 significant digits with trailing zeros trimmed — kills all
    // double noise (0.1+0.2 → "0.3", not "0.30000000000000004") and never
    // switches to scientific notation below 1e12 (Double.toString does at 1e7)
    return fmtSig(n, 12)
}

private data class HistItem(val expr: String, val result: String)

@Composable
fun CalculatorSheet() {
    val C = C()
    val pop = { UiStore.pop() }
    val pickHandler = UiStore.calculatorPickHandler

    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var justEvaluated by remember { mutableStateOf(false) }
    var memory by remember { mutableStateOf<Double?>(null) }
    var history by remember { mutableStateOf(listOf<HistItem>()) }
    var showHistory by remember { mutableStateOf(false) }
    // v1.6.27 — the memory keys live in an ADVANCED panel now (user report:
    // "we don't need the m+ m- mr, make them in advanced, the advanced icon
    // should be beside the history icon") — toggled by the Σ icon that sits
    // directly beside the history icon in the header.
    var showAdvanced by remember { mutableStateOf(false) }
    // v1.6.27 — pressed-state for the hold-to-repeat backspace key
    var backHeld by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    val currentResult: String? = result ?: expr.takeIf { it.isNotEmpty() }?.let { e ->
        evalExpression(e)?.let { fmtResult(it) }
    }
    val displayMain = if (justEvaluated && result != null) result!! else expr.ifEmpty { "0" }
    val displaySub = if (justEvaluated && result != null) expr else currentResult

    fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
    }

    DisposableEffect(Unit) {
        onDispose { stopRepeat() }
    }

    fun backspace() {
        justEvaluated = false
        result = null
        if (expr.isNotEmpty()) expr = expr.dropLast(1)
    }

    fun startBackspaceRepeat() {
        stopRepeat()
        repeatJob = scope.launch {
            delay(450)
            while (isActive) {
                backspace()
                delay(110)
            }
        }
    }

    fun pressDigit(d: String) {
        vibrate(longArrayOf(10))
        if (justEvaluated) {
            expr = d
            result = null
            justEvaluated = false
            return
        }
        val lastNum = Regex("(\\d+\\.?\\d*|\\.\\d*)$").find(expr)?.value ?: ""
        if (d == "." && lastNum.contains(".")) return
        // v1.6.27 — operand cap 12 → 15 digits: "the display should show very
        // long numbers" — the display autosizes to fit them.
        if (d != "." && lastNum.replace(".", "").length >= 15) return
        expr += d
    }

    fun pressOp(op: String) {
        vibrate(longArrayOf(10))
        if (justEvaluated && result != null) {
            expr = result!!
            result = null
            justEvaluated = false
        }
        if (expr.isEmpty()) {
            if (op == "−") expr = "−"
            return
        }
        expr = if (expr.last() in OP_CHARS) expr.dropLast(1) + op else expr + op
    }

    fun pressPercent() {
        vibrate(longArrayOf(10))
        if (expr.isEmpty() || expr.last() in OP_CHARS) return
        val v = evalExpression(expr) ?: return
        expr = fmtResult(v / 100)
    }

    fun pressSign() {
        vibrate(longArrayOf(10))
        val m = Regex("(\\d+\\.?\\d*|\\.\\d*)$").find(expr) ?: return
        val before = expr.substring(0, m.range.first)
        expr = when {
            before.endsWith("−") -> before.dropLast(1) + m.value
            before.endsWith("+") -> before.dropLast(1) + "−" + m.value
            else -> before + "−" + m.value
        }
    }

    fun pressEquals() {
        vibrate(longArrayOf(14))
        if (expr.isEmpty()) return
        val v = evalExpression(expr)
        if (v == null) {
            result = "Error"
            justEvaluated = true
            return
        }
        val res = fmtResult(v)
        result = res
        justEvaluated = true
        history = (listOf(HistItem(expr, res)) + history).take(30)
    }

    fun currentValue(): Double? {
        if (justEvaluated && result != null && result != "Error") return result!!.replace(",", "").toDoubleOrNull()
        return evalExpression(expr)
    }

    fun memoryAdd(sign: Int) {
        val v = currentValue() ?: return
        memory = kotlin.math.round(((memory ?: 0.0) + sign * v) * 1e10) / 1e10
        vibrate(longArrayOf(10))
    }

    fun memoryRecall() {
        val m = memory ?: return
        if (justEvaluated) {
            expr = fmtResult(m)
            result = null
            justEvaluated = false
        } else if (expr.isEmpty() || expr.last() in OP_CHARS) expr += fmtResult(m)
        else expr = fmtResult(m)
        vibrate(longArrayOf(10))
    }

    fun useValue() {
        val v = currentValue()
        if (v == null) {
            Toasts.toast(t("calc.noCalculations"), "error")
            return
        }
        UiStore.calculatorPickHandler?.invoke(v)
        UiStore.calculatorPickHandler = null
        UiStore.pop()
    }

    fun copyResult() {
        val v = currentValue() ?: return
        Platform.share.copyText(fmtResult(v))
        Toasts.toast(t("act.copied"))
    }

    Sheet(
        title = t("nav.calculator"),
        subtitle = memory?.let { "M = ${fmtResult(it)}" },
        onBack = pop,
        scrollable = false,
        headerRight = {
            Row {
                // v1.6.27 — the ADVANCED toggle (Σ) BESIDE the history icon
                Box(
                    Modifier
                        .padding(6.dp)
                        .clickable { showAdvanced = !showAdvanced },
                ) { MdiIcon("sigma", 24, if (showAdvanced) C.brand else C.sub) }
                Box(
                    Modifier
                        .padding(6.dp)
                        .clickable { showHistory = !showHistory },
                ) { MdiIcon("history", 22, if (showHistory) C.brand else C.sub) }
                Box(
                    Modifier
                        .padding(6.dp)
                        .clickable { copyResult() },
                ) { MdiIcon("content-copy", 20, C.sub) }
            }
        },
    ) {
        // v1.6.26 — THE CALCULATOR REDESIGN (user spec: "the calculator
        // should have 55% height the keypads and 45% height the display,
        // don't give the display a visible border, and make the keys and the
        // numbers colourful"). The body splits EXACTLY 45/55: the display
        // zone (display card + memory row + history) takes weight(45f),
        // the keypad zone weight(55f); the display card lost its hairline
        // border (the flat canvas tone alone separates it), and every key
        // now carries its own hue — operators keep the brand green, AC is
        // red, ⌫ amber, % blue, = solid ink, and the digits cycle a soft
        // five-colour tint so the pad reads like a modern calculator.
        // v1.6.27 display sizing — "the display should show very long
        // numbers": the font AUTOSIZES down as the expression/result grows
        // (40sp → 16sp) and wraps to 3 lines, so a 15-digit operand chain
        // stays fully readable instead of collapsing into an ellipsis.
        fun mainFontSize(s: String): Int = when {
            s.length <= 10 -> 40
            s.length <= 13 -> 34
            s.length <= 16 -> 28
            s.length <= 20 -> 23
            s.length <= 26 -> 19
            else -> 16
        }
        fun subFontSize(s: String): Int = when {
            s.length <= 14 -> 24
            s.length <= 20 -> 19
            else -> 15
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            // display zone — 45% of the body height
            Column(Modifier.weight(45f)) {
                // display — v1.6.27 — FULLY BORDERLESS: no border, no card
                // tone, no frame of any kind (user report: "the display
                // should not have visible border") — the numbers sit directly
                // on the canvas and autosize for very long values.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp)) {
                        Text(
                            displaySub ?: "",
                            fontSize = subFontSize(displaySub ?: "").sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (displaySub == "Error") C.red else C.sub,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            displayMain,
                            fontSize = mainFontSize(displayMain).sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = C.ink,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // v1.6.27 — the memory row ONLY inside the ADVANCED panel
                // (user report: "we don't need the m+ m- mr, make them in
                // advanced") — the Σ toggle beside the history icon in the
                // header shows/hides it. The Use-value button stays visible
                // whenever a value is being picked, advanced or not.
                if (showAdvanced || pickHandler != null) {
                    Row(
                        Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showAdvanced) {
                            MemBtn("M+", C) { memoryAdd(1) }
                            MemBtn("M−", C) { memoryAdd(-1) }
                            MemBtn("MR", C, memory != null) { memoryRecall() }
                            if (memory != null) MemBtn(t("calc.clearMemory"), C) { memory = null }
                        }
                        if (pickHandler != null) {
                            Box(
                                Modifier
                                    .weight(1f),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(C.brand)
                                        .clickable { useValue() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(t("calc.useValue"), color = C.onBrand, fontWeight = FontWeight.ExtraBold, fontSize = F.small.sp)
                                }
                            }
                        }
                    }
                }
                // history (inside the 45% zone; caps itself so the split holds)
                if (showHistory) {
                    Column(
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .weight(0.9f, fill = false)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, C.line, RoundedCornerShape(16.dp))
                            .background(C.card)
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (history.isEmpty()) {
                            Text(
                                t("calc.noCalculations"),
                                color = C.sub,
                                fontSize = F.small.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            )
                        }
                        history.forEachIndexed { _, h ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expr = h.expr
                                        result = null
                                        justEvaluated = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text("${h.expr} =", color = C.sub, fontSize = F.small.sp)
                                Text(h.result, color = C.ink, fontSize = F.body.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // keypad zone — 55% of the body height, 4×5 grid.
            // v1.6.27 — THE "KEYS ARE OVERLAPPING" FIX: the tiles were
            // aspectRatio(1f) — the WIDTH forced a height taller than the
            // row's weighted share, so tiles spilled over their neighbours
            // on narrow/short screens. Keys now FILL THE ROW HEIGHT
            // (weight(1f) × fillMaxHeight) with a slightly tighter 8dp gap —
            // they can never overlap again, at any size.
            Column(
                Modifier.padding(top = 12.dp).weight(55f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // v1.6.26 — the colourful key palette (soft tinted tile +
                // strong matching glyph). Hues stay inside the app's family
                // (teal/amber/blue/rose/violet) so nothing clashes with the
                // brand green reserved for the operators.
                val numTints = listOf(
                    Color(0xFF16a085) to Color(0x1f16a085), // teal
                    Color(0xFF4a7d9b) to Color(0x1f4a7d9b), // blue
                    Color(0xFF7c5cb8) to Color(0x1f7c5cb8), // violet
                    Color(0xFFb97d10) to Color(0x1fb97d10), // amber
                    Color(0xFFc65b6e) to Color(0x1fc65b6e), // rose
                )
                val rows = listOf(
                    listOf(
                        CalcKey("AC", CalcKeyKind.CLEAR) { vibrate(longArrayOf(10)); expr = ""; result = null; justEvaluated = false },
                        CalcKey("⌫", CalcKeyKind.BACK) { backspace() },
                        CalcKey("%", CalcKeyKind.PERCENT) { pressPercent() },
                        CalcKey("÷", CalcKeyKind.OP) { pressOp("÷") },
                    ),
                    listOf(
                        CalcKey("7", CalcKeyKind.NUM) { pressDigit("7") },
                        CalcKey("8", CalcKeyKind.NUM) { pressDigit("8") },
                        CalcKey("9", CalcKeyKind.NUM) { pressDigit("9") },
                        CalcKey("×", CalcKeyKind.OP) { pressOp("×") },
                    ),
                    listOf(
                        CalcKey("4", CalcKeyKind.NUM) { pressDigit("4") },
                        CalcKey("5", CalcKeyKind.NUM) { pressDigit("5") },
                        CalcKey("6", CalcKeyKind.NUM) { pressDigit("6") },
                        CalcKey("−", CalcKeyKind.OP) { pressOp("−") },
                    ),
                    listOf(
                        CalcKey("1", CalcKeyKind.NUM) { pressDigit("1") },
                        CalcKey("2", CalcKeyKind.NUM) { pressDigit("2") },
                        CalcKey("3", CalcKeyKind.NUM) { pressDigit("3") },
                        CalcKey("+", CalcKeyKind.OP) { pressOp("+") },
                    ),
                    listOf(
                        CalcKey("±", CalcKeyKind.PERCENT) { pressSign() },
                        CalcKey("0", CalcKeyKind.NUM) { pressDigit("0") },
                        CalcKey(".", CalcKeyKind.NUM) { pressDigit(".") },
                        CalcKey("=", CalcKeyKind.EQ) { pressEquals() },
                    ),
                )
                var numIdx = 0
                for (row in rows) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (ck in row) {
                            val (fg, bg) = when (ck.kind) {
                                CalcKeyKind.EQ -> C.onBrand to C.ink
                                CalcKeyKind.OP -> C.brand to C.brandSoft
                                CalcKeyKind.CLEAR -> C.red to Color(0x1fd64545)
                                CalcKeyKind.BACK -> C.amber to Color(0x1fb97d10)
                                CalcKeyKind.PERCENT -> C.blue to Color(0x1f4a7d9b)
                                CalcKeyKind.NUM -> numTints[numIdx++ % numTints.size].let { it.first to it.second }
                            }
                            val isHold = ck.kind == CalcKeyKind.BACK
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(18.dp))
                                    // v1.6.27 — pressed-state on the hold key
                                    // (pointerInput bypasses LocalIndication)
                                    .background(if (isHold && backHeld) Color(0x33b97d10) else bg)
                                    .then(
                                        if (isHold) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        backHeld = true
                                                        ck.onPress()
                                                        startBackspaceRepeat()
                                                        tryAwaitRelease()
                                                        backHeld = false
                                                        stopRepeat()
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier.clickable { ck.onPress() }
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    ck.label,
                                    fontSize = if (ck.kind == CalcKeyKind.NUM) 25.sp else 22.sp,
                                    fontWeight = if (ck.kind == CalcKeyKind.EQ || ck.kind == CalcKeyKind.OP) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = fg,
                                )
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 14.dp))
        }
    }
}

/* v1.6.26 — one calculator key: label + kind (colour) + action. */
private data class CalcKey(val label: String, val kind: CalcKeyKind, val onPress: () -> Unit)

private enum class CalcKeyKind { NUM, OP, EQ, CLEAR, BACK, PERCENT }

@Composable
private fun MemBtn(label: String, C: ThemeColors, active: Boolean = false, onPress: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) C.brandSoft else C.chip)
            .clickable { onPress() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (active) C.brand else C.sub, fontSize = F.small.sp, fontWeight = FontWeight.Bold)
    }
}

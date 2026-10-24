package com.callmony.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

private fun applyOp(op: Char, a: Double, b: Double): Double = when (op) {
    '+' -> a + b
    '−' -> a - b
    '×' -> a * b
    '÷' -> if (b == 0.0) Double.NaN else a / b
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
    val res = kotlin.math.round((stack[0] + 1e-10) * 1e10) / 1e10
    return if (res.isNaN() || res.isInfinite()) null else res
}

fun fmtResult(n: Double): String {
    if (kotlin.math.abs(n) >= 1e12) return n.toString()
    val rounded = kotlin.math.round((n + 1e-10) * 1e10) / 1e10
    val s = if (rounded == kotlin.math.floor(rounded) && !rounded.isInfinite() && kotlin.math.abs(rounded) < 1e15)
        rounded.toLong().toString() else rounded.toString()
    return if (s.length > 16) fmtSig(n, 12) else s
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
        if (d != "." && lastNum.replace(".", "").length >= 12) return
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
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            // display
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, C.line, RoundedCornerShape(20.dp))
                    .background(C.card)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    displayMain,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.sub,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    displaySub ?: "",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (displaySub == "Error") C.red else C.brand,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // memory row
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MemBtn("M+", C) { memoryAdd(1) }
                MemBtn("M−", C) { memoryAdd(-1) }
                MemBtn("MR", C, memory != null) { memoryRecall() }
                if (memory != null) MemBtn(t("calc.clearMemory"), C) { memory = null }
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
            // history
            if (showHistory) {
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, C.line, RoundedCornerShape(16.dp))
                        .background(C.card)
                        .heightIn(max = 160.dp)
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
            // keypad 4×5
            Column(
                Modifier.padding(top = 14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val rows = listOf(
                    listOf(Triple("AC", "fn", { vibrate(longArrayOf(10)); expr = ""; result = null; justEvaluated = false }),
                        Triple("⌫", "fn-hold", { backspace() }),
                        Triple("%", "fn", { pressPercent() }),
                        Triple("÷", "op", { pressOp("÷") })),
                    listOf(Triple("7", "num", { pressDigit("7") }), Triple("8", "num", { pressDigit("8") }),
                        Triple("9", "num", { pressDigit("9") }), Triple("×", "op", { pressOp("×") })),
                    listOf(Triple("4", "num", { pressDigit("4") }), Triple("5", "num", { pressDigit("5") }),
                        Triple("6", "num", { pressDigit("6") }), Triple("−", "op", { pressOp("−") })),
                    listOf(Triple("1", "num", { pressDigit("1") }), Triple("2", "num", { pressDigit("2") }),
                        Triple("3", "num", { pressDigit("3") }), Triple("+", "op", { pressOp("+") })),
                    listOf(Triple("±", "fn", { pressSign() }), Triple("0", "num", { pressDigit("0") }),
                        Triple(".", "num", { pressDigit(".") }), Triple("=", "eq", { pressEquals() })),
                )
                for (row in rows) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((label, kind, onPress) in row) {
                            val bg = when (kind) {
                                "eq" -> C.ink
                                "op" -> C.brandSoft
                                else -> if (kind == "num") C.card else C.chip
                            }
                            val fg = when (kind) {
                                "eq" -> C.onBrand
                                "op" -> C.brand
                                else -> C.ink
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(1.dp, C.line, RoundedCornerShape(22.dp))
                                    .background(bg)
                                    .then(
                                        if (kind == "fn-hold") {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        onPress()
                                                        startBackspaceRepeat()
                                                        tryAwaitRelease()
                                                        stopRepeat()
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier.clickable { onPress() }
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontSize = if (kind == "num") 26.sp else 22.sp,
                                    fontWeight = if (kind == "eq" || kind == "op") FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = fg,
                                )
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}

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

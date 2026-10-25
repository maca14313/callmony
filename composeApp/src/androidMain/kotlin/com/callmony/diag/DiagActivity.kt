package com.callmony.diag

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.callmony.MainActivity
import com.callmony.lib.AppStore
import com.callmony.i18n.t

/* v1.6.20 — the "show the problem when the app is trying to close" screen.

   Runs in its own :diag process, painted with PLAIN VIEWS (no Compose, no
   app runtime): even when the main process is frozen solid or dying, this
   activity comes up and shows exactly what was blocking or what crashed —
   boot phase, main-thread stack, app state — with a Copy button so the
   report can be pasted straight into a message back to the developer.

   Reached from:
     • AnrWatchdog    — the main app froze ≥ 5 s / saturated ≥ 4 s
     • CrashFirewall  — an uncaught exception is killing the process
   Labels reuse the commonMain i18n tables (the dictionaries are plain
   Kotlin maps compiled into the APK); the language is read from the same
   persisted settings the main app uses. */

class DiagActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val kind = intent?.getStringExtra("kind") ?: "freeze"
        val reportBody = intent?.getStringExtra("text") ?: "(no report captured)"

        // language: read the persisted settings JSON in THIS process (same
        // filesDir), then point the shared i18n at it — t() renders the
        // labels in the user's language with zero extra translation work.
        try {
            val raw = getSharedPreferences("callmony-store", MODE_PRIVATE)
                .getString("callmony.settings", null)
            val lang = Regex("\"language\"\\s*:\\s*\"(en|am|om|ar|ti)\"").find(raw ?: "")?.groupValues?.get(1)
            if (lang != null) {
                AppStore.state.settings = AppStore.state.settings.copy(language = lang)
            }
        } catch (_: Throwable) { }

        val isCrash = kind == "crash"
        val accent = 0xFF16A085.toInt()
        val cardBg = 0xFF1B1F24.toInt()

        val title = TextView(this).apply {
            text = t(if (isCrash) "diag.crashTitle" else "diag.freezeTitle")
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = t(if (isCrash) "diag.crashSub" else "diag.freezeSub")
            setTextColor(0xFF9AA4AF.toInt())
            textSize = 14f
            setLineSpacing(0f, 1.15f)
        }
        val report = TextView(this).apply {
            text = reportBody
            setTextColor(0xFFD7DDE3.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        val card = LinearLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = 18f
            }
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(report, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        fun pillButton(label: String, filled: Boolean, onClick: (View) -> Unit): Button =
            Button(this, null, 0).apply {
                text = label
                setTextColor(if (filled) Color.WHITE else accent)
                textSize = 15f
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(if (filled) accent else Color.TRANSPARENT)
                    setStroke(dp(1), accent)
                    cornerRadius = 26f
                }
                setPadding(dp(22), 0, dp(22), 0)
                minHeight = dp(46)
                setOnClickListener(onClick)
            }

        val copyBtn = pillButton(t("diag.copy"), false) {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("callmony diagnostics", reportBody))
                Toast.makeText(this, t("diag.copied"), Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) { }
        }
        val reopenBtn = pillButton(t("diag.reopen"), true) {
            try {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) { }
            finish()
        }
        val closeBtn = pillButton(t("diag.close"), false) { finish() }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(copyBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            })
            addView(reopenBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            })
            addView(closeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val badge = TextView(this).apply {
            text = if (isCrash) "!" else "·"
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(accent); shape = GradientDrawable.OVAL }
        }

        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(14) })
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
            addView(headRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
            addView(subtitle, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })
            addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = dp(16) })
            addView(buttons, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF101418.toInt())
            addView(content, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

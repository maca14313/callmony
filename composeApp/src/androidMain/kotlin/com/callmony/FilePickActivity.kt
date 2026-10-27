package com.callmony

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/* FilePickActivity — v1.6.26. ACTION_OPEN_DOCUMENT results only arrive on an
   Activity, but the app's share/pick services live on the application object.
   This tiny TRANSPARENT activity bridges the two: Platform.share.pickFile()
   parks the callback here (single pending slot — the user can only open one
   picker at a time), launches the system file picker in a NEW task, streams
   the picked document's text to the callback (on the MAIN thread — the
   callback publishes Compose state / store writes), and finishes. Nothing
   renders; the user never sees this activity, only the system picker.

   Part of the no-data-loss story ("any data should not be lost even after
   reinstall or update"): Restore-from-file reads a backup JSON the user
   saved anywhere on the phone (Downloads, Drive, Files…). */

class FilePickActivity : ComponentActivity() {

    companion object {
        @Volatile private var pending: ((String?) -> Unit)? = null
        private const val EXTRA_MIME = "mime"

        /** Park the callback and open the picker; false when launch failed. */
        fun request(ctx: Context, mime: String, onText: (String?) -> Unit): Boolean {
            return try {
                pending = onText
                ctx.startActivity(
                    Intent(ctx, FilePickActivity::class.java)
                        .putExtra(EXTRA_MIME, mime)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            } catch (_: Exception) {
                pending = null
                false
            }
        }

        private fun deliver(text: String?) {
            val cb = pending
            pending = null
            cb?.let {
                android.os.Handler(android.os.Looper.getMainLooper()).post { it(text) }
            }
        }
    }

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            deliver(null)
        } else {
            // stream the document on a background thread (a backup JSON can be
            // large); deliver() hops the result back to the MAIN thread
            Thread {
                val text = try {
                    contentResolver.openInputStream(uri)?.use { ins ->
                        ins.readBytes().toString(Charsets.UTF_8)
                    }
                } catch (_: Exception) { null }
                deliver(text)
            }.apply { isDaemon = true }.start()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mime = intent?.getStringExtra(EXTRA_MIME) ?: "*/*"
        try {
            pick.launch(arrayOf(mime))
        } catch (_: Exception) {
            deliver(null)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // activity destroyed without a result (system kill, back out of the
        // picker task directly…) — release the callback so a later pick works
        if (pending != null && isFinishing) deliver(null)
    }
}

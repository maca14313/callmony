package com.callmony

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import java.io.File

/**
 * Email with attachments — opens the user's email app with the backup
 * files already attached and the recipient pre-filled (auto email sync:
 * the user only presses Send; no account credentials ever live in the app).
 */
class EmailModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName() = "CallMonyEmail"

  /**
   * @param to        recipient address ("" = let the user pick)
   * @param subject   email subject
   * @param body      email body text
   * @param files     absolute file paths to attach (must live under the
   *                  app's files dir so the FileProvider can share them)
   */
  @ReactMethod
  fun compose(to: String, subject: String, body: String, files: ReadableArray, promise: Promise) {
    try {
      val activity = currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity")
        return
      }

      val uris = ArrayList<Uri>()
      for (i in 0 until files.size()) {
        val path = files.getString(i)
        if (path == null) continue
        val f = File(path)
        if (!f.exists()) continue
        uris.add(
          FileProvider.getUriForFile(
            reactApplicationContext,
            "${reactApplicationContext.packageName}.fileprovider",
            f
          )
        )
      }

      val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (uris.isNotEmpty()) "message/rfc822" else "text/plain"
        putExtra(Intent.EXTRA_EMAIL, if (to.isNotBlank()) arrayOf(to) else arrayOf<String>())
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (uris.isNotEmpty()) {
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      }

      // Prefer a chooser so any installed mail app can take it
      val chooser = Intent.createChooser(intent, null)
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      reactApplicationContext.startActivity(chooser)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("EMAIL_FAILED", e.message ?: "unknown", e)
    }
  }
}

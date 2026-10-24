package com.callmony

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.callmony.lib.KmpDate
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone

/** Runtime-permission bridge — one in-flight request at a time (like RN's
    PermissionsAndroid). Results are delivered from MainActivity callbacks. */
object PermissionBus {
    private var pending: MutableMap<Int, (Boolean) -> Unit> = mutableMapOf()
    private var nextCode = 5000

    fun request(perm: String, onResult: (Boolean) -> Unit) {
        val activity = ActivityRef.activity ?: run { onResult(false); return }
        if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
            return
        }
        val code = nextCode++
        pending[code] = onResult
        ActivityCompat.requestPermissions(activity, arrayOf(perm), code)
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val cb = pending.remove(requestCode) ?: return
        cb(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
    }
}

/** Holds the current activity for permission dialogs + share sheets. */
object ActivityRef {
    var activity: Activity? = null
}

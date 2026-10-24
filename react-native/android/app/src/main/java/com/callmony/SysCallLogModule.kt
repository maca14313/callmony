package com.callmony

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CallLog
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Reads the device's real system call log (the same list the built-in
 * dialer's "Recents" tab shows).
 *
 * load(limit) resolves with an array of:
 *   { id, name, phoneNumber, callType, timestamp, duration }
 * where callType follows android.provider.CallLog.Calls:
 *   1 INCOMING · 2 OUTGOING · 3 MISSED · 4 VOICEMAIL(→incoming)
 *   5 REJECTED(→missed) · 6 BLOCKED(→missed) · 7 ANSWERED_EXTERNALLY(→incoming)
 *
 * Rejects with "denied" when READ_CALL_LOG is not granted so JS can show
 * the grant-permission card.
 */
class SysCallLogModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "SysCallLog"
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun load(limit: Int, promise: Promise) {
        val capped = if (limit in 1..2000) limit else 400
        queryCalls(null, null, capped, promise)
    }

    /**
     * v1.6.11 — INCREMENTAL sync: only rows logged AFTER `since` (epoch ms).
     * The everyday open refresh moves a handful of rows (usually zero) over
     * the bridge instead of re-serializing the whole 400-row log — that dump
     * was the "takes this long to load the calls" report. Selection on the
     * indexed DATE column, newest first; JS dedupes the overlap window.
     */
    @ReactMethod
    fun loadSince(since: Double, limit: Int, promise: Promise) {
        val capped = if (limit in 1..2000) limit else 200
        val sinceMs = if (since.isFinite() && since > 0) since.toLong() else 0L
        queryCalls(
            CallLog.Calls.DATE + " > ?",
            arrayOf(sinceMs.toString()),
            capped,
            promise,
        )
    }

    /** Shared cursor walk — permission check, projection, sort, bridge array. */
    private fun queryCalls(
        selection: String?,
        selectionArgs: Array<String>?,
        capped: Int,
        promise: Promise,
    ) {
        val ctx = reactApplicationContext
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            promise.reject("denied", "READ_CALL_LOG permission not granted")
            return
        }
        try {
            val cursor = ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                ),
                selection,
                selectionArgs,
                CallLog.Calls.DATE + " DESC",
            )
            val out = Arguments.createArray()
            cursor?.use { c ->
                var count = 0
                while (c.moveToNext() && count < capped) {
                    val map = Arguments.createMap()
                    map.putString("id", "sys-" + c.getLong(0))
                    map.putString("name", c.getString(1) ?: "")
                    map.putString("phoneNumber", c.getString(2) ?: "")
                    map.putInt("callType", c.getInt(3))
                    map.putDouble("timestamp", c.getDouble(4))
                    map.putDouble("duration", c.getDouble(5))
                    out.pushMap(map)
                    count++
                }
            }
            promise.resolve(out)
        } catch (e: Exception) {
            promise.reject("error", e.message ?: "call log unavailable")
        }
    }
}

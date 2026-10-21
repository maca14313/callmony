package com.callmony

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Places REAL phone calls through the device's default dialer/phone app.
 *
 * immediateCall(number):
 *   - With the CALL_PHONE runtime permission granted it fires
 *     Intent.ACTION_CALL: the system's default phone app starts the call
 *     INSTANTLY — the number is never dropped into the keypad screen.
 *   - Without the permission it resolves "denied" so JS can request it and
 *     retry, or fall back to dial() (keypad pre-filled, one tap to call).
 *
 * dial(number):
 *   - ACTION_DIAL: opens the default dialer with the number pre-filled.
 *     Used only as a fallback when the CALL_PHONE permission is denied.
 */
class ImmediateCallModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "ImmediateCall"
    }

    override fun getName(): String = NAME

    /** Keep only characters a tel: URI can carry (digits, +, *, #, pauses). */
    private fun clean(number: String): String =
        number.trim().replace(Regex("[^0-9+*#,;]"), "")

    private fun hasCallPhone(): Boolean =
        reactApplicationContext.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    @ReactMethod
    fun immediateCall(number: String, promise: Promise) {
        try {
            val target = clean(number)
            if (target.isEmpty()) {
                promise.resolve("failed")
                return
            }
            if (!hasCallPhone()) {
                promise.resolve("denied")
                return
            }
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(target)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent)
            promise.resolve("placed")
        } catch (e: Exception) {
            // SecurityException / no default phone app — JS falls back to dial()
            promise.resolve("failed")
        }
    }

    @ReactMethod
    fun dial(number: String, promise: Promise) {
        try {
            val target = clean(number)
            if (target.isEmpty()) {
                promise.resolve("failed")
                return
            }
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(target)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent)
            promise.resolve("dialer")
        } catch (e: Exception) {
            promise.resolve("failed")
        }
    }
}

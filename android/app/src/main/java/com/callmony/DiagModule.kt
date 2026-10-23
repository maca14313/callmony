package com.callmony

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * v1.5.2 — diagnostics bridge.
 *
 *  mark(label)            — JS boot breadcrumb (fire-and-forget)
 *  readCrashReport()      — last native crash ("" when none) + boot marks
 *  clearCrashReport()     — wipe the stored crash
 *  readBootMarks()        — the breadcrumb tail alone
 *
 * v1.6.3 — reminder-reliability bridge. The #1 reason "reminders don't
 * remind" on real devices (Tecno/Infinix/Xiaomi/Samsung class) is the OEM
 * battery saver silently killing background alarms. These two methods let
 * the app CHECK the state and walk the user to the exemption dialog:
 *  isIgnoringBatteryOptimizations()      — true when the app is exempt
 *  requestIgnoreBatteryOptimizations()   — opens the OS exemption dialog
 */
class DiagModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "CallMonyDiag"
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun isIgnoringBatteryOptimizations(promise: Promise) {
        try {
            val pm = reactApplicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            promise.resolve(pm.isIgnoringBatteryOptimizations(reactApplicationContext.packageName))
        } catch (e: Exception) {
            promise.reject("diag", e.message ?: "check failed")
        }
    }

    @ReactMethod
    fun requestIgnoreBatteryOptimizations(promise: Promise) {
        try {
            val activity = currentActivity
            if (activity == null) {
                promise.resolve(false)
                return
            }
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${reactApplicationContext.packageName}")
            )
            activity.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            // some OEM ROMs don't host the dialog — fall back to the list screen
            try {
                val activity = currentActivity
                if (activity != null) {
                    activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
                promise.resolve(true)
            } catch (_: Throwable) {
                promise.resolve(false)
            }
        }
    }

    @ReactMethod
    fun mark(label: String) {
        CrashReporter.mark(reactApplicationContext, label)
    }

    @ReactMethod
    fun readCrashReport(promise: Promise) {
        try {
            promise.resolve(CrashReporter.readCrash(reactApplicationContext))
        } catch (e: Exception) {
            promise.reject("diag", e.message ?: "read failed")
        }
    }

    @ReactMethod
    fun clearCrashReport(promise: Promise) {
        try {
            CrashReporter.clearCrash(reactApplicationContext)
            CrashReporter.resetMarks(reactApplicationContext)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("diag", e.message ?: "clear failed")
        }
    }

    @ReactMethod
    fun readBootMarks(promise: Promise) {
        try {
            promise.resolve(
                try {
                    java.io.File(reactApplicationContext.filesDir, "boot-marks.txt").readText()
                } catch (_: Throwable) {
                    ""
                }
            )
        } catch (e: Exception) {
            promise.reject("diag", e.message ?: "read failed")
        }
    }

    /**
     * v1.6.1 — keep the SYSTEM navigation bar in sync with the app theme.
     * The static styles.xml color is light-only; when the user picks the dark
     * theme (or the OS switches to dark) the JS side calls this with the
     * resolved canvas color so the area under the gesture/button bar matches
     * the app instead of flashing light (user report: "black/white bar that
     * does not match in dark mode").
     *  color      — #rrggbb canvas color for the nav bar background
     *  lightIcons — true → dark icons on a light bar; false → light icons
     */
    @ReactMethod
    fun setNavBarColor(color: String, lightIcons: Boolean, promise: Promise) {
        try {
            val activity = currentActivity
            if (activity == null) {
                promise.resolve(false)
                return
            }
            activity.runOnUiThread {
                try {
                    activity.window.navigationBarColor = android.graphics.Color.parseColor(color)
                    val controller =
                        androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    controller.isAppearanceLightNavigationBars = lightIcons
                } catch (_: Throwable) {
                    /* best-effort: never crash over cosmetics */
                }
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("diag", e.message ?: "setNavBarColor failed")
        }
    }
}

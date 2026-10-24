package com.callmony

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
 */
class DiagModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "CallMonyDiag"
    }

    override fun getName(): String = NAME

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

    /* ---------------- v1.6.7 — reminder reliability (battery saver) ----------------
     * OEM battery managers (Tecno/Infinix/Xiaomi/Samsung "app sleep") are the
     * #1 reason scheduled alarms never fire while the app is closed:
     * a force-killed process loses its AlarmManager entries on many of these
     * builds. The battery-optimization exemption is the only reliable fix.
     * Ported back from v1.6.4 (the v1.6.2 revert dropped it). */

    @ReactMethod
    fun isIgnoringBatteryOptimizations(promise: Promise) {
        try {
            val pm = reactApplicationContext.getSystemService(android.content.Context.POWER_SERVICE)
                    as android.os.PowerManager
            promise.resolve(pm.isIgnoringBatteryOptimizations(reactApplicationContext.packageName))
        } catch (e: Exception) {
            promise.reject("diag", e.message ?: "battery check failed")
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
            val pm = activity.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(activity.packageName)) {
                promise.resolve(true)
                return
            }
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:" + activity.packageName)
            )
            activity.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("diag", e.message ?: "battery request failed")
        }
    }
}

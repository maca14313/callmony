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
}

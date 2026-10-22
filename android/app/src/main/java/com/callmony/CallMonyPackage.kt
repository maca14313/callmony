package com.callmony

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * callmony's own native modules:
 *  - ImmediateCallModule — real immediate calls via the default phone app
 *  - SysCallLogModule    — the device's real system call log
 *  - EmailModule         — email composer with backup attachments
 */
class CallMonyPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(ImmediateCallModule(reactContext), SysCallLogModule(reactContext), EmailModule(reactContext))

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}

package com.callmony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.callmony.lib.AppStore
import com.callmony.ui.CallmonyApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AndroidPlatform.init(applicationContext)
        setContent {
            CallmonyApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from the default dialer / messaging app: refresh the
        // system call log incrementally so calls made through the handoff
        // appear in Recents right away (cheap — usually zero rows).
        if (AppStore.state.hydrated) {
            AppStore.loadSystemCalls()
            // v1.6.14: TTL-guarded contact book refresh — contacts created
            // outside the app while it sat in the background now show up in
            // search / Recents / keypad matches without a restart.
            AppStore.maybeRefreshContacts()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionBus.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}

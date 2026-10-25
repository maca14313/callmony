package com.callmony.platform

/* NativeDateTime — the ADVANCED date & time setters (v1.6.13).
   The React Native edition used @react-native-community/datetimepicker, which
   opens the OS's own picker dialogs (Android: Material calendar + clock,
   iOS: UIDatePicker wheels) — "nothing invalid can ever be entered". These
   expect/actual functions give the KMP edition the same native pickers,
   replacing the plain text inputs the user rejected. */

expect object NativeDateTime {

    /** Open the OS date picker (Material calendar on Android). */
    fun pickDate(
        epochMillis: Long,
        onPick: (epochMillis: Long) -> Unit,
        onCancel: () -> Unit = {},
    )

    /** Open the OS time picker (Material clock on Android, 12 h + AM/PM). */
    fun pickTime(
        hourOfDay: Int,
        minute: Int,
        is24Hour: Boolean = false,
        onPick: (hourOfDay: Int, minute: Int) -> Unit,
        onCancel: () -> Unit = {},
    )
}

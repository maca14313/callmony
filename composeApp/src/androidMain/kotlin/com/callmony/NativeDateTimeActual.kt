package com.callmony.platform

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.callmony.ActivityRef
import java.util.Calendar

/* Android actual — the OS's own picker dialogs (exactly what the React
   Native edition showed via @react-native-community/datetimepicker):
   DatePickerDialog = Material calendar, TimePickerDialog = Material clock
   with AM/PM. Nothing invalid can ever be entered. */

actual object NativeDateTime {

    actual fun pickDate(
        epochMillis: Long,
        onPick: (Long) -> Unit,
        onCancel: () -> Unit,
    ) {
        val activity = ActivityRef.activity ?: run { onCancel(); return }
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        val dlg = DatePickerDialog(
            activity,
            { _, y, m, d ->
                val out = Calendar.getInstance()
                out.set(y, m, d, 12, 0, 0)
                out.set(Calendar.MILLISECOND, 0)
                onPick(out.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        )
        dlg.setOnCancelListener { onCancel() }
        dlg.show()
    }

    actual fun pickTime(
        hourOfDay: Int,
        minute: Int,
        is24Hour: Boolean,
        onPick: (Int, Int) -> Unit,
        onCancel: () -> Unit,
    ) {
        val activity = ActivityRef.activity ?: run { onCancel(); return }
        val dlg = TimePickerDialog(
            activity,
            { _, h, m -> onPick(h, m) },
            hourOfDay, minute, is24Hour,
        )
        dlg.setOnCancelListener { onCancel() }
        dlg.show()
    }
}

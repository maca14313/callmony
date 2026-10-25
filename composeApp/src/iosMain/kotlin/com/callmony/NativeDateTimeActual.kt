@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.callmony.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSTimeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyle
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyle
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIView

/* iOS actual — UIDatePicker wheels inside an alert, the same native picker
   experience @react-native-community/datetimepicker gave the RN edition. */

actual object NativeDateTime {

    private fun presentPickerAlert(
        title: String,
        configure: (UIDatePicker) -> Unit,
        onPick: (UIDatePicker) -> Unit,
        onCancel: () -> Unit,
    ) {
        val alert = UIAlertController(title, null, UIAlertControllerStyle.UIAlertControllerStyleActionSheet)
        val picker = UIDatePicker()
        picker.frame = useFrame(picker)
        configure(picker)
        alert.view.addSubview(picker)
        alert.addAction(UIAlertAction("Done", UIAlertActionStyle.UIAlertActionStyleDefault) { _ ->
            onPick(picker)
        })
        alert.addAction(UIAlertAction("Cancel", UIAlertActionStyle.UIAlertActionStyleCancel) { _ ->
            onCancel()
        })
        topViewController()?.presentViewController(alert, true, null)
    }

    private fun useFrame(picker: UIDatePicker) = picker.frame

    private fun topViewController(): platform.UIKit.UIViewController? {
        val root = platform.UIKit.UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: return null
        var top = root
        while (top.presentedViewController != null) top = top.presentedViewController!!
        return top
    }

    actual fun pickDate(epochMillis: Long, onPick: (Long) -> Unit, onCancel: () -> Unit) {
        presentPickerAlert(
            title = "",
            configure = { p ->
                p.datePickerMode = UIDatePickerMode.UIDatePickerModeDate
                if (p.respondsToSelector(platform.darwin.NSSelectorFromString("preferredDatePickerStyle"))) {
                    p.preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
                }
                p.date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
            },
            onPick = { p -> onPick((p.date.timeIntervalSince1970 * 1000).toLong()) },
            onCancel = onCancel,
        )
    }

    actual fun pickTime(hourOfDay: Int, minute: Int, is24Hour: Boolean, onPick: (Int, Int) -> Unit, onCancel: () -> Unit) {
        val cal = platform.Foundation.NSCalendar.currentCalendar
        val comps = platform.Foundation.NSDateComponents()
        comps.hour = hourOfDay.toLong()
        comps.minute = minute.toLong()
        val initial = cal.dateFromComponents(comps) ?: NSDate()
        presentPickerAlert(
            title = "",
            configure = { p ->
                p.datePickerMode = UIDatePickerMode.UIDatePickerModeTime
                if (p.respondsToSelector(platform.darwin.NSSelectorFromString("preferredDatePickerStyle"))) {
                    p.preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
                }
                p.date = initial
            },
            onPick = { p ->
                val c = cal.componentsFromDate(p.date)
                onPick(c.hour.toInt(), c.minute.toInt())
            },
            onCancel = onCancel,
        )
    }
}

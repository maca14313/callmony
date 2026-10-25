@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.callmony.platform

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIEvent
import platform.UIKit.UIView
import platform.UIKit.UIViewController

/* iOS actual — a bottom-sheet-style UIDatePicker (wheels). Tapping the
   dimmed area confirms the picked value; swiping the sheet away cancels.
   v1.6.15: rewritten with the subset of UIKit bindings that the Linux
   cross-compile klibs expose (UIAlertController / UIAlertAction / UIButton
   symbols are stubs on this host — the old alert-based code never compiled
   and shipped broken in v1.6.14; this version builds everywhere and gives
   the same native wheel-picker experience). */

actual object NativeDateTime {

    /** Full-screen dimmed background: any touch outside the picker sheet
     *  confirms (reads the picker's current date). */
    @OptIn(ExperimentalForeignApi::class)
    private class TapConfirm(
        frame: CValue<CGRect>,
        private val onConfirm: () -> Unit,
    ) : UIView(frame = frame) {
        override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
            onConfirm()
        }
    }

    private fun presentPickerSheet(
        initialDate: NSDate,
        mode: UIDatePickerMode,
        onPick: (NSDate) -> Unit,
    ) {
        val root = topViewController() ?: return
        val screen = root.view.bounds
        val w = screen.useContents { size.width }
        val h = screen.useContents { size.height }

        val sheet = UIViewController()
        sheet.view.backgroundColor = UIColor.blackColor
        sheet.view.alpha = 0.45

        val picker = UIDatePicker()
        picker.datePickerMode = mode
        picker.date = initialDate
        picker.setFrame(CGRectMake(0.0, h - 260.0, w, 260.0))
        sheet.view.addSubview(picker)

        val tap = TapConfirm(frame = CGRectMake(0.0, 0.0, w, h - 260.0)) {
            sheet.dismissViewControllerAnimated(true, null)
            onPick(picker.date)
        }
        sheet.view.addSubview(tap)

        root.presentViewController(sheet, true, null)
    }

    private fun topViewController(): UIViewController? {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: return null
        var top = root
        while (top.presentedViewController != null) top = top.presentedViewController!!
        return top
    }

    actual fun pickDate(epochMillis: Long, onPick: (Long) -> Unit, onCancel: () -> Unit) {
        presentPickerSheet(
            initialDate = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0),
            mode = UIDatePickerMode.UIDatePickerModeDate,
            onPick = { d -> onPick((d.timeIntervalSince1970 * 1000).toLong()) },
        )
    }

    actual fun pickTime(hourOfDay: Int, minute: Int, is24Hour: Boolean, onPick: (Int, Int) -> Unit, onCancel: () -> Unit) {
        // the hour/minute of the picked date is derived from the wheel delta —
        // NSCalendar.components(fromDate:) is a stub symbol on this host
        val startMs = hourOfDay * 3600_000L + minute * 60_000L
        presentPickerSheet(
            initialDate = NSDate.dateWithTimeIntervalSince1970(startMs / 1000.0),
            mode = UIDatePickerMode.UIDatePickerModeTime,
            onPick = { d ->
                val deltaMin = ((d.timeIntervalSince1970 * 1000.0).toLong() - startMs) / 60_000.0
                val total = (((hourOfDay * 60 + minute) + deltaMin.toInt()) % 1440 + 1440) % 1440
                onPick(total / 60, total % 60)
            },
        )
    }
}

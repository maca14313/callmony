package com.callmony

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callmony.lib.AppStore
import com.callmony.lib.Db
import com.callmony.platform.NotifyService
import com.callmony.platform.Platform
import com.callmony.platform.ReminderForAlarm

/* v1.6.16 — the RICH reminder notification.
   The old one was a bare title+text line. The new one is a fully styled
   Material notification: the round app logo as the large icon, brand-teal
   accent, the reminder KIND as subtext, the full notes in an expanded
   BigText body, tap-to-open, and — matching the React Native edition —
   "Done" + "Snooze 10 minutes" quick actions that resolve the reminder
   straight from the notification shade even while the app is closed. */

object NotifArt {
    const val BRAND_ARGB = 0xFF16a085.toInt()

    @Volatile private var roundLogo: Bitmap? = null

    /** The app logo circle-clipped — the notification's large icon
     *  (decoded once per process; receivers reuse it). */
    fun roundLogo(ctx: Context): Bitmap? {
        roundLogo?.let { return it }
        return try {
            val src = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_asset)
                ?: return null
            val size = minOf(src.width, src.height)
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val clip = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
            c.clipPath(clip)
            c.drawBitmap(src, (size - src.width) / 2f, (size - src.height) / 2f, null)
            // a subtle brand ring frames the circle
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BRAND_ARGB
                style = Paint.Style.STROKE
                strokeWidth = size * 0.035f
            }
            c.drawCircle(size / 2f, size / 2f, size / 2f - ring.strokeWidth / 2f, ring)
            roundLogo = out
            out
        } catch (_: Exception) { null }
    }
}

class AndroidNotify : NotifyService {
    override fun ensureChannel() {
        val ctx = AndroidPlatform.appContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Reminder alerts — due dates, follow-ups, birthdays and payments"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 220, 110, 220)
                    enableLights(true)
                    lightColor = NotifArt.BRAND_ARGB
                    setShowBadge(true)
                }
                nm.createNotificationChannel(ch)
            }
            ensureAlarmChannel(nm)
        }
    }

    /* v1.6.18 — ALARM reminders: a dedicated channel on the ALARM audio
       stream. Alarms ring like a clock alarm — audible over silent-mode
       media and DND (when the user lets alarms through), instead of the
       regular notification blip. Channels are immutable after creation,
       so this must exist BEFORE the first alarm notification is posted. */
    fun ensureAlarmChannel(nm: NotificationManager? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = AndroidPlatform.appContext
        val mgr = nm ?: ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                ALARM_CHANNEL_ID, "Reminder alarms", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders set as alarms — they ring on the alarm stream, audible over silent mode"
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
                enableLights(true)
                lightColor = NotifArt.BRAND_ARGB
                setShowBadge(true)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    override fun hasNotifPermission(): Boolean {
        val ctx = AndroidPlatform.appContext
        return if (Build.VERSION.SDK_INT >= 33) {
            AndroidPlatform.hasPerm(android.Manifest.permission.POST_NOTIFICATIONS)
        } else NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    override fun requestNotifPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionBus.request(android.Manifest.permission.POST_NOTIFICATIONS, onResult)
        } else onResult(hasNotifPermission())
    }

    override fun schedule(
        id: String,
        title: String,
        body: String,
        at: Long,
        kind: String,
        kindLabel: String,
        doneLabel: String,
        snoozeLabel: String,
        alarm: Boolean,
    ) {
        val ctx = AndroidPlatform.appContext
        ensureChannel()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = notifPendingIntent(id, title, body, kind, kindLabel, doneLabel, snoozeLabel, alarm)
        val atSafe = maxOf(at, System.currentTimeMillis() + 1000)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atSafe, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atSafe, pi)
            }
        } catch (_: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, atSafe, pi)
        }
    }

    override fun cancel(id: String) {
        val ctx = AndroidPlatform.appContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // cancel BOTH PendingIntent variants (regular + alarm-channel);
        // requestCode and extras match, so both cancel cleanly.
        am.cancel(notifPendingIntent(id, "", "", "", "", "", "", false))
        am.cancel(notifPendingIntent(id, "", "", "", "", "", "", true))
        NotificationManagerCompat.from(ctx).cancel(id.hashCode())
    }

    override fun rearmAll(reminders: List<ReminderForAlarm>) {
        for (r in reminders) schedule(r.id, r.title, r.body, r.dueAt, r.kind, r.kindLabel, r.doneLabel, r.snoozeLabel, r.alarm)
    }

    /* v1.6.16 KEYSTROKE LAG FIX: every keypad keypress vibrated, and each
       call did a fresh getSystemService(VIBRATOR_SERVICE) — a binder lookup
       per digit typed. The Vibrator is now cached once per process. */
    override fun vibrate(pattern: LongArray) {
        try {
            val v = defaultVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    override fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = AndroidPlatform.appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    override fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT in 31..32) {
            val ctx = AndroidPlatform.appContext
            try {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {}
        }
    }

    override fun isBatteryExempt(): Boolean {
        val ctx = AndroidPlatform.appContext
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    override fun requestBatteryExemption() {
        val ctx = AndroidPlatform.appContext
        try {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {}
    }

    private fun notifPendingIntent(
        id: String, title: String, body: String, kind: String,
        kindLabel: String, doneLabel: String, snoozeLabel: String, alarm: Boolean,
    ): PendingIntent {
        val ctx = AndroidPlatform.appContext
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("kind", kind)
            putExtra("kindLabel", kindLabel)
            putExtra("doneLabel", doneLabel)
            putExtra("snoozeLabel", snoozeLabel)
            putExtra("alarm", alarm)
        }
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "callmony-reminders"
        const val ALARM_CHANNEL_ID = "callmony-reminder-alarms"

        @Volatile private var cachedVibrator: Vibrator? = null

        @SuppressLint("ServiceCast")
        private fun defaultVibrator(): Vibrator? {
            cachedVibrator?.let { return it }
            val v = try {
                val ctx = AndroidPlatform.appContext
                if (Build.VERSION.SDK_INT >= 31) {
                    (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } catch (_: Exception) { null }
            cachedVibrator = v
            return v
        }
    }
}

class AlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: "Reminder"
        val body = intent.getStringExtra("body") ?: ""
        val kindLabel = intent.getStringExtra("kindLabel") ?: ""
        val doneLabel = intent.getStringExtra("doneLabel")?.ifBlank { "Done" } ?: "Done"
        val snoozeLabel = intent.getStringExtra("snoozeLabel")?.ifBlank { "Snooze 10 min" } ?: "Snooze 10 min"
        val isAlarm = intent.getBooleanExtra("alarm", false)
        // CallmonyApplication.onCreate initializes the platform before any
        // component runs, but be defensive anyway (fresh OEM process paths).
        if (!AndroidPlatform.isReady()) AndroidPlatform.init(context.applicationContext)
        val ctx = AndroidPlatform.appContext
        val nm = NotificationManagerCompat.from(ctx)
        if (nm.areNotificationsEnabled()) {
            val notify = AndroidNotify()
            notify.ensureChannel()
            if (isAlarm) notify.ensureAlarmChannel()

            val bodyText = body.ifBlank { kindLabel.ifBlank { "Reminder" } }

            val contentPi = PendingIntent.getActivity(
                ctx, id.hashCode(),
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val donePi = NotificationActionReceiver.pending(ctx, id, NotificationActionReceiver.ACTION_DONE)
            val snoozePi = NotificationActionReceiver.pending(ctx, id, NotificationActionReceiver.ACTION_SNOOZE)

            val channel = if (isAlarm) AndroidNotify.ALARM_CHANNEL_ID else AndroidNotify.CHANNEL_ID
            val builder = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat_callmony)
                .setLargeIcon(NotifArt.roundLogo(ctx))
                .setContentTitle(title)
                .setContentText(bodyText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                .setColor(NotifArt.BRAND_ARGB)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .addAction(R.drawable.ic_stat_done, doneLabel, donePi)
                .addAction(R.drawable.ic_stat_snooze, snoozeLabel, snoozePi)
            if (kindLabel.isNotBlank()) builder.setSubText(kindLabel)
            try {
                nm.notify(id.hashCode(), builder.build())
            } catch (_: SecurityException) {}
        }
        /* v1.6.17 — "the reminder is not working" for REPEATS while the app
         * is closed: the RN edition relies on notifee's repeatFrequency, which
         * re-arms the OS trigger automatically every day/week. AlarmManager
         * alarms are one-shot — without this the second occurrence of a
         * daily reminder never fired until the app was opened again. Advance
         * the persisted due moment to the next occurrence and arm the next
         * alarm right here, in the background thread (goAsync keeps the
         * process alive while the JSON surgery + reschedule run).
         * v1.6.18: the load-modify-save runs as ONE atomic block on the
         * Persist queue — ordered with every UI write, never racing them. */
        val pending = goAsync()
        // v1.6.21 — single-writer snapshot rule: read the live flag on the MAIN
        // thread (onReceive) instead of from the worker, and post the live
        // store update BACK to main — the worker thread never touches
        // Compose state (cross-thread snapshot writes were the contention
        // behind the v1.6.20 freeze report).
        val live = AppStore.state.hydrated
        Thread {
            try {
                if (live) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try { AppStore.fireReminder(id) } catch (_: Exception) {}
                    }
                } else {
                    com.callmony.lib.Persist.postAndWait {
                        val rec = Db.loadAll().reminders.find { it.id == id } ?: return@postAndWait
                        if (rec.repeat != "none" && !rec.done && rec.deletedAt == null) {
                            val nextDue = com.callmony.lib.nextOccurrence(rec.dueAt, rec.repeat)
                            val nowMs = com.callmony.lib.now()
                            val next = Db.loadAll().reminders.map {
                                if (it.id == id) it.copy(dueAt = nextDue, lastFiredAt = nowMs, firedLeads = emptyList(), snoozedUntil = null) else it
                            }
                            Db.saveReminders(next)
                            (Platform.notify as? AndroidNotify)?.schedule(
                                rec.id, rec.title, rec.notes ?: "", nextDue, rec.kind,
                                alarm = rec.isAlarm,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** v1.6.16 — resolves a reminder from the notification SHADE (RN parity):
 *  "Done" marks it complete, "Snooze 10 minutes" pushes it +10 min. Works
 *  with the app closed (JSON surgery on the persisted store) AND open
 *  (live AppStore update so the UI reflects it instantly). */
class NotificationActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val action = intent.action ?: return
        if (!AndroidPlatform.isReady()) AndroidPlatform.init(context.applicationContext)
        val ctx = AndroidPlatform.appContext
        // v1.6.21 — single-writer snapshot rule: read the live flag on the
        // MAIN thread (onReceive) and post the live store update BACK to
        // main; the worker thread only does JSON surgery / notifications.
        val live = AppStore.state.hydrated
        val pending = goAsync()
        Thread {
            try {
                val nm = NotificationManagerCompat.from(ctx)
                nm.cancel(id.hashCode())
                val now = System.currentTimeMillis()
                if (live) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            when (action) {
                                ACTION_DONE -> AppStore.completeReminder(id)
                                ACTION_SNOOZE -> AppStore.snoozeReminder(id, now + 10 * 60000L)
                            }
                        } catch (_: Exception) {}
                    }
                } else {
                    com.callmony.lib.Persist.postAndWait {
                        when (action) {
                            ACTION_DONE -> {
                                val next = Db.loadAll().reminders.map {
                                    if (it.id == id) it.copy(done = true, doneAt = now, updatedAt = now) else it
                                }
                                Db.saveReminders(next)
                            }
                            ACTION_SNOOZE -> {
                                val until = now + 10 * 60000L
                                val next = Db.loadAll().reminders.map {
                                    if (it.id == id) it.copy(snoozedUntil = until, updatedAt = now) else it
                                }
                                Db.saveReminders(next)
                                next.find { it.id == id }?.let { rec ->
                                    (Platform.notify as? AndroidNotify)?.schedule(
                                        rec.id, rec.title, rec.notes ?: "", until, rec.kind,
                                        alarm = rec.isAlarm,
                                    )
                                }
                            }
                        }
                    }
                }
                confirm(ctx, nm, id, action)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** Small self-dismissing confirmation ("Done ✓" / "Snoozed · 10 min"). */
    private fun confirm(ctx: Context, nm: NotificationManagerCompat, id: String, action: String) {
        try {
            if (!nm.areNotificationsEnabled()) return
            AndroidNotify().ensureChannel()
            val notif = NotificationCompat.Builder(ctx, AndroidNotify.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_callmony)
                .setLargeIcon(NotifArt.roundLogo(ctx))
                .setContentTitle(if (action == ACTION_DONE) "✓" else "⏱")
                .setContentText(if (action == ACTION_DONE) "callmony" else "callmony")
                .setColor(NotifArt.BRAND_ARGB)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setTimeoutAfter(6000)
                .setShowWhen(false)
                .build()
            nm.notify((id + action).hashCode(), notif)
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_DONE = "com.callmony.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.callmony.action.REMINDER_SNOOZE"

        fun pending(ctx: Context, id: String, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                ctx,
                (id + action).hashCode(),
                Intent(ctx, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra("id", id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

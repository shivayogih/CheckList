package com.dataloom.checklist.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Wakes the app at a reminder's time. Scheduling the same reminder again replaces its alarm. */
interface ReminderScheduler {
    fun schedule(reminder: Reminder)

    fun cancel(reminderId: String)
}

/**
 * [ReminderScheduler] on AlarmManager. The alarm is exact when the phone allows exact alarms
 * (SCHEDULE_EXACT_ALARM; the user can turn it off from Android 14), otherwise Android may deliver it
 * a few minutes late to save battery. Both kinds also fire in Doze. A time already passed fires at once,
 * so a reminder missed while the phone was off shows after it starts again.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val alarms: AlarmManager? get() = context.getSystemService()

    @SuppressLint("ScheduleExactAlarm") // Exact only when canScheduleExactAlarms() says so.
    override fun schedule(reminder: Reminder) {
        val manager = alarms ?: return
        val operation = pendingIntent(reminder.id)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, operation)
        }
    }

    override fun cancel(reminderId: String) {
        alarms?.cancel(pendingIntent(reminderId))
    }

    /** Matches on the reminder ID (the intent data), so [cancel] finds what [schedule] set. */
    private fun pendingIntent(reminderId: String): PendingIntent {
        val intent = ReminderReceiver.intent(context, reminderId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal companion object {
        fun Intent.reminderId(): String? = data?.schemeSpecificPart
    }
}

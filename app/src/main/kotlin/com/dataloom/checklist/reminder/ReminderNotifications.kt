package com.dataloom.checklist.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dataloom.checklist.MainActivity
import com.dataloom.checklist.R

/** The reminder notification channel and the notification itself (CL-350). */
object ReminderNotifications {

    const val CHANNEL_ID = "list_reminders"

    /** Extra on the MainActivity intent: the list to open when the notification is tapped. */
    const val EXTRA_CHECKLIST_ID = "com.dataloom.checklist.extra.CHECKLIST_ID"

    /** Creates (or renames, after a language change) the channel. Safe to call often. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Whether a notification can show now (the user may have turned them off or not allowed them). */
    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // canNotify() checks POST_NOTIFICATIONS first.
    fun show(context: Context, reminder: Reminder, listTitle: String) {
        if (!canNotify(context)) return
        createChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_CHECKLIST_ID, reminder.checklistId)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.reminder_notification_title, listTitle))
            .setContentText(context.getString(R.string.reminder_notification_body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.id, 0, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the call; nothing to show.
        }
    }
}

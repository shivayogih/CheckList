package com.dataloom.checklist.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.reminder.AlarmReminderScheduler.Companion.reminderId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Hilt access for the receivers, which Android creates itself. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun reminderSync(): ReminderSync

    @ApplicationScope
    fun scope(): CoroutineScope
}

private fun Context.reminderEntryPoint(): ReminderEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (ReminderSync) -> Unit) {
    val pending = goAsync()
    val entryPoint = context.reminderEntryPoint()
    entryPoint.scope().launch {
        try {
            block(entryPoint.reminderSync())
        } finally {
            pending.finish()
        }
    }
}

/** Receives a reminder's alarm (not exported: only this app's own PendingIntent reaches it). */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.reminderId() ?: return
        runAsync(context) { sync -> sync.fire(reminderId) }
    }

    companion object {
        private const val SCHEME = "reminder"

        /** The reminder ID travels as the intent data, so each reminder gets its own alarm. */
        fun intent(context: Context, reminderId: String): Intent =
            Intent(context, ReminderReceiver::class.java).setData(Uri.fromParts(SCHEME, reminderId, null))
    }
}

/** Alarms do not survive a restart or an app update; this sets them again. */
class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORE_ACTIONS) return
        runAsync(context) { sync -> sync.rescheduleAll() }
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}

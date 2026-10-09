package com.dataloom.checklist.reminder

import android.content.Context
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.domain.common.AppLog
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.repository.ChecklistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps reminders, their alarms and the lists in step (CL-350):
 * - saving or deleting a reminder sets or cancels its alarm;
 * - a reminder whose list is deleted or archived is removed with its alarm (also when that happens
 *   through an import, or while the app was closed);
 * - when an alarm fires, the notification shows only if the list still exists and is active, and the
 *   reminder is then removed (reminders are one-time).
 */
@Singleton
class ReminderSync @Inject constructor(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
    private val checklists: ChecklistRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    /** Active lists only: a reminder can be set on these, and only these keep their reminders. */
    val activeLists: Flow<List<ChecklistSummary>> =
        checklists.observeChecklists(ChecklistQuery(filter = ChecklistFilter.ACTIVE))

    /** At app start: sets every alarm again and starts removing reminders of deleted or archived lists. */
    fun start() {
        scope.launch {
            ReminderNotifications.createChannel(context)
            rescheduleAll()
            combine(store.reminders, activeLists) { reminders, lists ->
                val active = lists.mapTo(HashSet()) { it.checklist.id.value }
                reminders.filter { it.checklistId !in active }
            }
                .catch { failure -> AppLog.w(TAG, failure) { "Reminder clean-up stopped" } }
                .collect { orphans -> remove(orphans.mapTo(HashSet()) { it.id }) }
        }
    }

    suspend fun rescheduleAll() {
        store.reminders.first().forEach(scheduler::schedule)
    }

    /**
     * Saves a reminder for [checklistId] at [triggerAt], replacing [existingId] when given.
     * Returns false (and saves nothing) when the time is not in the future.
     */
    suspend fun save(checklistId: String, triggerAt: Long, existingId: String? = null): Boolean {
        if (triggerAt <= clock.nowMillis()) return false
        val reminder = Reminder(id = existingId ?: ids.newId(), checklistId = checklistId, triggerAt = triggerAt)
        store.upsert(reminder)
        scheduler.schedule(reminder)
        return true
    }

    suspend fun remove(reminderIds: Set<String>) {
        if (reminderIds.isEmpty()) return
        reminderIds.forEach(scheduler::cancel)
        store.delete(reminderIds)
    }

    /** The alarm of [reminderId] went off. */
    suspend fun fire(reminderId: String) {
        val reminder = store.get(reminderId) ?: return // deleted after the alarm was set
        store.delete(setOf(reminder.id))
        val list = activeLists.first().firstOrNull { it.checklist.id.value == reminder.checklistId } ?: return
        ReminderNotifications.show(context, reminder, list.checklist.title)
    }

    private companion object {
        const val TAG = "ReminderSync"
    }
}

package com.dataloom.checklist.presentation.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.reminder.ReminderStore
import com.dataloom.checklist.reminder.ReminderSync
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderRowUi(val id: String, val checklistId: String, val listTitle: String, val triggerAt: Long)

data class ListChoiceUi(val id: String, val title: String)

data class RemindersUiState(
    val isLoading: Boolean = true,
    val reminders: List<ReminderRowUi> = emptyList(),
    /** Active checklists, for "Add reminder". */
    val lists: List<ListChoiceUi> = emptyList(),
)

sealed interface ReminderEffect {
    data class Saved(val triggerAt: Long) : ReminderEffect

    data object InPast : ReminderEffect

    data object Deleted : ReminderEffect
}

/** The Reminders screen and the "Set reminder" action of a checklist (CL-350). */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val sync: ReminderSync,
    private val clock: Clock,
    store: ReminderStore,
) : ViewModel() {

    val state: StateFlow<RemindersUiState> = combine(store.reminders, sync.activeLists) { reminders, lists ->
        val titles = lists.associate { it.checklist.id.value to it.checklist.title }
        RemindersUiState(
            isLoading = false,
            // A reminder of a list that was just deleted or archived disappears before ReminderSync removes it.
            reminders = reminders.mapNotNull { reminder ->
                titles[reminder.checklistId]?.let { title ->
                    ReminderRowUi(reminder.id, reminder.checklistId, title, reminder.triggerAt)
                }
            },
            lists = lists.map { ListChoiceUi(it.checklist.id.value, it.checklist.title) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemindersUiState())

    private val effectChannel = Channel<ReminderEffect>(Channel.BUFFERED)
    val effects: Flow<ReminderEffect> = effectChannel.receiveAsFlow()

    /** The suggested time for a new reminder: a whole hour, local time, at least an hour from now. */
    fun suggestedTime(): Long =
        Instant.ofEpochMilli(clock.nowMillis()).atZone(ZoneId.systemDefault())
            .plusHours(2).truncatedTo(ChronoUnit.HOURS)
            .toInstant().toEpochMilli()

    fun isInFuture(triggerAt: Long): Boolean = triggerAt > clock.nowMillis()

    fun save(checklistId: String, triggerAt: Long, existingId: String? = null) {
        viewModelScope.launch {
            val saved = sync.save(checklistId, triggerAt, existingId)
            effectChannel.send(if (saved) ReminderEffect.Saved(triggerAt) else ReminderEffect.InPast)
        }
    }

    fun delete(reminderId: String) {
        viewModelScope.launch {
            sync.remove(setOf(reminderId))
            effectChannel.send(ReminderEffect.Deleted)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

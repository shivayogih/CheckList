package com.dataloom.checklist.presentation.checklist.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.currentAppLocale
import com.dataloom.checklist.presentation.components.AppIconButton
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.presentation.reminder.ReminderEffects
import com.dataloom.checklist.presentation.reminder.ReminderRowUi
import com.dataloom.checklist.presentation.reminder.ReminderTimeDialog
import com.dataloom.checklist.presentation.reminder.RemindersViewModel
import com.dataloom.checklist.presentation.reminder.formatReminderTime
import com.dataloom.checklist.presentation.reminder.reminderWhenText
import com.dataloom.checklist.presentation.theme.Dimens

/** A checklist's reminders as shown on its screen, with what the user can do with them (CL-350). */
internal class DetailReminders(
    val reminders: List<ReminderRowUi>,
    /** False for an archived checklist: archiving removes a list's reminders. */
    val canSet: Boolean,
    val onSet: () -> Unit,
    val onEdit: (ReminderRowUi) -> Unit,
    val onDelete: (ReminderRowUi) -> Unit,
    /** The next reminder worded for the PDF header ("Reminder: 10 Oct 2026, 6:00 PM"), or null. */
    val pdfText: String?,
)

/**
 * The reminders of [checklistId] with their actions, plus the new/change reminder dialog and the
 * reminder snackbars. "Set reminder" is offered on active checklists only.
 */
@Composable
internal fun reminderControls(
    checklistId: String,
    listTitle: String,
    viewModel: RemindersViewModel,
    snackbarHostState: SnackbarHostState,
): DetailReminders {
    ReminderEffects(viewModel.effects, snackbarHostState)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listReminders = remember(state.reminders, checklistId) {
        state.reminders.filter { it.checklistId == checklistId }
    }
    var reminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    // Null for a new reminder; the ID of the one being changed otherwise.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    reminderTime?.let { initial ->
        val close = {
            reminderTime = null
            editingId = null
        }
        ReminderTimeDialog(
            title = stringResource(
                if (editingId == null) R.string.reminder_new_title else R.string.reminder_edit_title,
            ),
            listTitle = listTitle,
            initialTime = initial,
            isInFuture = viewModel::isInFuture,
            onSave = { time ->
                viewModel.save(checklistId, time, editingId)
                close()
            },
            onDismiss = close,
        )
    }
    val locale = currentAppLocale()
    return DetailReminders(
        reminders = listReminders,
        canSet = state.lists.any { it.id == checklistId },
        onSet = {
            editingId = null
            reminderTime = viewModel.suggestedTime()
        },
        onEdit = { row ->
            editingId = row.id
            reminderTime = row.triggerAt
        },
        onDelete = { row -> viewModel.delete(row.id) },
        pdfText = listReminders.firstOrNull()?.let {
            stringResource(R.string.pdf_reminder, reminderWhenText(it.triggerAt, locale))
        },
    )
}

/**
 * Each reminder of the list ("⏰ Reminder: 10 Oct 2026, 6:00 PM" with change and delete buttons), or
 * a "Set reminder" button when it has none, so reminders are set and seen on the checklist itself.
 */
@Composable
internal fun ReminderSection(reminders: DetailReminders, listTitle: String) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        reminders.reminders.forEach { row ->
            ReminderCard(
                row = row,
                listTitle = listTitle,
                onEdit = { reminders.onEdit(row) },
                onDelete = { reminders.onDelete(row) },
            )
        }
        if (reminders.reminders.isEmpty() && reminders.canSet) {
            OutlinedActionButton(
                text = stringResource(R.string.reminder_set),
                leadingIcon = painterResource(R.drawable.ic_alarm),
                onClick = reminders.onSet,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReminderCard(row: ReminderRowUi, listTitle: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(Dimens.Corner12),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                stringResource(R.string.pdf_reminder, formatReminderTime(row.triggerAt)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            AppIconButton(
                icon = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(R.string.reminder_edit_named, listTitle),
                onClick = onEdit,
                tint = colors.onPrimaryContainer,
            )
            AppIconButton(
                icon = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.reminder_delete_named, listTitle),
                onClick = onDelete,
                tint = colors.onPrimaryContainer,
            )
        }
    }
}

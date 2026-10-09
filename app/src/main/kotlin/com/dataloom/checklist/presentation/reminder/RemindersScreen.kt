package com.dataloom.checklist.presentation.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.components.AppIconButton
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.EmptyState
import com.dataloom.checklist.presentation.components.PrimaryButton
import kotlinx.coroutines.launch

/** What the reminder dialog is open for: a new reminder on [listId], or a change to [reminderId]. */
private data class ReminderEdit(val listId: String, val listTitle: String, val reminderId: String?, val time: Long)

/** Keeps an open reminder dialog across rotation and process death. */
private val ReminderEditSaver = listSaver<ReminderEdit?, Any?>(
    save = { edit -> edit?.run { listOf(listId, listTitle, reminderId, time) }.orEmpty() },
    restore = { saved ->
        saved.takeIf { it.isNotEmpty() }?.let {
            ReminderEdit(it[0] as String, it[1] as String, it[2] as String?, it[3] as Long)
        }
    },
)

/** Every reminder, soonest first, with change and delete; "Add reminder" picks a checklist first (CL-350). */
@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    ReminderEffects(viewModel.effects, snackbarHostState)
    var choosingList by rememberSaveable { mutableStateOf(false) }
    var edit by rememberSaveable(stateSaver = ReminderEditSaver) { mutableStateOf<ReminderEdit?>(null) }
    val noLists = stringResource(R.string.reminders_no_lists)

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.reminders_title),
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            AddReminderBar {
                if (state.lists.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(noLists) }
                } else {
                    choosingList = true
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        RemindersList(
            state = state,
            onEdit = { row -> edit = ReminderEdit(row.checklistId, row.listTitle, row.id, row.triggerAt) },
            onDelete = { row -> viewModel.delete(row.id) },
            modifier = Modifier.padding(padding),
        )
    }

    if (choosingList) {
        ChooseListDialog(
            lists = state.lists,
            onChosen = { list ->
                choosingList = false
                edit = ReminderEdit(list.id, list.title, reminderId = null, time = viewModel.suggestedTime())
            },
            onDismiss = { choosingList = false },
        )
    }
    edit?.let { current ->
        ReminderTimeDialog(
            title = stringResource(
                if (current.reminderId == null) R.string.reminder_new_title else R.string.reminder_edit_title,
            ),
            listTitle = current.listTitle,
            initialTime = current.time,
            isInFuture = viewModel::isInFuture,
            onSave = { time ->
                viewModel.save(current.listId, time, current.reminderId)
                edit = null
            },
            onDismiss = { edit = null },
        )
    }
}

@Composable
private fun AddReminderBar(onClick: () -> Unit) {
    PrimaryButton(
        text = stringResource(R.string.reminder_add),
        leadingIcon = painterResource(R.drawable.ic_add),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
    )
}

@Composable
private fun RemindersList(
    state: RemindersUiState,
    onEdit: (ReminderRowUi) -> Unit,
    onDelete: (ReminderRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isLoading && state.reminders.isEmpty()) {
        EmptyState(
            emoji = "⏰",
            title = stringResource(R.string.reminders_empty_title),
            body = stringResource(R.string.reminders_empty_body),
            modifier = modifier,
        )
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(state.reminders, key = { it.id }) { row ->
                ReminderRow(row = row, onEdit = { onEdit(row) }, onDelete = { onDelete(row) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ReminderRow(row: ReminderRowUi, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.listTitle, style = MaterialTheme.typography.bodyLarge)
            Text(
                formatReminderTime(row.triggerAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AppIconButton(
            icon = painterResource(R.drawable.ic_edit),
            contentDescription = stringResource(R.string.reminder_edit_named, row.listTitle),
            onClick = onEdit,
        )
        AppIconButton(
            icon = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.reminder_delete_named, row.listTitle),
            onClick = onDelete,
        )
    }
}

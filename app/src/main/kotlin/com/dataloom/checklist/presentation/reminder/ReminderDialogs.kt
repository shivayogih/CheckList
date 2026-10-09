package com.dataloom.checklist.presentation.reminder

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.reminder.ReminderNotifications
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The app language's locale, for dates and times. */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
fun formatReminderTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, currentLocale()).format(Date(millis))

private fun Long.local(): ZonedDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

/**
 * Picks the date and time of a reminder for [listTitle]. Save stays off, with a hint, while the chosen
 * time is not in the future.
 */
@Composable
fun ReminderTimeDialog(
    title: String,
    listTitle: String,
    initialTime: Long,
    isInFuture: (Long) -> Boolean,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var time by rememberSaveable { mutableLongStateOf(initialTime) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val valid = isInFuture(time)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ReminderTimeFields(
                listTitle = listTitle,
                time = time,
                valid = valid,
                onPickDate = { pickingDate = true },
                onPickTime = { pickingTime = true },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(time) }, enabled = valid) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
    if (pickingDate) {
        ReminderDatePicker(
            initial = time.local().toLocalDate(),
            onPicked = { date ->
                time = time.local().with(date).toInstant().toEpochMilli()
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        ReminderTimePicker(
            initial = time.local().toLocalTime(),
            onPicked = { picked ->
                time = time.local().with(picked).toInstant().toEpochMilli()
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun ReminderTimeFields(
    listTitle: String,
    time: Long,
    valid: Boolean,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val locale = currentLocale()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.reminder_for_list, listTitle), style = MaterialTheme.typography.bodyMedium)
        OutlinedActionButton(
            text = stringResource(
                R.string.reminder_date_button,
                DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(time)),
            ),
            onClick = onPickDate,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedActionButton(
            text = stringResource(
                R.string.reminder_time_button,
                DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(time)),
            ),
            onClick = onPickTime,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!valid) {
            Text(
                stringResource(R.string.reminder_in_past),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works in UTC midnights; today and later are selectable.
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton onDismiss()
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(
            state = state,
            title = { Text(stringResource(R.string.reminder_pick_date), Modifier.padding(16.dp)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePicker(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Chooses the checklist a new reminder is for. */
@Composable
fun ChooseListDialog(lists: List<ListChoiceUi>, onChosen: (ListChoiceUi) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_choose_list)) },
        text = {
            LazyColumn {
                items(lists, key = { it.id }) { list ->
                    Text(
                        text = list.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onChosen(list) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Shows what happened to a reminder in [snackbarHostState]. After a save it asks for the notification
 * permission (Android 13+) when needed, and says so when notifications are off for the app.
 */
@Composable
fun ReminderEffects(effects: Flow<ReminderEffect>, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offMessage = stringResource(R.string.reminder_notifications_off)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted || !ReminderNotifications.canNotify(context)) {
            scope.launch { snackbarHostState.showSnackbar(offMessage) }
        }
    }
    val deleted = stringResource(R.string.reminder_deleted)
    val inPast = stringResource(R.string.reminder_in_past)
    val locale = currentLocale()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ReminderEffect.Saved -> {
                    val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                        .format(Date(effect.triggerAt))
                    when {
                        ReminderNotifications.canNotify(context) ->
                            snackbarHostState.showSnackbar(context.getString(R.string.reminder_saved, whenText))
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> snackbarHostState.showSnackbar(offMessage)
                    }
                }
                ReminderEffect.InPast -> snackbarHostState.showSnackbar(inPast)
                ReminderEffect.Deleted -> snackbarHostState.showSnackbar(deleted)
            }
        }
    }
}

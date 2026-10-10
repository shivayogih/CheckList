package com.dataloom.checklist.presentation.reminder

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.currentAppLocale
import com.dataloom.checklist.presentation.components.ChipFlow
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.presentation.components.SelectableChip
import com.dataloom.checklist.reminder.ReminderNotifications
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** "10 Oct 2026, 6:00 PM": the date in the app language and the time on a 12-hour clock. */
fun reminderWhenText(millis: Long, locale: Locale): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis)) + ", " +
        reminderClockText(millis, locale)

/** "6:00 PM": reminders always use the 12-hour clock with AM and PM, which most users read most easily. */
fun reminderClockText(millis: Long, locale: Locale): String = TWELVE_HOUR.withLocale(locale).format(millis.local())

private val TWELVE_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun formatReminderTime(millis: Long): String = reminderWhenText(millis, currentAppLocale())

private fun Long.local(): ZonedDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()

/**
 * Picks the date and time of a reminder for [listTitle]: one-tap choices ("In 1 hour", "Tomorrow
 * morning") for the common cases, then large date and time buttons. Past dates cannot be picked, a
 * past time on today cannot be confirmed, and Save stays off, with a hint, while the time is not in
 * the future.
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
                isInFuture = isInFuture,
                onQuickPick = { time = it },
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
                time = time.local().with(date).millis()
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        val date = time.local()
        ReminderTimePicker(
            initial = date.toLocalTime(),
            isAllowed = { picked -> isInFuture(date.with(picked).millis()) },
            onPicked = { picked ->
                time = date.with(picked).millis()
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** One-tap reminder times, offered only while they are still ahead. */
private enum class QuickTime(@StringRes val label: Int) {
    IN_AN_HOUR(R.string.reminder_quick_hour),
    THIS_EVENING(R.string.reminder_quick_evening),
    TOMORROW_MORNING(R.string.reminder_quick_tomorrow),
    ;

    fun at(now: ZonedDateTime): Long = when (this) {
        IN_AN_HOUR -> now.plusHours(1).truncatedTo(ChronoUnit.MINUTES)
        THIS_EVENING -> now.with(LocalTime.of(EVENING_HOUR, 0)).truncatedTo(ChronoUnit.MINUTES)
        TOMORROW_MORNING -> now.plusDays(1).with(LocalTime.of(MORNING_HOUR, 0)).truncatedTo(ChronoUnit.MINUTES)
    }.millis()

    private companion object {
        const val EVENING_HOUR = 18
        const val MORNING_HOUR = 9
    }
}

@Composable
private fun ReminderTimeFields(
    listTitle: String,
    time: Long,
    valid: Boolean,
    isInFuture: (Long) -> Boolean,
    onQuickPick: (Long) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val locale = currentAppLocale()
    // Read once per opening: the choices do not need to move while the dialog is open.
    val now = remember { ZonedDateTime.now() }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.reminder_for_list, listTitle), style = MaterialTheme.typography.bodyMedium)
        ChipFlow {
            QuickTime.entries.forEach { quick ->
                val at = quick.at(now)
                if (isInFuture(at)) {
                    SelectableChip(
                        text = stringResource(quick.label),
                        selected = at == time,
                        onClick = { onQuickPick(at) },
                    )
                }
            }
        }
        OutlinedActionButton(
            text = stringResource(
                R.string.reminder_date_button,
                DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date(time)),
            ),
            leadingIcon = painterResource(R.drawable.ic_alarm),
            onClick = onPickDate,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedActionButton(
            text = stringResource(R.string.reminder_time_button, reminderClockText(time, locale)),
            leadingIcon = painterResource(R.drawable.ic_alarm),
            onClick = onPickTime,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!valid) {
            Text(
                stringResource(R.string.reminder_in_past),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works in UTC midnights; today and later are selectable, past days are greyed out.
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

/**
 * A 12-hour clock with AM and PM, whatever the phone's setting. OK stays off, with a hint, while the
 * picked time on the chosen day has already passed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePicker(
    initial: LocalTime,
    isAllowed: (LocalTime) -> Boolean,
    onPicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    val picked = LocalTime.of(state.hour, state.minute)
    val allowed = isAllowed(picked)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_pick_time)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = state)
                if (!allowed) {
                    Text(
                        stringResource(R.string.reminder_in_past),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(picked) }, enabled = allowed) {
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
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val offMessage = stringResource(R.string.reminder_notifications_off)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted || !ReminderNotifications.canNotify(context)) {
            scope.launch { snackbarHostState.showSnackbar(offMessage) }
        }
    }
    val deleted = stringResource(R.string.reminder_deleted)
    val inPast = stringResource(R.string.reminder_in_past)
    val locale = currentAppLocale()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ReminderEffect.Saved -> {
                    val whenText = reminderWhenText(effect.triggerAt, locale)
                    when {
                        ReminderNotifications.canNotify(context) ->
                            snackbarHostState.showSnackbar(resources.getString(R.string.reminder_saved, whenText))
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

package com.dataloom.checklist.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.bringIntoViewWhenFocused
import com.dataloom.checklist.presentation.common.unitLabel
import com.dataloom.checklist.presentation.components.CheckRow
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.presentation.components.PrimaryButton

/**
 * The command field on the checklist detail screen, with progress and the last result. Shown only while
 * the assistant is on; the review sheet is [AiReviewSheet].
 */
@Composable
fun AiCommandPanel(state: AiCommandUiState, onAction: (AiCommandAction) -> Unit, modifier: Modifier = Modifier) {
    val working = stringResource(R.string.ai_working)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewWhenFocused()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.command,
                onValueChange = { onAction(AiCommandAction.CommandChanged(it)) },
                // The visible label is also the TalkBack label; the example sits below as supporting text.
                label = { Text(stringResource(R.string.ai_command_label)) },
                supportingText = { Text(stringResource(R.string.ai_command_hint)) },
                singleLine = true,
                enabled = !state.isWorking,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onAction(AiCommandAction.Submit) }),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.ai_command_submit),
                onClick = { onAction(AiCommandAction.Submit) },
                enabled = state.canSubmit,
                compact = true,
            )
        }
        if (state.isWorking) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = working },
            )
        }
        state.message?.let { message -> AiMessageCard(message) { onAction(AiCommandAction.DismissMessage) } }
    }
}

@Composable
private fun AiMessageCard(message: AiMessageUi, onDismiss: () -> Unit) {
    val colors = if (message.isProblem) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, end = 8.dp)
                // Read out when it appears, without moving TalkBack focus.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            message.lines.forEach { Text(it.asString(), style = MaterialTheme.typography.bodyLarge) }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 8.dp)
                .heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.ai_message_dismiss)) }
    }
}

/**
 * "Check what the assistant will do": one checkbox row per step, then Confirm and Cancel. Closing the
 * sheet any other way counts as Cancel, so nothing is ever written without the Confirm button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReviewSheet(review: AiReviewUi, onAction: (AiCommandAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(AiCommandAction.Cancel) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.ai_review_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
            Text(
                stringResource(R.string.ai_review_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            review.steps.forEach { step ->
                CheckRow(
                    label = stepLabel(step),
                    supporting = stepDetails(step),
                    checked = step.checked,
                    onCheckedChange = { onAction(AiCommandAction.ToggleStep(step.callIndex, it)) },
                )
            }
            review.notUnderstood.forEach { part ->
                Text(
                    stringResource(R.string.ai_review_not_understood, part),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            ReviewButtons(canConfirm = review.canConfirm, onAction = onAction)
        }
    }
}

@Composable
private fun ReviewButtons(canConfirm: Boolean, onAction: (AiCommandAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        OutlinedActionButton(
            text = stringResource(R.string.action_cancel),
            onClick = { onAction(AiCommandAction.Cancel) },
            compact = true,
        )
        PrimaryButton(
            text = stringResource(R.string.ai_action_confirm),
            onClick = { onAction(AiCommandAction.Confirm) },
            enabled = canConfirm,
            compact = true,
        )
    }
}

@Composable
private fun stepLabel(step: AiStepUi): String = stringResource(
    when (step.kind) {
        AiStepKind.CREATE_CHECKLIST -> R.string.ai_step_create_checklist
        AiStepKind.ADD_CATEGORY -> R.string.ai_step_add_category
        AiStepKind.ADD_ITEM -> R.string.ai_step_add
        AiStepKind.SET_QUANTITY -> R.string.ai_step_set_quantity
        AiStepKind.SET_UNIT -> R.string.ai_step_set_unit
        AiStepKind.COMPLETE -> R.string.ai_step_complete
        AiStepKind.UNCOMPLETE -> R.string.ai_step_uncomplete
        AiStepKind.REMOVE_SECTION -> R.string.ai_step_remove_section
        AiStepKind.DELETE_ITEM -> R.string.ai_step_delete
    },
    step.name,
)

/** "2 kg, to Groceries", "2 kg", "To Groceries", a unit alone, or null. */
@Composable
private fun stepDetails(step: AiStepUi): String? {
    val amount = amountText(step.quantity, step.unit)
    val section = step.sectionName
    return when {
        amount != null && section != null -> stringResource(R.string.ai_step_amount_to_section, amount, section)
        amount != null -> amount
        section != null -> stringResource(R.string.ai_step_to_section, section)
        else -> null
    }
}

@Composable
private fun amountText(quantity: Quantity?, unit: UnitCode?): String? = when {
    quantity != null && unit != null -> stringResource(R.string.item_quantity_with_unit, quantity.toPlainString(), unitLabel(unit))
    quantity != null -> quantity.toPlainString()
    unit != null -> unitLabel(unit)
    else -> null
}

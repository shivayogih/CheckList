package com.dataloom.checklist.presentation.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.components.DialogSurface
import com.dataloom.checklist.presentation.components.ItemRow
import com.dataloom.checklist.presentation.components.RadioRow
import com.dataloom.checklist.presentation.components.TextActionButton
import com.dataloom.checklist.presentation.theme.Dimens
import com.dataloom.checklist.settings.TextSize
import com.dataloom.checklist.settings.ThemeMode

@Composable
internal fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
internal fun TextSize.label(): String = stringResource(
    when (this) {
        TextSize.NORMAL -> R.string.text_size_normal
        TextSize.LARGE -> R.string.text_size_large
        TextSize.EXTRA_LARGE -> R.string.text_size_extra_large
    },
)

/** Theme choice: picking a row applies it and closes the dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        DialogSurface(
            title = stringResource(R.string.settings_theme),
            actions = {
                TextActionButton(stringResource(R.string.action_cancel), onClick = onDismiss, compact = true)
            },
        ) {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    RadioRow(
                        title = mode.label(),
                        selected = mode == current,
                        onSelect = { onSelect(mode) },
                        showDivider = index < ThemeMode.entries.lastIndex,
                    )
                }
            }
        }
    }
}

/**
 * Text size with a live preview (UI-SPEC screen 17): the preview row is drawn at the chosen size, and
 * Save applies it to the whole app. [current] is the size the app is drawn at now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextSizeDialog(current: TextSize, onSave: (TextSize) -> Unit, onDismiss: () -> Unit) {
    var choice by rememberSaveable { mutableStateOf(current) }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        DialogSurface(
            title = stringResource(R.string.settings_text_size),
            actions = {
                TextActionButton(stringResource(R.string.action_cancel), onClick = onDismiss, compact = true)
                TextActionButton(stringResource(R.string.action_save), onClick = { onSave(choice) }, compact = true)
            },
        ) {
            Column(Modifier.selectableGroup()) {
                TextSize.entries.forEach { size ->
                    RadioRow(title = size.label(), selected = size == choice, onSelect = { choice = size })
                }
            }
            TextSizePreview(current = current, choice = choice)
        }
    }
}

@Composable
private fun TextSizePreview(current: TextSize, choice: TextSize) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.Corner16)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(Dimens.Border1, colors.outline, shape)
            .padding(Dimens.Space12),
    ) {
        Text(
            stringResource(R.string.text_size_preview),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        val density = LocalDensity.current
        // The app is already drawn at [current]; swap that factor for the one being tried.
        val preview = Density(density.density, density.fontScale / current.scale * choice.scale)
        CompositionLocalProvider(LocalDensity provides preview) {
            ItemRow(
                name = stringResource(R.string.text_size_preview_item),
                checked = false,
                onCheckedChange = {},
            )
        }
    }
}

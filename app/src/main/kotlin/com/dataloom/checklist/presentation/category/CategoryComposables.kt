package com.dataloom.checklist.presentation.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.presentation.components.TextInputDialog

/**
 * Categories as a two-column grid of selectable tiles (J1 step 3), so long lists need less scrolling.
 * Categories the user has used before come first under "Used often" (the repository orders them by
 * use, then by when they were last used); the rest follow under "All categories".
 */
fun LazyListScope.categoryOptions(options: List<CategoryOptionUi>, onToggle: (CategoryId) -> Unit) {
    val (frequent, others) = options.partition { it.frequent }
    if (frequent.isEmpty()) {
        categoryTiles(options, onToggle)
        return
    }
    item(key = "group-frequent", contentType = "group") {
        CategoryGroupHeading(stringResource(R.string.category_group_frequent))
    }
    categoryTiles(frequent, onToggle)
    if (others.isNotEmpty()) {
        item(key = "group-all", contentType = "group") {
            CategoryGroupHeading(stringResource(R.string.category_group_all))
        }
        categoryTiles(others, onToggle)
    }
}

@Composable
private fun CategoryGroupHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

private fun LazyListScope.categoryTiles(options: List<CategoryOptionUi>, onToggle: (CategoryId) -> Unit) {
    items(options.chunked(2), key = { it.first().id.value }, contentType = { "tiles" }) { pair ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEach { option ->
                CategoryTile(option, onToggle = { onToggle(option.id) }, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoryTile(option: CategoryOptionUi, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val state = stringResource(if (option.selected) R.string.state_selected else R.string.state_not_selected)
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (option.selected) colors.primaryContainer else colors.surfaceContainerLow)
            .border(
                width = if (option.selected) 2.dp else 1.dp,
                color = if (option.selected) colors.primary else colors.outlineVariant,
                shape = shape,
            )
            .toggleable(value = option.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Emoji icons are decorative; the label carries the meaning.
        Text(option.icon, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics { })
        Text(
            option.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (option.selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (option.selected) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun CreateCategoryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedActionButton(
        text = stringResource(R.string.category_create),
        leadingIcon = painterResource(R.drawable.ic_add),
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    )
}

@Composable
fun NewCategoryDialog(
    state: NewCategoryDialogUi,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TextInputDialog(
        title = stringResource(R.string.category_create),
        label = stringResource(R.string.category_name_label),
        value = state.name,
        errorText = state.error?.asString(),
        confirmLabel = stringResource(R.string.action_create),
        enabled = state.canConfirm,
        maxLength = FieldLimits.CATEGORY_NAME_MAX,
        onValueChange = onNameChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

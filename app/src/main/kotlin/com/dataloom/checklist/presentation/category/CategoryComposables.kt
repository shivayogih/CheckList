package com.dataloom.checklist.presentation.category

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.components.CheckRow
import com.dataloom.checklist.presentation.components.TextInputDialog

/** Category rows as large checkbox rows (J1 step 3). */
fun LazyListScope.categoryOptions(options: List<CategoryOptionUi>, onToggle: (CategoryId) -> Unit) {
    items(options, key = { it.id.value }) { option ->
        CheckRow(
            label = option.name,
            checked = option.selected,
            onCheckedChange = { onToggle(option.id) },
            leading = option.icon,
        )
    }
}

@Composable
fun CreateCategoryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        Text(stringResource(R.string.category_create), modifier = Modifier.padding(start = 8.dp))
    }
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

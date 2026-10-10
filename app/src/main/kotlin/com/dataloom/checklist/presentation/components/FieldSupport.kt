package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.formatCount

/**
 * Supporting text for a text field (CL-280): the [message] (an error, which TalkBack reads through the
 * field's error semantics, or a hint) and, when [max] is given, a "length / max" counter on the end.
 * The counter is hidden from accessibility (the limit is also in the "too long" error), so a screen
 * reader is not interrupted at every keystroke. Returns null when there is nothing to show.
 */
fun fieldSupportingText(message: String?, length: Int, max: Int?): (@Composable () -> Unit)? {
    if (message == null && max == null) return null
    return {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message.orEmpty(), modifier = Modifier.weight(1f))
            if (max != null) {
                val colors = MaterialTheme.colorScheme
                Text(
                    stringResource(R.string.input_counter, formatCount(length), formatCount(max)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (length > max) colors.error else colors.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** Length as the user counts it (code points), the same as the validators. */
fun String.inputLength(): Int = codePointCount(0, length)

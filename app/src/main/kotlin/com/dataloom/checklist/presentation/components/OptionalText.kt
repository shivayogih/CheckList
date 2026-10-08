package com.dataloom.checklist.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** A text slot (such as a field's supportingText) that is absent when there is nothing to say. */
fun optionalText(text: String?): (@Composable () -> Unit)? = if (text == null) null else { { Text(text) } }

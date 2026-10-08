package com.dataloom.checklist.presentation.common

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.dataloom.checklist.R

/** "3 of 8 done" with the plural form chosen by [completed] and digits formatted by [LocaleNumbers]. */
@Composable
fun progressText(completed: Int, total: Int): String =
    pluralStringResource(R.plurals.progress_items_done, completed, formatCount(completed), formatCount(total))

/** A plural whose only argument is [count], such as "Add 3 items". */
@Composable
fun countText(@PluralsRes pluralRes: Int, count: Int): String = pluralStringResource(pluralRes, count, formatCount(count))

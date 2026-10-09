package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R

/**
 * The CheckList logo (vector drawable `ic_app_logo`). Use it on the Welcome / first-run screen at 96dp and in
 * Settings > About at 72dp or smaller. The content description comes from a string resource in all seven languages.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Image(
        painter = painterResource(R.drawable.ic_app_logo),
        contentDescription = stringResource(R.string.app_logo_description),
        modifier = modifier.size(size),
    )
}

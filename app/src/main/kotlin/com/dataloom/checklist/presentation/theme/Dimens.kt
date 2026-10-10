package com.dataloom.checklist.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Sizes from the mockup stylesheet and UI-SPEC section 3. One place, so no screen invents its own. */
object Dimens {
    // Touch targets and bars.
    val MinTouchTarget = 48.dp
    val ButtonHeight = 48.dp
    val SearchFieldHeight = 48.dp
    val TopBarHeight = 56.dp
    val RowMinHeight = 56.dp
    val ItemRowMinHeight = 60.dp
    val FieldHeight = 56.dp
    val FieldMultiLineHeight = 96.dp
    val ChipHeight = 36.dp
    val UnitChipHeight = 26.dp
    val CheckboxSize = 26.dp
    val RadioSize = 24.dp
    val SwitchWidth = 56.dp
    val SwitchHeight = 32.dp
    val StepperButton = 56.dp
    val StepperButtonCompact = 48.dp
    val StepperValueHeight = 56.dp
    val StepperValueHeightCompact = 48.dp
    val StepperValueMinWidth = 120.dp
    val StepperValueMinWidthCompact = 72.dp
    val StepperValueMaxWidth = 160.dp
    val EmptyStateArt = 120.dp
    val ProgressBar = 8.dp
    val ProgressBarLarge = 12.dp
    val SettingsIcon = 22.dp
    val Icon = 24.dp
    val SettingsIconTile = 40.dp
    val SplashLogo = 112.dp
    val SettingsAboutLogo = 48.dp

    // Corners.
    val Corner8 = 8.dp
    val Corner12 = 12.dp
    val Corner14 = 14.dp
    val Corner16 = 16.dp
    val Corner20 = 20.dp
    val Corner28 = 28.dp

    // Border widths.
    val Border1 = 1.dp
    val Border2 = 2.dp
    val Border3 = 3.dp

    // Spacing.
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp

    /** Side padding of every screen. */
    val ScreenPadding = 16.dp
}

internal val CheckListShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.Corner8),
    small = RoundedCornerShape(Dimens.Corner12),
    medium = RoundedCornerShape(Dimens.Corner16),
    large = RoundedCornerShape(Dimens.Corner20),
    extraLarge = RoundedCornerShape(Dimens.Corner28),
)

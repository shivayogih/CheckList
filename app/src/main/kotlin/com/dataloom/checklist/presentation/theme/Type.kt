package com.dataloom.checklist.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// Compact scale (body 16, secondary 12 to 14, titles 18 to 20): text sits neatly on the screen and the
// user's system font size does the enlarging. Line height stays generous because tall Indic scripts (Kannada, Tamil, Malayalam) from clipping, and the line height is not trimmed
// (LineHeightStyle.Trim.None) so the first and last line keep their full height. All sizes in sp,
// so system font scaling still applies on top. Floor: 12 sp. The scale is recorded in docs/ui-design-system.md.
// System fonts only: Android picks the right script font for each language.

private val NoTrim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    lineHeightStyle = NoTrim,
)

internal val CheckListTypography = Typography(
    displayLarge = style(28, 36, FontWeight.Bold),
    displayMedium = style(26, 34, FontWeight.Bold),
    displaySmall = style(24, 32, FontWeight.Bold),
    headlineLarge = style(26, 34, FontWeight.Bold),
    // Onboarding titles (UI-SPEC section 2).
    headlineMedium = style(24, 32, FontWeight.Bold),
    headlineSmall = style(22, 30, FontWeight.SemiBold),
    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(18, 26, FontWeight.SemiBold),
    titleSmall = style(16, 24, FontWeight.SemiBold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 22),
    bodySmall = style(12, 18),
    labelLarge = style(15, 22, FontWeight.SemiBold),
    labelMedium = style(13, 18, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.SemiBold),
)

/** Text styles from the mockups that have no Material slot. All are 12 sp or larger. */
object CheckListText {
    /** "5 of 12 done" in the progress card. */
    val bigCount = style(20, 28, FontWeight.Bold)

    /** "42%" in the progress card. */
    val percent = style(26, 34, FontWeight.Bold)

    /** The number in the quantity stepper. */
    val stepperValue = style(26, 34, FontWeight.Bold)

    /** "x of y done" under a checklist title, and the "Completed" label. */
    val progressLabel = style(14, 20, FontWeight.SemiBold)

    /** Unit chip next to an item name ("5 KG"). */
    val unitChip = style(13, 18, FontWeight.SemiBold)

    /** Filter and quick-start chips. */
    val chip = style(14, 20, FontWeight.Medium)

    /** Section header in Settings ("General"). */
    val sectionHeader = style(14, 20, FontWeight.Bold)

    /** Category name above its items. */
    val categoryName = style(18, 26, FontWeight.Bold)

    /** Name of a row in a list (item, selectable row, settings row). */
    val rowTitle = style(16, 24, FontWeight.Medium)

    /** Subtitle under the top bar title. */
    val subtitle = style(13, 18)

    /** Emoji lead of a card or row. */
    val emojiSmall = style(18, 26)
    val emojiLarge = style(24, 32)
}

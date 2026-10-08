package com.dataloom.checklist.presentation.home

import com.dataloom.checklist.domain.validation.FieldLimits

/**
 * Builds the duplicate's title with the localized pattern ("%1$s (copy)"), shortening the original
 * title when the result would exceed the title limit, so duplicating a long title never fails.
 * Lengths count code points, like the domain validator.
 */
fun copyTitle(title: String, pattern: (String) -> String, max: Int = FieldLimits.TITLE_MAX): String {
    val base = title.trim()
    val full = pattern(base)
    val overflow = full.codePointCount(0, full.length) - max
    if (overflow <= 0) return full
    val baseLength = base.codePointCount(0, base.length)
    val keep = (baseLength - overflow).coerceAtLeast(0)
    val shortened = base.substring(0, base.offsetByCodePoints(0, keep)).trimEnd()
    return pattern(shortened)
}

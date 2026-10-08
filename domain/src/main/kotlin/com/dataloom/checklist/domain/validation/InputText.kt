package com.dataloom.checklist.domain.validation

/**
 * Text hygiene shared by every text field (CL-280). One place decides which characters are never
 * kept, so the keyboard filter in the UI, the validators, the import path and the AI path agree.
 *
 * Removed: control characters (Unicode category Cc, except line breaks and tabs, handled below),
 * bidirectional marks, overrides and isolates (U+061C, U+200E, U+200F, U+202A-U+202E,
 * U+2066-U+2069), zero-width space, word joiner, byte-order mark and soft hyphen. They are invisible,
 * can reorder or hide text, and let a blank-looking value pass a "not empty" check.
 *
 * Kept: ZWJ and ZWNJ (U+200D, U+200C), which Indic spelling and emoji sequences need.
 *
 * Whitespace: every Unicode space (no-break space, ideographic space, tab) becomes a plain space and
 * runs of spaces collapse to one. Single-line text also turns line breaks (including U+2028 and
 * U+2029) into spaces; multi-line text keeps them as "\n", at most one blank line in a row.
 */
object InputText {

    /** Characters dropped everywhere; see the class comment. */
    private fun isDropped(ch: Char): Boolean = when {
        ch == '\n' || ch == '\r' || ch == '\t' -> false
        Character.getType(ch) == Character.CONTROL.toInt() -> true
        ch == '؜' || ch == '‎' || ch == '‏' -> true
        ch in '‪'..'‮' || ch in '⁦'..'⁩' -> true
        ch == '​' || ch == '⁠' || ch == '﻿' || ch == '­' -> true
        else -> false
    }

    private fun isLineBreak(ch: Char): Boolean = ch == '\n' || ch == '\r' || ch == ' ' || ch == ' '

    /**
     * Removes the characters above and normalizes whitespace, without trimming (a user who is still
     * typing "Rice " needs the trailing space to stay). Use [normalize] to store a value.
     */
    fun clean(raw: String, multiline: Boolean = false): String {
        val out = StringBuilder(raw.length)
        for ((i, ch) in raw.withIndex()) {
            when {
                // "\r\n" is one break: the "\n" that follows is the one kept.
                ch == '\r' && raw.getOrNull(i + 1) == '\n' -> Unit
                isLineBreak(ch) -> if (multiline) appendLineBreak(out) else appendSpace(out)
                isDropped(ch) -> Unit
                ch.isWhitespace() -> appendSpace(out)
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun appendSpace(out: StringBuilder) {
        if (out.isEmpty() || out.last() != ' ') out.append(' ')
    }

    private fun appendLineBreak(out: StringBuilder) {
        while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
        val trailingBreaks = out.reversed().takeWhile { it == '\n' }.length
        if (trailingBreaks < 2) out.append('\n')
    }

    /** [clean] plus trim: the form a value is validated and stored in. */
    fun normalize(raw: String, multiline: Boolean = false): String = clean(raw, multiline).trim()

    /** True when nothing visible is left after [clean]: empty, spaces, line breaks, zero-width characters. */
    fun isBlank(raw: String?): Boolean = raw == null || clean(raw, multiline = true).isBlank()

    /**
     * The keyboard filter for a text field: [clean] (leading and trailing spaces stay, so the user sees
     * what they typed and a whitespace-only entry can be reported), cut at [max] code points.
     * Apply it to every change event so pasted control or bidi characters never reach the form state.
     */
    fun forTyping(raw: String, max: Int, multiline: Boolean = false): String =
        clean(raw, multiline).takeCodePoints(max)

    /**
     * [forTyping] for a field whose validator limit is [limit]: the filter allows one code point more,
     * so a paste that is too long still shows the "too long" error (nothing is silently cut off) while
     * the field cannot grow without bound.
     */
    fun forField(raw: String, limit: Int, multiline: Boolean = false): String =
        forTyping(raw, limit + 1, multiline)

    /** E-mail addresses contain no whitespace at all, so any typed or pasted space is dropped. */
    fun forEmail(raw: String): String =
        clean(raw).filterNot { it.isWhitespace() }.takeCodePoints(FieldLimits.EMAIL_MAX)
}

internal fun String.takeCodePoints(max: Int): String {
    if (codePointLength() <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}

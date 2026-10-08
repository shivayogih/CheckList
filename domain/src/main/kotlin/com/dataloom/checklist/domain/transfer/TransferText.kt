package com.dataloom.checklist.domain.transfer

import java.text.Normalizer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

/** Text rules for values that come from a file, which may have been written by anything. */
internal object TransferText {

    private val REF = Regex("[A-Za-z0-9_.\\-]+")

    /** "rice", "cooking_oil", "custom:4f1c...". */
    private val CANONICAL_KEY = Regex("[a-z0-9_\\-]+(:[A-Za-z0-9_\\-]+)?")

    /** BCP 47 shaped: "kn", "en-IN", "zh-Hant-TW". */
    private val LOCALE = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")

    /**
     * Removes control characters (Unicode category Cc) and bidirectional overrides/isolates
     * (U+202A-U+202E, U+2066-U+2069), which no name or note in the app's left-to-right scripts
     * needs and which can hide or disguise content. Other format characters stay: ZWJ/ZWNJ are
     * part of Indic spelling and emoji sequences. With [multiline], line breaks are kept
     * (normalized to "\n"); otherwise they become spaces.
     */
    fun clean(raw: String, multiline: Boolean = false): String {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        val out = StringBuilder(text.length)
        text.forEach { ch ->
            when {
                ch == '\n' -> out.append(if (multiline) '\n' else ' ')
                ch == '\t' -> out.append(' ')
                Character.getType(ch) == Character.CONTROL.toInt() -> Unit
                ch in '‪'..'‮' || ch in '⁦'..'⁩' -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    fun cleanOrNull(raw: String?, multiline: Boolean = false): String? = raw?.let { clean(it, multiline) }

    fun isValidRef(ref: String): Boolean = ref.length <= TransferLimits.MAX_REF && REF.matches(ref)

    fun isValidCanonicalKey(key: String): Boolean = key.length <= TransferLimits.MAX_CANONICAL_KEY && CANONICAL_KEY.matches(key)

    fun isValidLocale(locale: String): Boolean = locale.length <= TransferLimits.MAX_LOCALE && LOCALE.matches(locale)

    fun isValidIcon(icon: String): Boolean =
        icon.isNotBlank() && icon.length <= TransferLimits.MAX_ICON && icon.none { Character.getType(it) == Character.CONTROL.toInt() }

    fun isValidTimestamp(text: String): Boolean = try {
        Instant.parse(text)
        true
    } catch (_: DateTimeParseException) {
        false
    }

    fun timestamp(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    /** Shortened ref for issue reports, so a hostile 2,000-character ref is not echoed back. */
    fun reportRef(ref: String?): String? = ref?.take(TransferLimits.MAX_REF)

    /** Key for case-insensitive name matching: NFKC, lower case, trimmed (like the catalog search). */
    fun matchKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

    /** "pooja_items" -> "Pooja items": a name for a seeded category this device does not know. */
    fun humanize(canonicalKey: String): String =
        canonicalKey.substringAfter(':').replace('_', ' ').replace('-', ' ').trim()
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
}

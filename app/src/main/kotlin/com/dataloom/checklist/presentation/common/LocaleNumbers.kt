package com.dataloom.checklist.presentation.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.dataloom.checklist.domain.model.Quantity
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats every number the UI shows: counts in strings ("3 of 8 done") and item amounts ("2.5 kg").
 *
 * Separators and grouping follow the app language, but digits are always Western Arabic (0-9) in
 * every language (assumption A-03, localization.md): Marathi, for example, would otherwise get
 * Devanagari digits from `%d`. That is why string resources take numbers as `%1$s` text produced
 * here, never as `%1$d`; StringResourcesTest enforces it.
 */
object LocaleNumbers {

    /** [locale] with the Unicode `nu-latn` extension, so formatters use 0-9. */
    fun westernDigits(locale: Locale): Locale =
        Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()

    fun formatCount(count: Long, locale: Locale): String =
        NumberFormat.getIntegerInstance(westernDigits(locale)).format(count)

    fun formatCount(count: Int, locale: Locale): String = formatCount(count.toLong(), locale)

    /** "2.5", "1,000", "0.125": up to the 3 decimals a [Quantity] stores, no trailing zeros. */
    fun formatQuantity(quantity: Quantity, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(westernDigits(locale))
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = Quantity.SCALE
        format.roundingMode = RoundingMode.HALF_UP
        return format.format(quantity.amount)
    }

    /** Integers among string arguments become formatted text; anything else is passed unchanged. */
    fun formatArgs(args: List<Any>, locale: Locale): Array<Any> = args.map { arg ->
        when (arg) {
            is Int -> formatCount(arg, locale)
            is Long -> formatCount(arg, locale)
            else -> arg
        }
    }.toTypedArray()
}

/** The app language of this configuration (the per-app language when the user picked one). */
fun Resources.appLocale(): Locale = configuration.locales[0] ?: Locale.getDefault()

@Composable
fun currentAppLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
fun formatCount(count: Int): String = LocaleNumbers.formatCount(count, currentAppLocale())

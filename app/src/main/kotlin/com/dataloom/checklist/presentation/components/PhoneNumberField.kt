package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.phone.PhoneCountries
import com.dataloom.checklist.domain.phone.PhoneCountry
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.presentation.common.currentAppLocale
import com.dataloom.checklist.presentation.theme.Dimens
import java.text.Collator
import java.util.Locale

/**
 * Phone number as two boxes in one row (CL-380), the way large apps draw it: a country button (flag and
 * dial code, India by default) that opens [CountryPickerDialog], and a digits-only number box. The
 * caller filters typing and pasting through `PhoneNumberInput` and owns whether the picker is open, so
 * both survive rotation.
 */
@Composable
fun PhoneNumberField(
    label: String,
    countryIso: String,
    digits: String,
    onDigitsChange: (String) -> Unit,
    onCountryClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    val country = PhoneCountries.orDefault(countryIso)
    FormField(
        label = label,
        value = digits,
        onValueChange = onDigitsChange,
        modifier = modifier,
        errorText = errorText,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = imeAction),
        leading = { CountryCodeButton(country, enabled, onCountryClick) },
    )
}

/** Flag, dial code and a drop-down arrow; TalkBack reads the country name and that it can be changed. */
@Composable
private fun CountryCodeButton(country: PhoneCountry, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = countryName(country.iso, currentAppLocale())
    val description = stringResource(R.string.profile_phone_country, name, country.dialText)
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .heightIn(min = Dimens.FieldHeight)
            .border(Dimens.Border2, colors.outline, RoundedCornerShape(Dimens.Corner12))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onClick { onClick(); true } else disabled()
            }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(country.flag, style = MaterialTheme.typography.titleLarge)
        Text(country.dialText, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1)
        Icon(
            painter = painterResource(R.drawable.ic_arrow_drop_down),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Full-screen, searchable list of every country (offline: names come from the platform's locale data in
 * the app language). India comes first, the rest follow in the language's alphabetical order. Search
 * matches the local name, the English name, the ISO code or the dial code. The query is saved state, so
 * it survives rotation; it is not personal data.
 */
@Composable
fun CountryPickerDialog(selectedIso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val locale = currentAppLocale()
    val countries = remember(locale) { sortedCountries(locale) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(countries, query) { countries.filter { it.matches(query) } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onDismiss)
                    Text(
                        text = stringResource(R.string.profile_country_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                FormField(
                    label = stringResource(R.string.profile_country_search),
                    value = query,
                    onValueChange = { query = InputText.forField(it, SEARCH_MAX) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_country_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(shown, key = { it.country.iso }) { row ->
                        CountryRow(row, selected = row.country.iso == selectedIso) { onPick(row.country.iso) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(row: CountryRowData, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(row.country.flag, style = MaterialTheme.typography.titleLarge)
        Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            row.country.dialText,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** One list row: the country and its names, precomputed once per language. */
private data class CountryRowData(val country: PhoneCountry, val name: String, val englishName: String) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val digits = q.removePrefix("+")
        if (digits.isNotEmpty() && digits.all { it in '0'..'9' }) return country.dialCode.toString().startsWith(digits)
        return name.contains(q, ignoreCase = true) ||
            englishName.contains(q, ignoreCase = true) ||
            country.iso.equals(q, ignoreCase = true)
    }
}

/** The default country first, then every other one sorted by its name in [locale]. */
private fun sortedCountries(locale: Locale): List<CountryRowData> {
    val collator = Collator.getInstance(locale)
    val rows = PhoneCountries.all.map {
        CountryRowData(it, countryName(it.iso, locale), countryName(it.iso, Locale.ENGLISH))
    }
    val (first, rest) = rows.partition { it.country.iso == PhoneCountries.DEFAULT_ISO }
    return first + rest.sortedWith { a, b -> collator.compare(a.name, b.name) }
}

/** The platform's name for the region, in [locale]; the ISO code if the platform has none. */
internal fun countryName(iso: String, locale: Locale): String =
    Locale.Builder().setRegion(iso).build().getDisplayCountry(locale).ifBlank { iso }

private const val SEARCH_MAX = 40

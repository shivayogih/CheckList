package com.dataloom.checklist.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef

/**
 * Built-in unit labels come from string resources keyed by unit code (section 9.3), so they are
 * translated; custom units show the label the user typed.
 */
private val BUILT_IN_UNIT_LABELS: Map<String, Int> = mapOf(
    "KG" to R.string.unit_kg,
    "GRAM" to R.string.unit_gram,
    "LITRE" to R.string.unit_litre,
    "MILLILITRE" to R.string.unit_millilitre,
    "DOZEN" to R.string.unit_dozen,
    "PIECE" to R.string.unit_piece,
    "PACK" to R.string.unit_pack,
    "BOX" to R.string.unit_box,
    "BOTTLE" to R.string.unit_bottle,
    "PAIR" to R.string.unit_pair,
    "METER" to R.string.unit_meter,
    "NOS" to R.string.unit_nos,
    "BUNCH" to R.string.unit_bunch,
)

/** Full names shown beside the short label in the unit picker: "kg (Kilogram)". */
private val BUILT_IN_UNIT_FULL_NAMES: Map<String, Int> = mapOf(
    "KG" to R.string.unit_kg_full,
    "GRAM" to R.string.unit_gram_full,
    "LITRE" to R.string.unit_litre_full,
    "MILLILITRE" to R.string.unit_millilitre_full,
    "DOZEN" to R.string.unit_dozen_full,
    "PIECE" to R.string.unit_piece_full,
    "PACK" to R.string.unit_pack_full,
    "BOX" to R.string.unit_box_full,
    "BOTTLE" to R.string.unit_bottle_full,
    "PAIR" to R.string.unit_pair_full,
    "METER" to R.string.unit_meter_full,
    "NOS" to R.string.unit_nos_full,
    "BUNCH" to R.string.unit_bunch_full,
)

@StringRes
fun builtInUnitLabel(code: UnitCode): Int? = BUILT_IN_UNIT_LABELS[code.value]

/** Label for a unit; an unknown built-in code falls back to the code itself rather than crashing. */
@Composable
fun unitLabel(unit: UnitDef): String = unit.customLabel ?: unitLabel(unit.code)

/**
 * Picker label: "kg (Kilogram)". When the full name says the same as the short label ("box"), or the
 * unit is custom, only the label is shown.
 */
@Composable
fun unitPickerLabel(unit: UnitDef): String {
    val short = unitLabel(unit)
    val fullRes = if (unit.customLabel == null) BUILT_IN_UNIT_FULL_NAMES[unit.code.value] else null
    val full = fullRes?.let { stringResource(it) } ?: return short
    if (full.equals(short, ignoreCase = true)) return short
    return stringResource(R.string.unit_with_full_name, short, full)
}

@Composable
fun unitLabel(code: UnitCode): String {
    val res = builtInUnitLabel(code)
    return if (res != null) stringResource(res) else code.value
}

/**
 * "5 kg", "2.5" (formatted for the app language), or null when there is no amount. [unit] is null
 * when the item has no unit.
 */
@Composable
fun quantityText(quantity: Quantity?, unit: UnitDef?): String? {
    if (quantity == null) return null
    val amount = LocaleNumbers.formatQuantity(quantity, currentAppLocale())
    return if (unit == null) amount else stringResource(R.string.item_quantity_with_unit, amount, unitLabel(unit))
}

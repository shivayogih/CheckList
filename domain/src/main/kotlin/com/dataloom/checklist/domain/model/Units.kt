package com.dataloom.checklist.domain.model

/**
 * Stable unit identifier stored in the database and in exports. Built-in codes are listed in
 * [BuiltInUnits]; user-created units use "CUSTOM_<uuid>". Display labels are never stored here:
 * the UI resolves built-in labels from string resources and custom ones from [UnitDef.customLabel].
 */
@JvmInline
value class UnitCode(val value: String) {
    val isCustom: Boolean get() = value.startsWith(CUSTOM_PREFIX)

    companion object {
        const val CUSTOM_PREFIX = "CUSTOM_"
    }
}

data class UnitDef(
    val code: UnitCode,
    /** Whole-number units (Piece, Nos, Dozen...) reject 2.5. */
    val allowsDecimal: Boolean,
    val customLabel: String? = null,
    val sortOrder: Int = 0,
) {
    val isCustom: Boolean get() = code.isCustom
}

object BuiltInUnits {
    val KG = UnitDef(UnitCode("KG"), allowsDecimal = true, sortOrder = 10)
    val GRAM = UnitDef(UnitCode("GRAM"), allowsDecimal = true, sortOrder = 20)
    val LITRE = UnitDef(UnitCode("LITRE"), allowsDecimal = true, sortOrder = 30)
    val MILLILITRE = UnitDef(UnitCode("MILLILITRE"), allowsDecimal = true, sortOrder = 40)
    val DOZEN = UnitDef(UnitCode("DOZEN"), allowsDecimal = false, sortOrder = 50)
    val PIECE = UnitDef(UnitCode("PIECE"), allowsDecimal = false, sortOrder = 60)
    val PACK = UnitDef(UnitCode("PACK"), allowsDecimal = false, sortOrder = 70)
    val BOX = UnitDef(UnitCode("BOX"), allowsDecimal = false, sortOrder = 80)
    val BOTTLE = UnitDef(UnitCode("BOTTLE"), allowsDecimal = false, sortOrder = 90)
    val PAIR = UnitDef(UnitCode("PAIR"), allowsDecimal = false, sortOrder = 100)
    val METER = UnitDef(UnitCode("METER"), allowsDecimal = true, sortOrder = 110)
    val NOS = UnitDef(UnitCode("NOS"), allowsDecimal = false, sortOrder = 120)

    val all: List<UnitDef> = listOf(KG, GRAM, LITRE, MILLILITRE, DOZEN, PIECE, PACK, BOX, BOTTLE, PAIR, METER, NOS)

    fun byCode(code: UnitCode): UnitDef? = all.firstOrNull { it.code == code }
}

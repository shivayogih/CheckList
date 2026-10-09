package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistValidatorTest {

    @Test
    fun `title is trimmed and blank description becomes null`() {
        val result = ChecklistValidator.validate("  Diwali shopping  ", "   ")
        assertEquals(ValidationResult.Valid(ChecklistFields("Diwali shopping", null)), result)
    }

    @Test
    fun `blank title is rejected`() {
        assertEquals(listOf(ValidationError.TITLE_BLANK), errorsOf(ChecklistValidator.validate("   ", null)))
    }

    @Test
    fun `title length boundary is 100 after trim`() {
        assertTrue(ChecklistValidator.validate(" " + "a".repeat(100) + " ", null) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.TITLE_TOO_LONG), errorsOf(ChecklistValidator.validate("a".repeat(101), null)))
    }

    @Test
    fun `length counts code points so emoji count once`() {
        val emoji = "🛒" // shopping cart, two UTF-16 units
        assertTrue(ChecklistValidator.validate(emoji.repeat(100), null) is ValidationResult.Valid)
    }

    @Test
    fun `description boundary is 500`() {
        assertTrue(ChecklistValidator.validate("T", "d".repeat(500)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.DESCRIPTION_TOO_LONG), errorsOf(ChecklistValidator.validate("T", "d".repeat(501))))
    }

    @Test
    fun `all errors are reported together`() {
        assertEquals(
            listOf(ValidationError.TITLE_BLANK, ValidationError.DESCRIPTION_TOO_LONG),
            errorsOf(ChecklistValidator.validate("", "d".repeat(501))),
        )
    }

    @Test
    fun `validateTitle trims and checks only the title`() {
        assertEquals(ValidationResult.Valid("Copy"), ChecklistValidator.validateTitle(" Copy "))
        assertEquals(listOf(ValidationError.TITLE_BLANK), errorsOf(ChecklistValidator.validateTitle("")))
    }
}

class ItemValidatorTest {

    @Test
    fun `valid item is normalized`() {
        val result = ItemValidator.validate(" Rice ", Quantity.of(5), BuiltInUnits.KG, "  ")
        assertEquals(ValidationResult.Valid(ItemFields("Rice", Quantity.of(5), BuiltInUnits.KG.code, null)), result)
    }

    @Test
    fun `item name boundaries are 1 to 80`() {
        assertEquals(listOf(ValidationError.ITEM_NAME_BLANK), errorsOf(ItemValidator.validate(" ", null, null, null)))
        assertTrue(ItemValidator.validate("n".repeat(80), null, null, null) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.ITEM_NAME_TOO_LONG), errorsOf(ItemValidator.validate("n".repeat(81), null, null, null)))
    }

    @Test
    fun `notes boundary is 500`() {
        assertTrue(ItemValidator.validate("Rice", null, null, "n".repeat(500)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.NOTES_TOO_LONG), errorsOf(ItemValidator.validate("Rice", null, null, "n".repeat(501))))
    }

    @Test
    fun `notes are trimmed`() {
        val fields = (ItemValidator.validate("Rice", null, null, "  basmati ") as ValidationResult.Valid).value
        assertEquals("basmati", fields.notes)
    }

    @Test
    fun `name, notes and quantity errors are combined`() {
        assertEquals(
            listOf(ValidationError.ITEM_NAME_BLANK, ValidationError.NOTES_TOO_LONG, ValidationError.UNIT_WITHOUT_QUANTITY),
            errorsOf(ItemValidator.validate("", null, BuiltInUnits.KG, "n".repeat(501))),
        )
    }
}

class QuantityValidatorTest {

    private val customWhole = UnitDef(UnitCode("CUSTOM_1"), allowsDecimal = false, customLabel = "bunch")

    @Test
    fun `no quantity and no unit is fine`() {
        assertTrue(QuantityValidator.validate(null, null).isEmpty())
    }

    @Test
    fun `quantity without unit is allowed`() {
        assertTrue(QuantityValidator.validate(Quantity.parse("2.5"), null).isEmpty())
    }

    @Test
    fun `unit without quantity is rejected`() {
        assertEquals(listOf(ValidationError.UNIT_WITHOUT_QUANTITY), QuantityValidator.validate(null, BuiltInUnits.PIECE))
    }

    @Test
    fun `decimal unit accepts fractions`() {
        assertTrue(QuantityValidator.validate(Quantity.parse("0.125"), BuiltInUnits.KG).isEmpty())
    }

    @Test
    fun `every whole-number unit rejects fractions and accepts whole amounts`() {
        val wholeUnits = BuiltInUnits.all.filterNot { it.allowsDecimal } + customWhole
        assertEquals(9, wholeUnits.size) // eight built-in whole units plus one custom
        for (unit in wholeUnits) {
            assertEquals(unit.code.value, listOf(ValidationError.QUANTITY_MUST_BE_WHOLE), QuantityValidator.validate(Quantity.parse("2.5"), unit))
            assertTrue(unit.code.value, QuantityValidator.validate(Quantity.parse("2.000"), unit).isEmpty())
        }
    }

    @Test
    fun `values beyond the maximum are rejected even when built from milli`() {
        assertTrue(QuantityValidator.validate(Quantity.parse("99999"), null).isEmpty())
        assertEquals(listOf(ValidationError.QUANTITY_OUT_OF_RANGE), QuantityValidator.validate(Quantity.fromMilli(99_999_001), null))
    }
}

class CategoryAndUnitValidatorTest {

    @Test
    fun `category name is trimmed and limited to 50`() {
        assertEquals(ValidationResult.Valid("Gifts"), CategoryValidator.validateName("  Gifts "))
        assertTrue(CategoryValidator.validateName("c".repeat(50)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.CATEGORY_NAME_TOO_LONG), errorsOf(CategoryValidator.validateName("c".repeat(51))))
        assertEquals(listOf(ValidationError.CATEGORY_NAME_BLANK), errorsOf(CategoryValidator.validateName("\t")))
    }

    @Test
    fun `unit label is trimmed and limited to 20`() {
        assertEquals(ValidationResult.Valid("bunch"), UnitValidator.validateLabel(" bunch "))
        assertTrue(UnitValidator.validateLabel("u".repeat(20)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.UNIT_LABEL_TOO_LONG), errorsOf(UnitValidator.validateLabel("u".repeat(21))))
        assertEquals(listOf(ValidationError.UNIT_LABEL_BLANK), errorsOf(UnitValidator.validateLabel("")))
    }

    @Test
    fun `every error code names its field`() {
        assertEquals(Field.UNIT, ValidationError.UNIT_WITHOUT_QUANTITY.field)
        assertEquals(Field.QUANTITY, ValidationError.QUANTITY_MUST_BE_WHOLE.field)
        assertNull(ValidationError.entries.firstOrNull { it.name.startsWith("TITLE") && it.field != Field.TITLE })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid result needs at least one error`() {
        ValidationResult.Invalid(emptyList())
    }
}

internal fun errorsOf(result: ValidationResult<*>): List<ValidationError> =
    (result as? ValidationResult.Invalid)?.errors.orEmpty()

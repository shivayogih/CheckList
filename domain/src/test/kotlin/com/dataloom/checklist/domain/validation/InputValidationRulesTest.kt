package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CL-280: every text rule, applied the same way by each validator. */
class InputValidationRulesTest {

    private val invisibleOnly = listOf("", " ", "   ", "\t", "\n", "\r\n", " ", "　", "​", "﻿", "‮", " ​ \n\t ")

    private fun <T> ValidationResult<T>.errors(): List<ValidationError> =
        (this as? ValidationResult.Invalid)?.errors.orEmpty()

    private fun <T> ValidationResult<T>.value(): T = (this as ValidationResult.Valid).value

    @Test
    fun `every required name rejects empty, whitespace-only and invisible-only text`() {
        for (blank in invisibleOnly) {
            assertEquals("title '$blank'", listOf(ValidationError.TITLE_BLANK), ChecklistValidator.validate(blank, null).errors())
            assertEquals("title only '$blank'", listOf(ValidationError.TITLE_BLANK), ChecklistValidator.validateTitle(blank).errors())
            assertEquals("item '$blank'", listOf(ValidationError.ITEM_NAME_BLANK), ItemValidator.validate(blank, null, null, null).errors())
            assertEquals("category '$blank'", listOf(ValidationError.CATEGORY_NAME_BLANK), CategoryValidator.validateName(blank).errors())
            assertEquals("unit '$blank'", listOf(ValidationError.UNIT_LABEL_BLANK), UnitValidator.validateLabel(blank).errors())
        }
    }

    @Test
    fun `every optional text turns blank and invisible-only text into null`() {
        for (blank in invisibleOnly) {
            assertEquals(null, ChecklistValidator.validate("T", blank).value().description)
            assertEquals(null, ItemValidator.validate("Rice", null, null, blank).value().notes)
            val profile = ProfileValidator.validate(blank, blank, blank, blank).value()
            assertTrue("profile '$blank'", profile.isEmpty)
        }
    }

    @Test
    fun `names are stored without control, bidi or zero width characters and with single spaces`() {
        assertEquals("Rice bag", ItemValidator.validate("  Rice ‮​  bag\u0000 ", null, null, null).value().name)
        assertEquals("Fruit and veg", CategoryValidator.validateName("Fruit\nand\tveg").value())
        assertEquals("kg", UnitValidator.validateLabel("​kg​").value())
        assertEquals("A B", ChecklistValidator.validateTitle("A  B").value())
    }

    @Test
    fun `invisible characters do not count towards the length limit`() {
        val padded = "a".repeat(80).chunked(1).joinToString("​")
        assertTrue(ItemValidator.validate(padded, null, null, null) is ValidationResult.Valid)
    }

    @Test
    fun `notes keep line breaks and drop control characters`() {
        val notes = ItemValidator.validate("Rice", null, null, "  line1\r\nline2\u0007\n\n\n\nline3 ").value().notes
        assertEquals("line1\nline2\n\nline3", notes)
    }

    @Test
    fun `joiners in Indic names are preserved`() {
        val name = "ಕ್‍ಷ"
        assertEquals(name, ItemValidator.validate(name, null, null, null).value().name)
    }

    @Test
    fun `length limits are exact for every field`() {
        fun ok(text: String, max: Int) = assertTrue(text.length == max)
        ok("a".repeat(FieldLimits.TITLE_MAX), 100)
        assertTrue(ChecklistValidator.validateTitle("a".repeat(FieldLimits.TITLE_MAX)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.TITLE_TOO_LONG), ChecklistValidator.validateTitle("a".repeat(FieldLimits.TITLE_MAX + 1)).errors())
        assertTrue(CategoryValidator.validateName("a".repeat(FieldLimits.CATEGORY_NAME_MAX)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.CATEGORY_NAME_TOO_LONG), CategoryValidator.validateName("a".repeat(FieldLimits.CATEGORY_NAME_MAX + 1)).errors())
        assertTrue(UnitValidator.validateLabel("a".repeat(FieldLimits.UNIT_LABEL_MAX)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.UNIT_LABEL_TOO_LONG), UnitValidator.validateLabel("a".repeat(FieldLimits.UNIT_LABEL_MAX + 1)).errors())
    }

    @Test
    fun `profile display name and address are cleaned`() {
        val profile = ProfileValidator.validate("  A‮​  B \n", null, null, "12 Main St\u0000\r\n\r\n  Bengaluru ​").value()
        assertEquals("A B", profile.displayName)
        assertEquals("12 Main St\nBengaluru", profile.address)
    }

    @Test
    fun `phone with letters or symbols is invalid and Indic digits are accepted`() {
        for (bad in listOf("98765abcde", "9876543210x", "#9876543210", "*123*456*", "+91 98765 4321O", "-9876543210")) {
            assertEquals("'$bad'", listOf(ValidationError.PHONE_INVALID), ProfileValidator.validate(null, null, bad).errors())
        }
        assertEquals("9876543210", ProfileValidator.validate(null, null, "९८७६५४३२१०").value().phone)
    }

    @Test
    fun `email rejects spaces, control characters and missing parts`() {
        for (bad in listOf("a b@c.com", "a@b", "@c.com", "a@@c.com", "a@c..com", "a@.com", "a@c.com.")) {
            assertEquals("'$bad'", listOf(ValidationError.EMAIL_INVALID), ProfileValidator.validate(null, bad, null).errors())
        }
        assertEquals("a@c.com", ProfileValidator.validate(null, "  a@c.com​ ", null).value().email)
    }

    @Test
    fun `item with a quantity amount stays valid and whole-number units still reject fractions`() {
        assertTrue(ItemValidator.validate("Rice", Quantity.parse("2,5"), BuiltInUnits.KG, null) is ValidationResult.Valid)
        assertEquals(
            listOf(ValidationError.QUANTITY_MUST_BE_WHOLE),
            ItemValidator.validate("Eggs", Quantity.parse("2.5"), BuiltInUnits.PIECE, null).errors(),
        )
    }

    @Test
    fun `search and AI limits are defined`() {
        assertEquals(100, FieldLimits.SEARCH_MAX)
        assertEquals(500, FieldLimits.AI_COMMAND_MAX)
    }
}

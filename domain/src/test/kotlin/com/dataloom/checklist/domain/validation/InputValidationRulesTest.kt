package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CL-280: every text rule, applied the same way by each validator. */
class InputValidationRulesTest {

    private val invisibleOnly = listOf(
        "", " ", "   ", "\t", "\n", "\r\n", "\u00A0", "\u3000", "\u200B", "\uFEFF", "\u202E", " \u200B \n\t ",
    )

    private fun title(text: String) = ChecklistValidator.validateTitle(text).errors()

    private fun <T> ValidationResult<T>.errors(): List<ValidationError> =
        (this as? ValidationResult.Invalid)?.errors.orEmpty()

    private fun <T> ValidationResult<T>.value(): T = (this as ValidationResult.Valid).value

    @Test
    fun `every required name rejects empty, whitespace-only and invisible-only text`() {
        for (blank in invisibleOnly) {
            val checklist = ChecklistValidator.validate(blank, null).errors()
            val item = ItemValidator.validate(blank, null, null, null).errors()
            val category = CategoryValidator.validateName(blank).errors()
            val unit = UnitValidator.validateLabel(blank).errors()
            assertEquals("title '$blank'", listOf(ValidationError.TITLE_BLANK), checklist)
            assertEquals("title only '$blank'", listOf(ValidationError.TITLE_BLANK), title(blank))
            assertEquals("item '$blank'", listOf(ValidationError.ITEM_NAME_BLANK), item)
            assertEquals("category '$blank'", listOf(ValidationError.CATEGORY_NAME_BLANK), category)
            assertEquals("unit '$blank'", listOf(ValidationError.UNIT_LABEL_BLANK), unit)
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
        val rice = ItemValidator.validate("  Rice \u202E\u200B  bag\u0000 ", null, null, null)
        assertEquals("Rice bag", rice.value().name)
        assertEquals("Fruit and veg", CategoryValidator.validateName("Fruit\nand\tveg").value())
        assertEquals("kg", UnitValidator.validateLabel("\u200Bkg\u200B").value())
        assertEquals("A B", ChecklistValidator.validateTitle("A\u00A0\u00A0B").value())
    }

    @Test
    fun `invisible characters do not count towards the length limit`() {
        val padded = "a".repeat(80).chunked(1).joinToString("\u200B")
        assertTrue(ItemValidator.validate(padded, null, null, null) is ValidationResult.Valid)
    }

    @Test
    fun `notes keep line breaks and drop control characters`() {
        val notes = ItemValidator.validate("Rice", null, null, "  line1\r\nline2\u0007\n\n\n\nline3 ").value().notes
        assertEquals("line1\nline2\n\nline3", notes)
    }

    @Test
    fun `joiners in Indic names are preserved`() {
        val name = "\u0C95\u0CCD\u200D\u0CB7"
        assertEquals(name, ItemValidator.validate(name, null, null, null).value().name)
    }

    @Test
    fun `length limits are exact for every field`() {
        fun ok(text: String, max: Int) = assertTrue(text.length == max)
        ok("a".repeat(FieldLimits.TITLE_MAX), 100)
        assertTrue(ChecklistValidator.validateTitle("a".repeat(FieldLimits.TITLE_MAX)) is ValidationResult.Valid)
        assertEquals(listOf(ValidationError.TITLE_TOO_LONG), title("a".repeat(FieldLimits.TITLE_MAX + 1)))
        assertTrue(CategoryValidator.validateName("a".repeat(FieldLimits.CATEGORY_NAME_MAX)) is ValidationResult.Valid)
        val category = CategoryValidator.validateName("a".repeat(FieldLimits.CATEGORY_NAME_MAX + 1)).errors()
        assertEquals(listOf(ValidationError.CATEGORY_NAME_TOO_LONG), category)
        assertTrue(UnitValidator.validateLabel("a".repeat(FieldLimits.UNIT_LABEL_MAX)) is ValidationResult.Valid)
        val unit = UnitValidator.validateLabel("a".repeat(FieldLimits.UNIT_LABEL_MAX + 1)).errors()
        assertEquals(listOf(ValidationError.UNIT_LABEL_TOO_LONG), unit)
    }

    @Test
    fun `profile display name and address are cleaned`() {
        val address = "12 Main St\u0000\r\n\r\n  Bengaluru \u200B"
        val profile = ProfileValidator.validate("  A\u202E\u200B  B \n", null, null, address).value()
        assertEquals("A B", profile.displayName)
        assertEquals("12 Main St\nBengaluru", profile.address)
    }

    @Test
    fun `phone with letters or symbols is invalid and Indic digits are accepted`() {
        for (bad in listOf("98765abcde", "9876543210x", "#9876543210", "*123*456*", "+91 98765 4321O", "-9876543210")) {
            val errors = ProfileValidator.validate(null, null, bad).errors()
            assertEquals("'$bad'", listOf(ValidationError.PHONE_INVALID), errors)
        }
        val devanagari = "\u096F\u096E\u096D\u096C\u096B\u096A\u0969\u0968\u0967\u0966"
        assertEquals("9876543210", ProfileValidator.validate(null, null, devanagari).value().phone)
    }

    @Test
    fun `email rejects spaces, control characters and missing parts`() {
        for (bad in listOf("a b@c.com", "a@b", "@c.com", "a@@c.com", "a@c..com", "a@.com", "a@c.com.")) {
            val errors = ProfileValidator.validate(null, bad, null).errors()
            assertEquals("'$bad'", listOf(ValidationError.EMAIL_INVALID), errors)
        }
        assertEquals("a@c.com", ProfileValidator.validate(null, "  a@c.com\u200B ", null).value().email)
    }

    @Test
    fun `item with a quantity amount stays valid and whole-number units still reject fractions`() {
        val rice = ItemValidator.validate("Rice", Quantity.parse("2,5"), BuiltInUnits.KG, null)
        assertTrue(rice is ValidationResult.Valid)
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

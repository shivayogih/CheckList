package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {

    // Display name

    @Test
    fun `name is trimmed, inner whitespace collapsed and blank optional fields become null`() {
        val result = ProfileValidator.validate("  Asha \n  Rao ", "  ", "")
        assertEquals(ValidationResult.Valid(UserProfile("Asha Rao", null, null)), result)
    }

    @Test
    fun `every field is optional and a blank name becomes null`() {
        assertEquals(ValidationResult.Valid(UserProfile()), ProfileValidator.validate(" \t ", null, null))
        assertEquals(ValidationResult.Valid(UserProfile()), ProfileValidator.validate(null, " ", "", "  \n "))
        assertTrue((ProfileValidator.validate("", "", "") as ValidationResult.Valid).value.isEmpty)
    }

    @Test
    fun `a profile without a name keeps its other fields`() {
        val result = ProfileValidator.validate("", "asha@example.com", "+91 98765 43210") as ValidationResult.Valid
        assertEquals(UserProfile(null, "asha@example.com", "+91 98765 43210", null), result.value)
        assertFalse(result.value.isEmpty)
    }

    // Address

    @Test
    fun `address keeps line breaks, trims lines, drops blank lines and control characters`() {
        val raw = "  12,  4th Cross \r\n\n  Vidyanagar\u0007, Hubballi  \r"
        val result = ProfileValidator.validate(null, null, null, raw) as ValidationResult.Valid
        assertEquals("12, 4th Cross\nVidyanagar, Hubballi", result.value.address)
    }

    @Test
    fun `address boundary is 200 code points`() {
        assertTrue(ProfileValidator.validate(null, null, null, "ಅ".repeat(FieldLimits.ADDRESS_MAX)) is ValidationResult.Valid)
        assertTrue(ProfileValidator.validate(null, null, null, "🙂".repeat(FieldLimits.ADDRESS_MAX)) is ValidationResult.Valid)
        assertEquals(
            listOf(ValidationError.ADDRESS_TOO_LONG),
            errorsOf(ProfileValidator.validate(null, null, null, "a".repeat(FieldLimits.ADDRESS_MAX + 1))),
        )
    }

    @Test
    fun `name boundary is 50 code points`() {
        assertTrue(ProfileValidator.validate("ಅ".repeat(50), null, null) is ValidationResult.Valid)
        assertTrue(ProfileValidator.validate("🙂".repeat(50), null, null) is ValidationResult.Valid)
        assertEquals(
            listOf(ValidationError.DISPLAY_NAME_TOO_LONG),
            errorsOf(ProfileValidator.validate("a".repeat(51), null, null)),
        )
    }

    // Email

    @Test
    fun `plausible emails are accepted and trimmed`() {
        listOf("asha@example.com", "a.b+list@mail.example.in", "ಆಶಾ@ಉದಾಹರಣೆ.ಭಾರತ").forEach {
            assertEquals(it, (ProfileValidator.validate("Asha", " $it ", null) as ValidationResult.Valid).value.email)
        }
    }

    @Test
    fun `malformed emails are rejected`() {
        listOf("asha", "@example.com", "asha@", "asha@example", "asha@@example.com", "asha@example..com", "as ha@example.com", "asha@.com", "asha@example.com.")
            .forEach {
                assertEquals(it, listOf(ValidationError.EMAIL_INVALID), errorsOf(ProfileValidator.validate("Asha", it, null)))
            }
    }

    @Test
    fun `email length limits`() {
        val domain = "@" + "d".repeat(60) + ".example.com"
        assertEquals(
            listOf(ValidationError.EMAIL_INVALID),
            errorsOf(ProfileValidator.validate("Asha", "l".repeat(65) + domain, null)),
        )
        assertTrue(ProfileValidator.validate("Asha", "l".repeat(64) + domain, null) is ValidationResult.Valid)
        val tooLong = "a@" + "d".repeat(250) + ".in"
        assertEquals(listOf(ValidationError.EMAIL_TOO_LONG), errorsOf(ProfileValidator.validate("Asha", tooLong, null)))
    }

    // Phone

    @Test
    fun `common phone formats are accepted`() {
        listOf("+91 98765 43210", "098765-43210", "(080) 2345 6789", "9876543").forEach {
            assertTrue(it, ProfileValidator.validate("Asha", null, it) is ValidationResult.Valid)
        }
    }

    @Test
    fun `indic digits are normalized to ascii and whitespace collapsed`() {
        val result = ProfileValidator.validate("Asha", null, " +९१  ९८७६५ ४३२१० ") as ValidationResult.Valid
        assertEquals("+91 98765 43210", result.value.phone)
    }

    @Test
    fun `invalid phones are rejected`() {
        listOf("12345", "1234567890123456", "+91 98765 4321O", "98765#43210", "++919876543210", "-9876543210", "9-".repeat(13) + "9")
            .forEach {
                assertEquals(it, listOf(ValidationError.PHONE_INVALID), errorsOf(ProfileValidator.validate("Asha", null, it)))
            }
    }

    @Test
    fun `every problem is reported at once`() {
        assertEquals(
            listOf(ValidationError.DISPLAY_NAME_TOO_LONG, ValidationError.EMAIL_INVALID, ValidationError.PHONE_INVALID, ValidationError.ADDRESS_TOO_LONG),
            errorsOf(ProfileValidator.validate("n".repeat(51), "nope", "abc", "a".repeat(201))),
        )
    }

    // Privacy

    @Test
    fun `profile toString never prints personal data`() {
        val text = UserProfile("Asha Rao", "asha@example.com", "+91 98765 43210", "12 Vidyanagar").toString()
        listOf("Asha", "asha@", "98765", "Vidyanagar").forEach { assertFalse(text.contains(it)) }
    }

    private fun errorsOf(result: ValidationResult<*>): List<ValidationError> = (result as ValidationResult.Invalid).errors
}

package com.dataloom.checklist.domain.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedLanguagesTest {

    @Test
    fun `first release ships exactly the seven agreed languages in picker order`() {
        assertEquals(
            listOf("en", "kn", "hi", "ta", "te", "mr", "ml"),
            SupportedLanguages.all.map { it.tag },
        )
    }

    @Test
    fun `language tags are unique`() {
        val tags = SupportedLanguages.all.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun `fromTag ignores region and case`() {
        assertEquals("kn", SupportedLanguages.fromTag("kn-IN")?.tag)
        assertEquals("hi", SupportedLanguages.fromTag("HI")?.tag)
        assertEquals("ta", SupportedLanguages.fromTag("ta_IN")?.tag)
    }

    @Test
    fun `fromTag returns null for blank or unsupported tags`() {
        assertNull(SupportedLanguages.fromTag(null))
        assertNull(SupportedLanguages.fromTag(""))
        assertNull(SupportedLanguages.fromTag("  "))
        assertNull(SupportedLanguages.fromTag("bn"))
    }

    @Test
    fun `empty stored locales mean system default`() {
        assertEquals(LanguagePreference.SystemDefault, SupportedLanguages.preferenceFromTags(""))
        assertEquals(LanguagePreference.SystemDefault, SupportedLanguages.preferenceFromTags(null))
    }

    @Test
    fun `unsupported stored locale falls back to system default`() {
        assertEquals(LanguagePreference.SystemDefault, SupportedLanguages.preferenceFromTags("fr-FR"))
    }

    @Test
    fun `first stored locale decides the preference`() {
        val preference = SupportedLanguages.preferenceFromTags("ml-IN,en-US")
        assertEquals(LanguagePreference.Specific(SupportedLanguages.fromTag("ml")!!), preference)
    }
}

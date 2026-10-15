package com.dataloom.checklist.ai.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftMatcherTest {

    private val matcher = DraftMatcher(DraftTemplates.bundled())

    private fun ids(prompt: String, locale: String = "en") = matcher.match(prompt, locale)?.templates?.map { it.id }

    @Test
    fun `the tester's requests pick the right scenario and keep a readable title`() {
        val goa = matcher.match("Create check list for my next Go trip", "en")!!
        assertEquals(listOf("trip"), goa.templates.map { it.id })
        assertEquals("Go trip", goa.title)

        assertEquals("Goa trip", matcher.match("Create checklist for my next Goa trip", "en")!!.title)

        val diwali = matcher.match("Diwali festival shopping", "en")!!
        // "festival" and "shopping" are only hints: they support Diwali but add no second template.
        assertEquals(listOf("diwali"), diwali.templates.map { it.id })
        assertEquals("Diwali festival shopping", diwali.title)

        assertEquals("property", ids("Prepare the document checklist for my new property buy")!!.first())
        assertEquals(listOf("property"), ids("Prepare the document checklist for my new property buy"))
    }

    @Test
    fun `requests in Indian languages match, including inflected words`() {
        val kannada = matcher.match("ಗೋವಾ ಪ್ರವಾಸಕ್ಕೆ ಚೆಕ್‌ಲಿಸ್ಟ್ ಮಾಡಿ", "kn")!!
        assertEquals(listOf("trip"), kannada.templates.map { it.id })
        assertEquals("ಗೋವಾ ಪ್ರವಾಸಕ್ಕೆ", kannada.title)

        assertEquals("diwali", ids("दिवाली की खरीदारी", "hi")!!.first())
        assertEquals("diwali", ids("தீபாவளி ஷாப்பிங்", "ta")!!.first())
        assertEquals("property", ids("కొత్త ఇల్లు కొనుగోలు పత్రాలు", "te")!!.first())
        assertEquals("wedding", ids("लग्नाची तयारी", "mr")!!.first())
        assertEquals("festival", ids("ഓണം ആഘോഷം", "ml")!!.first())
        assertEquals("moving", ids("मेरा घर बदलना है", "hi")!!.first())
    }

    @Test
    fun `mixed scripts work`() {
        assertEquals("trip", ids("ಗೋವಾ trip", "kn")!!.first())
    }

    @Test
    fun `two scenarios with their own keywords are merged`() {
        assertEquals(listOf("diwali", "festival"), ids("Diwali pooja"))
        assertEquals(setOf("trip", "abroad"), ids("Dubai trip, visa needed")!!.toSet())
    }

    @Test
    fun `every keyword finds its own template`() {
        DraftTemplates.bundled().templates.forEach { template ->
            template.keywords.forEach { (language, words) ->
                words.forEach { word ->
                    assertTrue("$word -> ${template.id}", ids(word, language).orEmpty().contains(template.id))
                }
            }
        }
    }

    @Test
    fun `unknown requests are not understood`() {
        assertNull(matcher.match("asdf qwerty", "en"))
        assertNull(matcher.match("   ", "en"))
    }

    @Test
    fun `a request that is only command words falls back to the template title`() {
        assertEquals("ಪ್ರವಾಸ", matcher.match("ಪ್ರವಾಸ", "kn")!!.title)
        assertNull(matcher.title("create a checklist for me"))
        assertEquals("Trip", matcher.match("make a trip list", "en")!!.title)
    }
}

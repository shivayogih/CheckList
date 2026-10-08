package com.dataloom.checklist.localization

import com.dataloom.checklist.domain.localization.SupportedLanguages
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the localization foundation: every supported language has a resource folder, and every
 * translatable English string exists in all of them (no missing or stray keys).
 */
class StringResourcesTest {

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val resDir = File("src/main/res")

    private fun translatableKeys(folder: String): Set<String> {
        val file = File(resDir, "$folder/strings.xml")
        assertTrue("Missing ${file.path}", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .map { it.getAttribute("name") }
            .toSet()
    }

    private fun pluralKeys(folder: String): Set<String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "$folder/strings.xml")).getElementsByTagName("plurals")
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }.toSet()
    }

    @Test
    fun `all languages have exactly the English plurals`() {
        val english = pluralKeys("values")
        assertTrue(english.contains("home_greeting_in_progress"))
        SupportedLanguages.all
            .filter { it != SupportedLanguages.ENGLISH }
            .forEach { language ->
                assertEquals("Plural mismatch in values-${language.tag}", english, pluralKeys("values-${language.tag}"))
            }
    }

    @Test
    fun `every supported language except English has a values folder`() {
        SupportedLanguages.all
            .filter { it != SupportedLanguages.ENGLISH }
            .forEach { language ->
                assertTrue(
                    "No translations for ${language.tag}",
                    File(resDir, "values-${language.tag}/strings.xml").isFile,
                )
            }
    }

    @Test
    fun `all languages have exactly the English keys`() {
        val english = translatableKeys("values")
        assertTrue(english.isNotEmpty())
        SupportedLanguages.all
            .filter { it != SupportedLanguages.ENGLISH }
            .forEach { language ->
                assertEquals(
                    "Key mismatch in values-${language.tag}",
                    english,
                    translatableKeys("values-${language.tag}"),
                )
            }
    }
}

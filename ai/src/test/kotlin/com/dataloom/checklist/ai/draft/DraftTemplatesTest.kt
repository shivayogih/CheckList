package com.dataloom.checklist.ai.draft

import com.dataloom.checklist.ai.parser.TextNormalizer
import com.dataloom.checklist.domain.localization.SupportedLanguages
import com.dataloom.checklist.domain.validation.FieldLimits
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The template file is code too: a typo in a key or a missing translation must fail the build. */
class DraftTemplatesTest {

    private val templates = DraftTemplates.bundled()
    private val languages = SupportedLanguages.all.map { it.tag }.toSet()

    /** Seed item key to its category key, read from the bundled catalog. */
    private val seedCategoryOf: Map<String, String> by lazy {
        val dir = File(System.getProperty("checklist.seedDir") ?: error("checklist.seedDir is not set"))
        Json.parseToJsonElement(File(dir, "catalog.json").readText()).jsonObject.getValue("items").jsonArray.associate {
            val item = it.jsonObject
            item.getValue("key").jsonPrimitive.content to item.getValue("category").jsonPrimitive.content
        }
    }

    @Test
    fun `every template has a title and keywords in every language`() {
        templates.templates.forEach { template ->
            assertEquals(template.id, languages, template.title.keys)
            assertEquals(template.id, languages, template.keywords.keys)
            template.keywords.forEach { (language, words) ->
                assertTrue("${template.id}/$language", words.isNotEmpty())
            }
            assertTrue("${template.id} hints", template.hints.keys.all { it in languages })
        }
        assertEquals(languages, templates.fillers.keys)
    }

    @Test
    fun `template ids are unique`() {
        assertEquals(templates.templates.size, templates.templates.map { it.id }.toSet().size)
    }

    @Test
    fun `every catalog key exists in the category it is listed under`() {
        templates.templates.forEach { template ->
            template.sections.forEach { section ->
                section.items.forEach { key ->
                    assertEquals("${template.id}: $key", section.category, seedCategoryOf[key])
                }
            }
        }
    }

    @Test
    fun `typed items have a valid name in every language`() {
        templates.templates.flatMap { it.sections }.flatMap { it.custom }.forEach { names ->
            assertEquals(names.toString(), languages, names.keys)
            names.values.forEach { name ->
                assertTrue(name, name.isNotBlank() && name.codePointCount(0, name.length) <= FieldLimits.ITEM_NAME_MAX)
            }
        }
    }

    @Test
    fun `every keyword normalizes to at least one word`() {
        templates.templates.flatMap { it.keywords.values.flatten() + it.hints.values.flatten() }.forEach { keyword ->
            assertTrue(keyword, TextNormalizer.words(keyword).isNotEmpty())
        }
    }
}

package com.dataloom.checklist.localization

import com.dataloom.checklist.domain.localization.SupportedLanguages
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Format rules for every string and plural in all seven languages (CL-142, docs/localization.md):
 *
 * - every plural exists in every language with the `one` and `other` forms (the CLDR categories of
 *   all seven languages);
 * - a translation uses exactly the placeholders of its English source, so no argument is lost or
 *   added when word order changes;
 * - placeholders are positional (`%1$s`) and numbers are passed as text from LocaleNumbers, never
 *   as `%d`, so digits stay Western in every language (A-03);
 * - a literal `%` is written `%%`, or the string is marked `formatted="false"`.
 */
class StringFormatTest {

    private val resDir = File("src/main/res")
    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?([a-zA-Z%])""")
    private val folders = listOf("values") + SupportedLanguages.all.filter { it != SupportedLanguages.ENGLISH }.map { "values-${it.tag}" }

    private data class Entry(val key: String, val text: String, val formatted: Boolean)

    /** Strings as `name` and plural items as `name#quantity`. */
    private fun entries(folder: String): Map<String, Entry> {
        val result = linkedMapOf<String, Entry>()
        listOf("strings.xml", "plurals.xml").map { File(resDir, "$folder/$it") }.filter { it.isFile }.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val e = strings.item(i) as Element
                if (e.getAttribute("translatable") == "false" && folder != "values") continue
                result[e.getAttribute("name")] = Entry(e.getAttribute("name"), e.textContent, e.getAttribute("formatted") != "false")
            }
            val plurals = doc.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val p = plurals.item(i) as Element
                val items = p.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val item = items.item(j) as Element
                    val key = "${p.getAttribute("name")}#${item.getAttribute("quantity")}"
                    result[key] = Entry(key, item.textContent, true)
                }
            }
        }
        return result
    }

    private fun placeholders(entry: Entry): List<String> =
        if (!entry.formatted) emptyList() else placeholder.findAll(entry.text).map { it.value }.filter { it != "%%" }.sorted().toList()

    @Test
    fun `every plural has one and other forms in every language`() {
        folders.forEach { folder ->
            val keys = entries(folder).keys.filter { '#' in it }
            val names = keys.map { it.substringBefore('#') }.toSet()
            names.forEach { name ->
                assertTrue("$folder/$name lacks 'one'", "$name#one" in keys)
                assertTrue("$folder/$name lacks 'other'", "$name#other" in keys)
            }
        }
        val englishPlurals = entries("values").keys.filter { '#' in it }.map { it.substringBefore('#') }.toSet()
        folders.drop(1).forEach { folder ->
            val plurals = entries(folder).keys.filter { '#' in it }.map { it.substringBefore('#') }.toSet()
            assertEquals("Plural names in $folder", englishPlurals, plurals)
        }
    }

    @Test
    fun `translations use exactly the English placeholders`() {
        val english = entries("values")
        folders.drop(1).forEach { folder ->
            entries(folder).forEach { (key, entry) ->
                val source = english[key.replace("#one", "#other")] ?: english[key] ?: return@forEach
                assertEquals("Placeholders of $folder/$key", placeholders(source), placeholders(entry))
            }
        }
    }

    @Test
    fun `placeholders are positional strings, never %d`() {
        folders.forEach { folder ->
            entries(folder).values.forEach { entry ->
                placeholders(entry).forEach { p ->
                    assertTrue("$folder/${entry.key}: '$p' is not positional (use %1\$s)", Regex("""%\d+\$.*""").matches(p))
                    assertTrue("$folder/${entry.key}: '$p' formats a number; pass LocaleNumbers text as %N\$s", p.endsWith("s"))
                }
            }
        }
    }

    @Test
    fun `a literal percent sign is escaped or the string is not formatted`() {
        folders.forEach { folder ->
            entries(folder).values.filter { it.formatted }.forEach { entry ->
                val rest = placeholder.replace(entry.text, "")
                assertTrue("$folder/${entry.key} has a bare '%': escape it as %% or set formatted=\"false\"", '%' !in rest)
            }
        }
    }
}

package com.dataloom.checklist.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Each language uses one word per concept (docs/localization.md, "Glossary"). This test fails when a
 * string brings back a variant the CL-330 strings audit replaced, such as a second word for "item"
 * or an English loan word where the app already uses the native one.
 */
class TerminologyTest {

    private val resDir = File("src/main/res")

    /** Folder to the variants that must not appear, each with the glossary word to use instead. */
    private val replacedVariants = mapOf(
        "values-hi" to mapOf(
            "चीज़ें" to "आइटम", "वस्तु" to "आइटम", "इम्पोर्ट" to "आयात", "चेकलिस्ट" to "सूची",
            "शेयर" to "साझा", "पाएं" to "पाएँ", "सूचियां" to "सूचियाँ",
        ),
        "values-mr" to mapOf(
            "आयटम" to "वस्तू", "इम्पोर्ट" to "आयात", "चेकलिस्ट" to "यादी", "श्रेणी" to "वर्ग",
            "अ‍ॅप" to "ॲप",
        ),
        "values-kn" to mapOf(
            "ಐಟಂ" to "ವಸ್ತು", "ಇಂಪೋರ್ಟ್" to "ಆಮದು", "ಚೆಕ್‌ಲಿಸ್ಟ್" to "ಪಟ್ಟಿ", "ಬೇಕೆ?" to "ಬೇಕೇ?",
        ),
        "values-te" to mapOf(
            "అంశ" to "వస్తువు", "యూనిట్" to "కొలమానం", "ఇంపోర్ట్" to "దిగుమతి",
            "చెక్‌లిస్ట్" to "జాబితా", "మళ్ళీ" to "మళ్లీ", "ఇమెయిల్" to "ఈమెయిల్",
        ),
        "values-ta" to mapOf(
            "தொலைபேசி" to "ஃபோன்", "விருப்பத்திற்குரியது" to "விருப்பம்",
            "சரிபார்ப்புப் பட்டியல்" to "பட்டியல்",
        ),
        "values-ml" to mapOf(
            "പട്ടിക" to "ലിസ്റ്റ്", "ഓപ്ഷണൽ" to "ഐച്ഛികം", "സാധന" to "ഇനം",
            "ചെക്ക്‌ലിസ്റ്റ്" to "ലിസ്റ്റ്",
        ),
    )

    private fun texts(folder: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "$folder/strings.xml"))
        val result = LinkedHashMap<String, String>()
        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i) as Element
            result[element.getAttribute("name")] = element.textContent
        }
        val plurals = document.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val plural = plurals.item(i) as Element
            val items = plural.getElementsByTagName("item")
            for (j in 0 until items.length) {
                result["${plural.getAttribute("name")}[$j]"] = items.item(j).textContent
            }
        }
        return result
    }

    @Test
    fun `strings use the glossary word for each concept`() {
        val problems = replacedVariants.flatMap { (folder, variants) ->
            texts(folder).flatMap { (key, text) ->
                variants.filterKeys { it in text }
                    .map { (variant, word) -> "$folder/$key uses \"$variant\"; use \"$word\"" }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

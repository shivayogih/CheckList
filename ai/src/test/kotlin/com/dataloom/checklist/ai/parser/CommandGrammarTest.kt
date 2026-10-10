package com.dataloom.checklist.ai.parser

import com.dataloom.checklist.ai.fixtures.bundledPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Table tests of the pure grammar: intent, item names as typed, quantities and units, in all seven
 * languages, native script and transliteration. Item lookup is covered by [OfflineCommandParserTest].
 */
class CommandGrammarTest {

    private val grammar = CommandGrammar(bundledPacks)

    /** "name|qty|UNIT" per item; qty and unit may be empty. */
    private fun check(locale: String, text: String, intent: CommandIntent, vararg items: String) {
        val parsed = grammar.parse(text, locale)
        val actual = parsed.items.map { "${it.name}|${it.quantity?.toPlainString().orEmpty()}|${it.unit?.value.orEmpty()}" }
        assertEquals("[$locale] '$text' intent", intent, parsed.intent)
        assertEquals("[$locale] '$text' items", items.toList(), actual)
    }

    @Test
    fun `English commands`() {
        val table = listOf(
            Triple("2 kg rice, 1 dozen eggs and milk", CommandIntent.ADD, listOf("rice|2|KG", "eggs|1|DOZEN", "milk||")),
            Triple("add 500g dal", CommandIntent.ADD, listOf("dal|500|GRAM")),
            Triple("rice 5 kg", CommandIntent.ADD, listOf("rice|5|KG")),
            Triple("a dozen bananas", CommandIntent.ADD, listOf("bananas|1|DOZEN")),
            Triple("half kg sugar", CommandIntent.ADD, listOf("sugar|0.5|KG")),
            Triple("half a kg of sugar", CommandIntent.ADD, listOf("sugar|0.5|KG")),
            Triple("two litres of milk", CommandIntent.ADD, listOf("milk|2|LITRE")),
            Triple("1½ kg onions", CommandIntent.ADD, listOf("onions|1.5|KG")),
            Triple("1/2 litre oil", CommandIntent.ADD, listOf("oil|0.5|LITRE")),
            Triple("2,5 kg potato", CommandIntent.ADD, listOf("potato|2.5|KG")),
            Triple("3 soap", CommandIntent.ADD, listOf("soap|3|")),
            Triple("dozen eggs", CommandIntent.ADD, listOf("eggs|1|DOZEN")),
            Triple("2 kg rice 1 kg dal", CommandIntent.ADD, listOf("rice|2|KG", "dal|1|KG")),
            Triple("rice 2 kg dal 1 kg", CommandIntent.ADD, listOf("rice|2|KG", "dal|1|KG")),
            Triple("7up 2 bottles", CommandIntent.ADD, listOf("7up|2|BOTTLE")),
            Triple("please add some salt to the list", CommandIntent.ADD, listOf("salt||")),
            Triple("mark milk done", CommandIntent.COMPLETE, listOf("milk||")),
            Triple("mark rice and dal as done", CommandIntent.COMPLETE, listOf("rice||", "dal||")),
            Triple("tick off bread", CommandIntent.COMPLETE, listOf("bread||")),
            Triple("mark milk as not done", CommandIntent.UNCOMPLETE, listOf("milk||")),
            Triple("untick milk", CommandIntent.UNCOMPLETE, listOf("milk||")),
            Triple("remove onion from the list", CommandIntent.REMOVE, listOf("onion||")),
            Triple("delete ghee", CommandIntent.REMOVE, listOf("ghee||")),
            Triple("change rice to 3 kg", CommandIntent.UPDATE, listOf("rice|3|KG")),
        )
        table.forEach { (text, intent, items) -> check("en", text, intent, *items.toTypedArray()) }
    }

    @Test
    fun `Kannada commands`() {
        check("kn", "5 ಕೆಜಿ ಅಕ್ಕಿ", CommandIntent.ADD, "ಅಕ್ಕಿ|5|KG")
        check("kn", "೫ ಕೆಜಿ ಅಕ್ಕಿ", CommandIntent.ADD, "ಅಕ್ಕಿ|5|KG")
        check("kn", "ಅಕ್ಕಿ 5 ಕೆಜಿ ಸೇರಿಸಿ", CommandIntent.ADD, "ಅಕ್ಕಿ|5|KG")
        check("kn", "ಎರಡು ಲೀಟರ್ ಹಾಲು ಮತ್ತು ಅರ್ಧ ಕೆಜಿ ಸಕ್ಕರೆ ಬೇಕು", CommandIntent.ADD, "ಹಾಲು|2|LITRE", "ಸಕ್ಕರೆ|0.5|KG")
        check("kn", "2ಕೆಜಿ ಅಕ್ಕಿ, ಒಂದು ಡಜನ್ ಬಾಳೆಹಣ್ಣು", CommandIntent.ADD, "ಅಕ್ಕಿ|2|KG", "ಬಾಳೆಹಣ್ಣು|1|DOZEN")
        check("kn", "2 kilo akki mattu halu", CommandIntent.ADD, "akki|2|KG", "halu||")
        check("kn", "eradu kg sakkare serisi", CommandIntent.ADD, "sakkare|2|KG")
        check("kn", "ಹಾಲು ಆಯ್ತು", CommandIntent.COMPLETE, "ಹಾಲು||")
        check("kn", "haalu aaytu", CommandIntent.COMPLETE, "haalu||")
        check("kn", "ಹಾಲು ಆಗಿಲ್ಲ", CommandIntent.UNCOMPLETE, "ಹಾಲು||")
        check("kn", "ಈರುಳ್ಳಿ ಬೇಡ", CommandIntent.REMOVE, "ಈರುಳ್ಳಿ||")
        check("kn", "ಅಕ್ಕಿ 3 ಕೆಜಿ ಮಾಡಿ", CommandIntent.UPDATE, "ಅಕ್ಕಿ|3|KG")
    }

    @Test
    fun `Hindi commands`() {
        check("hi", "5 किलो चावल", CommandIntent.ADD, "चावल|5|KG")
        check("hi", "५ किलो चावल", CommandIntent.ADD, "चावल|5|KG")
        check("hi", "दो लीटर दूध और एक दर्जन केले चाहिए", CommandIntent.ADD, "दूध|2|LITRE", "केले|1|DOZEN")
        check("hi", "चावल 5 किलो जोड़ो", CommandIntent.ADD, "चावल|5|KG")
        check("hi", "आधा किलो चीनी", CommandIntent.ADD, "चीनी|0.5|KG")
        check("hi", "डेढ़ किलो आलू", CommandIntent.ADD, "आलू|1.5|KG")
        check("hi", "doodh 2 litre aur chawal 5 kilo", CommandIntent.ADD, "doodh|2|LITRE", "chawal|5|KG")
        check("hi", "do kilo chawal add karo", CommandIntent.ADD, "chawal|2|KG")
        check("hi", "दूध हो गया", CommandIntent.COMPLETE, "दूध||")
        check("hi", "doodh le liya", CommandIntent.COMPLETE, "doodh||")
        check("hi", "दूध नहीं हुआ", CommandIntent.UNCOMPLETE, "दूध||")
        check("hi", "प्याज़ नहीं चाहिए", CommandIntent.REMOVE, "प्याज़||")
        check("hi", "चावल 3 किलो कर दो", CommandIntent.UPDATE, "चावल|3|KG")
    }

    @Test
    fun `Tamil commands`() {
        check("ta", "அரிசி 5 கிலோ", CommandIntent.ADD, "அரிசி|5|KG")
        check("ta", "இரண்டு லிட்டர் பால் மற்றும் அரை கிலோ சர்க்கரை வேண்டும்", CommandIntent.ADD, "பால்|2|LITRE", "சர்க்கரை|0.5|KG")
        check("ta", "அரிசியும் பாலும் வேண்டும்", CommandIntent.ADD, "அரிசி||", "பாலும்||")
        check("ta", "arisi 2 kilo matrum paal", CommandIntent.ADD, "arisi|2|KG", "paal||")
        check("ta", "பால் வாங்கியாச்சு", CommandIntent.COMPLETE, "பால்||")
        check("ta", "பால் வாங்கவில்லை", CommandIntent.UNCOMPLETE, "பால்||")
        check("ta", "வெங்காயம் வேண்டாம்", CommandIntent.REMOVE, "வெங்காயம்||")
        check("ta", "அரிசி 3 கிலோ ஆக்கு", CommandIntent.UPDATE, "அரிசி|3|KG")
    }

    @Test
    fun `Telugu commands`() {
        check("te", "5 కిలో బియ్యం", CommandIntent.ADD, "బియ్యం|5|KG")
        check("te", "రెండు లీటర్ పాలు మరియు అర కిలో చక్కెర కావాలి", CommandIntent.ADD, "పాలు|2|LITRE", "చక్కెర|0.5|KG")
        check("te", "biyyam 5 kilo mariyu paalu", CommandIntent.ADD, "biyyam|5|KG", "paalu||")
        check("te", "పాలు అయింది", CommandIntent.COMPLETE, "పాలు||")
        check("te", "పాలు అవలేదు", CommandIntent.UNCOMPLETE, "పాలు||")
        check("te", "ఉల్లిపాయ వద్దు", CommandIntent.REMOVE, "ఉల్లిపాయ||")
        check("te", "బియ్యం 3 కిలో చేయి", CommandIntent.UPDATE, "బియ్యం|3|KG")
    }

    @Test
    fun `Marathi commands`() {
        check("mr", "5 किलो तांदूळ", CommandIntent.ADD, "तांदूळ|5|KG")
        check("mr", "दोन लिटर दूध आणि अर्धा किलो साखर हवी", CommandIntent.ADD, "दूध|2|LITRE", "साखर|0.5|KG")
        check("mr", "दीड किलो बटाटा", CommandIntent.ADD, "बटाटा|1.5|KG")
        check("mr", "tandul 5 kilo ani dudh", CommandIntent.ADD, "tandul|5|KG", "dudh||")
        check("mr", "दूध झाले", CommandIntent.COMPLETE, "दूध||")
        check("mr", "दूध झाले नाही", CommandIntent.UNCOMPLETE, "दूध||")
        check("mr", "कांदा नको", CommandIntent.REMOVE, "कांदा||")
        check("mr", "तांदूळ 3 किलो करा", CommandIntent.UPDATE, "तांदूळ|3|KG")
    }

    @Test
    fun `Malayalam commands`() {
        check("ml", "5 കിലോ അരി", CommandIntent.ADD, "അരി|5|KG")
        check("ml", "രണ്ട് ലിറ്റർ പാൽ പിന്നെ അര കിലോ പഞ്ചസാര വേണം", CommandIntent.ADD, "പാൽ|2|LITRE", "പഞ്ചസാര|0.5|KG")
        check("ml", "അരിയും പാലും വേണം", CommandIntent.ADD, "അരി||", "പാലും||")
        check("ml", "ari 2 kilo pinne paal", CommandIntent.ADD, "ari|2|KG", "paal||")
        check("ml", "പാൽ വാങ്ങി", CommandIntent.COMPLETE, "പാൽ||")
        check("ml", "പാൽ വാങ്ങിയില്ല", CommandIntent.UNCOMPLETE, "പാൽ||")
        check("ml", "ഉള്ളി വേണ്ട", CommandIntent.REMOVE, "ഉള്ളി||")
        check("ml", "അരി 3 കിലോ ആക്കുക", CommandIntent.UPDATE, "അരി|3|KG")
    }

    @Test
    fun `create checklist in every language`() {
        val table = listOf(
            Triple("en", "create a checklist called Diwali shopping", "Diwali shopping"),
            Triple("en", "new list Goa trip", "Goa trip"),
            Triple("kn", "ದೀಪಾವಳಿ ಪಟ್ಟಿ ಮಾಡಿ", "ದೀಪಾವಳಿ"),
            Triple("kn", "ಹೊಸ ಪಟ್ಟಿ ಪ್ರವಾಸ", "ಪ್ರವಾಸ"),
            Triple("hi", "दिवाली की सूची बनाओ", "दिवाली"),
            Triple("hi", "नई सूची गोवा यात्रा", "गोवा यात्रा"),
            Triple("ta", "தீபாவளி பட்டியல் உருவாக்கு", "தீபாவளி"),
            Triple("te", "దీపావళి జాబితా తయారు చేయి", "దీపావళి"),
            Triple("mr", "दिवाळी यादी तयार करा", "दिवाळी"),
            Triple("ml", "ഓണം ലിസ്റ്റ് ഉണ്ടാക്കുക", "ഓണം"),
        )
        table.forEach { (locale, text, title) ->
            val parsed = grammar.parse(text, locale)
            assertEquals("[$locale] $text", CommandIntent.CREATE_CHECKLIST, parsed.intent)
            assertEquals("[$locale] $text", title, parsed.title)
        }
    }

    @Test
    fun `a create command without a name has no title`() {
        assertNull(grammar.parse("create a new checklist", "en").title)
    }

    @Test
    fun `phrases without a name are reported, not guessed`() {
        val parsed = grammar.parse("2 kg, milk", "en")
        assertEquals(listOf("milk"), parsed.items.map { it.name })
        assertEquals(listOf("2 kg"), parsed.unparsed)
    }

    @Test
    fun `short unit spellings never eat a word of a name`() {
        check("en", "m and m chocolates", CommandIntent.ADD, "m||", "m chocolates||")
        check("en", "g pay card", CommandIntent.ADD, "g pay card||")
    }

    @Test
    fun `quantity edge cases are passed on exactly for validation to judge`() {
        check("en", "0 kg rice", CommandIntent.ADD, "rice|0|KG")
        check("en", "100000 kg rice", CommandIntent.ADD, "rice|100000|KG")
        check("en", "2.1234 kg rice", CommandIntent.ADD, "rice|2.1234|KG")
        check("en", "2.5 dozen eggs", CommandIntent.ADD, "eggs|2.5|DOZEN")
        check("en", "1/3 kg rice", CommandIntent.ADD, "rice|0.333|KG")
    }

    @Test
    fun `languages follow the locale, the script and Latin transliteration`() {
        assertEquals(listOf("kn", "en"), grammar.languagesFor("5 ಕೆಜಿ ಅಕ್ಕಿ", "kn-IN"))
        assertEquals(listOf("en", "hi", "mr"), grammar.languagesFor("5 किलो चावल", "en"))
        val latin = grammar.languagesFor("2 kilo akki", "ta")
        assertEquals("ta", latin.first())
        assertTrue(latin.containsAll(listOf("en", "kn", "hi", "te", "mr", "ml")))
    }
}

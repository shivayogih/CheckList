package com.dataloom.checklist.ai.parser

import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.model.UnresolvedFragment
import com.dataloom.checklist.ai.model.UnresolvedReason
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.domain.model.ChecklistId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden utterances: text in each language becomes the expected tool calls, with item names resolved
 * against the real seed catalog (canonical keys, so the database never depends on the input language).
 *
 * The open checklist is [AiTestHarness.weeklyList]: s1 Groceries [i1 Rice 1 kg, i2 Milk (ticked),
 * i3 Ghee (typed)], s2 Vegetables [i4 Onion].
 */
class OfflineCommandParserTest {

    private val harness = AiTestHarness()

    /** Compact form of a call: "add rice 2 KG", "add ?eggs 1 DOZEN" (no catalog match), "completeItem i2". */
    private fun ToolCall.describe(): String {
        val args = arguments
        return when (name) {
            "addChecklistItem" -> listOfNotNull(
                "add",
                (args["canonicalKey"] as String?) ?: "?${args["name"]}",
                args["quantity"] as String?,
                args["unit"] as String?,
            ).joinToString(" ")
            "updateItemQuantity" -> listOfNotNull("update", args["itemRef"], args["quantity"], args["unit"]).joinToString(" ")
            "createChecklist" -> "create ${args["title"]}"
            else -> "$name ${args.values.joinToString(" ")}"
        }
    }

    private suspend fun interpret(text: String, locale: String, checklist: ChecklistId?): AiResult<ActionPlan> {
        val snapshot = harness.snapshot(checklist, locale)
        return harness.parser.interpret(Utterance(text, locale), snapshot.context)
    }

    private suspend fun plan(text: String, locale: String, checklist: ChecklistId?): ActionPlan {
        val result = interpret(text, locale, checklist)
        assertTrue("[$locale] '$text' gave $result", result is AiResult.Success)
        return (result as AiResult.Success).value
    }

    private suspend fun golden(locale: String, checklist: ChecklistId, vararg cases: Pair<String, List<String>>) {
        for ((text, expected) in cases) {
            val plan = plan(text, locale, checklist)
            assertEquals("[$locale] '$text'", expected, plan.calls.map { it.describe() })
            assertEquals(PlanSource.OFFLINE_PARSER, plan.source)
        }
    }

    @Test
    fun `English golden utterances`() = runTest {
        val list = harness.weeklyList()
        golden(
            "en",
            list,
            "2 kg rice, 1 dozen eggs and milk" to listOf("add rice 2 KG", "add ?eggs 1 DOZEN", "add milk"),
            "5 kg rice" to listOf("add rice 5 KG"),
            "1½ kg onions" to listOf("add onion 1.5 KG"),
            "two litres of milk and a dozen bananas" to listOf("add milk 2 LITRE", "add banana 1 DOZEN"),
            "mark onion done" to listOf("completeItem i4"),
            "mark milk as not done" to listOf("uncompleteItem i2"),
            "remove ghee" to listOf("deleteItem i3"),
            "remove vegetables" to listOf("removeCategory s2"),
            "change rice to 3 kg" to listOf("update i1 3 KG"),
            "change rice to 3" to listOf("update i1 3"),
        )
    }

    @Test
    fun `Kannada golden utterances`() = runTest {
        val list = harness.weeklyList()
        golden(
            "kn",
            list,
            "5 ಕೆಜಿ ಅಕ್ಕಿ" to listOf("add rice 5 KG"),
            "ಅಕ್ಕಿಯನ್ನು ಸೇರಿಸಿ" to listOf("add rice"),
            "ಎರಡು ಲೀಟರ್ ಹಾಲು ಮತ್ತು ಅರ್ಧ ಕೆಜಿ ಸಕ್ಕರೆ ಬೇಕು" to listOf("add milk 2 LITRE", "add sugar 0.5 KG"),
            "2 kilo akki mattu halu" to listOf("add rice 2 KG", "add milk"),
            "ಹಾಲು ಆಗಿಲ್ಲ" to listOf("uncompleteItem i2"),
            "ಈರುಳ್ಳಿ ಆಯ್ತು" to listOf("completeItem i4"),
            "ಈರುಳ್ಳಿ ಬೇಡ" to listOf("deleteItem i4"),
            "ಅಕ್ಕಿ 3 ಕೆಜಿ ಮಾಡಿ" to listOf("update i1 3 KG"),
        )
    }

    @Test
    fun `Hindi golden utterances`() = runTest {
        val list = harness.weeklyList()
        golden(
            "hi",
            list,
            "5 किलो चावल" to listOf("add rice 5 KG"),
            "दो लीटर दूध और एक दर्जन केले चाहिए" to listOf("add milk 2 LITRE", "add banana 1 DOZEN"),
            "chawal 5 kilo aur doodh" to listOf("add rice 5 KG", "add milk"),
            "प्याज़ हो गया" to listOf("completeItem i4"),
            "प्याज नहीं चाहिए" to listOf("deleteItem i4"),
            "चावल 3 किलो कर दो" to listOf("update i1 3 KG"),
        )
    }

    @Test
    fun `Tamil golden utterances`() = runTest {
        val list = harness.weeklyList()
        golden(
            "ta",
            list,
            "அரிசி 5 கிலோ" to listOf("add rice 5 KG"),
            "அரிசியும் பாலும் வேண்டும்" to listOf("add rice", "add milk"),
            "arisi 2 kilo matrum paal" to listOf("add rice 2 KG", "add milk"),
            "வெங்காயம் வாங்கியாச்சு" to listOf("completeItem i4"),
        )
    }

    @Test
    fun `Telugu golden utterances`() = runTest {
        val list = harness.weeklyList()
        golden(
            "te",
            list,
            "5 కిలో బియ్యం" to listOf("add rice 5 KG"),
            "రెండు లీటర్ పాలు మరియు అర కిలో చక్కెర కావాలి" to listOf("add milk 2 LITRE", "add sugar 0.5 KG"),
            "biyyam 5 kilo mariyu paalu" to listOf("add rice 5 KG", "add milk"),
            "ఉల్లిపాయ వద్దు" to listOf("deleteItem i4"),
        )
    }

    @Test
    fun `Marathi golden utterances`() = runTest {
        val list = harness.weeklyList()
        golden(
            "mr",
            list,
            "5 किलो तांदूळ" to listOf("add rice 5 KG"),
            "दोन लिटर दूध आणि अर्धा किलो साखर हवी" to listOf("add milk 2 LITRE", "add sugar 0.5 KG"),
            "tandul 5 kilo ani dudh" to listOf("add rice 5 KG", "add milk"),
            "कांदा झाला" to listOf("completeItem i4"),
        )
    }

    @Test
    fun `Malayalam golden utterances`() = runTest {
        val list = harness.weeklyList()
        golden(
            "ml",
            list,
            "5 കിലോ അരി" to listOf("add rice 5 KG"),
            "അരിയും പാലും വേണം" to listOf("add rice", "add milk"),
            "ari 2 kilo pinne paal" to listOf("add rice 2 KG", "add milk"),
            "ഉള്ളി വേണ്ട" to listOf("deleteItem i4"),
        )
    }

    @Test
    fun `any script works whatever the app language`() = runTest {
        val list = harness.weeklyList()
        golden("en", list, "5 ಕೆಜಿ ಅಕ್ಕಿ" to listOf("add rice 5 KG"), "2 kilo akki" to listOf("add rice 2 KG"))
        golden("kn", list, "5 किलो चावल" to listOf("add rice 5 KG"), "2 kg rice" to listOf("add rice 2 KG"))
    }

    @Test
    fun `catalog items keep the user's language for the name`() = runTest {
        val list = harness.weeklyList()
        val call = plan("5 kg rice", "kn", list).calls.single()
        assertEquals("ಅಕ್ಕಿ", call.arguments["name"])
    }

    @Test
    fun `prefix matches of a different word are not taken as the item`() = runTest {
        val list = harness.weeklyList()
        golden(
            "en",
            list,
            "pa" to listOf("add ?pa"),
            // "eggplant" (brinjal) starts with "egg", but eggs are not brinjal.
            "1 egg" to listOf("add ?egg 1"),
            "6 eggs" to listOf("add ?eggs 6"),
            // Whole aliases still work.
            "2 kg aloo" to listOf("add potato 2 KG"),
            "1 litre oil" to listOf("add cooking_oil 1 LITRE"),
        )
        golden("ml", list, "ari 2 kilo" to listOf("add rice 2 KG"))
    }

    @Test
    fun `create checklist needs no open checklist`() = runTest {
        assertEquals(listOf("create Diwali shopping"), plan("create a checklist called Diwali shopping", "en", null).calls.map { it.describe() })
        assertEquals(listOf("create ದೀಪಾವಳಿ"), plan("ದೀಪಾವಳಿ ಪಟ್ಟಿ ಮಾಡಿ", "kn", null).calls.map { it.describe() })
    }

    @Test
    fun `item commands need an open checklist`() = runTest {
        assertEquals(AiResult.Rejected(RejectionReason.NEEDS_CHECKLIST), interpret("2 kg rice", "en", null))
    }

    @Test
    fun `empty, too long and meaningless input are rejected`() = runTest {
        val list = harness.weeklyList()
        assertEquals(AiResult.Rejected(RejectionReason.EMPTY_INPUT), interpret("   ", "en", list))
        assertEquals(AiResult.Rejected(RejectionReason.TOO_LONG), interpret("rice ".repeat(120), "en", list))
        assertEquals(AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD), interpret("2 kg", "en", list))
        assertEquals(AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD), interpret("create a new checklist", "en", null))
        assertEquals(AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD), interpret("mark saffron done", "en", list))
    }

    @Test
    fun `partly understood commands report what was skipped`() = runTest {
        val list = harness.weeklyList()
        val plan = plan("mark onion and saffron done", "en", list)
        assertEquals(listOf("completeItem i4"), plan.calls.map { it.describe() })
        assertEquals(listOf(UnresolvedFragment("saffron", UnresolvedReason.ITEM_NOT_IN_CHECKLIST)), plan.unresolved)

        val update = plan("change rice, onion to 2 kg", "en", list)
        assertEquals(listOf("update i4 2 KG"), update.calls.map { it.describe() })
        assertEquals(listOf(UnresolvedFragment("rice", UnresolvedReason.MISSING_QUANTITY)), update.unresolved)

        val add = plan("2 kg, milk", "en", list)
        assertEquals(listOf(UnresolvedFragment("2 kg", UnresolvedReason.NOT_UNDERSTOOD)), add.unresolved)
    }

    @Test
    fun `the offline parser cannot generate or suggest, but can count`() = runTest {
        val snapshot = harness.snapshot(harness.weeklyList())
        assertTrue(harness.parser.suggestCategories("Trip", "en") is AiResult.Unavailable)
        val summary = (harness.parser.summarize(snapshot.context) as AiResult.Success).value
        assertEquals(4, summary.totalItems)
        assertEquals(1, summary.completedItems)
        assertEquals(listOf("Rice", "Ghee", "Onion"), summary.pendingItems)
    }

    @Test
    fun `parsing writes nothing`() = runTest {
        val list = harness.weeklyList()
        val before = harness.checklists.writes
        plan("2 kg rice, 1 dozen eggs and milk", "en", list)
        plan("remove vegetables", "en", list)
        assertEquals(before, harness.checklists.writes)
    }
}

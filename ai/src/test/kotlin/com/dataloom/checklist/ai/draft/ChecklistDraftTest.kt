package com.dataloom.checklist.ai.draft

import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.ai.fixtures.bundledPacks
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.ObserveMasterItemsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Building a draft reads only; saving it is all or nothing. Real seed catalog, in-memory checklists. */
class ChecklistDraftTest {

    private val harness = AiTestHarness()
    private val builder = ChecklistDraftBuilder(
        DraftMatcher(DraftTemplates.bundled()),
        harness.lookup,
        ObserveMasterItemsUseCase(harness.catalog),
        bundledPacks,
    )
    private val saver = ChecklistDraftSaver(
        CreateChecklistUseCase(harness.checklists),
        AddMasterItemsToSectionUseCase(harness.checklists, harness.catalog),
        AddCustomItemUseCase(harness.checklists, harness.catalog),
        harness.observeDetail,
        DeleteChecklistUseCase(harness.checklists),
    )

    private suspend fun draft(prompt: String, locale: String = "en"): ChecklistDraft =
        (builder.build(prompt, locale) as AiResult.Success).value

    @Test
    fun `a trip request becomes a draft from the catalog and writes nothing`() = runTest {
        val draft = draft("Create checklist for my next Goa trip")

        assertEquals("Goa trip", draft.title)
        assertEquals(
            listOf("travel", "clothing", "documents", "electronics", "toiletries"),
            draft.sections.map { it.category.canonicalKey },
        )
        val travel = draft.sections.first().items
        assertTrue(travel.all { it.masterItem != null })
        assertTrue(travel.any { it.masterItem?.canonicalKey == "tickets" })
        assertEquals(0, harness.checklists.writes)
    }

    @Test
    fun `names come in the app language, typed template items too`() = runTest {
        val draft = draft("ವಿದೇಶ ಪ್ರವಾಸ", "kn")

        val travel = draft.sections.first { it.category.canonicalKey == "travel" }
        assertTrue(travel.items.any { it.masterItem == null && it.name == "ವೀಸಾ" })
        val tickets = travel.items.first { it.masterItem?.canonicalKey == "tickets" }
        assertEquals(harness.catalog.masterItem("tickets", "kn").displayName, tickets.name)
        // Trip and abroad share the travel group: items appear once.
        assertEquals(travel.items.size, travel.items.map { it.key }.toSet().size)
    }

    @Test
    fun `the property request gets the document group with typed deeds`() = runTest {
        val draft = draft("Prepare the document checklist for my new property buy")

        val documents = draft.sections.single()
        assertEquals("documents", documents.category.canonicalKey)
        assertTrue(documents.items.any { it.name == "Encumbrance certificate (EC)" })
        assertTrue(documents.items.any { it.masterItem?.canonicalKey == "pan_card" })
    }

    @Test
    fun `a category name alone gives that category's items`() = runTest {
        val draft = draft("medicines")

        assertEquals(listOf("medicines"), draft.sections.map { it.category.canonicalKey })
        assertTrue(draft.itemCount in 1..ChecklistDraftBuilder.ITEMS_PER_CATEGORY)
    }

    @Test
    fun `unknown, empty and too long requests are refused honestly`() = runTest {
        assertEquals(AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD), builder.build("asdf qwerty", "en"))
        assertEquals(AiResult.Rejected(RejectionReason.EMPTY_INPUT), builder.build("   ", "en"))
        assertEquals(AiResult.Rejected(RejectionReason.TOO_LONG), builder.build("trip ".repeat(200), "en"))
    }

    @Test
    fun `an approved draft is saved with every item in its group`() = runTest {
        val draft = draft("Prepare the document checklist for my new property buy")

        val result = saver.save(draft, "en")

        val id = (result as DomainResult.Success).value
        val detail = harness.checklists.detailNow(id)!!
        assertEquals(draft.title, detail.checklist.title)
        assertEquals(draft.itemCount, detail.totalItems)
        assertEquals(
            draft.sections.single().items.map { it.name },
            detail.sections.single().items.map { it.displayName },
        )
    }

    @Test
    fun `a refused item undoes the whole save`() = runTest {
        val good = draft("Goa trip")
        val bad = good.copy(
            sections = good.sections.mapIndexed { index, section ->
                if (index != good.sections.lastIndex) {
                    section
                } else {
                    section.copy(items = section.items + DraftItem("x".repeat(81)))
                }
            },
        )

        val result = saver.save(bad, "en")

        assertTrue(result is DomainResult.Failure)
        assertEquals(0, harness.checklists.checklistCount())
    }
}

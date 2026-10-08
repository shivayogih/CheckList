package com.dataloom.checklist.ai.model

import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.NewChecklistItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Section 11.4: only checklist text leaves the device; never notes, never database IDs. */
class ContextSnapshotTest {

    private val harness = AiTestHarness()

    @Test
    fun `the context holds names, keys, amounts and short refs only`() = runTest {
        val list = harness.weeklyList()
        val context = harness.snapshot(list, "en").context
        assertEquals("Weekly shopping", context.title)
        assertEquals(listOf("s1", "s2"), context.sections.map { it.ref })
        assertEquals(listOf("groceries", "vegetables"), context.sections.map { it.categoryKey })
        assertEquals(listOf("i1", "i2", "i3", "i4"), context.items.map { it.ref })
        assertEquals(listOf("rice", "milk", null, "onion"), context.items.map { it.canonicalKey })
        assertEquals(listOf(false, true, false, false), context.items.map { it.isCompleted })
        assertEquals(BuiltInUnits.all.map { it.code }, context.allowedUnits)

        val text = context.toString()
        listOf("private note", "cl-", "sec-", "item-", "mi-", "cat-").forEach { secret ->
            assertFalse("'$secret' leaked into the context", secret in text)
        }
    }

    @Test
    fun `user item keys hide their database id`() = runTest {
        val list = harness.weeklyList()
        val section = harness.checklists.detailNow(list)!!.sections[0]
        val customId = harness.catalog.createMasterItem(section.category.id, "Ragi", "en", null)
        val custom = harness.catalog.getMasterItem(customId, "en")!!
        harness.checklists.addItems(section.id, listOf(NewChecklistItem(custom.id, custom.canonicalKey, "Ragi", "en", null, null, null)))
        val ragi = harness.snapshot(list).context.items.first { it.name == "Ragi" }
        assertNull(ragi.canonicalKey)
    }

    @Test
    fun `the focused section is passed as a ref`() = runTest {
        val list = harness.weeklyList()
        val vegetables = harness.checklists.detailNow(list)!!.sections[1].id
        assertEquals("s2", harness.snapshot(list, focused = vegetables).context.focusedSectionRef)
    }

    @Test
    fun `Home has no checklist`() = runTest {
        val context = harness.snapshot(null).context
        assertFalse(context.hasChecklist)
        assertEquals(emptyList<SectionContext>(), context.sections)
    }
}

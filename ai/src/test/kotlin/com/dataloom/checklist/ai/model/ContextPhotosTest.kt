package com.dataloom.checklist.ai.model

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.Checklist
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.model.SectionId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Photos stay on the phone (docs/security.md, CL-215): an item with photos gives the AI exactly the
 * context it gave before, and no code in the AI layer can reach photo data.
 */
class ContextPhotosTest {

    private val itemId = ChecklistItemId("item-1")
    private val photo = ItemPhoto(
        id = PhotoId("photo-secret-id"),
        itemId = itemId,
        fileName = "0b1c2d3e-secret-file.jpg",
        width = 1200,
        height = 900,
        byteSize = 123_456,
        position = 1000,
        caption = "Front door key under the mat",
        createdAt = 1L,
    )

    private fun detail(photos: List<ItemPhoto>): ChecklistDetail {
        val checklistId = ChecklistId("cl-1")
        val sectionId = SectionId("sec-1")
        val category = Category(
            CategoryId("cat-1"), "groceries", null, "Groceries", "cart", isCustom = false, isHidden = false,
        )
        val item = ChecklistItem(
            id = itemId,
            sectionId = sectionId,
            masterItemId = null,
            canonicalKey = "rice",
            displayName = "Rice",
            displayNameLocale = "en",
            quantity = null,
            unit = null,
            notes = null,
            isCompleted = false,
            position = 1000,
            createdAt = 1L,
            updatedAt = 1L,
            photos = photos,
        )
        return ChecklistDetail(
            Checklist(checklistId, "Weekly", null, 1L, 1L, isArchived = false),
            listOf(ChecklistSection(sectionId, checklistId, category, 0, listOf(item))),
        )
    }

    @Test
    fun `an item with photos gives the same context as one without`() {
        val without = ContextSnapshot.of(detail(emptyList()), "en", emptyList()).context
        val with = ContextSnapshot.of(detail(listOf(photo)), "en", emptyList()).context
        assertEquals(without, with)
    }

    @Test
    fun `no photo file name, id or caption appears in the context`() {
        val text = ContextSnapshot.of(detail(listOf(photo)), "en", emptyList()).context.toString()
        listOf(photo.fileName, photo.id.value, photo.caption.orEmpty(), "photo", "caption").forEach {
            assertFalse("'$it' leaked into the AI context", text.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `the context types have no photo property`() {
        listOf(ChecklistContext::class, SectionContext::class, ItemContext::class).forEach { type ->
            val names = type.java.declaredFields.map { it.name }
            assertTrue("$type has $names", names.none { it.contains("photo", ignoreCase = true) })
        }
    }

    @Test
    fun `the AI layer's sources never mention photos`() {
        // Gradle runs unit tests with the module directory (ai/) as the working directory.
        val sources = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("No AI sources found from ${File(".").absolutePath}", sources.isNotEmpty())
        val offenders = sources.filter { it.readText().contains("photo", ignoreCase = true) }.map { it.name }
        assertEquals("The AI layer must not touch photos", emptyList<String>(), offenders)
    }
}

package com.dataloom.checklist.domain.repository

import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistItemUpdate
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.SectionId
import kotlinx.coroutines.flow.Flow

/**
 * Checklists, their sections and items. Implemented by :data on Room (the single source of truth).
 * Every multi-row write is one transaction. Inputs are already validated by use cases.
 * [locale] is the BCP 47 language used to resolve category display names.
 *
 * Writes that target a single existing row (setArchived, deleteChecklist, removeSection,
 * moveSection, updateItem, setItemCompleted, deleteItem, moveItem) are no-ops when the ID does not
 * exist, because the row may have been deleted concurrently (another screen, import, AI).
 */
interface ChecklistRepository {

    fun observeChecklists(query: ChecklistQuery): Flow<List<ChecklistSummary>>

    /** Emits null once the checklist is deleted. */
    fun observeChecklist(id: ChecklistId, locale: String): Flow<ChecklistDetail?>

    suspend fun createChecklist(title: String, description: String?, categoryIds: List<CategoryId>): ChecklistId

    suspend fun updateChecklist(id: ChecklistId, title: String, description: String?)

    suspend fun setArchived(id: ChecklistId, archived: Boolean)

    suspend fun deleteChecklist(id: ChecklistId)

    /** Deep copy with new IDs; all items un-ticked. */
    suspend fun duplicateChecklist(id: ChecklistId, newTitle: String): ChecklistId

    /** Adds categories not already present, appended after existing sections. Returns new section IDs. */
    suspend fun addSections(checklistId: ChecklistId, categoryIds: List<CategoryId>): List<SectionId>

    /** Removes the section and its items. */
    suspend fun removeSection(sectionId: SectionId)

    suspend fun moveSection(sectionId: SectionId, toIndex: Int)

    /** Appends items to the section and increments use counts of their master items. */
    suspend fun addItems(sectionId: SectionId, items: List<NewChecklistItem>): List<ChecklistItemId>

    suspend fun updateItem(itemId: ChecklistItemId, update: ChecklistItemUpdate)

    suspend fun setItemCompleted(itemId: ChecklistItemId, completed: Boolean)

    suspend fun deleteItem(itemId: ChecklistItemId)

    suspend fun moveItem(itemId: ChecklistItemId, toIndex: Int)

    /** True when a checklist other than [excluding] already has this title (case-insensitive). */
    suspend fun titleExists(title: String, excluding: ChecklistId? = null): Boolean
}

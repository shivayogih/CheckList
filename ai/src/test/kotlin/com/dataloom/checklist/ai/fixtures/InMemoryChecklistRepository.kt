package com.dataloom.checklist.ai.fixtures

import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.Checklist
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistItemUpdate
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory checklists. Every write increments [writes], so tests can prove that proposing and
 * validating a plan writes nothing.
 */
class InMemoryChecklistRepository(private val catalog: CatalogRepository) : ChecklistRepository {

    private data class StoredSection(val id: SectionId, val categoryId: CategoryId, val items: List<ChecklistItem>)

    private data class Stored(val checklist: Checklist, val sections: List<StoredSection>)

    private val state = MutableStateFlow<List<Stored>>(emptyList())
    private var nextId = 1

    var writes = 0
        private set

    fun detailNow(id: ChecklistId): ChecklistDetail? = state.value.firstOrNull { it.checklist.id == id }?.let { stored ->
        // Category names are resolved in English for assertions.
        kotlinx.coroutines.runBlocking { stored.toDetail("en") }
    }

    fun checklistCount(): Int = state.value.size

    override fun observeChecklists(query: ChecklistQuery): Flow<List<ChecklistSummary>> = state.map { emptyList() }

    override fun observeChecklist(id: ChecklistId, locale: String): Flow<ChecklistDetail?> =
        state.map { list -> list.firstOrNull { it.checklist.id == id }?.toDetail(locale) }

    override suspend fun createChecklist(title: String, description: String?, categoryIds: List<CategoryId>): ChecklistId {
        writes++
        val id = ChecklistId("cl-${nextId++}")
        state.update { it + Stored(Checklist(id, title, description, 1L, 1L, false), categoryIds.distinct().map { c -> StoredSection(newSectionId(), c, emptyList()) }) }
        return id
    }

    override suspend fun updateChecklist(id: ChecklistId, title: String, description: String?) {
        writes++
    }

    override suspend fun setArchived(id: ChecklistId, archived: Boolean) {
        writes++
    }

    override suspend fun deleteChecklist(id: ChecklistId) {
        writes++
        state.update { list -> list.filterNot { it.checklist.id == id } }
    }

    override suspend fun duplicateChecklist(id: ChecklistId, newTitle: String): ChecklistId = error("not used")

    override suspend fun addSections(checklistId: ChecklistId, categoryIds: List<CategoryId>): List<SectionId> {
        writes++
        val stored = state.value.firstOrNull { it.checklist.id == checklistId } ?: return emptyList()
        val present = stored.sections.map { it.categoryId }.toSet()
        val added = categoryIds.distinct().filterNot { it in present }.map { StoredSection(newSectionId(), it, emptyList()) }
        state.update { list -> list.map { if (it.checklist.id == checklistId) it.copy(sections = it.sections + added) else it } }
        return added.map { it.id }
    }

    override suspend fun removeSection(sectionId: SectionId) {
        writes++
        editSections { sections -> sections.filterNot { it.id == sectionId } }
    }

    override suspend fun moveSection(sectionId: SectionId, toIndex: Int) {
        writes++
    }

    override suspend fun addItems(sectionId: SectionId, items: List<NewChecklistItem>): List<ChecklistItemId> {
        writes++
        val ids = items.map { ChecklistItemId("item-${nextId++}") }
        editSections { sections ->
            sections.map { section ->
                if (section.id != sectionId) return@map section
                section.copy(
                    items = section.items + items.zip(ids) { item, id ->
                        ChecklistItem(
                            id, sectionId, item.masterItemId, item.canonicalKey, item.displayName, item.displayNameLocale,
                            item.quantity, item.unit, item.notes, isCompleted = false, position = 0, createdAt = 1L, updatedAt = 1L,
                        )
                    },
                )
            }
        }
        return ids
    }

    override suspend fun updateItem(itemId: ChecklistItemId, update: ChecklistItemUpdate) {
        writes++
        editItem(itemId) { item ->
            item.copy(
                displayName = update.displayName ?: item.displayName,
                quantity = if (update.clearQuantity) null else update.quantity ?: item.quantity,
                unit = if (update.clearQuantity || update.clearUnit) null else update.unit ?: item.unit,
                notes = if (update.clearNotes) null else update.notes ?: item.notes,
            )
        }
    }

    override suspend fun setItemCompleted(itemId: ChecklistItemId, completed: Boolean) {
        writes++
        editItem(itemId) { it.copy(isCompleted = completed) }
    }

    override suspend fun deleteItem(itemId: ChecklistItemId) {
        writes++
        editSections { sections -> sections.map { s -> s.copy(items = s.items.filterNot { it.id == itemId }) } }
    }

    override suspend fun moveItem(itemId: ChecklistItemId, toIndex: Int) {
        writes++
    }

    override suspend fun titleExists(title: String, excluding: ChecklistId?): Boolean =
        state.value.any { it.checklist.id != excluding && it.checklist.title.equals(title, ignoreCase = true) }

    private fun newSectionId() = SectionId("sec-${nextId++}")

    private suspend fun Stored.toDetail(locale: String) = ChecklistDetail(
        checklist,
        sections.mapIndexed { index, s ->
            ChecklistSection(
                id = s.id,
                checklistId = checklist.id,
                category = catalog.getCategory(s.categoryId, locale)!!,
                displayOrder = index,
                items = s.items.mapIndexed { position, item -> item.copy(position = position) },
            )
        },
    )

    private fun editSections(edit: (List<StoredSection>) -> List<StoredSection>) {
        state.update { list -> list.map { it.copy(sections = edit(it.sections)) } }
    }

    private fun editItem(itemId: ChecklistItemId, edit: (ChecklistItem) -> ChecklistItem) = editSections { sections ->
        sections.map { s -> s.copy(items = s.items.map { if (it.id == itemId) edit(it) else it }) }
    }
}

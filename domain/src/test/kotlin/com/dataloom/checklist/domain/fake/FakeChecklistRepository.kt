package com.dataloom.checklist.domain.fake

import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.Checklist
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistItemUpdate
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory checklists for use case tests. Sections resolve their [com.dataloom.checklist.domain.model.Category]
 * from [catalog] when observed, like the Room relation would. Order is list order; display order and
 * positions are renumbered after every write.
 */
class FakeChecklistRepository(
    private val catalog: FakeCatalogRepository,
    private val now: () -> Long = { 1_000L },
) : ChecklistRepository {

    private data class StoredSection(val id: SectionId, val categoryId: CategoryId, val items: List<ChecklistItem>)

    private data class Stored(val checklist: Checklist, val sections: List<StoredSection>)

    private val state = MutableStateFlow<List<Stored>>(emptyList())
    private var nextId = 1

    /** Last arguments, for asserting what the use case passed through. */
    var lastQuery: ChecklistQuery? = null
    val updates = mutableListOf<Pair<ChecklistItemId, ChecklistItemUpdate>>()
    val addItemsCalls = mutableListOf<Pair<SectionId, List<NewChecklistItem>>>()

    init {
        catalog.checklistUsage = { categoryId -> state.value.count { s -> s.sections.any { it.categoryId == categoryId } } }
    }

    fun checklist(id: ChecklistId): Checklist? = state.value.firstOrNull { it.checklist.id == id }?.checklist

    fun checklistCount(): Int = state.value.size

    fun detail(id: ChecklistId): ChecklistDetail? = state.value.firstOrNull { it.checklist.id == id }?.toDetail()

    fun item(id: ChecklistItemId): ChecklistItem? =
        state.value.flatMap { it.sections }.flatMap { it.items }.firstOrNull { it.id == id }

    override fun observeChecklists(query: ChecklistQuery): Flow<List<ChecklistSummary>> {
        lastQuery = query
        return state.map { list ->
            list.filter {
                when (query.filter) {
                    ChecklistFilter.ACTIVE -> !it.checklist.isArchived
                    ChecklistFilter.ARCHIVED -> it.checklist.isArchived
                    ChecklistFilter.ALL -> true
                }
            }.filter { it.checklist.title.contains(query.search, ignoreCase = true) }
                .map { stored ->
                    val detail = stored.toDetail()
                    ChecklistSummary(stored.checklist, detail.totalItems, detail.completedItems)
                }
        }
    }

    override fun observeChecklist(id: ChecklistId, locale: String): Flow<ChecklistDetail?> =
        state.map { list -> list.firstOrNull { it.checklist.id == id }?.toDetail() }

    override suspend fun createChecklist(title: String, description: String?, categoryIds: List<CategoryId>): ChecklistId {
        val id = ChecklistId("cl-${nextId++}")
        val checklist = Checklist(id, title, description, now(), now(), isArchived = false)
        state.update { it + Stored(checklist, categoryIds.distinct().map { StoredSection(newSectionId(), it, emptyList()) }) }
        return id
    }

    override suspend fun updateChecklist(id: ChecklistId, title: String, description: String?) =
        editChecklist(id) { it.copy(checklist = it.checklist.copy(title = title, description = description, updatedAt = now())) }

    override suspend fun setArchived(id: ChecklistId, archived: Boolean) =
        editChecklist(id) { it.copy(checklist = it.checklist.copy(isArchived = archived)) }

    override suspend fun deleteChecklist(id: ChecklistId) {
        state.update { list -> list.filterNot { it.checklist.id == id } }
    }

    override suspend fun duplicateChecklist(id: ChecklistId, newTitle: String): ChecklistId {
        val source = state.value.first { it.checklist.id == id }
        val copyId = ChecklistId("cl-${nextId++}")
        val sections = source.sections.map { section ->
            val sectionId = newSectionId()
            StoredSection(
                sectionId,
                section.categoryId,
                section.items.map { it.copy(id = newItemId(), sectionId = sectionId, isCompleted = false) },
            )
        }
        state.update { it + Stored(source.checklist.copy(id = copyId, title = newTitle, isArchived = false), sections) }
        return copyId
    }

    override suspend fun addSections(checklistId: ChecklistId, categoryIds: List<CategoryId>): List<SectionId> {
        val stored = state.value.first { it.checklist.id == checklistId }
        val present = stored.sections.map { it.categoryId }.toSet()
        val added = categoryIds.distinct().filterNot { it in present }.map { StoredSection(newSectionId(), it, emptyList()) }
        editChecklist(checklistId) { it.copy(sections = it.sections + added) }
        return added.map { it.id }
    }

    override suspend fun removeSection(sectionId: SectionId) = editSections { sections -> sections.filterNot { it.id == sectionId } }

    override suspend fun moveSection(sectionId: SectionId, toIndex: Int) = editSections { sections ->
        val section = sections.firstOrNull { it.id == sectionId } ?: return@editSections sections
        val rest = sections - section
        rest.toMutableList().apply { add(toIndex.coerceAtMost(rest.size), section) }
    }

    override suspend fun addItems(sectionId: SectionId, items: List<NewChecklistItem>): List<ChecklistItemId> {
        addItemsCalls += sectionId to items
        val ids = items.map { newItemId() }
        editItems(sectionId) { existing ->
            existing + items.zip(ids) { item, id ->
                ChecklistItem(
                    id = id,
                    sectionId = sectionId,
                    masterItemId = item.masterItemId,
                    canonicalKey = item.canonicalKey,
                    displayName = item.displayName,
                    displayNameLocale = item.displayNameLocale,
                    quantity = item.quantity,
                    unit = item.unit,
                    notes = item.notes,
                    isCompleted = false,
                    position = 0,
                    createdAt = now(),
                    updatedAt = now(),
                )
            }
        }
        return ids
    }

    override suspend fun updateItem(itemId: ChecklistItemId, update: ChecklistItemUpdate) {
        updates += itemId to update
        editItem(itemId) { item ->
            item.copy(
                displayName = update.displayName ?: item.displayName,
                quantity = if (update.clearQuantity) null else update.quantity ?: item.quantity,
                unit = if (update.clearQuantity || update.clearUnit) null else update.unit ?: item.unit,
                notes = if (update.clearNotes) null else update.notes ?: item.notes,
            )
        }
    }

    override suspend fun setItemCompleted(itemId: ChecklistItemId, completed: Boolean) =
        editItem(itemId) { it.copy(isCompleted = completed) }

    override suspend fun deleteItem(itemId: ChecklistItemId) = editSections { sections ->
        sections.map { s -> s.copy(items = s.items.filterNot { it.id == itemId }) }
    }

    override suspend fun moveItem(itemId: ChecklistItemId, toIndex: Int) = editSections { sections ->
        sections.map { s ->
            val item = s.items.firstOrNull { it.id == itemId } ?: return@map s
            val rest = s.items - item
            s.copy(items = rest.toMutableList().apply { add(toIndex.coerceAtMost(rest.size), item) })
        }
    }

    override suspend fun titleExists(title: String, excluding: ChecklistId?): Boolean =
        state.value.any { it.checklist.id != excluding && it.checklist.title.equals(title, ignoreCase = true) }

    private fun newSectionId() = SectionId("sec-${nextId++}")

    private fun newItemId() = ChecklistItemId("item-${nextId++}")

    private fun Stored.toDetail() = ChecklistDetail(
        checklist,
        sections.mapIndexed { index, s ->
            ChecklistSection(
                id = s.id,
                checklistId = checklist.id,
                category = catalog.categoryById(s.categoryId)!!,
                displayOrder = index,
                items = s.items.mapIndexed { position, item -> item.copy(position = position) },
            )
        },
    )

    private fun editChecklist(id: ChecklistId, edit: (Stored) -> Stored) {
        state.update { list -> list.map { if (it.checklist.id == id) edit(it) else it } }
    }

    private fun editSections(edit: (List<StoredSection>) -> List<StoredSection>) {
        state.update { list -> list.map { it.copy(sections = edit(it.sections)) } }
    }

    private fun editItems(sectionId: SectionId, edit: (List<ChecklistItem>) -> List<ChecklistItem>) = editSections { sections ->
        sections.map { if (it.id == sectionId) it.copy(items = edit(it.items)) else it }
    }

    private fun editItem(itemId: ChecklistItemId, edit: (ChecklistItem) -> ChecklistItem) = editSections { sections ->
        sections.map { s -> s.copy(items = s.items.map { if (it.id == itemId) edit(it) else it }) }
    }
}

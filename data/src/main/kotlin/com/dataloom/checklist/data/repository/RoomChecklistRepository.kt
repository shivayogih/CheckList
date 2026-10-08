package com.dataloom.checklist.data.repository

import androidx.room.withTransaction
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.entity.ChecklistCategoryEntity
import com.dataloom.checklist.data.local.entity.ChecklistEntity
import com.dataloom.checklist.data.local.entity.ChecklistItemEntity
import com.dataloom.checklist.data.local.entity.ChecklistWithSections
import com.dataloom.checklist.data.local.entity.ItemPhotoEntity
import com.dataloom.checklist.data.mapper.DisplayNames
import com.dataloom.checklist.data.mapper.toDomain
import com.dataloom.checklist.data.mapper.toIndex
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistItemUpdate
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSort
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.repository.ChecklistRepository
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room implementation of [ChecklistRepository]. Multi-row writes run in one transaction, and every
 * change inside a checklist bumps its updated_at so "Recent" reflects the last edit.
 */
class RoomChecklistRepository @Inject constructor(
    private val db: CheckListDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val photoStore: PhotoStore,
) : ChecklistRepository {

    private val checklistDao = db.checklistDao()
    private val sectionDao = db.sectionDao()
    private val itemDao = db.checklistItemDao()
    private val categoryDao = db.categoryDao()
    private val masterItemDao = db.masterItemDao()
    private val photoDao = db.itemPhotoDao()

    override fun observeChecklists(query: ChecklistQuery): Flow<List<ChecklistSummary>> {
        val archived = when (query.filter) {
            ChecklistFilter.ACTIVE -> false
            ChecklistFilter.ARCHIVED -> true
            ChecklistFilter.ALL -> null
        }
        val pattern = query.search.trim().takeIf { it.isNotEmpty() }?.let { "%${escapeLike(it)}%" }
        return checklistDao.observeSummaries(archived, pattern)
            .map { rows -> sort(rows.map { it.toDomain() }, query.sort) }
            .distinctUntilChanged()
    }

    override fun observeChecklist(id: ChecklistId, locale: String): Flow<ChecklistDetail?> =
        checklistDao.observeDetail(id.value)
            .map { detail -> detail?.let { toDetail(it, locale) } }
            .distinctUntilChanged()

    override suspend fun createChecklist(title: String, description: String?, categoryIds: List<CategoryId>): ChecklistId {
        val now = clock.nowMillis()
        val id = ids.newId()
        db.withTransaction {
            checklistDao.insert(ChecklistEntity(id = id, title = title, description = description, createdAt = now, updatedAt = now))
            insertSections(id, categoryIds.map { it.value }.distinct(), lastOrder = null, now = now)
        }
        return ChecklistId(id)
    }

    override suspend fun updateChecklist(id: ChecklistId, title: String, description: String?) {
        checklistDao.updateContent(id.value, title, description, clock.nowMillis())
    }

    override suspend fun setArchived(id: ChecklistId, archived: Boolean) {
        val now = clock.nowMillis()
        checklistDao.setArchived(id.value, archived, archivedAt = if (archived) now else null, now = now)
    }

    override suspend fun deleteChecklist(id: ChecklistId) {
        checklistDao.delete(id.value)
    }

    override suspend fun duplicateChecklist(id: ChecklistId, newTitle: String): ChecklistId {
        val now = clock.nowMillis()
        val copyId = ids.newId()
        // Photo files are copied to new names as part of the duplicate; if anything fails they are removed again.
        val copiedFiles = ArrayList<String>()
        try {
            db.withTransaction {
                val source = requireNotNull(checklistDao.getDetail(id.value)) { "Checklist ${id.value} not found" }
                checklistDao.insert(
                    source.checklist.copy(id = copyId, title = newTitle, createdAt = now, updatedAt = now, isArchived = false, archivedAt = null),
                )
                val sectionCopies = source.sections.map { it to ids.newId() }
                sectionDao.insertAll(
                    sectionCopies.map { (section, newId) -> section.section.copy(id = newId, checklistId = copyId, createdAt = now) },
                )
                val itemCopies = sectionCopies.flatMap { (section, newSectionId) ->
                    section.items.map { sourceItem -> sourceItem to ids.newId() to newSectionId }
                }
                itemDao.insertAll(
                    itemCopies.map { (sourceAndId, newSectionId) ->
                        val (sourceItem, newItemId) = sourceAndId
                        sourceItem.item.copy(
                            id = newItemId,
                            checklistCategoryId = newSectionId,
                            isCompleted = false,
                            completedAt = null,
                            createdAt = now,
                            updatedAt = now,
                        )
                    },
                )
                val photoRows = ArrayList<ItemPhotoEntity>()
                itemCopies.forEach { (sourceAndId, _) ->
                    val (sourceItem, newItemId) = sourceAndId
                    sourceItem.photos.sortedBy { it.position }.forEach { photo ->
                        // A photo whose file is missing cannot be copied; the copy simply has one photo fewer.
                        val newName = photoStore.copy(photo.fileName) ?: return@forEach
                        copiedFiles += newName
                        photoRows += photo.copy(
                            id = ids.newId(),
                            checklistItemId = newItemId,
                            fileName = newName,
                            createdAt = now,
                        )
                    }
                }
                if (photoRows.isNotEmpty()) photoDao.insertAll(photoRows)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // Copied files are removed on any failure, cancellation included, before the error moves on.
            withContext(NonCancellable) { copiedFiles.forEach { photoStore.delete(it) } }
            throw e
        }
        return ChecklistId(copyId)
    }

    override suspend fun addSections(checklistId: ChecklistId, categoryIds: List<CategoryId>): List<SectionId> {
        val now = clock.nowMillis()
        return db.withTransaction {
            val existing = sectionDao.getForChecklist(checklistId.value)
            val present = existing.mapTo(HashSet()) { it.categoryId }
            val toAdd = categoryIds.map { it.value }.distinct().filterNot { it in present }
            if (toAdd.isEmpty()) return@withTransaction emptyList<SectionId>()
            val added = insertSections(checklistId.value, toAdd, existing.maxOfOrNull { it.displayOrder }, now)
            checklistDao.touch(checklistId.value, now)
            added
        }
    }

    override suspend fun removeSection(sectionId: SectionId) {
        db.withTransaction {
            val section = sectionDao.getById(sectionId.value) ?: return@withTransaction
            sectionDao.delete(section.id)
            checklistDao.touch(section.checklistId, clock.nowMillis())
        }
    }

    override suspend fun moveSection(sectionId: SectionId, toIndex: Int) {
        db.withTransaction {
            val section = sectionDao.getById(sectionId.value) ?: return@withTransaction
            val siblings = sectionDao.getForChecklist(section.checklistId).map { SparseOrder.Entry(it.id, it.displayOrder) }
            val changes = SparseOrder.move(siblings, section.id, toIndex)
            if (changes.isEmpty()) return@withTransaction
            changes.forEach { (id, order) -> sectionDao.updateDisplayOrder(id, order) }
            checklistDao.touch(section.checklistId, clock.nowMillis())
        }
    }

    override suspend fun addItems(sectionId: SectionId, items: List<NewChecklistItem>): List<ChecklistItemId> {
        if (items.isEmpty()) return emptyList()
        val now = clock.nowMillis()
        return db.withTransaction {
            val section = requireNotNull(sectionDao.getById(sectionId.value)) { "Section ${sectionId.value} not found" }
            val positions = SparseOrder.appended(itemDao.maxPosition(section.id), items.size)
            val rows = items.zip(positions) { item, position ->
                ChecklistItemEntity(
                    id = ids.newId(),
                    checklistCategoryId = section.id,
                    masterItemId = item.masterItemId?.value,
                    canonicalKey = item.canonicalKey,
                    displayName = item.displayName,
                    displayNameLocale = item.displayNameLocale,
                    quantityMilli = item.quantity?.milli,
                    unitCode = item.unit?.value,
                    notes = item.notes,
                    isCompleted = false,
                    completedAt = null,
                    position = position,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            itemDao.insertAll(rows)
            items.mapNotNull { it.masterItemId }.forEach { masterItemDao.incrementUseCount(it.value) }
            checklistDao.touch(section.checklistId, now)
            rows.map { ChecklistItemId(it.id) }
        }
    }

    override suspend fun updateItem(itemId: ChecklistItemId, update: ChecklistItemUpdate) {
        db.withTransaction {
            val item = itemDao.getById(itemId.value) ?: return@withTransaction
            val now = clock.nowMillis()
            val clearUnit = update.clearQuantity || update.clearUnit
            itemDao.update(
                item.copy(
                    displayName = update.displayName ?: item.displayName,
                    quantityMilli = if (update.clearQuantity) null else update.quantity?.milli ?: item.quantityMilli,
                    unitCode = if (clearUnit) null else update.unit?.value ?: item.unitCode,
                    notes = if (update.clearNotes) null else update.notes ?: item.notes,
                    updatedAt = now,
                ),
            )
            touchChecklistOf(item.id, now)
        }
    }

    override suspend fun setItemCompleted(itemId: ChecklistItemId, completed: Boolean) {
        db.withTransaction {
            val now = clock.nowMillis()
            if (itemDao.setCompleted(itemId.value, completed, completedAt = if (completed) now else null, now = now) > 0) {
                touchChecklistOf(itemId.value, now)
            }
        }
    }

    override suspend fun deleteItem(itemId: ChecklistItemId) {
        db.withTransaction {
            val checklistId = itemDao.checklistIdOf(itemId.value) ?: return@withTransaction
            itemDao.delete(itemId.value)
            checklistDao.touch(checklistId, clock.nowMillis())
        }
    }

    override suspend fun moveItem(itemId: ChecklistItemId, toIndex: Int) {
        db.withTransaction {
            val item = itemDao.getById(itemId.value) ?: return@withTransaction
            val siblings = itemDao.getForSection(item.checklistCategoryId).map { SparseOrder.Entry(it.id, it.position) }
            val changes = SparseOrder.move(siblings, item.id, toIndex)
            if (changes.isEmpty()) return@withTransaction
            changes.forEach { (id, position) -> itemDao.updatePosition(id, position) }
            touchChecklistOf(item.id, clock.nowMillis())
        }
    }

    override suspend fun titleExists(title: String, excluding: ChecklistId?): Boolean =
        checklistDao.titleExists(title.trim(), excluding?.value)

    private suspend fun insertSections(checklistId: String, categoryIds: List<String>, lastOrder: Int?, now: Long): List<SectionId> {
        val rows = categoryIds.zip(SparseOrder.appended(lastOrder, categoryIds.size)) { categoryId, order ->
            ChecklistCategoryEntity(id = ids.newId(), checklistId = checklistId, categoryId = categoryId, displayOrder = order, createdAt = now)
        }
        sectionDao.insertAll(rows)
        return rows.map { SectionId(it.id) }
    }

    private suspend fun touchChecklistOf(itemId: String, now: Long) {
        itemDao.checklistIdOf(itemId)?.let { checklistDao.touch(it, now) }
    }

    private suspend fun toDetail(detail: ChecklistWithSections, locale: String): ChecklistDetail {
        val keys = detail.sections.mapNotNull { it.category.canonicalKey }.distinct()
        val translations = if (keys.isEmpty()) {
            emptyMap()
        } else {
            categoryDao.getTranslationsForKeys(keys, DisplayNames.lookupLocales(locale)).toIndex()
        }
        return detail.toDomain(translations, locale)
    }

    private fun sort(summaries: List<ChecklistSummary>, sort: ChecklistSort): List<ChecklistSummary> = when (sort) {
        // Rows already arrive newest first.
        ChecklistSort.RECENT -> summaries
        ChecklistSort.TITLE -> {
            val collator = DisplayNames.collator(Locale.getDefault().toLanguageTag())
            summaries.sortedWith { a, b -> collator.compare(a.checklist.title, b.checklist.title) }
        }
        // Most complete first; the stable sort keeps "newest first" among equal progress.
        ChecklistSort.PROGRESS -> summaries.sortedByDescending { it.progress }
    }

    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

package com.dataloom.checklist.domain.photo

import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.repository.PhotoRepository
import javax.inject.Inject

/** Files that belong to rows about to be deleted; [run] deletes them once the rows are gone. */
fun interface PendingFileDeletion {
    suspend fun run()
}

/**
 * Room cascades only delete rows. A delete use case asks the cleaner for the files *before* it
 * deletes, deletes the rows (one transaction), and only then runs the returned [PendingFileDeletion],
 * so a failed delete never loses files and a crash in between only leaves orphans for the sweep
 * ([com.dataloom.checklist.domain.usecase.SweepOrphanPhotosUseCase]).
 */
interface PhotoFileCleaner {
    suspend fun filesOfItem(itemId: ChecklistItemId): PendingFileDeletion

    suspend fun filesOfSection(sectionId: SectionId): PendingFileDeletion

    suspend fun filesOfChecklist(checklistId: ChecklistId): PendingFileDeletion
}

/** Does nothing; the default where no photo storage is wired. */
object NoPhotoFileCleaner : PhotoFileCleaner {
    private val nothing = PendingFileDeletion { }

    override suspend fun filesOfItem(itemId: ChecklistItemId): PendingFileDeletion = nothing

    override suspend fun filesOfSection(sectionId: SectionId): PendingFileDeletion = nothing

    override suspend fun filesOfChecklist(checklistId: ChecklistId): PendingFileDeletion = nothing
}

class StorePhotoFileCleaner @Inject constructor(
    private val photos: PhotoRepository,
    private val store: PhotoStore,
) : PhotoFileCleaner {

    override suspend fun filesOfItem(itemId: ChecklistItemId): PendingFileDeletion =
        deletion(photos.fileNamesOfItem(itemId))

    override suspend fun filesOfSection(sectionId: SectionId): PendingFileDeletion =
        deletion(photos.fileNamesOfSection(sectionId))

    override suspend fun filesOfChecklist(checklistId: ChecklistId): PendingFileDeletion =
        deletion(photos.fileNamesOfChecklist(checklistId))

    private fun deletion(names: List<String>) = PendingFileDeletion { names.forEach { store.delete(it) } }
}

package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.NoPhotoStore
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.StagePhotoResult
import com.dataloom.checklist.domain.photo.StagedPhoto
import com.dataloom.checklist.domain.repository.NoPhotoRepository
import com.dataloom.checklist.domain.repository.PhotoRepository
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * What to export. [checklistIds] null exports every checklist, archived ones included.
 * [locale] is the app language, written to the metadata. [appVersion] is the app's version name.
 *
 * The profile is never exported yet: the format reserves `profile` for the opt-in profile export
 * of Phase 5 and always writes null today.
 */
data class ExportRequest(
    val checklistIds: List<ChecklistId>? = null,
    val locale: String,
    val appVersion: String,
    /**
     * Write a zip archive with `checklists.json` (formatVersion 2) and the item photos instead of
     * the plain JSON file. Photos whose file is missing or over the per-photo limit are left out.
     */
    val includePhotos: Boolean = false,
)

/**
 * Exports checklists to the versioned JSON format (section 20.3). Database IDs never leave the
 * device: every object gets a file-local ref. The file is fully built and size-checked before the
 * destination is opened, so a refused export never leaves a half-written file.
 */
class ExportChecklistsUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val catalog: CatalogRepository,
    private val codec: TransferCodec,
    private val clock: Clock,
    private val photoStore: PhotoStore = NoPhotoStore,
) {
    suspend operator fun invoke(request: ExportRequest, sink: ExportSink): ExportResult {
        val details = loadDetails(request)
        if (details.isEmpty()) return ExportResult.NothingToExport
        val itemCount = details.sumOf { it.totalItems }
        if (details.size > TransferLimits.MAX_CHECKLISTS) {
            return ExportResult.TooLarge(TransferLimit.CHECKLISTS, TransferLimits.MAX_CHECKLISTS.toLong())
        }
        if (itemCount > TransferLimits.MAX_ITEMS) return ExportResult.TooLarge(TransferLimit.ITEMS, TransferLimits.MAX_ITEMS.toLong())

        val photos = if (request.includePhotos) PhotoCollector(photoStore) else null
        val bytes = codec.encode(buildDocument(details, request, photos))
        if (bytes.size > TransferLimits.MAX_FILE_BYTES) {
            return ExportResult.TooLarge(TransferLimit.FILE_SIZE, TransferLimits.MAX_FILE_BYTES)
        }
        if (photos == null) {
            withContext(Dispatchers.IO) { sink.openStream().use { it.write(bytes) } }
            return ExportResult.Exported(details.size, itemCount, bytes.size)
        }
        if (photos.entries.size > TransferLimits.MAX_ARCHIVE_ENTRIES - 1) {
            return ExportResult.TooLarge(TransferLimit.PHOTOS, (TransferLimits.MAX_ARCHIVE_ENTRIES - 1).toLong())
        }
        if (bytes.size + photos.totalBytes > TransferLimits.MAX_ARCHIVE_BYTES) {
            return ExportResult.TooLarge(TransferLimit.ARCHIVE_SIZE, TransferLimits.MAX_ARCHIVE_BYTES)
        }
        val written = withContext(Dispatchers.IO) { writeArchive(sink, bytes, photos.entries) }
        return ExportResult.Exported(details.size, itemCount, written, photos.entries.size, photos.skipped)
    }

    /** Streams the zip to the sink; photos are copied one at a time, never held in memory together. */
    private fun writeArchive(sink: ExportSink, json: ByteArray, photos: List<ExportedPhoto>): Int {
        val counter = CountingOutputStream(sink.openStream())
        val time = clock.nowMillis()
        ZipOutputStream(counter).use { zip ->
            zip.putNextEntry(ZipEntry(TransferFormat.ARCHIVE_JSON_ENTRY).also { it.time = time })
            zip.write(json)
            zip.closeEntry()
            photos.forEach { photo ->
                zip.putNextEntry(ZipEntry(photo.entryName).also { it.time = time })
                val input = photoStore.open(photo.fileName) ?: throw IOException("Photo file disappeared during export")
                input.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return counter.count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    internal class ExportedPhoto(val entryName: String, val fileName: String)

    /** Decides which photos travel: those with an intact file within the per-photo limit. */
    internal class PhotoCollector(private val store: PhotoStore) {
        val entries = ArrayList<ExportedPhoto>()
        var totalBytes = 0L
            private set
        var skipped = 0
            private set

        suspend fun add(photo: ItemPhoto): TransferPhoto? {
            val size = store.byteSize(photo.fileName) ?: -1L
            if (size <= 0 || size > TransferLimits.MAX_PHOTO_BYTES) {
                skipped++
                return null
            }
            val number = entries.size + 1
            val entryName = "${TransferFormat.ARCHIVE_PHOTO_DIRECTORY}p$number.jpg"
            entries += ExportedPhoto(entryName, photo.fileName)
            totalBytes += size
            return TransferPhoto(ref = "p$number", file = entryName, caption = photo.caption)
        }
    }

    private suspend fun loadDetails(request: ExportRequest): List<ChecklistDetail> {
        val ids = request.checklistIds?.distinct()
            ?: checklists.observeChecklists(ChecklistQuery(filter = ChecklistFilter.ALL)).first().map { it.checklist.id }
        return ids.mapNotNull { checklists.observeChecklist(it, request.locale).first() }
    }

    internal suspend fun buildDocument(
        details: List<ChecklistDetail>,
        request: ExportRequest,
        photos: PhotoCollector? = null,
    ): TransferDocument {
        val categoryRefs = LinkedHashMap<String, TransferCategory>()
        val unitRefs = LinkedHashMap<UnitCode, TransferUnit>()
        val items = ArrayList<TransferItem>()
        var sectionCount = 0

        suspend fun unitRef(code: UnitCode): String? {
            if (!code.isCustom) return code.value
            unitRefs[code]?.let { return it.ref }
            val unit = catalog.getUnit(code) ?: return null
            val ref = "u${unitRefs.size + 1}"
            unitRefs[code] = TransferUnit(ref, TransferFormat.CUSTOM_UNIT_CODE, unit.customLabel ?: code.value, unit.allowsDecimal)
            return ref
        }

        val exported = details.mapIndexed { checklistIndex, detail ->
            val sections = detail.sections.mapIndexed { sectionIndex, section ->
                val category = section.category
                val categoryRef = categoryRefs.getOrPut(category.id.value) {
                    val ref = "c${categoryRefs.size + 1}"
                    // A seeded category travels by key, even if renamed here: renames are device-local.
                    if (category.canonicalKey != null) {
                        TransferCategory(ref, canonicalKey = category.canonicalKey)
                    } else {
                        TransferCategory(ref, customName = category.customName ?: category.displayName, icon = category.iconKey)
                    }
                }.ref
                val sectionRef = "s${++sectionCount}"
                section.items.forEachIndexed { position, item ->
                    val unit = item.unit?.let { unitRef(it) }
                    items += TransferItem(
                        ref = "i${items.size + 1}",
                        sectionRef = sectionRef,
                        canonicalKey = item.canonicalKey,
                        displayName = item.displayName,
                        displayNameLocale = item.displayNameLocale,
                        quantity = item.quantity?.toPlainString(),
                        // A unit without an amount is invalid, so a missing unit drops nothing else.
                        unit = if (item.quantity != null) unit else null,
                        notes = item.notes,
                        completed = item.isCompleted,
                        position = position,
                        photos = if (photos == null) emptyList() else item.photos.mapNotNull { photos.add(it) },
                    )
                }
                TransferSection(sectionRef, categoryRef, order = sectionIndex)
            }
            TransferChecklist(
                ref = "k${checklistIndex + 1}",
                title = detail.checklist.title,
                description = detail.checklist.description,
                archived = detail.checklist.isArchived,
                createdAt = TransferText.timestamp(detail.checklist.createdAt),
                sections = sections,
            )
        }
        return TransferDocument(
            formatVersion = if (photos != null) TransferFormat.FORMAT_VERSION else TransferFormat.JSON_FORMAT_VERSION,
            schemaVersion = TransferFormat.SCHEMA_VERSION,
            metadata = TransferMetadata(
                app = TransferFormat.APP_NAME,
                appVersion = request.appVersion,
                exportedAt = TransferText.timestamp(clock.nowMillis()),
                locale = request.locale,
                includesProfile = false,
            ),
            units = unitRefs.values.toList(),
            categories = categoryRefs.values.toList(),
            checklists = exported,
            items = items,
            profilePresent = false,
        )
    }
}

/**
 * Reads, parses and validates a picked file and matches it against the database, without
 * writing anything (section 20.2). The result is either a preview for the confirmation screen or
 * a typed rejection. The file is either plain JSON (at most 10 MB) or a photo archive (a zip,
 * recognised by its first bytes, at most 100 MB); an archive's photos wait in a scratch directory
 * until [ApplyImportUseCase] runs or [ValidatedImport.discard] is called.
 */
class PreviewImportUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val catalog: CatalogRepository,
    private val codec: TransferCodec,
    private val photoStore: PhotoStore = NoPhotoStore,
) {
    suspend operator fun invoke(source: ImportSource, locale: String): ImportPreviewResult {
        var archive: ImportArchive? = null
        val bytes = when (val read = read(source)) {
            is ReadResult.Json -> read.bytes
            is ReadResult.Archive -> {
                archive = read.archive
                read.json
            }
            is ReadResult.Failed -> return ImportPreviewResult.Rejected(read.rejection)
        }
        suspend fun reject(rejection: ImportRejection): ImportPreviewResult {
            archive?.let { photoStore.deleteScratchDirectory(it.directory) }
            return ImportPreviewResult.Rejected(rejection)
        }
        val document = when (val decoded = withContext(Dispatchers.Default) { codec.decode(bytes) }) {
            is DecodeResult.Rejected -> return reject(decoded.rejection)
            is DecodeResult.Decoded -> decoded.document
        }
        ImportValidator.validate(document, archive?.photos?.keys)?.let { return reject(it) }
        val validated = ValidatedImport(document, archive, photoStore)

        return when (val outcome = ImportPlanner(checklists, catalog).plan(document, locale)) {
            is ImportPlanner.Outcome.Rejected -> reject(outcome.rejection)
            is ImportPlanner.Outcome.Planned -> {
                val plan = outcome.plan
                ImportPreviewResult.Ready(
                    ImportPreview(
                        checklistCount = plan.checklists.size,
                        itemCount = plan.itemCount,
                        completedItemCount = plan.completedItemCount,
                        newCategories = plan.newCategories.map { it.name },
                        matchedCategoryCount = plan.matchedCategoryCount,
                        newUnits = plan.newUnits.map { it.label },
                        matchedUnitCount = plan.matchedUnitCount,
                        renamedChecklists = plan.renames,
                        profileSkipped = document.profilePresent,
                        sourceLocale = document.metadata.locale,
                        exportedAt = document.metadata.exportedAt,
                        validated = validated,
                        photoCount = plan.photoCount,
                    ),
                )
            }
        }
    }

    private sealed interface ReadResult {
        class Json(val bytes: ByteArray) : ReadResult

        class Archive(val json: ByteArray, val archive: ImportArchive) : ReadResult

        class Failed(val rejection: ImportRejection) : ReadResult
    }

    /**
     * Trusts neither the reported size nor the stream: JSON reading stops one byte past its limit,
     * and an archive is read by [ZipArchiveReader], which counts the bytes it really gets.
     */
    private suspend fun read(source: ImportSource): ReadResult = withContext(Dispatchers.IO) {
        val reported = source.sizeBytes
        if (reported != null && reported > TransferLimits.MAX_ARCHIVE_BYTES) return@withContext ReadResult.Failed(ImportRejection.FileTooLarge)
        try {
            BufferedInputStream(source.openStream()).use { input ->
                if (startsLikeZip(input)) {
                    readArchive(input)
                } else {
                    if (reported != null && reported > TransferLimits.MAX_FILE_BYTES) return@use ReadResult.Failed(ImportRejection.FileTooLarge)
                    readJson(input)
                }
            }
        } catch (_: IOException) {
            ReadResult.Failed(ImportRejection.Unreadable)
        } catch (_: SecurityException) {
            ReadResult.Failed(ImportRejection.Unreadable)
        }
    }

    private fun startsLikeZip(input: BufferedInputStream): Boolean {
        input.mark(ZIP_SIGNATURE.size)
        val head = ByteArray(ZIP_SIGNATURE.size)
        var read = 0
        while (read < head.size) {
            val n = input.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        input.reset()
        return read == head.size && head.contentEquals(ZIP_SIGNATURE)
    }

    private fun readJson(input: InputStream): ReadResult {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > TransferLimits.MAX_FILE_BYTES) return ReadResult.Failed(ImportRejection.FileTooLarge)
            out.write(buffer, 0, read)
        }
        return ReadResult.Json(out.toByteArray())
    }

    private suspend fun readArchive(input: InputStream): ReadResult {
        val scratch = photoStore.newScratchDirectory()
        return when (val result = ZipArchiveReader.read(input, scratch)) {
            is ArchiveReadResult.Read -> ReadResult.Archive(result.json, result.archive)
            is ArchiveReadResult.Rejected -> {
                photoStore.deleteScratchDirectory(scratch)
                ReadResult.Failed(result.rejection)
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024

        /** The letters P and K, then 0x03 0x04: the start of a zip file. */
        val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }
}

/**
 * Writes a previewed import in one transaction: custom units, then categories, then each
 * checklist with its sections and items, all with fresh IDs. Import always adds and never
 * overwrites. Matching is redone against the current data first, so anything created since the
 * preview is reused rather than duplicated. If any write fails, the transaction rolls back and
 * nothing is imported.
 *
 * Photos of an archive are re-encoded into the store's staging area first (so even a hostile image
 * ends up as a clean, resized JPEG without metadata), their rows are written inside the same
 * transaction, and the staged files are moved into place only after the transaction committed. A
 * failure discards the staged files. A photo that cannot be decoded is left out and counted in
 * [ImportSummary.skippedPhotoCount]; the rest of the import goes ahead.
 */
class ApplyImportUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val catalog: CatalogRepository,
    private val transactions: TransactionRunner,
    private val photos: PhotoRepository = NoPhotoRepository,
    private val photoStore: PhotoStore = NoPhotoStore,
) {
    suspend operator fun invoke(validated: ValidatedImport, locale: String): ImportResult {
        val plan = when (val outcome = ImportPlanner(checklists, catalog).plan(validated.document, locale)) {
            is ImportPlanner.Outcome.Rejected -> {
                validated.discard()
                return ImportResult.Rejected(outcome.rejection)
            }
            is ImportPlanner.Outcome.Planned -> outcome.plan
        }
        val staging = stagePhotos(plan, validated.archive)
        val ids = try {
            transactions.inTransaction { write(plan, staging) }
        } catch (e: Throwable) {
            // The caller may retry or dismiss; the raw photos stay until then.
            staging.all.forEach { photoStore.discard(it.staged) }
            throw e
        }
        staging.all.forEach { photoStore.commit(it.staged) }
        validated.discard()
        return ImportResult.Imported(
            ImportSummary(
                checklistIds = ids,
                itemCount = plan.itemCount,
                newCategoryCount = plan.newCategories.size,
                newUnitCount = plan.newUnits.size,
                renamedChecklists = plan.renames,
                photoCount = staging.all.size,
                skippedPhotoCount = staging.skipped,
            ),
        )
    }

    private class StagedImportPhoto(val staged: StagedPhoto, val caption: String?)

    /** Keyed by identity: the same [PlannedItem] instances are used for staging and for writing. */
    private class Staging(val byItem: Map<PlannedItem, List<StagedImportPhoto>>, val skipped: Int) {
        val all: List<StagedImportPhoto> get() = byItem.values.flatten()
    }

    private suspend fun stagePhotos(plan: ImportPlan, archive: ImportArchive?): Staging {
        val byItem = java.util.IdentityHashMap<PlannedItem, List<StagedImportPhoto>>()
        var skipped = 0
        val items = plan.checklists.flatMap { list -> list.sections.flatMap { it.items } }.filter { it.photos.isNotEmpty() }
        for (item in items) {
            val staged = ArrayList<StagedImportPhoto>()
            for (photo in item.photos) {
                val file = archive?.photos?.get(photo.key)
                val result = if (file == null) null else photoStore.stage(fileSource(file))
                if (result is StagePhotoResult.Staged) staged += StagedImportPhoto(result.staged, photo.caption) else skipped++
            }
            if (staged.isNotEmpty()) byItem[item] = staged
        }
        return Staging(byItem, skipped)
    }

    private fun fileSource(file: File) = ImageSource { file.inputStream() }

    private suspend fun write(plan: ImportPlan, staging: Staging): List<ChecklistId> {
        val unitCodes = plan.newUnits.map { catalog.createCustomUnit(it.label, it.allowsDecimal) }
        val categoryIds = plan.newCategories.map { catalog.createCategory(it.name, it.icon) }
        return plan.checklists.map { checklist ->
            val id = checklists.createChecklist(checklist.title, checklist.description, emptyList())
            val sectionCategories = checklist.sections.map { section ->
                when (val target = section.category) {
                    is CategoryTarget.Existing -> target.id
                    is CategoryTarget.New -> categoryIds[target.index]
                }
            }
            val sectionIds = checklists.addSections(id, sectionCategories)
            check(sectionIds.size == sectionCategories.size) { "Import created ${sectionIds.size} of ${sectionCategories.size} sections" }
            checklist.sections.zip(sectionIds).forEach { (section, sectionId) ->
                if (section.items.isEmpty()) return@forEach
                val itemIds = checklists.addItems(
                    sectionId,
                    section.items.map { item ->
                        NewChecklistItem(
                            masterItemId = null,
                            canonicalKey = item.canonicalKey,
                            displayName = item.name,
                            displayNameLocale = item.locale,
                            quantity = item.quantity,
                            unit = when (val unit = item.unit) {
                                null -> null
                                is UnitTarget.Existing -> unit.code
                                is UnitTarget.New -> unitCodes[unit.index]
                            },
                            notes = item.notes,
                        )
                    },
                )
                markCompleted(section.items.zip(itemIds).filter { it.first.completed }.map { it.second })
                section.items.zip(itemIds).forEach { (item, itemId) -> writePhotos(itemId, staging.byItem[item]) }
            }
            if (checklist.archived) checklists.setArchived(id, archived = true)
            id
        }
    }

    private suspend fun writePhotos(itemId: ChecklistItemId, staged: List<StagedImportPhoto>?) {
        if (staged.isNullOrEmpty()) return
        val rows = photos.addPhotos(itemId, staged.map { it.staged.photo })
        rows.zip(staged).forEach { (row, photo) -> photo.caption?.let { photos.setCaption(row.id, it) } }
    }

    private suspend fun markCompleted(ids: List<ChecklistItemId>) {
        ids.forEach { checklists.setItemCompleted(it, completed = true) }
    }
}

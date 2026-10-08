package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
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
) {
    suspend operator fun invoke(request: ExportRequest, sink: ExportSink): ExportResult {
        val details = loadDetails(request)
        if (details.isEmpty()) return ExportResult.NothingToExport
        val itemCount = details.sumOf { it.totalItems }
        if (details.size > TransferLimits.MAX_CHECKLISTS) {
            return ExportResult.TooLarge(TransferLimit.CHECKLISTS, TransferLimits.MAX_CHECKLISTS.toLong())
        }
        if (itemCount > TransferLimits.MAX_ITEMS) return ExportResult.TooLarge(TransferLimit.ITEMS, TransferLimits.MAX_ITEMS.toLong())

        val bytes = codec.encode(buildDocument(details, request))
        if (bytes.size > TransferLimits.MAX_FILE_BYTES) {
            return ExportResult.TooLarge(TransferLimit.FILE_SIZE, TransferLimits.MAX_FILE_BYTES)
        }
        withContext(Dispatchers.IO) { sink.openStream().use { it.write(bytes) } }
        return ExportResult.Exported(details.size, itemCount, bytes.size)
    }

    private suspend fun loadDetails(request: ExportRequest): List<ChecklistDetail> {
        val ids = request.checklistIds?.distinct()
            ?: checklists.observeChecklists(ChecklistQuery(filter = ChecklistFilter.ALL)).first().map { it.checklist.id }
        return ids.mapNotNull { checklists.observeChecklist(it, request.locale).first() }
    }

    internal suspend fun buildDocument(details: List<ChecklistDetail>, request: ExportRequest): TransferDocument {
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
            formatVersion = TransferFormat.FORMAT_VERSION,
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
 * a typed rejection.
 */
class PreviewImportUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val catalog: CatalogRepository,
    private val codec: TransferCodec,
) {
    suspend operator fun invoke(source: ImportSource, locale: String): ImportPreviewResult {
        val bytes = when (val read = readBounded(source)) {
            is ReadResult.Bytes -> read.bytes
            is ReadResult.Failed -> return ImportPreviewResult.Rejected(read.rejection)
        }
        val document = when (val decoded = withContext(Dispatchers.Default) { codec.decode(bytes) }) {
            is DecodeResult.Rejected -> return ImportPreviewResult.Rejected(decoded.rejection)
            is DecodeResult.Decoded -> decoded.document
        }
        ImportValidator.validate(document)?.let { return ImportPreviewResult.Rejected(it) }
        val validated = ValidatedImport(document)

        return when (val outcome = ImportPlanner(checklists, catalog).plan(document, locale)) {
            is ImportPlanner.Outcome.Rejected -> ImportPreviewResult.Rejected(outcome.rejection)
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
                    ),
                )
            }
        }
    }

    private sealed interface ReadResult {
        class Bytes(val bytes: ByteArray) : ReadResult

        class Failed(val rejection: ImportRejection) : ReadResult
    }

    /** Trusts neither the reported size nor the stream: reading stops one byte past the limit. */
    private suspend fun readBounded(source: ImportSource): ReadResult = withContext(Dispatchers.IO) {
        val reported = source.sizeBytes
        if (reported != null && reported > TransferLimits.MAX_FILE_BYTES) return@withContext ReadResult.Failed(ImportRejection.FileTooLarge)
        try {
            source.openStream().use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > TransferLimits.MAX_FILE_BYTES) return@withContext ReadResult.Failed(ImportRejection.FileTooLarge)
                    out.write(buffer, 0, read)
                }
                ReadResult.Bytes(out.toByteArray())
            }
        } catch (_: IOException) {
            ReadResult.Failed(ImportRejection.Unreadable)
        } catch (_: SecurityException) {
            ReadResult.Failed(ImportRejection.Unreadable)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

/**
 * Writes a previewed import in one transaction: custom units, then categories, then each
 * checklist with its sections and items, all with fresh IDs. Import always adds and never
 * overwrites. Matching is redone against the current data first, so anything created since the
 * preview is reused rather than duplicated. If any write fails, the transaction rolls back and
 * nothing is imported.
 */
class ApplyImportUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val catalog: CatalogRepository,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(validated: ValidatedImport, locale: String): ImportResult {
        val plan = when (val outcome = ImportPlanner(checklists, catalog).plan(validated.document, locale)) {
            is ImportPlanner.Outcome.Rejected -> return ImportResult.Rejected(outcome.rejection)
            is ImportPlanner.Outcome.Planned -> outcome.plan
        }
        val ids = transactions.inTransaction { write(plan) }
        return ImportResult.Imported(
            ImportSummary(
                checklistIds = ids,
                itemCount = plan.itemCount,
                newCategoryCount = plan.newCategories.size,
                newUnitCount = plan.newUnits.size,
                renamedChecklists = plan.renames,
            ),
        )
    }

    private suspend fun write(plan: ImportPlan): List<ChecklistId> {
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
            }
            if (checklist.archived) checklists.setArchived(id, archived = true)
            id
        }
    }

    private suspend fun markCompleted(ids: List<ChecklistItemId>) {
        ids.forEach { checklists.setItemCompleted(it, completed = true) }
    }
}

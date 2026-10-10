package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.photo.NoPhotoStore
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.validation.ValidationError

/**
 * Why a file cannot be imported. Nothing has been written when any of these is returned. The UI
 * maps each case to a string resource; the domain never produces user-visible text.
 */
sealed interface ImportRejection {
    /** Over [TransferLimits.MAX_FILE_BYTES], by the provider's size or by actually reading it. */
    data object FileTooLarge : ImportRejection

    /** The file could not be opened or read (permission revoked, provider gone, I/O error). */
    data object Unreadable : ImportRejection

    /** Not JSON, not UTF-8, or not the shape of the format. */
    data object Malformed : ImportRejection

    /**
     * A version this app cannot read. [requiresNewerApp] is true when the file is newer than the
     * app ("Update the app to import this file"); false for versions that never existed.
     */
    data class UnsupportedVersion(
        val formatVersion: Int,
        val schemaVersion: Int,
        val requiresNewerApp: Boolean,
    ) : ImportRejection

    data class LimitExceeded(val limit: TransferLimit, val max: Long) : ImportRejection

    /** A zip that is malformed or unsafe (path traversal, wrong entry type, duplicate...). */
    data class UnsafeArchive(val problem: ArchiveProblem) : ImportRejection

    /** Valid file without any checklist. */
    data object NothingToImport : ImportRejection

    /**
     * Content problems. [issues] holds at most [TransferLimits.MAX_REPORTED_ISSUES] entries;
     * [totalIssues] counts all of them.
     */
    data class Invalid(val issues: List<ImportIssue>, val totalIssues: Int) : ImportRejection
}

enum class ArchiveProblem {
    /** Not a readable zip, or `checklists.json` is missing. */
    NOT_AN_ARCHIVE,

    /** The same entry name twice (compared ignoring case). */
    DUPLICATE_ENTRY,

    /** A name with `..`, an absolute path, a backslash, a drive letter or a nested directory. */
    UNSAFE_PATH,

    /** A directory, or a file other than `checklists.json` and `photos/<name>.<ext>`. */
    UNEXPECTED_ENTRY,

    /** An entry with an extension that is not jpg, jpeg, png or webp. */
    DISALLOWED_EXTENSION,

    /** An entry whose first bytes are not an image of its extension's family. */
    NOT_AN_IMAGE,
}

/** The kind of file element an issue belongs to. */
enum class TransferElement { UNIT, CATEGORY, CHECKLIST, SECTION, ITEM, PHOTO }

enum class ImportProblem {
    /** Blank, too long, or characters other than letters, digits, '_', '-', '.'. */
    INVALID_REF,
    DUPLICATE_REF,

    /** A sectionRef or categoryRef that names nothing in the file. */
    MISSING_REFERENCE,

    /** A category needs exactly one of canonicalKey and customName. */
    CATEGORY_KEY_OR_NAME,
    INVALID_CANONICAL_KEY,

    /** Two sections of one checklist resolve to the same category. */
    DUPLICATE_CATEGORY_IN_CHECKLIST,

    /** A `units` entry whose code is not "CUSTOM". */
    UNSUPPORTED_UNIT_CODE,

    /** A `units` ref equal to a built-in code would make item units ambiguous. */
    UNIT_REF_SHADOWS_BUILT_IN,

    /** An item unit that is neither a built-in code nor a ref from `units`. */
    UNKNOWN_UNIT,
    INVALID_QUANTITY,
    INVALID_LOCALE,
    INVALID_TIMESTAMP,
    INVALID_ICON,

    /** Domain validation failed; see [ImportIssue.fieldErrors] (same rules as the UI). */
    INVALID_FIELDS,

    /** A photo `file` that is not `photos/<name>.<jpg|jpeg|png|webp>` with a plain name. */
    INVALID_PHOTO_PATH,

    /** Two photos naming the same archive file (compared ignoring case). */
    DUPLICATE_PHOTO_FILE,

    /** An item with more than [com.dataloom.checklist.domain.photo.PhotoLimits.MAX_PER_ITEM] photos. */
    TOO_MANY_PHOTOS,

    /** The photo names an archive entry that does not exist, or the file is not a photo archive. */
    MISSING_PHOTO_FILE,

    /** `photos` in a file that is not formatVersion 2. */
    PHOTOS_NEED_FORMAT_2,
}

/**
 * One problem in a file. [index] is the element's position in its array (for a section, within
 * its checklist); [ref] is the element's ref as written, cut to [TransferLimits.MAX_REF] characters.
 */
data class ImportIssue(
    val element: TransferElement,
    val index: Int,
    val ref: String?,
    val problem: ImportProblem,
    val fieldErrors: List<ValidationError> = emptyList(),
)

/** A checklist whose title already exists is imported under a numbered title ("Goa Trip (2)"). */
data class ChecklistRename(val originalTitle: String, val importedTitle: String)

/**
 * A file that passed parsing and validation. Only this module can create one, so
 * [ApplyImportUseCase] never receives an unchecked document.
 */
class ValidatedImport internal constructor(
    internal val document: TransferDocument,
    internal val archive: ImportArchive? = null,
    private val store: PhotoStore = NoPhotoStore,
) {
    /**
     * Deletes the raw photos of an archive import from the scratch directory. Call it when the user
     * dismisses the preview; [ApplyImportUseCase] does it itself once the import is over.
     */
    suspend fun discard() {
        archive?.let { store.deleteScratchDirectory(it.directory) }
    }
}

/**
 * What an import would do, for the confirmation screen ("3 checklists, 42 items, 2 new
 * categories"). Nothing has been written yet. Pass [validated] to [ApplyImportUseCase].
 */
data class ImportPreview(
    val checklistCount: Int,
    val itemCount: Int,
    val completedItemCount: Int,
    /** Names of categories that do not exist yet and will be created. */
    val newCategories: List<String>,
    /** Distinct existing categories the file's categories map to. */
    val matchedCategoryCount: Int,
    /** Labels of custom units that will be created. */
    val newUnits: List<String>,
    val matchedUnitCount: Int,
    val renamedChecklists: List<ChecklistRename>,
    /** The file holds a profile; it is not imported (see [TransferDocument.profilePresent]). */
    val profileSkipped: Boolean,
    /** From the file's metadata; informational. */
    val sourceLocale: String,
    val exportedAt: String,
    val validated: ValidatedImport,
    /** Photos that will be imported (0 for a plain JSON file). */
    val photoCount: Int = 0,
)

sealed interface ImportPreviewResult {
    data class Ready(val preview: ImportPreview) : ImportPreviewResult

    data class Rejected(val rejection: ImportRejection) : ImportPreviewResult
}

data class ImportSummary(
    val checklistIds: List<ChecklistId>,
    val itemCount: Int,
    val newCategoryCount: Int,
    val newUnitCount: Int,
    val renamedChecklists: List<ChecklistRename>,
    val photoCount: Int = 0,
    /** Photos of the file that could not be decoded and were left out. */
    val skippedPhotoCount: Int = 0,
)

sealed interface ImportResult {
    data class Imported(val summary: ImportSummary) : ImportResult

    /** Nothing was written. The data changed since the preview in a way that broke the file. */
    data class Rejected(val rejection: ImportRejection) : ImportResult
}

sealed interface ExportResult {
    data class Exported(
        val checklistCount: Int,
        val itemCount: Int,
        val byteCount: Int,
        val photoCount: Int = 0,
        /** Photos left out because their file is missing or over the archive's per-photo limit. */
        val skippedPhotoCount: Int = 0,
    ) : ExportResult

    /** No checklist to export (none selected, or all of them were deleted meanwhile). */
    data object NothingToExport : ExportResult

    /** The selection is bigger than the importer accepts, so the file could never be read back. */
    data class TooLarge(val limit: TransferLimit, val max: Long) : ExportResult
}

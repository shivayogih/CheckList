package com.dataloom.checklist.domain.transfer

/**
 * The import/export file in memory, mirroring docs/import-export-format.md field for field. It is
 * deliberately "raw": strings are exactly what the file holds and nothing here is trusted until
 * [ImportValidator] and the import planner have checked it. Database IDs never appear; every
 * object has a file-local `ref` instead, and import always creates fresh IDs.
 *
 * :domain has no serialization library, so the JSON mapping lives in :data (JsonTransferCodec).
 */
data class TransferDocument(
    val formatVersion: Int,
    val schemaVersion: Int,
    val metadata: TransferMetadata,
    val units: List<TransferUnit>,
    val categories: List<TransferCategory>,
    val checklists: List<TransferChecklist>,
    val items: List<TransferItem>,
    /**
     * True when the file carried a non-null `profile`. Profile import arrives with the profile
     * feature (Phase 5); until then it is never applied and the preview says it was skipped.
     */
    val profilePresent: Boolean = false,
)

/** Informational only; never used for validation decisions. */
data class TransferMetadata(
    val app: String,
    val appVersion: String,
    /** ISO-8601 UTC, e.g. "2026-10-08T04:00:00Z". */
    val exportedAt: String,
    /** App language at export time. */
    val locale: String,
    val includesProfile: Boolean,
)

/** A custom unit used by items. Built-in units are referenced by code and never listed. */
data class TransferUnit(
    val ref: String,
    /** Always [TransferFormat.CUSTOM_UNIT_CODE] in v1. */
    val code: String,
    val label: String,
    val allowsDecimal: Boolean,
)

/** Exactly one of [canonicalKey] (seeded category) or [customName] (user category). */
data class TransferCategory(
    val ref: String,
    val canonicalKey: String? = null,
    val customName: String? = null,
    val icon: String? = null,
)

data class TransferChecklist(
    val ref: String,
    val title: String,
    val description: String? = null,
    val archived: Boolean = false,
    /** ISO-8601 UTC. Informational: an imported checklist is created "now". */
    val createdAt: String? = null,
    val sections: List<TransferSection>,
)

/** One category placed in one checklist; [order] sorts sections (values are relative only). */
data class TransferSection(
    val ref: String,
    val categoryRef: String,
    val order: Int,
)

data class TransferItem(
    val ref: String,
    val sectionRef: String,
    val canonicalKey: String? = null,
    val displayName: String,
    val displayNameLocale: String,
    /** Exact decimal as text ("2.5"), see Quantity. */
    val quantity: String? = null,
    /** A built-in unit code ("KG") or the `ref` of an entry in [TransferDocument.units]. */
    val unit: String? = null,
    val notes: String? = null,
    val completed: Boolean = false,
    /** Order within the section; values are relative only. */
    val position: Int = 0,
    /** Photos of the item (formatVersion 2, zip archive only), in display order. */
    val photos: List<TransferPhoto> = emptyList(),
)

/** One photo of an item. The image itself is the archive entry named by [file]. */
data class TransferPhoto(
    val ref: String,
    /** Archive path, `photos/<name>.jpg` (also `.jpeg`, `.png`, `.webp` on import). */
    val file: String,
    val caption: String? = null,
)

/** Version numbers and fixed values of the format. Bump with care: see the format document. */
object TransferFormat {
    /** Shape of the envelope. Version 2 adds `items[].photos` and is only valid inside a zip archive. */
    const val FORMAT_VERSION = 2

    /** The plain JSON format (no photos); still written when photos are not exported, and always readable. */
    const val JSON_FORMAT_VERSION = 1

    /** Shape of the content model. */
    const val SCHEMA_VERSION = 1

    const val APP_NAME = "CheckList"

    /** Value of [TransferUnit.code] for user-created units. */
    const val CUSTOM_UNIT_CODE = "CUSTOM"

    const val MIME_TYPE = "application/json"

    /** Photo archive: a zip with [ARCHIVE_JSON_ENTRY] and `photos/<name>.jpg` entries. */
    const val ZIP_MIME_TYPE = "application/zip"
    const val ARCHIVE_JSON_ENTRY = "checklists.json"
    const val ARCHIVE_PHOTO_DIRECTORY = "photos/"

    /** Extensions an archive photo may have; the bytes must also be an image of that family. */
    val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
}

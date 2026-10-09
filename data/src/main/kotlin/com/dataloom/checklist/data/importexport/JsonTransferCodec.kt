package com.dataloom.checklist.data.importexport

import com.dataloom.checklist.domain.transfer.DecodeResult
import com.dataloom.checklist.domain.transfer.ImportRejection
import com.dataloom.checklist.domain.transfer.TransferCategory
import com.dataloom.checklist.domain.transfer.TransferChecklist
import com.dataloom.checklist.domain.transfer.TransferCodec
import com.dataloom.checklist.domain.transfer.TransferDocument
import com.dataloom.checklist.domain.transfer.TransferFormat
import com.dataloom.checklist.domain.transfer.TransferItem
import com.dataloom.checklist.domain.transfer.TransferMetadata
import com.dataloom.checklist.domain.transfer.TransferPhoto
import com.dataloom.checklist.domain.transfer.TransferSection
import com.dataloom.checklist.domain.transfer.TransferUnit
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Upgrades a file one version step, on the JSON tree (section 20.1). Register a migrator for every
 * old version when the format changes, together with a golden file of the old version.
 */
internal interface JsonMigrator {
    val fromFormatVersion: Int
    val fromSchemaVersion: Int

    /** Returns the tree in the next version, with its version fields updated. */
    fun migrate(root: JsonObject): JsonObject
}

internal object JsonMigrators {
    /** Version 1 is the first format, so there is nothing to upgrade yet. */
    val ALL: List<JsonMigrator> = emptyList()
}

/**
 * The import/export file as JSON, with kotlinx.serialization (docs/import-export-format.md).
 *
 * Decoding is strict and never throws for bad input:
 * - UTF-8 only (a leading byte order mark is allowed); malformed bytes are rejected,
 * - the version header is read first, so a newer file is reported as "update the app" even when the
 *   rest of it has a shape this version does not know,
 * - unknown keys, wrong types, comments and trailing commas are rejected,
 * - array sizes and string lengths are bounded while parsing ([TransferLimitException]).
 * Domain rules (refs, field limits, units) are checked afterwards by the domain ImportValidator.
 */
class JsonTransferCodec internal constructor(private val migrators: List<JsonMigrator>) : TransferCodec {

    @Inject constructor() : this(JsonMigrators.ALL)

    override fun encode(document: TransferDocument): ByteArray {
        // Items without photos (every item of a version 1 file) carry no "photos" key,
        // so version 1 output is unchanged.
        val tree = WRITER.encodeToJsonElement(TransferFileDto.serializer(), document.toDto()).jsonObject
        val items = tree.getValue("items").jsonArray.map { item ->
            val fields = item.jsonObject
            if (fields["photos"]?.jsonArray?.isEmpty() == true) JsonObject(fields - "photos") else fields
        }
        val trimmed = JsonObject(tree + ("items" to JsonArray(items)))
        return WRITER.encodeToString(JsonObject.serializer(), trimmed).toByteArray(StandardCharsets.UTF_8)
    }

    override fun decode(bytes: ByteArray): DecodeResult {
        val text = decodeUtf8(bytes) ?: return DecodeResult.Rejected(ImportRejection.Malformed)
        // kotlinx.serialization recurses per nesting level (even when skipping), so a file of
        // a few hundred thousand '[' would overflow the stack. The format needs only a few levels.
        if (nestingDepth(text) > MAX_NESTING_DEPTH) return DecodeResult.Rejected(ImportRejection.Malformed)
        return try {
            val header = HEADER_READER.decodeFromString(VersionHeaderDto.serializer(), text)
            decodeVersioned(text, header.formatVersion, header.schemaVersion)
        } catch (e: TransferLimitException) {
            DecodeResult.Rejected(ImportRejection.LimitExceeded(e.limit, e.max))
        } catch (e: RuntimeException) {
            // kotlinx.serialization reports bad input with several exception types; a limit
            // breach may arrive wrapped. Anything else is a file we cannot read.
            val limit = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<TransferLimitException>().firstOrNull()
            DecodeResult.Rejected(limit?.let { ImportRejection.LimitExceeded(it.limit, it.max) } ?: ImportRejection.Malformed)
        }
    }

    private fun decodeVersioned(text: String, formatVersion: Int, schemaVersion: Int): DecodeResult {
        // Every format version from 1 up to the current one has the same shape (version 2 only adds optional photos).
        val current = formatVersion in 1..TransferFormat.FORMAT_VERSION &&
            schemaVersion == TransferFormat.SCHEMA_VERSION
        if (current) return DecodeResult.Decoded(READER.decodeFromString(TransferFileDto.serializer(), text).toDomain())

        val newer = formatVersion > TransferFormat.FORMAT_VERSION || schemaVersion > TransferFormat.SCHEMA_VERSION
        val unsupported = DecodeResult.Rejected(ImportRejection.UnsupportedVersion(formatVersion, schemaVersion, requiresNewerApp = newer))
        if (newer || migrators.none { it.fromFormatVersion == formatVersion && it.fromSchemaVersion == schemaVersion }) {
            return unsupported
        }
        var root = READER.parseToJsonElement(text).jsonObject
        var format = formatVersion
        var schema = schemaVersion
        while (format !in 1..TransferFormat.FORMAT_VERSION || schema != TransferFormat.SCHEMA_VERSION) {
            val migrator = migrators.firstOrNull { it.fromFormatVersion == format && it.fromSchemaVersion == schema } ?: return unsupported
            root = migrator.migrate(root)
            val nextFormat = root.getValue("formatVersion").jsonPrimitive.int
            val nextSchema = root.getValue("schemaVersion").jsonPrimitive.int
            check(nextFormat to nextSchema != format to schema) { "Migrator did not advance the version" }
            format = nextFormat
            schema = nextSchema
        }
        return DecodeResult.Decoded(READER.decodeFromJsonElement(TransferFileDto.serializer(), root).toDomain())
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        return text.removePrefix(BYTE_ORDER_MARK)
    }

    /** Deepest bracket nesting outside strings; stops counting once past the limit. */
    private fun nestingDepth(text: String): Int {
        var depth = 0
        var deepest = 0
        var inString = false
        var escaped = false
        for (ch in text) {
            when {
                escaped -> escaped = false
                inString && ch == '\\' -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' || ch == '[' -> {
                    depth++
                    if (depth > deepest) deepest = depth
                    if (deepest > MAX_NESTING_DEPTH) return deepest
                }
                ch == '}' || ch == ']' -> depth--
            }
        }
        return deepest
    }

    private companion object {
        const val BYTE_ORDER_MARK = "\uFEFF"

        /** Version 1 needs 5 levels (file, checklists, checklist, sections, section); the rest is headroom for the profile. */
        const val MAX_NESTING_DEPTH = 32

        /** Strict: the format document is the contract, so anything outside it is refused. */
        val READER = Json {
            ignoreUnknownKeys = false
            isLenient = false
            allowSpecialFloatingPointValues = false
        }

        /** Only reads the two version numbers and skips everything else. */
        val HEADER_READER = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        /** Readable files; nulls written out so every field of the format is visible. */
        val WRITER = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = true
        }
    }
}

private fun TransferDocument.toDto() = TransferFileDto(
    formatVersion = formatVersion,
    schemaVersion = schemaVersion,
    metadata = MetadataDto(metadata.app, metadata.appVersion, metadata.exportedAt, metadata.locale, metadata.includesProfile),
    units = units.map { UnitDto(it.ref, it.code, it.label, it.allowsDecimal) },
    categories = categories.map { CategoryDto(it.ref, it.canonicalKey, it.customName, it.icon) },
    checklists = checklists.map { checklist ->
        ChecklistDto(
            ref = checklist.ref,
            title = checklist.title,
            description = checklist.description,
            archived = checklist.archived,
            createdAt = checklist.createdAt,
            sections = checklist.sections.map { SectionDto(it.ref, it.categoryRef, it.order) },
        )
    },
    items = items.map {
        ItemDto(
            ref = it.ref,
            sectionRef = it.sectionRef,
            canonicalKey = it.canonicalKey,
            displayName = it.displayName,
            displayNameLocale = it.displayNameLocale,
            quantity = it.quantity,
            unit = it.unit,
            notes = it.notes,
            completed = it.completed,
            position = it.position,
            photos = it.photos.map { photo -> PhotoDto(photo.ref, photo.file, photo.caption) },
        )
    },
    // Profile export arrives with the profile feature; until then the field is always null.
    profile = JsonNull,
)

private fun TransferFileDto.toDomain() = TransferDocument(
    formatVersion = formatVersion,
    schemaVersion = schemaVersion,
    metadata = TransferMetadata(metadata.app, metadata.appVersion, metadata.exportedAt, metadata.locale, metadata.includesProfile),
    units = units.map { TransferUnit(it.ref, it.code, it.label, it.allowsDecimal) },
    categories = categories.map { TransferCategory(it.ref, it.canonicalKey, it.customName, it.icon) },
    checklists = checklists.map { checklist ->
        TransferChecklist(
            ref = checklist.ref,
            title = checklist.title,
            description = checklist.description,
            archived = checklist.archived,
            createdAt = checklist.createdAt,
            sections = checklist.sections.map { TransferSection(it.ref, it.categoryRef, it.order) },
        )
    },
    items = items.map {
        TransferItem(
            ref = it.ref,
            sectionRef = it.sectionRef,
            canonicalKey = it.canonicalKey,
            displayName = it.displayName,
            displayNameLocale = it.displayNameLocale,
            quantity = it.quantity,
            unit = it.unit,
            notes = it.notes,
            completed = it.completed,
            position = it.position,
            photos = it.photos.map { photo -> TransferPhoto(photo.ref, photo.file, photo.caption) },
        )
    },
    profilePresent = profile != null && profile !is JsonNull,
)

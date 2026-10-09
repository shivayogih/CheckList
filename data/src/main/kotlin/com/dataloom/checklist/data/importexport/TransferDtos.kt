@file:UseSerializers(CappedStringSerializer::class)

package com.dataloom.checklist.data.importexport

import com.dataloom.checklist.domain.transfer.TransferLimit
import com.dataloom.checklist.domain.transfer.TransferLimits
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

// JSON shape of docs/import-export-format.md, version 1. Every String in this file goes through
// CappedStringSerializer and every list through a BoundedListSerializer, so limits are enforced
// while the file is read, before a huge value or array is fully built.

/** Read first, leniently, to decide whether the rest of the file can be read at all. */
@Serializable
internal data class VersionHeaderDto(val formatVersion: Int, val schemaVersion: Int)

@Serializable
internal data class TransferFileDto(
    val formatVersion: Int,
    val schemaVersion: Int,
    val metadata: MetadataDto,
    @Serializable(with = UnitListSerializer::class) val units: List<UnitDto>,
    @Serializable(with = CategoryListSerializer::class) val categories: List<CategoryDto>,
    @Serializable(with = ChecklistListSerializer::class) val checklists: List<ChecklistDto>,
    @Serializable(with = ItemListSerializer::class) val items: List<ItemDto>,
    /** Reserved for the opt-in profile export (Phase 5); written as null, never applied on import. */
    val profile: JsonElement? = null,
)

@Serializable
internal data class MetadataDto(
    val app: String,
    val appVersion: String,
    val exportedAt: String,
    val locale: String,
    val includesProfile: Boolean = false,
)

@Serializable
internal data class UnitDto(
    val ref: String,
    val code: String,
    val label: String,
    val allowsDecimal: Boolean,
)

@Serializable
internal data class CategoryDto(
    val ref: String,
    val canonicalKey: String? = null,
    val customName: String? = null,
    val icon: String? = null,
)

@Serializable
internal data class ChecklistDto(
    val ref: String,
    val title: String,
    val description: String? = null,
    val archived: Boolean = false,
    val createdAt: String? = null,
    @Serializable(with = SectionListSerializer::class) val sections: List<SectionDto>,
)

@Serializable
internal data class SectionDto(
    val ref: String,
    val categoryRef: String,
    val order: Int,
)

@Serializable
internal data class ItemDto(
    val ref: String,
    val sectionRef: String,
    val canonicalKey: String? = null,
    val displayName: String,
    val displayNameLocale: String,
    val quantity: String? = null,
    val unit: String? = null,
    val notes: String? = null,
    val completed: Boolean = false,
    val position: Int = 0,
    /** formatVersion 2 only. A version 1 file that has the key decodes, and the validator refuses it. */
    @Serializable(with = PhotoListSerializer::class) val photos: List<PhotoDto> = emptyList(),
)

@Serializable
internal data class PhotoDto(
    val ref: String,
    val file: String,
    val caption: String? = null,
)

/** Thrown while parsing when a file breaks a limit; the codec turns it into a typed rejection. */
internal class TransferLimitException(val limit: TransferLimit, val max: Long) : RuntimeException("$limit over $max")

/** Rejects any string longer than [TransferLimits.MAX_RAW_TEXT] UTF-16 units. */
internal object CappedStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CappedString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val value = decoder.decodeString()
        if (value.length > TransferLimits.MAX_RAW_TEXT) {
            throw TransferLimitException(TransferLimit.TEXT_LENGTH, TransferLimits.MAX_RAW_TEXT.toLong())
        }
        return value
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** A list serializer that stops reading as soon as the array holds more than [max] elements. */
internal abstract class BoundedListSerializer<T>(
    private val element: KSerializer<T>,
    private val max: Int,
    private val limit: TransferLimit,
) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val result = ArrayList<T>()
        val composite = decoder.beginStructure(descriptor)
        while (true) {
            val index = composite.decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            if (result.size >= max) throw TransferLimitException(limit, max.toLong())
            result += composite.decodeSerializableElement(descriptor, index, element)
        }
        composite.endStructure(descriptor)
        return result
    }
}

internal object UnitListSerializer :
    BoundedListSerializer<UnitDto>(UnitDto.serializer(), TransferLimits.MAX_UNITS, TransferLimit.UNITS)

internal object CategoryListSerializer :
    BoundedListSerializer<CategoryDto>(CategoryDto.serializer(), TransferLimits.MAX_CATEGORIES, TransferLimit.CATEGORIES)

internal object ChecklistListSerializer :
    BoundedListSerializer<ChecklistDto>(ChecklistDto.serializer(), TransferLimits.MAX_CHECKLISTS, TransferLimit.CHECKLISTS)

internal object SectionListSerializer :
    BoundedListSerializer<SectionDto>(SectionDto.serializer(), TransferLimits.MAX_SECTIONS_PER_CHECKLIST, TransferLimit.SECTIONS)

/** Bounded by the archive's entry limit; the 3-per-item rule is a validation issue, not a parse failure. */
internal object PhotoListSerializer :
    BoundedListSerializer<PhotoDto>(PhotoDto.serializer(), TransferLimits.MAX_ARCHIVE_ENTRIES - 1, TransferLimit.PHOTOS)

internal object ItemListSerializer :
    BoundedListSerializer<ItemDto>(ItemDto.serializer(), TransferLimits.MAX_ITEMS, TransferLimit.ITEMS)

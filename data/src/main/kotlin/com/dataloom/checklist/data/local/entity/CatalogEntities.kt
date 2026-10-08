package com.dataloom.checklist.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Seeded categories have [canonicalKey] and translated names; user categories (or a user rename of
 * a seeded one) have [customName]. At least one of the two is required: Room cannot declare CHECK
 * constraints, so triggers created with the schema enforce it (see SchemaTriggers).
 */
@Entity(
    tableName = "category",
    indices = [
        Index(value = ["canonical_key"], unique = true),
        Index(value = ["is_hidden"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "canonical_key") val canonicalKey: String?,
    @ColumnInfo(name = "custom_name") val customName: String?,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "is_hidden", defaultValue = "0") val isHidden: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Seeded name of a category in one language. Keyed by canonical key so it survives re-seeding. */
@Entity(
    tableName = "category_translation",
    primaryKeys = ["canonical_key", "locale"],
)
data class CategoryTranslationEntity(
    @ColumnInfo(name = "canonical_key") val canonicalKey: String,
    /** BCP 47 language, "en", "kn"... */
    val locale: String,
    val name: String,
)

/** A reusable item suggestion. Identity is [canonicalKey] ("rice", or "custom:<uuid>"), never its text. */
@Entity(
    tableName = "master_item",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UnitDefEntity::class,
            parentColumns = ["code"],
            childColumns = ["default_unit_code"],
        ),
    ],
    indices = [
        Index(value = ["category_id", "canonical_key"], unique = true),
        Index(value = ["category_id", "is_hidden"]),
        Index(value = ["canonical_key"]),
        Index(value = ["default_unit_code"]),
    ],
)
data class MasterItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "canonical_key") val canonicalKey: String,
    @ColumnInfo(name = "custom_name") val customName: String?,
    @ColumnInfo(name = "custom_name_locale") val customNameLocale: String?,
    @ColumnInfo(name = "default_unit_code") val defaultUnitCode: String?,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "is_hidden", defaultValue = "0") val isHidden: Boolean = false,
    /** How often the item was added to a checklist; ranks suggestions and search results. */
    @ColumnInfo(name = "use_count", defaultValue = "0") val useCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Seeded name of an item in one language, plus search aliases such as transliterations ("akki"). */
@Entity(
    tableName = "master_item_translation",
    primaryKeys = ["canonical_key", "locale"],
)
data class MasterItemTranslationEntity(
    @ColumnInfo(name = "canonical_key") val canonicalKey: String,
    val locale: String,
    val name: String,
    /** Aliases joined with [ALIAS_SEPARATOR]; null when there are none. */
    val aliases: String?,
) {
    val aliasList: List<String>
        get() = aliases?.split(ALIAS_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        /** A newline never appears in a name, and FTS treats it as a token separator. */
        const val ALIAS_SEPARATOR = "\n"
    }
}

/** Built-in labels come from string resources keyed by [code]; custom units store the typed label. */
@Entity(tableName = "unit_def")
data class UnitDefEntity(
    @PrimaryKey val code: String,
    @ColumnInfo(name = "allows_decimal") val allowsDecimal: Boolean,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "custom_label") val customLabel: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

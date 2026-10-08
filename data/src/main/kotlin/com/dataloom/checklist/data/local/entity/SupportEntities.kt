package com.dataloom.checklist.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * The single profile row (id "me"). The whole profile is one AES-256-GCM blob rather than
 * per-column ciphertext: nothing in it needs SQL, and one blob leaks no per-field lengths.
 * Reading and writing it arrives with the profile feature (Phase 5).
 *
 * Not a data class: generated equals would compare the array by reference.
 */
@Entity(tableName = "user_profile")
class UserProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "enc_payload", typeAffinity = ColumnInfo.BLOB) val encPayload: ByteArray,
    @ColumnInfo(name = "key_alias") val keyAlias: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Derived search index over master item names in every language, their aliases and custom names
 * (section 6.6). Only [text] is tokenized; the other columns filter matches. Rows are rebuilt for an
 * item whenever its names change, inside the same transaction.
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    notIndexed = ["ref_type", "ref_id", "category_id", "locale"],
)
@Entity(tableName = "item_search_fts")
data class ItemSearchFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    /** [REF_MASTER] for a seeded translation, [REF_CUSTOM] for a user-typed name. */
    @ColumnInfo(name = "ref_type") val refType: String,
    /** master_item.id */
    @ColumnInfo(name = "ref_id") val refId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val locale: String,
    /** Normalized name and aliases, one per line. */
    val text: String,
) {
    companion object {
        const val REF_MASTER = "MASTER"
        const val REF_CUSTOM = "CUSTOM"
    }
}

/** Version of the bundled seed catalog last applied (section 6.4). Single row, id 1. */
@Entity(tableName = "seed_meta")
data class SeedMetaEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "seed_version") val seedVersion: Int,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

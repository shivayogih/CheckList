package com.dataloom.checklist.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.dataloom.checklist.data.local.entity.ItemSearchFtsEntity

/**
 * The data users of each released schema version have on their phones (CL-320, docs/db-migrations.md).
 *
 * [populate] writes raw SQL against the columns of that exact version, never through the current
 * entities, so a later schema change cannot silently rewrite history. Never edit an existing
 * version's data; when you add schema version N, add the case for N here (a test fails until you do)
 * and keep it a superset of N-1 so that every step is tested with realistic content.
 */
object MigrationFixtures {

    /** Bytes standing in for the AES-GCM profile blob; the migration must copy them bit for bit. */
    val profileBlob: ByteArray = ByteArray(64) { (it * 7 + 3).toByte() }

    fun populate(version: Int, db: SupportSQLiteDatabase) {
        when (version) {
            1 -> populateV1(db)
            2 -> {
                populateV1(db)
                populatePhotosV2(db)
            }
            else -> error("Add the data of schema v$version to MigrationFixtures.populate (docs/db-migrations.md)")
        }
    }

    private fun populateV1(db: SupportSQLiteDatabase) {
        listOf(
            arrayOf<Any?>("KG", 1, 0, null, 10),
            arrayOf<Any?>("PIECE", 0, 0, null, 60),
            arrayOf<Any?>("CUSTOM_u-1", 0, 1, "Bag", 900),
        ).forEach {
            db.execSQL(
                "INSERT INTO unit_def (code, allows_decimal, is_custom, custom_label, sort_order) VALUES (?, ?, ?, ?, ?)",
                it,
            )
        }
        listOf(
            arrayOf<Any?>("cat-1", "groceries", null, "cart", 0, 0),
            arrayOf<Any?>("cat-2", "vegetables", null, "carrot", 0, 1),
            arrayOf<Any?>("cat-3", null, "My shop", "star", 1, 0),
        ).forEach {
            db.execSQL(
                "INSERT INTO category (id, canonical_key, custom_name, icon_key, is_custom, is_hidden, created_at, " +
                    "updated_at) VALUES (?, ?, ?, ?, ?, ?, 100, 200)",
                it,
            )
        }
        listOf(
            arrayOf<Any?>("groceries", "en", "Groceries"),
            arrayOf<Any?>("groceries", "kn", "ದಿನಸಿ"),
            arrayOf<Any?>("vegetables", "en", "Vegetables"),
        ).forEach { db.execSQL("INSERT INTO category_translation (canonical_key, locale, name) VALUES (?, ?, ?)", it) }
        listOf(
            arrayOf<Any?>("mi-1", "cat-1", "rice", null, null, "KG", 0, 0, 5),
            arrayOf<Any?>("mi-2", "cat-2", "tomato", null, null, "KG", 0, 1, 0),
            arrayOf<Any?>("mi-3", "cat-3", "custom:3f2a", "Akki bag", "kn", "CUSTOM_u-1", 1, 0, 2),
        ).forEach {
            db.execSQL(
                "INSERT INTO master_item (id, category_id, canonical_key, custom_name, custom_name_locale, " +
                    "default_unit_code, is_custom, is_hidden, use_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 100, 200)",
                it,
            )
        }
        listOf(
            arrayOf<Any?>("rice", "en", "Rice", "chawal"),
            arrayOf<Any?>("rice", "kn", "ಅಕ್ಕಿ", "akki"),
            arrayOf<Any?>("rice", "hi", "चावल", null),
            arrayOf<Any?>("tomato", "en", "Tomato", "tamatar"),
        ).forEach {
            db.execSQL("INSERT INTO master_item_translation (canonical_key, locale, name, aliases) VALUES (?, ?, ?, ?)", it)
        }
        listOf(
            arrayOf<Any?>(ItemSearchFtsEntity.REF_MASTER, "mi-1", "cat-1", "en", "Rice chawal"),
            arrayOf<Any?>(ItemSearchFtsEntity.REF_MASTER, "mi-1", "cat-1", "kn", "ಅಕ್ಕಿ akki"),
            arrayOf<Any?>(ItemSearchFtsEntity.REF_CUSTOM, "mi-3", "cat-3", "kn", "Akki bag"),
        ).forEach {
            db.execSQL("INSERT INTO item_search_fts (ref_type, ref_id, category_id, locale, text) VALUES (?, ?, ?, ?, ?)", it)
        }
        db.execSQL("INSERT INTO seed_meta (id, seed_version) VALUES (1, 1)")
        db.execSQL(
            "INSERT INTO user_profile (id, enc_payload, key_alias, schema_version, updated_at) VALUES ('me', ?, ?, 1, 300)",
            arrayOf<Any?>(profileBlob, "checklist_profile_master"),
        )
        db.execSQL(
            "INSERT INTO checklist (id, title, description, created_at, updated_at, is_archived, archived_at) " +
                "VALUES ('list-1', 'Goa Trip', 'Beach week', 1000, 2000, 0, NULL)",
        )
        db.execSQL(
            "INSERT INTO checklist (id, title, description, created_at, updated_at, is_archived, archived_at) " +
                "VALUES ('list-2', 'Old exam list', NULL, 500, 600, 1, 700)",
        )
        listOf(
            arrayOf<Any?>("sec-1", "list-1", "cat-1", 1000),
            arrayOf<Any?>("sec-2", "list-1", "cat-2", 2000),
            arrayOf<Any?>("sec-3", "list-2", "cat-1", 1000),
        ).forEach {
            db.execSQL(
                "INSERT INTO checklist_category (id, checklist_id, category_id, display_order, created_at) " +
                    "VALUES (?, ?, ?, ?, 1000)",
                it,
            )
        }
        listOf(
            // A snapshot of a catalog item, with a note and a decimal quantity.
            arrayOf<Any?>("item-1", "sec-1", "mi-1", "rice", "Rice", "en", 2_500L, "KG", "Basmati only", 0, null, 1000),
            // Completed, name saved in Kannada: the snapshot must not follow the catalog.
            arrayOf<Any?>("item-2", "sec-1", "mi-1", "rice", "ಅಕ್ಕಿ", "kn", 1_000L, "KG", null, 1, 1500L, 2000),
            // Typed by the user: no master item, custom unit.
            arrayOf<Any?>("item-3", "sec-2", null, null, "Cloth bag", "en", 3_000L, "CUSTOM_u-1", null, 0, null, 1000),
            arrayOf<Any?>("item-4", "sec-3", "mi-1", "rice", "Rice", "en", 12_000L, "PIECE", "Exam week", 1, 650L, 1000),
        ).forEach {
            db.execSQL(
                "INSERT INTO checklist_item (id, checklist_category_id, master_item_id, canonical_key, display_name, " +
                    "display_name_locale, quantity_milli, unit_code, notes, is_completed, completed_at, position, " +
                    "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1000, 2000)",
                it,
            )
        }
    }

    private fun populatePhotosV2(db: SupportSQLiteDatabase) {
        listOf(
            arrayOf<Any?>("ph-1", "item-1", "ph-1.jpg", 1600, 1200, 245_000L, 1000, null),
            arrayOf<Any?>("ph-2", "item-1", "ph-2.jpg", 1200, 1600, 198_000L, 2000, "Brand to buy"),
            arrayOf<Any?>("ph-3", "item-3", "ph-3.jpg", 800, 800, 90_000L, 1000, null),
        ).forEach {
            db.execSQL(
                "INSERT INTO item_photo (id, checklist_item_id, file_name, width, height, byte_size, position, caption, " +
                    "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 3000)",
                it,
            )
        }
    }
}

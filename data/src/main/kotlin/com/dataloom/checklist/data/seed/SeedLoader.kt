package com.dataloom.checklist.data.seed

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dataloom.checklist.data.local.entity.MasterItemTranslationEntity
import com.dataloom.checklist.data.local.entity.SeedMetaEntity
import com.dataloom.checklist.data.local.fts.SearchText
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IdGenerator
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * Applies the bundled seed catalog (section 6.4) when the database is created and whenever the
 * asset's seedVersion is higher than the one recorded in seed_meta.
 *
 * It works on the raw connection because it runs inside Room's open callbacks, before DAOs may be
 * used. Rows are upserted by canonical key and never deleted; a category or item the user renamed
 * (custom_name set) or hid keeps its row untouched. Translations are seed-owned and always refreshed.
 * The search index is rebuilt in the same transaction. Pre-3.24 SQLite (API 26) has no UPSERT
 * clause, and INSERT OR REPLACE would delete and cascade, so updates are explicit.
 */
class SeedLoader @Inject constructor(
    private val source: SeedSource,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns true when the catalog was applied. */
    fun seedIfNeeded(db: SupportSQLiteDatabase): Boolean {
        val catalog = source.catalog()?.let { json.decodeFromString<SeedCatalog>(it) } ?: return false
        val applied = appliedVersion(db)
        if (applied != null && applied >= catalog.seedVersion) return false
        val translations = source.translations().map { json.decodeFromString<SeedTranslations>(it) }

        db.beginTransaction()
        try {
            apply(db, catalog, translations, clock.nowMillis())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    private fun appliedVersion(db: SupportSQLiteDatabase): Int? =
        db.query(
            "SELECT seed_version FROM seed_meta WHERE id = ?",
            arrayOf<Any?>(SeedMetaEntity.SINGLE_ROW_ID),
        ).use { if (it.moveToFirst()) it.getInt(0) else null }

    private fun apply(db: SupportSQLiteDatabase, catalog: SeedCatalog, translations: List<SeedTranslations>, now: Long) {
        upsertUnits(db, catalog.units)
        val categoryIds = upsertCategories(db, catalog.categories, now)
        upsertItems(db, catalog.items, categoryIds, now)
        upsertTranslations(db, catalog, translations)
        rebuildSearchIndex(db)
        db.execSQL(
            "INSERT OR REPLACE INTO seed_meta (id, seed_version) VALUES (?, ?)",
            arrayOf<Any?>(SeedMetaEntity.SINGLE_ROW_ID, catalog.seedVersion),
        )
    }

    private fun upsertUnits(db: SupportSQLiteDatabase, units: List<SeedUnit>) {
        units.forEach { unit ->
            val allowsDecimal = unit.allowsDecimal.toInt()
            db.execSQL(
                """
                INSERT OR IGNORE INTO unit_def (code, allows_decimal, is_custom, custom_label, sort_order)
                VALUES (?, ?, 0, NULL, ?)
                """.trimIndent(),
                arrayOf<Any?>(unit.code, allowsDecimal, unit.sortOrder),
            )
            db.execSQL(
                "UPDATE unit_def SET allows_decimal = ?, sort_order = ? WHERE code = ? AND is_custom = 0",
                arrayOf<Any?>(allowsDecimal, unit.sortOrder, unit.code),
            )
        }
    }

    /** Returns canonical key -> category id for every seeded category, new or existing. */
    private fun upsertCategories(db: SupportSQLiteDatabase, categories: List<SeedCategory>, now: Long): Map<String, String> =
        categories.associate { category ->
            val existing = db.query(
                "SELECT id, icon_key, custom_name, is_hidden FROM category WHERE canonical_key = ?",
                arrayOf<Any?>(category.key),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    ExistingRow(cursor.getString(0), cursor.isUserOwned(customName = 2, hidden = 3), cursor.getString(1))
                } else {
                    null
                }
            }
            val id = when {
                existing == null -> ids.newId().also { id ->
                    db.execSQL(
                        """
                        INSERT INTO category (id, canonical_key, custom_name, icon_key, is_custom, is_hidden, created_at, updated_at)
                        VALUES (?, ?, NULL, ?, 0, 0, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any?>(id, category.key, category.icon, now, now),
                    )
                }
                !existing.userOwned && existing.signature != category.icon -> existing.id.also { id ->
                    db.execSQL(
                        "UPDATE category SET icon_key = ?, updated_at = ? WHERE id = ?",
                        arrayOf<Any?>(category.icon, now, id),
                    )
                }
                else -> existing.id
            }
            category.key to id
        }

    private fun upsertItems(db: SupportSQLiteDatabase, items: List<SeedItem>, categoryIds: Map<String, String>, now: Long) {
        val unitCodes = db.query("SELECT code FROM unit_def").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        items.forEach { item ->
            // An item whose category is not in the catalog has nowhere to live; skip it.
            val categoryId = categoryIds[item.category] ?: return@forEach
            // A unit missing from the catalog would violate the foreign key; fall back to none.
            val unit = item.defaultUnit?.takeIf { it in unitCodes }
            val existing = db.query(
                """
                SELECT id, category_id, default_unit_code, custom_name, is_hidden
                FROM master_item WHERE canonical_key = ? AND is_custom = 0
                """.trimIndent(),
                arrayOf<Any?>(item.key),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val signature = "${cursor.getString(1)}|${cursor.getStringOrNull(2)}"
                    ExistingRow(cursor.getString(0), cursor.isUserOwned(customName = 3, hidden = 4), signature)
                } else {
                    null
                }
            }
            when {
                existing == null -> db.execSQL(
                    """
                    INSERT INTO master_item (id, category_id, canonical_key, custom_name, custom_name_locale,
                      default_unit_code, is_custom, is_hidden, use_count, created_at, updated_at)
                    VALUES (?, ?, ?, NULL, NULL, ?, 0, 0, 0, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(ids.newId(), categoryId, item.key, unit, now, now),
                )
                !existing.userOwned && existing.signature != "$categoryId|$unit" -> db.execSQL(
                    "UPDATE master_item SET category_id = ?, default_unit_code = ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any?>(categoryId, unit, now, existing.id),
                )
            }
        }
    }

    private fun upsertTranslations(db: SupportSQLiteDatabase, catalog: SeedCatalog, translations: List<SeedTranslations>) {
        val categoryKeys = catalog.categories.mapTo(HashSet()) { it.key }
        val itemKeys = catalog.items.mapTo(HashSet()) { it.key }
        translations.forEach { file ->
            val locale = file.locale.trim().lowercase()
            file.categories.filterKeys { it in categoryKeys }.forEach { (key, name) ->
                db.execSQL(
                    "INSERT OR REPLACE INTO category_translation (canonical_key, locale, name) VALUES (?, ?, ?)",
                    arrayOf<Any?>(key, locale, name.trim()),
                )
            }
            file.items.filterKeys { it in itemKeys }.forEach { (key, value) ->
                val aliases = value.aliases.map { it.trim() }.filter { it.isNotEmpty() }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(MasterItemTranslationEntity.ALIAS_SEPARATOR)
                db.execSQL(
                    "INSERT OR REPLACE INTO master_item_translation (canonical_key, locale, name, aliases) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(key, locale, value.name.trim(), aliases),
                )
            }
        }
    }

    /** Recreates every index row, so custom items and removed translations are always consistent. */
    private fun rebuildSearchIndex(db: SupportSQLiteDatabase) {
        val translationsByKey = db.query("SELECT canonical_key, locale, name, aliases FROM master_item_translation").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MasterItemTranslationEntity(
                            canonicalKey = cursor.getString(0),
                            locale = cursor.getString(1),
                            name = cursor.getString(2),
                            aliases = cursor.getStringOrNull(3),
                        ),
                    )
                }
            }
        }.groupBy { it.canonicalKey }

        db.execSQL("DELETE FROM item_search_fts")
        db.query("SELECT id, category_id, canonical_key, custom_name, custom_name_locale FROM master_item").use { cursor ->
            while (cursor.moveToNext()) {
                val rows = SearchText.rowsFor(
                    masterItemId = cursor.getString(0),
                    categoryId = cursor.getString(1),
                    customName = cursor.getStringOrNull(3),
                    customNameLocale = cursor.getStringOrNull(4),
                    translations = translationsByKey[cursor.getString(2)].orEmpty(),
                )
                rows.forEach { row ->
                    db.execSQL(
                        "INSERT INTO item_search_fts (ref_type, ref_id, category_id, locale, text) VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any?>(row.refType, row.refId, row.categoryId, row.locale, row.text),
                    )
                }
            }
        }
    }

    /** [signature] holds the seed-owned values, to skip no-op updates. */
    private class ExistingRow(val id: String, val userOwned: Boolean, val signature: String)

    private fun Cursor.isUserOwned(customName: Int, hidden: Int): Boolean = !isNull(customName) || getInt(hidden) != 0

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun Boolean.toInt(): Int = if (this) 1 else 0
}

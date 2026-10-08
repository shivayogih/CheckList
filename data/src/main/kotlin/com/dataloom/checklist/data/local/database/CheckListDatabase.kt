package com.dataloom.checklist.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dataloom.checklist.data.local.dao.CategoryDao
import com.dataloom.checklist.data.local.dao.ChecklistDao
import com.dataloom.checklist.data.local.dao.ChecklistItemDao
import com.dataloom.checklist.data.local.dao.ItemPhotoDao
import com.dataloom.checklist.data.local.dao.MasterItemDao
import com.dataloom.checklist.data.local.dao.ProfileDao
import com.dataloom.checklist.data.local.dao.SearchIndexDao
import com.dataloom.checklist.data.local.dao.SectionDao
import com.dataloom.checklist.data.local.dao.SeedMetaDao
import com.dataloom.checklist.data.local.dao.UnitDao
import com.dataloom.checklist.data.local.entity.CategoryEntity
import com.dataloom.checklist.data.local.entity.CategoryTranslationEntity
import com.dataloom.checklist.data.local.entity.ChecklistCategoryEntity
import com.dataloom.checklist.data.local.entity.ChecklistEntity
import com.dataloom.checklist.data.local.entity.ChecklistItemEntity
import com.dataloom.checklist.data.local.entity.ItemPhotoEntity
import com.dataloom.checklist.data.local.entity.ItemSearchFtsEntity
import com.dataloom.checklist.data.local.entity.MasterItemEntity
import com.dataloom.checklist.data.local.entity.MasterItemTranslationEntity
import com.dataloom.checklist.data.local.entity.SeedMetaEntity
import com.dataloom.checklist.data.local.entity.UnitDefEntity
import com.dataloom.checklist.data.local.entity.UserProfileEntity
import com.dataloom.checklist.data.seed.SeedLoader

/**
 * The app's single source of truth (ADR-002). Schemas are exported to data/schemas and committed;
 * every version change ships a tested migration and never falls back to a destructive one.
 */
@Database(
    entities = [
        ChecklistEntity::class,
        CategoryEntity::class,
        CategoryTranslationEntity::class,
        MasterItemEntity::class,
        MasterItemTranslationEntity::class,
        UnitDefEntity::class,
        ChecklistCategoryEntity::class,
        ChecklistItemEntity::class,
        UserProfileEntity::class,
        ItemSearchFtsEntity::class,
        SeedMetaEntity::class,
        ItemPhotoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CheckListDatabase : RoomDatabase() {

    abstract fun checklistDao(): ChecklistDao

    abstract fun sectionDao(): SectionDao

    abstract fun checklistItemDao(): ChecklistItemDao

    abstract fun categoryDao(): CategoryDao

    abstract fun masterItemDao(): MasterItemDao

    abstract fun searchIndexDao(): SearchIndexDao

    abstract fun unitDao(): UnitDao

    abstract fun seedMetaDao(): SeedMetaDao

    /** Encrypted profile row (Phase 5). Adding a DAO does not change the schema. */
    abstract fun profileDao(): ProfileDao

    /** Photos of checklist items (schema v2, CL-210). */
    abstract fun itemPhotoDao(): ItemPhotoDao

    companion object {
        const val NAME = "checklist.db"

        fun build(context: Context, seedLoader: SeedLoader): CheckListDatabase =
            Room.databaseBuilder(context, CheckListDatabase::class.java, NAME)
                .addMigrations(*DatabaseMigrations.ALL)
                .addCallback(CheckListDatabaseCallback(seedLoader))
                .build()
    }
}

/**
 * Creates what Room cannot declare (the category name check) and applies the bundled seed
 * catalog: in full on creation, and on open when the app ships a newer seed version. Both run
 * synchronously on the opening connection, so the first query already sees the catalog.
 */
internal class CheckListDatabaseCallback(private val seedLoader: SeedLoader?) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        SchemaTriggers.create(db)
        seedLoader?.seedIfNeeded(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        seedLoader?.seedIfNeeded(db)
    }
}

/** Stand-ins for CHECK constraints, which Room entities cannot express. */
internal object SchemaTriggers {

    private val statements = listOf("INSERT", "UPDATE").map { event ->
        """
        CREATE TRIGGER IF NOT EXISTS category_requires_name_${event.lowercase()}
        BEFORE $event ON category
        WHEN NEW.canonical_key IS NULL AND NEW.custom_name IS NULL
        BEGIN SELECT RAISE(ABORT, 'category needs canonical_key or custom_name'); END
        """.trimIndent()
    }

    fun create(db: SupportSQLiteDatabase) {
        statements.forEach { db.execSQL(it) }
    }
}

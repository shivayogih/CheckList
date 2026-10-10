package com.dataloom.checklist.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, oldest first. A destructive fallback is never used (ADR-002); each new
 * migration gets its data in `MigrationFixtures` and is exercised by `MigrationStepTest` (data/src/test).
 * Rules, the runtime backup/restore and the checklist for adding a step: docs/db-migrations.md.
 *
 * Version 1 to 2 (CL-210): adds item_photo and its index, nothing else. No row of any existing table
 * is read, rewritten or deleted. The SQL is the one in the exported schema data/schemas/.../2.json,
 * and ItemPhotoMigrationTest runs it against a version 1 database that contains data.
 */
object DatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `item_photo` (`id` TEXT NOT NULL, `checklist_item_id` TEXT NOT NULL, " +
                    "`file_name` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, " +
                    "`byte_size` INTEGER NOT NULL, `position` INTEGER NOT NULL, `caption` TEXT, " +
                    "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`checklist_item_id`) REFERENCES `checklist_item`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_item_photo_checklist_item_id_position` " +
                    "ON `item_photo` (`checklist_item_id`, `position`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

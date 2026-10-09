package com.dataloom.checklist.data.local.database

import androidx.room.migration.Migration

/**
 * Every schema migration, oldest first. Version 1 is the first schema, so the list is empty.
 *
 * To ship schema v2: bump `version` in [CheckListDatabase], commit the new exported schema, add
 * `MIGRATION_1_2` here, and add its data checks to `DatabaseMigrationTest` (data/src/test). That
 * test already migrates every committed schema to the latest version with [ALL] and validates the
 * result, so a missing migration fails CI. A destructive fallback is never used (ADR-002).
 */
object DatabaseMigrations {
    val ALL: Array<Migration> = emptyArray()
}

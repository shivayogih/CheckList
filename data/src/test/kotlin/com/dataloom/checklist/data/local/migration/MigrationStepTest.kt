package com.dataloom.checklist.data.local.migration

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.database.CheckListDatabaseCallback
import com.dataloom.checklist.data.local.database.DatabaseMigrations
import com.dataloom.checklist.data.seed.AssetSeedSource
import com.dataloom.checklist.data.seed.SeedLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every schema version step N to N+1, and the whole chain from version 1 (CL-320, docs/db-migrations.md).
 *
 * Each test builds a database from the committed schema JSON, fills it with [MigrationFixtures] data,
 * migrates it, and checks what a user cares about: nothing lost, nothing broken (integrity and foreign
 * keys), and the structure equal to the exported schema and to a fresh install.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationStepTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CheckListDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        listOf(STEP_DB, CHAIN_DB, FRESH_DB, OPEN_DB).forEach { context.deleteDatabase(it) }
    }

    @Test
    fun `every released version has fixture data`() {
        (1..LATEST).forEach { version ->
            helper.createDatabase(STEP_DB, version).use { MigrationFixtures.populate(version, it) }
            context.deleteDatabase(STEP_DB)
        }
    }

    @Test
    fun `every step N to N+1 keeps all data, passes integrity and foreign key checks and matches the schema JSON`() {
        (1 until LATEST).forEach { from ->
            val to = from + 1
            val migration = stepMigration(from)
            lateinit var before: Map<String, Pair<List<String>, List<List<String?>>>>
            helper.createDatabase(STEP_DB, from).use {
                MigrationFixtures.populate(from, it)
                assertEquals("fixture v$from", listOf("ok"), it.integrityCheck())
                before = it.snapshot()
            }

            // validateDroppedTables = true: a table the new schema does not declare must be dropped by the migration.
            helper.runMigrationsAndValidate(STEP_DB, to, true, migration).use { db ->
                assertEquals("v$from -> v$to integrity", listOf("ok"), db.integrityCheck())
                assertEquals("v$from -> v$to foreign keys", emptyList<String>(), db.foreignKeyViolations())
                assertRowsKept(from, to, before, db)
            }
            context.deleteDatabase(STEP_DB)
        }
    }

    @Test
    fun `the full chain from version 1 to the latest keeps every row and equals the exported schema`() {
        lateinit var before: Map<String, Pair<List<String>, List<List<String?>>>>
        helper.createDatabase(CHAIN_DB, 1).use {
            MigrationFixtures.populate(1, it)
            before = it.snapshot()
        }

        helper.runMigrationsAndValidate(CHAIN_DB, LATEST, true, *DatabaseMigrations.ALL).use { db ->
            assertEquals(LATEST, db.version)
            assertEquals(listOf("ok"), db.integrityCheck())
            assertEquals(emptyList<String>(), db.foreignKeyViolations())
            assertRowsKept(1, LATEST, before, db)
            // The encrypted profile payload is copied bit for bit.
            db.query("SELECT enc_payload FROM user_profile WHERE id = 'me'").use {
                assertTrue(it.moveToFirst())
                assertTrue(MigrationFixtures.profileBlob.contentEquals(it.getBlob(0)))
            }
            // The FTS index still answers queries.
            assertEquals(
                listOf("mi-1"),
                db.strings("SELECT ref_id FROM item_search_fts WHERE text MATCH 'chawal' AND locale = 'en'"),
            )
        }
    }

    @Test
    fun `a migrated database has the same structure as a fresh install`() {
        helper.createDatabase(CHAIN_DB, 1).use { MigrationFixtures.populate(1, it) }
        val migrated = helper.runMigrationsAndValidate(CHAIN_DB, LATEST, true, *DatabaseMigrations.ALL)
            .use { it.schemaDescription() }

        val fresh = Room.databaseBuilder(context, CheckListDatabase::class.java, FRESH_DB)
            .addCallback(CheckListDatabaseCallback(seedLoader = null))
            .allowMainThreadQueries()
            .build()
        val installed = try {
            fresh.openHelper.writableDatabase.schemaDescription()
        } finally {
            fresh.close()
        }

        assertEquals(installed, migrated)
    }

    @Test
    fun `the app opens a migrated database and applies the catalogue without touching user data`() {
        helper.createDatabase(OPEN_DB, 1).use { MigrationFixtures.populate(1, it) }
        lateinit var before: Map<String, Pair<List<String>, List<List<String?>>>>
        helper.runMigrationsAndValidate(OPEN_DB, LATEST, true, *DatabaseMigrations.ALL).use { before = it.snapshot() }

        val loader = SeedLoader(AssetSeedSource(context), FakeClock(), SequentialIds("seed"))
        val room = Room.databaseBuilder(context, CheckListDatabase::class.java, OPEN_DB)
            .addMigrations(*DatabaseMigrations.ALL)
            .addCallback(CheckListDatabaseCallback(loader))
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase
            val after = db.snapshot()

            // The user's own tables are byte for byte what the migration produced.
            listOf("checklist", "checklist_category", "checklist_item", "user_profile").forEach { table ->
                assertEquals(table, before.getValue(table), after.getValue(table))
            }
            // A custom category and item are not catalogue rows and are not touched.
            assertEquals(
                before.getValue("category").second.single { it[0] == "cat-3" },
                after.getValue("category").second.single { it[0] == "cat-3" },
            )
            assertEquals(
                before.getValue("master_item").second.single { it[0] == "mi-3" },
                after.getValue("master_item").second.single { it[0] == "mi-3" },
            )
            // The catalogue arrived, and applying it again changes nothing.
            assertTrue(db.count("master_item") > 3)
            assertTrue(
                db.strings("SELECT name FROM sqlite_master WHERE type = 'trigger'")
                    .containsAll(listOf("category_requires_name_insert", "category_requires_name_update")),
            )
            val masterItems = db.count("master_item")
            val searchRows = db.count("item_search_fts")
            db.execSQL("DELETE FROM seed_meta")
            assertTrue(loader.seedIfNeeded(db))
            assertEquals(masterItems, db.count("master_item"))
            assertEquals(searchRows, db.count("item_search_fts"))
            assertEquals(listOf("ok"), db.integrityCheck())
            assertEquals(emptyList<String>(), db.foreignKeyViolations())
        } finally {
            room.close()
        }
    }

    @Test
    fun `opening the database puts back a schema trigger that a table rebuild dropped`() {
        val room = Room.databaseBuilder(context, CheckListDatabase::class.java, OPEN_DB)
            .addCallback(CheckListDatabaseCallback(seedLoader = null))
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase
            db.execSQL("DROP TRIGGER category_requires_name_insert")
            db.execSQL("DROP TRIGGER category_requires_name_update")
        } finally {
            room.close()
        }

        val reopened = Room.databaseBuilder(context, CheckListDatabase::class.java, OPEN_DB)
            .addCallback(CheckListDatabaseCallback(seedLoader = null))
            .allowMainThreadQueries()
            .build()
        try {
            val triggers = reopened.openHelper.writableDatabase
                .strings("SELECT name FROM sqlite_master WHERE type = 'trigger'")
            assertEquals(2, triggers.size)
        } finally {
            reopened.close()
        }
    }

    private fun stepMigration(from: Int): Migration {
        val matches = DatabaseMigrations.ALL.filter { it.startVersion == from && it.endVersion == from + 1 }
        assertEquals("Exactly one migration from $from to ${from + 1} is required", 1, matches.size)
        return matches.single()
    }

    /**
     * Every table of the old version must still exist with all its old columns and rows. A migration
     * that intentionally rewrites or drops something must say so in [intentionalChanges] with the
     * reason; there is none yet, the schema has only grown.
     */
    private fun assertRowsKept(
        from: Int,
        to: Int,
        before: Map<String, Pair<List<String>, List<List<String?>>>>,
        db: SupportSQLiteDatabase,
    ) {
        before.forEach { (table, old) ->
            if ((from to table) in intentionalChanges.keys) return@forEach
            val (columns, oldRows) = old
            assertTrue("v$from -> v$to dropped table $table", table in db.userTables())
            val kept = db.columnNames(table)
            assertTrue("v$from -> v$to removed columns of $table: ${columns - kept.toSet()}", kept.containsAll(columns))
            val expected = oldRows.map(::flatten).sorted()
            val actual = db.rows(table, columns).map(::flatten).sorted()
            assertEquals("v$from -> v$to changed rows of $table", expected, actual)
        }
    }

    private fun flatten(row: List<String?>): String = row.joinToString("\u0000") { it ?: "\u0001null" }

    private companion object {
        const val STEP_DB = "migration-step-test.db"
        const val CHAIN_DB = "migration-chain-test.db"
        const val FRESH_DB = "migration-fresh-test.db"
        const val OPEN_DB = "migration-open-test.db"
        const val LATEST = CheckListDatabase.VERSION

        /** (from version, table) to the reason a migration changes that table's rows on purpose. Empty so far. */
        val intentionalChanges: Map<Pair<Int, String>, String> = emptyMap()
    }
}

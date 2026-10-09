package com.dataloom.checklist.data.local.migration

import androidx.room.migration.Migration
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.database.DatabaseMigrations
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of docs/db-migrations.md that can be checked without a database. Plain JVM, so it also
 * fails fast on a machine without Robolectric. The same rules run as a CI gate in
 * tools/checks/schema_gate.py, which additionally compares with the base branch.
 */
class DatabaseMigrationPolicyTest {

    // Gradle runs unit tests with the module directory (data/) as the working directory.
    private val repoRoot = File("..").canonicalFile
    private val schemaDir = File("schemas/${CheckListDatabase::class.java.name}")

    private companion object {
        const val DATABASE_SOURCES = "src/main/kotlin/com/dataloom/checklist/data/local/database"
    }

    private val forbidden = listOf(
        "fallbackToDestructiveMigration",
        "fallbackToDestructiveMigrationFrom",
        "fallbackToDestructiveMigrationOnDowngrade",
        "allowDataLossOnRecovery(true",
        "deleteDatabase(",
        "deleteDatabaseFile",
    )

    @Test
    fun `no production source can wipe the database`() {
        val offenders = listOf("app", "data", "domain", "ai")
            .map { File(repoRoot, "$it/src/main") }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList() }
            .flatMap { source ->
                val code = withoutComments(source.readText())
                forbidden.filter { it in code }.map { "${source.relativeTo(repoRoot)}: $it" }
            }
        assertEquals("Destructive database calls are banned (docs/db-migrations.md)", emptyList<String>(), offenders)
    }

    @Test
    fun `migrations never delete or drop data unless the line says why`() {
        val source = File("$DATABASE_SOURCES/DatabaseMigrations.kt").readLines()
        val risky = Regex("""(?i)\b(DELETE\s+FROM|DROP\s+TABLE|DROP\s+COLUMN|TRUNCATE)\b""")
        val offenders = source.withIndex().filter { (_, line) ->
            !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//") &&
                risky.containsMatchIn(line) && "data-safe:" !in line
        }.map { "line ${it.index + 1}: ${it.value.trim()}" }
        assertEquals("Add a '// data-safe: <why no data is lost>' comment or remove it", emptyList<String>(), offenders)
    }

    @Test
    fun `the declared version, the committed schemas and the migrations agree`() {
        val committed = schemaDir.listFiles().orEmpty()
            .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()
        assertEquals((1..CheckListDatabase.VERSION).toList(), committed)

        val declared = Regex("""version\s*=\s*(\d+)""").find(
            File("$DATABASE_SOURCES/CheckListDatabase.kt").readText(),
        )?.groupValues?.get(1)?.toInt()
        assertEquals("@Database(version) must equal CheckListDatabase.VERSION", CheckListDatabase.VERSION, declared)

        val steps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals((1 until CheckListDatabase.VERSION).map { it to it + 1 }, steps.sortedBy { it.first })
        assertEquals("duplicate migrations", steps.size, steps.toSet().size)
    }

    @Test
    fun `every exported schema file names its own version`() {
        schemaDir.listFiles().orEmpty().filter { it.extension == "json" }.forEach { file ->
            val version = Regex(""""version"\s*:\s*(\d+)""").find(file.readText())?.groupValues?.get(1)?.toInt()
            assertEquals("${file.name} declares another version", file.name.removeSuffix(".json").toInt(), version)
        }
    }

    @Test
    fun `the migration list is typed and ordered oldest first`() {
        val all: Array<Migration> = DatabaseMigrations.ALL
        assertTrue(all.zipWithNext().all { (a, b) -> a.endVersion == b.startVersion })
    }

    /** Drops line and block comments so that documentation may mention the banned names. */
    private fun withoutComments(text: String): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), " ").lines().joinToString("\n") { it.substringBefore("//") }
}

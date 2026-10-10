package com.dataloom.checklist.data.local.database

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The private copy of the database taken just before a version upgrade (CL-320, docs/db-migrations.md).
 *
 * One copy is kept: the last one that was taken from a healthy file. It lives in [directory], which
 * the app places in the no-backup folder (the database itself is already in Auto Backup, a second
 * copy would only waste quota). Writes go to a temporary file first and are moved into place, so a
 * crash never leaves a half-written copy under the real name, and the previous good copy is only
 * replaced once the new one is complete.
 */
internal class PreMigrationBackup(private val database: File, private val directory: File) {

    private val copy = File(directory, "pre-migration.db")
    private val versionFile = File(directory, "pre-migration.version")

    /** The schema version stored in the database file, or null when there is no file or it cannot be read. */
    fun storedVersion(): Int? {
        if (!database.isFile || database.length() == 0L) return null
        return try {
            withConnection { it.version }
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") unreadable: RuntimeException) {
            null
        }
    }

    /**
     * Copies the database (after folding its write-ahead log into the main file) as the new
     * pre-migration copy. Returns false and keeps the previous copy when the source fails its own
     * integrity check or the copy cannot be written.
     */
    fun create(version: Int): Boolean = try {
        val healthy = withConnection { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        }
        if (healthy) {
            directory.mkdirs()
            replace(database, copy)
            replaceText(versionFile, version.toString())
        }
        healthy
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception) {
        false
    }

    /** Version of the stored copy, or null when there is none. */
    fun backedUpVersion(): Int? =
        if (copy.isFile) versionFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() else null

    /**
     * Puts the copy back in place of the database. The copy is first written next to the database
     * and only then moved over it, so a failure half way leaves the current file untouched.
     * The copy itself is kept.
     */
    fun restore(): Boolean = try {
        if (!copy.isFile) {
            false
        } else {
            val staged = File(database.parentFile, database.name + ".restore")
            copy.copyTo(staged, overwrite = true)
            listOf("-wal", "-shm", "-journal").forEach { File(database.path + it).delete() }
            Files.move(staged.toPath(), database.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        }
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception) {
        false
    }

    private fun <T> withConnection(block: (SQLiteDatabase) -> T): T {
        val db = SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            // The default handler deletes a database it finds damaged; here that must never happen.
            DatabaseErrorHandler { },
        )
        try {
            return block(db)
        } finally {
            db.close()
        }
    }

    @Throws(IOException::class)
    private fun replace(source: File, target: File) {
        val staged = File(target.parentFile, target.name + ".tmp")
        FileInputStream(source).use { input ->
            FileOutputStream(staged).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    private fun replaceText(target: File, text: String) {
        val staged = File(target.parentFile, target.name + ".tmp")
        staged.writeText(text)
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

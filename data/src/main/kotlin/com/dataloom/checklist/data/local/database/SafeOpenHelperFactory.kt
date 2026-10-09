package com.dataloom.checklist.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.dataloom.checklist.domain.common.AppLog
import java.io.File

/**
 * Makes opening the database safe across app updates (CL-320, docs/db-migrations.md).
 *
 * On the first open of a process, before Room sees the file:
 * 1. a file written by a newer app version is left alone and reported as [DatabaseFailure.Downgrade];
 * 2. a file of an older version is copied to the private pre-migration backup;
 * 3. Room then migrates it. SQLite's open helper runs the whole upgrade in one transaction, so a
 *    failing step rolls back and leaves the data as it was;
 * 4. if the open fails after a backup was taken, the backup is put back and the failure is reported
 *    as [DatabaseFailure.MigrationFailed]. Nothing is ever deleted to recover.
 *
 * A failure is remembered: later calls in the same process throw the same [DatabaseOpenException]
 * at once instead of retrying and restoring again, so the app can show one recovery state instead of
 * crash-looping. The next launch (for example after an app fix) tries again from the restored file.
 */
internal class SafeOpenHelperFactory(
    private val databaseFile: File,
    backupDirectory: File,
    private val currentVersion: Int,
    private val health: DatabaseHealth,
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {

    private val backup = PreMigrationBackup(databaseFile, backupDirectory)
    private val lock = Any()
    private var opened = false
    private var failure: DatabaseOpenException? = null

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val keepCorruptFile = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name)
            .callback(KeepFileOnCorruption(configuration.callback))
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
            .build()
        return SafeOpenHelper(delegate.create(keepCorruptFile)) { open(it) }
    }

    private fun open(helper: SupportSQLiteOpenHelper) {
        synchronized(lock) {
            failure?.let { throw it }
            if (opened) return
            val stored = backup.storedVersion()
            if (stored != null && stored > currentVersion) {
                fail(DatabaseFailure.Downgrade(stored, currentVersion), null)
            }
            val from = stored?.takeIf { it in 1 until currentVersion }
            val backedUp = from != null && backup.create(from)
            if (from != null && !backedUp) {
                AppLog.w(TAG) { "No pre-migration backup of v$from; migrating inside its transaction only" }
            }
            try {
                helper.writableDatabase
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                val restored = backedUp && closeQuietly(helper) && backup.restore()
                val reason = if (from != null) {
                    DatabaseFailure.MigrationFailed(from, currentVersion, restored)
                } else {
                    DatabaseFailure.OpenFailed
                }
                fail(reason, error)
            }
            opened = true
            health.report(DatabaseState.Ready(from))
            if (from != null) AppLog.i(TAG) { "Database migrated from v$from to v$currentVersion" }
        }
    }

    private fun fail(reason: DatabaseFailure, cause: Throwable?): Nothing {
        AppLog.e(TAG, cause) { "Database open failed: $reason" }
        val exception = DatabaseOpenException(reason, cause)
        failure = exception
        health.report(DatabaseState.Failed(reason))
        throw exception
    }

    private fun closeQuietly(helper: SupportSQLiteOpenHelper): Boolean = try {
        helper.close()
        true
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception) {
        false
    }

    private companion object {
        const val TAG = "DbMigration"
    }
}

/** Forwards to the framework helper, but runs [guard] before the first database is handed out. */
private class SafeOpenHelper(
    private val delegate: SupportSQLiteOpenHelper,
    private val guard: (SupportSQLiteOpenHelper) -> Unit,
) : SupportSQLiteOpenHelper by delegate {

    override val writableDatabase: SupportSQLiteDatabase
        get() {
            guard(delegate)
            return delegate.writableDatabase
        }

    override val readableDatabase: SupportSQLiteDatabase
        get() {
            guard(delegate)
            return delegate.readableDatabase
        }
}

/**
 * Forwards everything to Room's callback except corruption handling: the default reaction of both the
 * framework and [SupportSQLiteOpenHelper.Callback] to a damaged file is to delete it, which would turn
 * one bad open into permanent data loss. Here the file is left alone and the open fails with
 * [DatabaseFailure.OpenFailed].
 */
private class KeepFileOnCorruption(private val inner: SupportSQLiteOpenHelper.Callback) :
    SupportSQLiteOpenHelper.Callback(inner.version) {

    override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)

    override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        inner.onUpgrade(db, oldVersion, newVersion)

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        inner.onDowngrade(db, oldVersion, newVersion)

    override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)

    override fun onCorruption(db: SupportSQLiteDatabase) {
        AppLog.e("DbMigration") { "The database file is damaged; it is kept as it is" }
    }
}

package com.dataloom.checklist.data.local.database

import dagger.Lazy
import javax.inject.Inject

/**
 * Forces the first database open (schema check, and the seed import on a fresh install or newer seed)
 * without exposing Room to :app. Blocking: call it from an I/O dispatcher. The database is a [Lazy]
 * so injecting this class never builds it on the calling thread.
 */
class DatabaseOpener @Inject constructor(private val database: Lazy<CheckListDatabase>) {
    fun open() {
        database.get().openHelper.writableDatabase
    }
}

package com.dataloom.checklist.data.local.database

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the database could not be opened. Never carries user data, only versions and a flag (CL-320). */
sealed interface DatabaseFailure {

    /**
     * A version step failed. Its transaction was rolled back, so the data is as it was before the
     * update; [restoredBackup] says the pre-migration copy was also put back in place.
     */
    data class MigrationFailed(val fromVersion: Int, val toVersion: Int, val restoredBackup: Boolean) : DatabaseFailure

    /** The file was written by a newer app version. It is left untouched; install the newer version again. */
    data class Downgrade(val foundVersion: Int, val supportedVersion: Int) : DatabaseFailure

    /** Opening failed for another reason (damaged file, storage error). Nothing was deleted or replaced. */
    data object OpenFailed : DatabaseFailure
}

/** What the database layer reports about the last open attempt of this process. */
sealed interface DatabaseState {

    /** Not opened yet in this process. */
    data object Unopened : DatabaseState

    /** Opened. [migratedFrom] is the previous version when this launch upgraded the file, else null. */
    data class Ready(val migratedFrom: Int?) : DatabaseState

    data class Failed(val failure: DatabaseFailure) : DatabaseState
}

/** Thrown by every database access after [DatabaseState.Failed]; catch it to show a recovery screen instead of crashing. */
class DatabaseOpenException(val failure: DatabaseFailure, cause: Throwable? = null) :
    IllegalStateException("The database could not be opened: $failure", cause)

/** The observable result of the safe open. One per process (provided as a singleton). */
@Singleton
class DatabaseHealth @Inject constructor() {

    private val mutableState = MutableStateFlow<DatabaseState>(DatabaseState.Unopened)

    val state: StateFlow<DatabaseState> = mutableState.asStateFlow()

    internal fun report(state: DatabaseState) {
        mutableState.value = state
    }
}

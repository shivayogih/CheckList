package com.dataloom.checklist.startup

import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.domain.common.IoDispatcher
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the database in the background at launch. Room opens lazily on the first query, and that
 * first open runs the seed importer (parsing the catalog and writing about a hundred items with
 * seven languages of names). Doing it from `Application.onCreate` overlaps that cost with the
 * first-run flag read and the first frame instead of making the first screen wait for it.
 *
 * The database is a [Lazy]: injecting this class must not build it on the main thread. A failure is
 * dropped here on purpose (it must never crash the launch): the first real query hits the same error
 * and reports it to the screen that needs the data.
 */
@Singleton
class DatabaseWarmUp @Inject constructor(
    private val database: Lazy<CheckListDatabase>,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun start() {
        scope.launch {
            try {
                withContext(io) { database.get().openHelper.writableDatabase }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception) {
                // Intentionally ignored, see above.
            }
        }
    }
}

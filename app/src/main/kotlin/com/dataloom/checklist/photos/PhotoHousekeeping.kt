package com.dataloom.checklist.photos

import android.content.Context
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.domain.common.AppLog
import com.dataloom.checklist.domain.usecase.SweepOrphanPhotosUseCase
import com.dataloom.checklist.presentation.photos.clearCameraCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background clean-up at app start so a crash can never leak storage: photo files without a
 * database row (older than an hour) and stale import scratch files are deleted, and a camera
 * picture left by an interrupted capture is removed from the cache.
 */
@Singleton
class PhotoHousekeeping @Inject constructor(
    private val sweepOrphanPhotos: SweepOrphanPhotosUseCase,
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            withContext(Dispatchers.IO) { clearCameraCache(context) }
            val deleted = sweepOrphanPhotos()
            if (deleted > 0) AppLog.d(TAG) { "Deleted $deleted orphan photo files" }
        }
    }

    private companion object {
        const val TAG = "PhotoHousekeeping"
    }
}

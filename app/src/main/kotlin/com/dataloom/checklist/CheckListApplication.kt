package com.dataloom.checklist

import android.app.Application
import com.dataloom.checklist.domain.common.AppLog
import com.dataloom.checklist.logging.AndroidLogSink
import com.dataloom.checklist.photos.PhotoHousekeeping
import com.dataloom.checklist.startup.DatabaseWarmUp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hosts the app-wide Hilt component (database, repositories).
 *
 * Start-up does as little as possible on the main thread: nothing here builds the database, opens a
 * file or touches the Keystore. The one piece of start-up work, [DatabaseWarmUp], runs on a
 * background thread.
 */
@HiltAndroidApp
class CheckListApplication : Application() {

    @Inject
    lateinit var photoHousekeeping: PhotoHousekeeping

    @Inject
    lateinit var databaseWarmUp: DatabaseWarmUp

    override fun onCreate() {
        super.onCreate()
        // Release-safe logging (design 19.3): only debug builds get a sink; release builds log nothing.
        if (BuildConfig.DEBUG) AppLog.install(AndroidLogSink)
        photoHousekeeping.start()
        databaseWarmUp.start()
    }
}

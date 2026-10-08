package com.dataloom.checklist

import android.app.Application
import com.dataloom.checklist.domain.common.AppLog
import com.dataloom.checklist.logging.AndroidLogSink
import dagger.hilt.android.HiltAndroidApp

/** Hosts the app-wide Hilt component (database, repositories). */
@HiltAndroidApp
class CheckListApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Release-safe logging (design 19.3): only debug builds get a sink; release builds log nothing.
        if (BuildConfig.DEBUG) AppLog.install(AndroidLogSink)
    }
}

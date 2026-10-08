package com.dataloom.checklist.logging

import android.util.Log
import com.dataloom.checklist.domain.common.AppLog

/**
 * Sends [AppLog] lines to logcat. The only file allowed to use `android.util.Log` (detekt
 * `ForbiddenImport` and `tools/checks/logging_ban.py` exclude it by name).
 *
 * Installed by `CheckListApplication` in debug builds only, so release builds never log.
 */
object AndroidLogSink : AppLog.Sink {

    override fun log(level: AppLog.Level, tag: String, message: String, throwable: Throwable?) {
        val priority = when (level) {
            AppLog.Level.DEBUG -> Log.DEBUG
            AppLog.Level.INFO -> Log.INFO
            AppLog.Level.WARN -> Log.WARN
            AppLog.Level.ERROR -> Log.ERROR
        }
        val text = if (throwable == null) message else message + "\n" + Log.getStackTraceString(throwable)
        Log.println(priority, tag, text)
    }
}

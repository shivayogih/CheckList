package com.dataloom.checklist.domain.common

/**
 * The one logging entry point for every module (design section 19.3, CL-183).
 *
 * It does nothing until a [Sink] is installed, and only debug builds install one
 * (`CheckListApplication` installs the Android sink when `BuildConfig.DEBUG` is true). In release
 * builds no sink exists, so log calls cost one null check and the message lambda never runs:
 * nothing reaches logcat, not even by mistake.
 *
 * Messages are lambdas so that building the text (string templates, `toString()` calls) also only
 * happens when logging is on. Never log personal data (profile fields, checklist contents, AI
 * prompts or responses) even in debug builds: debug logs end up in bug reports.
 *
 * Direct `android.util.Log`, `println`, `print` and `System.out`/`System.err` are banned in main
 * sources by detekt (`ForbiddenImport`) and `tools/checks/logging_ban.py`.
 *
 * Added in CL-183 (additive; no existing contract changed).
 */
object AppLog {

    /** Log levels, in increasing severity. */
    enum class Level { DEBUG, INFO, WARN, ERROR }

    /** Where log lines go. The Android implementation lives in `:app` and writes to logcat. */
    fun interface Sink {
        fun log(level: Level, tag: String, message: String, throwable: Throwable?)
    }

    @Volatile
    private var sink: Sink? = null

    /** True when a sink is installed (debug builds only). */
    val isEnabled: Boolean get() = sink != null

    /** Starts sending log lines to [sink]. Call only from debug builds. */
    fun install(sink: Sink) {
        this.sink = sink
    }

    /** Stops logging; later calls do nothing again. */
    fun uninstall() {
        sink = null
    }

    fun d(tag: String, message: () -> String) = log(Level.DEBUG, tag, null, message)

    fun i(tag: String, message: () -> String) = log(Level.INFO, tag, null, message)

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) = log(Level.WARN, tag, throwable, message)

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) = log(Level.ERROR, tag, throwable, message)

    private fun log(level: Level, tag: String, throwable: Throwable?, message: () -> String) {
        val target = sink ?: return
        target.log(level, tag, message(), throwable)
    }
}

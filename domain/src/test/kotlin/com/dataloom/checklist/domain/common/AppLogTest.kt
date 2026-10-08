package com.dataloom.checklist.domain.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogTest {

    private data class Line(val level: AppLog.Level, val tag: String, val message: String, val throwable: Throwable?)

    private val lines = mutableListOf<Line>()
    private val recordingSink = AppLog.Sink { level, tag, message, throwable ->
        lines += Line(level, tag, message, throwable)
    }

    @After
    fun tearDown() {
        AppLog.uninstall()
    }

    @Test
    fun `without a sink nothing is logged and the message is never built`() {
        var built = false

        AppLog.d("Tag") {
            built = true
            "debug"
        }
        AppLog.e("Tag", IllegalStateException()) {
            built = true
            "error"
        }

        assertFalse(AppLog.isEnabled)
        assertFalse(built)
    }

    @Test
    fun `an installed sink receives every level with tag, message and throwable`() {
        val failure = IllegalStateException("boom")
        AppLog.install(recordingSink)

        AppLog.d("A") { "one" }
        AppLog.i("B") { "two" }
        AppLog.w("C") { "three" }
        AppLog.e("D", failure) { "four" }

        assertTrue(AppLog.isEnabled)
        assertEquals(
            listOf(
                Line(AppLog.Level.DEBUG, "A", "one", null),
                Line(AppLog.Level.INFO, "B", "two", null),
                Line(AppLog.Level.WARN, "C", "three", null),
                Line(AppLog.Level.ERROR, "D", "four", failure),
            ),
            lines,
        )
        assertSame(failure, lines.last().throwable)
    }

    @Test
    fun `uninstall turns logging off again`() {
        AppLog.install(recordingSink)
        AppLog.uninstall()

        AppLog.i("Tag") { "ignored" }

        assertFalse(AppLog.isEnabled)
        assertTrue(lines.isEmpty())
    }
}

package com.dataloom.checklist.domain.common

import java.util.UUID

/** Injected so tests control time. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

/** Injected so tests get predictable IDs. */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val UUID_V4 = IdGenerator { UUID.randomUUID().toString() }
    }
}

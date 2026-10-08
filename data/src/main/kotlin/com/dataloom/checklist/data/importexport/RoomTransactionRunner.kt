package com.dataloom.checklist.data.importexport

import androidx.room.withTransaction
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.domain.transfer.TransactionRunner
import javax.inject.Inject

/**
 * One Room transaction around a multi-repository write such as an import. The repositories' own
 * `withTransaction` blocks join this outer transaction, so an exception anywhere inside rolls back
 * every row written by [inTransaction], including FTS rows of new master items.
 */
class RoomTransactionRunner @Inject constructor(private val db: CheckListDatabase) : TransactionRunner {

    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}

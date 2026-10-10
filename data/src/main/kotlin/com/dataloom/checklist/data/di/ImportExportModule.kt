package com.dataloom.checklist.data.di

import com.dataloom.checklist.data.importexport.JsonTransferCodec
import com.dataloom.checklist.data.importexport.RoomTransactionRunner
import com.dataloom.checklist.domain.transfer.TransactionRunner
import com.dataloom.checklist.domain.transfer.TransferCodec
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Import/export (Phase 6): the JSON codec and the Room transaction the importer writes in. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImportExportModule {

    @Binds
    abstract fun bindTransferCodec(impl: JsonTransferCodec): TransferCodec

    @Binds
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}

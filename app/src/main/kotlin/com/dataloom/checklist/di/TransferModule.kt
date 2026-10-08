package com.dataloom.checklist.di

import com.dataloom.checklist.transfer.ContentResolverDocumentAccess
import com.dataloom.checklist.transfer.DocumentAccess
import com.dataloom.checklist.transfer.FileSharer
import com.dataloom.checklist.transfer.SharesheetFileSharer
import com.dataloom.checklist.transfer.pdf.AndroidChecklistPdfWriter
import com.dataloom.checklist.transfer.pdf.ChecklistPdfWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Android side of import/export (Phase 6): documents, Sharesheet and PDF. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {

    @Binds
    abstract fun bindDocumentAccess(impl: ContentResolverDocumentAccess): DocumentAccess

    @Binds
    abstract fun bindFileSharer(impl: SharesheetFileSharer): FileSharer

    @Binds
    abstract fun bindChecklistPdfWriter(impl: AndroidChecklistPdfWriter): ChecklistPdfWriter
}

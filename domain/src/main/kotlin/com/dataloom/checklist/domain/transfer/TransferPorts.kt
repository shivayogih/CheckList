package com.dataloom.checklist.domain.transfer

import java.io.InputStream
import java.io.OutputStream

/**
 * Turns a [TransferDocument] into file bytes and back. Implemented in :data with
 * kotlinx.serialization (JsonTransferCodec).
 *
 * [decode] must enforce the parse-time limits of [TransferLimits] while it reads (element counts,
 * raw string lengths), reject versions it cannot read, upgrade older supported versions, and
 * return a document whose versions are the current ones. It never throws for bad input.
 */
interface TransferCodec {

    fun encode(document: TransferDocument): ByteArray

    fun decode(bytes: ByteArray): DecodeResult
}

sealed interface DecodeResult {
    data class Decoded(val document: TransferDocument) : DecodeResult

    data class Rejected(val rejection: ImportRejection) : DecodeResult
}

/** A file the user picked (Storage Access Framework in :app). */
interface ImportSource {
    /** Size reported by the provider, or null when unknown. Never trusted alone: reads are capped too. */
    val sizeBytes: Long?

    /** Opens a fresh stream; the caller closes it. May throw IOException or SecurityException. */
    fun openStream(): InputStream
}

/** Where an export is written (Storage Access Framework in :app). */
fun interface ExportSink {
    /** Opens the destination, truncating it; the caller closes it. */
    fun openStream(): OutputStream
}

/**
 * Runs [block] in one database transaction: if it throws, every write inside it is rolled back.
 * Repository methods called inside join the same transaction. Implemented on Room in :data.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/**
 * Hard limits for files the importer accepts and the exporter produces (section 20.2). The
 * exporter refuses to write a file the importer would refuse to read.
 */
object TransferLimits {
    const val MAX_FILE_BYTES: Long = 10L * 1024 * 1024
    const val MAX_CHECKLISTS = 500
    const val MAX_ITEMS = 20_000
    const val MAX_CATEGORIES = 1_000
    const val MAX_UNITS = 500
    const val MAX_SECTIONS_PER_CHECKLIST = 200
    const val MAX_SECTIONS = 20_000

    /**
     * Parse-time cap for any single string, in UTF-16 units. The domain limits (title 100, notes
     * 500 code points...) are checked later; this only stops absurd values early.
     */
    const val MAX_RAW_TEXT = 2_000

    const val MAX_REF = 64
    const val MAX_CANONICAL_KEY = 100
    const val MAX_LOCALE = 35
    const val MAX_ICON = 32

    /** At most this many issues are kept in a rejection; the total is still counted. */
    const val MAX_REPORTED_ISSUES = 50
}

/** Which limit a file broke. */
enum class TransferLimit { FILE_SIZE, CHECKLISTS, ITEMS, CATEGORIES, UNITS, SECTIONS, TEXT_LENGTH }

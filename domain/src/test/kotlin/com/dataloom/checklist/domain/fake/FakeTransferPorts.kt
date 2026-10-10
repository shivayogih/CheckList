package com.dataloom.checklist.domain.fake

import com.dataloom.checklist.domain.transfer.DecodeResult
import com.dataloom.checklist.domain.transfer.ExportSink
import com.dataloom.checklist.domain.transfer.ImportSource
import com.dataloom.checklist.domain.transfer.TransactionRunner
import com.dataloom.checklist.domain.transfer.TransferCodec
import com.dataloom.checklist.domain.transfer.TransferDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Keeps documents in memory: encode returns a small token, decode looks it up. The real JSON
 * codec is tested in :data; this lets use case tests focus on validation and matching.
 */
class FakeTransferCodec : TransferCodec {
    private val documents = mutableListOf<TransferDocument>()

    /** Set to make every decode return this instead (e.g. a rejection). */
    var forcedResult: DecodeResult? = null

    /** Pads encoded output to this many bytes, to test size limits. */
    var encodedSize: Int? = null

    fun bytesFor(document: TransferDocument): ByteArray {
        documents += document
        val token = "doc:${documents.lastIndex}".toByteArray()
        return encodedSize?.let { token.copyOf(it) } ?: token
    }

    override fun encode(document: TransferDocument): ByteArray = bytesFor(document)

    override fun decode(bytes: ByteArray): DecodeResult {
        forcedResult?.let { return it }
        val index = bytes.decodeToString().trimEnd('\u0000').removePrefix("doc:").toInt()
        return DecodeResult.Decoded(documents[index])
    }
}

class BytesSource(private val bytes: ByteArray, override val sizeBytes: Long? = bytes.size.toLong()) : ImportSource {
    var opened = false
    override fun openStream(): InputStream {
        opened = true
        return ByteArrayInputStream(bytes)
    }
}

/** A stream that never ends, as a hostile provider could return. */
class EndlessSource(override val sizeBytes: Long? = null) : ImportSource {
    override fun openStream(): InputStream = object : InputStream() {
        override fun read(): Int = 'a'.code
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            b.fill('a'.code.toByte(), off, off + len)
            return len
        }
    }
}

class FailingSource : ImportSource {
    override val sizeBytes: Long? = null
    override fun openStream(): InputStream = throw IOException("provider gone")
}

class MemorySink : ExportSink {
    val out = ByteArrayOutputStream()
    var opened = false
    override fun openStream(): OutputStream {
        opened = true
        return out
    }
}

/** Runs the block directly; rollback is covered by the Room tests in :data. */
class DirectTransactionRunner : TransactionRunner {
    var transactions = 0
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactions++
        return block()
    }
}

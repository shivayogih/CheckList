package com.dataloom.checklist.domain.transfer

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hostile and well-formed zip archives against [ZipArchiveReader] (docs/import-export-format.md, "Photo archive"). */
class PhotoArchiveTest {

    private val scratch: File = Files.createTempDirectory("archive-test").toFile()

    @After
    fun tearDown() {
        scratch.deleteRecursively()
    }

    private val json = """{"formatVersion":2}""".toByteArray()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(32) { 7 }
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(16)
    private val webp = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + ByteArray(8)

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(archive: ByteArray): ArchiveReadResult = ZipArchiveReader.read(archive.inputStream(), scratch)

    private fun rejection(archive: ByteArray): ImportRejection = (read(archive) as ArchiveReadResult.Rejected).rejection

    private fun assertUnsafe(problem: ArchiveProblem, archive: ByteArray) =
        assertEquals(ImportRejection.UnsafeArchive(problem), rejection(archive))

    @Test
    fun `a well-formed archive yields the json and every photo in the scratch directory`() {
        val result = read(
            zipOf(
                "checklists.json" to json,
                "photos/p1.jpg" to jpeg,
                "photos/p2.PNG" to png,
                "photos/p3.webp" to webp,
            ),
        ) as ArchiveReadResult.Read

        assertArrayEquals(json, result.json)
        assertEquals(listOf("photos/p1.jpg", "photos/p2.png", "photos/p3.webp"), result.archive.photos.keys.toList())
        assertArrayEquals(jpeg, result.archive.photos.getValue("photos/p1.jpg").readBytes())
        // Files live in the scratch directory under the checked name, nowhere else.
        assertTrue(result.archive.photos.values.all { it.parentFile == scratch })
    }

    @Test
    fun `path traversal and unsafe names are refused before anything is written`() {
        listOf(
            "../evil.jpg",
            "photos/../evil.jpg",
            "/etc/evil.jpg",
            "photos\\evil.jpg",
            "C:/evil.jpg",
            "photos/a/b.jpg",
            "photos/..jpg/../../x.jpg",
            "photos/.hidden.jpg",
        ).forEach { name ->
            assertUnsafe(ArchiveProblem.UNSAFE_PATH, zipOf("checklists.json" to json, name to jpeg))
        }
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `directories and other files are refused`() {
        assertUnsafe(ArchiveProblem.UNEXPECTED_ENTRY, zipOf("checklists.json" to json, "photos/" to ByteArray(0)))
        assertUnsafe(ArchiveProblem.UNEXPECTED_ENTRY, zipOf("checklists.json" to json,
            "readme.txt" to "hi".toByteArray()))
        assertUnsafe(ArchiveProblem.UNEXPECTED_ENTRY, zipOf("checklists.json" to json, "other/p1.jpg" to jpeg))
    }

    @Test
    fun `only jpg jpeg png and webp are accepted by extension`() {
        listOf("photos/p1.gif", "photos/p1.exe", "photos/p1.jpg.exe", "photos/p1", "photos/p1.svg").forEach { name ->
            assertUnsafe(ArchiveProblem.DISALLOWED_EXTENSION, zipOf("checklists.json" to json, name to jpeg))
        }
    }

    @Test
    fun `an entry that is not an image of its extension is refused`() {
        assertUnsafe(ArchiveProblem.NOT_AN_IMAGE, zipOf("checklists.json" to json,
            "photos/p1.jpg" to "<svg/>".toByteArray()))
        assertUnsafe(ArchiveProblem.NOT_AN_IMAGE, zipOf("checklists.json" to json, "photos/p1.jpg" to png))
        assertUnsafe(ArchiveProblem.NOT_AN_IMAGE, zipOf("checklists.json" to json, "photos/p1.png" to jpeg))
        assertUnsafe(ArchiveProblem.NOT_AN_IMAGE, zipOf("checklists.json" to json, "photos/p1.jpg" to ByteArray(0)))
        // What a symbolic link entry holds: the path it points to.
        assertUnsafe(ArchiveProblem.NOT_AN_IMAGE, zipOf("checklists.json" to json,
            "photos/link.jpg" to "../../../etc/passwd".toByteArray()))
    }

    @Test
    fun `the same name twice is refused ignoring case`() {
        assertUnsafe(ArchiveProblem.DUPLICATE_ENTRY, zipOf("checklists.json" to json, "photos/p1.jpg" to jpeg,
            "photos/P1.JPG" to jpeg))
    }

    @Test
    fun `an archive without checklists json or that is not a zip is refused`() {
        assertUnsafe(ArchiveProblem.NOT_AN_ARCHIVE, zipOf("photos/p1.jpg" to jpeg))
        assertUnsafe(ArchiveProblem.NOT_AN_ARCHIVE, "PK\u0003\u0004 definitely not a zip".toByteArray())
        // Incompressible photo data dominates the file, so half of it ends inside that entry.
        val noise = jpeg + kotlin.random.Random(1).nextBytes(20_000)
        val truncated = zipOf("checklists.json" to json, "photos/p1.jpg" to noise).let { it.copyOf(it.size / 2) }
        assertUnsafe(ArchiveProblem.NOT_AN_ARCHIVE, truncated)
    }

    @Test
    fun `a photo over the per-photo limit is refused by the bytes actually read`() {
        val big = jpeg + ByteArray(TransferLimits.MAX_PHOTO_BYTES.toInt())
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.PHOTO_SIZE, TransferLimits.MAX_PHOTO_BYTES),
            rejection(zipOf("checklists.json" to json, "photos/p1.jpg" to big)),
        )
    }

    @Test
    fun `an oversized checklists json is refused`() {
        val big = ByteArray(TransferLimits.MAX_FILE_BYTES.toInt() + 1) { ' '.code.toByte() }
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.FILE_SIZE, TransferLimits.MAX_FILE_BYTES),
            rejection(zipOf("checklists.json" to big)),
        )
    }

    @Test
    fun `a zip bomb is stopped by the running total of decompressed bytes`() {
        // 25 photos of 4.5 MB of zeros each: tiny when compressed, 112 MB when read. Headers are never consulted.
        val body = jpeg + ByteArray(4_500_000)
        val bomb = zipOf("checklists.json" to json, *Array(25) { "photos/p$it.jpg" to body })
        assertTrue("the archive itself is small", bomb.size < 1_000_000)
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.ARCHIVE_SIZE, TransferLimits.MAX_ARCHIVE_BYTES),
            rejection(bomb),
        )
    }

    @Test
    fun `too many entries are refused`() {
        val entries = Array(TransferLimits.MAX_ARCHIVE_ENTRIES) { "photos/p$it.jpg" to jpeg }
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.PHOTOS, TransferLimits.MAX_ARCHIVE_ENTRIES.toLong()),
            rejection(zipOf("checklists.json" to json, *entries)),
        )
    }

    @Test
    fun `archive paths classify names consistently for entries and for the photo file field`() {
        assertEquals("photos/p1.jpg", ArchivePaths.photoKey("photos/p1.jpg"))
        assertEquals("photos/p1.jpeg", ArchivePaths.photoKey("photos/P1.JPEG"))
        assertEquals(null, ArchivePaths.photoKey("checklists.json"))
        assertEquals(null, ArchivePaths.photoKey("photos/../checklists.json"))
        assertEquals(null, ArchivePaths.photoKey("photos/p1.gif"))
        assertFalse(ArchivePaths.looksLikeImage("jpg", ByteArray(0), 0))
    }
}

package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.fake.BytesSource
import com.dataloom.checklist.domain.fake.DirectTransactionRunner
import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.fake.FakeChecklistRepository
import com.dataloom.checklist.domain.fake.FakePhotoStore
import com.dataloom.checklist.domain.fake.FakeTransferCodec
import com.dataloom.checklist.domain.fake.MemorySink
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoFailure
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.StagePhotoResult
import com.dataloom.checklist.domain.photo.StoredPhoto
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.repository.NoPhotoRepository
import com.dataloom.checklist.domain.repository.PhotoRepository
import com.dataloom.checklist.domain.transfer.TransferFixtures.checklist
import com.dataloom.checklist.domain.transfer.TransferFixtures.document
import com.dataloom.checklist.domain.transfer.TransferFixtures.item
import com.dataloom.checklist.domain.validation.ValidationError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Photos in the import/export pipeline: validation rules, archive export, and staged import (CL-213). */
class PhotoTransferTest {

    // ---- Validation ----

    private fun photoDocument(vararg photos: TransferPhoto, formatVersion: Int = 2) = document(
        units = emptyList(),
        categories = listOf(TransferCategory("c1", canonicalKey = "groceries")),
        checklists = listOf(checklist("k1", "One", TransferSection("s1", "c1", 0))),
        items = listOf(item("i1", "s1", name = "Rice").copy(photos = photos.toList())),
        formatVersion = formatVersion,
    )

    private fun photo(n: Int, caption: String? = null) = TransferPhoto("p$n", "photos/p$n.jpg", caption)

    private fun available(vararg n: Int): Set<String> = n.map { "photos/p$it.jpg" }.toSet()

    private fun problems(document: TransferDocument, available: Set<String>?): List<ImportProblem> =
        (ImportValidator.validate(document, available) as ImportRejection.Invalid).issues.map { it.problem }

    @Test
    fun `a version 2 file with up to three photos that exist in the archive is valid`() {
        assertNull(ImportValidator.validate(photoDocument(photo(1, "Front"), photo(2), photo(3)), available(1, 2, 3)))
        assertNull(ImportValidator.validate(photoDocument(), null))
    }

    @Test
    fun `a fourth photo on one item is refused`() {
        assertEquals(
            listOf(ImportProblem.TOO_MANY_PHOTOS),
            problems(photoDocument(photo(1), photo(2), photo(3), photo(4)), available(1, 2, 3, 4)),
        )
    }

    @Test
    fun `photos are only valid in format version 2`() {
        assertEquals(listOf(ImportProblem.PHOTOS_NEED_FORMAT_2), problems(photoDocument(photo(1), formatVersion = 1),
            available(1)))
    }

    @Test
    fun `a photo whose archive entry is missing or that comes from a plain json file is refused`() {
        assertEquals(listOf(ImportProblem.MISSING_PHOTO_FILE), problems(photoDocument(photo(1), photo(2)),
            available(1)))
        assertEquals(listOf(ImportProblem.MISSING_PHOTO_FILE), problems(photoDocument(photo(1)), null))
    }

    @Test
    fun `photo paths must be plain photos entries and unique ignoring case`() {
        val hostile = listOf("../x.jpg", "photos/../x.jpg", "/abs.jpg", "photos/a/b.jpg", "photos/p1.gif",
            "checklists.json", "")
        hostile.forEach { file ->
            val p = (ImportValidator.validate(photoDocument(TransferPhoto("p1", file)),
                available(1)) as ImportRejection.Invalid).issues
            assertEquals(file, listOf(ImportProblem.INVALID_PHOTO_PATH), p.map { it.problem })
        }
        val duplicate = photoDocument(TransferPhoto("p1", "photos/a.jpg"), TransferPhoto("p2", "photos/A.JPG"))
        assertEquals(listOf(ImportProblem.DUPLICATE_PHOTO_FILE), problems(duplicate, setOf("photos/a.jpg")))
    }

    @Test
    fun `photo refs must be well formed and unique`() {
        val doc = photoDocument(TransferPhoto("p 1", "photos/p1.jpg"), TransferPhoto("p2", "photos/p2.jpg"),
            TransferPhoto("p2", "photos/p3.jpg"))
        assertEquals(listOf(ImportProblem.INVALID_REF, ImportProblem.DUPLICATE_REF), problems(doc, available(1, 2, 3)))
    }

    @Test
    fun `a caption over 80 characters is refused using the same rule as the form`() {
        val ok = photoDocument(TransferPhoto("p1", "photos/p1.jpg", "ಅ".repeat(80)))
        assertNull(ImportValidator.validate(ok, available(1)))
        val bad = (ImportValidator.validate(photoDocument(TransferPhoto("p1", "photos/p1.jpg", "x".repeat(81))),
            available(1)) as ImportRejection.Invalid).issues.single()
        assertEquals(ImportProblem.INVALID_FIELDS, bad.problem)
        assertEquals(listOf(ValidationError.CAPTION_TOO_LONG), bad.fieldErrors)
        assertEquals(TransferElement.PHOTO, bad.element)
    }

    @Test
    fun `format versions 1 and 2 are accepted and 3 asks for an update`() {
        assertNull(ImportValidator.validate(document(formatVersion = 1)))
        assertNull(ImportValidator.validate(document(formatVersion = 2)))
        assertEquals(
            ImportRejection.UnsupportedVersion(3, 1, requiresNewerApp = true),
            ImportValidator.validate(document(formatVersion = 3)),
        )
    }

    // ---- Devices ----

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    /** Adds photos to the items of the wrapped repository, by item name (the fake repository stores none). */
    private class PhotosByName(
        private val inner: FakeChecklistRepository,
        val byName: MutableMap<String, List<ItemPhoto>> = HashMap(),
    ) : ChecklistRepository by inner {
        override fun observeChecklist(id: ChecklistId,
            locale: String): Flow<com.dataloom.checklist.domain.model.ChecklistDetail?> =
            inner.observeChecklist(id, locale).map { detail ->
                detail?.copy(
                    sections = detail.sections.map { section ->
                        section.copy(items = section.items.map { it.copy(photos = byName[it.displayName].orEmpty()) })
                    },
                )
            }
    }

    private class RecordingPhotos : PhotoRepository by NoPhotoRepository {
        val added = ArrayList<Pair<ChecklistItemId, List<StoredPhoto>>>()
        val captions = HashMap<PhotoId, String>()
        var failOnAdd = false
        private var next = 1

        override suspend fun addPhotos(itemId: ChecklistItemId, photos: List<StoredPhoto>): List<ItemPhoto> {
            if (failOnAdd) error("database write failed")
            added += itemId to photos
            return photos.map {
                ItemPhoto(PhotoId("ph-${next++}"), itemId, it.fileName, it.width, it.height, it.byteSize, 1000, null,
                    1L)
            }
        }

        override suspend fun setCaption(photoId: PhotoId, caption: String?) {
            if (caption != null) captions[photoId] = caption
        }
    }

    /** A store that cannot decode: every [stage] fails, as for a corrupt image. */
    private class UndecodableStore(private val inner: FakePhotoStore) : PhotoStore by inner {
        override suspend fun stage(source: ImageSource): StagePhotoResult =
            StagePhotoResult.Failed(PhotoFailure.NOT_AN_IMAGE)
    }

    private inner class Device(store: PhotoStore = FakePhotoStore()) {
        val codec = FakeTransferCodec()
        val fileStore = store as? FakePhotoStore
        val catalog = FakeCatalogRepository()
        val checklists = FakeChecklistRepository(catalog)
        val withPhotos = PhotosByName(checklists)
        val groceries: Category = catalog.seedCategory("groceries", "Groceries")
        val photos = RecordingPhotos()
        val transactions = DirectTransactionRunner()
        val export = ExportChecklistsUseCase(withPhotos, catalog, codec, Clock { 1_791_432_000_000L }, store)
        val preview = PreviewImportUseCase(checklists, catalog, codec, store)
        val apply = ApplyImportUseCase(checklists, catalog, transactions, photos, store)

        /** A checklist with the items "Rice" (photos [a.jpg with a caption, gone.jpg]) and "Salt" (none). */
        suspend fun sample(vararg stored: Pair<String, String?>): ChecklistId {
            val id = checklists.createChecklist("Trip", null, listOf(groceries.id))
            val section = checklists.detail(id)!!.sections.single().id
            val ids = checklists.addItems(
                section,
                listOf(
                    NewChecklistItem(null, null, "Rice", "en", null, null, null),
                    NewChecklistItem(null, null, "Salt", "en", null, null, null),
                ),
            )
            withPhotos.byName["Rice"] = stored.mapIndexed { index, (file, caption) ->
                ItemPhoto(PhotoId("p$index"), ids[0], file, 800, 600, 10, (index + 1) * 1000, caption, 1L)
            }
            return id
        }
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                result[entry.name] = input.readBytes()
            }
        }
        return result
    }

    // ---- Export ----

    @Test
    fun `export with photos writes a zip with the json and the photo files`() = runTest {
        val device = Device()
        device.fileStore!!.put("a.jpg", "AAAA")
        val id = device.sample("a.jpg" to "Front of shop", "gone.jpg" to null)
        val sink = MemorySink()

        val result = device.export(ExportRequest(listOf(id), "en", "1.0", includePhotos = true),
            sink) as ExportResult.Exported

        assertEquals(1, result.photoCount)
        assertEquals(1, result.skippedPhotoCount)
        assertEquals(sink.out.size(), result.byteCount)
        val zip = entries(sink.out.toByteArray())
        assertEquals(listOf("checklists.json", "photos/p1.jpg"), zip.keys.toList())
        assertEquals("AAAA", String(zip.getValue("photos/p1.jpg")))
        val decoded = (device.codec.decode(zip.getValue("checklists.json")) as DecodeResult.Decoded).document
        assertEquals(2, decoded.formatVersion)
        assertEquals(listOf(TransferPhoto("p1", "photos/p1.jpg", "Front of shop")),
            decoded.items.first { it.displayName == "Rice" }.photos)
        assertTrue(decoded.items.first { it.displayName == "Salt" }.photos.isEmpty())
    }

    @Test
    fun `export without the photos switch writes the plain version 1 json`() = runTest {
        val device = Device()
        device.fileStore!!.put("a.jpg", "AAAA")
        val id = device.sample("a.jpg" to "Front")
        val sink = MemorySink()

        val result = device.export(ExportRequest(listOf(id), "en", "1.0"), sink) as ExportResult.Exported

        assertEquals(0, result.photoCount)
        val decoded = (device.codec.decode(sink.out.toByteArray()) as DecodeResult.Decoded).document
        assertEquals(1, decoded.formatVersion)
        assertTrue(decoded.items.all { it.photos.isEmpty() })
    }

    @Test
    fun `a photo over the per-photo limit is left out of the archive and counted`() = runTest {
        val device = Device()
        device.fileStore!!.put("huge.jpg", "x".repeat(TransferLimits.MAX_PHOTO_BYTES.toInt() + 1))
        device.fileStore.put("ok.jpg", "ok")
        val id = device.sample("huge.jpg" to null, "ok.jpg" to null)

        val result = device.export(ExportRequest(listOf(id), "en", "1.0", includePhotos = true),
            MemorySink()) as ExportResult.Exported

        assertEquals(1, result.photoCount)
        assertEquals(1, result.skippedPhotoCount)
    }

    @Test
    fun `more photos than an archive may hold are refused before the destination is opened`() = runTest {
        val device = Device()
        val files = (1..TransferLimits.MAX_ARCHIVE_ENTRIES).map { "f$it.jpg" }
        files.forEach { device.fileStore!!.put(it, "x") }
        val id = device.sample(*files.map { it to null }.toTypedArray())
        val sink = MemorySink()

        val result = device.export(ExportRequest(listOf(id), "en", "1.0", includePhotos = true), sink)

        assertEquals(ExportResult.TooLarge(TransferLimit.PHOTOS, (TransferLimits.MAX_ARCHIVE_ENTRIES - 1).toLong()),
            result)
        assertFalse(sink.opened)
    }

    // ---- Import ----

    private fun archive(device: Device, document: TransferDocument, vararg photos: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("checklists.json"))
            zip.write(device.codec.bytesFor(document))
            zip.closeEntry()
            photos.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val twoPhotos = photoDocument(photo(1, "Front"), TransferPhoto("p2", "photos/p2.jpg"))

    @Test
    fun `an archive round trip creates the photo rows with captions and commits the files`() = runTest {
        val source = Device()
        source.fileStore!!.putBytes("a.jpg", jpeg + "AAAA".toByteArray())
        source.fileStore.putBytes("b.jpg", jpeg + "BBBB".toByteArray())
        val id = source.sample("a.jpg" to "Front", "b.jpg" to null)
        val sink = MemorySink()
        source.export(ExportRequest(listOf(id), "en", "1.0", includePhotos = true), sink)

        val target = Device()
        // The fake codec keeps documents in memory per device, so the target reads with the source's codec.
        val bytes = sink.out.toByteArray()
        val ready = (
            PreviewImportUseCase(target.checklists, target.catalog, source.codec,
                target.fileStore!!)(BytesSource(bytes), "en")
                as ImportPreviewResult.Ready
            ).preview
        assertEquals(2, ready.photoCount)

        val summary = (
            ApplyImportUseCase(target.checklists, target.catalog, target.transactions, target.photos,
                target.fileStore)(ready.validated, "en")
                as ImportResult.Imported
            ).summary

        assertEquals(2, summary.photoCount)
        assertEquals(0, summary.skippedPhotoCount)
        assertEquals(1, target.photos.added.size)
        assertEquals(2, target.photos.added.single().second.size)
        assertEquals(listOf("Front"), target.photos.captions.values.toList())
        // Committed: the files are in the store, none are left in staging, the scratch directory is gone.
        target.photos.added.single().second.forEach { assertTrue(target.fileStore.files.containsKey(it.fileName)) }
        assertTrue(target.fileStore.staged.isEmpty())
        assertFalse(ready.validated.archive!!.directory.exists())
    }

    @Test
    fun `a photo that cannot be decoded is skipped and counted while the rest is imported`() = runTest {
        val undecodable = Device(UndecodableStore(FakePhotoStore()))
        val bytes = archive(undecodable, twoPhotos, "photos/p1.jpg" to jpeg, "photos/p2.jpg" to jpeg)

        val ready = (undecodable.preview(BytesSource(bytes), "en") as ImportPreviewResult.Ready).preview
        val summary = (undecodable.apply(ready.validated, "en") as ImportResult.Imported).summary

        assertEquals(0, summary.photoCount)
        assertEquals(2, summary.skippedPhotoCount)
        assertEquals(1, summary.itemCount)
        assertTrue(undecodable.photos.added.isEmpty())
    }

    @Test
    fun `a failed transaction discards staged photos and keeps the scratch files for a retry`() = runTest {
        val device = Device()
        val bytes = archive(device, twoPhotos, "photos/p1.jpg" to jpeg, "photos/p2.jpg" to jpeg)
        val ready = (device.preview(BytesSource(bytes), "en") as ImportPreviewResult.Ready).preview
        device.photos.failOnAdd = true

        try {
            device.apply(ready.validated, "en")
            fail("the write failure must reach the caller")
        } catch (e: IllegalStateException) {
            assertEquals("database write failed", e.message)
        }

        assertTrue("staged files are removed", device.fileStore!!.staged.isEmpty())
        assertTrue("nothing is committed", device.fileStore.files.isEmpty())
        val scratch = ready.validated.archive!!.directory
        assertTrue(scratch.exists())

        ready.validated.discard()
        assertFalse(scratch.exists())
    }

    @Test
    fun `dismissing a preview deletes the scratch directory`() = runTest {
        val device = Device()
        val bytes = archive(device, twoPhotos, "photos/p1.jpg" to jpeg, "photos/p2.jpg" to jpeg)
        val ready = (device.preview(BytesSource(bytes), "en") as ImportPreviewResult.Ready).preview
        val scratch = ready.validated.archive!!.directory
        assertTrue(scratch.listFiles().orEmpty().size == 2)

        ready.validated.discard()

        assertFalse(scratch.exists())
    }

    @Test
    fun `an archive whose json names a missing photo is rejected and leaves no scratch files behind`() = runTest {
        val device = Device()
        val bytes = archive(device, twoPhotos, "photos/p1.jpg" to jpeg)

        val rejection = (device.preview(BytesSource(bytes), "en") as ImportPreviewResult.Rejected).rejection

        assertEquals(listOf(ImportProblem.MISSING_PHOTO_FILE),
            (rejection as ImportRejection.Invalid).issues.map { it.problem })
        assertEquals(0, device.checklists.checklistCount())
    }

    @Test
    fun `a hostile archive is rejected with a typed reason and writes nothing`() = runTest {
        val device = Device()
        val bytes = archive(device, twoPhotos, "photos/p1.jpg" to jpeg, "../evil.jpg" to jpeg)

        val result = device.preview(BytesSource(bytes), "en")

        assertEquals(ImportPreviewResult.Rejected(ImportRejection.UnsafeArchive(ArchiveProblem.UNSAFE_PATH)), result)
        assertEquals(0, device.checklists.checklistCount())
    }

    @Test
    fun `a plain json file with photos in it cannot import`() = runTest {
        val device = Device()
        val result = device.preview(BytesSource(device.codec.bytesFor(photoDocument(photo(1)))), "en")
        assertTrue(result is ImportPreviewResult.Rejected)
    }
}

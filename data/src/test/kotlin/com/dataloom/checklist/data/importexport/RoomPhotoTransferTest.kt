package com.dataloom.checklist.data.importexport

import android.graphics.BitmapFactory
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.FakeSeedSource
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.photo.FilePhotoStore
import com.dataloom.checklist.data.photo.TestImages
import com.dataloom.checklist.data.repository.RoomCatalogRepository
import com.dataloom.checklist.data.repository.RoomChecklistRepository
import com.dataloom.checklist.data.repository.RoomPhotoRepository
import com.dataloom.checklist.data.seed.SeedLoader
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.transfer.ApplyImportUseCase
import com.dataloom.checklist.domain.transfer.ArchiveProblem
import com.dataloom.checklist.domain.transfer.ExportChecklistsUseCase
import com.dataloom.checklist.domain.transfer.ExportRequest
import com.dataloom.checklist.domain.transfer.ExportResult
import com.dataloom.checklist.domain.transfer.ExportSink
import com.dataloom.checklist.domain.transfer.ImportPreviewResult
import com.dataloom.checklist.domain.transfer.ImportRejection
import com.dataloom.checklist.domain.transfer.ImportResult
import com.dataloom.checklist.domain.transfer.ImportSource
import com.dataloom.checklist.domain.transfer.PreviewImportUseCase
import com.dataloom.checklist.domain.usecase.AddItemPhotosUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.SetPhotoCaptionUseCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Photo archives end to end on Room and real files: export a zip from one phone, import it on
 * another, and survive hostile entries. Real codec, real `FilePhotoStore`, real transactions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomPhotoTransferTest {

    @get:Rule
    val temp = TemporaryFolder()

    private inner class Phone(name: String) {
        val clock = FakeClock(now = 1_791_432_000_000L)
        val ids = SequentialIds(name)
        val db: CheckListDatabase = inMemoryDatabase()
        val root = File(temp.root, "$name-photos")
        val store = FilePhotoStore(root, Dispatchers.Unconfined, SequentialIds("$name-img"))
        val photos = RoomPhotoRepository(db, clock, ids)
        val checklists = RoomChecklistRepository(db, clock, ids, store)
        val catalog = RoomCatalogRepository(db, clock, ids)
        val codec = JsonTransferCodec()
        val export = ExportChecklistsUseCase(checklists, catalog, codec, clock, store)
        val preview = PreviewImportUseCase(checklists, catalog, codec, store)
        val apply = ApplyImportUseCase(checklists, catalog, RoomTransactionRunner(db), photos, store)

        init {
            SeedLoader(FakeSeedSource(), clock, SequentialIds("$name-seed")).seedIfNeeded(db.openHelper.writableDatabase)
        }

        suspend fun detail(id: ChecklistId) = checklists.observeChecklist(id, "en").first()!!

        fun storedFiles(): List<String> = root.listFiles().orEmpty().filter { it.isFile }.map { it.name }.sorted()

        /** "Weekly" with the item "Rice" holding [count] photos; the first one has a caption. */
        suspend fun weeklyWithPhotos(count: Int): ChecklistId {
            val groceries = catalog.observeCategories("en").first().single { it.canonicalKey == "groceries" }
            val id = checklists.createChecklist("Weekly", null, listOf(groceries.id))
            val section = detail(id).sections.single().id
            val item = checklists.addItems(section, listOf(NewChecklistItem(null, null, "Rice", "en", null, null, null))).single()
            val sources = List(count) { TestImages.source(TestImages.jpegBytes(TestImages.halves(120 + it * 10, 90))) }
            val added = (AddItemPhotosUseCase(photos, store)(item, sources) as DomainResult.Success).value.added
            SetPhotoCaptionUseCase(photos)(added.first().id, "Front of the shop")
            return id
        }

        suspend fun importArchive(bytes: ByteArray): ImportResult.Imported {
            val ready = preview(Bytes(bytes), "en") as ImportPreviewResult.Ready
            return apply(ready.preview.validated, "en") as ImportResult.Imported
        }
    }

    private class Bytes(private val bytes: ByteArray) : ImportSource {
        override val sizeBytes: Long = bytes.size.toLong()
        override fun openStream(): InputStream = ByteArrayInputStream(bytes)
    }

    private class MemorySink : ExportSink {
        val out = ByteArrayOutputStream()
        override fun openStream() = out
    }

    private val phoneA = Phone("a")
    private val phoneB = Phone("b")

    @After
    fun tearDown() {
        phoneA.db.close()
        phoneB.db.close()
    }

    private suspend fun Phone.exportZip(id: ChecklistId): ByteArray {
        val sink = MemorySink()
        val result = export(ExportRequest(listOf(id), "en", "1.0.0", includePhotos = true), sink)
        assertTrue(result.toString(), result is ExportResult.Exported)
        return sink.out.toByteArray()
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

    private fun zipOf(json: ByteArray, vararg photos: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("checklists.json"))
            zip.write(json)
            zip.closeEntry()
            photos.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ChecklistItem.fileNames() = photos.map { it.fileName }

    @Test
    fun `photos travel in a zip and come back as new files with their captions`() = runTest {
        val id = phoneA.weeklyWithPhotos(count = 2)
        val zip = phoneA.exportZip(id)

        val names = entries(zip).keys.toList()
        assertEquals(listOf("checklists.json", "photos/p1.jpg", "photos/p2.jpg"), names)
        assertTrue(String(entries(zip).getValue("checklists.json")).contains("\"formatVersion\": 2"))

        val summary = phoneB.importArchive(zip).summary
        assertEquals(2, summary.photoCount)
        assertEquals(0, summary.skippedPhotoCount)

        val sourceItem = phoneA.detail(id).sections.single().items.single()
        val importedItem = phoneB.detail(summary.checklistIds.single()).sections.single().items.single()
        assertEquals(2, importedItem.photos.size)
        assertEquals(listOf("Front of the shop", null), importedItem.photos.map { it.caption })
        // New names on the new phone, same pictures: dimensions survive and the files decode.
        assertTrue(importedItem.fileNames().none { it in sourceItem.fileNames() })
        assertEquals(sourceItem.photos.map { it.width to it.height }, importedItem.photos.map { it.width to it.height })
        importedItem.photos.forEach { photo ->
            val bitmap = BitmapFactory.decodeFile(phoneB.store.file(photo.fileName).path)
            assertNotNull(bitmap)
            assertEquals(photo.width, bitmap.width)
            assertTrue(phoneB.store.thumbnailFile(photo.fileName).isFile)
        }
        // Only the two images and their thumbnails remain; staging and scratch are empty.
        assertEquals(4, phoneB.storedFiles().size)
        assertTrue(File(phoneB.root, "staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a png entry is re-encoded and an image that cannot be decoded is skipped without failing the import`() = runTest {
        val id = phoneA.weeklyWithPhotos(count = 1)
        val original = entries(phoneA.exportZip(id))
        val png = TestImages.pngBytes(TestImages.halves(64, 48))
        val json = String(original.getValue("checklists.json"))
            .replace("photos/p1.jpg", "photos/p1.png")
            .let { text ->
                // Add two more photos to the item: one that is a stub PNG, one claiming a huge size.
                text.replace(
                    "\"photos\": [",
                    "\"photos\": [\n{\"ref\":\"p8\",\"file\":\"photos/stub.png\",\"caption\":null},\n{\"ref\":\"p9\",\"file\":\"photos/huge.png\",\"caption\":null},",
                )
            }
        val archive = zipOf(
            json.toByteArray(),
            "photos/p1.png" to png,
            "photos/stub.png" to png.copyOf(30),
            "photos/huge.png" to TestImages.pngClaiming(20_000, 20_000),
        )

        val summary = phoneB.importArchive(archive).summary

        assertEquals(1, summary.photoCount)
        assertEquals(2, summary.skippedPhotoCount)
        val photo = phoneB.detail(summary.checklistIds.single()).sections.single().items.single().photos.single()
        assertTrue(photo.fileName.endsWith(".jpg"))
        val head = phoneB.store.file(photo.fileName).inputStream().use { it.readNBytes(3) }
        assertEquals(listOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), head.toList())
        assertEquals(2, phoneB.storedFiles().size)
    }

    @Test
    fun `a zip with a path traversal entry imports nothing and leaves no files`() = runTest {
        val id = phoneA.weeklyWithPhotos(count = 1)
        val original = entries(phoneA.exportZip(id))
        val archive = zipOf(
            original.getValue("checklists.json"),
            "photos/p1.jpg" to original.getValue("photos/p1.jpg"),
            "../../escaped.jpg" to original.getValue("photos/p1.jpg"),
        )

        val result = phoneB.preview(Bytes(archive), "en")

        assertEquals(ImportPreviewResult.Rejected(ImportRejection.UnsafeArchive(ArchiveProblem.UNSAFE_PATH)), result)
        assertEquals(0, phoneB.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM checklist").use { it.moveToFirst(); it.getInt(0) })
        assertTrue(phoneB.storedFiles().isEmpty())
        assertFalse(File(temp.root, "escaped.jpg").exists())
    }

    @Test
    fun `exporting without the photos switch gives a plain json file`() = runTest {
        val id = phoneA.weeklyWithPhotos(count = 1)
        val sink = MemorySink()
        val result = phoneA.export(ExportRequest(listOf(id), "en", "1.0.0"), sink) as ExportResult.Exported
        assertEquals(0, result.photoCount)
        val text = sink.out.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("{"))
        assertTrue(text.contains("\"formatVersion\": 1"))
        assertFalse(text.contains("photos"))
    }
}

package com.dataloom.checklist.data.repository

import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.FakeSeedSource
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.photo.FilePhotoStore
import com.dataloom.checklist.data.photo.TestImages
import com.dataloom.checklist.data.seed.SeedLoader
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.domain.photo.PhotoNames
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.StorePhotoFileCleaner
import com.dataloom.checklist.domain.usecase.AddItemPhotosUseCase
import com.dataloom.checklist.domain.usecase.AddPhotosOutcome
import com.dataloom.checklist.domain.usecase.DeleteChecklistItemUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.RemoveItemPhotoUseCase
import com.dataloom.checklist.domain.usecase.RemoveSectionUseCase
import com.dataloom.checklist.domain.usecase.ReorderItemPhotoUseCase
import com.dataloom.checklist.domain.usecase.SetPhotoCaptionUseCase
import com.dataloom.checklist.domain.usecase.SweepOrphanPhotosUseCase
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Photo rows with the real database and the real file store: ordering, cascades, duplicate, cleanup, sweep. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomPhotoRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val clock = FakeClock(now = 10_000)
    private val ids = SequentialIds()
    private val db = inMemoryDatabase()
    private val root by lazy { File(temp.root, "item_photos") }
    private val store by lazy { FilePhotoStore(root, Dispatchers.Unconfined, SequentialIds("img")) }
    private val photos by lazy { RoomPhotoRepository(db, clock, ids) }
    private val checklists by lazy { RoomChecklistRepository(db, clock, ids, store) }
    private val catalog = RoomCatalogRepository(db, clock, ids)
    private val cleaner by lazy { StorePhotoFileCleaner(photos, store) }

    private lateinit var groceries: Category
    private lateinit var checklistId: ChecklistId
    private lateinit var sectionId: SectionId
    private lateinit var itemId: ChecklistItemId

    @Before
    fun setUp() = runTest {
        SeedLoader(FakeSeedSource(), clock, SequentialIds("seed")).seedIfNeeded(db.openHelper.writableDatabase)
        groceries = catalog.observeCategories("en").first().single { it.canonicalKey == "groceries" }
        checklistId = checklists.createChecklist("Weekly", null, listOf(groceries.id))
        sectionId = detail(checklistId).sections.single().id
        itemId = addItem(sectionId, "Rice")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun detail(id: ChecklistId): ChecklistDetail = checklists.observeChecklist(id, "en").first()!!

    private suspend fun addItem(section: SectionId, name: String): ChecklistItemId =
        checklists.addItems(section, listOf(NewChecklistItem(null, null, name, "en", null, null, null))).single()

    private fun image(shade: Int = 0) = TestImages.source(TestImages.jpegBytes(TestImages.halves(80 + shade, 60)))

    private suspend fun addPhotos(item: ChecklistItemId, count: Int): AddPhotosOutcome =
        (AddItemPhotosUseCase(photos, store)(item, List(count) { image(it) }) as DomainResult.Success).value

    private fun filesOnDisk(): List<String> = root.listFiles().orEmpty().filter { it.isFile }.map { it.name }.sorted()

    // ---- Rows and the detail query ----

    @Test
    fun `the detail query returns each item with its photos in position order`() = runTest {
        val added = addPhotos(itemId, 3).added
        assertEquals(listOf(1000, 2000, 3000), added.map { it.position })

        ReorderItemPhotoUseCase(photos)(added[2].id, 0)

        val item = detail(checklistId).sections.single().items.single()
        assertEquals(listOf(added[2].id, added[0].id, added[1].id), item.photos.map { it.id })
        assertEquals(added[0].fileName, item.photos[1].fileName)
        assertEquals(80, item.photos[1].width)
    }

    @Test
    fun `an item without photos has an empty list`() = runTest {
        assertTrue(detail(checklistId).sections.single().items.single().photos.isEmpty())
    }

    @Test
    fun `moving a photo to the same place changes nothing and a far index moves it last`() = runTest {
        val added = addPhotos(itemId, 3).added
        ReorderItemPhotoUseCase(photos)(added[0].id, 0)
        assertEquals(added.map { it.id }, photos.photosOf(itemId)!!.map { it.id })
        ReorderItemPhotoUseCase(photos)(added[0].id, 99)
        assertEquals(listOf(added[1].id, added[2].id, added[0].id), photos.photosOf(itemId)!!.map { it.id })
    }

    @Test
    fun `caption is stored, cleared and limited to 80 characters by the use case`() = runTest {
        val photo = addPhotos(itemId, 1).added.single()
        val setCaption = SetPhotoCaptionUseCase(photos)
        setCaption(photo.id, "Blue packet")
        assertEquals("Blue packet", photos.photosOf(itemId)!!.single().caption)
        setCaption(photo.id, null)
        assertEquals(null, photos.photosOf(itemId)!!.single().caption)
        assertTrue(setCaption(photo.id, "x".repeat(PhotoLimits.CAPTION_MAX + 1)) is DomainResult.Failure)
    }

    @Test
    fun `a fourth photo is skipped and its file is never written`() = runTest {
        addPhotos(itemId, 3)
        val outcome = addPhotos(itemId, 2)
        assertEquals(2, outcome.skippedForLimit)
        assertEquals(6, filesOnDisk().size)
    }

    @Test
    fun `photos of an unknown item are not added`() = runTest {
        assertEquals(emptyList<Any>(), photos.addPhotos(ChecklistItemId("nope"), emptyList()))
        assertEquals(null, photos.photosOf(ChecklistItemId("nope")))
    }

    @Test
    fun `changing a photo moves the checklist to the top of recent`() = runTest {
        val before = detail(checklistId).checklist.updatedAt
        clock.now = 99_000
        addPhotos(itemId, 1)
        assertNotEquals(before, detail(checklistId).checklist.updatedAt)
        assertEquals(99_000L, detail(checklistId).checklist.updatedAt)
    }

    // ---- Cascades and file cleanup ----

    private suspend fun rowCount(): Int = photos.allFileNames().size

    @Test
    fun `deleting the item cascades its photo rows and the use case deletes the files`() = runTest {
        addPhotos(itemId, 2)
        assertEquals(4, filesOnDisk().size)

        DeleteChecklistItemUseCase(checklists, cleaner)(itemId)

        assertEquals(0, rowCount())
        assertTrue(filesOnDisk().isEmpty())
    }

    @Test
    fun `without the use case the rows still cascade and the sweep removes the leftover files`() = runTest {
        addPhotos(itemId, 1)
        checklists.deleteItem(itemId)
        assertEquals(0, rowCount())
        assertEquals(2, filesOnDisk().size)

        // Young files are kept (an add may be in progress), old ones go.
        val young = SweepOrphanPhotosUseCase(photos, store) { System.currentTimeMillis() }()
        assertEquals(0, young)
        val old = SweepOrphanPhotosUseCase(photos, store) { System.currentTimeMillis() + 2 * PhotoLimits.ORPHAN_MIN_AGE_MILLIS }()
        assertEquals(1, old)
        assertTrue(filesOnDisk().isEmpty())
    }

    @Test
    fun `the sweep keeps files that have a row, thumbnails included`() = runTest {
        val kept = addPhotos(itemId, 1).added.single()
        val deleted = SweepOrphanPhotosUseCase(photos, store) { System.currentTimeMillis() + 2 * PhotoLimits.ORPHAN_MIN_AGE_MILLIS }()
        assertEquals(0, deleted)
        assertEquals(listOf(PhotoNames.thumbnailName(kept.fileName), kept.fileName).sorted(), filesOnDisk())
    }

    @Test
    fun `removing a section or a checklist deletes the photo files of its items`() = runTest {
        addPhotos(itemId, 1)
        RemoveSectionUseCase(checklists, cleaner)(sectionId)
        assertTrue(filesOnDisk().isEmpty())

        val other = checklists.createChecklist("Other", null, listOf(groceries.id))
        val otherSection = detail(other).sections.single().id
        val otherItem = addItem(otherSection, "Salt")
        addPhotos(otherItem, 2)
        assertEquals(4, filesOnDisk().size)
        DeleteChecklistUseCase(checklists, cleaner)(other)
        assertTrue(filesOnDisk().isEmpty())
        assertEquals(0, rowCount())
    }

    @Test
    fun `removing one photo deletes only its files`() = runTest {
        val added = addPhotos(itemId, 2).added
        RemoveItemPhotoUseCase(photos, store)(added[0].id)
        assertEquals(listOf(PhotoNames.thumbnailName(added[1].fileName), added[1].fileName).sorted(), filesOnDisk())
    }

    @Test
    fun `file names are listed per item, section and checklist`() = runTest {
        val first = addPhotos(itemId, 2).added.map { it.fileName }
        val second = addItem(sectionId, "Salt")
        val salt = addPhotos(second, 1).added.map { it.fileName }

        assertEquals(first.sorted(), photos.fileNamesOfItem(itemId).sorted())
        assertEquals((first + salt).sorted(), photos.fileNamesOfSection(sectionId).sorted())
        assertEquals((first + salt).sorted(), photos.fileNamesOfChecklist(checklistId).sorted())
        assertEquals((first + salt).toSet(), photos.allFileNames())
    }

    // ---- Duplicate ----

    @Test
    fun `duplicating a checklist copies each photo file to a new name and keeps order and captions`() = runTest {
        val added = addPhotos(itemId, 2).added
        SetPhotoCaptionUseCase(photos)(added[1].id, "Large size")

        val copyId = checklists.duplicateChecklist(checklistId, "Weekly (copy)")

        val original = detail(checklistId).sections.single().items.single().photos
        val copy = detail(copyId).sections.single().items.single().photos
        assertEquals(2, copy.size)
        assertEquals(listOf(null, "Large size"), copy.map { it.caption })
        assertEquals(original.map { it.width to it.height }, copy.map { it.width to it.height })
        assertTrue(copy.map { it.fileName }.intersect(original.map { it.fileName }.toSet()).isEmpty())
        assertTrue(copy.map { it.id }.intersect(original.map { it.id }.toSet()).isEmpty())
        copy.forEach {
            assertTrue(store.file(it.fileName).isFile)
            assertTrue(store.thumbnailFile(it.fileName).isFile)
        }
        assertEquals(8, filesOnDisk().size)

        // The copy is independent: deleting the original checklist leaves the copy's files.
        DeleteChecklistUseCase(checklists, cleaner)(checklistId)
        assertEquals(4, filesOnDisk().size)
        copy.forEach { assertTrue(store.file(it.fileName).isFile) }
    }

    @Test
    fun `a photo whose file is missing is left out of the duplicate`() = runTest {
        val added = addPhotos(itemId, 2).added
        store.file(added[0].fileName).delete()

        val copyId = checklists.duplicateChecklist(checklistId, "Copy")

        assertEquals(1, detail(copyId).sections.single().items.single().photos.size)
    }

    @Test
    fun `a failing duplicate removes the files it already copied and creates nothing`() = runTest {
        addPhotos(itemId, 3)
        val before = filesOnDisk()
        var copies = 0
        val flaky = object : PhotoStore by store {
            override suspend fun copy(fileName: String): String? {
                if (++copies == 3) throw IOException("disk full")
                return store.copy(fileName)
            }
        }
        val failing = RoomChecklistRepository(db, clock, ids, flaky)

        try {
            failing.duplicateChecklist(checklistId, "Copy")
            fail("The duplicate must fail")
        } catch (_: IOException) {
            // expected
        }

        assertEquals(before, filesOnDisk())
        assertFalse(checklists.titleExists("Copy"))
    }
}

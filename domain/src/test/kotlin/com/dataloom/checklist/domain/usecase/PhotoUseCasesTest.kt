package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.fake.FakeChecklistRepository
import com.dataloom.checklist.domain.fake.FakePhotoRepository
import com.dataloom.checklist.domain.fake.FakePhotoStore
import com.dataloom.checklist.domain.fake.textSource
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.photo.PhotoFailure
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.domain.photo.PhotoNames
import com.dataloom.checklist.domain.photo.StorePhotoFileCleaner
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoUseCasesTest {

    private val store = FakePhotoStore()
    private val photos = FakePhotoRepository()
    private val item = ChecklistItemId("rice")
    private val add = AddItemPhotosUseCase(photos, store)

    init {
        photos.addItem(item)
    }

    private fun outcome(result: DomainResult<AddPhotosOutcome>) = (result as DomainResult.Success).value

    // ---- AddItemPhotos ----

    @Test
    fun `adds up to three photos and appends in order`() = runTest {
        val result = outcome(add(item, listOf(textSource("a"), textSource("b"), textSource("c"))))
        assertEquals(3, result.added.size)
        assertEquals(0, result.skippedForLimit)
        assertEquals(listOf(1000, 2000, 3000), photos.photosOf(item)!!.map { it.position })
    }

    @Test
    fun `sources beyond the free slots are skipped, counted and never stored`() = runTest {
        add(item, listOf(textSource("a"), textSource("b")))
        val result = outcome(add(item, listOf(textSource("c"), textSource("d"), textSource("e"))))
        assertEquals(1, result.added.size)
        assertEquals(2, result.skippedForLimit)
        assertEquals(PhotoLimits.MAX_PER_ITEM, photos.photosOf(item)!!.size)
        // 3 images and their 3 thumbnails; the skipped sources left nothing behind.
        assertEquals(6, store.files.size)
    }

    @Test
    fun `a full item accepts nothing and skips every source`() = runTest {
        add(item, listOf(textSource("a"), textSource("b"), textSource("c")))
        val result = outcome(add(item, listOf(textSource("d"))))
        assertTrue(result.added.isEmpty())
        assertEquals(1, result.skippedForLimit)
    }

    @Test
    fun `an unreadable or non-image source is counted as failed and the others are still added`() = runTest {
        val result = outcome(add(item, listOf(textSource("BAD pixels"), textSource("ok"))))
        assertEquals(1, result.added.size)
        assertEquals(1, result.failed)
        assertEquals(PhotoFailure.NOT_AN_IMAGE, result.lastFailure)
    }

    @Test
    fun `adding to a missing item is NotFound and stores nothing`() = runTest {
        val result = add(ChecklistItemId("gone"), listOf(textSource("a")))
        assertEquals(DomainResult.Failure(DomainError.NotFound), result)
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun `files saved for an item deleted meanwhile are removed again`() = runTest {
        val racing = object : com.dataloom.checklist.domain.repository.PhotoRepository by photos {
            override suspend fun addPhotos(
                itemId: ChecklistItemId,
                photos: List<com.dataloom.checklist.domain.photo.StoredPhoto>,
            ): List<com.dataloom.checklist.domain.model.ItemPhoto> = emptyList() // the item vanished
        }
        val result = AddItemPhotosUseCase(racing, store)(item, listOf(textSource("a")))
        assertEquals(DomainResult.Failure(DomainError.NotFound), result)
        assertTrue(store.files.isEmpty())
    }

    // ---- Remove, reorder, caption ----

    @Test
    fun `removing a photo deletes its row and both files`() = runTest {
        val added = outcome(add(item, listOf(textSource("a"), textSource("b")))).added
        RemoveItemPhotoUseCase(photos, store)(added[0].id)
        assertEquals(listOf(added[1].id), photos.photosOf(item)!!.map { it.id })
        assertTrue(added[0].fileName !in store.files)
        assertTrue(PhotoNames.thumbnailName(added[0].fileName) !in store.files)
        assertTrue(added[1].fileName in store.files)
    }

    @Test
    fun `removing a photo twice succeeds`() = runTest {
        val added = outcome(add(item, listOf(textSource("a")))).added.single()
        val remove = RemoveItemPhotoUseCase(photos, store)
        remove(added.id)
        assertTrue(remove(added.id) is DomainResult.Success)
    }

    @Test
    fun `moving right and left reorders the item photos`() = runTest {
        val a = outcome(add(item, listOf(textSource("a"), textSource("b"), textSource("c")))).added
        val reorder = ReorderItemPhotoUseCase(photos)
        reorder(a[0].id, 1)
        assertEquals(listOf(a[1].id, a[0].id, a[2].id), photos.photosOf(item)!!.map { it.id })
        reorder(a[2].id, 0)
        assertEquals(listOf(a[2].id, a[1].id, a[0].id), photos.photosOf(item)!!.map { it.id })
    }

    @Test
    fun `reorder rejects a negative index with a typed error`() = runTest {
        val a = outcome(add(item, listOf(textSource("a")))).added.single()
        val result = ReorderItemPhotoUseCase(photos)(a.id, -1) as DomainResult.Failure
        assertEquals(DomainError.Invalid(listOf(ValidationError.NEGATIVE_POSITION)), result.error)
    }

    @Test
    fun `caption is trimmed, kept on one line and cleared when blank`() = runTest {
        val a = outcome(add(item, listOf(textSource("a")))).added.single()
        val caption = SetPhotoCaptionUseCase(photos)
        caption(a.id, "  Red\nbottle  ")
        assertEquals("Red bottle", photos.photosOf(item)!!.single().caption)
        caption(a.id, "   ")
        assertNull(photos.photosOf(item)!!.single().caption)
    }

    @Test
    fun `caption over 80 characters is a typed error and is not saved`() = runTest {
        val a = outcome(add(item, listOf(textSource("a")))).added.single()
        val result = SetPhotoCaptionUseCase(photos)(a.id, "x".repeat(PhotoLimits.CAPTION_MAX + 1)) as DomainResult.Failure
        assertEquals(DomainError.Invalid(listOf(ValidationError.CAPTION_TOO_LONG)), result.error)
        assertNull(photos.photosOf(item)!!.single().caption)
        assertTrue(SetPhotoCaptionUseCase(photos)(a.id, "x".repeat(PhotoLimits.CAPTION_MAX)) is DomainResult.Success)
    }

    // ---- Orphan sweep ----

    @Test
    fun `sweep deletes old files without a row and keeps referenced and young ones`() = runTest {
        val now = 10 * PhotoLimits.ORPHAN_MIN_AGE_MILLIS
        val old = now - 2 * PhotoLimits.ORPHAN_MIN_AGE_MILLIS
        val kept = outcome(add(item, listOf(textSource("a")))).added.single()
        store.modified[kept.fileName] = old
        store.modified[PhotoNames.thumbnailName(kept.fileName)] = old
        store.put("orphan-old.jpg", lastModified = old)
        store.put("orphan-young.jpg", lastModified = now - 1000)

        val deleted = SweepOrphanPhotosUseCase(photos, store, Clock { now })()

        assertEquals(1, deleted)
        assertTrue(kept.fileName in store.files)
        assertTrue(PhotoNames.thumbnailName(kept.fileName) in store.files)
        assertTrue("orphan-old.jpg" !in store.files)
        assertTrue(PhotoNames.thumbnailName("orphan-old.jpg") !in store.files)
        assertTrue("orphan-young.jpg" in store.files)
        assertEquals(1, store.staleStagingCalls)
    }

    // ---- Delete use cases remove files after the rows ----

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val cleaner = StorePhotoFileCleaner(photos, store)

    @Test
    fun `deleting an item deletes its photo files`() = runTest {
        val groceries = catalog.seedCategory("groceries", "Groceries")
        val list = (CreateChecklistUseCase(repo)("Weekly", categoryIds = listOf(groceries.id)) as DomainResult.Success).value.id
        val section = repo.detail(list)!!.sections.single().id
        val itemId = repo.addItems(section, listOf(NewChecklistItem(null, null, "Rice", "en", null, null, null))).single()
        photos.addItem(itemId, section, list)
        val saved = outcome(AddItemPhotosUseCase(photos, store)(itemId, listOf(textSource("a"), textSource("b")))).added

        DeleteChecklistItemUseCase(repo, cleaner)(itemId)

        assertNull(repo.item(itemId))
        saved.forEach { assertTrue(it.fileName !in store.files) }
    }

    @Test
    fun `deleting a checklist or a section deletes the photo files of its items`() = runTest {
        val groceries = catalog.seedCategory("groceries", "Groceries")
        val list = (CreateChecklistUseCase(repo)("Weekly", categoryIds = listOf(groceries.id)) as DomainResult.Success).value.id
        val section = repo.detail(list)!!.sections.single().id
        val itemId = repo.addItems(section, listOf(NewChecklistItem(null, null, "Rice", "en", null, null, null))).single()
        photos.addItem(itemId, section, list)
        AddItemPhotosUseCase(photos, store)(itemId, listOf(textSource("a")))
        assertEquals(2, store.files.size)

        RemoveSectionUseCase(repo, cleaner)(section)
        assertTrue(store.files.isEmpty())

        val other = ChecklistItemId("other")
        photos.addItem(other, checklist = ChecklistId("cl-x"))
        AddItemPhotosUseCase(photos, store)(other, listOf(textSource("b")))
        assertEquals(2, store.files.size)
        DeleteChecklistUseCase(repo, cleaner)(ChecklistId("cl-x"))
        assertTrue(store.files.isEmpty())
    }
}

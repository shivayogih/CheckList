package com.dataloom.checklist.data.photo

import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoFailure
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.domain.photo.PhotoNames
import com.dataloom.checklist.domain.photo.SavePhotoResult
import com.dataloom.checklist.domain.photo.StagePhotoResult
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The image pipeline and the file layout, on a temp directory with Robolectric's native graphics
 * (real decoding and JPEG encoding, so orientation and EXIF results are real).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilePhotoStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val root: File by lazy { File(temp.root, "item_photos") }
    private val store: FilePhotoStore by lazy { FilePhotoStore(root, Dispatchers.Unconfined, SequentialIds("p")) }

    private suspend fun saved(source: ImageSource) = (store.save(source) as SavePhotoResult.Saved).photo

    @Test
    fun `save writes a jpeg and a 320 px thumbnail named after the same id`() = runTest {
        val photo = saved(TestImages.source(TestImages.jpegBytes(TestImages.halves(800, 600))))

        assertEquals("p-1.jpg", photo.fileName)
        assertEquals(800, photo.width)
        assertEquals(600, photo.height)
        val main = store.file(photo.fileName)
        val thumb = store.thumbnailFile(photo.fileName)
        assertEquals(main.length(), photo.byteSize)
        assertEquals("p-1_t.jpg", thumb.name)
        val thumbBitmap = TestImages.decode(thumb)
        assertEquals(PhotoLimits.THUMBNAIL_EDGE_PX, max(thumbBitmap.width, thumbBitmap.height))
        assertEquals(240, thumbBitmap.height)
    }

    @Test
    fun `a large photo is scaled so the long edge is 1600`() = runTest {
        val photo = saved(TestImages.source(TestImages.jpegBytes(TestImages.halves(4000, 3000), quality = 60)))
        assertEquals(1600, photo.width)
        assertEquals(1200, photo.height)
        val decoded = TestImages.decode(store.file(photo.fileName))
        assertEquals(1600, decoded.width)
        assertEquals(1200, decoded.height)
    }

    @Test
    fun `a small photo is never scaled up`() = runTest {
        val photo = saved(TestImages.source(TestImages.pngBytes(TestImages.halves(120, 80))))
        assertEquals(120, photo.width)
        assertEquals(80, photo.height)
    }

    @Test
    fun `the sample size keeps the decoded long edge at or above 1600`() {
        assertEquals(1, ImageEncoder.sampleSizeFor(1600))
        assertEquals(1, ImageEncoder.sampleSizeFor(3199))
        assertEquals(2, ImageEncoder.sampleSizeFor(3200))
        assertEquals(4, ImageEncoder.sampleSizeFor(6400))
        assertEquals(8, ImageEncoder.sampleSizeFor(16384))
    }

    @Test
    fun `exif orientation 6 rotates a landscape image into portrait`() = runTest {
        val source = TestImages.jpegWithExif(temp.root, width = 200, height = 100, orientation = ExifInterface.ORIENTATION_ROTATE_90, withGps = false)
        val photo = saved(TestImages.source(source))
        assertEquals(100, photo.width)
        assertEquals(200, photo.height)
        val decoded = TestImages.decode(store.file(photo.fileName))
        // The left (red) half is now on top, the right (blue) half at the bottom.
        assertTrue(Color.red(decoded.getPixel(50, 20)) > 180 && Color.blue(decoded.getPixel(50, 20)) < 80)
        assertTrue(Color.blue(decoded.getPixel(50, 180)) > 180 && Color.red(decoded.getPixel(50, 180)) < 80)
    }

    @Test
    fun `exif orientation 3 turns the image upside down and 2 mirrors it`() = runTest {
        val upside = saved(
            TestImages.source(TestImages.jpegWithExif(temp.root, 200, 100, ExifInterface.ORIENTATION_ROTATE_180, withGps = false)),
        )
        val rotated = TestImages.decode(store.file(upside.fileName))
        assertTrue(Color.blue(rotated.getPixel(20, 50)) > 180)
        assertTrue(Color.red(rotated.getPixel(180, 50)) > 180)

        val mirrored = saved(
            TestImages.source(TestImages.jpegWithExif(temp.root, 200, 100, ExifInterface.ORIENTATION_FLIP_HORIZONTAL, withGps = false)),
        )
        val flipped = TestImages.decode(store.file(mirrored.fileName))
        assertTrue(Color.blue(flipped.getPixel(20, 50)) > 180)
        assertTrue(Color.red(flipped.getPixel(180, 50)) > 180)
    }

    @Test
    fun `a gps tagged input produces files with no location and no exif`() = runTest {
        val source = TestImages.jpegWithExif(temp.root, 300, 200, ExifInterface.ORIENTATION_NORMAL, withGps = true)
        // The input really carries a location.
        assertNotNull(ExifInterface(source).latLong)

        val photo = saved(TestImages.source(source))

        listOf(store.file(photo.fileName), store.thumbnailFile(photo.fileName)).forEach { file ->
            val exif = ExifInterface(file)
            assertNull("No location may survive in ${file.name}", exif.latLong)
            assertFalse(exif.hasAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertFalse(exif.hasAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertFalse(exif.hasAttribute(ExifInterface.TAG_MAKE))
            // A defensive byte-level check: the GPS tag block is not in the file at all.
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("GPS"))
        }
    }

    @Test
    fun `transparent png pixels become white, not black`() = runTest {
        val clear = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        val photo = saved(TestImages.source(TestImages.pngBytes(clear)))
        val decoded = TestImages.decode(store.file(photo.fileName))
        assertTrue(Color.red(decoded.getPixel(32, 32)) > 240)
    }

    @Test
    fun `bytes that are not an image are refused and leave no file`() = runTest {
        val result = store.save(TestImages.source("not an image at all".toByteArray()))
        assertEquals(SavePhotoResult.Failed(PhotoFailure.NOT_AN_IMAGE), result)
        assertTrue(store.listStored().isEmpty())
    }

    @Test
    fun `an absurd pixel count is refused from the header before decoding`() = runTest {
        val result = store.save(TestImages.source(TestImages.pngClaiming(30_000, 30_000)))
        assertEquals(SavePhotoResult.Failed(PhotoFailure.TOO_MANY_PIXELS), result)
    }

    @Test
    fun `a source that cannot be opened is unreadable`() = runTest {
        val result = store.save { throw IOException("provider gone") }
        assertEquals(SavePhotoResult.Failed(PhotoFailure.UNREADABLE), result)
        val denied = store.save { throw SecurityException("permission revoked") }
        assertEquals(SavePhotoResult.Failed(PhotoFailure.UNREADABLE), denied)
    }

    @Test
    fun `decoding runs on the injected dispatcher`() = runTest {
        var dispatched = 0
        val counting = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatched++
                block.run()
            }
        }
        val counted = FilePhotoStore(File(temp.root, "counted"), counting, SequentialIds("c"))
        counted.save(TestImages.source(TestImages.jpegBytes(TestImages.halves(40, 40))))
        assertTrue("save must switch to the injected dispatcher", dispatched > 0)
    }

    @Test
    fun `delete removes the image and its thumbnail and ignores unknown or unsafe names`() = runTest {
        val photo = saved(TestImages.source(TestImages.jpegBytes(TestImages.halves(40, 40))))
        assertTrue(store.file(photo.fileName).isFile)

        store.delete("../../etc/passwd")
        store.delete("missing.jpg")
        assertTrue(store.file(photo.fileName).isFile)

        store.delete(photo.fileName)
        assertFalse(store.file(photo.fileName).exists())
        assertFalse(store.thumbnailFile(photo.fileName).exists())
    }

    @Test
    fun `copy makes an independent pair of files with a new name`() = runTest {
        val photo = saved(TestImages.source(TestImages.jpegBytes(TestImages.halves(60, 40))))
        val copy = store.copy(photo.fileName)

        assertNotNull(copy)
        assertTrue(copy != photo.fileName)
        assertTrue(store.file(copy!!).isFile)
        assertTrue(store.thumbnailFile(copy).isFile)
        assertTrue(photo.fileName.let { store.file(it).readBytes().contentEquals(store.file(copy).readBytes()) })

        store.delete(photo.fileName)
        assertTrue("The copy survives deleting the original", store.file(copy).isFile)
    }

    @Test
    fun `copying a missing file returns null`() = runTest {
        assertNull(store.copy("missing.jpg"))
        assertNull(store.copy("../outside.jpg"))
    }

    @Test
    fun `open returns null for a missing file instead of failing`() = runTest {
        assertNull(store.open("missing.jpg"))
        assertNull(store.openThumbnail("missing.jpg"))
        val photo = saved(TestImages.source(TestImages.jpegBytes(TestImages.halves(40, 40))))
        store.open(photo.fileName)!!.use { assertTrue(it.readBytes().isNotEmpty()) }
    }

    @Test
    fun `a staged photo is invisible until committed and removable by discard`() = runTest {
        val staged = (store.stage(TestImages.source(TestImages.jpegBytes(TestImages.halves(40, 40)))) as StagePhotoResult.Staged).staged
        assertFalse(store.file(staged.photo.fileName).exists())
        assertTrue(store.listStored().isEmpty())

        assertTrue(store.commit(staged))
        assertTrue(store.file(staged.photo.fileName).isFile)
        assertTrue(store.thumbnailFile(staged.photo.fileName).isFile)
        assertFalse("Committing twice is not possible", store.commit(staged))

        val other = (store.stage(TestImages.source(TestImages.jpegBytes(TestImages.halves(40, 40)))) as StagePhotoResult.Staged).staged
        store.discard(other)
        assertFalse(store.commit(other))
        assertFalse(store.file(other.photo.fileName).exists())
    }

    @Test
    fun `stale staging files and scratch directories are swept, fresh ones kept`() = runTest {
        val stale = (store.stage(TestImages.source(TestImages.jpegBytes(TestImages.halves(40, 40)))) as StagePhotoResult.Staged).staged
        val scratch = store.newScratchDirectory()
        File(scratch, "raw-1.bin").writeBytes(ByteArray(10))
        val now = System.currentTimeMillis()

        store.deleteStaleStaging(now, olderThanMillis = PhotoLimits.ORPHAN_MIN_AGE_MILLIS)
        assertTrue("Fresh staging is kept", scratch.isDirectory)

        store.deleteStaleStaging(now + 2 * PhotoLimits.ORPHAN_MIN_AGE_MILLIS, olderThanMillis = PhotoLimits.ORPHAN_MIN_AGE_MILLIS)
        assertFalse(scratch.exists())
        assertFalse(store.commit(stale))
    }

    @Test
    fun `scratch directories are removed only when the store made them`() = runTest {
        val scratch = store.newScratchDirectory()
        assertTrue(scratch.isDirectory)
        store.deleteScratchDirectory(scratch)
        assertFalse(scratch.exists())

        val foreign = temp.newFolder("foreign")
        store.deleteScratchDirectory(foreign)
        assertTrue(foreign.isDirectory)
    }

    @Test
    fun `file names are checked so a hostile name cannot leave the directory`() {
        assertTrue(PhotoNames.isSafeName("3f2a9c1e-0000-4000-8000-123456789abc.jpg"))
        assertFalse(PhotoNames.isSafeName("../x.jpg"))
        assertFalse(PhotoNames.isSafeName("a/b.jpg"))
        assertFalse(PhotoNames.isSafeName(".hidden.jpg"))
        assertFalse(PhotoNames.isSafeName("x.png"))
        assertEquals("invalid-name", store.file("../x.jpg").name)
    }
}

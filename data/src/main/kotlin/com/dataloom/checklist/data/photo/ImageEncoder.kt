package com.dataloom.checklist.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoFailure
import com.dataloom.checklist.domain.photo.PhotoLimits
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The two JPEGs made from one source image. */
internal class EncodedImage(
    val main: ByteArray,
    val width: Int,
    val height: Int,
    val thumbnail: ByteArray,
)

internal sealed interface EncodeResult {
    class Ok(val image: EncodedImage) : EncodeResult

    class Failed(val reason: PhotoFailure) : EncodeResult
}

/**
 * The image pipeline of docs/item-photos-spec.md. Blocking; the store runs it on its IO dispatcher.
 *
 * 1. Decode the bounds only and refuse absurd pixel counts before any pixel is decoded.
 * 2. Read the EXIF orientation.
 * 3. Decode with `inSampleSize`, so a 50 megapixel photo never needs 200 MB of memory.
 * 4. Apply the orientation and scale so the long edge is at most 1600 px (never up).
 * 5. Re-encode as JPEG quality 80, plus a 320 px thumbnail.
 *
 * Re-encoding writes no EXIF block, which removes the GPS location and every other tag.
 */
@Suppress("TooManyFunctions") // The pipeline steps, one small function each.
internal object ImageEncoder {

    /** Why a source was refused; thrown inside the pipeline and turned into [EncodeResult.Failed] once. */
    private class Refused(val reason: PhotoFailure) : RuntimeException(null, null, false, false)

    fun encode(source: ImageSource): EncodeResult = try {
        EncodeResult.Ok(process(source))
    } catch (refused: Refused) {
        EncodeResult.Failed(refused.reason)
    }

    private fun process(source: ImageSource): EncodedImage {
        val (width, height) = readBounds(source)
        val orientation = readOrientation(source)
        val decoded = decode(source, max(width, height))
        return try {
            val oriented = orientAndScale(decoded, orientation)
            val flat = flattenOnWhite(oriented)
            val thumb = scaledTo(flat, PhotoLimits.THUMBNAIL_EDGE_PX)
            val result = EncodedImage(jpeg(flat), flat.width, flat.height, jpeg(thumb))
            if (thumb !== flat) thumb.recycle()
            if (flat !== oriented) flat.recycle()
            if (oriented !== decoded) oriented.recycle()
            result
        } catch (_: OutOfMemoryError) {
            throw Refused(PhotoFailure.TOO_MANY_PIXELS)
        } finally {
            decoded.recycle()
        }
    }

    /** Step 1: the pixel size from the header alone; absurd sizes are refused before anything is decoded. */
    private fun readBounds(source: ImageSource): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeStream(source, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw Refused(PhotoFailure.NOT_AN_IMAGE)
        if (width.toLong() * height > PhotoLimits.MAX_SOURCE_PIXELS) throw Refused(PhotoFailure.TOO_MANY_PIXELS)
        return width to height
    }

    /** Step 3: decodes downsampled so a 50 megapixel photo never needs 200 MB of memory. */
    private fun decode(source: ImageSource, longEdge: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(longEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return decodeStream(source, options) ?: throw Refused(PhotoFailure.NOT_AN_IMAGE)
    }

    @Suppress("ThrowsCount") // One mapping from platform failures to the three reasons the UI knows.
    private fun decodeStream(source: ImageSource, options: BitmapFactory.Options): Bitmap? = try {
        source.openStream().use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: IOException) {
        throw Refused(PhotoFailure.UNREADABLE)
    } catch (_: SecurityException) {
        throw Refused(PhotoFailure.UNREADABLE)
    } catch (_: OutOfMemoryError) {
        throw Refused(PhotoFailure.TOO_MANY_PIXELS)
    }

    /**
     * The largest power of two that keeps the decoded long edge at least [PhotoLimits.LONG_EDGE_PX],
     * so quality is lost only by the final, exact scaling.
     */
    fun sampleSizeFor(longEdge: Int): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= PhotoLimits.LONG_EDGE_PX) sample *= 2
        return sample
    }

    /** Size after scaling the long edge down to at most [edge]; never larger than the input. */
    fun fitWithin(width: Int, height: Int, edge: Int): Pair<Int, Int> {
        val longEdge = max(width, height)
        if (longEdge <= edge) return width to height
        val scale = edge.toFloat() / longEdge
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    private fun readOrientation(source: ImageSource): Int = try {
        source.openStream().use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    } catch (_: RuntimeException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun orientAndScale(decoded: Bitmap, orientation: Int): Bitmap {
        val matrix = orientationMatrix(orientation)
        val longEdge = max(decoded.width, decoded.height)
        val scale = min(1f, PhotoLimits.LONG_EDGE_PX.toFloat() / longEdge)
        if (scale < 1f) matrix.postScale(scale, scale)
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    /** The rotation and mirroring that make the pixels upright for an EXIF orientation value. */
    private fun orientationMatrix(orientation: Int): Matrix {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(MIRROR, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(HALF_TURN)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(HALF_TURN)
                matrix.postScale(MIRROR, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(QUARTER_TURN)
                matrix.postScale(MIRROR, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(QUARTER_TURN)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-QUARTER_TURN)
                matrix.postScale(MIRROR, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-QUARTER_TURN)
        }
        return matrix
    }

    private const val QUARTER_TURN = 90f
    private const val HALF_TURN = 180f
    private const val MIRROR = -1f

    /** JPEG has no alpha: transparent PNG and WebP pixels become white instead of black. */
    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return flat
    }

    private fun scaledTo(bitmap: Bitmap, edge: Int): Bitmap {
        val (w, h) = fitWithin(bitmap.width, bitmap.height, edge)
        return if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun jpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoLimits.JPEG_QUALITY, out)) { "JPEG compression failed" }
        return out.toByteArray()
    }
}

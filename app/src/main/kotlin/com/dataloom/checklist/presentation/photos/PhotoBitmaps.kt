package com.dataloom.checklist.presentation.photos

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import java.io.File
import kotlin.math.max
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Decodes photo files with sub-sampling and keeps the results in a small memory cache (a stand-in
 * for an image library: the files are small, private and already re-encoded by the store).
 * Names are `<uuid>.jpg`, so a path never changes content and is a safe cache key.
 */
internal object PhotoBitmaps {
    private const val CACHE_DIVISOR = 16
    private const val BYTES_PER_KIB = 1024
    private const val BYTES_PER_PIXEL = 4
    private const val DECODER_THREADS = 2

    /**
     * Decoding runs on this small pool of daemon threads. Composables cannot take an injected
     * dispatcher, and a pool of its own keeps thumbnail decoding from competing with database work.
     */
    private val decoder = Executors.newFixedThreadPool(DECODER_THREADS) { task ->
        Thread(task, "photo-decoder").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / BYTES_PER_KIB / CACHE_DIVISOR).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * BYTES_PER_PIXEL / BYTES_PER_KIB
    }

    suspend fun loadAsync(file: File, maxEdgePx: Int): ImageBitmap? = withContext(decoder) { load(file, maxEdgePx) }

    fun cached(file: File, maxEdgePx: Int): ImageBitmap? = cache.get(key(file, maxEdgePx))

    /** Blocking; run on an I/O dispatcher. Null when the file is missing or not an image. */
    fun load(file: File, maxEdgePx: Int): ImageBitmap? =
        cached(file, maxEdgePx) ?: decode(file, maxEdgePx)?.also { cache.put(key(file, maxEdgePx), it) }

    private fun decode(file: File, maxEdgePx: Int): ImageBitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val edge = max(bounds.outWidth, bounds.outHeight)
        if (edge <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(edge, maxEdgePx) }
        return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }

    /** Largest power of two that keeps the decoded long edge at [maxEdgePx] or more. */
    fun sampleSize(sourceEdge: Int, maxEdgePx: Int): Int {
        var sample = 1
        while (sourceEdge / (sample * 2) >= maxEdgePx) sample *= 2
        return sample
    }

    private fun key(file: File, maxEdgePx: Int) = "${file.path}@$maxEdgePx"
}

/** The decoded photo, or null while loading or when the file is missing. */
@Composable
fun rememberPhotoBitmap(file: File, maxEdgePx: Int): ImageBitmap? {
    val bitmap by produceState(PhotoBitmaps.cached(file, maxEdgePx), file, maxEdgePx) {
        value = PhotoBitmaps.loadAsync(file, maxEdgePx)
    }
    return bitmap
}

private const val THUMBNAIL_DECODE_PX = 320

/**
 * A rounded square thumbnail loaded from the private thumbnail file. It is decorative: the
 * caller adds the spoken description to the element that contains it.
 */
@Composable
fun PhotoThumbnail(file: File, modifier: Modifier = Modifier) {
    val bitmap = rememberPhotoBitmap(file, THUMBNAIL_DECODE_PX)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Missing file or still loading: a neutral picture icon, never a broken image.
            Icon(
                painterResource(R.drawable.ic_photo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

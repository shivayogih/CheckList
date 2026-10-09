package com.dataloom.checklist.presentation.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoLimits
import java.io.File
import java.io.IOException

/** Sources chosen by the user, and what to call once they have been processed (to delete a camera temp file). */
fun interface PhotoSourcesListener {
    fun onSources(sources: List<ImageSource>, onFinished: () -> Unit)
}

/** What the "Add photo" sheet can do. [takePhoto] is null on phones without a camera app. */
class PhotoAdder(val pickFromGallery: () -> Unit, val takePhoto: (() -> Unit)?)

/** Where camera pictures are written before they are copied into the photo store (cache, never backed up). */
internal const val CAMERA_DIRECTORY = "camera"

/**
 * Wires the system Photo Picker and the camera. Neither needs a permission: the Photo Picker hands
 * out only the pictures the user selects, and the camera app writes to a temporary file this app
 * shares through the existing FileProvider. [freeSlots] is how many more photos fit; with one slot
 * left the single-selection picker is used, because the multi-selection one needs a limit above 1.
 * Extra pictures from a picker that ignores the limit are cut off by the ViewModel and counted.
 */
@Composable
fun rememberPhotoAdder(freeSlots: Int, listener: PhotoSourcesListener): PhotoAdder {
    val context = LocalContext.current
    val currentListener by rememberUpdatedState(listener)
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) currentListener.onSources(listOf(uriSource(context, uri))) {}
    }
    val multiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(freeSlots.coerceIn(MIN_MULTIPLE, PhotoLimits.MAX_PER_ITEM)),
    ) { uris ->
        if (uris.isNotEmpty()) currentListener.onSources(uris.map { uriSource(context, it) }) {}
    }

    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pending?.let { File(it) }
        pending = null
        if (file != null && taken) {
            currentListener.onSources(listOf(ImageSource { file.inputStream() })) { file.delete() }
        } else {
            file?.delete()
        }
    }

    return PhotoAdder(
        pickFromGallery = { if (freeSlots <= 1) single.launch(imageOnly) else multiple.launch(imageOnly) },
        takePhoto = if (hasCameraApp(context)) {
            {
                val file = newCameraFile(context)
                pending = file.path
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file))
            }
        } else {
            null
        },
    )
}

private const val MIN_MULTIPLE = 2
private const val AUTHORITY_SUFFIX = ".fileprovider"

private fun uriSource(context: Context, uri: Uri) = ImageSource {
    context.contentResolver.openInputStream(uri) ?: throw IOException("No stream for the picked photo")
}

private fun newCameraFile(context: Context): File {
    val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
    return File(directory, "capture-${System.nanoTime()}.jpg")
}

/** True when some app can take a picture; the "Take photo" row is hidden otherwise. */
private fun hasCameraApp(context: Context): Boolean {
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    return intent.resolveActivity(context.packageManager) != null
}

/** Removes camera pictures left by an interrupted capture. Called once at app start. */
fun clearCameraCache(context: Context) {
    File(context.cacheDir, CAMERA_DIRECTORY).deleteRecursively()
}

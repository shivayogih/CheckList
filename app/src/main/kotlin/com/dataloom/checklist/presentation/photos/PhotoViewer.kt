package com.dataloom.checklist.presentation.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.presentation.common.ResizeDialogForKeyboard
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.formatCount
import com.dataloom.checklist.presentation.common.toUiText
import com.dataloom.checklist.presentation.components.AppIconButton
import com.dataloom.checklist.presentation.components.ConfirmDialog
import com.dataloom.checklist.presentation.components.FormField
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.presentation.components.inputLength

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val ZOOM_STEP = 1.5f
private const val VIEWER_DECODE_PX = PhotoLimits.LONG_EDGE_PX

/**
 * Full-screen viewer for the photos of one item: pinch and double-tap zoom (with visible zoom
 * buttons as the alternative), visible Previous/Next buttons, an editable caption and a Delete
 * button with a confirmation. [photos] is the live list, so a deleted photo disappears at once
 * and the viewer closes when the last one is gone.
 */
@Composable
fun PhotoViewerDialog(
    itemName: String,
    photos: List<PhotoUi>,
    startIndex: Int,
    onCaption: (PhotoUi, String) -> Unit,
    onDelete: (PhotoUi) -> Unit,
    onClose: () -> Unit,
) {
    var index by rememberSaveable { mutableStateOf(startIndex) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val current = index.coerceIn(0, photos.lastIndex)
    val photo = photos[current]

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The caption field must stay above the keyboard (docs/ui-keyboard-and-insets.md): the photo shrinks instead.
        ResizeDialogForKeyboard()
        PhotoViewerContent(
            itemName = itemName,
            photos = photos,
            index = current,
            onIndexChange = { index = it },
            onCaption = onCaption,
            onDelete = { confirmDelete = true },
            onClose = onClose,
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.photo_delete_title),
            message = stringResource(R.string.photo_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete(photo)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * The viewer's screen, without the dialog window around it, so it can be drawn in screenshot
 * tests. Always dark, like a gallery, whatever the app theme is. [onDelete] only asks: the
 * dialog shows the confirmation.
 */
@Composable
internal fun PhotoViewerContent(
    itemName: String,
    photos: List<PhotoUi>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onCaption: (PhotoUi, String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val photo = photos[index]
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    MaterialTheme(colorScheme = darkColorScheme(), typography = typography, shapes = shapes) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ViewerTopBar(itemName, onClose)
            ZoomableImage(photo, index, photos.size, itemName, Modifier.weight(1f))
            ViewerControls(
                position = index,
                count = photos.size,
                onPrevious = { onIndexChange(index - 1) },
                onNext = { onIndexChange(index + 1) },
            )
            // One instance per photo, so a caption typed for one is saved when the next is shown.
            key(photo.id) { CaptionField(photo, onCaption) }
            OutlinedActionButton(
                text = stringResource(R.string.photo_viewer_delete),
                onClick = onDelete,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ViewerTopBar(itemName: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.photo_viewer_title, itemName),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        AppIconButton(
            painterResource(R.drawable.ic_close),
            stringResource(R.string.photo_viewer_close),
            onClick = onClose,
            tint = Color.White,
        )
    }
}

/** Pinch to zoom and pan, double-tap to toggle; the zoom resets when another photo is shown. */
@Composable
private fun ZoomableImage(photo: PhotoUi, position: Int, count: Int, itemName: String, modifier: Modifier) {
    var scale by remember(photo.id) { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    val bitmap = rememberPhotoBitmap(photo.image, VIEWER_DECODE_PX)
    val description = photoDescription(position, count, itemName, photo.caption)
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    offset = if (scale == MIN_ZOOM) Offset.Zero else offset + pan
                }
            }
            .pointerInput(photo.id) {
                detectTapGestures(onDoubleTap = {
                    val zoomedOut = scale == MIN_ZOOM
                    scale = if (zoomedOut) DOUBLE_TAP_ZOOM else MIN_ZOOM
                    offset = Offset.Zero
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        } else {
            Text(
                stringResource(R.string.photo_missing),
                color = Color.White,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
        ZoomButtons(
            onZoomIn = { scale = (scale * ZOOM_STEP).coerceAtMost(MAX_ZOOM) },
            onZoomOut = {
                scale = (scale / ZOOM_STEP).coerceAtLeast(MIN_ZOOM)
                if (scale == MIN_ZOOM) offset = Offset.Zero
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun ZoomButtons(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier) {
    Row(modifier = modifier.padding(8.dp)) {
        AppIconButton(
            painterResource(R.drawable.ic_zoom_out),
            stringResource(R.string.photo_viewer_zoom_out),
            onClick = onZoomOut,
            tint = Color.White,
        )
        AppIconButton(
            painterResource(R.drawable.ic_zoom_in),
            stringResource(R.string.photo_viewer_zoom_in),
            onClick = onZoomIn,
            tint = Color.White,
        )
    }
}

@Composable
private fun ViewerControls(position: Int, count: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppIconButton(
            painterResource(R.drawable.ic_chevron_left),
            stringResource(R.string.photo_viewer_previous),
            onClick = onPrevious,
            enabled = position > 0,
            tint = Color.White,
        )
        Text(
            stringResource(R.string.photo_viewer_position, formatCount(position + 1), formatCount(count)),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        AppIconButton(
            painterResource(R.drawable.ic_chevron_right),
            stringResource(R.string.photo_viewer_next),
            onClick = onNext,
            enabled = position < count - 1,
            tint = Color.White,
        )
    }
}

/** One short line; saved when the field loses focus, on Done and when the viewer moves to another photo. */
@Composable
private fun CaptionField(photo: PhotoUi, onCaption: (PhotoUi, String) -> Unit) {
    var text by remember(photo.id) { mutableStateOf(photo.caption.orEmpty()) }
    val tooLong = text.inputLength() > PhotoLimits.CAPTION_MAX
    // A caption over the limit shows the domain's error and is not saved until it is shortened.
    val save by rememberUpdatedState {
        if (!tooLong && InputText.normalize(text) != photo.caption.orEmpty()) onCaption(photo, text)
    }
    val errorText = if (tooLong) ValidationError.CAPTION_TOO_LONG.toUiText().asString() else null
    val focus = LocalFocusManager.current
    DisposableEffect(photo.id) { onDispose { save() } }
    FormField(
        label = stringResource(R.string.photo_caption_label),
        value = text,
        onValueChange = { text = InputText.forField(it, PhotoLimits.CAPTION_MAX) },
        errorText = errorText,
        maxLength = PhotoLimits.CAPTION_MAX,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            save()
            focus.clearFocus()
        }),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .onFocusChanged { if (!it.hasFocus) save() },
    )
}

/** "Photo 1 of 2 for Rice", plus the caption when there is one (spoken description of a photo). */
@Composable
internal fun photoDescription(position: Int, count: Int, itemName: String, caption: String?): String =
    if (caption.isNullOrBlank()) {
        stringResource(R.string.photo_cd, formatCount(position + 1), formatCount(count), itemName)
    } else {
        stringResource(R.string.photo_cd_caption, formatCount(position + 1), formatCount(count), itemName, caption)
    }

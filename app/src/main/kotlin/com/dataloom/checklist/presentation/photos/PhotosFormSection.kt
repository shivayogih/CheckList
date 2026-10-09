package com.dataloom.checklist.presentation.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.formatCount
import com.dataloom.checklist.presentation.components.AppIconButton
import com.dataloom.checklist.presentation.components.AppModalBottomSheet

private val TileSize = 96.dp

/**
 * The "Photos (optional)" part of the item form: the current photos, each with visible move-left,
 * remove and move-right buttons (no long-press or drag), a placeholder while photos are being
 * processed, the "N of 3 photos" count and the "Add photo" tile.
 */
@Composable
fun PhotosFormSection(
    photos: List<FormPhotoUi>,
    processing: Int,
    message: UiText?,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.photos_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.photos_count, formatCount(photos.size), formatCount(PhotoLimits.MAX_PER_ITEM)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        photos.forEachIndexed { index, photo ->
            PhotoTile(
                number = index + 1,
                photo = photo,
                canMoveLeft = index > 0,
                canMoveRight = index < photos.lastIndex,
                onRemove = { onRemove(photo.key) },
                onMove = { delta -> onMove(photo.key, delta) },
            )
        }
        repeat(processing) { ProcessingTile() }
        message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        if (photos.size + processing < PhotoLimits.MAX_PER_ITEM) AddPhotoTile(onAdd)
    }
}

@Composable
private fun PhotoTile(
    number: Int,
    photo: FormPhotoUi,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoThumbnail(photo.thumbnail, Modifier.size(TileSize))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            AppIconButton(
                painterResource(R.drawable.ic_chevron_left),
                stringResource(R.string.photos_move_left_cd, formatCount(number)),
                onClick = { onMove(-1) },
                enabled = canMoveLeft,
            )
            AppIconButton(
                painterResource(R.drawable.ic_close),
                stringResource(R.string.photos_remove_cd, formatCount(number)),
                onClick = onRemove,
            )
            AppIconButton(
                painterResource(R.drawable.ic_chevron_right),
                stringResource(R.string.photos_move_right_cd, formatCount(number)),
                onClick = { onMove(+1) },
                enabled = canMoveRight,
            )
        }
    }
}

@Composable
private fun ProcessingTile() {
    val description = stringResource(R.string.photos_processing)
    Box(
        modifier = Modifier
            .size(TileSize)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(TileSize),
        ) {}
        CircularProgressIndicator()
    }
}

@Composable
private fun AddPhotoTile(onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .size(TileSize)
            .clickable(role = Role.Button, onClick = onAdd),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(
                stringResource(R.string.photos_add),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
            )
        }
    }
}

/** The two big choices behind the "Add photo" tile. [onTake] is null when no camera app exists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onTake: (() -> Unit)?, onPick: () -> Unit, onDismiss: () -> Unit) {
    AppModalBottomSheet(title = stringResource(R.string.photos_add), onDismissRequest = onDismiss) {
        Column {
            if (onTake != null) SourceRow(stringResource(R.string.photos_take), onTake)
            SourceRow(stringResource(R.string.photos_pick), onPick)
        }
    }
}

@Composable
private fun SourceRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

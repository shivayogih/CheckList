package com.dataloom.checklist.presentation.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.formatCount

/** Side of the thumbnail on an item row (docs/item-photos-spec.md). */
private val ThumbnailSize = 48.dp

/**
 * The photo slot at the end of an item row: the first thumbnail, a "+N" badge when more photos
 * exist, and a tap that opens the viewer. Self-contained so the row layout can change around it.
 * Draws nothing when [photos] is empty.
 */
@Composable
fun ItemPhotoSlot(photos: List<PhotoUi>, itemName: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val first = photos.firstOrNull() ?: return
    val description = pluralStringResource(R.plurals.photos_view_cd, photos.size, formatCount(photos.size), itemName)
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(ThumbnailSize + 8.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PhotoThumbnail(first.thumbnail, Modifier.size(ThumbnailSize))
        val more = photos.size - 1
        if (more > 0) {
            // The count is already in the description; the badge is only for eyes.
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clearAndSetSemantics { },
            ) {
                Text(
                    stringResource(R.string.photos_more_badge, formatCount(more)),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

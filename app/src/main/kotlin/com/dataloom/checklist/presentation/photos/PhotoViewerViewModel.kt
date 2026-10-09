package com.dataloom.checklist.presentation.photos

import androidx.lifecycle.ViewModel
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.usecase.RemoveItemPhotoUseCase
import com.dataloom.checklist.domain.usecase.SetPhotoCaptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The two writes the photo viewer makes. The viewer shows the live list from the screen it is
 * opened on, so there is no state here: a change comes back through the database. The writes run
 * in the application scope, so a caption typed just before the viewer closes is still saved.
 */
@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val removePhoto: RemoveItemPhotoUseCase,
    private val setCaption: SetPhotoCaptionUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    fun delete(id: PhotoId) {
        applicationScope.launch { removePhoto(id) }
    }

    /** The viewer limits the field to the caption length, so a rejected caption cannot happen here. */
    fun saveCaption(id: PhotoId, caption: String) {
        applicationScope.launch { setCaption(id, caption) }
    }
}

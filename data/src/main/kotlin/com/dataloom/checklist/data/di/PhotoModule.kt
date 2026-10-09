package com.dataloom.checklist.data.di

import android.content.Context
import com.dataloom.checklist.data.photo.FilePhotoStore
import com.dataloom.checklist.data.repository.RoomPhotoRepository
import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.domain.common.IoDispatcher
import com.dataloom.checklist.domain.photo.PhotoFileCleaner
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.StorePhotoFileCleaner
import com.dataloom.checklist.domain.repository.PhotoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** Item photos (CL-210): rows in Room, files in `filesDir/item_photos`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoModule {

    @Binds
    @Singleton
    abstract fun bindPhotoRepository(impl: RoomPhotoRepository): PhotoRepository

    @Binds
    abstract fun bindPhotoFileCleaner(impl: StorePhotoFileCleaner): PhotoFileCleaner

    companion object {
        const val PHOTO_DIRECTORY = "item_photos"

        @Provides
        @Singleton
        fun providePhotoStore(
            @ApplicationContext context: Context,
            @IoDispatcher io: CoroutineDispatcher,
            ids: IdGenerator,
        ): PhotoStore = FilePhotoStore(File(context.filesDir, PHOTO_DIRECTORY), io, ids)
    }
}

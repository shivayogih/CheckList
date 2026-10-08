package com.dataloom.checklist.domain.fake

import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory profile store. [storageAvailable] = false simulates a failing Keystore. */
class FakeProfileRepository : ProfileRepository {

    val state = MutableStateFlow<ProfileState>(ProfileState.NotSet)
    var storageAvailable = true
    var saveCalls = 0

    override fun observeProfile(): Flow<ProfileState> = state

    override suspend fun saveProfile(profile: UserProfile): Boolean {
        saveCalls++
        if (!storageAvailable) return false
        state.value = ProfileState.Available(profile)
        return true
    }

    override suspend fun clearProfile() {
        state.value = ProfileState.NotSet
    }

    override suspend fun acknowledgeReset() {
        if (state.value == ProfileState.Reset) state.value = ProfileState.NotSet
    }
}

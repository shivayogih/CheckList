package com.dataloom.checklist.domain.repository

import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * The single, encrypted user profile. Implementations never throw for key or decryption problems:
 * they report them through [ProfileState] and erase data that can no longer be decrypted, so a
 * broken Keystore cannot crash the app.
 */
interface ProfileRepository {

    fun observeProfile(): Flow<ProfileState>

    /**
     * Encrypts and stores [profile], replacing any previous one. Callers validate first.
     * Returns false when secure storage is unavailable; nothing is ever stored in plain text.
     */
    suspend fun saveProfile(profile: UserProfile): Boolean

    /** Deletes the profile and destroys its keys, so old ciphertext can never be decrypted again. */
    suspend fun clearProfile()

    /** Dismisses [ProfileState.Reset]; the state becomes [ProfileState.NotSet]. */
    suspend fun acknowledgeReset()
}

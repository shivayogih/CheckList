package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.repository.ProfileRepository
import com.dataloom.checklist.domain.validation.ProfileValidator
import com.dataloom.checklist.domain.validation.ValidationResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveProfileUseCase @Inject constructor(private val profiles: ProfileRepository) {
    operator fun invoke(): Flow<ProfileState> = profiles.observeProfile()
}

/**
 * Validates and stores the profile. Every field is optional. Returns the normalized profile that was
 * saved, every field error at once, or [DomainError.SecureStorageUnavailable] when it cannot be
 * encrypted. When every field is blank there is nothing to keep: the stored profile (if any) is
 * deleted and an empty profile is returned, so saving an emptied form never stores an empty row.
 * First-run setup does not call this at all for a blank form (it must not erase an existing profile).
 */
class SaveProfileUseCase @Inject constructor(private val profiles: ProfileRepository) {
    suspend operator fun invoke(
        displayName: String?,
        email: String?,
        phone: String?,
        address: String? = null,
        phoneCountry: String? = null,
    ): DomainResult<UserProfile> {
        val profile = when (val result = ProfileValidator.validate(displayName, email, phone, address, phoneCountry)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
        }
        if (profile.isEmpty) {
            profiles.clearProfile()
            return success(profile)
        }
        if (!profiles.saveProfile(profile)) return failure(DomainError.SecureStorageUnavailable)
        return success(profile)
    }
}

/** Deletes the profile and its keys. Always succeeds; also the way out of [ProfileState.Unavailable]. */
class ClearProfileUseCase @Inject constructor(private val profiles: ProfileRepository) {
    suspend operator fun invoke() = profiles.clearProfile()
}

/** The user has seen the "your profile was reset" notice ([ProfileState.Reset]). */
class AcknowledgeProfileResetUseCase @Inject constructor(private val profiles: ProfileRepository) {
    suspend operator fun invoke() = profiles.acknowledgeReset()
}

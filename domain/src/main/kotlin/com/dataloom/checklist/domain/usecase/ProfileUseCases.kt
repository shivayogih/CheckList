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
 * Validates and stores the profile. Returns the normalized profile that was saved, every field
 * error at once, or [DomainError.SecureStorageUnavailable] when it cannot be encrypted.
 */
class SaveProfileUseCase @Inject constructor(private val profiles: ProfileRepository) {
    suspend operator fun invoke(displayName: String, email: String?, phone: String?): DomainResult<UserProfile> {
        val profile = when (val result = ProfileValidator.validate(displayName, email, phone)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
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

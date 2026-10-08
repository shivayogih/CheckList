package com.dataloom.checklist.domain.model

/**
 * The user's own details, kept only on this device and only encrypted (architecture section 19.1).
 *
 * Data minimization: the Phase 0 sketch also listed a postal address and home/office locations. No
 * v1 feature uses them, so they are not collected. A display name (for "Include my name" on PDFs and
 * exports) plus optional contact fields is all the app needs.
 *
 * Values are validated and normalized by [com.dataloom.checklist.domain.validation.ProfileValidator];
 * create and save one through `SaveProfileUseCase`.
 *
 * [toString] is redacted so the profile cannot leak into logs, crash reports or test output by
 * accident. Read the properties explicitly where they are really needed.
 */
data class UserProfile(
    val displayName: String,
    val email: String? = null,
    val phone: String? = null,
) {
    override fun toString(): String = "UserProfile(***)"
}

/** What the profile store holds right now, as observed by the UI. */
sealed interface ProfileState {

    /** No profile has been saved, or it was cleared. */
    data object NotSet : ProfileState

    data class Available(val profile: UserProfile) : ProfileState

    /**
     * A stored profile could not be decrypted and was erased. This happens when the encryption key
     * is gone or invalidated (the app data was restored on a new phone, the Keystore was reset) or the
     * stored data was tampered with. The UI tells the user and asks them to enter the profile again.
     * The state stays until a new profile is saved or `AcknowledgeProfileResetUseCase` dismisses it.
     */
    data object Reset : ProfileState

    /**
     * Secure storage cannot be used right now, for example because the Keystore is failing. Nothing
     * was deleted. Clearing the profile always works and starts over with fresh keys.
     */
    data object Unavailable : ProfileState
}

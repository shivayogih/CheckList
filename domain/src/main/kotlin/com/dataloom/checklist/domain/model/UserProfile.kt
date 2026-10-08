package com.dataloom.checklist.domain.model

/**
 * The user's own details, kept only on this device and only encrypted (architecture section 19.1).
 *
 * Every field is optional (CL-250): first-run setup lets the user skip all of them. A display name
 * (for the Home greeting and "Include my name" on PDFs and exports), email, phone and an optional
 * postal [address] are all the app collects; there are no locations and no location permission.
 * None of it is ever sent to AI.
 *
 * Values are validated and normalized by [com.dataloom.checklist.domain.validation.ProfileValidator];
 * create and save one through `SaveProfileUseCase`.
 *
 * [toString] is redacted so the profile cannot leak into logs, crash reports or test output by
 * accident. Read the properties explicitly where they are really needed.
 */
data class UserProfile(
    val displayName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    /** Added in CL-250. Stored inside the same encrypted payload; older payloads simply lack it. */
    val address: String? = null,
) {
    /** True when no field has a value; such a profile is never stored (see `SaveProfileUseCase`). */
    val isEmpty: Boolean get() = displayName == null && email == null && phone == null && address == null

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

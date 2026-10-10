package com.dataloom.checklist.presentation.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.phone.PhoneCountries
import com.dataloom.checklist.domain.usecase.AcknowledgeProfileResetUseCase
import com.dataloom.checklist.domain.usecase.ClearProfileUseCase
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.ObserveProfileUseCase
import com.dataloom.checklist.domain.usecase.SaveProfileUseCase
import com.dataloom.checklist.domain.validation.PhoneNumberInput
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the encrypted store reported, as the screen needs it ([ProfileState] plus "still loading"). */
enum class ProfileStatus { LOADING, NOT_SET, AVAILABLE, RESET, UNAVAILABLE }

data class ProfileUiState(
    val status: ProfileStatus = ProfileStatus.LOADING,
    val name: String = "",
    val nameError: UiText? = null,
    val email: String = "",
    val emailError: UiText? = null,
    /** National digits only; the country is picked separately (CL-380). */
    val phone: String = "",
    val phoneCountry: String = PhoneCountries.DEFAULT_ISO,
    val countryPickerOpen: Boolean = false,
    val phoneError: UiText? = null,
    val address: String = "",
    val addressError: UiText? = null,
    val isSaving: Boolean = false,
    val showClearConfirm: Boolean = false,
) {
    /** Save is enabled only while every field would be accepted (CL-280). */
    val canSave: Boolean
        get() = canEdit && !isSaving &&
            nameError == null && emailError == null && phoneError == null && addressError == null

    /** While secure storage is failing nothing can be read or saved; only "Delete profile" works. */
    val canEdit: Boolean get() = status != ProfileStatus.LOADING && status != ProfileStatus.UNAVAILABLE

    /** Delete is offered whenever something is (or may be) stored. */
    val canClear: Boolean get() = status == ProfileStatus.AVAILABLE || status == ProfileStatus.UNAVAILABLE
}

sealed interface ProfileAction {
    data class NameChanged(val name: String) : ProfileAction
    data class EmailChanged(val email: String) : ProfileAction
    data class PhoneChanged(val phone: String) : ProfileAction
    data class AddressChanged(val address: String) : ProfileAction
    data object OpenCountryPicker : ProfileAction
    data object CloseCountryPicker : ProfileAction
    data class CountryChosen(val iso: String) : ProfileAction
    data object Save : ProfileAction
    data object AcknowledgeReset : ProfileAction
    data object RequestClear : ProfileAction
    data object ConfirmClear : ProfileAction
    data object DismissClear : ProfileAction
}

sealed interface ProfileEffect {
    data object Saved : ProfileEffect
    data object Cleared : ProfileEffect
    data class Error(val message: UiText) : ProfileEffect
}

/**
 * The optional user profile (name, email, phone, address), stored only encrypted on the device (CL-135).
 * The form is filled from the stored profile until the user starts typing, so a store update never
 * overwrites unsaved edits.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    observeProfile: ObserveProfileUseCase,
    private val saveProfile: SaveProfileUseCase,
    private val clearProfile: ClearProfileUseCase,
    private val acknowledgeProfileReset: AcknowledgeProfileResetUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(ProfileUiState())

    /**
     * The store is observed only while the screen is on show (plus a short grace period for a
     * rotation), not for the whole life of the ViewModel: decrypting the profile touches the Keystore,
     * and nothing should do that while the user is elsewhere. The form text lives in [state], which
     * the ViewModel keeps across rotation, language, dark mode and font size changes.
     *
     * Deliberately no SavedStateHandle: saved state is written to disk by the system, and the profile
     * must exist only encrypted (docs/security.md). After process death the form starts from the stored profile.
     */
    val uiState: StateFlow<ProfileUiState> = channelFlow {
        launch { observeProfile().collect { profileState -> onStoreChanged(profileState) } }
        state.collect { send(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), state.value)

    private val effects = Channel<ProfileEffect>(Channel.BUFFERED)
    val effect: Flow<ProfileEffect> = effects.receiveAsFlow()

    /** True once the user has typed; stored values then no longer replace the form. */
    private var edited = false

    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.NameChanged -> edit { it.copy(name = ProfileTyping.name(action.name)).revalidated() }
            is ProfileAction.EmailChanged -> edit { it.copy(email = ProfileTyping.email(action.email)).revalidated() }
            is ProfileAction.PhoneChanged -> edit {
                val entry = ProfileTyping.phone(action.phone, it.phoneCountry)
                it.copy(phone = entry.digits, phoneCountry = entry.countryIso).revalidated()
            }
            ProfileAction.OpenCountryPicker -> state.update { it.copy(countryPickerOpen = true) }
            ProfileAction.CloseCountryPicker -> state.update { it.copy(countryPickerOpen = false) }
            is ProfileAction.CountryChosen -> edit {
                val entry = PhoneNumberInput.forCountry(it.phone, action.iso)
                it.copy(phone = entry.digits, phoneCountry = entry.countryIso, countryPickerOpen = false).revalidated()
            }
            is ProfileAction.AddressChanged ->
                edit { it.copy(address = ProfileTyping.address(action.address)).revalidated() }
            ProfileAction.Save -> save()
            ProfileAction.AcknowledgeReset -> viewModelScope.launch { acknowledgeProfileReset() }
            ProfileAction.RequestClear -> state.update { it.copy(showClearConfirm = true) }
            ProfileAction.DismissClear -> state.update { it.copy(showClearConfirm = false) }
            ProfileAction.ConfirmClear -> clear()
        }
    }

    private fun onStoreChanged(profileState: ProfileState) {
        val status = when (profileState) {
            ProfileState.NotSet -> ProfileStatus.NOT_SET
            is ProfileState.Available -> ProfileStatus.AVAILABLE
            ProfileState.Reset -> ProfileStatus.RESET
            ProfileState.Unavailable -> ProfileStatus.UNAVAILABLE
        }
        state.update { current ->
            val withStatus = current.copy(status = status)
            if (!edited && profileState is ProfileState.Available) withStatus.fill(profileState.profile) else withStatus
        }
    }

    private fun edit(change: (ProfileUiState) -> ProfileUiState) {
        edited = true
        state.update(change)
    }

    private fun save() {
        val current = state.value
        if (current.isSaving || !current.canEdit) return
        state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveProfile(current.name, current.email, current.phone, current.address, current.phoneCountry)
            when (result) {
                is DomainResult.Success -> {
                    edited = false
                    // Show the normalized values that were actually stored.
                    state.update { it.fill(result.value).copy(isSaving = false) }
                    effects.send(ProfileEffect.Saved)
                }
                is DomainResult.Failure -> {
                    val error = result.error
                    if (error is DomainError.Invalid) {
                        val errors = error.toProfileFieldErrors(PhoneCountries.orDefault(current.phoneCountry))
                        state.update {
                            it.copy(
                                isSaving = false,
                                nameError = errors.name,
                                emailError = errors.email,
                                phoneError = errors.phone,
                                addressError = errors.address,
                            )
                        }
                    } else {
                        state.update { it.copy(isSaving = false) }
                        effects.send(ProfileEffect.Error(error.toUiText()))
                    }
                }
            }
        }
    }

    private fun clear() {
        state.update { it.copy(showClearConfirm = false) }
        viewModelScope.launch {
            clearProfile()
            edited = false
            state.update { it.fill(null) }
            effects.send(ProfileEffect.Cleared)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }

    /** Shows the validator's verdict for what is typed now, next to each field. */
    private fun ProfileUiState.revalidated(): ProfileUiState {
        val errors = liveProfileErrors(name, email, phone, address, phoneCountry)
        return copy(
            nameError = errors.name,
            emailError = errors.email,
            phoneError = errors.phone,
            addressError = errors.address,
        )
    }

    private fun ProfileUiState.fill(profile: UserProfile?) = copy(
        name = profile?.displayName.orEmpty(),
        email = profile?.email.orEmpty(),
        phone = profile?.phone.orEmpty(),
        phoneCountry = PhoneCountries.orDefault(profile?.phoneCountry).iso,
        address = profile?.address.orEmpty(),
        nameError = null,
        emailError = null,
        phoneError = null,
        addressError = null,
    )
}

package com.dataloom.checklist.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.phone.PhoneCountries
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.SaveProfileUseCase
import com.dataloom.checklist.domain.validation.PhoneNumberInput
import com.dataloom.checklist.onboarding.OnboardingStore
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText
import com.dataloom.checklist.presentation.settings.profile.ProfileFieldErrors
import com.dataloom.checklist.presentation.settings.profile.ProfileTyping
import com.dataloom.checklist.presentation.settings.profile.isClear
import com.dataloom.checklist.presentation.settings.profile.liveProfileErrors
import com.dataloom.checklist.presentation.settings.profile.toProfileFieldErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three stages of the first-run flow (UI-SPEC section 1): 00a, 00b to 00d, 00e and 00f. */
enum class OnboardingStep { WELCOME, TUTORIAL, PROFILE }

/** Number of tutorial slides. */
const val TUTORIAL_PAGE_COUNT = 3

data class OnboardingUiState(
    /** Replaying the tutorial from Settings: no welcome, no profile setup, the flag is untouched. */
    val replay: Boolean = false,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** Zero-based slide index. */
    val tutorialPage: Int = 0,
    val name: String = "",
    val email: String = "",
    /** National digits only; the country is picked separately (CL-380). */
    val phone: String = "",
    val phoneCountry: String = PhoneCountries.DEFAULT_ISO,
    val countryPickerOpen: Boolean = false,
    val address: String = "",
    val fieldErrors: ProfileFieldErrors = ProfileFieldErrors(),
    /** The "Add email and address" section; collapsed until the user opens it. */
    val moreExpanded: Boolean = false,
    /** A problem that is not tied to one field, such as secure storage being unavailable. */
    val saveError: UiText? = null,
    val isBusy: Boolean = false,
) {
    val isLastPage: Boolean get() = tutorialPage >= TUTORIAL_PAGE_COUNT - 1

    /** Continue on profile setup is enabled only while every typed field would be accepted (CL-280). */
    val canSubmitProfile: Boolean get() = !isBusy && fieldErrors.isClear

    /** Whether system Back (and the arrow, where drawn) moves inside the flow instead of leaving it. */
    val canStepBack: Boolean
        get() = when (step) {
            OnboardingStep.WELCOME -> false
            OnboardingStep.TUTORIAL -> !replay || tutorialPage > 0
            OnboardingStep.PROFILE -> true
        }
}

sealed interface OnboardingAction {
    data object ContinueFromWelcome : OnboardingAction
    data object NextPage : OnboardingAction

    /** The pager settled on [page] after a swipe. */
    data class PageSettled(val page: Int) : OnboardingAction

    /** "Skip" on any slide: jumps to profile setup, or closes the replay. */
    data object SkipTutorial : OnboardingAction
    data object Back : OnboardingAction
    data class NameChanged(val value: String) : OnboardingAction
    data class EmailChanged(val value: String) : OnboardingAction
    data class PhoneChanged(val value: String) : OnboardingAction
    data class AddressChanged(val value: String) : OnboardingAction
    data object OpenCountryPicker : OnboardingAction
    data object CloseCountryPicker : OnboardingAction
    data class CountryChosen(val iso: String) : OnboardingAction
    data object ToggleMore : OnboardingAction
    data object SubmitProfile : OnboardingAction

    /** "Skip for now" and the top "Skip" on profile setup. */
    data object SkipProfile : OnboardingAction
}

sealed interface OnboardingEffect {
    /** The flow is over: leave it (to Home, or back to Settings after a replay). */
    data object Finished : OnboardingEffect
}

/**
 * Drives the first-run flow and the tutorial replay (CL-250).
 *
 * The step and the slide index live in [SavedStateHandle], so after process death the user resumes
 * on the same screen. Typed profile text is deliberately not saved there: saved state is written to
 * disk by the system, and the profile is meant to exist only encrypted (docs/security.md).
 *
 * Finishing writes `onboarding_completed = true` first, then emits [OnboardingEffect.Finished]. The
 * screen then clears the back stack, so Back from Home leaves the app. A replay never writes the flag.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val store: OnboardingStore,
    private val saveProfile: SaveProfileUseCase,
) : ViewModel() {

    private val replay: Boolean = savedState.get<Boolean>(KEY_REPLAY) ?: false

    private val state = MutableStateFlow(
        OnboardingUiState(
            replay = replay,
            step = restoredStep(),
            tutorialPage = (savedState.get<Int>(KEY_PAGE) ?: 0).coerceIn(0, TUTORIAL_PAGE_COUNT - 1),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    private val effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effect: Flow<OnboardingEffect> = effects.receiveAsFlow()

    init {
        persist()
    }

    fun onAction(action: OnboardingAction) {
        if (state.value.isBusy) return
        when (action) {
            OnboardingAction.ContinueFromWelcome -> go(OnboardingStep.TUTORIAL, page = 0)
            OnboardingAction.NextPage -> {
                val current = state.value
                if (current.isLastPage) leaveTutorial() else go(OnboardingStep.TUTORIAL, current.tutorialPage + 1)
            }
            is OnboardingAction.PageSettled ->
                go(OnboardingStep.TUTORIAL, action.page.coerceIn(0, TUTORIAL_PAGE_COUNT - 1))
            OnboardingAction.SkipTutorial -> leaveTutorial()
            OnboardingAction.Back -> stepBack()
            is OnboardingAction.NameChanged -> edit { it.copy(name = ProfileTyping.name(action.value)).revalidated() }
            is OnboardingAction.EmailChanged ->
                edit { it.copy(email = ProfileTyping.email(action.value)).revalidated() }
            is OnboardingAction.PhoneChanged -> edit {
                val entry = ProfileTyping.phone(action.value, it.phoneCountry)
                it.copy(phone = entry.digits, phoneCountry = entry.countryIso).revalidated()
            }
            OnboardingAction.OpenCountryPicker -> state.update { it.copy(countryPickerOpen = true) }
            OnboardingAction.CloseCountryPicker -> state.update { it.copy(countryPickerOpen = false) }
            is OnboardingAction.CountryChosen -> edit {
                val entry = PhoneNumberInput.forCountry(it.phone, action.iso)
                it.copy(phone = entry.digits, phoneCountry = entry.countryIso, countryPickerOpen = false).revalidated()
            }
            is OnboardingAction.AddressChanged ->
                edit { it.copy(address = ProfileTyping.address(action.value)).revalidated() }
            OnboardingAction.ToggleMore -> state.update { it.copy(moreExpanded = !it.moreExpanded) }
            OnboardingAction.SubmitProfile -> submitProfile()
            OnboardingAction.SkipProfile -> finish()
        }
    }

    /** Slide "Get started" and "Skip": profile setup in the first run, the end of the replay otherwise. */
    private fun leaveTutorial() {
        if (replay) finish() else go(OnboardingStep.PROFILE, state.value.tutorialPage)
    }

    private fun stepBack() {
        val current = state.value
        when (current.step) {
            OnboardingStep.WELCOME -> Unit
            OnboardingStep.TUTORIAL -> when {
                current.tutorialPage > 0 -> go(OnboardingStep.TUTORIAL, current.tutorialPage - 1)
                !replay -> go(OnboardingStep.WELCOME, 0)
            }
            OnboardingStep.PROFILE -> go(OnboardingStep.TUTORIAL, TUTORIAL_PAGE_COUNT - 1)
        }
    }

    private fun go(step: OnboardingStep, page: Int) {
        state.update { it.copy(step = step, tutorialPage = page) }
        persist()
    }

    /** Shows the validator's verdict for what is typed now, next to each field (CL-280). */
    private fun OnboardingUiState.revalidated(): OnboardingUiState =
        copy(fieldErrors = liveProfileErrors(name, email, phone, address, phoneCountry))

    private fun edit(change: (OnboardingUiState) -> OnboardingUiState) {
        state.update { change(it).copy(saveError = null) }
    }

    /**
     * Continue on profile setup. All fields blank saves nothing (and never erases an existing
     * profile). Otherwise the same validation as the Settings screen runs, and an invalid value
     * keeps the user here with the inline error.
     */
    private fun submitProfile() {
        val current = state.value
        if (listOf(current.name, current.email, current.phone, current.address).all { it.isBlank() }) {
            finish()
            return
        }
        state.update { it.copy(isBusy = true, saveError = null) }
        viewModelScope.launch {
            val result = saveProfile(current.name, current.email, current.phone, current.address, current.phoneCountry)
            when (result) {
                is DomainResult.Success -> finish(alreadyBusy = true)
                is DomainResult.Failure -> {
                    val error = result.error
                    if (error is DomainError.Invalid) {
                        val errors = error.toProfileFieldErrors(PhoneCountries.orDefault(current.phoneCountry))
                        state.update {
                            it.copy(
                                isBusy = false,
                                fieldErrors = errors,
                                // Never leave an error hidden inside the collapsed section.
                                moreExpanded = it.moreExpanded || errors.email != null || errors.address != null,
                            )
                        }
                    } else {
                        state.update { it.copy(isBusy = false, saveError = error.toUiText()) }
                    }
                }
            }
        }
    }

    private fun finish(alreadyBusy: Boolean = false) {
        if (!alreadyBusy) state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            if (!replay) {
                // A failed write only means the flow shows again next launch; never trap the user here.
                try {
                    store.markCompleted()
                } catch (e: IOException) {
                    // Ignored on purpose: see above.
                }
            }
            effects.send(OnboardingEffect.Finished)
        }
    }

    private fun restoredStep(): OnboardingStep {
        if (replay) return OnboardingStep.TUTORIAL
        val saved = savedState.get<String>(KEY_STEP)?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
        // Profile setup is never entered in a replay, whatever an old saved value says.
        return saved ?: OnboardingStep.WELCOME
    }

    private fun persist() {
        val current = state.value
        savedState[KEY_STEP] = current.step.name
        savedState[KEY_PAGE] = current.tutorialPage
    }

    companion object {
        /** Navigation puts the route argument [OnboardingRoute.replay] into the saved state under its name. */
        const val KEY_REPLAY = "replay"
        const val KEY_STEP = "onboarding_step"
        const val KEY_PAGE = "onboarding_page"
    }
}

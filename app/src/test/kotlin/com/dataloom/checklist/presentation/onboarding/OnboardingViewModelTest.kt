package com.dataloom.checklist.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.usecase.SaveProfileUseCase
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeOnboardingStore
import com.dataloom.checklist.testing.FakeProfileRepository
import com.dataloom.checklist.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val store = FakeOnboardingStore()
    private val repo = FakeProfileRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        OnboardingViewModel(handle, store, SaveProfileUseCase(repo))

    private fun replayViewModel(handle: SavedStateHandle = SavedStateHandle(mapOf("replay" to true))) = viewModel(handle)

    private fun OnboardingViewModel.act(vararg actions: OnboardingAction) = actions.forEach(::onAction)

    private suspend fun OnboardingViewModel.assertFinished(block: () -> Unit) {
        effect.test {
            block()
            assertEquals(OnboardingEffect.Finished, awaitItem())
        }
    }

    // First run: steps

    @Test
    fun `the flow starts at the welcome screen with nothing finished`() {
        val vm = viewModel()

        assertEquals(OnboardingStep.WELCOME, vm.uiState.value.step)
        assertFalse(vm.uiState.value.canStepBack)
        assertFalse(store.flag.value)
    }

    @Test
    fun `continue, next and get started walk through all three slides to profile setup`() {
        val vm = viewModel()

        vm.act(OnboardingAction.ContinueFromWelcome)
        assertEquals(OnboardingStep.TUTORIAL, vm.uiState.value.step)
        assertEquals(0, vm.uiState.value.tutorialPage)

        vm.act(OnboardingAction.NextPage)
        assertEquals(1, vm.uiState.value.tutorialPage)
        vm.act(OnboardingAction.NextPage)
        assertEquals(2, vm.uiState.value.tutorialPage)
        assertTrue(vm.uiState.value.isLastPage)

        vm.act(OnboardingAction.NextPage) // "Get started"
        assertEquals(OnboardingStep.PROFILE, vm.uiState.value.step)
        assertFalse(store.flag.value) // nothing is finished until profile setup is left
    }

    @Test
    fun `a swipe reports the settled page and is clamped`() {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.PageSettled(2))
        assertEquals(2, vm.uiState.value.tutorialPage)

        vm.act(OnboardingAction.PageSettled(9))
        assertEquals(TUTORIAL_PAGE_COUNT - 1, vm.uiState.value.tutorialPage)
    }

    @Test
    fun `skip on any slide jumps to profile setup`() {
        listOf(0, 1, 2).forEach { page ->
            val vm = viewModel()
            vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.PageSettled(page), OnboardingAction.SkipTutorial)

            assertEquals(OnboardingStep.PROFILE, vm.uiState.value.step)
        }
    }

    @Test
    fun `back goes one step back through the flow`() {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.NextPage, OnboardingAction.NextPage, OnboardingAction.NextPage)
        assertEquals(OnboardingStep.PROFILE, vm.uiState.value.step)
        assertTrue(vm.uiState.value.canStepBack)

        vm.act(OnboardingAction.Back)
        assertEquals(OnboardingStep.TUTORIAL, vm.uiState.value.step)
        assertEquals(2, vm.uiState.value.tutorialPage)

        vm.act(OnboardingAction.Back, OnboardingAction.Back)
        assertEquals(0, vm.uiState.value.tutorialPage)
        vm.act(OnboardingAction.Back)
        assertEquals(OnboardingStep.WELCOME, vm.uiState.value.step)

        vm.act(OnboardingAction.Back) // nothing before the welcome screen
        assertEquals(OnboardingStep.WELCOME, vm.uiState.value.step)
    }

    // Process death

    @Test
    fun `the step and the slide survive process death through the saved state`() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.NextPage)

        // A new ViewModel on the same saved state is what the system makes after process death.
        val restored = viewModel(handle)

        assertEquals(OnboardingStep.TUTORIAL, restored.uiState.value.step)
        assertEquals(1, restored.uiState.value.tutorialPage)
    }

    @Test
    fun `profile setup is resumed on the profile step, with the typed text gone`() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        first.act(OnboardingAction.NameChanged("Kamala"))

        val restored = viewModel(handle)

        assertEquals(OnboardingStep.PROFILE, restored.uiState.value.step)
        assertEquals("", restored.uiState.value.name)
        assertFalse(handle.keys().any { handle.get<Any>(it) == "Kamala" })
    }

    @Test
    fun `a corrupt saved step falls back to the welcome screen`() {
        val handle = SavedStateHandle(mapOf(OnboardingViewModel.KEY_STEP to "nonsense", OnboardingViewModel.KEY_PAGE to 99))

        val vm = viewModel(handle)

        assertEquals(OnboardingStep.WELCOME, vm.uiState.value.step)
        assertEquals(TUTORIAL_PAGE_COUNT - 1, vm.uiState.value.tutorialPage)
    }

    // First run: finishing

    @Test
    fun `skip for now sets the flag, saves nothing and finishes`() = runTest {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)

        vm.assertFinished { vm.onAction(OnboardingAction.SkipProfile) }

        assertTrue(store.flag.value)
        assertEquals(1, store.writes)
        assertEquals(0, repo.saveCalls)
    }

    @Test
    fun `continue with every field blank saves nothing and finishes`() = runTest {
        repo.state.value = ProfileState.Available(UserProfile("Existing"))
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        vm.act(OnboardingAction.NameChanged("  "), OnboardingAction.PhoneChanged(""))

        vm.assertFinished { vm.onAction(OnboardingAction.SubmitProfile) }

        assertEquals(0, repo.saveCalls)
        assertTrue(store.flag.value)
        // A blank form never erases a profile that already exists.
        assertEquals(ProfileState.Available(UserProfile("Existing")), repo.state.value)
    }

    @Test
    fun `continue with a name saves the normalized profile and finishes`() = runTest {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        vm.act(OnboardingAction.NameChanged("  Kamala   Hiremath "), OnboardingAction.PhoneChanged("+91 98450 12345"))
        vm.act(OnboardingAction.ToggleMore, OnboardingAction.AddressChanged("12, 4th Cross\nHubballi"))

        vm.assertFinished { vm.onAction(OnboardingAction.SubmitProfile) }

        val saved = (repo.state.value as ProfileState.Available).profile
        assertEquals(UserProfile("Kamala Hiremath", null, "9845012345", "12, 4th Cross\nHubballi", "IN"), saved)
        assertTrue(store.flag.value)
    }

    @Test
    fun `an invalid email blocks continue, shows the error and opens the hidden section`() = runTest {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        vm.act(OnboardingAction.NameChanged("Kamala"), OnboardingAction.EmailChanged("kamala@example"))
        assertFalse(vm.uiState.value.moreExpanded)

        vm.effect.test {
            vm.onAction(OnboardingAction.SubmitProfile)
            expectNoEvents()
        }

        val state = vm.uiState.value
        assertEquals(UiText(R.string.error_email_invalid), state.fieldErrors.email)
        assertTrue(state.moreExpanded)
        assertFalse(state.isBusy)
        assertEquals(OnboardingStep.PROFILE, state.step)
        assertFalse(store.flag.value)
        assertEquals(0, repo.saveCalls)

        // Fixing the field clears the error and Continue then goes through.
        vm.onAction(OnboardingAction.EmailChanged("kamala@example.com"))
        assertNull(vm.uiState.value.fieldErrors.email)
        vm.assertFinished { vm.onAction(OnboardingAction.SubmitProfile) }
        assertTrue(store.flag.value)
    }

    @Test
    fun `an address that is too long blocks continue`() {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        vm.act(OnboardingAction.AddressChanged("a".repeat(FieldLimits.ADDRESS_MAX + 1)), OnboardingAction.SubmitProfile)

        assertEquals(UiText(R.string.error_too_long, listOf(FieldLimits.ADDRESS_MAX)), vm.uiState.value.fieldErrors.address)
        assertTrue(vm.uiState.value.moreExpanded)
        assertFalse(store.flag.value)
    }

    @Test
    fun `the email section stays collapsed unless it is toggled`() {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        assertFalse(vm.uiState.value.moreExpanded)

        vm.act(OnboardingAction.NameChanged("Kamala"), OnboardingAction.PhoneChanged("+91 98450 12345"))
        assertFalse(vm.uiState.value.moreExpanded)

        vm.act(OnboardingAction.ToggleMore)
        assertTrue(vm.uiState.value.moreExpanded)
        vm.act(OnboardingAction.ToggleMore)
        assertFalse(vm.uiState.value.moreExpanded)
    }

    @Test
    fun `failing secure storage keeps the user on the form with a message`() = runTest {
        repo.storageAvailable = false
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial, OnboardingAction.NameChanged("Kamala"))

        vm.effect.test {
            vm.onAction(OnboardingAction.SubmitProfile)
            expectNoEvents()
        }

        assertEquals(UiText(R.string.error_secure_storage), vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.isBusy)
        assertFalse(store.flag.value)
        // "Skip for now" is still the way out.
        vm.assertFinished { vm.onAction(OnboardingAction.SkipProfile) }
        assertTrue(store.flag.value)
    }

    @Test
    fun `a failed flag write never traps the user in the flow`() = runTest {
        store.failWrites = true
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)

        vm.assertFinished { vm.onAction(OnboardingAction.SkipProfile) }

        assertFalse(store.flag.value)
    }

    @Test
    fun `finishing twice writes the flag once`() = runTest {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)

        vm.assertFinished {
            vm.onAction(OnboardingAction.SkipProfile)
            vm.onAction(OnboardingAction.SkipProfile)
        }

        assertEquals(1, store.writes)
    }

    // Replay from Settings

    @Test
    fun `a replay opens the tutorial, not the welcome screen`() {
        val vm = replayViewModel()

        assertTrue(vm.uiState.value.replay)
        assertEquals(OnboardingStep.TUTORIAL, vm.uiState.value.step)
        assertEquals(0, vm.uiState.value.tutorialPage)
        // Page 1 has no previous step, so system Back leaves the replay.
        assertFalse(vm.uiState.value.canStepBack)
    }

    @Test
    fun `a replay finishes on get started without the flag or the profile`() = runTest {
        val vm = replayViewModel()
        vm.act(OnboardingAction.NextPage, OnboardingAction.NextPage)

        vm.assertFinished { vm.onAction(OnboardingAction.NextPage) }

        assertEquals(0, store.writes)
        assertEquals(0, repo.saveCalls)
        assertEquals(OnboardingStep.TUTORIAL, vm.uiState.value.step)
    }

    @Test
    fun `a replay finishes on skip without touching the flag`() = runTest {
        val vm = replayViewModel()

        vm.assertFinished { vm.onAction(OnboardingAction.SkipTutorial) }

        assertEquals(0, store.writes)
        assertFalse(store.flag.value)
    }

    @Test
    fun `a replay never enters profile setup even from a stale saved step`() {
        val handle = SavedStateHandle(mapOf("replay" to true, OnboardingViewModel.KEY_STEP to "PROFILE"))

        assertEquals(OnboardingStep.TUTORIAL, replayViewModel(handle).uiState.value.step)
    }

    @Test
    fun `a replay does not overwrite a completed flag`() = runTest {
        store.flag.value = true
        val vm = replayViewModel()

        vm.assertFinished { vm.onAction(OnboardingAction.SkipTutorial) }

        assertTrue(store.flag.value)
        assertEquals(0, store.writes)
    }

    @Test
    fun `back inside a replay steps through the slides`() {
        val vm = replayViewModel()
        vm.act(OnboardingAction.NextPage)
        assertTrue(vm.uiState.value.canStepBack)

        vm.act(OnboardingAction.Back)

        assertEquals(0, vm.uiState.value.tutorialPage)
    }

    // CL-280: keyboard filters and the enabled state of Continue.

    @Test
    fun `phone and email are filtered and Continue is disabled while a field is invalid`() {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        assertTrue(vm.uiState.value.canSubmitProfile)

        vm.act(OnboardingAction.PhoneChanged("98a7#6*"), OnboardingAction.EmailChanged(" a@b.c o\n"))
        assertEquals("9876", vm.uiState.value.phone)
        assertEquals("a@b.co", vm.uiState.value.email)

        vm.act(OnboardingAction.EmailChanged("kamala@example"))
        assertEquals(UiText(R.string.error_email_invalid), vm.uiState.value.fieldErrors.email)
        assertFalse(vm.uiState.value.canSubmitProfile)

        vm.act(OnboardingAction.EmailChanged("kamala@example.com"), OnboardingAction.PhoneChanged("98450 12345"))
        assertTrue(vm.uiState.value.canSubmitProfile)
    }

    // CL-380: country and number are separate fields.

    @Test
    fun `the country picker opens, closes and keeps the typed digits`() = runTest {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        assertEquals("IN", vm.uiState.value.phoneCountry)
        vm.act(OnboardingAction.PhoneChanged("7911 123456"), OnboardingAction.OpenCountryPicker)
        assertTrue(vm.uiState.value.countryPickerOpen)
        vm.act(OnboardingAction.CloseCountryPicker)
        assertFalse(vm.uiState.value.countryPickerOpen)
        assertEquals("IN", vm.uiState.value.phoneCountry)

        vm.act(OnboardingAction.OpenCountryPicker, OnboardingAction.CountryChosen("GB"))
        assertFalse(vm.uiState.value.countryPickerOpen)
        assertEquals("GB", vm.uiState.value.phoneCountry)
        assertEquals("7911123456", vm.uiState.value.phone)

        vm.assertFinished { vm.onAction(OnboardingAction.SubmitProfile) }
        val saved = (repo.state.value as ProfileState.Available).profile
        assertEquals(UserProfile(phone = "7911123456", phoneCountry = "GB"), saved)
    }

    @Test
    fun `a name with control characters is cleaned and one of only spaces counts as blank`() {
        val vm = viewModel()
        vm.act(OnboardingAction.ContinueFromWelcome, OnboardingAction.SkipTutorial)
        vm.act(OnboardingAction.NameChanged("Ka\u202Emala\u0000"))
        assertEquals("Kamala", vm.uiState.value.name)
        vm.act(OnboardingAction.NameChanged("\u200B \t "))
        assertTrue(vm.uiState.value.name.isBlank())
        assertTrue(vm.uiState.value.canSubmitProfile)
    }
}

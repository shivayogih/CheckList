package com.dataloom.checklist.presentation.settings.profile

import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.usecase.AcknowledgeProfileResetUseCase
import com.dataloom.checklist.domain.usecase.ClearProfileUseCase
import com.dataloom.checklist.domain.usecase.ObserveProfileUseCase
import com.dataloom.checklist.domain.usecase.SaveProfileUseCase
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeProfileRepository
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repo = FakeProfileRepository()

    /** The screen is on show for the whole test, so the (lifecycle-bound) store observation runs. */
    private fun TestScope.viewModel(): ProfileViewModel {
        val vm = ProfileViewModel(
            observeProfile = ObserveProfileUseCase(repo),
            saveProfile = SaveProfileUseCase(repo),
            clearProfile = ClearProfileUseCase(repo),
            acknowledgeProfileReset = AcknowledgeProfileResetUseCase(repo),
        )
        keepCollecting(vm.uiState)
        return vm
    }

    @Test
    fun `the store is not observed while no screen shows the profile, and typed text survives a pause`() = runTest {
        val vm = ProfileViewModel(
            observeProfile = ObserveProfileUseCase(repo),
            saveProfile = SaveProfileUseCase(repo),
            clearProfile = ClearProfileUseCase(repo),
            acknowledgeProfileReset = AcknowledgeProfileResetUseCase(repo),
        )
        assertEquals(0, repo.state.subscriptionCount.value)

        val screen = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(1, repo.state.subscriptionCount.value)
        vm.onAction(ProfileAction.NameChanged("Asha"))

        // Rotation: the screen collects again within the grace period; a long pause stops the observation.
        screen.cancel()
        advanceTimeBy(5_001)
        assertEquals(0, repo.state.subscriptionCount.value)

        keepCollecting(vm.uiState)
        assertEquals("Asha", vm.uiState.value.name)
    }

    @Test
    fun `a stored profile fills the form`() = runTest {
        repo.state.value = ProfileState.Available(UserProfile("Asha", "asha@example.com", null))
        val vm = viewModel()

        val state = vm.uiState.value
        assertEquals(ProfileStatus.AVAILABLE, state.status)
        assertEquals("Asha", state.name)
        assertEquals("asha@example.com", state.email)
        assertEquals("", state.phone)
        assertTrue(state.canEdit)
        assertTrue(state.canClear)
    }

    @Test
    fun `saving stores the normalized profile`() = runTest {
        val vm = viewModel()
        assertEquals(ProfileStatus.NOT_SET, vm.uiState.value.status)
        assertFalse(vm.uiState.value.canClear)

        vm.onAction(ProfileAction.NameChanged("  Asha  "))
        vm.onAction(ProfileAction.EmailChanged(""))
        vm.onAction(ProfileAction.PhoneChanged("+91 98450 12345"))
        vm.effect.test {
            vm.onAction(ProfileAction.Save)
            assertEquals(ProfileEffect.Saved, awaitItem())
        }

        val saved = (repo.state.value as ProfileState.Available).profile
        assertEquals("Asha", saved.displayName)
        assertNull(saved.email)
        assertEquals("Asha", vm.uiState.value.name)
        assertEquals(ProfileStatus.AVAILABLE, vm.uiState.value.status)
    }

    @Test
    fun `every field error is shown at once and nothing is stored`() = runTest {
        val vm = viewModel()
        vm.onAction(ProfileAction.NameChanged("n".repeat(FieldLimits.DISPLAY_NAME_MAX + 1)))
        vm.onAction(ProfileAction.EmailChanged("not-an-email"))
        vm.onAction(ProfileAction.PhoneChanged("12"))
        vm.onAction(ProfileAction.AddressChanged("a".repeat(FieldLimits.ADDRESS_MAX + 1)))

        vm.onAction(ProfileAction.Save)

        val state = vm.uiState.value
        assertEquals(UiText(R.string.error_too_long, listOf(FieldLimits.DISPLAY_NAME_MAX)), state.nameError)
        assertEquals(UiText(R.string.error_too_long, listOf(FieldLimits.ADDRESS_MAX)), state.addressError)
        assertEquals(UiText(R.string.error_email_invalid), state.emailError)
        assertEquals(
            UiText(R.string.error_phone_invalid, listOf(FieldLimits.PHONE_DIGITS_MIN, FieldLimits.PHONE_DIGITS_MAX)),
            state.phoneError,
        )
        assertFalse(state.isSaving)
        assertEquals(0, repo.saveCalls)

        vm.onAction(ProfileAction.EmailChanged("asha@example.com"))
        assertNull(vm.uiState.value.emailError)
    }

    @Test
    fun `the address is saved and shown again`() = runTest {
        val vm = viewModel()
        vm.onAction(ProfileAction.AddressChanged("  12, 4th Cross \n Hubballi "))
        vm.effect.test {
            vm.onAction(ProfileAction.Save)
            assertEquals(ProfileEffect.Saved, awaitItem())
        }

        assertEquals("12, 4th Cross\nHubballi", (repo.state.value as ProfileState.Available).profile.address)
        assertEquals("12, 4th Cross\nHubballi", vm.uiState.value.address)
    }

    @Test
    fun `saving a form with every field blank is allowed and stores nothing`() = runTest {
        val vm = viewModel()
        vm.effect.test {
            vm.onAction(ProfileAction.Save)
            assertEquals(ProfileEffect.Saved, awaitItem())
        }

        assertEquals(0, repo.saveCalls)
        assertEquals(ProfileState.NotSet, repo.state.value)
    }

    @Test
    fun `a failing secure store reports an error and keeps the typed values`() = runTest {
        repo.storageAvailable = false
        val vm = viewModel()
        vm.onAction(ProfileAction.NameChanged("Asha"))

        vm.effect.test {
            vm.onAction(ProfileAction.Save)
            assertEquals(ProfileEffect.Error(UiText(R.string.error_secure_storage)), awaitItem())
        }
        assertEquals("Asha", vm.uiState.value.name)
        assertEquals(ProfileStatus.NOT_SET, vm.uiState.value.status)
    }

    @Test
    fun `store updates do not overwrite unsaved edits`() = runTest {
        repo.state.value = ProfileState.Available(UserProfile("Asha"))
        val vm = viewModel()
        vm.onAction(ProfileAction.NameChanged("Asha K"))

        repo.state.value = ProfileState.Available(UserProfile("Someone else"))

        assertEquals("Asha K", vm.uiState.value.name)
    }

    @Test
    fun `a reset profile shows the notice until it is acknowledged`() = runTest {
        repo.state.value = ProfileState.Reset
        val vm = viewModel()
        assertEquals(ProfileStatus.RESET, vm.uiState.value.status)
        assertTrue(vm.uiState.value.canEdit)

        vm.onAction(ProfileAction.AcknowledgeReset)

        assertEquals(ProfileState.NotSet, repo.state.value)
        assertEquals(ProfileStatus.NOT_SET, vm.uiState.value.status)
    }

    @Test
    fun `unavailable storage disables editing but still allows deleting`() = runTest {
        repo.state.value = ProfileState.Unavailable
        val vm = viewModel()

        val state = vm.uiState.value
        assertEquals(ProfileStatus.UNAVAILABLE, state.status)
        assertFalse(state.canEdit)
        assertTrue(state.canClear)

        vm.onAction(ProfileAction.Save)
        assertEquals(0, repo.saveCalls)
    }

    @Test
    fun `deleting asks first, then erases the profile and empties the form`() = runTest {
        repo.state.value = ProfileState.Available(UserProfile("Asha", null, "9845012345"))
        val vm = viewModel()

        vm.onAction(ProfileAction.RequestClear)
        assertTrue(vm.uiState.value.showClearConfirm)
        vm.onAction(ProfileAction.DismissClear)
        assertFalse(vm.uiState.value.showClearConfirm)
        assertTrue(repo.state.value is ProfileState.Available)

        vm.onAction(ProfileAction.RequestClear)
        vm.effect.test {
            vm.onAction(ProfileAction.ConfirmClear)
            assertEquals(ProfileEffect.Cleared, awaitItem())
        }
        assertEquals(ProfileState.NotSet, repo.state.value)
        assertEquals("", vm.uiState.value.name)
        assertEquals("", vm.uiState.value.phone)
        assertFalse(vm.uiState.value.showClearConfirm)
    }
}

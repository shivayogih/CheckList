package com.dataloom.checklist.domain.usecase

import app.cash.turbine.test
import com.dataloom.checklist.domain.fake.FakeProfileRepository
import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUseCasesTest {

    private val repository = FakeProfileRepository()
    private val observe = ObserveProfileUseCase(repository)
    private val save = SaveProfileUseCase(repository)
    private val clear = ClearProfileUseCase(repository)
    private val acknowledge = AcknowledgeProfileResetUseCase(repository)

    @Test
    fun `save stores the normalized profile and observe emits it`() = runTest {
        observe().test {
            assertEquals(ProfileState.NotSet, awaitItem())

            val result = save("  Asha  ", " asha@example.com ", "")

            val expected = UserProfile("Asha", "asha@example.com", null)
            assertEquals(DomainResult.Success(expected), result)
            assertEquals(ProfileState.Available(expected), awaitItem())
        }
    }

    @Test
    fun `invalid input is never stored`() = runTest {
        val result = save(" ", "asha", null)

        assertEquals(
            DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.DISPLAY_NAME_BLANK, ValidationError.EMAIL_INVALID))),
            result,
        )
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun `failing secure storage is reported, not thrown`() = runTest {
        repository.storageAvailable = false

        assertEquals(DomainResult.Failure(DomainError.SecureStorageUnavailable), save("Asha", null, null))
        assertEquals(ProfileState.NotSet, repository.state.value)
    }

    @Test
    fun `clear removes the profile`() = runTest {
        save("Asha", null, null)

        clear()

        assertEquals(ProfileState.NotSet, repository.state.value)
    }

    @Test
    fun `acknowledging a reset returns to not set`() = runTest {
        repository.state.value = ProfileState.Reset

        acknowledge()

        assertEquals(ProfileState.NotSet, repository.state.value)
    }
}

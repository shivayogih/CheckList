package com.dataloom.checklist.onboarding

import com.dataloom.checklist.testing.FakeOnboardingStore
import com.dataloom.checklist.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StartViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `a fresh install starts the first-run flow`() {
        val vm = StartViewModel(FakeOnboardingStore(initial = false))

        assertEquals(StartDestination.ONBOARDING, vm.start.value)
    }

    @Test
    fun `after the flow was finished the app opens on Home`() {
        val vm = StartViewModel(FakeOnboardingStore(initial = true))

        assertEquals(StartDestination.HOME, vm.start.value)
    }

    @Test
    fun `the answer is decided once and does not flip when the flag is set later`() {
        val store = FakeOnboardingStore(initial = false)
        val vm = StartViewModel(store)

        store.flag.value = true

        assertEquals(StartDestination.ONBOARDING, vm.start.value)
    }
}

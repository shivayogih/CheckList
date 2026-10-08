package com.dataloom.checklist.testing

import com.dataloom.checklist.onboarding.OnboardingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory first-run flag. [writes] counts [markCompleted] calls; [failWrites] simulates a full disk. */
class FakeOnboardingStore(initial: Boolean = false) : OnboardingStore {

    val flag = MutableStateFlow(initial)
    var writes = 0
        private set
    var failWrites = false

    override val completed: Flow<Boolean> = flag

    override suspend fun markCompleted() {
        if (failWrites) throw java.io.IOException("disk full")
        writes++
        flag.value = true
    }
}

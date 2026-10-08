package com.dataloom.checklist.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.usecase.ObserveChecklistsUseCase
import com.dataloom.checklist.domain.usecase.ObserveProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The Home greeting card (00g): "Namaste, Kamala" and how many lists are in progress. */
data class GreetingUi(val name: String, val inProgress: Int)

/**
 * Feeds the greeting card. It is its own small ViewModel so Home's own state stays untouched. The
 * card exists only while the profile has a name; with no name, a reset or an unavailable profile
 * there is no card.
 */
@HiltViewModel
class HomeGreetingViewModel @Inject constructor(
    observeProfile: ObserveProfileUseCase,
    observeChecklists: ObserveChecklistsUseCase,
) : ViewModel() {

    val greeting: StateFlow<GreetingUi?> = combine(
        observeProfile(),
        observeChecklists(ChecklistQuery(filter = ChecklistFilter.ACTIVE)),
    ) { profile, lists ->
        val name = (profile as? ProfileState.Available)?.profile?.displayName?.let(::greetingName)
        name?.let { GreetingUi(it, countInProgress(lists)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The first word of the name, so "Kamala Hiremath" is greeted as "Kamala". Null when there is no name. */
internal fun greetingName(displayName: String?): String? =
    displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }

/** Active lists that have items and are not finished; an empty list is not "in progress" yet. */
internal fun countInProgress(lists: List<ChecklistSummary>): Int =
    lists.count { !it.checklist.isArchived && it.totalItems > 0 && it.completedItems < it.totalItems }

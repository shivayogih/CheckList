package com.dataloom.checklist.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.localization.AppLocales

/**
 * The first-run flow and the tutorial replay. [onFinished] runs once, after the "completed" flag has
 * been written (first run) or immediately (replay); the caller then leaves the flow.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onAction = viewModel::onAction

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { onFinished() }
    }
    // Inside the flow, system Back goes one step back. On the first screen (and on the first slide
    // of a replay) it is not handled here, so it leaves the screen or the app as usual.
    BackHandler(enabled = state.canStepBack) { onAction(OnboardingAction.Back) }

    when (state.step) {
        OnboardingStep.WELCOME -> {
            // Applying a language recreates the activity; this keeps the tile highlighted meanwhile.
            var selected by remember { mutableStateOf(AppLocales.current()) }
            WelcomeContent(
                selected = selected,
                onSelect = { choice ->
                    if (choice != selected) {
                        selected = choice
                        AppLocales.apply(choice)
                    }
                },
                onContinue = { onAction(OnboardingAction.ContinueFromWelcome) },
            )
        }
        OnboardingStep.TUTORIAL -> TutorialContent(
            page = state.tutorialPage,
            onPageSettled = { onAction(OnboardingAction.PageSettled(it)) },
            onBack = { onAction(OnboardingAction.Back) },
            onSkip = { onAction(OnboardingAction.SkipTutorial) },
            onNext = { onAction(OnboardingAction.NextPage) },
        )
        OnboardingStep.PROFILE -> ProfileSetupContent(state, onAction)
    }
}

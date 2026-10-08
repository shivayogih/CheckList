package com.dataloom.checklist.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dataloom.checklist.presentation.home.HomeScreen
import com.dataloom.checklist.presentation.settings.LanguageScreen
import com.dataloom.checklist.presentation.settings.SettingsScreen
import kotlinx.serialization.Serializable

// Type-safe destinations: arguments (from Phase 3, e.g. a checklist id) become constructor fields.
@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data object LanguageRoute

@Composable
fun CheckListNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLanguage = { navController.navigate(LanguageRoute) },
            )
        }
        composable<LanguageRoute> {
            LanguageScreen(onBack = { navController.popBackStack() })
        }
    }
}

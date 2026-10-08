package com.dataloom.checklist.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dataloom.checklist.presentation.category.AddCategoriesScreen
import com.dataloom.checklist.presentation.checklist.create.CreateChecklistScreen
import com.dataloom.checklist.presentation.checklist.detail.ChecklistDetailNavigation
import com.dataloom.checklist.presentation.checklist.detail.ChecklistDetailScreen
import com.dataloom.checklist.presentation.checklist.item.ItemEditorScreen
import com.dataloom.checklist.presentation.home.HomeScreen
import com.dataloom.checklist.presentation.masteritem.AddItemsScreen
import com.dataloom.checklist.presentation.settings.LanguageScreen
import com.dataloom.checklist.presentation.settings.SettingsScreen
import com.dataloom.checklist.presentation.settings.profile.ProfileScreen
import kotlinx.serialization.Serializable

// Type-safe destinations: arguments are constructor fields. IDs travel as their String values
// because navigation arguments must be serializable primitives.
@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data object LanguageRoute

@Serializable
data object ProfileRoute

@Serializable
data object CreateChecklistRoute

@Serializable
data class ChecklistDetailRoute(val checklistId: String)

@Serializable
data class AddCategoriesRoute(val checklistId: String)

@Serializable
data class AddItemsRoute(val checklistId: String, val sectionId: String)

/** A null [itemId] creates a custom item in the section, pre-filled with [initialName]. */
@Serializable
data class ItemEditorRoute(
    val checklistId: String,
    val sectionId: String,
    val itemId: String? = null,
    val initialName: String = "",
)

@Composable
fun CheckListNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onCreateChecklist = { navController.navigate(CreateChecklistRoute) },
                onOpenChecklist = { id -> navController.navigate(ChecklistDetailRoute(id.value)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLanguage = { navController.navigate(LanguageRoute) },
                onOpenProfile = { navController.navigate(ProfileRoute) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable<LanguageRoute> {
            LanguageScreen(onBack = { navController.popBackStack() })
        }
        composable<CreateChecklistRoute> {
            CreateChecklistScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    // Back from the new checklist goes to Home, not to the finished form.
                    navController.navigate(ChecklistDetailRoute(id.value)) {
                        popUpTo<CreateChecklistRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<ChecklistDetailRoute> { entry ->
            val route = entry.toRoute<ChecklistDetailRoute>()
            ChecklistDetailScreen(
                checklistId = route.checklistId,
                navigation = ChecklistDetailNavigation(
                    onBack = { navController.popBackStack() },
                    onAddCategories = { navController.navigate(AddCategoriesRoute(route.checklistId)) },
                    onAddItem = { sectionId -> navController.navigate(AddItemsRoute(route.checklistId, sectionId.value)) },
                    onEditItem = { sectionId, itemId ->
                        navController.navigate(ItemEditorRoute(route.checklistId, sectionId.value, itemId.value))
                    },
                ),
            )
        }
        composable<AddCategoriesRoute> { entry ->
            AddCategoriesScreen(
                checklistId = entry.toRoute<AddCategoriesRoute>().checklistId,
                onDone = { navController.popBackStack() },
            )
        }
        composable<AddItemsRoute> { entry ->
            val route = entry.toRoute<AddItemsRoute>()
            AddItemsScreen(
                checklistId = route.checklistId,
                sectionId = route.sectionId,
                onDone = { navController.popBackStack() },
                onCreateCustom = { name ->
                    navController.navigate(ItemEditorRoute(route.checklistId, route.sectionId, initialName = name))
                },
            )
        }
        composable<ItemEditorRoute> { entry ->
            val route = entry.toRoute<ItemEditorRoute>()
            ItemEditorScreen(
                checklistId = route.checklistId,
                sectionId = route.sectionId,
                itemId = route.itemId,
                initialName = route.initialName,
                onBack = { navController.popBackStack() },
                onSaved = {
                    // Opened from "Add item" or from the detail screen: either way, back to the checklist.
                    if (!navController.popBackStack<ChecklistDetailRoute>(inclusive = false)) navController.popBackStack()
                },
            )
        }
    }
}

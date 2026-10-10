package com.dataloom.checklist.presentation.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.ads.BannerAdSlot
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistSort
import com.dataloom.checklist.presentation.common.countText
import com.dataloom.checklist.presentation.common.dismissKeyboardOnOutsideInteraction
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.common.progressText
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppIconButton
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.ConfirmDialog
import com.dataloom.checklist.presentation.components.MenuAction
import com.dataloom.checklist.presentation.components.OverflowMenu
import com.dataloom.checklist.presentation.components.SearchField
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onCreateChecklist: () -> Unit,
    onOpenChecklist: (ChecklistId) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    greetingViewModel: HomeGreetingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val greeting by greetingViewModel.greeting.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            // Each effect gets its own coroutine so a waiting snackbar never blocks the next effect.
            scope.launch {
                when (effect) {
                    is HomeEffect.Archived -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.home_archived_message, effect.title),
                            actionLabel = resources.getString(R.string.action_undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onAction(HomeAction.UndoArchive(effect.id))
                    }
                    is HomeEffect.Duplicated -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.home_duplicated_message, effect.title),
                            actionLabel = resources.getString(R.string.action_open),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) onOpenChecklist(effect.id)
                    }
                    is HomeEffect.Unarchived ->
                        snackbarHostState.showSnackbar(resources.getString(R.string.home_unarchived_message, effect.title))
                    is HomeEffect.Deleted ->
                        snackbarHostState.showSnackbar(resources.getString(R.string.home_deleted_message, effect.title))
                    is HomeEffect.Error -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
                }
            }
        }
    }

    // Back on Home would close the app at once; ask first (Home is the root of the back stack).
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    if (confirmExit) {
        val activity = LocalActivity.current
        ConfirmDialog(
            title = stringResource(R.string.home_exit_title),
            message = stringResource(R.string.home_exit_message),
            confirmLabel = stringResource(R.string.action_exit),
            onConfirm = {
                confirmExit = false
                activity?.finish()
            },
            onDismiss = { confirmExit = false },
        )
    }

    HomeContent(
        state = state,
        greeting = greeting,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onOpenSettings = onOpenSettings,
        onCreateChecklist = onCreateChecklist,
        onOpenChecklist = onOpenChecklist,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    greeting: GreetingUi?,
    snackbarHostState: SnackbarHostState,
    onAction: (HomeAction) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateChecklist: () -> Unit,
    onOpenChecklist: (ChecklistId) -> Unit,
) {
    Scaffold(
        modifier = Modifier.keyboardAwareScreen(),
        topBar = { HomeTopBar(onOpenSettings) },
        floatingActionButton = {
            if (!state.isFirstUse) {
                // The content-slot overload: its label merges into the button, so TalkBack reads
                // "Create checklist" (the icon/text overload left the button unlabelled in the audit).
                ExtendedFloatingActionButton(onClick = onCreateChecklist) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.home_create_checklist))
                }
            }
        },
        // Ads (CL-370): the only Home ad; takes no space when ads are off or not allowed yet.
        bottomBar = { BannerAdSlot() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isFirstUse) {
            FirstUse(onCreateChecklist, Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .dismissKeyboardOnOutsideInteraction(),
                // Room below the last card so the floating button never covers its menu.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Stable keys: the greeting card appearing must not shift the search field's identity.
                item(key = "search", contentType = "search") { HomeSearch(state.search, onAction) }
                item(key = "filter-sort", contentType = "filter-sort") {
                    FilterAndSort(state.filter, state.sort, onAction)
                }
                greeting?.let { item(key = "greeting", contentType = "greeting") { GreetingCard(it) } }
                if (!state.isLoading && state.checklists.isEmpty()) {
                    item(key = "empty", contentType = "empty") { EmptyResults(state) }
                }
                items(state.checklists, key = { it.id.value }, contentType = { "checklist" }) { row ->
                    ChecklistCard(row, onAction, onOpen = { onOpenChecklist(row.id) })
                }
            }
        }
    }

    state.pendingDelete?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.delete_checklist_title, row.title),
            message = stringResource(R.string.delete_checklist_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onAction(HomeAction.ConfirmDelete) },
            onDismiss = { onAction(HomeAction.DismissDelete) },
        )
    }
}

/** The app logo and name, and Settings as an icon only (01 mockup); TalkBack and the tooltip say "Settings". */
@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    AppTopBar(
        title = stringResource(R.string.app_name),
        titleIcon = {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        },
        actions = {
            AppIconButton(
                icon = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
            )
        },
    )
}

/** 00g: shown only when the profile has a name. */
@Composable
private fun GreetingCard(greeting: GreetingUi) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.home_greeting_title, greeting.name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = countText(R.plurals.home_greeting_in_progress, greeting.inProgress),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HomeSearch(search: String, onAction: (HomeAction) -> Unit) {
    SearchField(
        value = search,
        onValueChange = { onAction(HomeAction.SearchChanged(it)) },
        placeholder = stringResource(R.string.home_search_label),
        clearContentDescription = stringResource(R.string.search_clear),
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterAndSort(filter: ChecklistFilter, sort: ChecklistSort, onAction: (HomeAction) -> Unit) {
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }
    // A flow row, not a row: at 200% font the sort button wraps to its own line instead of squeezing.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Active / Archived as one segmented button (01 mockup): the chosen side is filled and ticked.
        SingleChoiceSegmentedButtonRow {
            val options = listOf(
                ChecklistFilter.ACTIVE to R.string.home_filter_active,
                ChecklistFilter.ARCHIVED to R.string.home_filter_archived,
            )
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = filter == option,
                    onClick = { onAction(HomeAction.FilterChanged(option)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.weight(1f))
        Box {
            AppIconButton(
                icon = painterResource(R.drawable.ic_sort),
                contentDescription = stringResource(R.string.home_sort_button, stringResource(sort.labelRes())),
                onClick = { sortMenuOpen = true },
            )
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                ChecklistSort.entries.forEach { option ->
                    val current = option == sort
                    val color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(option.labelRes()),
                                color = color,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        trailingIcon = if (current) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = color) }
                        } else {
                            null
                        },
                        modifier = Modifier.semantics { selected = current },
                        onClick = {
                            sortMenuOpen = false
                            onAction(HomeAction.SortChanged(option))
                        },
                    )
                }
            }
        }
    }
}

private fun ChecklistSort.labelRes(): Int = when (this) {
    ChecklistSort.RECENT -> R.string.sort_recent
    ChecklistSort.TITLE -> R.string.sort_title
    ChecklistSort.PROGRESS -> R.string.sort_progress
}

@Composable
private fun EmptyResults(state: HomeUiState) {
    val text = when {
        state.search.isNotBlank() -> stringResource(R.string.home_no_results, state.search.trim())
        state.filter == ChecklistFilter.ARCHIVED -> stringResource(R.string.home_no_archived)
        else -> stringResource(R.string.home_no_active)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    )
}

@Composable
private fun ChecklistCard(row: ChecklistRowUi, onAction: (HomeAction) -> Unit, onOpen: () -> Unit) {
    val resources = LocalResources.current
    val actions = listOf(
        MenuAction(stringResource(R.string.action_duplicate)) {
            onAction(HomeAction.Duplicate(row.id, copyTitle(row.title, { resources.getString(R.string.checklist_copy_title, it) })))
        },
        if (row.isArchived) {
            MenuAction(stringResource(R.string.action_unarchive)) { onAction(HomeAction.Unarchive(row)) }
        } else {
            MenuAction(stringResource(R.string.action_archive)) { onAction(HomeAction.Archive(row)) }
        },
        MenuAction(stringResource(R.string.action_delete)) { onAction(HomeAction.RequestDelete(row)) },
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .clickable(onClickLabel = stringResource(R.string.action_open), onClick = onOpen)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (row.totalItems == 0) {
                        stringResource(R.string.progress_no_items)
                    } else {
                        progressText(row.completedItems, row.totalItems)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (row.totalItems > 0) {
                    LinearProgressIndicator(progress = { row.progress }, modifier = Modifier.fillMaxWidth())
                }
                if (row.isArchived) {
                    Text(
                        stringResource(R.string.home_filter_archived),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OverflowMenu(stringResource(R.string.checklist_more_options, row.title), actions)
        }
    }
}

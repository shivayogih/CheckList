# Performance, state and concurrency

Audit and fixes for CL-290 to CL-299 (branch `feature/CL-290-performance-robustness`).

**Read this first: nothing here was measured on a device.** The build container has no Android SDK, no
emulator and no device, and Android builds only run on GitHub Actions. Every statement below is one of
two kinds: a fact about the code (with file and line), or a design argument about cost. There are no
frame-time, start-up-time, memory or jank numbers, and none are claimed. The "Not measured" section lists
what a person with a device should measure.

## 1. Findings and what was done

| # | Area | Finding (evidence) | Fix |
|---|------|--------------------|-----|
| 1 | Coroutines | `Dispatchers.IO`/`Default` hardcoded in `TransferUseCases.kt` (export, preview, read), `TransferViewModel.kt` (PDF write), `EncryptedProfileRepository.kt` (secondary constructor) and `AppModule.kt` (application scope) | `@IoDispatcher`/`@DefaultDispatcher` qualifiers in `domain/.../common/Dispatchers.kt`; one provider, `data/.../di/CoroutinesModule.kt`; all five sites take them by constructor. `ConcurrencyRulesTest` fails `:domain:test` on any other `Dispatchers.IO/Default/Unconfined`, `GlobalScope`, `runBlocking` or stray `CoroutineScope(` in production code |
| 2 | Coroutines | Export built and encoded the whole JSON on the caller's thread (`TransferUseCases.kt:58` before the change; `viewModelScope` is main) | Build and encode run in `withContext(default)` (line 58); file write in `withContext(io)` (line 62) |
| 3 | Coroutines | `ExportFiles.newFile` (mkdirs, listFiles, delete) and FileProvider setup ran on main inside Share (`FileSharer.kt:64-71`, called from `TransferViewModel`) | Wrapped in `withContext(io)` (`TransferViewModel.kt:124,132,179,181`) |
| 4 | Coroutines | Cancellation: every `catch (Exception)` in a suspending path rethrows `CancellationException` (`AiCommandViewModel.kt` `guarded`, `CompositeAIService.kt:69`); the other `catch` clauses name specific non-cancellation types; no `runCatching` around suspend calls (the two in `ProfileKeys.kt` wrap blocking Keystore calls) | None needed; the new background warm-up (`startup/DatabaseWarmUp.kt`) follows the same pattern |
| 5 | Coroutines | Application scope (`AppModule.kt`) is justified in exactly two places: finishing a confirmed AI plan (`AiCommandViewModel`) and committing Undo-pending deletes in `onCleared` (`ChecklistDetailViewModel.kt:242`). It has a `SupervisorJob` and a `CoroutineName`; it deliberately has no exception handler, so an unexpected failure is seen instead of swallowed. No `GlobalScope` anywhere | Scope now built from the injected default dispatcher; documented on the qualifier |
| 6 | Flows | Room flow `.map { }` ran in the collector's context, i.e. main for a ViewModel (`RoomChecklistRepository` observe functions, `RoomCatalogRepository` observe functions incl. a `Collator` sort over all categories) | Every observe function ends in `.flowOn(default)` (`RoomChecklistRepository.kt:67,76`, `RoomCatalogRepository.kt:61,117,191`). Suspend DAO calls need nothing: Room runs them on its own executor |
| 7 | Flows | Home had no search debounce: each keystroke re-ran the summaries query with its two correlated COUNT subqueries (`ChecklistDao.observeSummaries`) | `HomeViewModel.kt:125`: search text is debounced 250 ms, an empty text applies at once, sort and filter apply at once; the text field itself reads the un-debounced state, so typing never lags |
| 8 | Flows | Home ran a second, complete `observeChecklists(ALL)` (every checklist with both COUNT subqueries) only to learn whether any checklist exists (`HomeViewModel` before the change) | `ChecklistRepository.observeHasChecklists()` (additive, default method so existing fakes still compile), `SELECT EXISTS(SELECT 1 FROM checklist)` in `ChecklistDao.observeAny` (`ChecklistDao.kt:46`), `ObserveHasChecklistsUseCase` |
| 9 | Flows | Already correct, left alone: all UI state uses `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` (Home, Detail, ItemEditor, AddItems, Create, AddCategories, AiCommand, AiSettings, HomeGreeting); screens use `collectAsStateWithLifecycle`; one-shot effects are `Channel(BUFFERED).receiveAsFlow()`; `AddItemsViewModel.kt` already had `debounce` + `mapLatest` + `distinctUntilChanged` + a shared `shareIn` for the detail query; `CreateChecklistViewModel` debounces the title-exists check | None |
| 10 | Flows | `ProfileViewModel` collected the profile (Keystore decrypt) from `init` for the ViewModel's whole life, not the screen's | `ProfileViewModel.kt:93`: `channelFlow` + `stateIn(WhileSubscribed(5_000))`; observation runs only while the screen shows (plus the 5 s rotation grace period). Typed text stays in the ViewModel's state flow |
| 11 | Flows | `AddCategoriesViewModel` sends an effect from inside a `combine` lambda (`AddCategoriesViewModel.kt` ~line 95) | Not changed (out of scope): a repeated `ChecklistGone` only asks the screen to go back, so it is harmless. Listed under follow-ups |
| 12 | State | Search, sort and filter on Home were plain `MutableStateFlow`s: lost on process death | `SavedStateHandle` keys in `HomeViewModel` (`home_search`, `home_sort`, `home_filter`); unknown values fall back to defaults |
| 13 | State | Create-checklist title, description and ticked categories were lost on process death | `CreateChecklistViewModel` mirrors them to `SavedStateHandle` |
| 14 | State | Item editor name, amount, unit, notes and the "save to suggestions" switch were lost; the loader would also have overwritten them | `ItemEditorViewModel`: fields mirrored, and a `dirty` flag stops `load()` from replacing the user's edits with the stored item |
| 15 | State | Add-items search text and "all categories" switch; add-categories ticks; detail rename dialog and its text; AI command text | Same pattern in `AddItemsViewModel`, `AddCategoriesViewModel`, `ChecklistDetailViewModel`, `AiCommandViewModel` |
| 16 | State | Profile text (name, email, phone, address) must not be written unencrypted | Deliberately **not** in any `SavedStateHandle`: `ProfileViewModel` and `OnboardingViewModel` take none for it (already the documented rule in `OnboardingViewModel`). After process death the form is refilled from the encrypted store. Not changed: onboarding already follows this |
| 17 | State | Rotation, locale, dark mode, font size: `android:configChanges` is not used (`AndroidManifest.xml`), no UI state in Activity fields (`MainActivity` holds only an injected provider), `StartViewModel` keeps the first-run answer across recreation, ViewModels are scoped to nav entries, `rememberSaveable` is used for local toggles (`OverflowMenu`, `HomeScreen` sort menu, unit pickers) and `LazyColumn` scroll state is saved by `rememberLazyListState` | None needed. Dialog state that lives in ViewModels (delete confirmation, new category, rename) survives configuration change by construction |
| 18 | State | By design not restored after process death: delete confirmations (a destructive confirm must be asked again), the AI review sheet and its plan (a plan must never run from state the user did not see), items waiting for Undo (the Undo window is gone, the items stay), picked add-items rows with amounts (hold full catalog objects) | Documented here and in KDoc |
| 19 | DI | Scopes: repositories, database, DataStore, language provider are singletons; ViewModels use `@HiltViewModel` and `@AssistedInject`; the only `Context` injected is `@ApplicationContext` (`TransferViewModel`, `ContentResolverDocumentAccess`, `ExportFiles`, DataStore store): no Activity context leaks. Bindings are `@Binds` wherever there is an implementation to bind; `@Provides` is used only for third-party or computed objects (database, DataStore, scope, dispatchers, clock) | None needed. `EncryptedProfileRepository` lost its hand-written secondary constructor (it existed only to hardcode a dispatcher) and is now plain constructor injection |
| 20 | DI | Test replacement: the dispatcher module is a single `object` so a test can `@TestInstallIn(replaces = [CoroutinesModule::class])`. There is no Hilt graph test in the repository at this commit (`app/src/test` has none), so "keeps passing" is vacuous; the graph is validated at compile time by the Hilt/KSP processor on CI | Added qualifier bindings only; graph checked by CI build. Recommended follow-up: an instrumented or Robolectric Hilt graph test once the test-setup track lands |
| 21 | Compose | Domain types live in a module without the Compose compiler, so every UI model holding a `ChecklistId`, `Quantity` or `UnitDef` was inferred **unstable**. With strong skipping (default) an unstable parameter is skipped only when the instance is identical, but the ViewModels build new `ChecklistRowUi`/`ItemUi` instances on every emission, so every row recomposed on every change | `app/compose-stability.conf` declares `com.dataloom.checklist.domain.model.**` stable (checked: no `var`, `MutableList`, `MutableMap` or array in `domain/model`), wired in `app/build.gradle.kts` (`composeCompiler { stabilityConfigurationFiles }`). Reports: `./gradlew :app:assembleDevRelease -PcomposeReports` writes to `app/build/compose_compiler` |
| 22 | Compose | `kotlinx.collections.immutable` is not used in the project | **Not added.** With the stability file above and `List` fields inside stable data classes compared structurally, the library would add a dependency and conversion code in every ViewModel for no measurable gain that this audit can demonstrate. Revisit if a compiler report shows an unstable `List` parameter on a hot row |
| 23 | Compose | `LazyColumn`s had `key` for item rows but no `contentType`, and the header, search, greeting and footer items had no key (`HomeScreen.kt`, `ChecklistDetailScreen.kt`, `AddItemsScreen.kt`, `UnitPicker.kt`). Without a key the greeting card appearing shifted later items' identity | Keys and `contentType` on every lazy item |
| 24 | Compose | Allocation in composition: `ChecklistDetailScreen` rebuilt the custom-unit-label map (flatMap/filter/associate over all items) on every recomposition; `viewModel::onAction` created a new reference each time; `UnitPickerDialog` re-sorted the unit list on every pass | `remember(state.sections)`, `remember(viewModel)`, `remember(units)` |
| 25 | Compose | `derivedStateOf`: no screen computes state from scroll position or from a frequently changing state that needs it, so none was added (adding it without a use is noise) | None |
| 26 | Data | Indexes: see section 3 | No schema change, no migration |
| 27 | Data | Transactions: every multi-row write in `RoomChecklistRepository` and `deleteCategory` uses `db.withTransaction`; import runs inside one outer transaction (`RoomTransactionRunner`); seed runs in one transaction (`SeedLoader`) | None needed |
| 28 | Data | Projections: `observeSummaries` returns a row type with two counts, not item lists; detail uses one `@Transaction` relation query. The master-item search returns `MasterItemMatch` (item plus matched text). Full-row `SELECT *` remains only on tables with at most a few hundred rows | None needed |
| 29 | Start-up | `KeysetProfileAeadProvider` registered Tink key managers in a property initializer, i.e. on the thread that first injected the profile repository (a ViewModel, so main) (`ProfileKeys.kt:90`) | `by lazy`, first used inside `existingAead`/`getOrCreateAead`, which the repository calls on `@IoDispatcher` |
| 30 | Start-up | First database open, which also imports the seed (about 87 items, 7 languages) on a fresh install or a newer seed, happened on the first query, on the critical path of the first screen | `startup/DatabaseWarmUp.kt` opens it in the background from `Application.onCreate`; the database is injected as `dagger.Lazy` so construction does not happen on main |
| 31 | Start-up | Keystore on main: none. All Keystore and Tink calls are inside `EncryptedProfileRepository`, which switches to the I/O dispatcher for `observeProfile` (`flowOn(io)`) and `saveProfile`/`clearProfile` (`withContext(io)`) | None needed |
| 32 | Start-up | R8: release builds minify and shrink resources (`app/build.gradle.kts`). `proguard-rules.pro` was empty. Navigation routes are `@Serializable` (library rules), Room and Hilt ship their rules | Added the Tink-documented keep rule for protobuf-lite message fields. CI already builds `assembleStagingRelease` and `assembleProductionRelease`, which runs R8 |
| 33 | Start-up | Baseline Profile | See section 4 |

## 2. Rules this adds

1. **No class names a concrete dispatcher.** Ask for `@IoDispatcher` (files, content resolver, Keystore, SharedPreferences) or `@DefaultDispatcher` (sorting, encoding, parsing, mapping a list). Only `CoroutinesModule` may write `Dispatchers.IO/Default`. Enforced by `ConcurrencyRulesTest`.
2. **A suspend function that switches with `withContext` is main-safe**, so a ViewModel calls it without thinking about threads. A function that returns a Flow from Room ends in `flowOn(default)`.
3. **A ViewModel's UI state is `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.** A ViewModel never starts a collection in `init` for UI data. The 5 s grace period keeps the upstream alive across a rotation.
4. **A text field is bound to a flow with no thread hop in between** (no `flowOn` on the flow that carries the typed text), otherwise the cursor jumps. Debounce the query that goes to the database, not the text that is shown.
5. **State that a user typed goes into `SavedStateHandle`; state that is sensitive does not.** Profile fields are never saved unencrypted. Destructive confirmations and AI plans are never restored.
6. **Never swallow `CancellationException`.** Catch specific types, or rethrow it first.
7. **Domain models stay immutable** (`val` only, read-only collections): `compose-stability.conf` depends on it.

## 3. Data layer decisions (with evidence)

Seed size: `catalog.json` has 12 units, 15 categories and 87 items (read from the file, not estimated); translations add one row per item and language. User data is the only table that can grow: `checklist`, `checklist_category`, `checklist_item`, plus custom master items and categories.

| Query | Filter / sort / join | Index | Verdict |
|-------|----------------------|-------|---------|
| `observeSummaries` | `is_archived = ?`, `ORDER BY updated_at DESC` | `checklist(is_archived, updated_at)` | covered. The `LIKE '%x%'` search cannot use any B-tree index; it scans `checklist` (one row per checklist the user created, realistically tens to low hundreds) |
| summary counts | `checklist_category.checklist_id`, `checklist_item.checklist_category_id` | unique `(checklist_id, category_id)`; `(checklist_category_id, position)` | covered |
| `observeDetail`, `getForSection` | `checklist_id`, `checklist_category_id ORDER BY position` | as above | covered |
| `observeVisibleInCategory`, `mostUsed` | `category_id`, `is_hidden`, `ORDER BY use_count DESC` | `master_item(category_id, is_hidden)` | covered for the filter. The sort on `use_count` is over at most one category (seed: 87 items in 15 categories) and the all-categories form is bounded by `LIMIT`. A `(is_hidden, use_count)` index would need a schema v2 and a migration for no gain at this size, so it is **not added** |
| `search` | FTS4 `MATCH` on `item_search_fts.text`, join `master_item.id`, `category_id` | FTS4 index; PK; `(category_id, ...)` | FTS is used for search (not `LIKE`), with unicode61 tokenizer and prefix matching. `locale`, `ref_type`, `category_id` are `notIndexed` columns used only as filters on the already-matched rows |
| translations | `locale IN (...)`, `canonical_key IN (...)` | PK `(canonical_key, locale)` | covered by the PK for key lookups; the `locale IN` only scans form scans a table of at most seven rows per key |
| `observeWithUsage` | `COUNT` per category | `checklist_category(category_id)` | covered |

**Paging 3: not justified, not added.** No query can plausibly return more than about 1,000 rows: the largest are the user's own checklists (Home, realistic range tens), the items of one checklist (a shopping list; the export format itself caps at `TransferLimits.MAX_ITEMS`), and a category's master items (seed: at most a few dozen). Home and Detail are already `LazyColumn`s, so only visible rows are composed. The cost that would grow first is the summary query's two COUNT subqueries per checklist, which Paging would not fix. Revisit if the export limits are raised or Home gets a thousand checklists; the trigger to measure is the time of `observeSummaries` on a 1,000-checklist database.

## 4. Baseline Profile: what was and was not done

Done: `androidx.profileinstaller:profileinstaller:1.4.1` (stable, checked on the AndroidX release page on 2026-10-08; a plain AAR, no Kotlin/AGP coupling) and `app/src/main/baseline-prof.txt`, a **hand-written** profile for the start-up path (application, activity, first-run flag, navigation, Home, database open, checklist query). CI proves the file parses and that the release build still assembles (`assembleStagingRelease`/`assembleProductionRelease` compile the ART profile). It does **not** prove the profile improves anything: a profile is only known to be good when it is recorded from a real run.

Deferred (needs a device or emulator): a `:baselineprofile` Macrobenchmark module with `BaselineProfileRule` to generate the profile from a real launch + scroll journey, a startup `MacrobenchmarkRule` (cold start `StartupTimingMetric`), and a CI job that runs on a device lab. Nothing in this repository's CI can run an instrumented test, so adding the module now would be code that is never executed.

## 5. Not measured (needs a device)

- Cold, warm and hot start time, before/after the warm-up, the lazy Tink configuration and the profile.
- Frame timing and jank on Home and Detail with 100, 500 and 1,000 items (the stability file should reduce recomposition counts; the compiler report with `-PcomposeReports` shows skippability but not time).
- Memory (heap, ViewModel retention across 20 rotations) and whether the application scope ever outlives its tasks.
- `EXPLAIN QUERY PLAN` for the queries in section 3 on a populated database (the table says which index each query should use; it was reasoned from the SQL and the entity annotations, not run).
- Real process-death restore: the ViewModel tests use `SavedStateHandle` directly, which proves the keys round-trip; they cannot prove the platform writes and restores the Bundle. A manual check is "Don't keep activities" plus the developer option "Background process limit".
- Whether `proguard-rules.pro` is complete for a release build exercised on a device: CI proves it builds, not that every path (Tink keyset parsing in particular) works after shrinking.

## 6. Tests added

- `ConcurrencyRulesTest` (`:domain`, runs locally): no concrete dispatcher outside the module, no `GlobalScope`/`runBlocking`, scopes only created in `AppModule`.
- `HomeViewModelTest`: search applies after the debounce and not per keystroke, clearing applies at once, search/sort/filter restore from `SavedStateHandle`, an unknown saved sort falls back.
- `CreateChecklistViewModelTest`, `AddItemsViewModelTest`, `ItemEditorViewModelTest` (restored edits win over the stored item and nothing is written), `ChecklistDetailViewModelTest` (rename dialog restore), `ProfileViewModelTest` (store observed only while subscribed; typed text survives a pause).
- `RoomChecklistRepositoryTest.hasChecklistsFollowsTheTableWithoutLoadingProgress`.

## 7. Contract changes

- `ChecklistRepository.observeHasChecklists()` added with a default implementation (additive). New `ObserveHasChecklistsUseCase`.
- Constructors changed: `ExportChecklistsUseCase` and `PreviewImportUseCase` take `@IoDispatcher` and `@DefaultDispatcher`; `RoomChecklistRepository` and `RoomCatalogRepository` take `@DefaultDispatcher`; `TransferViewModel` takes `@IoDispatcher`; `HomeViewModel` takes `ObserveHasChecklistsUseCase`. Tests that build these directly must pass them (`Dispatchers.Unconfined` or a test dispatcher). The SavedStateHandle parameter added to ViewModels has a default value, so existing tests that omit it still compile.

## 8. Follow-ups (not done)

- `AddCategoriesViewModel` sends `ChecklistGone` from inside a `combine` transform (a side effect in a pure operator); move it to an `onEach` on the detail flow.
- `HomeGreetingViewModel` and `HomeViewModel` both run the ACTIVE summaries query; share one flow if Home recomposition profiling shows it matters.
- Hilt graph test, and macrobenchmark / baseline profile generation, when a device lab exists.

# Architecture

How CheckList is put together and the rules that keep it that way. The approved design, with the reasoning behind every decision, is [phase0-architecture.md](phase0-architecture.md) (revision 2). This page is the working reference: read it first, then follow the links.

**Status legend:** **Implemented** = on `develop` today. **In progress** = on a feature branch or in review. **Planned (Phase N)** = designed, not built yet.

## At a glance

| What | Status |
|---|---|
| Four Gradle modules, version catalog, Kotlin 2.4 / AGP 9.4 / Gradle 9.6 | Implemented (Phase 1, CL-101) |
| Flavors `dev` / `staging` / `production`, versionCode Option B | Implemented (Phase 1, CL-102) |
| Material 3 theme, type-safe Navigation Compose shell (Home, Settings, Language) | Implemented (Phase 1, CL-103) |
| Per-app language for 7 languages | Implemented (Phase 1, CL-104) |
| GitHub Actions build of all three environments | Implemented (Phase 1, CL-105) |
| Domain models and repository contracts | In progress (Phase 2, CL-110) |
| Use cases, Room data layer, Hilt, seed catalog | Planned (Phase 2, CL-111 to CL-119) |
| Feature screens | Planned (Phase 3) |
| Optional AI layer | Planned (Phase 10) |

## Principles

1. **Offline-first.** Every core feature works in airplane mode. There is no backend and no account.
2. **Room is the single source of truth.** Screens observe `Flow`s from Room. A write from anywhere (UI, import, AI) shows up everywhere without manual refresh (ADR-002).
3. **Business rules live once, in `:domain`.** The UI, the JSON importer and the AI executor all call the same use cases and validators.
4. **AI proposes, the app disposes.** AI output is a typed `ActionPlan` that the app validates, shows for confirmation when needed, and executes through use cases (ADR-008).
5. **Nothing secret in the app or the repository.** See [security.md](security.md).

## Modules

```text
                 :app  (UI, ViewModels, navigation, DI)
                /        |         \
               ▼         ▼          ▼
           :data  ──►  :domain  ◄──  :ai
   (Room, DataStore)  (pure Kotlin)  (optional AI)

   Arrows are Gradle dependencies. :data implements the :domain
   repository interfaces. :ai sees :domain only, never :data.
```

| Module | Plugin | Depends on | Holds | Status |
|---|---|---|---|---|
| `:app` | `com.android.application` | `:domain`, `:data`, `:ai` | UI, navigation, localization helpers, dependency wiring | Implemented shell (Phase 1) |
| `:domain` | `org.jetbrains.kotlin.jvm` | coroutines only | Models, repository interfaces, use cases, validation, `SupportedLanguages` | Languages implemented; models in progress (Phase 2) |
| `:data` | `com.android.library` | `:domain` | Room, DataStore, seed loader, import/export, PDF | Empty module; Planned (Phase 2, 5, 6) |
| `:ai` | `com.android.library` | `:domain` only | `AIService` implementations, tool catalog, command mapper/validator, confirmation policy, executor | Empty module; Planned (Phase 10) |

Two rules are enforced by the compiler rather than by review (ADR-001):

- `:domain` is a plain Kotlin/JVM module, so it cannot import Android, Room or Compose.
- `:ai` has no dependency on `:data`, so AI code cannot reach the database even by accident.

Do not add a dependency that breaks either rule. If you think you need one, write an ADR first ([adr/README.md](adr/README.md)).

## Layers and data flow

The presentation pattern is MVVM with a unidirectional flow: each screen has one immutable `UiState`, receives user `Action`s through `onAction(...)`, and emits one-shot `Effect`s (snackbars, navigation).

A write, for example ticking an item (Planned, Phase 2-3):

```text
Checkbox tap
 → ChecklistDetailViewModel.onAction(ToggleItem(id))
 → SetItemCompletedUseCase                 (:domain, validates)
 → ChecklistRepository.setItemCompleted    (:domain interface)
 → RoomChecklistRepository → DAO update    (:data, Dispatchers.IO)
 → Room invalidates → DAO Flow emits → ViewModel maps to UiState → recomposition
```

Multi-step writes (create with categories, add several items, duplicate, import, AI plan) run in one Room transaction inside the repository.

## Packages

Base package `com.dataloom.checklist`. Sources live under `src/main/kotlin`.

```text
app/      presentation/{home,settings,components,theme,...}, navigation/, localization/, di/ (Phase 2)
domain/   domain/{model,repository,common,localization,usecase (Phase 2),validation (Phase 2)}
data/     data/{local/{dao,entity,database,migration,fts},repository,mapper,seed,settings,profile,importexport,pdf}
ai/       ai/{model,tools,service,mapper,policy,executor}
```

## Build configuration

| Item | Value | Where |
|---|---|---|
| Gradle | 9.6.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.0, with built-in Kotlin (no `org.jetbrains.kotlin.android` plugin) | `gradle/libs.versions.toml` |
| Kotlin | 2.4.20 | `gradle/libs.versions.toml` |
| Compose BOM | 2026.09.00 | `gradle/libs.versions.toml` |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 36 / 26 | `app/build.gradle.kts` |
| Java target | 17 | every module |
| Annotation processing | KSP (kapt is not supported with AGP 9 built-in Kotlin) | from Phase 2 |

All versions come from the version catalog. Do not hard-code a version in a module build file.

## Environments

| Flavor | Application ID | App name | Typical variant |
|---|---|---|---|
| `dev` | `com.dataloom.checklist.dev` | CheckList Dev | `devDebug` |
| `staging` | `com.dataloom.checklist.staging` | CheckList Staging | `stagingRelease` |
| `production` | `com.dataloom.checklist` | CheckList | `productionRelease` |

Flavor means which environment; build type means how it is built (`release` is minified with R8 and resource shrinking); a variant is one of each. Each flavor sets `BuildConfig.ENVIRONMENT`. Only non-secret values go in `BuildConfig`. Versioning is described in [release-process.md](release-process.md).

## Key libraries

| Library | Use | Status |
|---|---|---|
| Jetpack Compose, Material 3 | UI | Implemented |
| Navigation Compose (type-safe routes, kotlinx.serialization) | Navigation | Implemented |
| AppCompat | Per-app language only | Implemented |
| kotlinx.coroutines | Flows, suspend APIs | Implemented |
| Hilt (KSP) | Dependency injection | Planned (Phase 2, CL-112) |
| Room (KSP), DataStore | Storage | Planned (Phase 2) |
| Tink (`tink-android`) + Android Keystore | Profile encryption | Implemented (Phase 5) |
| Turbine, Robolectric, Roborazzi | Tests | Planned (Phase 2-7) |
| Firebase AI Logic, App Check | Optional online AI | Planned (Phase 10) |

## Related documents

[database.md](database.md) · [localization.md](localization.md) · [accessibility.md](accessibility.md) · [security.md](security.md) · [ai-automation.md](ai-automation.md) · [import-export-format.md](import-export-format.md) · [testing.md](testing.md) · [adr/README.md](adr/README.md)

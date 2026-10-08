# CheckList

A universal, offline-first checklist app for Android: shopping, travel, exams, job joining, hospital visits, weddings, or anything you invent. Simple for first-time and elderly users, built with production-grade engineering underneath.

- **Offline-first**: everything works without internet; Room is the single source of truth.
- **7 languages**: English, Kannada, Hindi, Tamil, Telugu, Marathi, Malayalam, switchable inside the app.
- **Accessible**: large touch targets, large type, screen-reader labels, no hidden gestures.
- **Optional AI**: an offline quick-add parser plus opt-in Gemini suggestions; the app never depends on AI.

> Status: **Phase 1 (project setup)**. The design is in [docs/phase0-architecture.md](docs/phase0-architecture.md). A full README arrives in Phase 8.

## Modules

| Module | Type | Responsibility |
|---|---|---|
| `:app` | Android application | Compose UI, navigation, per-app language, dependency wiring |
| `:domain` | Pure Kotlin/JVM | Models, use cases, validation, language rules. No Android imports. |
| `:data` | Android library | Room, DataStore, seed catalog, import/export, PDF (from Phase 2) |
| `:ai` | Android library | Optional AI layer. Depends on `:domain` only, so it cannot reach the database. |

## Build variants

| Flavor | Application ID | Typical variant |
|---|---|---|
| `dev` | `com.dataloom.checklist.dev` | `devDebug` |
| `staging` | `com.dataloom.checklist.staging` | `stagingRelease` |
| `production` | `com.dataloom.checklist` | `productionRelease` |

```bash
./gradlew :domain:test :app:testDevDebugUnitTest      # unit tests
./gradlew :app:lintDevDebug                           # lint
./gradlew :app:assembleDevDebug                       # install-ready dev build
./gradlew :app:assembleStagingRelease :app:assembleProductionRelease   # unsigned release builds
```

Requirements: JDK 17+, Android SDK with API 37. Versions are set in [`version.properties`](version.properties); `versionCode = MAJOR*10000 + MINOR*1000 + PATCH*100 + BUILD`.

## Workflow

Branches: `main` (production) ← `release/x.y.z` (staging) ← `develop` (dev) ← `feature/CL-<id>-<name>`. Commits: `CL-101 Add checklist creation flow`. Issues are tracked in [docs/project-management/issues.csv](docs/project-management/issues.csv).

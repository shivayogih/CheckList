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

## Documentation

| Document | What it covers |
|---|---|
| [Phase 0 architecture](docs/phase0-architecture.md) | The approved design and the reasoning behind every decision |
| [Architecture](docs/architecture.md) | Modules, layers, data flow, build configuration, current status |
| [Database](docs/database.md) | Room schema, seed catalog, search, migrations |
| [Localization](docs/localization.md) | Seven languages, per-app language, string rules |
| [Accessibility](docs/accessibility.md) | Targets, Compose rules, plain language, TalkBack script |
| [Security](docs/security.md) | Data protection, secrets, signing, review checklist |
| [AI and automation](docs/ai-automation.md) | Optional in-app AI and ADK agents in CI |
| [Import/export format](docs/import-export-format.md) | JSON file specification and import pipeline |
| [Testing](docs/testing.md) | Test layers, tools, rules |
| [CI/CD](docs/ci-cd.md) | GitHub Actions and Bitrise workflows, versioning, approvals |
| [Branching strategy](docs/branching-strategy.md) | Branches, commits, PRs, protection rules |
| [Release process](docs/release-process.md) | From release branch to Google Play |
| [Troubleshooting](docs/troubleshooting.md) | Known build and runtime problems |
| [Project management](docs/project-management/README.md) | Issue, build, test and release CSVs and statuses |
| [Architecture decision records](docs/adr/README.md) | ADR index and how to add one |
| [Security policy](SECURITY.md) | How to report a vulnerability |

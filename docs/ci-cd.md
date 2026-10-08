# CI/CD

How code is built, checked and delivered, on free tiers only. Design reasoning: [phase0-architecture.md](phase0-architecture.md) sections 15 to 17 (revision 2). Branch rules are in [branching-strategy.md](branching-strategy.md); the release steps are in [release-process.md](release-process.md).

## The split, and why

| Service | Free allowance | Used for |
|---|---|---|
| **GitHub Actions** | Free and unlimited on standard runners for **public** repositories | Every PR check (high frequency), CI AI agents, deployment approvals through Environments |
| **Bitrise Hobby** | **300 credits/month**, 90-minute timeout, 5 concurrent builds, 1 private app, 1 user | Environment builds: dev, staging, production (low frequency), signing, Play upload |
| Firebase (Spark) | App Distribution, App Check, AI Logic free tier | Tester builds, AI protection |
| Google Play | One-time registration fee | Closed testing and production |

A PR-heavy month can run 50-100 PR builds, which would exhaust 300 Bitrise credits. Frequency decides the split: checks that run on every push go to GitHub Actions; builds that run a few times a month go to Bitrise (ADR-014).

All build logic lives in Gradle tasks (and, later, `ci/scripts/`), so both systems run the same commands. Moving a workflow from one to the other is a configuration change, not a rewrite.

## Status

| Workflow | Runs on | Status |
|---|---|---|
| `Android build` (`.github/workflows/android-build.yml`) | GitHub Actions | **Implemented (Phase 1, CL-105)** |
| `pr-checks` | GitHub Actions | Planned (Phase 8, replaces `Android build`) |
| `ai-review`, `ci-failure` | GitHub Actions | Planned (Phase 11) |
| `pr`, `dev`, `staging`, `production` | Bitrise | Planned (Phase 9) |

## What runs today: `Android build`

Trigger: every push to any branch, plus manual `workflow_dispatch`. The push run's status also shows on the branch's PR. Concurrent runs for the same branch cancel older ones.

```text
checkout → JDK 17 (Temurin) → gradle/actions/setup-gradle (cache)
→ ./gradlew :domain:test :app:testDevDebugUnitTest
→ ./gradlew :app:lintDevDebug
→ ./gradlew :app:assembleDevDebug :app:assembleStagingRelease :app:assembleProductionRelease
→ upload artifacts: reports (always), apks
```

The workflow has `permissions: contents: read` and uses no secrets. Release APKs it builds are **unsigned**. Because the cloud development container cannot reach `dl.google.com`, this workflow is where builds are verified (see [troubleshooting.md](troubleshooting.md)).

## Planned workflows

| Workflow | Runs on | Trigger | Steps |
|---|---|---|---|
| `pr-checks` | GitHub Actions | Every PR to `develop`, `release/*`, `main` | Commit-message check (`^CL-\d+ [A-Z]`), gitleaks, lint, detekt, ktlint, translation-key check, `testDevDebugUnitTest`, Robolectric Room/UI smoke, `assembleDevDebug`, test report |
| `ai-review` | GitHub Actions | PR, after the static jobs of `pr-checks` | ADK `review_agent`, advisory only (see [ai-automation.md](ai-automation.md)) |
| `ci-failure` | GitHub Actions | When `pr-checks` fails | ADK `failure_agent` → `CI_FAILURE_REPORT.md` |
| `pr` | Bitrise | PRs into `release/*` and `main` only (rare) | Same checks as `pr-checks`; exists to learn the Bitrise PR flow without burning credits |
| `dev` | Bitrise | Push to `develop` | `assembleDevDebug` → Firebase App Distribution (dev testers) |
| `staging` | Bitrise | Push to `release/*` | Tests → `bundleStagingRelease` → Firebase App Distribution; `bundleProductionRelease` → Android Sign → Play **closed testing** track |
| `production` | Bitrise | Tag `vX.Y.Z` | Verify the tag is on `main` and matches the version → tests, lint, security → `bundleProductionRelease` → Android Sign → release notes → `google-play-deploy@3` with `track: production`, `status: draft` |

**Credit guard:** credit use per build is measured in Phase 9 and recorded in [builds.csv](project-management/builds.csv). If `dev` on every `develop` push costs too much, it moves to a nightly schedule (only when `develop` changed). If the free limits still bite, GitHub Actions can take over any Bitrise workflow: `setup-gradle` for caching, signing secrets in a GitHub Environment, a Play-publishing action and the Firebase CLI.

### Bitrise configuration conventions (Phase 9)

- Target-based `triggers:` (`push.branch`, `pull_request.target_branch`, `tag.name`), not the legacy `trigger_map`.
- Step bundles (`setup`, `finish`) instead of the legacy `before_run`/`after_run`.
- No multi-workflow PR pipeline on Bitrise: parallelism happens on GitHub Actions instead.
- Restore Gradle cache before dependency steps; Save Gradle cache at the end.
- `bitrise.yml` syntax and stack names are re-verified against the Bitrise docs when Phase 9 starts.

## Manual approval to production (free)

1. The production workflow uploads to Play as **`status: draft`**. Nothing reaches users until a person presses release in Play Console. This draft is the manual approval gate, and it costs nothing (ADR-013).
2. Bitrise Release Management approvals are not relied on: their availability on the Hobby plan is not documented.
3. If production ever moves to GitHub Actions, it runs in a `production` Environment with the owner as required reviewer (free on public repositories).
4. Merging to `main`, creating the `v*` tag and releasing the Play draft each need the owner's explicit go (see [branching-strategy.md](branching-strategy.md)).

## Google Play closed-testing rule

The Play account is a new personal developer account, so Play requires a **closed test with at least 12 testers opted in for 14 consecutive days** before production access can be requested.

- `staging` uploads the production-ID AAB to the closed testing track from the first `release/1.0.0` build.
- The owner recruits 12+ testers with Google accounts. Firebase App Distribution testers do **not** count.
- Fixes during the 14 days are new uploads with new version codes (the `BUILD` part, below).
- Phase 12 (production) cannot start before the 14 days are complete.

## Versioning

`versionName = MAJOR.MINOR.PATCH`; **`versionCode = MAJOR*10000 + MINOR*1000 + PATCH*100 + BUILD`** (Option B, ADR-016). Implemented in `app/build.gradle.kts`, read from [`version.properties`](../version.properties).

| Version | Codes |
|---|---|
| 1.0.0 | 10000-10099 (first upload 10000, next closed-test fix 10001, ...) |
| 1.0.1 | from 10100 |
| 1.1.0 | from 11000 |

MINOR and PATCH are 0-9, BUILD is 0-99; the build fails if a value is out of range. Non-production flavors add `-dev` / `-staging` to `versionName`. From Phase 9, `BUILD` comes from [releases.csv](project-management/releases.csv) (last uploaded code + 1), and the production workflow fails if the tag differs from `versionName` or the code is not higher than the last release.

## Caching

| What | GitHub Actions | Bitrise | Invalidated by |
|---|---|---|---|
| Gradle dependencies and wrapper | `gradle/actions/setup-gradle` (implemented) | Restore/Save Gradle cache steps | Changes to `libs.versions.toml`, the wrapper or build scripts |
| Gradle build cache | `org.gradle.caching=true` (implemented) | Same | Gradle input hashing |
| Never cached | Signing files, secrets, release artifacts | Same | |

## Secrets

Secrets never reach PR builds. Signing, Play and Firebase credentials live in Bitrise protected storage; GitHub Actions holds only the Gemini key for CI AI agents, and fork PRs never receive it. Details: [security.md](security.md#secrets-and-signing).

## Reports and artifacts

Today: unit test results, lint HTML, APKs. Planned: JUnit XML, lint/detekt SARIF in GitHub code scanning, coverage, AABs, `CI_REPORT.md`, `AI_REVIEW.json`, `CI_FAILURE_REPORT.md`, release notes. Builds and test results are recorded in [project-management](project-management/README.md).

## Branch protection (Phase 8, CL-18x)

Required check: `pr-checks`. Approval count 0, because Claude works through the owner's account and cannot approve its own PRs (ADR-015). Details in [branching-strategy.md](branching-strategy.md#protection-rules).

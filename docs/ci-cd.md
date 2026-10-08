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
| `Android build` (`.github/workflows/android-build.yml`) | GitHub Actions | **Implemented (Phase 1, CL-105)**; actions SHA-pinned in Phase 8 |
| `Quality` (`.github/workflows/quality.yml`) | GitHub Actions | **Implemented (Phase 8 and 11, CL-182 to CL-184, CL-221 to CL-223)** |
| `pr-checks` (`.github/workflows/pr-checks.yml`) | GitHub Actions | **Implemented (Phase 8, CL-185)**: PR conventions only; it does not replace `Android build` |
| `Release notes` (`.github/workflows/release-notes.yml`) | GitHub Actions | **Implemented (Phase 11, CL-222)** |
| `ai-review`, `ci-failure` | GitHub Actions | Planned (Phase 11; need the opt-in Gemini key) |
| `pr`, `dev`, `staging`, `production` | Bitrise | Planned (Phase 9) |

## What runs on every push

`Android build` and `Quality` both run on every push to any branch (and on demand), in parallel. `pr-checks` runs on pull requests. Required checks for branch protection: the `build` job of `Android build`, every job of `Quality`, and `Commit and branch conventions` from `pr-checks`.

### `Quality`

| Job | What it does | Fails when |
|---|---|---|
| Gradle wrapper validation | `gradle/actions/wrapper-validation` | `gradle-wrapper.jar` is not an official Gradle release |
| Secret scan (gitleaks) | gitleaks 8.30.1 CLI over the **whole history**, report redacted; SARIF uploaded as an artifact on failure | Any leak is found |
| Static analysis (detekt) | `ci/scripts/detekt.sh` (see below); SARIF to GitHub code scanning, HTML report as an artifact | Any finding not in the baseline |
| Logging ban, CI agents and their tests | pytest for `tools/agents` and `tools/checks`; `tools/checks/logging_ban.py`; the translation, issue-sync and release-notes agents (reports in the job summary) | A test fails, direct logging is found, or an agent reports an error (warnings never fail) |

### `pr-checks`

Runs on `opened`, `synchronize`, `reopened` and `edited` (a renamed title is re-checked). `tools/checks/conventions.py` checks:

- every commit subject in `base..head` matches `^CL-\d+ [A-Z]`; merge commits and `dependabot[bot]` commits are exempt; only the subject is read, so message bodies and trailers (`Co-Authored-By:`, `Claude-Session:`) are free-form;
- the PR title follows the same rule (it becomes the squash-merge subject);
- the head branch is `feature/CL-<id>-<slug>`, `fix/CL-<id>-<slug>`, `hotfix/CL-<id>-<slug>` or `release/X.Y.Z`; `dependabot/...` branches are exempt;
- subjects over 72 characters are a warning only (several older commits are longer).

Branch name and title are passed through environment variables, never pasted into the script, so a crafted branch name cannot inject shell commands.

### Static analysis: detekt 2 (CL-182)

| Checked 2026-10-08 | Result |
|---|---|
| detekt 1.23.8 (latest 1.x, Feb 2025) | Built on Kotlin 2.0; cannot analyse Kotlin 2.4 code. This is why CL-106 was deferred. |
| detekt **2.0.0-alpha.6** (Maven Central `dev.detekt`, 2026-08-04) | Compiled against Kotlin 2.4.10, tested by detekt with Gradle 9.6.1 and AGP 9.3.1, AGP 9 built-in Kotlin supported since alpha.3. Analyses this code base (Kotlin 2.4.20, language version 2.4) without errors. **Adopted.** |
| ktlint 1.8.0 / ktlint-gradle 14.2.0 | Not adopted: one tool was the goal, and detekt covers code smells and the logging import ban; formatting stays with the IDE and `.editorconfig`. |

How it runs: `ci/scripts/detekt.sh` downloads `detekt-cli-2.0.0-alpha.6-all.jar` from Maven Central into `~/.cache/checklist-detekt`, checks its SHA-256 and runs it over `app/src`, `data/src`, `domain/src` and `ai/src` (main and test sources) in detekt's "light" mode (no type resolution). It is **not** a Gradle plugin, so it cannot break the Android build when AGP or Kotlin change, and it runs anywhere Java 17 is installed:

```bash
ci/scripts/detekt.sh                    # same check as CI; reports in build/reports/detekt/
ci/scripts/detekt.sh --update-baseline  # only to re-baseline pre-existing code (see below)
```

Configuration (`config/detekt/detekt.yml`) is detekt's defaults plus three project conventions: `@Composable` functions may be PascalCase, guard-clause early returns do not count towards `ReturnCount`, and `ForbiddenImport` bans `android.util.Log` (except `AndroidLogSink.kt`).

**Baseline (`config/detekt/baseline.xml`).** The first run found 471 issues in code written before detekt existed: 414 `MaxLineLength` (lines of 121-200 characters, about half in tests), `ReturnCount`, `SwallowedException` (deliberate in the profile key-loss handling), `LongMethod` on Compose screens, `TooManyFunctions` on repositories and a few others. Rewrapping hundreds of lines across files that other work was changing at the same time would have caused merge conflicts for no behaviour change, so they were baselined instead (442 entries; detekt matches entries by rule, file and code signature, not line number). Rules for the baseline:

- It may only ever shrink. New and changed code must pass without new entries (Definition of Done item 4); `--update-baseline` is not used to hide new findings.
- When a file is refactored anyway, fix its baselined findings and delete their entries.
- detekt is an alpha: if a future alpha breaks, pin the previous version in `detekt.sh` and record it here. Move to 2.0.0 stable when it ships.

### Secrets and supply chain (CL-184)

- **gitleaks** runs as its own CLI (MIT, no licence key, unlike `gitleaks-action` for organisations), downloaded from the gitleaks release and checked against the published SHA-256. A local scan of all 63 commits before adoption found nothing.
- **Gradle wrapper validation** on every push.
- **Actions pinned to full commit SHAs** with the version in a comment, for example `actions/checkout@3d3c42e5... # v7.0.1`. Dependabot (`github-actions` ecosystem) updates the SHA and the comment together. `Android build` keeps its v4 majors (only pinned) to avoid behaviour changes in the shared workflow.
- **Least privilege:** each workflow sets `permissions: {}` and each job asks only for what it needs: `contents: read` everywhere, `security-events: write` only for the detekt SARIF upload, `pull-requests: read` only for the issue-sync merged-PR lookup. Checkouts use `persist-credentials: false`.
- Downloaded tools (detekt, gitleaks) are verified by SHA-256; Python packages are pinned to exact versions in `tools/agents/requirements.txt` and updated by Dependabot (`pip` ecosystem).

### `Android build`

Trigger: every push to any branch, plus manual `workflow_dispatch`. The push run's status also shows on the branch's PR. Concurrent runs for the same branch cancel older ones.

```text
checkout → JDK 17 (Temurin) → gradle/actions/setup-gradle (cache)
→ ./gradlew :domain:test :app:testDevDebugUnitTest
→ ./gradlew :app:lintDevDebug
→ ./gradlew :app:assembleDevDebug :app:assembleStagingRelease :app:assembleProductionRelease
→ upload artifacts: reports (always), apks
```

The job has `permissions: contents: read` and uses no secrets. Release APKs it builds are **unsigned**. Because the cloud development container cannot reach `dl.google.com`, this workflow is where builds are verified (see [troubleshooting.md](troubleshooting.md)).

## Planned workflows

| Workflow | Runs on | Trigger | Steps |
|---|---|---|---|
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

Secrets never reach PR builds. Signing, Play and Firebase credentials live in Bitrise protected storage. GitHub Actions needs **no secret** today; the only one it may ever hold is the optional `GEMINI_API_KEY` for the CI agents' AI summary (see [ai-automation.md](ai-automation.md#optional-ai-summary-opt-in)), and fork PRs never receive it. Details: [security.md](security.md#secrets-and-signing).

## Reports and artifacts

Today: unit test results, lint HTML, APKs (`Android build`); detekt HTML and SARIF (artifact and GitHub code scanning), gitleaks SARIF on failure, agent reports in the job summary, release notes (`whatsnew-en-US`, `RELEASE_NOTES.md`) as an artifact. Planned: coverage, AABs, `CI_REPORT.md`, `AI_REVIEW.json`, `CI_FAILURE_REPORT.md`. Builds and test results are recorded in [project-management](project-management/README.md).

## Branch protection (Phase 8, CL-18x)

Required checks: `build` (`Android build`), the four `Quality` jobs and `Commit and branch conventions` (`pr-checks`). Applying the rulesets in GitHub settings is the owner's step. Approval count 0, because Claude works through the owner's account and cannot approve its own PRs (ADR-015). Details in [branching-strategy.md](branching-strategy.md#protection-rules).

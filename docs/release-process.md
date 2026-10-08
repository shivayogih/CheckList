# Release process

From `develop` to users on Google Play. **Status: Planned (Phases 9 and 12).** Versioning is already implemented (Phase 1). Background: [ci-cd.md](ci-cd.md), [branching-strategy.md](branching-strategy.md), [phase0-architecture.md](phase0-architecture.md) section 17.

## Versioning (implemented)

[`version.properties`](../version.properties) holds `VERSION_MAJOR`, `VERSION_MINOR`, `VERSION_PATCH` and `VERSION_BUILD`. `app/build.gradle.kts` computes:

```text
versionName = MAJOR.MINOR.PATCH            (+ "-dev" / "-staging" for those flavors)
versionCode = MAJOR*10000 + MINOR*1000 + PATCH*100 + BUILD
```

- `BUILD` (0-99) counts uploads within one version, for example fixes during the closed test: 1.0.0 is 10000, then 10001, 10002.
- A new PATCH or MINOR resets `BUILD` to 0: 1.0.1 is 10100, 1.1.0 is 11000.
- MINOR and PATCH must stay 0-9. If MINOR would reach 10, switch to a wider formula that still produces larger codes (write an ADR first).
- Play rejects a code that is not higher than every code uploaded before, on any track.

## Steps

### 1. Cut the release branch

1. On `develop`, all issues for the release are merged and `pr-checks` is green.
2. Create `release/x.y.z` from `develop`. Set `version.properties` to x.y.z with `VERSION_BUILD=0` in a `CL-xxx Prepare release x.y.z` commit.
3. Add a row to [releases.csv](project-management/releases.csv) with status `IN_PROGRESS`.

### 2. Staging and QA

1. The push to `release/*` triggers Bitrise `staging`: tests, `bundleStagingRelease` to Firebase App Distribution, and the signed production-ID AAB to Play **closed testing**.
2. Issues move to `READY_FOR_QA`, then `QA_IN_PROGRESS`. Run the manual checks in [testing.md](testing.md#manual-checks-per-release-phase-4-onward).
3. Each fix is a PR into `release/x.y.z` and bumps `VERSION_BUILD` by one. Record each upload in [builds.csv](project-management/builds.csv).
4. When QA passes, issues move to `READY_FOR_RELEASE`.

### 3. First release only: the closed-testing rule

Before the first production release, Play requires **at least 12 testers opted in to the closed test for 14 consecutive days** (new personal developer account). Firebase testers do not count. Then apply for production access in Play Console. Recruiting testers is the owner's job; start during Phase 9.

### 4. Production (needs the owner's go)

1. Claude opens the PR `release/x.y.z` → `main` and waits for an explicit "go".
2. After the go: merge with a merge commit, then merge `release/x.y.z` back into `develop`.
3. Create the annotated tag `vx.y.z` on `main` and push it.
4. Bitrise `production` verifies the tag is on `main`, equals `versionName`, and the code is higher than the last release; runs tests, lint and security checks; builds, signs and uploads `bundleProductionRelease` to the production track as a **draft** with release notes.
5. The owner reviews the draft in Play Console and presses release (staged rollout recommended). This draft is the manual approval.
6. Update `releases.csv` (`RELEASED`, approved by, date) and move the issues to `RELEASED`, then `DONE`.

## Hotfix

1. Branch `hotfix/CL-<id>-<slug>` from `main`; bump PATCH (or `BUILD` if the version has not reached production).
2. PR into `main` with the owner's go; build passes through `staging` checks; tag and release as above.
3. Merge the hotfix back into `develop` as well.

## Rollback

Play cannot install a lower version code over a higher one. A bad release is fixed forward: halt the staged rollout in Play Console, ship a hotfix with a higher code. That is why production is always a draft first and why staged rollouts are recommended.

## Release notes

Written for users, in plain words, in English plus the six other languages where Play supports them. From Phase 11 the ADK `release_notes_agent` drafts them from commits, PRs and `issues.csv`; a person always edits the result.

# Branching strategy

Git flow with one branch per issue. Design reasoning: [phase0-architecture.md](phase0-architecture.md) sections 14 and 15.

**Status:** branches, naming and commit format are in use since Phase 1. The CI check of commit subjects, PR titles and branch names is Implemented (`pr-checks`, Phase 8, CL-185). Protection rules (applied by the owner in GitHub settings) and the local `commit-msg` hook are Planned.

```text
main  ◄── release/x.y.z ◄── develop ◄── feature/CL-123-short-name
  ▲
  └── hotfix/CL-456-short-name   (from main, merged into main AND develop)
```

## Branches

| Branch | Purpose | Created from | Merges into | Merge style | Environment |
|---|---|---|---|---|---|
| `feature/CL-<id>-<slug>` | One issue | `develop` | `develop` via PR | Squash | PR checks only |
| `fix/CL-<id>-<slug>` | A bug found during development (not in production) | `develop` | `develop` via PR | Squash | PR checks only |
| `develop` (default branch) | Integration | | `release/*` (branch cut) | | DEV |
| `release/x.y.z` | Stabilization; fixes only | `develop` | `main` and back into `develop` | Merge commit | STAGING |
| `main` | What is in production | | | | PRODUCTION (via tag) |
| `hotfix/CL-<id>-<slug>` | Emergency fix | `main` | `main` and `develop` | Merge commit | STAGING, then PRODUCTION |

Squash merges keep `develop` at one commit per issue; merge commits keep release boundaries visible.

## Naming

- Branch slug: lowercase, hyphenated, short: `feature/CL-121-create-checklist`.
- Use the type prefix that fits: `feature/` covers features, tech debt, docs and CI work alike; `fix/` is for bugs found before release; `hotfix/` is only for production emergencies.
- Allowed patterns, checked by `pr-checks` on every PR: `feature/CL-<id>-<slug>`, `fix/CL-<id>-<slug>`, `hotfix/CL-<id>-<slug>`, `release/X.Y.Z`. Dependabot's `dependabot/...` branches are exempt.

## Commits

```text
CL-121 Add checklist creation flow

Optional body: why, not what. Wrap at 72 characters.
```

- Subject: issue ID, space, imperative verb with a capital letter, at most 72 characters. Pattern: `^CL-\d+ [A-Z]`.
- One logical change per commit. Several commits per PR are fine: the PR is squashed into one.
- Enforcement: `pr-checks` (implemented, CL-185, `tools/checks/conventions.py`) checks every commit subject in the PR and the PR title against `^CL-\d+ [A-Z]`. Merge commits and `dependabot[bot]` commits are exempt. Only the subject line is checked, so bodies and trailers (`Co-Authored-By:`, `Claude-Session:`) are free. Subjects over 72 characters get a warning, not a failure. A local `commit-msg` hook installed by a Gradle task is still Planned.

## Pull requests

- Title in the commit format; it becomes the squash commit subject.
- Fill in the [PR template](../.github/pull_request_template.md), including the Definition of Done checklist.
- Update the issue's row in [issues.csv](project-management/issues.csv) in the same PR.
- Keep PRs to one issue. Split large work into several issues.

## Protection rules

Claude works through the owner's GitHub account, so GitHub sees Claude's PRs as the owner's and self-approval is impossible. The rules therefore rely on **required CI checks instead of approval counts** (ADR-015).

| Branch | Rule | Who merges |
|---|---|---|
| `develop` | PR required, `pr-checks` green, no force push, 0 approvals | Claude, after CI is green |
| `release/*` | PR required for fixes, required checks green | Claude |
| `main` | PR required, required checks green, no force push or deletion, 0 approvals, owner may bypass | Claude prepares the PR; merges only after the owner says "go" for that release |
| Tags `v*` | Tag ruleset | Created after the `main` merge, with the owner's go |
| Play production release | Draft in Play Console | The owner presses release (or tells Claude explicitly) |

The owner's go is kept for `main` and Play because those are the two steps that reach real users and cannot be undone by a revert. Everything before them is reversible.

## Tags

Annotated `vX.Y.Z` on `main` only, matching `versionName`. The production workflow verifies the tagged commit is reachable from `origin/main`. See [release-process.md](release-process.md).

## Typical feature flow

```bash
git switch develop && git pull
git switch -c feature/CL-121-create-checklist
# work, commit "CL-121 ..." in small steps
./gradlew :domain:test :app:testDevDebugUnitTest :app:lintDevDebug
git push -u origin feature/CL-121-create-checklist
# open a PR to develop, fill in the template, wait for green checks, squash merge
```

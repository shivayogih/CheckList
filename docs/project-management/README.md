# Project management

CheckList simulates a Jira-style tracker with CSV files in this folder, so issue, build, test and release history lives in Git next to the code. Design reasoning: [phase0-architecture.md](../phase0-architecture.md) section 21.

**Status:** the CSV files are maintained by hand (Implemented). The `:tools:issuetracker` CLI that validates transitions and applies CI reports is Planned (Phase 8 or 9).

## Files

| File | One row per | Columns |
|---|---|---|
| [issues.csv](issues.csv) | Issue | Issue ID, Title, Description, Type, Priority, Status, Assignee, Branch, Pull Request, Build Number, Environment, Test Status, AI Review Status, Created Date, Updated Date, Release |
| [builds.csv](builds.csv) | CI build | Build Number, Workflow, Branch, Commit, Issue IDs, Status, Duration, Artifact, Started At |
| [test-results.csv](test-results.csv) | Test suite in a build | Build Number, Suite, Total, Passed, Failed, Skipped, Coverage, Report Link |
| [releases.csv](releases.csv) | Uploaded version code | Version Name, Version Code, Tag, Release Branch, Issues, Build Number, Track, Status, Approved By, Released At |
| `issue-comments.csv` (Planned) | Comment | Keeps `issues.csv` at one row per issue |

`builds.csv`, `test-results.csv` and `releases.csv` hold only their headers until Bitrise builds start (Phase 9). `releases.csv` is also the source of the `BUILD` part of the version code: next upload = last code + 1 (see [release-process.md](../release-process.md)).

## Editing rules

- Standard CSV (RFC 4180), UTF-8, `\n` line endings, header row first. Quote any field that contains a comma, a double quote or a line break; double any quote inside a quoted field.
- Never reorder or rename columns. Every row has exactly as many fields as the header.
- Never delete a row. Closed or abandoned issues keep their row with a final status.
- Dates are `YYYY-MM-DD`; timestamps (`Started At`, `Released At`) are ISO-8601 UTC (`2026-10-08T04:00:00Z`). Durations are whole seconds.
- Update `Updated Date` whenever you change an issue row. Update the row in the same PR as the work.
- Build numbers: `GA-<run number>` for GitHub Actions, `BR-<build number>` for Bitrise.

## Issue IDs

`CL-<number>`, never reused. Each phase reserves a range (design section 23):

| Phase | Range | Phase | Range |
|---|---|---|---|
| 0 | CL-100 | 7 | CL-170 to CL-176 |
| 1 | CL-101 to CL-106 (plus CL-107) | 8 | CL-180 to CL-186 |
| 2 | CL-110 to CL-119 | 9 | CL-190 to CL-197 |
| 3 | CL-120 to CL-134 | 10 | CL-200 to CL-212 |
| 4 | CL-140 to CL-147 | 11 | CL-220 to CL-224 |
| 5 | CL-150 to CL-154 | 12 | CL-230 to CL-232 |
| 6 | CL-160 to CL-166 | | |

Future phases are tracked as one `Epic` row with status `BACKLOG`, using the first free ID of the range. When the phase starts, its issues get their own rows and the epic row moves along with the phase (it reaches `DONE` when every child does).

## Field values

| Field | Allowed values |
|---|---|
| Type | `Feature`, `Bug`, `Technical Debt`, `Security`, `CI/CD`, `Documentation`, `Task`, `Epic` |
| Priority | `Low`, `Medium`, `High`, `Critical` |
| Environment | `DEV`, `STAGING`, `PRODUCTION` (blank until built) |
| Test Status | `PENDING`, `PASSED`, `FAILED`, `NOT_APPLICABLE` |
| AI Review Status | `PENDING`, `PASSED`, `CHANGES_SUGGESTED`, `NOT_APPLICABLE` (AI review starts in Phase 11) |
| Build Status (`builds.csv`) | `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED` |
| Track (`releases.csv`) | `internal`, `closed`, `production` |
| Release Status (`releases.csv`) | `IN_PROGRESS`, `UPLOADED`, `IN_REVIEW`, `RELEASED`, `HALTED` |

## Issue statuses

| Status | Meaning |
|---|---|
| `BACKLOG` | Known, not scheduled |
| `TODO` | Scheduled for the current phase |
| `IN_PROGRESS` | A branch exists and work has started |
| `CODE_REVIEW` | PR open |
| `CI_RUNNING` | Checks running on the PR or merge |
| `CI_FAILED` | Checks failed; fix and push again |
| `READY_FOR_QA` | Merged and built in the right environment (DEV for features, STAGING for release fixes) |
| `QA_IN_PROGRESS` | Being tested on a device |
| `READY_FOR_RELEASE` | QA passed; waiting for the release |
| `RELEASED` | In a version released on Play |
| `DONE` | Finished, nothing more to do |

### Transitions

```text
BACKLOG → TODO → IN_PROGRESS → CODE_REVIEW → CI_RUNNING ─┬─► READY_FOR_QA → QA_IN_PROGRESS → READY_FOR_RELEASE → RELEASED → DONE
                                    ▲                    │
                                    └──── CI_FAILED ◄────┘
```

| From | Allowed next |
|---|---|
| `BACKLOG` | `TODO` |
| `TODO` | `IN_PROGRESS`, `BACKLOG` |
| `IN_PROGRESS` | `CODE_REVIEW`, `TODO` |
| `CODE_REVIEW` | `CI_RUNNING`, `IN_PROGRESS` (changes requested) |
| `CI_RUNNING` | `READY_FOR_QA`, `CI_FAILED` |
| `CI_FAILED` | `CODE_REVIEW` (fix pushed), `IN_PROGRESS` (larger rework) |
| `READY_FOR_QA` | `QA_IN_PROGRESS` |
| `QA_IN_PROGRESS` | `READY_FOR_RELEASE`, `IN_PROGRESS` (QA found a bug) |
| `READY_FOR_RELEASE` | `RELEASED` |
| `RELEASED` | `DONE` |

Shortcut: issues that never ship in the app (documentation, process, CI) may go from `CI_RUNNING` straight to `DONE` once merged, with Test Status `NOT_APPLICABLE` where no tests apply. Phase 0 (CL-100) used this.

## How CI updates these files (Planned)

CI never commits to protected branches. From Phase 8-9, CI runs the issue tracker in report mode: it computes the build row, test rows and a status suggestion, and publishes them as an artifact and a PR comment. The developer applies them in the PR:

```bash
./gradlew :tools:issuetracker:run --args="apply build-123.json"
```

This mirrors how CI webhooks update Jira, and the `IssueTracker` interface means a `JiraIssueTracker` can replace the CSV one later without changing callers.

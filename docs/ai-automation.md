# AI and automation

CheckList uses AI in two separate places: optional features **inside the app**, and agents **inside CI** that help the developer. Neither is required for the app to work. **Status: in-app AI in progress (Phase 10, CL-200 to CL-213): the offline part is implemented in `:ai` (contract, tool validation, confirmation gate, executor, offline parser in 7 languages); the Settings switches, the command field and the review sheet are implemented in `:app` (CL-240); online AI is Planned (CL-211). CI agents: the translation, issue-sync and release-notes agents are implemented as rule-based Google ADK workflows in GitHub Actions (Phase 11, CL-220 to CL-225); the model-based review and failure agents are Planned.** Design reasoning: [phase0-architecture.md](phase0-architecture.md) sections 11 to 13.

## Rules

1. **The app is complete without AI.** AI is off by default and switched on in Settings after a short privacy explanation. Offline or disabled, the app shows one friendly message and a button to do the task manually.
2. **AI proposes, the app disposes.** Every AI result becomes a typed `ActionPlan`, validated and executed by the same use cases the UI uses (ADR-008).
3. **AI never touches the database.** `:ai` depends on `:domain` only, so Room is not even on its classpath (ADR-001).
4. **No credentials in the APK, no profile data in prompts.**
5. **Provider-independent.** Business code depends on the `AIService` interface, never on a vendor SDK.
6. **No backend.** There is no server of ours (ADR-009).

## In-app AI (Phase 10)

**Status:** the offline part is implemented in `:ai` (CL-201 to CL-209) and has a UI (CL-240, see "In the app" below). The online service is CL-211, Undo is CL-212. AI is off until the user turns it on in Settings; while it is off every call answers `Unavailable(DISABLED)`.

### In the app (CL-240)

- **Settings → "AI assistant (offline)"**, off by default, with a one-line privacy note (it runs on the phone; nothing is sent). A second switch, **"Let AI add items without asking"**, is the `autoExecuteSimple` setting that `PlanGate.autoApprove` depends on; it is off by default, can only be turned on while the assistant is on, and turns off with it. Both live in a Preferences DataStore (`settings/AiPreferences.kt`, file `ai_settings`) that is bound as the AI layer's settings source, so the switch takes effect on the next call.
- **Checklist detail → command field**, shown only while the assistant is on. The user types a command in any app language ("2 kg rice and 1 litre milk", "दो किलो चावल और एक लीटर दूध"); `AiCommandViewModel` builds the snapshot, calls `AiAssistant.interpret` and opens the **review sheet**: one checkbox row per step with the item, quantity, unit and target section, the parts that were not understood, and Confirm / Cancel. Only ticked steps go to `AiAssistant.confirm` (`PlanGate`) and `execute`; Cancel or closing the sheet drops the plan, so it can never run later. A one-step plan skips the sheet only when "add without asking" is on (`autoApprove`).
- **Messages** (translated, under the field, announced by TalkBack): what ran ("Done: Rice, Milk."), what was already on the list, what did not run (a failed step and the steps after it); and, instead of a plan, "the assistant is off", "took too long" (timeout), "not understood" (also for a plan with no steps), "can't do that right now" (offline, quota, not supported), or "type a command". Each one points to adding items by hand.
- A confirmed plan finishes in the application scope even if the screen closes, like the deferred item delete.

### Implementations

| Implementation | Network | Role | Status |
|---|---|---|---|
| `MockAIService` | None | Tests and demos. Deterministic canned plans (or scripted answers per utterance) that pass validation against the real catalog; records every request. | Implemented |
| `OfflineCommandParser` | None | Rule-based commands in all 7 languages, native script and common transliteration: "2 kg rice, 1 litre milk", "ಎರಡು ಕೆಜಿ ಅಕ್ಕಿ", "दो किलो चावल और एक लीटर दूध", "rice done", "remove onion", "make rice 3 kg", "new list Diwali shopping". Not an LLM. `generateChecklist` and the suggestion calls answer `Unavailable(NOT_SUPPORTED)`; `summarize` is computed locally. | Implemented |
| `CompositeAIService` | Optional | What Hilt binds as `AIService`. Off unless the user enabled AI. Tries the offline parser first; asks the online service only when it is installed, the user enabled online AI and the offline answer was not a success. Online calls time out after 15 s and every failure becomes a friendly `AiResult`. | Implemented |
| `GeminiAIService` (opt-in) | Internet | Firebase AI Logic → Gemini Developer API on the free Spark plan, protected by App Check (Play Integrity). Generation, suggestions, free-form commands. | Planned (CL-211) |
| `OnDeviceGeminiNanoService` | None | Experiment behind a developer flag only (ML Kit Prompt API is alpha and limited to a few devices). | Not planned yet |

### Flow and guarantees

```text
AiAssistant.snapshot(checklistId)       ContextSnapshot: refs s1, i3 → real IDs stay on the device
 → AIService.interpret / generate        AiResult<ActionPlan>  (raw ToolCalls, nothing executed)
 → PlanValidator                         shape + catalog + checklist checks → ValidatedPlan
 → ConfirmationPolicy                    NONE | CONFIRM | REVIEW → ReviewedPlan
 → PlanGate.confirm(reviewed, unticked)  the user's yes → ConfirmedPlan (single use)
   or PlanGate.autoApprove(reviewed)     only when the requirement is NONE
 → ActionPlanExecutor.execute(confirmed) domain use cases only → ExecutionReport
```

- **Nothing runs without a yes.** `ActionPlanExecutor` accepts only a `ConfirmedPlan`, whose constructor is internal to `:ai`; the only ways to get one are `PlanGate.confirm` (the user confirmed on screen) and `PlanGate.autoApprove` (returns `null` unless the policy said `NONE`). A confirmed plan can run once.
- **AI never writes.** The executor calls `CreateChecklistUseCase`, `AddCategoriesToChecklistUseCase`, `AddMasterItemsToSectionUseCase`, `AddCustomItemUseCase`, `UpdateChecklistItemUseCase`, `SetItemCompletedUseCase`, `RemoveSectionUseCase` and `DeleteChecklistItemUseCase`: the same business rules as the UI. `:ai` has no path to Room.
- **Validation before review.** Unknown tools, read tools used as plan steps, missing, extra or wrongly typed arguments, unknown refs, unit codes not in `BuiltInUnits`, quantities that are not positive or exceed `Quantity` limits, unknown categories, a category already in the list, and `createChecklist` not first are rejected with a typed `ToolProblem`. A partly rejected plan is still shown, always on the full review screen, with the rejected parts listed.
- **Partial execution:** the executor stops at the first failed step and reports what ran. It does not wrap the plan in one database transaction because `:domain` has no transaction use case; Undo is CL-212.

### Confirmation policy

| Plan | Requirement |
|---|---|
| Read tools | Answered on the device by `ReadToolRunner`; never a plan step |
| One create or modify step (`addChecklistItem`, `addCategory`, `updateItemQuantity`, `updateItemUnit`, `completeItem`, `uncompleteItem`) | `CONFIRM`; `NONE` only if the user enabled "Let AI add items without asking" |
| An item that also creates its category section | `REVIEW` |
| `createChecklist`, any plan with more than one step, anything rejected or not understood | `REVIEW` |
| `removeCategory`, `deleteItem` | `REVIEW`, always |

### Tools

The 13 tools from design section 13 are the `AiTool` enum (`tools/ToolCatalog.kt`): name, risk class and typed parameters. `ToolCatalog.declarations(allowedUnits)` turns them into vendor-neutral function declarations (unit parameters are limited to the allowed codes). `ToolCatalogTest` checks that every declared write tool is understood by the validator and every read tool is answered by `ReadToolRunner`, so a tool cannot be declared to a model without being handled.

| Tool | Risk |
|---|---|
| `searchMasterItems`, `suggestCategories`, `suggestItems`, `summarizeChecklist` | Read |
| `createChecklist`, `addCategory`, `addChecklistItem` | Create |
| `updateItemQuantity`, `updateItemUnit`, `completeItem`, `uncompleteItem` | Modify |
| `removeCategory`, `deleteItem` | Destructive |

### How the offline parser works

1. **Normalize** (`TextNormalizer`): NFKC, lower case, native digits of every Indian script to ASCII, zero-width joiners removed, decimal commas and `½` fractions read, `2kg` split only when the glued part is a real unit word.
2. **Intent** (`CommandGrammar`): each language pack lists prefix and suffix markers per intent ("remove", "ತೆಗೆ", "हटाओ", "done", "ಆಯ್ತು", ...). The longest matching marker wins; with no marker the command means "add".
3. **Items:** the rest is split at separators and "and" words or suffixes (`और`, `ಮತ್ತು`, Tamil `-உம்`, Malayalam `-ഉം`). Each part becomes quantity (digits or number words, including "half", "डेढ़", "ಒಂದೂವರೆ"), unit (synonyms mapped to `BuiltInUnits` codes) and a name, in either order ("2 kg rice" or "rice 2 kg").
4. **Lookup:** the name is searched with `SearchMasterItemsUseCase` in the user's language and English, trying the name as typed and then with common case endings removed (Kannada `-ಅನ್ನು`, Hindi `को`, Tamil `-ஐ`, Malayalam chillu repair, ...). A hit is accepted only if it matches the whole word or differs by a short ending, so "egg" never becomes "eggplant". No hit means a custom item with the typed name.
5. **Plan:** add-intents become `addChecklistItem` calls (master key or custom name); complete, uncomplete, remove and update look the item up in the open checklist and use its ref. Anything that cannot be placed becomes an `UnresolvedFragment` the review screen shows.

**Known limits:** one intent per sentence; transliterated words with case endings ("akkiyannu") are matched only if the pack lists the ending; compound number words ("two hundred and fifty") are not read, so larger amounts need digits; no spelling correction beyond the catalog's search. Free-form requests ("plan a Goa trip") need online AI.

### Language packs

Vocabulary lives in data, not code: `ai/src/main/resources/com/dataloom/checklist/ai/lang/<tag>.json`, one file per language (`en`, `kn`, `hi`, `ta`, `te`, `mr`, `ml`), with number words, unit synonyms, intent markers, separators, "and" suffixes, stripable endings, stem repairs and filler words. English is always merged in because people mix it in. Packs are loaded for every language in `SupportedLanguages` (`:domain`), so a new app language needs only its JSON file plus a table in `OfflineCommandParserTest`; `LanguagePacksTest` fails if a pack is missing, a unit key is not a `BuiltInUnits` code, a number word is not a valid quantity, or a language cannot express every intent. The six Indian-language packs need review by native speakers (CL-213).

### What is sent (context minimization)

`ChecklistContext` holds only: checklist title, section category keys and names, item canonical keys or names, quantities, units and completion, the locale and the allowed unit codes. Database IDs are replaced by per-request refs (`s1`, `i3`) mapped back on the device by `ContextSnapshot`; notes and user-item keys (which embed IDs) are never included. `ContextSnapshotTest` asserts this. Prompts and responses must not be logged in release builds. **Photos are never part of the context** (CL-215): `ItemContext` has no photo field, `ContextSnapshot.of` ignores `ChecklistItem.photos`, and `ContextPhotosTest` fails if a photo file name, id or caption shows up in the context, if a context type gains a photo property, or if any source file of `:ai` mentions photos.

### Wiring

`AiModule` (Hilt, installed in `SingletonComponent`) binds `AIService` to `CompositeAIService`. Two optional slots are declared with `@BindsOptionalOf`:

- `@AiSettingsStore AiSettingsSource`: the settings store. `:app` binds `DataStoreAiPreferences` here (`di/SettingsModule.kt`, CL-240). Missing → AI off. The slot is qualified because `AiModule` itself provides the unqualified `AiSettingsSource` that the rest of `:ai` injects (the store, or `DISABLED`); an unqualified binding in the app would be a duplicate binding.
- `@OnlineAiService AIService`: bind the online implementation (CL-211). Missing → offline only.

Callers use `AiAssistant`: `snapshot`, `interpret` or `generate`, `review`, then `confirm` (or `autoApprove`) and `execute`.

### Plugging in online AI (CL-211)

1. Create a Firebase project on the free Spark plan with no billing account; enable Firebase AI Logic with the Gemini Developer API, and App Check with Play Integrity (debug provider for `dev`). Only `google-services.json` (an identifier, not a secret) goes in the app; keep it out of Git for `prod`.
2. Add the Firebase BoM, `firebase-ai` and `firebase-appcheck-playintegrity` to `gradle/libs.versions.toml`, verifying the versions against AGP 9.4, Kotlin 2.4 and minSdk 26 at that time. Put them in a new `GeminiAIService` in `:ai` (or a separate `:ai-online` module to keep the SDK out of builds that do not need it).
3. Convert `ToolCatalog.declarations(context.allowedUnits)` to Firebase `FunctionDeclaration`s and send the minimized `ChecklistContext` with a system instruction to use only canonical keys, unit codes and refs.
4. Run the function-calling loop on the device: answer read calls with `ReadToolRunner` and send the results back; collect write calls as `ToolCall`s into `ActionPlan(source = ONLINE_MODEL)`. Never execute inside the loop: the plan goes through the same validator, policy and gate.
5. Map errors: no network → `Unavailable(OFFLINE)`, quota → `Unavailable(QUOTA)`; `CompositeAIService` already applies the timeout.
6. Bind it: `@Binds @OnlineAiService abstract fun online(impl: GeminiAIService): AIService`.
7. Add an online opt-in switch with the privacy explanation below to Settings (next to the CL-240 switches) before `onlineEnabled` can be turned on; `DataStoreAiPreferences` always reports it off today. Add recorded-response tests: real model output replayed through the validator.

### Tradeoffs to know

- **Contract location:** `AIService` and its models live in `:ai`, not `:domain`. The contract talks about tool calls, refs and plans, which only the AI layer needs; keeping it out of `:domain` meant no change to the shared domain contract. `:app` depends on `:ai`, so it can still inject the interface.
- **Rules versus a model:** the offline parser is predictable, free, private and instant, but understands only the patterns in its packs. It covers quick add and simple edits; everything else waits for online AI.
- **Key protection:** the app ships only the Firebase config. App Check attests a genuine app and device. A determined attacker on a rooted device could still use up the free quota; with no billing account linked, the worst case is that online AI is unavailable, never a bill.
- **Privacy:** on the Gemini free tier, Google may use request content to improve its products. Hence opt-in, plain-language disclosure, and minimal context.

### Voice (future)

`SpeechRecognizer` produces an `Utterance(source = VOICE)`; everything downstream is unchanged.

## CI agents with Google ADK (Phase 11)

**Status: three rule-based agents Implemented (CL-221 to CL-225); `review_agent` and `failure_agent` Planned (they need a model, so they wait for the opt-in key).**

The Android app does not use ADK: an agent loop needs a trusted place to hold a model key, and the app has neither a server nor a safe place for a key. ADK lives **only in CI**: short Python jobs in GitHub Actions that start, do one job and exit. Nothing is hosted, nothing is billed. The code is in `tools/agents/` (the Phase 0 sketch said `ci/agents/`; `tools/` is where the repository's other Python tooling lives).

### What is implemented

| Agent | Workflow and trigger | Checks / output | Fails the job on |
|---|---|---|---|
| Translation consistency (`translations`) | `Quality`, every push | Compares the six `values-<tag>/strings.xml` with English: missing and extra keys, placeholder mismatches (`%1$s` vs `%1$d`, dropped `%2$d`), English left untranslated, Latin-only text where Kannada/Devanagari/Tamil/Telugu/Malayalam is expected, letters from another Indian script. Brand terms that stay Latin (`CheckList`, `Google Play`, `PDF`...) are in `tools/agents/config/translation_allowlist.json`. | Missing/extra keys, placeholder mismatch, missing locale, invalid XML |
| Issue tracker sync (`issue-sync`) | `Quality`, every push | Validates `docs/project-management/issues.csv`: header, field count, `CL-<n>` IDs unique, Type/Priority/Status/Environment/Test/AI-review values from the [project-management README](project-management/README.md), dates, `IN_PROGRESS` rows have a branch that exists on the remote. Flags **stale** rows: still `IN_PROGRESS`/`CODE_REVIEW`/`CI_*` although the branch is merged into `develop`/`main` (git ancestry) or GitHub reports its PR as merged (catches squash merges; uses the job's read-only `GITHUB_TOKEN`). | Structural or value errors; branch problems and stale rows are warnings |
| Release notes (`release-notes`) | `Release notes` on a `v*` tag or on demand; a preview runs in `Quality` | From the `CL-` commits since the previous `v*` tag: Google Play "What's new" text (`whatsnew-en-US`, at most 15 notes and 500 characters, internal work, URLs, e-mails and @mentions removed, fallback "Bug fixes and improvements."), plus a developer changelog grouped into New / Fixes / Internal using each issue's type and title from `issues.csv`. Same rules as Bitrise's `ci/scripts/release-notes.sh` (Phase 9), re-implemented so neither depends on the other. | Never (no `CL-` commits or unknown issue IDs are warnings) |

Every report is markdown in the job summary; errors fail the job, warnings never do.

### How ADK is used, and why it adds something

Verified 2026-10-08 against PyPI and adk.dev: `google-adk` **2.10.0** (Python >= 3.10). 2.11.0 was published 2026-10-02; it is under two weeks old, so the previous release is pinned (Dependabot proposes updates). ADK 2.x adds **graph workflows**: a `Workflow` whose nodes can be plain Python functions or LLM `Agent`s, joined by edges; each node's return value is the next node's input, and ADK's `InMemoryRunner` runs the graph with a session and an event per node.

Each agent is a `Workflow` of three deterministic function nodes:

```text
START → collect (read files / git) → analyse (rules → report dict) → render (markdown Event)
```

- **No model, no key, no network** for the checks themselves. Runs are deterministic and testable; `InMemoryRunner` needs no Google Cloud project.
- ADK gives the three agents the same shape, runner, event trail and session; the optional model step uses the same runtime (an ADK `Agent`), so adding the planned `review_agent` later is a node, not a new framework.
- The rules live in plain modules (`translations.py`, `issue_sync.py`, `release_notes.py`), so most tests do not need ADK; `workflows.py` wraps them. Tokens are never passed through the graph: the GitHub token is read from the environment inside the node that uses it, so it never appears in ADK events or session state.

Run locally (Python 3.10+):

```bash
python -m venv .venv && .venv/bin/pip install -r tools/agents/requirements.txt
cd tools/agents
../../.venv/bin/python -m checklist_agents translations --repo ../..
../../.venv/bin/python -m checklist_agents issue-sync --repo ../..
../../.venv/bin/python -m checklist_agents release-notes --repo ../.. --out-dir /tmp/whatsnew
../../.venv/bin/python -m pytest -q tests ../checks
```

### Optional AI summary (opt-in)

The checks never need a model. If the owner wants richer reports, they can add a free-tier Gemini Developer API key as the GitHub Actions secret **`GEMINI_API_KEY`** (Settings → Secrets and variables → Actions). Then:

- the agent steps add `--enrich`, and `enrich.py` runs an ADK `Agent` (model `gemini-flash-latest`, overridable with `CHECKLIST_AGENT_MODEL`) that appends an **"AI summary (advisory)"** section: priorities and fixes for translation findings, which tracker rows to update first, friendlier Play text for release notes;
- before anything is sent, the report goes through `redact()` (Google, GitHub, AWS and Slack key patterns, private keys, JWTs, `password=`/`token=` values) and is capped at 20,000 characters. Reports contain only data that is already public in this repository (string resources, commit subjects, `issues.csv`);
- the model never decides pass or fail, and any model error is reduced to one "AI summary skipped" line;
- **with no secret, the flag is not passed and nothing is imported, sent or billed.** Fork PRs never receive secrets. On the free tier Google may use the content to improve its products, which is why this is opt-in.

### Guarantees

- Agents cannot approve, merge, push, comment or change branch protection: their jobs have `contents: read` (plus `pull-requests: read` for the merged-PR lookup).
- Agents never commit to `issues.csv`; they report, and the developer applies changes in the PR (design section 21.2).

### Still planned

| Agent | Trigger | Output |
|---|---|---|
| `review_agent` | `ai-review` workflow on PRs, only when `GEMINI_API_KEY` exists | `AI_REVIEW.json` and a PR comment. Advisory only. |
| `failure_agent` | `ci-failure` workflow when a check fails; Bitrise failures attach a trimmed log | `CI_FAILURE_REPORT.md` |

Diffs and logs will pass through the same `redact()` step (plus Bitrise's own log redaction) before any model sees them.

## Keeping the door open

A hosted agent (`RemoteAgentAIService`, for example ADK on Cloud Run behind App Check) can be added later behind the same `AIService` interface without changing `:domain` or `:data`.

# AI and automation

CheckList uses AI in two separate places: optional features **inside the app**, and agents **inside CI** that help the developer. Neither is required for the app to work. **Status: Planned (in-app AI: Phase 10, CL-200 to CL-212; CI agents: Phase 11, CL-220 to CL-224).** The `:ai` module exists but is empty. Design reasoning: [phase0-architecture.md](phase0-architecture.md) sections 11 to 13.

## Rules

1. **The app is complete without AI.** AI is off by default and switched on in Settings after a short privacy explanation. Offline or disabled, the app shows one friendly message and a button to do the task manually.
2. **AI proposes, the app disposes.** Every AI result becomes a typed `ActionPlan`, validated and executed by the same use cases the UI uses (ADR-008).
3. **AI never touches the database.** `:ai` depends on `:domain` only, so Room is not even on its classpath (ADR-001).
4. **No credentials in the APK, no profile data in prompts.**
5. **Provider-independent.** Business code depends on the `AIService` interface, never on a vendor SDK.
6. **No backend.** There is no server of ours (ADR-009).

## In-app AI (Phase 10)

### Implementations

| Implementation | Network | Role |
|---|---|---|
| `MockAIService` | None | Default in `dev` builds and in all tests. Deterministic canned plans, so the review UI can be built before any real AI. |
| `OfflineCommandParser` | None | Rule-based quick add in all 7 languages: number + unit synonym + item name matched against the local catalog and aliases. "5 ಕೆಜಿ ಅಕ್ಕಿ", "5 किलो चावल", "5 kg rice" all become `rice, 5, KG`. Not an LLM. |
| `GeminiAIService` (opt-in) | Internet | Firebase AI Logic → Gemini Developer API on the free Spark plan, protected by App Check (Play Integrity). Generation, suggestions, free-form commands and summaries through function calling. |
| `OnDeviceGeminiNanoService` | None | Experiment behind a developer flag only (ML Kit Prompt API is alpha and limited to a few devices). |

`CompositeAIService` picks the best available: the offline parser for simple quick add, Gemini if the user enabled online AI and is connected, otherwise `AiResult.Unavailable`.

### Flow

```text
AiAssistantViewModel (:app)
 → AIService                      returns AiResult<ActionPlan | Suggestions | Summary>
 → CommandMapper + CommandValidator (:ai)   resolves canonical keys and units against the catalog
 → ConfirmationPolicy (:ai)       ── needs review ──► review screen (:app) ── confirm ──┐
 │ auto-allowed                                                                        │
 ▼                                                                                     ▼
 ActionPlanExecutor (:ai) → domain use cases → repositories → Room (one transaction, one Undo)
```

### Tools the model may call

| Tool | Use case | Confirmation |
|---|---|---|
| `searchMasterItems`, `suggestCategories`, `suggestItems`, `summarizeChecklist` | Read-only | None |
| `addChecklistItem`, `addCategory`, `updateItemQuantity`, `updateItemUnit`, `completeItem`, `uncompleteItem` | Create or modify one thing | Confirm by default; auto only if the user enables "Let AI add items without asking" |
| `createChecklist` and any plan with more than one operation | Bulk | Always reviewed |
| `removeCategory`, `deleteItem` | Destructive | Always confirmed |

The tool list is defined once in `ToolCatalog` (`:ai`) and converted to Gemini function declarations; a contract test keeps the declarations and the executor in sync. The model must answer with canonical keys and unit codes; unknown units are a validation error shown to the user, never a silently created custom unit.

### What is sent (context minimization)

Only: checklist title, category canonical keys, item keys or names and quantities, the user's locale, and the allowed unit codes. Database IDs are replaced by short per-request refs (`s1`, `i3`). Never: profile fields, notes, IDs. Prompts and responses are not logged in release builds.

### Tradeoffs to know

- **Key protection:** the app ships only the Firebase config (an identifier). App Check attests genuine app and device. A determined attacker on a rooted device could still use up the free quota; with no billing account linked, the worst case is that online AI is unavailable, never a bill.
- **Privacy:** on the Gemini free tier, Google may use request content to improve its products. Hence opt-in, plain-language disclosure, and minimal context.

### Voice (future)

`SpeechRecognizer` produces an `Utterance(source = VOICE)`; everything downstream is unchanged.

## CI agents with Google ADK (Phase 11)

The Android app does not use ADK: an agent loop needs a trusted place to hold a model key, and the app has neither a server nor a safe place for a key. ADK runs where a secret can live safely: **GitHub Actions**, as short command-line jobs in `ci/agents/` (Python). Each run starts, does one job and exits; nothing is hosted.

| Agent | Trigger | Output |
|---|---|---|
| `review_agent` | `ai-review` workflow on PRs | `AI_REVIEW.json` and a PR comment. Advisory only. |
| `failure_agent` | `ci-failure` workflow when `pr-checks` fails; Bitrise failures attach a trimmed log | `CI_FAILURE_REPORT.md` |
| `release_notes_agent` | Release preparation | Draft user-facing release notes from commits, PRs and `issues.csv` |

- The Gemini key is a GitHub Actions secret on the free tier. Fork PRs never receive it.
- Diffs and logs pass through a redaction step (key and token patterns, plus Bitrise's own log redaction) before any agent sees them.
- Agents cannot approve, merge, push or change branch protection.
- Locally, the same agents can be explored with `adk web`.

## Keeping the door open

A hosted agent (`RemoteAgentAIService`, for example ADK on Cloud Run behind App Check) can be added later behind the same `AIService` interface without changing `:domain` or `:data`.

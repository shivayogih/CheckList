# :ai
Optional AI layer (Phase 10). Depends on `:domain` only and never on `:data`, so AI code cannot reach Room (ADR-001, ADR-008).

AI proposes, the app disposes: an `AIService` turns a request into an `ActionPlan` of tool calls; `PlanValidator` checks it against the catalog and the open checklist; `ConfirmationPolicy` and `PlanGate` decide what the user must confirm; only a `ConfirmedPlan` reaches `ActionPlanExecutor`, which calls domain use cases. `AiAssistant` is the entry point for callers.

| Package | Contents |
|---|---|
| `model` | `AiResult`, `Utterance`, `ActionPlan`/`ToolCall`, `ChecklistContext` and `ContextSnapshot` (refs instead of IDs) |
| `service` | `AIService`, `MockAIService`, `CompositeAIService` |
| `parser` | `OfflineCommandParser`, `CommandGrammar`, `TextNormalizer`, language packs in `src/main/resources/.../ai/lang/*.json` |
| `tools` | `AiTool` registry, function declarations, `ReadToolRunner` |
| `mapper` | `PlanValidator`, `CatalogLookup`, planned operations and typed problems |
| `policy` | `AiSettings`, `ConfirmationPolicy`, `ReviewedPlan`, `ConfirmedPlan`, `PlanGate` |
| `executor` | `ActionPlanExecutor` |
| `di` | `AiModule` with optional slots for settings and an online service |

Tests run on the JVM over the real seed catalog (`data/src/main/assets/seed`): `./gradlew :ai:testDebugUnitTest`. Full guide, including how to plug in online AI: [docs/ai-automation.md](../docs/ai-automation.md).

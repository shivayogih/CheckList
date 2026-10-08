# CheckList: Phase 0 Product and Architecture

Status: **Revision 2, draft for approval** · Phase 0 of 12 · 2026-10-08
Author: Claude (acting as architect) for Shivayogi Hiremath
Scope: design only. No code, no Gradle files, no CI config are created in this phase.

### Revision 2: your answers (2026-10-08) and what they changed
| # | Your answer | Effect on the design |
|---|---|---|
| 1 | Public repository `shivayogih/CheckList` | GitHub Actions is free and unlimited on public repos; deployment approvals (GitHub Environments) are available. Sections 15, 16. |
| 2 | App ID `com.dataloom.checklist` | `.dev` / `.staging` suffixes. Permanent once published on Play. Section 17. |
| 3 | minSdk 26 | Confirmed. |
| 4 | Staging testers via Firebase App Distribution | Confirmed (free). Play's closed-testing rule added (section 17.3). |
| 5 | Claude works with owner-level access end to end; you may bypass rules on `main` | Approval count set to 0 with required CI checks; Claude merges to `develop` itself; merges to `main` and Play releases still need your explicit go. Section 15.2. |
| 6 | Encrypt the profile only, more if needed | Kept profile-only, with a written trigger for revisiting (section 19.1). |
| 7 | Firebase OK | Used for App Distribution, App Check and AI Logic, all on the free Spark plan. |
| 8 | No backend; offline-only app | **No server of ours.** ADK moved out of the app into CI tooling. App AI = offline rule parser + optional Gemini via Firebase AI Logic (free tier). Sections 11, 12. |
| 9 | Zero spend except the Play fee; new to Bitrise | Bitrise free Hobby plan for dev/staging/production; GitHub Actions for high-frequency PR checks and AI CI. Section 16. |
| — | Version code scheme: Option B confirmed | 1.0.0 = 10000–10099, room for closed-test fixes (section 17.4). |

---

## 0. Workspace inspection

| Checked | Result |
|---|---|
| Cloud working directory `/home/claude` | Empty apart from tool config. Not a git repository. |
| Shared project folder `/mnt/project-files/` | Empty before this document. |
| Repositories attached to this project | None. |
| Folders on your device | None shared with the project. |

**Conclusion:** this is a greenfield project. There is no existing architecture to preserve, so every decision below is new. Phase 1 needs a GitHub repository (see Open Questions, section 25.3).

### Documentation verified for this phase (2026-10-08)

| Topic | What I verified | Source |
|---|---|---|
| Google ADK languages | ADK is available in Python, TypeScript, Go, Java and Kotlin (`google-adk`, `@google/adk`, `google.golang.org/adk/v2`, `com.google.adk:google-adk`, `com.google.adk:google-adk-kotlin-core`). The site does not describe an Android client SDK. | [adk.dev](https://adk.dev/) |
| ADK deployment | Managed target is now called **Agent Runtime** (on "Agent Platform"); deploy with `adk deploy agent_engine`. Cloud Run and GKE are also supported. | [adk.dev: Deploy to Agent Runtime](https://adk.dev/deploy/agent-runtime/deploy/) |
| ADK API server | `adk api_server` exposes `GET /list-apps`, `POST/GET/PATCH/DELETE /apps/{app}/users/{user}/sessions/{session}`, `POST /run`, `POST /run_sse`. Docs present it as a local testing tool and say nothing about auth. | [adk.dev: Use the API Server](https://adk.dev/runtime/api-server/) |
| ADK on Cloud Run | Python agent deployed with `gcloud run deploy --source .`, model via Vertex/Agent Platform (`GOOGLE_GENAI_USE_VERTEXAI=TRUE`). | [Cloud Run: Build and deploy an ADK agent](https://docs.cloud.google.com/run/docs/ai/build-and-deploy-ai-agents/adk) |
| Gemini from Android | Google recommends **Firebase AI Logic** client SDK, protected by **Firebase App Check** (Play Integrity); App Check enforcement is automatic in guided setup from July 2026. Model names should come from Remote Config. | [Android Developers: Gemini AI models](https://developer.android.com/ai/gemini) |
| Bitrise YAML | Top-level keys `format_version`, `app.envs`, `workflows`, `pipelines`, `step_bundles`, `include`, `tools`, `meta`. `trigger_map` is **legacy**; use target-based `triggers:` (`push.branch`, `pull_request.source_branch/target_branch`, `tag.name`, with `pattern`/`regex`). Pipelines order workflows with `depends_on`. `before_run`/`after_run` are legacy; use step bundles. | [Bitrise configuration YAML reference](https://docs.bitrise.io/en/bitrise-ci/references/configuration-yaml-reference) |
| Bitrise Gradle caching | Key-based caching with **Restore Gradle cache** (before dependency steps) and **Save Gradle cache** (end of workflow); keys are generated automatically. | [Bitrise: Android dependencies](https://docs.bitrise.io/en/bitrise-ci/dependencies-and-caching/android-dependencies) |
| Bitrise signing | Uploaded keystore yields `BITRISEIO_ANDROID_KEYSTORE_URL`, `_PASSWORD`, `_ALIAS`, `_PRIVATE_KEY_PASSWORD`; **Android Sign** step signs AABs (jarsigner). | [Bitrise: Android Sign step](https://docs.bitrise.io/en/bitrise-ci/code-signing/android-code-signing/android-code-signing-using-the-android-sign-step) |
| Bitrise Play deploy | `android-build@1` → `sign-apk@1` → `google-play-deploy@3` with `service_account_json_key_path: $BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL`, `track` (internal/alpha/beta/production), `status` (docs suggest `draft` for production). | [Bitrise: Deploy to Google Play recipe](https://docs.bitrise.io/en/bitrise-ci/workflows-and-pipelines/workflows/workflow-recipes-for-android-projects/android-deploy-to-google-play-internal-alpha-beta-production) |
| Bitrise free plan | **Hobby**: free, 300 credits/month, 5 concurrent builds, 90-minute build timeout, 1 private app, team of one. Release Management on Hobby is not stated. | [Bitrise plans](https://bitrise.io/plans-pricing/velocity) |
| GitHub Actions cost | "free for standard GitHub-hosted runners in public repositories". | [GitHub Actions billing](https://docs.github.com/en/actions/concepts/billing-and-usage) |
| GitHub deployment approvals | Required reviewers on environments are available for **public** repos on all plans; optional "Prevent self-review". | [GitHub: Managing environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments) |
| Firebase AI Logic cost | Usable on the no-cost **Spark** plan with the Gemini Developer API free tier (no billing account); higher volume needs Blaze. | [Firebase AI Logic pricing](https://firebase.google.com/docs/ai-logic/pricing) |
| Gemini free tier data use | Free tier: content **is** "used to improve our products"; paid tier: not. | [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing) |
| On-device Gemini Nano | ML Kit GenAI **Prompt API is alpha**; works offline on-device; best on Pixel 10 series; limited device list. | [Android Developers: ML Kit Prompt API](https://developer.android.com/blog/posts/ml-kit-s-prompt-api-unlock-custom-on-device-gemini-nano-experiences) |
| Play production access | Personal developer accounts created after 2023-11-13 must run a **closed test with at least 12 testers opted in for 14 consecutive days** before applying for production. | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en) |
| Bitrise approvals | Release Management supports release candidates, Play upload, assigned approval tasks and staged rollout, gated by a Release Manager role. | [Bitrise Release Management concepts](https://docs.bitrise.io/en/release-management/getting-started-with-release-management/release-management-concepts) |

Not yet verified (deferred to the phase that uses them, and flagged there): exact current versions of AGP, Kotlin, Compose BOM, Room, Hilt and Play target-API requirements (Phase 1); Tink/Keystore API details (Phase 5); Compose accessibility-check APIs (Phase 7); Bitrise stack names and `format_version` value (Phase 9).

---

## 1. Product requirements

### 1.1 Vision
A universal, offline-first checklist app. Simple for an elderly first-time user; production-grade underneath. Any purpose: shopping, travel, exams, job joining, hospital visits, weddings, or anything the user invents.

### 1.2 Personas
| Persona | Need | Design consequence |
|---|---|---|
| **Kamala, 68**, reads Kannada, uses WhatsApp only | Diwali shopping list for her son to buy | Large controls, Kannada UI, PDF share to WhatsApp, no jargon |
| **Arjun, 29**, IT professional | Trip packing, reuses lists every trip | Duplicate checklist, reusable categories, AI generation |
| **Priya, 17**, student | Exam-day checklist | Fast create, template-like reuse, works offline in exam centre |

### 1.3 Functional requirements (v1.0)
| ID | Requirement | Priority |
|---|---|---|
| FR-01 | Create, edit, archive, unarchive, duplicate, delete checklists | Must |
| FR-02 | Attach zero or more categories to a checklist; add/remove later | Must |
| FR-03 | Pick from suggested categories, or create a custom category; rename custom categories | Must |
| FR-04 | Per category, search and multi-select master items, then "Add selected" | Must |
| FR-05 | Add a custom item (name, quantity, unit, notes); optionally save it to the category's master list | Must |
| FR-06 | Quantity (integer or decimal) and unit (built-in or custom) per item | Must |
| FR-07 | Mark complete/incomplete; progress per checklist | Must |
| FR-08 | Reorder categories and items without hidden gestures (explicit move up/down buttons; drag is optional extra) | Must |
| FR-09 | Home: search, sort (recent, name, progress), filter (active/archived) | Must |
| FR-10 | 7 languages, in-app language picker with "System default", persisted | Must |
| FR-11 | Offline search over master items in the current language and English, plus user items | Must |
| FR-12 | Profile (name, email, phone, address, home/office location), encrypted at rest | Must |
| FR-13 | JSON export/import with validation, preview and new IDs | Must |
| FR-14 | Local PDF export, shared through the Android Sharesheet | Must |
| FR-15 | Settings: language, theme, text size, notifications, privacy, security, import/export, AI, accessibility, about | Must |
| FR-16 | Optional AI: offline quick-add parser ("5 kg rice") in all 7 languages; online (opt-in) generate checklist, suggest categories/items, natural-language add, summarize | Should (Phase 10) |
| FR-17 | Voice input | Won't (v1), architecture ready |
| FR-18 | Cloud sync / multi-device | Won't (v1), IDs and timestamps chosen to allow it later |

### 1.4 Non-functional requirements
| Area | Target |
|---|---|
| Offline | 100% of FR-01 to FR-15 work in airplane mode. |
| Performance | Cold start < 1.5 s on a mid-range device; master-item search < 100 ms for 5,000 items; no main-thread I/O (StrictMode in dev). |
| Accessibility | WCAG 2.1 AA contrast, 48dp minimum touch targets (56dp for checkboxes), usable at 200% font scale, full TalkBack navigation. |
| Localization | Zero hard-coded user-visible strings (enforced by lint). |
| Security/privacy | Profile encrypted with a Keystore-backed key; no PII in logs, AI requests, analytics or default exports. No secrets in the APK/AAB or in Git. |
| Reliability | No destructive migrations; every schema change has a tested migration. |
| Size | Production AAB download < 15 MB. |
| Platform | minSdk 26 (assumption A-02), targetSdk = latest required by Play at Phase 1. |

---

## 2. User journeys

**J1 Create a Diwali shopping list (first use, no AI)**
1. Home is empty: big "Create checklist" button and a one-line hint.
2. Enters name "Diwali Shopping" (description optional) → Create.
3. "Add categories" screen shows suggested categories as large checkbox rows; ticks Groceries, Vegetables, Gifts; taps "Create category" and adds "Pooja Items". Can tap "Skip".
4. Lands on Checklist detail with empty category sections, each with "+ Add item".
5. Groceries → Add item → search screen lists Rice, Wheat, Sugar… → ticks Rice and Sugar → "Add selected (2)".
6. Each added item opens a compact quantity sheet (quantity stepper + unit dropdown defaulting to master item's default unit). Quantity can be skipped.
7. Ticks items while shopping; progress bar updates; screen reader announces "Rice, 5 kilograms, completed".

**J2 Custom item saved for reuse**: Add item → types "Dry fruits" → no match → "Create new item 'Dry fruits'" → fills 2 KG, note "For guests" → toggles "Save to Groceries suggestions" → Add. Next checklist, "Dry fruits" appears in Groceries suggestions.

**J3 Reuse**: Long-press is never required. Each checklist row has a visible "⋮" menu → Duplicate → new checklist "Goa Trip (copy)" with all items un-ticked.

**J4 Change language**: Settings → Language → മലയാളം → UI switches immediately; master items show Malayalam names; existing checklist item names stay exactly as created (snapshot rule, section 6).

**J5 Share a list**: Checklist ⋮ → Share as PDF → preview options ("Include completed items", "Include my name": off by default) → Android Sharesheet → WhatsApp.

**J6 Move phones**: Settings → Export → JSON saved through the system file picker → on new phone Import → preview "3 checklists, 42 items, 2 new categories" → Import → all created with new IDs, nothing overwritten.

**J7 AI generation (Phase 10, opt-in)**: "Create with AI" → "5-day Goa trip" → AI returns a proposal (categories + items with quantities) → review screen with checkboxes to drop anything → Confirm → app executes through use cases. Offline: friendly "AI needs internet. You can still create the checklist yourself." with a button that opens normal creation.

**J8 Natural language add (Phase 10)**: In a checklist, "5 ಕೆಜಿ ಅಕ್ಕಿ ಸೇರಿಸಿ" → AI returns `addChecklistItem(canonicalKey=rice, quantity=5, unit=KG)` → app resolves to "ಅಕ್ಕಿ" (or English name, per user locale) → confirmation chip "Add Rice 5 KG?" → Add.

---

## 3. Complete architecture

### 3.1 Style
Clean Architecture with three layers, MVVM with a unidirectional state flow (MVI-flavoured: one immutable `UiState` per screen, user `Action`s in, one-shot `Effect`s out). Room is the single source of truth; the UI observes `Flow`s from Room, so every write anywhere (UI, import, AI) shows up everywhere without manual refresh.

```text
┌──────────────────────── :app (presentation, di, navigation) ───────────────────────┐
│ Compose screens → ViewModel (StateFlow<UiState>, onAction(Action), Channel<Effect>)  │
└───────────────┬─────────────────────────────────────────────┬───────────────────────┘
                │ calls                                       │ calls
                ▼                                             ▼
┌──────── :domain (pure Kotlin/JVM) ────────┐      ┌──────── :ai (Android lib) ────────┐
│ models, use cases, repository interfaces, │◄─────│ AIService, tools, command mapper,  │
│ validators, AI command contract            │ uses │ ActionPlan executor (via use cases)│
└───────────────▲───────────────────────────┘      └────────────────────────────────────┘
                │ implements
┌──────── :data (Android lib) ──────────────┐
│ Room (entities, DAOs, migrations, FTS),    │
│ DataStore settings, encrypted profile,     │
│ seed loader, import/export, PDF writer     │
└────────────────────────────────────────────┘
```

### 3.2 Why four modules, not one
The prompt suggests packages inside one `app` module and warns against over-engineering. I recommend a **small** split because two of the project's hard rules become compiler-enforced instead of review-enforced:

1. `:domain` is a plain Kotlin/JVM module, so it *cannot* import Android or Room. Use cases stay pure and fast to test.
2. `:ai` depends on `:domain` only, never on `:data`. "AI must not access Room" is then impossible to violate by accident: there is no Room class on its classpath.

`:app` wires everything with Hilt. Inside each module, packages follow the structure in section 22. No further feature modules until there is a real need.

### 3.3 Data flow (write)
```text
Checkbox tap → ChecklistDetailViewModel.onAction(ToggleItem(id))
  → SetItemCompletedUseCase(id, true)            (:domain, validates)
  → ChecklistRepository.setCompleted(...)        (:domain interface)
  → RoomChecklistRepository → ChecklistItemDao.update(...)  (:data, Dispatchers.IO)
  → Room invalidates → DAO Flow emits → ViewModel maps to UiState → recomposition
```

### 3.4 Key libraries (versions pinned in Phase 1 via Version Catalog)
Kotlin, Jetpack Compose + Material 3, Navigation Compose with type-safe routes (Navigation 3 evaluated in Phase 1), Hilt, Room (+ KSP), DataStore (Preferences), kotlinx.serialization, kotlinx.coroutines, AppCompat (for per-app locales only), Tink (profile encryption). Test: JUnit, kotlinx-coroutines-test, Turbine, Robolectric, Room testing, Compose UI test, a screenshot library (Roborazzi). Static: Android Lint, detekt, ktlint (via detekt formatting). No networking library in the app until Phase 10.

---

## 4. Domain model

Pure Kotlin types in `:domain`. Times are `kotlinx.datetime.Instant` (or `Long` epoch millis at the boundary). IDs are value classes over UUID strings.

```kotlin
// Illustrative only; not code for Phase 0
@JvmInline value class ChecklistId(val value: String)

data class Checklist(id, title, description, createdAt, updatedAt, isArchived,
                     categories: List<ChecklistSection>)          // aggregate root
data class ChecklistSection(id: ChecklistCategoryId, category: Category,
                            displayOrder: Int, items: List<ChecklistItem>)
data class ChecklistItem(id, masterItemId: MasterItemId?, canonicalKey: String?,
                         displayName: String, quantity: Quantity?, notes: String?,
                         isCompleted: Boolean, position: Int, ...)
data class Category(id, canonicalKey: String?, customName: String?, icon: IconKey,
                    isCustom: Boolean)                          // display name resolved by Localizer
data class MasterItem(id, categoryId, canonicalKey: String, defaultUnit: UnitCode?,
                      isCustom: Boolean)
data class Quantity(amount: BigDecimal, unit: UnitCode)
data class UnitDef(code: UnitCode, allowsDecimal: Boolean, isCustom: Boolean, customLabel: String?)
data class UserProfile(name, email, phone, address, home: GeoPoint?, office: GeoPoint?)
```

Business rules that live in `:domain` (and therefore apply equally to UI, import and AI):
- Title 1–100 chars after trim; description ≤ 500; item name 1–80; notes ≤ 500.
- Quantity > 0 and ≤ 99,999; at most 3 decimal places; a unit with `allowsDecimal = false` (Piece, Nos, Pair, Dozen, Box, Pack, Bottle) rejects decimals.
- A category appears at most once per checklist.
- Adding a master item already present (same `masterItemId`) in the same section asks "Increase quantity instead?" rather than duplicating.
- Deleting a checklist is a soft confirm (dialog) and hard delete; archive is the suggested alternative.

---

## 5. Room database design

### 5.1 Decisions
| Decision | Choice | Why |
|---|---|---|
| Primary keys | `TEXT` UUID v4 | Import never reuses foreign IDs; future sync needs globally unique IDs; no collision when merging. Cost: slightly larger indexes, fine at this scale. |
| Time | `INTEGER` epoch millis UTC | Sortable, timezone-safe. |
| Quantity | `quantity_milli INTEGER` (amount × 1000) + `unit_code TEXT` | Exact decimals without floating-point error ("0.1 + 0.2"), still sortable and summable. |
| Localized names | Translation tables seeded from bundled JSON assets | Must be **queryable** for offline search in any language; string resources are not searchable with SQL. |
| Search | FTS4 virtual table over all names | Fast prefix search, offline. |
| Migrations | `exportSchema = true`, schemas committed, `AutoMigration` where possible, manual `Migration` otherwise, `MigrationTestHelper` test for each | Never `fallbackToDestructiveMigration()`. |

### 5.2 ER diagram
```text
checklist 1───* checklist_category *───1 category 1───* master_item
                        │                    │              │
                        1                    *              *
                        │            category_translation  master_item_translation
                        *
                 checklist_item *───0..1 master_item   (FK ON DELETE SET NULL)
                        │
                        *───1 unit_def

user_profile (single row, encrypted columns)       item_search_fts (FTS4, derived)
```

### 5.3 Tables
**checklist**: `id PK`, `title`, `description NULL`, `created_at`, `updated_at`, `is_archived INT DEFAULT 0`, `archived_at NULL`. Index `(is_archived, updated_at)`.

**category**: `id PK`, `canonical_key TEXT NULL UNIQUE` (seeded, e.g. `groceries`), `custom_name TEXT NULL` (user categories, or user rename of a seeded one), `icon_key TEXT`, `is_custom INT`, `is_hidden INT DEFAULT 0`, `created_at`, `updated_at`. Check: `canonical_key IS NOT NULL OR custom_name IS NOT NULL`. Index `(is_hidden)`.

**category_translation**: `canonical_key`, `locale` (BCP-47 language, `en`, `kn`…), `name`. PK `(canonical_key, locale)`.

**master_item**: `id PK`, `category_id FK→category ON DELETE CASCADE`, `canonical_key TEXT`, `custom_name TEXT NULL`, `custom_name_locale TEXT NULL`, `default_unit_code TEXT NULL FK→unit_def`, `is_custom`, `is_hidden`, `use_count INT DEFAULT 0` (ranking), `created_at`, `updated_at`. Unique `(category_id, canonical_key)`. Index `(category_id, is_hidden)`.

**master_item_translation**: `canonical_key`, `locale`, `name`, `aliases TEXT NULL` (e.g. Latin transliteration "akki", "chawal" for search). PK `(canonical_key, locale)`.

**unit_def**: `code PK` (`KG`, `GRAM`, `LITRE`, `ML`, `DOZEN`, `PIECE`, `PACK`, `BOX`, `BOTTLE`, `PAIR`, `METER`, `NOS`, or `CUSTOM_<uuid>`), `allows_decimal`, `is_custom`, `custom_label NULL`, `sort_order`. Built-in labels come from string resources `unit_kg`, `unit_gram`…; custom labels are stored as typed.

**checklist_category**: `id PK`, `checklist_id FK ON DELETE CASCADE`, `category_id FK ON DELETE RESTRICT`, `display_order`, `created_at`. Unique `(checklist_id, category_id)`. Index `(category_id)`.

**checklist_item**: `id PK`, `checklist_category_id FK ON DELETE CASCADE`, `master_item_id FK NULL ON DELETE SET NULL`, `canonical_key TEXT NULL`, `display_name TEXT NOT NULL`, `display_name_locale TEXT`, `quantity_milli INT NULL`, `unit_code TEXT NULL FK→unit_def`, `notes NULL`, `is_completed`, `completed_at NULL`, `position INT`, `created_at`, `updated_at`. Indexes `(checklist_category_id, position)`, `(master_item_id)`.

**user_profile**: `id PK = 'me'`, `enc_payload BLOB` (AES-256-GCM ciphertext of a serialized profile), `key_alias`, `schema_version`, `updated_at`. One encrypted blob instead of per-column encryption: nothing in the profile needs SQL querying, and one blob leaks less (no per-field lengths).

**item_search_fts** (FTS4, `tokenize=unicode61`): `ref_type` (MASTER/CUSTOM), `ref_id`, `category_id`, `locale`, `text` (name + aliases, normalized). Rebuilt for a row when its source changes, inside the same transaction.

**seed_meta**: `seed_version INT` for the bundled catalog (section 6.4).

### 5.4 Transactions
Multi-step writes run in `RoomDatabase.withTransaction {}` from repository methods: create checklist with categories; add-selected items (N inserts + use_count bump + FTS); duplicate checklist; import; AI action-plan execution.

---

## 6. Master item architecture

### 6.1 Identity
A seeded master item's identity is `canonical_key` (`rice`), never its display text. Translations are rows in `master_item_translation`, not separate items. This is what lets search, AI, import/export, analytics and future languages agree on "rice" regardless of script.

### 6.2 Display name resolution (`Localizer` in `:domain`, data in `:data`)
```text
custom_name (if user-created or renamed)
  → translation[current app locale]
  → translation["en"]
  → canonical_key humanized ("cooking_oil" → "Cooking oil")
```

### 6.3 Snapshot rule
When a master item is added to a checklist, the app **copies** the resolved `display_name`, its locale, `canonical_key`, and the chosen quantity/unit into `checklist_item`. `master_item_id` is kept only as a back-reference for ranking and AI. Renaming or deleting the master item never changes existing checklist items. Switching app language does not rewrite saved items either (an option "Show item names in current language" can be added later because `canonical_key` is preserved).

### 6.4 Seed catalog
- Bundled asset `assets/seed/catalog.json` (structure: units, categories, items with `canonicalKey`, `categoryKey`, `defaultUnit`) and `assets/seed/i18n/<locale>.json` (name + aliases per key). Not in Composables, not in Kotlin constants.
- `SeedLoader` runs in the Room `onCreate` callback and again when `seed_version` in the asset is higher than in `seed_meta`. It **upserts by canonical key** and never touches rows the user customized (`custom_name` set) or hid.
- Initial catalog = the lists in the brief (Groceries, Vegetables, Fruits, Clothing, Travel, Documents, Exam) plus empty seeded categories Gifts, Pooja Items, Decorations, Medicines, Toiletries, Electronics, Stationery, Other. Around 70 items.

### 6.5 Custom items
"Create new item" adds a `checklist_item` with `master_item_id = NULL`. If "Save to suggestions" is on, it also inserts a `master_item` with `is_custom = 1`, `canonical_key = "custom:<uuid>"`, `custom_name` and `custom_name_locale`, in the same transaction.

### 6.6 Search
Query normalized (NFKC, lowercase, trimmed). FTS prefix match over current locale + `en` + aliases + custom names; then ranked: exact > prefix > contains, then `use_count` desc, then alphabetical in the current locale's collation. Results grouped by category when searching across categories. Under 2 characters, show the category's suggestions ordered by `use_count`.

---

## 7. Category architecture
- Category is a reusable concept shared by all checklists; `checklist_category` is the per-checklist instance with its own order.
- Seeded categories: `canonical_key` + translations + `icon_key` (an emoji or Material symbol name mapped in the UI layer). Custom categories: `custom_name`, `is_custom = 1`.
- **Rename**: custom categories rename in place (affects every checklist using it, which is the expected meaning of "rename the Gifts category"). Renaming a seeded category sets `custom_name`, which overrides translations everywhere; "Reset name" clears it.
- **Delete**: if any checklist uses it, deletion is blocked with a clear message and the option to **Hide from suggestions** (`is_hidden = 1`). Unused custom categories can be deleted (cascades their custom master items after confirmation).
- **Suggestions on create**: seeded + custom categories, most-used first, then alphabetical in current locale. Phase 10 AI can reorder suggestions based on the title.

---

## 8. Checklist architecture
- `Checklist` is the aggregate root. Repository returns `Flow<ChecklistDetail>` built from one `@Transaction` DAO query with `@Relation`s (checklist → sections → items), mapped to domain.
- **Progress** = completed / total items, computed in the domain mapper (not stored), so it can never be stale.
- **Ordering**: `display_order` and `position` are sparse integers (steps of 1,000) so a move updates one row; renumber when gaps run out.
- **Duplicate**: deep copy with new UUIDs, all items `is_completed = 0`, title "<title> (copy)" localized.
- **Archive** hides from Home's default filter; archived lists remain searchable under the Archived filter.
- **Home queries**: search on title/description (LIKE on a normalized column is enough here), sort by `updated_at`, title collation, or progress.

---

## 9. Localization architecture (7 languages)

### 9.1 UI strings
- `res/values/strings.xml` (English, canonical) + `values-kn`, `values-hi`, `values-ta`, `values-te`, `values-mr`, `values-ml`.
- Rule: no user-visible literal in Kotlin. Enforced by Android Lint (`HardcodedText`, `SetTextI18n`) plus a custom detekt rule that flags string literals passed to `Text(...)`.
- Plurals via `<plurals>` (`pluralStringResource`), never string concatenation. Placeholders are positional (`%1$s`) so word order can change per language.
- Translation workflow: English is written first; the six translations live in the same PR. Untranslated keys fail a CI check (a small Gradle task comparing key sets), with an explicit allow-list for brand names.

### 9.2 Per-app language
- Use **AndroidX per-app language**: `AppCompatDelegate.setApplicationLocales(LocaleListCompat)`. On Android 13+ this maps to the platform `LocaleManager` and the choice also appears in system Settings; on API 26–32 AppCompat stores and applies it (`autoStoreLocales` in the manifest service entry).
- `locales_config.xml` generated by AGP (`androidResources.generateLocaleConfig = true`) with `resources.properties` declaring `unqualifiedResLocale=en`.
- "System default" = empty locale list. The persisted value is owned by the platform/AppCompat; we do not duplicate it in DataStore (single source of truth).
- `MainActivity` extends `AppCompatActivity` (still Compose-only UI) so locale changes recreate correctly.

### 9.3 Data localization
- Seeded categories/items: translation tables (section 5.3).
- Units: string resources keyed by unit code (`unit_kg`), so units are not hard-coded in UI logic; Compose gets labels through a `UnitLabelProvider`.
- Numbers and dates: `NumberFormat`/`DateTimeFormatter` with the app locale; digits remain Western Arabic by default (Indian users overwhelmingly expect them on phones), revisitable per language.

### 9.4 Adding a language later (Bengali, Gujarati, Punjabi, Odia, Assamese, Urdu)
Add `values-xx/strings.xml`, `assets/seed/i18n/xx.json`, one entry in `SupportedLanguages` (code + native name for the picker), bump `seed_version`. No business-logic change. Urdu is RTL: we use `start/end` everywhere from day one and run the `ar-XB` pseudo-locale in dev builds to catch RTL bugs early (`en-XA` catches truncation).

### 9.5 Script-specific UI risks
Tamil and Malayalam strings are often 30–60% longer than English; Indic scripts need taller line height. Mitigations: no fixed-width text containers, `lineHeight` from typography tokens tuned per script, screenshot tests for all 7 locales at 100% and 200% font scale.

---

## 10. Accessibility architecture
| Principle | Implementation |
|---|---|
| Large targets | Min 48dp everywhere, 56dp checkbox rows; whole row toggles, not just the box. |
| Readable type | Material 3 type scale bumped one step; all sizes in `sp`; layouts tested at 200%; in-app "Text size" (Normal / Large / Extra large) multiplies on top of system scale via a `LocalDensity` font-scale wrapper. |
| Contrast | Custom M3 color scheme verified ≥ 4.5:1 for text, ≥ 3:1 for UI; high-contrast theme option; no meaning by color alone (completed = tick + strikethrough + "completed" in semantics). |
| Screen reader | `Modifier.semantics(mergeDescendants = true)` per item row; `stateDescription` for completion; `contentDescription` from string resources for every icon button; headings marked with `heading()` for category sections; progress exposed as `progressBarRangeInfo`. |
| No hidden gestures | Swipe-to-delete and drag-to-reorder are optional shortcuts only; every action is also a visible button or menu item. |
| Destructive actions | Confirm dialog with explicit verb ("Delete 'Goa Trip'?" / "Delete" / "Cancel"); Undo snackbar for item deletion. |
| Feedback | Snackbars with clear text, also announced via live region; errors next to the field. |
| Simple words | Copy guide in `docs/accessibility.md` ("Add item", not "Insert entry"). |
| Testing | Compose semantics assertions, accessibility checks in UI tests, manual TalkBack script per release. |

---

## 11. AI architecture

### 11.1 Principles
1. The app is complete without AI. AI is **off by default** and enabled in Settings with a short privacy explanation.
2. AI **proposes**; the app **disposes**. Every AI output becomes a typed `ActionPlan` that is validated and executed by the same use cases the UI uses.
3. No credentials in the APK. No profile data in prompts.
4. Provider-independent: business code depends on `AIService`, never on a vendor SDK.

### 11.2 Components (`:ai` module)
```text
AiAssistantViewModel (:app)
        │
        ▼
AIService (interface, :domain contract)        ← MockAIService | OfflineCommandParser | GeminiAIService (Firebase AI Logic)
        │ returns AiResult<ActionPlan | Suggestions | Summary>
        ▼
CommandMapper + CommandValidator (:ai)         ← resolves canonical keys/units against local catalog
        │
        ▼
ConfirmationPolicy (:ai) ──needs review──► Review UI (:app) ──confirm──┐
        │ auto-allowed                                                 │
        ▼                                                              ▼
ActionPlanExecutor (:ai) → domain use cases → repositories → Room (one transaction)
```

### 11.3 Contract
```kotlin
// Illustrative
interface AIService {
    suspend fun generateChecklist(req: GenerateRequest): AiResult<ActionPlan>
    suspend fun suggestCategories(title: String, locale: String): AiResult<List<CategorySuggestion>>
    suspend fun suggestItems(ctx: ItemSuggestionContext): AiResult<List<ItemSuggestion>>
    suspend fun interpret(utterance: Utterance, ctx: ChecklistContext): AiResult<ActionPlan>
    suspend fun summarize(ctx: ChecklistContext): AiResult<String>
}
data class Utterance(val text: String, val locale: String, val source: InputSource) // TEXT now, VOICE later
sealed interface AiResult<out T> { Success, Unavailable(reason), Rejected(reason), Error(cause) }
```
`AiResult.Unavailable` (offline, disabled, quota, timeout) maps to one friendly message and a fallback button. AI calls have a timeout and never block the main checklist UI.

### 11.4 Context minimization
`ChecklistContext` contains only: checklist title, category canonical keys, item canonical keys/names and quantities, the user's locale, and the list of allowed unit codes. Never profile fields, notes (notes may contain personal info; opt-in later), IDs from the DB (replaced by short per-request refs like `s1`, `i3`).

### 11.5 Implementations (no backend, zero cost)
| Implementation | Network | Cost | Role |
|---|---|---|---|
| `MockAIService` | None | Free | Default in `dev` builds and all tests. Deterministic canned plans, so Phases 3–9 can build the review UI with no AI at all. |
| `OfflineCommandParser` ✅ | None | Free | Rule-based, fully offline "quick add" for simple commands in 7 languages: number + unit synonym + item name, matched against the local catalog and its translations/aliases ("5 ಕೆಜಿ ಅಕ್ಕಿ", "5 किलो चावल", "5 kg rice" → `rice, 5, KG`). Not an LLM, but covers the most common elderly-user need with zero dependency. |
| `GeminiAIService` ✅ (online, opt-in) | Internet | Free tier | Firebase AI Logic client SDK → Gemini Developer API on the Spark plan, protected by App Check (Play Integrity). Handles generation, suggestions, free-form commands and summaries using **function calling**, where the functions execute on the device. |
| `OnDeviceGeminiNanoService` (experiment) | None | Free | ML Kit GenAI Prompt API. Alpha and limited to a few flagship devices, so only behind a developer flag; never required. |

A `CompositeAIService` picks the best available: on-device parser for simple quick-add, Gemini if the user enabled online AI and is connected, otherwise `AiResult.Unavailable` with the friendly fallback.

**Key protection tradeoff (Gemini via Firebase AI Logic).** The app ships only the Firebase config (an identifier, not a secret). Abuse protection comes from App Check, which attests that calls come from the genuine app on a genuine device. This is weaker than a private server holding the key (a determined attacker on a rooted device can still burn quota), but it is Google's recommended pattern for apps without a backend, and the worst case on Spark is hitting the free quota, never a bill: no billing account is linked.

**Privacy tradeoff.** On the free tier Google may use request content to improve its products. Therefore: online AI is off by default, the opt-in screen says so in plain words, and requests carry only checklist text (section 11.4), never profile data or notes.

---

## 12. Google ADK integration approach

### 12.1 What the docs say (verified 2026-10-08)
- ADK is a **server-side agent framework** (Python, TypeScript, Go, Java, Kotlin). Its docs show no Android client SDK.
- Agents run as a process that serves HTTP (`adk api_server`: `/run`, `/run_sse`, sessions) or are deployed to Agent Runtime, Cloud Run or GKE. These need a Google Cloud project, and model access through Agent Platform needs billing.

### 12.2 Why ADK would need a backend, and why we drop it from the app
ADK is the "brain loop" of an agent: it holds the conversation, decides which tool to call, calls the model with credentials, and loops. That loop has to run somewhere trusted with a model key. On a phone that means shipping the key (forbidden); off the phone it means a server (you chose no backend, no spend). So **the Android app does not use ADK**. Instead, the app uses the same *agentic pattern* directly: Gemini function calling via Firebase AI Logic, with the app itself running the tool loop (model proposes a tool call → app validates → user confirms → use case runs → result returned to the model). Interview-wise this is the same concept ADK packages, just implemented on the client.

### 12.3 Where ADK still lives in the project (zero cost, no server) ✅
ADK runs as a **command-line agent inside CI** (GitHub Actions, free on a public repo), in `ci/agents/` (Python ADK):
- `review_agent`: AI code review of the PR diff → `AI_REVIEW.json` + a PR comment (Phase 11).
- `failure_agent`: reads trimmed failed-build logs → `CI_FAILURE_REPORT.md` (Phase 11).
- `release_notes_agent`: commits + PRs + `issues.csv` → user-facing release notes (Phase 11).
Each run starts, does one job, and exits: no hosting. The model key is a GitHub Actions secret on the Gemini free tier. Locally you can explore the same agents with `adk web` for learning.

### 12.4 Keeping the door open
`AIService` stays provider-independent. If you ever want a hosted agent, a `RemoteAgentAIService` can be added behind the same interface and the design from revision 1 (ADK on Cloud Run with App Check) applies unchanged. Nothing in `:domain` or `:data` would move.

---

## 13. AI tool/function architecture

| Tool | Args (canonical) | Use case called | Risk class |
|---|---|---|---|
| `searchMasterItems` | `query, locale, categoryKey?` | `SearchMasterItemsUseCase` | Read |
| `suggestCategories` | `title, locale` | read-only catalog | Read |
| `suggestItems` | `categoryKey, context` | read-only catalog | Read |
| `summarizeChecklist` | `checklistRef` | `GetChecklistUseCase` | Read |
| `createChecklist` | `title, description?, categories[]` | `CreateChecklistUseCase` | Create (bulk) → confirm |
| `addCategory` | `checklistRef, categoryKey or name` | `AddCategoryToChecklistUseCase` | Create |
| `addChecklistItem` | `sectionRef, canonicalKey? , name, quantity?, unit?` | `AddChecklistItemUseCase` | Create (simple) |
| `updateItemQuantity` / `updateItemUnit` | `itemRef, value` | `UpdateItemUseCase` | Modify |
| `completeItem` / `uncompleteItem` | `itemRef` | `SetItemCompletedUseCase` | Modify (simple) |
| `removeCategory` | `sectionRef` | `RemoveCategoryFromChecklistUseCase` | Destructive → always confirm |
| `deleteItem` | `itemRef` | `DeleteChecklistItemUseCase` | Destructive → always confirm |

- Tool schemas are generated from one Kotlin source of truth (`ToolCatalog` in `:ai`) and converted to Gemini function declarations, so the tool list and the executor cannot drift (contract test in CI).
- `unit` is an enum of unit codes; unknown units become a validation error shown to the user, never a silent custom unit.
- **ConfirmationPolicy**: Read → no confirmation. Create/Modify simple single operation → confirm by default; auto-execute only if the user enables "Let AI add items without asking". Bulk (more than 1 operation) and Destructive → always reviewed. Every executed plan gets a single Undo.
- **Execution**: with Firebase AI Logic, these are declared as Gemini function declarations; the model returns function calls and the app executes them through `ActionPlanExecutor`. The offline parser produces the same `ActionPlan`, so the review UI and executor are shared.
- **Multilingual**: the user's text and locale go to the model; the model must answer with canonical keys and unit codes (e.g. "5 ಕೆಜಿ ಅಕ್ಕಿ" → `rice, 5, KG`). The app maps keys to localized display names. The database never depends on the input language.
- **Voice (future)**: `SpeechRecognizer` (or on-device speech) produces an `Utterance(source = VOICE)`; everything downstream is unchanged.

---

## 14. Git branching strategy

```text
main  ◄── release/x.y.z ◄── develop ◄── feature/CL-123-short-name
  ▲                                         
  └── hotfix/CL-456-short-name (from main, merged to main AND develop)
```
| Branch | Purpose | Merges into | Environment |
|---|---|---|---|
| `feature/CL-<id>-<slug>` | One issue | `develop` via PR (squash merge) | PR checks only |
| `develop` | Integration | `release/*` (branch cut) | DEV |
| `release/x.y.z` | Stabilization, only fixes | `main` (merge commit) and back to `develop` | STAGING |
| `main` | What is in production | — | PRODUCTION (via tag) |
| `hotfix/CL-<id>-<slug>` | Emergency fix from `main` | `main` + `develop` | STAGING then PRODUCTION |

- Commits: `CL-101 Add checklist creation flow` (issue ID + imperative, ≤ 72 chars subject). A `commit-msg` hook (installed by a Gradle task) and a CI check enforce `^CL-\d+ [A-Z]`.
- Tags: annotated `vX.Y.Z` on `main` only; the production pipeline verifies the tag commit is reachable from `origin/main`.
- Squash for features (clean history: one commit per issue), merge commit for release/hotfix (keeps the release boundary visible).

---

## 15. GitHub strategy

### 15.1 Repository
- `shivayogih/CheckList`, **public**. Default branch `develop`; `main` protected.
- Public means: never commit anything you would not show an interviewer. Secret scanning + push protection on; gitleaks in CI as a second net.
- **Files (Phase 8)**: `README.md`, `.github/pull_request_template.md`, `.github/ISSUE_TEMPLATE/{feature,bug,tech_debt,security,ci_cd}.yml`, `CODEOWNERS`, `SECURITY.md`, `.github/dependabot.yml`, `.github/workflows/*.yml` (section 16).

### 15.2 Who can do what (your answer 5)
Claude works through your GitHub account via the Claude GitHub app, so GitHub sees Claude's PRs as yours and **an approval from the same account is impossible**. The rules therefore rely on required CI checks instead of approval counts:

| Branch | Rule | Who merges |
|---|---|---|
| `develop` | PR required, required checks green (`pr-checks`), no force push, 0 approvals | Claude, after CI is green |
| `release/*` | PR required for fixes, required checks green | Claude |
| `main` | PR required, required checks green, no force push or deletion, 0 approvals, **bypass allowed for you** | Claude prepares the PR; merges only after you say "go" for that release |
| Tags `v*` | Tag protection (ruleset) | Created after the `main` merge, with your go |
| Play production release | Draft in Play Console | **You** press release (or tell Claude explicitly) |

Why keep your "go" for `main` and Play even with full access: those are the two steps that reach real users and cannot be undone by a revert. Everything before them is reversible and Claude drives it without asking.

### 15.3 Security features
Secret scanning + push protection, Dependabot alerts and updates, private vulnerability reporting, CodeQL is not needed (Kotlin Android coverage is limited and detekt/lint cover most of it).

---

## 16. CI/CD strategy: Bitrise + GitHub Actions on free tiers

### 16.1 Budget reality
| Service | Free allowance (verified) | What it is good for here |
|---|---|---|
| Bitrise Hobby | 300 credits/month, 90-minute timeout, 5 concurrent builds, 1 private app, 1 user | Learning Bitrise: environment builds, signing, Play upload. Low frequency. |
| GitHub Actions | Unlimited on standard runners for public repos | Every-push PR checks, AI CI agents, approvals via Environments. High frequency. |
| Firebase (Spark) | App Distribution, App Check, AI Logic free tier | Tester builds, AI protection |
| Google Play | One-time registration fee (you accepted) | Closed testing and production |

A PR-heavy project can easily run 50–100 PR builds a month; at Bitrise's credit rates that would exhaust 300 credits. So frequency decides the split.

### 16.2 Split ✅
| Workflow | Runs on | Trigger | Steps |
|---|---|---|---|
| `pr-checks` | **GitHub Actions** | every PR to `develop`, `release/*`, `main` | commit-message check, gitleaks, lint, detekt, ktlint, translation-key check, `testDevDebugUnitTest`, Robolectric Room/UI smoke, `assembleDevDebug`, test report |
| `ai-review` | GitHub Actions | PR (after `pr-checks` static jobs) | ADK `review_agent`, advisory only |
| `pr` (Bitrise) | Bitrise | PRs into `release/*` and `main` only (rare) | Same checks as `pr-checks`; exists so you learn the Bitrise PR workflow without burning credits |
| `dev` | Bitrise | push to `develop` | `assembleDevDebug` → Firebase App Distribution (dev testers) |
| `staging` | Bitrise | push to `release/*` | tests → `bundleStagingRelease` → Firebase App Distribution; `bundleProductionRelease` → Android Sign → Play **closed testing** track |
| `production` | Bitrise | tag `vX.Y.Z` | validate tag on `main` + version → tests, lint, security → `bundleProductionRelease` → Android Sign → release notes → `google-play-deploy@3` (`track: production`, `status: draft`) |
| `ci-failure` | GitHub Actions | when `pr-checks` fails; Bitrise failures post their trimmed log as an artifact | ADK `failure_agent` → `CI_FAILURE_REPORT.md` |

Budget guard: if `dev` on every `develop` push becomes too expensive, it runs on a schedule (nightly when `develop` changed) instead. Credit usage per build is measured in Phase 9 and recorded in `builds.csv`.

**If Bitrise's free limits bite**, GitHub Actions can take over any workflow: same Gradle tasks, Gradle caching via `gradle/actions/setup-gradle`, signing secrets in GitHub Environment secrets, Play upload via a Play-publishing action, Firebase upload via the Firebase CLI. The design keeps all build logic in Gradle tasks and `ci/scripts/`, so both CI systems call the same commands and switching is a config change, not a rewrite.

### 16.3 Bitrise YAML shape (sketch; syntax re-verified in Phase 9)
```yaml
format_version: "<current>"
project_type: android
app:
  envs:
  - PROJECT_LOCATION: .
  - MODULE: app
step_bundles:
  setup:   # git-clone, set-java-version, restore-gradle-cache
  finish:  # save-gradle-cache, deploy-to-bitrise-io (artifacts + test reports)
workflows:
  pr:
    triggers:
      pull_request:
      - target_branch: main
      - target_branch: { pattern: "release/*" }
  dev:
    triggers: { push: [ { branch: develop } ] }
  staging:
    triggers: { push: [ { branch: { pattern: "release/*" } } ] }
  production:
    triggers: { tag: [ { name: { regex: "^v\\d+\\.\\d+\\.\\d+$" } } ] }
```
Target-based `triggers` and step bundles are used because the docs mark `trigger_map` and `before_run/after_run` as legacy. A multi-workflow PR pipeline is not used on Bitrise (it would multiply credit use); parallelism happens on GitHub Actions instead.

### 16.4 Manual approval to production (free)
1. The production workflow uploads to Play as **`status: draft`**. Nothing reaches users until a human presses release in Play Console. This is the primary gate and costs nothing.
2. Bitrise Release Management approvals are not relied on, because Hobby-plan availability is not documented.
3. If production ever moves to GitHub Actions, it runs in a `production` **Environment with you as required reviewer** (free on public repos).

### 16.5 Caching
| What | Bitrise | GitHub Actions | Invalidation |
|---|---|---|---|
| Gradle dependencies + wrapper | Restore/Save Gradle cache steps (auto keys) | `setup-gradle` cache | Changing `libs.versions.toml`, wrapper or build scripts changes the key |
| Gradle configuration/build cache | Gradle's own caches | same | Gradle input hashing |
| Never cached | Signing files, secrets, release artifacts | same | — |
Caching matters more on the free tier: fewer minutes per build means more builds per month.

### 16.6 Secrets
| Item | Stored in | Exposed to PR builds |
|---|---|---|
| Upload keystore + passwords | Bitrise Code Signing & Files (`BITRISEIO_ANDROID_KEYSTORE_*`) | **No** |
| Play service account JSON | Bitrise generic file storage (`BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL`) | **No** |
| Firebase App Distribution credentials | Bitrise secret | No |
| Gemini key for CI agents | GitHub Actions secret | Same-repo PRs only (fork PRs never receive secrets) |
Nothing secret is ever in the app or the repo; Firebase config files for the app contain identifiers only.

### 16.7 Reports and artifacts
JUnit XML, lint/detekt HTML + SARIF (shown in GitHub code scanning), coverage, APK/AAB, `CI_REPORT.md`, `AI_REVIEW.json`, `CI_FAILURE_REPORT.md`, release notes.

---

## 17. DEV / STAGING / PRODUCTION strategy

| | dev | staging | production |
|---|---|---|---|
| Flavor `applicationId` | `<base>.dev` | `<base>.staging` | `<base>` |
| Typical variant | `devDebug` | `stagingRelease` | `productionRelease` |
| App name | "CheckList Dev" | "CheckList Staging" | "CheckList" |
| Launcher icon | Badge "DEV" | Badge "STG" | Normal |
| Minify (R8) | No | Yes | Yes |
| StrictMode, debug logs | On | Off | Off |
| AI default | `MockAIService` + offline parser | Offline parser; online AI opt-in (Firebase staging project) | Offline parser; online AI opt-in (Firebase production project) |
| Distribution | Firebase App Distribution | Firebase App Distribution (+ Play closed testing uses the production ID) | Google Play (manual release of a draft) |
| Signing | Debug key | Upload key from Bitrise | Upload key from Bitrise (Play App Signing holds the app key) |

- `<base>` = **`com.dataloom.checklist`** (your answer 2): `com.dataloom.checklist.dev`, `com.dataloom.checklist.staging`, `com.dataloom.checklist`. The production ID is permanent once published on Play. Kotlin packages use `com.dataloom.checklist`.
- **Flavors vs build types vs variants**: flavor = *which environment* (config, IDs, endpoints); build type = *how it is built* (debuggable, minified, signed); variant = flavor × build type. Unused combos (e.g. `productionDebug` on CI) are filtered with `androidComponents.beforeVariants`.
- Environment config via `BuildConfig` fields per flavor (agent base URL, feature flags). Only **non-secret** values; anything secret lives server-side.
### 17.3 Google Play closed-testing rule
Because your Play account is a new personal account, Play requires a **closed test with at least 12 testers opted in for 14 consecutive days** before you can apply for production access. Plan:
- The `staging` workflow uploads the production-ID AAB to Play's **closed testing** track from the first `release/1.0.0` build.
- You recruit 12+ testers (family/friends with Google accounts); Firebase App Distribution testers do not count toward this rule.
- Fixes during the 14 days are new uploads, which need new version codes (handled by the version code scheme in 17.4).
- Milestone 12 (production) cannot start before those 14 days complete.

### 17.4 Versioning
- **Versioning (Option B, confirmed by you 2026-10-08)**: `versionName = MAJOR.MINOR.PATCH`; `versionCode = MAJOR*10000 + MINOR*1000 + PATCH*100 + BUILD`. So 1.0.0 uses codes 10000–10099 (first upload 10000, next fix during the closed test 10001, …), 1.0.1 starts at 10100, 1.1.0 at 11000. It keeps your "1.0.0 = 10000" starting point and allows up to 100 uploads per version. Limits: MINOR and PATCH 0–9, BUILD 0–99; if MINOR ever reaches 10 we move to a wider formula, which still produces larger codes, so Play's "always increase" rule holds. `BUILD` is read from `releases.csv` (last uploaded code + 1). The production pipeline fails if the tag ≠ `versionName` or the code is not greater than the last released one.

---

## 18. Testing strategy

| Layer | Tooling | What | Runs in |
|---|---|---|---|
| Domain unit | JUnit, coroutines-test | Use cases, validators, quantity math, localizer fallback, ConfirmationPolicy, CommandValidator | `pr` |
| ViewModel | JUnit, Turbine, fake repositories | State transitions, effects, error/empty states | `pr` |
| Data (Room) | Robolectric in-memory DB (fast) + a small instrumented set | DAOs, FK cascades, FTS search, transactions, seed idempotency | `pr` (Robolectric), `staging` (instrumented) |
| Migrations | `MigrationTestHelper` + exported schemas | Every version N→N+1 and 1→latest | `pr` |
| Repository | Fakes + Room | Offline behavior, error mapping, consistency after import | `pr` |
| Import/export | Golden JSON files | Valid, malformed, oversized, future-version, hostile IDs, round-trip | `pr` |
| AI mapping | Golden utterances in 7 languages → expected `ActionPlan` (with MockAIService/recorded responses) | `pr` |
| Compose UI | Compose test rule + Hilt test runner | J1–J6 journeys, navigation, quantity/unit, language switching | `staging` (emulator), smoke on `pr` via Robolectric |
| Screenshot | Roborazzi | Key screens × 7 locales × {100%, 200%} font × {light, dark} | `pr` (diff report) |
| Accessibility | Semantics assertions + automated accessibility checks | Labels, touch target size, contrast | `pr`/`staging` |
| Static | Lint (warnings as errors for i18n/security), detekt, ktlint | | `pr` |

Principles: prefer fakes over mocks for repositories; no `Thread.sleep`; tests never skipped to get green; coverage reported (target ≥ 80% for `:domain`, no global gate that encourages junk tests).

---

## 19. Security strategy

### 19.1 Data at rest
- Android already encrypts app storage at rest (file-based encryption). Checklist data is not highly sensitive, so the Room database is **not** additionally encrypted in v1 (avoids SQLCipher size/perf cost). **Decision (your answer 6): profile-only.** Revisit trigger: if a feature starts storing identity numbers, medical details or financial data inside checklist items (not just names like "Aadhaar" or "Medical reports"), switch to SQLCipher with a Keystore-wrapped passphrase via a tested migration.
- **Profile** is encrypted: AES-256-GCM with a key wrapped by an **Android Keystore** key (non-exportable, hardware-backed where available). Implementation via **Tink** with a Keystore master key. Jetpack `security-crypto` (EncryptedSharedPreferences) is deprecated, so we avoid it (re-verified in Phase 5).
- Backup: `dataExtractionRules`/`fullBackupContent` include checklists, **exclude** `user_profile` and Tink keysets (Keystore keys are not restorable on a new device, so a restored blob would be undecryptable anyway).

### 19.2 Data in transit and to third parties
- No network permission use until Phase 10; then HTTPS only (`usesCleartextTraffic=false`, network security config).
- No analytics/crash SDK in v1. If added later: PII scrubbing, no profile fields, opt-in.
- Firebase SDKs used: App Distribution (tester builds only, not in production code path), App Check and AI Logic (only when online AI is enabled).
- AI: context minimization (11.4); profile never sent; prompt and response bodies never logged in release builds. Gemini free tier may use content to improve Google products, so online AI is opt-in with that disclosure (section 11.5).

### 19.3 Code and logs
- Logging via a thin wrapper that is a no-op in release; lint rule bans `Log.*` and `println` outside it; a `@Sensitive` value class whose `toString()` is `"***"` for profile fields.
- R8 enabled for staging/production; `debuggable=false`; `exported` only for launcher activity and FileProvider (non-exported, grant-on-share).
- Intents for share use `FileProvider` URIs with temporary read grants; files in `cacheDir/exports`, deleted on next launch.

### 19.4 Secrets and signing
- Nothing secret in Git: `local.properties`, `*.jks`, `*.keystore`, `google-services.json` for prod, service-account JSON in `.gitignore`; **gitleaks** in the PR pipeline; GitHub push protection on.
- **Play App Signing**: Google holds the app signing key; we hold only the **upload key**, stored in Bitrise Code Signing & Files. Losing the upload key is recoverable via Play support; leaking it can be revoked.
- Local release builds read signing values from environment variables only; no signing config committed.
- **GitHub Secrets** hold only the Gemini key for CI agents (never signing material); **Bitrise Secrets** hold signing, Play and Firebase credentials, marked protected and never exposed to PR builds (16.6).

### 19.5 AI-specific
Never send passwords, API keys, tokens, keystore data or Bitrise/GitHub secrets to any AI. CI AI tools receive diffs and logs after a redaction pass (regexes for keys/tokens + Bitrise's own log redaction). AI review is advisory only: it cannot approve, merge, push, or change branch protection.

---

## 20. Import / export design

### 20.1 Format (documented in `docs/import-export-format.md` in Phase 6)
```json
{
  "formatVersion": 1,
  "schemaVersion": 1,
  "metadata": { "app": "CheckList", "appVersion": "1.0.0", "exportedAt": "2026-10-08T04:00:00Z",
                "locale": "kn", "includesProfile": false },
  "units":      [ { "ref": "u1", "code": "CUSTOM", "label": "Bundle", "allowsDecimal": false } ],
  "categories": [ { "ref": "c1", "canonicalKey": "groceries" },
                  { "ref": "c2", "customName": "Pooja Items", "icon": "temple" } ],
  "checklists": [ { "ref": "k1", "title": "Diwali Shopping", "description": "...", "archived": false,
                    "createdAt": "...", "sections": [ { "ref": "s1", "categoryRef": "c1", "order": 0 } ] } ],
  "items":      [ { "ref": "i1", "sectionRef": "s1", "canonicalKey": "rice", "displayName": "ಅಕ್ಕಿ",
                    "displayNameLocale": "kn", "quantity": "5", "unit": "KG", "notes": null,
                    "completed": false, "position": 0 } ],
  "profile": null
}
```
- `ref`s are file-local; **database IDs are never exported**. Quantities are strings to keep exact decimals.
- `formatVersion` = envelope shape; `schemaVersion` = content model. Importer supports `<= current`, upgrading older files through chained `JsonMigrator`s; newer versions are rejected with "Update the app to import this file".

### 20.2 Import pipeline
```text
SAF pick (ACTION_OPEN_DOCUMENT, no storage permission)
→ size check (reject > 10 MB) → streaming parse (kotlinx.serialization) with limits
  (≤ 500 checklists, ≤ 20,000 items, string length caps)
→ version check/upgrade → structural + domain validation (same validators as UI)
→ reference integrity (every sectionRef/categoryRef resolves; no cycles/dupes)
→ resolve categories: canonicalKey → existing seeded category; customName → match existing custom (case-insensitive) or create
→ Preview screen: counts, new categories, conflicts ("A checklist named 'Goa Trip' exists → import as 'Goa Trip (2)'")
→ user confirms → single Room transaction with fresh UUIDs → success summary
```
Default strategy is **always add as new**, never overwrite. Any failure rolls back the whole transaction.

### 20.3 Export
SAF `ACTION_CREATE_DOCUMENT`; choose all or selected checklists; profile excluded unless explicitly ticked (and then a warning). Same privacy rule for PDF.

### 20.4 PDF
Generated locally with `android.graphics.pdf.PdfDocument` + `StaticLayout` (correct Indic shaping through the platform text stack), A4, large font, checkbox glyphs, category headings, optional "completed items" and optional name. Shared via Sharesheet (`ACTION_SEND`, `application/pdf`, FileProvider). No WhatsApp API.

---

## 21. CSV / Jira simulation

### 21.1 Files (`docs/project-management/`)
- `issues.csv`: `Issue ID, Title, Description, Type, Priority, Status, Assignee, Branch, Pull Request, Build Number, Environment, Test Status, AI Review Status, Created Date, Updated Date, Release`
- `builds.csv`: `Build Number, Workflow, Branch, Commit, Issue IDs, Status, Duration, Artifact, Started At`
- `test-results.csv`: `Build Number, Suite, Total, Passed, Failed, Skipped, Coverage, Report Link`
- `releases.csv`: `Version Name, Version Code, Tag, Release Branch, Issues, Build Number, Track, Status, Approved By, Released At`

Statuses: `BACKLOG → TODO → IN_PROGRESS → CODE_REVIEW → CI_RUNNING → (CI_FAILED ↺) → READY_FOR_QA → QA_IN_PROGRESS → READY_FOR_RELEASE → RELEASED → DONE`. Allowed transitions are encoded once and validated.

### 21.2 Abstraction
A small JVM module `:tools:issuetracker` (Kotlin CLI, not part of the app):
```kotlin
interface IssueTracker {
    fun get(id: IssueId): Issue
    fun updateIssue(id: IssueId, change: IssueChange)
    fun updateStatus(id: IssueId, to: IssueStatus)   // validates transition
    fun addComment(id: IssueId, text: String)
}
class CsvIssueTracker(path) : IssueTracker   // now
class JiraIssueTracker(baseUrl, token) : IssueTracker   // later, same interface
```
- Comments go to `issue-comments.csv` (keeps `issues.csv` one row per issue).
- **CI does not commit to protected branches.** CI runs the tool in "report" mode: it computes the CSV updates (build row, test row, status suggestion) and publishes them as artifacts and a PR comment. The developer applies them locally (`./gradlew :tools:issuetracker:run --args="apply build-123.json"`) in the PR. This mirrors how Jira is updated by CI webhooks, without CI pushing to `main`/`develop`.

---

## 22. Project folder structure

```text
CheckList/
├── app/                                 # :app  presentation + DI + navigation
│   └── src/main/java/<base>/
│       ├── presentation/{home,checklist,category,masteritem,profile,settings,importexport,ai,components,theme}
│       ├── navigation/
│       ├── localization/                # language picker, SupportedLanguages
│       └── di/
│   └── src/main/res/values{,-kn,-hi,-ta,-te,-mr,-ml}/strings.xml
├── domain/                              # :domain  pure Kotlin
│   └── src/main/kotlin/<base>/domain/{model,repository,usecase,validation,localization}
├── data/                                # :data  Android library
│   └── src/main/kotlin/<base>/data/{local/{dao,entity,database,migration,fts},repository,mapper,seed,settings,profile,importexport,pdf}
│   └── src/main/assets/seed/{catalog.json,i18n/*.json}
│   └── schemas/                         # Room exported schemas (committed)
├── ai/                                  # :ai  Android library, depends on :domain only
│   └── src/main/kotlin/<base>/ai/{model,tools,service,mapper,policy,executor}
├── tools/issuetracker/                  # :tools:issuetracker  JVM CLI
├── ci/scripts/                          # validate-version, trim-logs, csv report helpers
├── ci/agents/                           # Python ADK agents run in CI: review, failure, release notes
├── docs/  architecture.md database.md security.md testing.md ci-cd.md branching-strategy.md
│          release-process.md troubleshooting.md import-export-format.md ai-automation.md
│          localization.md accessibility.md  adr/0001-*.md  project-management/*.csv
├── gradle/libs.versions.toml
├── bitrise.yml
├── .github/{pull_request_template.md,ISSUE_TEMPLATE/,CODEOWNERS,dependabot.yml,workflows/}
├── README.md  SECURITY.md  version.properties
└── settings.gradle.kts  build.gradle.kts
```
There is no backend repository: the app is fully offline, and ADK runs only as CI tooling.

---

## 23. Development milestones

| Phase | Deliverable | Issue IDs (proposed) | Exit check |
|---|---|---|---|
| 0 | This document | CL-100 | Your approval |
| 1 | Repository `shivayogih/CheckList` created; project skeleton: 4 modules, Version Catalog, flavors, build types, theme, navigation shell, string resources skeleton, lint/detekt | CL-101–CL-106 | `devDebug`, `stagingRelease`, `productionRelease` build in the cloud container |
| 2 | Room schema v1, DAOs, FTS, seed loader, repositories, use cases, Hilt, tests | CL-110–CL-119 | Room + unit tests green |
| 3 | Home, create, categories, detail, search, add selected, custom item, quantity/unit, completion, reorder | CL-120–CL-134 | UI tests for J1–J3 |
| 4 | 7 languages, language picker, text size, accessibility semantics | CL-140–CL-147 | Screenshot matrix + TalkBack script |
| 5 | Profile, Keystore/Tink encryption, backup rules, privacy rules | CL-150–CL-154 | Security checklist |
| 6 | JSON export/import with preview, PDF + Sharesheet | CL-160–CL-166 | Golden-file tests |
| 7 | Test expansion: migrations, repository, ViewModel, UI, localization, accessibility | CL-170–CL-176 | Coverage report |
| 8 | GitHub repo hygiene, templates, rulesets, README, `pr-checks` on GitHub Actions | CL-180–CL-186 | Protection verified with a test PR |
| 9 | Bitrise pr/dev/staging/production on Hobby, caching, signing, Firebase App Distribution, Play closed track; credit use measured | CL-190–CL-197 | Each workflow green once; monthly credit forecast under 300 |
| 10 | AI: Mock → contract → offline parser (7 languages) → review UI → Gemini via Firebase AI Logic with function calling and App Check | CL-200–CL-212 | AI off = app unchanged; golden utterance tests |
| 11 | ADK agents in CI: code review, failure analyzer, release notes | CL-220–CL-224 | Reports on a real PR and a forced failure |
| 12 | End-to-end simulation; 14-day closed test with 12+ testers; apply for production access; release 1.0.0 draft → you release | CL-230–CL-232 | Documented walkthrough; app live on Play |

Every phase ends with the File Change Report and Verification Report formats from the brief.

---

## 24. Definition of Done

A feature is done only when **all** apply:
1. Code implemented following this architecture (layers respected; `:ai` never depends on `:data`).
2. Unit tests (and Room/UI tests where relevant) added and passing; no skipped or disabled tests.
3. UI verified on a device/emulator at 100% and 200% font scale, light and dark.
4. Lint, detekt, ktlint pass with no new baseline entries.
5. Security reviewed: no secrets, no PII in logs/exports/AI context, permissions justified.
6. Localization: all strings in resources; all 7 translations present (or an explicit tracked follow-up).
7. Accessibility: labels, targets, contrast, TalkBack path checked.
8. Documentation updated (relevant `docs/*.md`, ADR if a decision changed).
9. Commit(s) follow `CL-xxx Imperative summary`; PR opened with template filled.
10. CI green; AI review read and findings addressed or answered.
11. `issues.csv` status updated; build verified in the right environment (DEV for features, STAGING for release fixes).

---

## 25. Risks and architectural decisions

### 25.1 Architecture decision records (to be committed as `docs/adr/` in Phase 1)
| ADR | Decision | Main alternative rejected | Why |
|---|---|---|---|
| 001 | 4 Gradle modules (`app`, `domain`, `data`, `ai`) | Single module with packages | Compiler enforces "domain is pure" and "AI cannot touch Room" |
| 002 | Room as single source of truth, UI observes Flows | In-memory cache + manual refresh | Offline-first, consistency across UI/import/AI |
| 003 | UUID text primary keys | Auto-increment longs | Safe import, future sync |
| 004 | Quantities as integer milli-units | `Double` / `REAL` | Exact decimals |
| 005 | Translations in seeded DB tables + FTS | String resources for item names | Offline multilingual search, canonical identity |
| 006 | Checklist items are snapshots | Live reference to master item | User data never changes behind their back |
| 007 | AndroidX per-app locales | Custom `Context` wrapping | Official, integrates with Android 13+ settings |
| 008 | AI proposes `ActionPlan`; app executes via use cases | AI calls repositories | Business rules and confirmation stay in app |
| 009 | No backend. App AI = offline parser + Gemini via Firebase AI Logic (App Check, Spark free tier); ADK runs only as CI agents | ADK on Cloud Run; LLM key in the app | Your no-backend, zero-cost constraint; no credentials in APK; ADK still learned |
| 010 | AI off by default, MockAIService for dev/tests | Real AI everywhere | Privacy, offline, deterministic tests |
| 011 | Tink + Keystore for profile; no DB-wide encryption in v1 | SQLCipher | Proportionate protection; smaller, faster |
| 012 | Bitrise target-based triggers + step bundles | `trigger_map`, `before_run` | Current docs mark those as legacy |
| 014 | PR checks on GitHub Actions; environment builds on Bitrise Hobby | Everything on Bitrise | 300 credits/month cannot cover every PR push; Actions is free for public repos |
| 015 | Required CI checks instead of approval counts; your go for `main` and Play | 1 required approval | Claude acts through your account, so self-approval is impossible |
| 016 | versionCode = MAJOR*10000 + MINOR*1000 + PATCH*100 + BUILD (Option B) | MAJOR*10000 + MINOR*100 + PATCH | Play closed testing needs several uploads per version |
| 013 | Production upload as Play draft + human release | Auto-publish | Manual approval requirement |

### 25.2 Risks
| Risk | Impact | Mitigation |
|---|---|---|
| Translation quality for 6 Indic languages | Elderly users confused | Native-speaker review per language before release; short, simple strings; screenshot review |
| Indic text overflow/clipping | Broken UI | Flexible layouts, screenshot matrix, pseudo-locales |
| Users typing Latin transliterations ("akki") | Search misses | `aliases` column seeded for common items; future transliteration |
| Gemini free-tier quota exhausted or terms change | Online AI unavailable | AI is optional; offline parser keeps quick-add working; no billing account linked, so never a bill |
| Bitrise credits run out mid-month | Environment builds stop | Measure per-build credits; nightly `dev`; fallback workflows on GitHub Actions |
| Fewer than 12 closed testers | Cannot get production access | Start recruiting testers during Phase 9 |
| ADK and Bitrise APIs evolve fast | Rework | Re-verify docs at Phase 9/10; isolate behind `AIService` and step bundles |
| Keystore key loss (factory reset/backup restore) | Profile unreadable | Profile excluded from backup; app detects and asks user to re-enter profile |
| Scope creep (13 phases) | Unfinished portfolio | Each phase is shippable on its own; Phase 3 already gives a usable app |

### 25.3 Open questions that materially affect architecture, security or data
None remaining. All revision-1 questions are answered, including the version code (Option B, section 17.4).

### 25.4 Assumptions (proceeding with these unless you object)
- A-01 Single user per device, no login, no cloud sync, no backend in v1.
- A-08 Online AI is opt-in and limited to the Gemini free tier; if the quota is exhausted the app shows the normal "AI unavailable" message.
- A-09 Testers for Play's closed test are people you invite; Claude cannot recruit them.
- A-02 minSdk 26, targetSdk latest required by Play at Phase 1.
- A-03 Western Arabic digits in all languages.
- A-04 Phone portrait first; tablets supported by adaptive layout but not optimized.
- A-05 Default master catalog ~70 items across 15 categories with translations prepared by me and flagged for native review.
- A-06 Home/office location entered manually or via a map pick; no background location, no location permission unless the user taps "Use current location".
- A-07 Notifications in v1 are limited to optional reminders on a checklist (exact design in Phase 3); no push notifications.

---

## Interview learning mode: the main decisions

**Offline-first with Room as source of truth**
- WHAT: every screen observes Room Flows; writes go through use cases into Room.
- WHY: works without network, one place where truth lives, no cache invalidation bugs.
- HOW: ViewModel → UseCase → Repository → DAO → Room invalidation → Flow → UiState.
- PRODUCTION USE: Google Keep-style apps, field-service apps, banking offline drafts.
- INTERVIEW ANSWER: "I made Room the single source of truth. The UI never holds its own copy of data; it observes Flows from Room, so a change from any source, whether the user, an import or the AI layer, shows up everywhere consistently. That gives me offline support for free and removes a whole class of stale-cache bugs."

**Master items vs checklist items (snapshot)**
- WHAT: master items are reusable templates with canonical keys; checklist items are copies.
- WHY: editing the catalog must never alter a list someone is shopping from.
- HOW: on "Add selected", the app copies resolved name, key, quantity and unit into `checklist_item`, keeping `master_item_id` only as a soft back-reference (`ON DELETE SET NULL`).
- PRODUCTION USE: order lines copying product price at purchase time in e-commerce.
- INTERVIEW ANSWER: "I separated the reusable catalog from the user's actual data. A master item is a template identified by a stable canonical key; when the user picks it, we snapshot it into the checklist. It's the same reason an order line stores the price at the time of purchase: history shouldn't change when the catalog does."

**Canonical keys for multilingual data**
- WHAT: one item `rice`, seven translations in a table, searchable with FTS.
- WHY: search, AI, import/export and future languages all agree on identity.
- HOW: Localizer resolves custom name → current locale → English → key.
- PRODUCTION USE: product catalogs in multi-region e-commerce.
- INTERVIEW ANSWER: "Translations are attributes of an item, not separate items. Each seeded item has a canonical key and per-locale names in a translation table indexed with FTS, so a Kannada user typing ಅಕ್ಕಿ and an English user typing rice hit the same record. When the AI parses 'add 5 kilo chawal', it returns the key 'rice', and the database never depends on the input language."

**AI as an optional, controlled layer**
- WHAT: `AIService` returns an `ActionPlan`; the app validates, asks for confirmation, and executes via use cases.
- WHY: AI can be wrong; business rules and user consent must stay in the app; AI must not be a single point of failure.
- HOW: AI → canonical command → validator → confirmation policy → use cases → Room, all in one transaction with Undo.
- PRODUCTION USE: assistants in banking or email apps that draft actions for user approval.
- INTERVIEW ANSWER: "The AI never touches the database. It can only propose structured commands from a fixed tool catalog. The app validates them with the same validators the UI uses, shows a review for anything bulk or destructive, and then runs them through normal use cases. The module graph enforces it too: the AI module has no dependency on the data module."

**Agentic AI without a backend (and where ADK fits)**
- WHAT: in the app, Gemini function calling through Firebase AI Logic, with the app running the tool loop; ADK agents run in CI for review, failure analysis and release notes.
- WHY: ADK needs a trusted process holding a model key. The app has no server by design, and keys can't ship in an APK, so the app uses client-side function calling protected by App Check, and ADK runs where a secret can live safely: CI.
- HOW: model proposes a tool call → app validates against domain rules → user confirms → use case runs → result returned to the model. In CI, an ADK agent reads a diff or log and writes a report.
- PRODUCTION USE: offline-first consumer apps that add optional cloud AI without operating servers; engineering teams using agents in pipelines.
- INTERVIEW ANSWER: "The core app is fully offline and has no backend. For AI I used Gemini through Firebase AI Logic with App Check, so there's no key in the APK, and I implemented the agent loop on the client with function calling: the model can only request tools from a fixed catalog, and the app validates and asks the user before anything changes. I used Google ADK where an agent framework actually belongs, in CI, for code review and failure analysis, so I got the agent experience without running a server."

**Git flow + Bitrise environments**
- WHAT: feature → develop (DEV) → release/* (STAGING) → main + tag (PRODUCTION with manual release).
- WHY: each environment has a clear source branch and promotion step; production needs a human.
- HOW: GitHub Actions runs every PR check for free; Bitrise (free Hobby plan) builds dev, staging and production from branch/tag triggers; signing secrets only in protected Bitrise storage; Play upload as draft.
- PRODUCTION USE: standard mobile release trains.
- INTERVIEW ANSWER: "Branches map one-to-one to environments. Feature PRs run fast checks in parallel; develop builds a dev variant; a release branch produces a signed staging build for QA; and only a version tag on main builds the production AAB, which goes to Play as a draft so a person makes the final call. Signing keys live only in Bitrise's protected storage and Google holds the app signing key through Play App Signing."

---

## File change report (Phase 0)
```text
Created:
- /mnt/project-files/checklist/phase0-architecture.md (revision 1)
Modified:
- /mnt/project-files/checklist/phase0-architecture.md (revision 2: your answers folded in; sections 0, 1.3, 11.5, 12, 13, 15, 16, 17, 19, 22, 23, 25 and interview notes)
Deleted:
- (none)
Reason:
Phase 0 design deliverable. No repository exists yet, so nothing was written to a codebase.
```

## Verification report (Phase 0)
```text
Build: NOT APPLICABLE (no code in Phase 0)
Unit Tests: NOT APPLICABLE
UI Tests: NOT APPLICABLE
Room Tests: NOT APPLICABLE
Lint: NOT APPLICABLE
Static Analysis: NOT APPLICABLE
Security: PASS (design level; no backend, no secrets in app or repo, AI opt-in)
Localization: NOT APPLICABLE (architecture defined in section 9)
Accessibility: NOT APPLICABLE (architecture defined in section 10)
Documentation: PASS (all 25 sections delivered, external facts cited)
Git: NOT APPLICABLE (no repository attached)
```

**STOP. Waiting for your approval of Phase 0 before starting Phase 1.**

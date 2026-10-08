# Database

The local database design. **Status: Planned (Phase 2, CL-112 Room data layer, CL-113 seed catalog).** The domain types it maps to (`Quantity`, IDs, `UnitDef`, repository interfaces) are in progress in `:domain` (CL-110). Full reasoning: [phase0-architecture.md](phase0-architecture.md) sections 5 to 8.

## Rules that never change

| Rule | Why | ADR |
|---|---|---|
| Room is the single source of truth; the UI observes DAO `Flow`s | Offline-first, no stale caches | 002 |
| Primary keys are `TEXT` UUID v4 | Import never reuses foreign IDs; ready for future sync | 003 |
| Times are `INTEGER` epoch milliseconds, UTC | Sortable, timezone-safe | |
| Quantities are `quantity_milli INTEGER` (amount × 1000) plus `unit_code` | Exact decimals, no floating-point error | 004 |
| Seeded names live in translation tables, searched with FTS4 | Offline search in any of the 7 languages | 005 |
| Checklist items are snapshots of master items | Editing the catalog never changes a saved list | 006 |
| `exportSchema = true`, schemas committed under `data/schemas/`, a tested migration for every version | Never lose user data | |
| **Never** call `fallbackToDestructiveMigration()` | Same | |

## Entity relationships

```text
checklist 1───* checklist_category *───1 category 1───* master_item
                        │                    │              │
                        1                    *              *
                        │            category_translation  master_item_translation
                        *
                 checklist_item *───0..1 master_item   (ON DELETE SET NULL)
                        │
                        *───1 unit_def

user_profile (one row, encrypted blob)     item_search_fts (FTS4, derived)     seed_meta
```

## Tables

| Table | Key columns | Notes |
|---|---|---|
| `checklist` | `id`, `title`, `description`, `created_at`, `updated_at`, `is_archived`, `archived_at` | Index `(is_archived, updated_at)` for Home |
| `category` | `id`, `canonical_key` (unique, seeded), `custom_name`, `icon_key`, `is_custom`, `is_hidden` | Check: `canonical_key` or `custom_name` is set |
| `category_translation` | PK `(canonical_key, locale)`, `name` | Seeded from assets |
| `master_item` | `id`, `category_id` (CASCADE), `canonical_key`, `custom_name`, `custom_name_locale`, `default_unit_code`, `is_custom`, `is_hidden`, `use_count` | Unique `(category_id, canonical_key)` |
| `master_item_translation` | PK `(canonical_key, locale)`, `name`, `aliases` | Aliases hold transliterations such as "akki", "chawal" |
| `unit_def` | `code` (PK), `allows_decimal`, `is_custom`, `custom_label`, `sort_order` | Built-in codes (`BuiltInUnits` in `:domain`): KG, GRAM, LITRE, MILLILITRE, DOZEN, PIECE, PACK, BOX, BOTTLE, PAIR, METER, NOS; custom: `CUSTOM_<uuid>`. Labels come from string resources, never from this table |
| `checklist_category` | `id`, `checklist_id` (CASCADE), `category_id` (RESTRICT), `display_order` | Unique `(checklist_id, category_id)`: a category appears once per checklist |
| `checklist_item` | `id`, `checklist_category_id` (CASCADE), `master_item_id` (SET NULL), `canonical_key`, `display_name`, `display_name_locale`, `quantity_milli`, `unit_code`, `notes`, `is_completed`, `completed_at`, `position` | Index `(checklist_category_id, position)` |
| `user_profile` | `id = 'me'`, `enc_payload` (AES-256-GCM blob), `key_alias`, `schema_version`, `updated_at` | Phase 5; see [security.md](security.md) |
| `item_search_fts` | FTS4 `unicode61`: `ref_type`, `ref_id`, `category_id`, `locale`, `text` | Rebuilt for a row in the same transaction as its source |
| `seed_meta` | `seed_version` | Drives catalog upgrades |

## Domain rules the data layer relies on

These are enforced in `:domain` (so they apply to UI, import and AI alike), not by the database:

- Title 1-100 characters after trimming; description up to 500; item name 1-80; notes up to 500.
- Quantity greater than 0 and at most 99,999, with at most 3 decimal places. Units with `allowsDecimal = false` (Piece, Nos, Pair, Dozen, Box, Pack, Bottle) reject decimals.
- Adding a master item already in the same section offers "Increase quantity instead?" rather than a duplicate.

## Ordering

`display_order` and `position` are sparse integers in steps of 1,000, so a move updates one row. When a gap runs out, the section is renumbered in one transaction. Reordering is always available through visible move up/down buttons (see [accessibility.md](accessibility.md)).

## Display names and the snapshot rule

A seeded item's identity is its `canonical_key` (`rice`), never its text. The `Localizer` resolves a name in this order:

```text
custom_name → translation[app locale] → translation["en"] → humanized key ("cooking_oil" → "Cooking oil")
```

When a master item is added to a checklist, the resolved `display_name`, its locale, `canonical_key`, quantity and unit are **copied** into `checklist_item`. `master_item_id` stays only as a back-reference for ranking and AI. Renaming or deleting the master item, or switching the app language, never rewrites saved items.

Custom items: "Create new item" inserts a `checklist_item` with `master_item_id = NULL`. With "Save to suggestions" on, it also inserts a `master_item` with `is_custom = 1` and `canonical_key = "custom:<uuid>"` in the same transaction.

## Seed catalog (Planned, Phase 2, CL-113)

- Assets: `data/src/main/assets/seed/catalog.json` (units, categories, items with `canonicalKey`, `categoryKey`, `defaultUnit`) and `seed/i18n/<locale>.json` (name and aliases per key) for all 7 languages.
- `SeedLoader` runs in Room's `onCreate` and again when the asset's `seed_version` is higher than `seed_meta`. It upserts by canonical key and never touches rows the user renamed or hid.
- Initial content: about 70 items across Groceries, Vegetables, Fruits, Clothing, Travel, Documents and Exam, plus empty categories Gifts, Pooja Items, Decorations, Medicines, Toiletries, Electronics, Stationery and Other.
- Translations are machine-assisted and need native-speaker review before release (CL-107).

## Search

Queries are normalized (NFKC, lowercase, trimmed) and prefix-matched in FTS over the current locale, English, aliases and custom names. Ranking: exact, then prefix, then contains; then `use_count` descending; then alphabetical in the current locale. Under 2 characters, the category's suggestions are shown by `use_count`. Target: under 100 ms for 5,000 items.

## Transactions

Run in `RoomDatabase.withTransaction {}` from repository methods: create a checklist with categories, add selected items (inserts, `use_count` bump, FTS update), duplicate a checklist, import, and AI plan execution. Any failure rolls back the whole operation.

## Migrations

1. Change the entity and bump the database version.
2. Build: Room exports the new schema JSON to `data/schemas/`. Commit it.
3. Prefer `@AutoMigration`; write a manual `Migration` when Room cannot infer it (renames, data moves).
4. Add a `MigrationTestHelper` test for N → N+1, and keep the 1 → latest test passing.
5. Update this page.

## Category deletion

A category used by any checklist cannot be deleted (`ON DELETE RESTRICT` on `checklist_category`); the UI offers **Hide from suggestions** instead (`is_hidden = 1`). An unused custom category can be deleted after confirmation, which cascades its custom master items.

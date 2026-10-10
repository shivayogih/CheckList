# Seed catalog (CL-113, CL-310)

> **seedVersion 2 (CL-310)** replaces the starter list with the India master catalogue: 31 categories and 563 items in 7 languages. See [India master catalogue](#india-master-catalogue-seedversion-2) for the source, id scheme and upgrade rules. The older sections below still describe the file format.

The default master catalog (units, categories and items) ships as JSON assets in `:data` and is loaded into Room by `SeedLoader` (architecture section 6.4). Display text lives only in the per-locale files; the catalog itself is language-neutral.

> **Translations are machine-assisted drafts.** Every non-English name and alias needs review by a native speaker before release (issue **CL-107**). Uncertain entries are listed at the end of this document.

## Files

```text
data/src/main/assets/seed/
├── catalog.json        units, categories, items (keys, icons, default units)
└── i18n/
    ├── en.json  kn.json  hi.json  ta.json  te.json  mr.json  ml.json
tools/seed/validate_seed.py   validator, run in CI ("Validate seed catalog" step)
```

### `catalog.json`

```json
{
  "seedVersion": 1,
  "units": [{ "code": "KG", "allowsDecimal": true, "sortOrder": 10 }],
  "categories": [{ "key": "groceries", "icon": "🛒", "sortOrder": 10 }],
  "items": [{ "key": "rice", "category": "groceries", "defaultUnit": "KG" }]
}
```

| Field | Rule |
|---|---|
| `seedVersion` | Positive integer. Bump it whenever any seed file changes so existing installs re-run the upsert. |
| `units` | Exactly the 12 `BuiltInUnits` (Units.kt), same `allowsDecimal`, `sortOrder` and order. |
| `categories[].key` | Canonical key, lowercase snake_case, unique. |
| `categories[].icon` | One emoji. Prefer emoji without a variation selector (`🎒`, not `✈️`). |
| `categories[].sortOrder` | Steps of 10, in display order. |
| `items[].key` | Canonical key, lowercase snake_case, unique across the **whole** catalog. |
| `items[].category` | A key from `categories`. Each item belongs to exactly one category. |
| `items[].defaultUnit` | A `BuiltInUnits` code, or `null` when no quantity makes sense (documents, tickets). Always present. |

Items are listed grouped by category in display order; the loader may use list position as the default order.

### `i18n/<locale>.json`

```json
{
  "locale": "kn",
  "categories": { "groceries": "ದಿನಸಿ" },
  "items": { "rice": { "name": "ಅಕ್ಕಿ", "aliases": ["akki"] } }
}
```

- `locale` equals the file name and is a tag in `SupportedLanguages`.
- Every category key and item key in `catalog.json` has an entry, with no extras.
- `name`: the everyday word a grandparent would say, non-empty, trimmed, no double spaces. Title Case in English.
- `aliases`: always present (may be empty). Extra search terms for this locale: Latin-script transliterations (`akki`, `chawal`), the common English loan word people type (`tomato`), shorter native forms (`ಎಣ್ಣೆ` for cooking oil) and genuine synonyms (`brinjal` → `eggplant`). No brand names, no alias equal to the name, no duplicates (case-insensitive).

## Key rules

1. Keys are lowercase snake_case English: `cooking_oil`, `hall_ticket`, `t_shirt`. They never change once released (they are the identity stored in checklists, exports and analytics); fix wording in the i18n files instead.
2. Item keys are unique across the whole catalog. When a concept appears in more than one category, the category where it most naturally belongs keeps the plain key and the other occurrence is prefixed with its category key: `passport` (documents) and `travel_passport` (travel).
3. The same prefix is used when an item would share its key with a category: `travel_medicines`, `travel_toiletries`.
4. Plural or singular follows how people write the list: `books`, `socks`, but `rice`, `soap`.
5. User-created items use `custom:<uuid>` keys and never appear here.

## Adding an item

1. Add `{ "key", "category", "defaultUnit" }` to `catalog.json` under its category.
2. Add the key to **all seven** `i18n/*.json` files (name, aliases).
3. Bump `seedVersion`.
4. Run `python3 tools/seed/validate_seed.py` and open a CL-107 review task for the new translations.

Removing or renaming a released key needs a data migration; prefer hiding the item instead.

## Adding a language

1. Add the `AppLanguage` to `SupportedLanguages.kt` (and `values-xx/strings.xml`, architecture 9.4).
2. Copy `i18n/en.json` to `i18n/<tag>.json`, set `locale`, translate every category and item.
3. Bump `seedVersion` so existing installs load the new translations.
4. Run the validator: it reads the locale list from `SupportedLanguages.kt`, so a missing file fails CI.

## Validation

`python3 tools/seed/validate_seed.py` (Python 3 standard library, no install) checks: JSON parses; `seedVersion` is a positive integer; units match `BuiltInUnits`; keys are snake_case and unique; every item's category exists; every `defaultUnit` is a `BuiltInUnits` code or `null`; every locale file has a name for every category and item and nothing extra; names and aliases are non-empty and trimmed. It exits non-zero with one line per problem.

## India master catalogue (seedVersion 2)

**Source**: `tools/seed/src_data/master_catalog_india.json` (schema 1.0.0, market India), plus the six translation files in `tools/seed/src_data/translations/`. `python3 tools/seed/build_india_catalog.py` generates `catalog.json`, `i18n/*.json`, `docs/catalog-translation-review.md` and `docs/catalog-templates-backlog.md`; CI runs it with `--check`, so the generated files can never drift from the source. Edit the source, regenerate, bump `SEED_VERSION` in the script.

**Counts**: 31 categories (CAT001..CAT031), 563 catalogue items (ITM0001..ITM0563, all active) and 47 older items that have no catalogue equivalent (610 seed items; 40 older items were merged into catalogue items and keep their keys), 32 categories (31 from the catalogue plus the kept `pooja_items`), 13 units, 7 languages (en, kn, hi, ta, te, mr, ml). The catalogue's 68 tags and 15 checklist templates are not separate tables (see below).

### Id scheme

| Catalogue | Seed `key` (stable id) | Notes |
|---|---|---|
| `CATnnn` | `catnnn` (e.g. `cat004`), or the **old key** for the 12 categories that existed in seedVersion 1 (`groceries`, `vegetables`, `fruits`, `clothing`, `travel`, `documents`, `exam`, `medicines`, `toiletries`, `electronics`, `stationery`, `other`) | `catalogId` records the `CATnnn`. Old icons are kept. |
| `ITMnnnn` | `itmnnnn` (e.g. `itm0003`), or the **old key** when the item is the same thing as a seedVersion 1 item (`rice`, `potato`, `milk`...) | `catalogId` records the `ITMnnnn`. Matching is by English name (see below); `ITEM_SYNONYMS` in the build script can force a merge. |
| old item with no catalogue equivalent (`dal`, `spices`, `kumkum`...) | unchanged key | Stays in the catalogue, in its mapped category. Their translations are the seedVersion 1 ones. |
| old item with the **same English name** as a catalogue item (`milk`, `onion`, `banana`...) | unchanged key | Merged: same row, so there is never a second "Milk" (name search and the AI parser need unique names). Same-category matches win, then any category (`milk` moves to Dairy & Eggs). The old default unit is kept (sugar stays kg). Synonyms are not merged: "Rice" and "Raw rice" are separate items. |
| old categories `gifts`, `decorations` | unchanged key, listed in `retiredCategories` | Their items moved to Events & Celebrations (`cat029`); the category row is hidden once empty and never deleted. |
| old category `pooja_items` | unchanged key, no `catalogId` | The master catalogue has no pooja category, so it stays as the 32nd category with its items. |

Keys never change once released: `canonical_key` is what checklists, exports and search refer to.

### Field mapping

| Catalogue field | Stored as |
|---|---|
| `category_id`, `item_id` | `catalogId` (informational) and the key above |
| `name` / `item_name` | English `i18n/en.json`; the other six from the translation files |
| `subcategory` | `subcategory` in `catalog.json`; indexed as an English search keyword for every language (the model has no sub-grouping column) |
| `tags` | `tags` in `catalog.json`; indexed as English search keywords (underscores become spaces). No tag table: tags only help search |
| `default_unit` | `defaultUnit` via the unit map: kg KG, g GRAM, L LITRE, ml MILLILITRE, dozen DOZEN, pc PIECE, pack PACK, box BOX, bottle BOTTLE, pair PAIR, m METER, unit NOS, **bunch BUNCH (new)**, task = no unit (`null`, a task has no quantity) |
| `allowed_units` | Not stored (no per-item allowed-unit model; the unit picker offers all units). Every `default_unit` is in `allowed_units` in the source |
| `notes`, `is_user_editable` | Not stored (empty or always true) |
| `is_active` | Items with `is_active: false` are not seeded. All 563 are active |
| `units[]` | Only the unit used as a default and missing from the app was added: `BUNCH`. The other catalogue units (mg, can, jar, bag, roll, sheet, set, serving, hour, min, km, cm) are deferred; users can create custom units. Units carry no conversion factors in the app (quantities are milli-units of one unit) |
| `checklist_templates` | No templates feature: kept as backlog CL-319 in [catalog-templates-backlog.md](catalog-templates-backlog.md) |
| `measurement_guidance` | Not stored; guidance for the unit and quantity UX |

English item names are added as aliases in the other six languages, so typing "basmati" finds the item in any language.

### Upgrade rules (installed apps)

`SeedLoader` runs when `seedVersion` in the asset is higher than `seed_meta`. It is versioned, idempotent and non-destructive:

1. Units, categories and items are **upserted by key**. A found row is updated in place (same internal id), so master item references in checklists stay valid.
2. Rows the user renamed (`custom_name`) or hid (`is_hidden`) are never touched. User-created categories, items and units are never touched.
3. Nothing is deleted. Checklist items are snapshots (`display_name`, quantity, unit); the upgrade does not write to `checklist*` tables.
4. Retired categories are hidden only when empty (no seed or custom items).
5. Translations are seed-owned and refreshed; the search index is rebuilt in the same transaction. Re-running the same version, or clearing `seed_meta` and re-running, gives identical data.
6. The work runs in one transaction inside Room's open callback on its background executor, never on the main thread (the existing warm-up). Queries stay per category (at most about 60 rows) or search with a limit of 50, so no paging is needed.

Tests: `IndiaCatalogSeedTest` (upgrade from the real seedVersion 1 files with user data kept, idempotency, Kannada, English and tag search, all seven languages present or flagged) and `SeedLoaderTest`.

### Translation review process

All non-English names are machine-assisted drafts. `docs/catalog-translation-review.md` lists the names the translators were least sure of, per language, plus any `fallback` rows (English shown for lack of a translation). A reviewer edits `tools/seed/src_data/translations/<tag>.json`, removes the item from its `review` list, regenerates and bumps the seed version. An item whose name has no native script (ORS, LED) must be in the review list: a test enforces it.

## Contents of seedVersion 1 (superseded)

15 categories, 87 items: Groceries 10, Vegetables 9, Fruits 6, Clothing 7, Travel 8, Documents 6, Exam 8, Gifts 2, Pooja Items 7, Decorations 3, Medicines 6, Toiletries 6, Electronics 4, Stationery 5, Other 0.

## Translations needing native review (CL-107)

| Key | Locale | Draft | Doubt |
|---|---|---|---|
| `toiletries`, `travel_toiletries` | all | ಸ್ನಾನದ ಸಾಮಗ್ರಿ, नहाने-धोने का सामान, குளியல் பொருட்கள், స్నానపు సామగ్రి, अंघोळीचे साहित्य, ശുചിത്വ സാധനങ്ങൾ | No common everyday word; "bath items" used. |
| `tea`, `coffee` | all | Powder forms (ಟೀ ಪುಡಿ, चाय पत्ती, டீ தூள்...) | Shopping sense chosen over the drink. |
| `spices` | ml, ta, te | മസാലകൾ, மசாலா பொருட்கள், మసాలా దినుసులు | Natural collective word? |
| `spinach` | ta, ml | பசலைக் கீரை, ചീര | ചീര is usually amaranth; Malayalam may need "പാലക്". |
| `cooking_oil` | hi, mr | खाने का तेल, खाद्यतेल | People usually just say तेल. |
| `hair_oil` | kn, ml | ಕೂದಲಿನ ಎಣ್ಣೆ, ഹെയർ ഓയിൽ | Everyday phrasing. |
| `rangoli_colours` | ta, te | கோலப் பொடி, ముగ్గు రంగులు | Kolam powder is often white, not coloured. |
| `dress` | ta, ml | உடை, ഉടുപ്പ് | Generic "garment"; may want ட்ரெஸ் / ഫ്രോക്ക്. |
| `shoes` | ta | காலணிகள் | Formal; ஷூ may be more common. |
| `pain_balm` | ta, te | தைலம், నొప్పి బామ్ | |
| `eraser` | mr | खोडरबर | |
| `cotton_wicks` | hi, ta | रुई की बत्ती, திரி | |
| `decorative_lights` | all | ಅಲಂಕಾರಿಕ ದೀಪಗಳು, सजावटी लाइट, அலங்கார விளக்குகள், అలంకరణ దీపాలు, सजावटी दिवे, അലങ്കാര വിളക്കുകൾ | "serial lights" is the common Indian term; native form may be preferred. |
| `ors` | all | "ORS" + powder/solution word | Latin acronym kept intentionally. |
| `hall_ticket` | hi, mr | प्रवेश पत्र, प्रवेशपत्र | "हॉल टिकट" is also widely used. |

# Seed catalog (CL-113)

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

## Current contents (seedVersion 1)

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

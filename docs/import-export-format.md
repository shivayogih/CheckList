# Import/export format

The JSON file format CheckList uses to move checklists between phones and for backups the user controls. **Status: In progress (Phase 6, CL-160 to CL-169).** This page is the specification the implementation and its golden-file tests must follow. Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 20.

Where the code lives: the pipeline (validation, matching, use cases `ExportChecklistsUseCase`, `PreviewImportUseCase`, `ApplyImportUseCase`) is in `:domain` under `domain/transfer`; the JSON codec and the Room transaction are in `:data` under `data/importexport`; the Storage Access Framework, Sharesheet and PDF helpers are in `:app` under `transfer/`.

## Principles

- **Database IDs are never exported.** Every object has a file-local `ref` (`k1`, `s1`, `i1`). Import always creates fresh UUIDs.
- **Import always adds; it never overwrites.** Name clashes are resolved by renaming ("Goa Trip (2)").
- **The profile is excluded** unless the user explicitly ticks it, and then they see a warning.
- **Quantities are strings** ("2.5") so decimals stay exact.
- **Seeded things are referenced by canonical key**, so a file made in Kannada imports correctly on a phone set to Hindi.
- Import runs the same domain validators as the UI. Any failure rolls back the whole import.

## Example

```json
{
  "formatVersion": 1,
  "schemaVersion": 1,
  "metadata": {
    "app": "CheckList",
    "appVersion": "1.0.0",
    "exportedAt": "2026-10-08T04:00:00Z",
    "locale": "kn",
    "includesProfile": false
  },
  "units": [
    { "ref": "u1", "code": "CUSTOM", "label": "Bundle", "allowsDecimal": false }
  ],
  "categories": [
    { "ref": "c1", "canonicalKey": "groceries" },
    { "ref": "c2", "customName": "Pooja Items", "icon": "temple" }
  ],
  "checklists": [
    {
      "ref": "k1", "title": "Diwali Shopping", "description": "For the festival", "archived": false,
      "createdAt": "2026-10-01T10:00:00Z",
      "sections": [ { "ref": "s1", "categoryRef": "c1", "order": 0 } ]
    }
  ],
  "items": [
    {
      "ref": "i1", "sectionRef": "s1", "canonicalKey": "rice",
      "displayName": "ಅಕ್ಕಿ", "displayNameLocale": "kn",
      "quantity": "5", "unit": "KG", "notes": null,
      "completed": false, "position": 0
    }
  ],
  "profile": null
}
```

## Fields

### Envelope

| Field | Type | Required | Meaning |
|---|---|---|---|
| `formatVersion` | integer | yes | Shape of the envelope. Currently `1`. |
| `schemaVersion` | integer | yes | Shape of the content model. Currently `1`. |
| `metadata` | object | yes | Informational; not trusted for validation. |
| `units` | array | yes (may be empty) | Custom units used by items. Built-in units are referenced by code and not listed. |
| `categories` | array | yes | Categories used by sections. |
| `checklists` | array | yes | Checklists with their sections. |
| `items` | array | yes | Checklist items, linked to sections by `sectionRef`. |
| `profile` | object or null | yes | `null` unless the user chose to include the profile. |

### Metadata

`app` (always "CheckList"), `appVersion`, `exportedAt` (ISO-8601 UTC), `locale` (app language at export), `includesProfile` (boolean).

### Category

Exactly one of `canonicalKey` (seeded category) or `customName` (user category). `icon` is optional.

### Checklist

`ref`, `title` (1-100 chars), `description` (up to 500, optional), `archived`, `createdAt`, `sections[]` with `ref`, `categoryRef` and `order`. A category appears at most once per checklist.

### Item

| Field | Rule |
|---|---|
| `sectionRef` | Must match a section in this file |
| `canonicalKey` | Optional; links the item to a seeded master item for ranking and AI |
| `displayName` | 1-80 chars; the snapshot name, imported as is |
| `displayNameLocale` | BCP-47 language of `displayName` |
| `quantity` | Optional string; greater than 0, at most 99,999, at most 3 decimal places |
| `unit` | Built-in code (`KG`, `GRAM`, `LITRE`, `MILLILITRE`, `DOZEN`, `PIECE`, `PACK`, `BOX`, `BOTTLE`, `PAIR`, `METER`, `NOS`) or a `ref` from `units`; whole-number units reject decimals |
| `notes` | Up to 500 chars, or null |
| `completed`, `position` | Completion state and order within the section |

### Rules the importer adds

These complete the field tables above; the code and its tests follow them.

- **Refs** are 1-64 characters of letters, digits, `_`, `-` and `.`, unique per kind (sections unique across all checklists). A `units` ref may not equal a built-in code, so an item's `unit` is never ambiguous. `units[].code` must be `"CUSTOM"`.
- **Optional fields** may be left out: `description`, `createdAt`, `icon`, `canonicalKey`, `quantity`, `unit`, `notes` (null), `archived`, `completed`, `includesProfile` (false), `position` (0), `profile` (null). Everything else is required. Unknown keys, comments, trailing commas, wrong types and non-UTF-8 bytes make the file malformed (a leading byte order mark is accepted).
- **`order` and `position`** are relative: sections and items are sorted by them (ties keep file order) and get fresh sparse values on import. They must not be negative. An export writes 0, 1, 2...
- **`createdAt`**, if present, must be ISO-8601; it is informational. Imported checklists are created at import time and sorted as the most recent.
- **`canonicalKey`** is lower-case letters, digits, `_` and `-`, optionally followed by `:` and an id (`custom:<uuid>`), at most 100 characters. **`displayNameLocale`** is BCP 47 shaped (`kn`, `en-IN`), at most 35 characters. **`icon`** is at most 32 UTF-16 units.
- **Text cleaning:** control characters (Unicode Cc) and bidirectional overrides/isolates (U+202A-U+202E, U+2066-U+2069) are removed from every text before validation; line breaks are kept only in `description` and `notes`. ZWJ/ZWNJ and other format characters are kept because Indic spelling and emoji need them. Lengths are then checked in code points after trimming, exactly like the UI.
- **Profile:** exports always write `"profile": null` until the opt-in profile export ships (Phase 5). A file with a non-null `profile` still imports; the profile is skipped and the preview says so.
- **Empty files:** a valid file without any checklist is refused as "nothing to import".

### Matching on import (never duplicate)

| File says | Maps to | Otherwise |
|---|---|---|
| `canonicalKey` | The seeded category with that key | A custom category named after the key ("festival_lights" becomes "Festival lights"), for files from a newer catalog |
| `customName` | An existing custom category with that name, ignoring case and Unicode width (NFKC); else any category whose name in the app language matches (so "Pooja Items" maps to the seeded Pooja Items) | One new custom category, however many file categories carry that name |
| Custom unit `label` | An existing custom unit with that label, ignoring case | One new custom unit per label |
| Built-in unit code | The built-in unit | Refused as an unknown unit if this database lacks it |
| `title` already used (ignoring case), or repeated in the file | "Goa Trip (2)", "Goa Trip (3)"... shortened to stay within 100 characters | Imported unchanged |

Only categories and units that a section or item uses are created. If two sections of one checklist end up on the same category, the file is refused. When an existing unit matched by label counts whole things but the file has a fraction for it, the file is refused rather than the amount changed. Matching runs again when the user confirms, so anything created since the preview is reused, and renames shown in the summary are the ones actually applied.

### Limits

| Limit | Value | When it is checked |
|---|---|---|
| File size | 10 MB (10,485,760 bytes) | Reported size before opening, then actual bytes while reading |
| Checklists / items | 500 / 20,000 | While parsing (the array stops being read) |
| Categories / custom units | 1,000 / 500 | While parsing |
| Sections | 200 per checklist, 20,000 in total | While parsing / after parsing |
| Any single string | 2,000 UTF-16 units | While parsing; the field limits above apply afterwards |
| Reported problems | First 50, with the total count | Validation |

An export that would break a limit is refused instead of written, so every exported file can be imported again.

- The importer supports every `formatVersion` and `schemaVersion` up to the app's current one, upgrading older files through chained `JsonMigrator`s.
- A newer version is rejected with "Update the app to import this file".
- Bump `schemaVersion` whenever the content model changes, add a migrator and a golden file for the old version, and update this page.

## Import pipeline

```text
Pick a file (ACTION_OPEN_DOCUMENT; no storage permission)
→ size check: reject files over 10 MB
→ streaming parse with limits: ≤ 500 checklists, ≤ 20,000 items, string length caps
→ version check and upgrade
→ structural and domain validation (same validators as the UI)
→ reference integrity: every sectionRef, categoryRef and unit ref resolves; no duplicates
→ resolve categories: canonicalKey → existing seeded category;
                      customName → existing custom category (case-insensitive) or a new one
→ preview: "3 checklists, 42 items, 2 new categories", plus renames for name clashes
→ user confirms → one Room transaction with fresh UUIDs → summary
```

## Export

`ACTION_CREATE_DOCUMENT` (the user picks where to save). All checklists or a selection. The profile is excluded unless ticked. The same privacy rule applies to PDF export.

## PDF export (related, Phase 6)

Generated on the device with `android.graphics.pdf.PdfDocument` and `StaticLayout` (correct shaping for Indic scripts), A4, large font, checkbox glyphs, category headings. Options: include completed items, include my name (off by default). Shared through the Android Sharesheet with a `FileProvider` URI; no app-specific APIs.

As built: checkboxes are drawn as shapes (not font glyphs, which not every device font has), headings stay with their first item, and every item is laid out with the locale of its name so locale-specific glyph forms (Marathi versus Hindi) are right. Shaping limits: glyphs come from the device's fonts (a device without a font for a script shows boxes); the PDF stores shaped glyphs, so copying or searching Indic text in some PDF viewers can return wrong characters; before Android 9 lines get extra padding instead of fallback-font line heights; colour emoji may be dropped. Share files are written to `cacheDir/exports` and deleted once older than an hour, whenever a new one is made (instead of "on next launch", which would need startup work in the application class).

## Test fixtures

Golden files live with the `:data` tests: valid files (every field, minimal), malformed JSON, oversized files, a future version, broken and duplicate references, hostile strings, and a round trip (export → import → export gives the same content apart from refs and timestamps).

As built: `data/src/test/resources/transfer/` holds `valid-full.json` (all seven scripts), `valid-minimal.json`, `future-version.json`, `broken-refs.json` and `hostile-strings.json`; malformed, oversized and over-limit inputs are generated in `JsonTransferCodecTest`. `RoomImportExportTest` runs export → import → export between two Room databases, the rename and no-duplicate rules, and a failure injected halfway through an import that must leave every table unchanged. Domain rules are covered by `ImportValidatorTest` and `TransferUseCasesTest`.

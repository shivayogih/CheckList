# Import/export format

The JSON file format CheckList uses to move checklists between phones and for backups the user controls. **Status: Planned (Phase 6, CL-160 to CL-166).** This page is the specification the implementation and its golden-file tests must follow. Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 20.

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

## Versions

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

## Test fixtures

Golden files live with the `:data` tests: valid files (every field, minimal), malformed JSON, oversized files, a future version, broken and duplicate references, hostile strings, and a round trip (export → import → export gives the same content apart from refs and timestamps).

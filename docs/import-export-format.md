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
| `quantity` | Optional string; greater than 0, at most 99,999, at most 3 decimal places; digits and one decimal separator only (no sign, exponent such as `1e5`, letters or grouping; CL-280) |
| `unit` | Built-in code (`KG`, `GRAM`, `LITRE`, `MILLILITRE`, `DOZEN`, `PIECE`, `PACK`, `BOX`, `BOTTLE`, `PAIR`, `METER`, `NOS`) or a `ref` from `units`; whole-number units reject decimals |
| `notes` | Up to 500 chars, or null |
| `completed`, `position` | Completion state and order within the section |

### Photos (formatVersion 2, zip archives only)

`items[].photos` is an optional array of at most 3 objects, in display order:

| Field | Rule |
|---|---|
| `ref` | Unique ref (same rules as other refs) |
| `file` | `photos/<name>.<ext>`, exactly one directory level; `<name>` is 1-64 characters of letters, digits, `_`, `-`, `.` and does not start with `.` or `-`; `<ext>` is `jpg`, `jpeg`, `png` or `webp`; must match an entry of the archive (ignoring case) |
| `caption` | Optional, up to 80 characters, one line |

Two files exist:

- **Plain JSON (`application/json`), `formatVersion` 1.** Exactly what earlier versions wrote. Written when the user does not tick "Include photos". Photos are not part of it. A version 1 file that carries `items[].photos` is refused, because photos only travel inside an archive.
- **Photo archive (`application/zip`), `formatVersion` 2.** A zip with `checklists.json` (the same document, `formatVersion` 2, with `items[].photos`) and `photos/p1.jpg`, `photos/p2.jpg`... `schemaVersion` stays 1: the content model did not change shape, only gained an optional field. A zip is recognised by its first bytes, not its name.

The importer reads both versions. `formatVersion` above 2 is refused as "from a newer app version". Version 1 files keep importing through the migrator chain (`older versions are upgraded through migrators`).

**Archive rules (all checked before anything is written):**

- Streamed, never extracted to the shared storage or trusted by header sizes: sizes are counted from the bytes actually read.
- At most 2,000 entries, each photo at most 5 MB, `checklists.json` at most 10 MB, the whole archive at most 100 MB.
- Allowed entries: `checklists.json` and `photos/<name>.<ext>` only. Directories, other file types, absolute paths, `..`, `.` segments, backslashes, drive letters, control characters, a second directory level and duplicate names (ignoring case) are refused.
- The first bytes of every photo must match its extension (JPEG, PNG or WebP magic numbers).
- At most 3 photos per item. A `file` that is missing from the archive, appears twice or is not a plain `photos/<name>.<ext>` path is refused; an archive photo that no item refers to is never imported.
- Every photo is decoded and re-encoded through the same pipeline as the camera and the gallery (JPEG quality 80, long edge 1600 px, EXIF dropped). An undecodable image does not fail the import: it is left out and counted ("Photos left out: N").
- Any failure rolls back the whole import and removes every file already written.

Refusals are typed (`ImportRejection.UnsafeArchive(ArchiveProblem)`, `LimitExceeded`) and shown as one friendly message; the tests live in `PhotoArchiveTest`, `PhotoTransferTest` and `RoomPhotoTransferTest`.

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
| JSON nesting | 32 levels of `{`/`[` (the format uses 5) | Before parsing, so hostile nesting cannot overflow the parser's stack |
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

`ACTION_CREATE_DOCUMENT` (the user picks where to save). All checklists or a selection. The profile is excluded unless ticked. "Include photos" (off by default) in the Settings export dialog writes a `.zip` photo archive instead of a `.json` file. The same privacy rule applies to PDF export.

## PDF export (related, Phase 6)

Generated on the device with `android.graphics.pdf.PdfDocument` and `StaticLayout` (correct shaping for Indic scripts), A4, large font, checkbox glyphs, category headings. Options: include completed items, include my name (off by default), include photos (off by default, asked only when the checklist has photos): up to 3 thumbnails of about 60 pt under each item, drawn from the 320 px thumbnails. Each photo adds an embedded image, so the file grows with the number of photos; the size was not measured on a device yet (the Robolectric `PdfDocument` is a stub that writes no image data) and stays on the follow-up list. Shared through the Android Sharesheet with a `FileProvider` URI; no app-specific APIs.

As built: checkboxes are drawn as shapes (not font glyphs, which not every device font has), headings stay with their first item, and every item is laid out with the locale of its name so locale-specific glyph forms (Marathi versus Hindi) are right. Shaping limits: glyphs come from the device's fonts (a device without a font for a script shows boxes); the PDF stores shaped glyphs, so copying or searching Indic text in some PDF viewers can return wrong characters; before Android 9 lines get extra padding instead of fallback-font line heights; colour emoji may be dropped. Share files are written to `cacheDir/exports` and deleted once older than an hour, whenever a new one is made (instead of "on next launch", which would need startup work in the application class).

Branding (CL-167). Every page, in this drawing order:

| Part | What is drawn | Where |
|---|---|---|
| Watermark | The app name "CheckList", bold, at about 9 % opacity (alpha 23 of 255), rotated along the page diagonal (bottom left to top right). It is sized to at most 60 % of the diagonal and never runs off the page. | Page centre, drawn first so all content sits on top of it and printed text stays readable |
| Header band | A drawn copy of the launcher icon (white tick on a rounded square in `ic_launcher_background`), the app name in the brand colour, and the checklist title as a running head (one line, ellipsised). A thin rule underneath. | 28 pt band at the top margin |
| Content | Title block, sections and items, paginated as before. | Between the header and footer bands, with a 14 pt gap on each side; blocks never run into either band |
| Last block | After the last item, so on the last page: the import hint `pdf_import_hint` ("To use this list in the app, ask the sender for the CheckList file (.json) and import it."). With a store link it sits in a "Get CheckList" box with a QR code of the store URL (84 pt, about 3 cm, error correction M, 4-module quiet zone on white), `pdf_promo_scan` and the URL in text. | Content area, kept clear of the footer like any other block |
| Footer band | A thin rule, then one row of three one-line columns: the tagline `pdf_powered_by` ("Powered by CheckList"), the export time `pdf_exported_at`, and the page number `pdf_page_number`. Only when a store link is set, a second row: `pdf_get_app` ("Get the app on Google Play") and the store URL, which is never cut (the label is shortened first). | 18 pt band at the bottom margin, 33 pt with the store row; the content area shrinks to match |

The app name comes from `app_name`, which is `translatable="false"`, so it reads "CheckList" in every language; only the words around it ("Powered by", "Exported") are translated. The export time is the injected domain `Clock`, formatted with `DateTimeFormatter.ofLocalizedDateTime(MEDIUM, SHORT)` in the app language and the device time zone. The geometry (bands, placement, watermark size and angle) is in the pure `PdfPageLayout` object and is covered by JVM unit tests in `TransferHelpersTest`.

Store link (CL-168). The Google Play URL is set in exactly one place: `val playStoreUrl` near the top of `app/build.gradle.kts`, which feeds `BuildConfig.PLAY_STORE_URL` for every flavor. It is empty until the listing is live, and `StoreLink.of` treats an empty or non-https value as "no link". With no link the PDF has no Google Play row and no QR code (the hint still prints), and shared files carry no store line. Once set, use the production listing (`https://play.google.com/store/apps/details?id=com.dataloom.checklist`) even for dev and staging builds. `PdfDocument` cannot add clickable link annotations, so the printed URL and the QR code are what make the link usable. The QR code is encoded with zxing `core` 3.5.4 (Apache-2.0) and drawn as vector rectangles, one per run of dark modules in a row.

The PDF itself cannot be imported; importing from a CheckList PDF is in the backlog as CL-169.

Sharing the .json file (CL-168). `TransferViewModel.shareExport` writes the export to the share folder and sends it through the Sharesheet with `EXTRA_SUBJECT` (`share_export_subject`) and `EXTRA_TEXT` built by `ExportShareText.build`: a line saying it is a CheckList file (`share_export_intro`), the store line (`share_export_get_app`) only when a store link is set, then `share_export_steps_title` and four numbered steps: install CheckList, open the app → Settings → Import, choose this file, review the preview and confirm.

## Test fixtures

Golden files live with the `:data` tests: valid files (every field, minimal), malformed JSON, oversized files, a future version, broken and duplicate references, hostile strings, and a round trip (export → import → export gives the same content apart from refs and timestamps).

As built: `data/src/test/resources/transfer/` holds `valid-full.json` (all seven scripts), `valid-minimal.json`, `future-version.json`, `broken-refs.json` and `hostile-strings.json`; malformed, oversized and over-limit inputs are generated in `JsonTransferCodecTest`. `RoomImportExportTest` runs export → import → export between two Room databases, the rename and no-duplicate rules, and a failure injected halfway through an import that must leave every table unchanged. Domain rules are covered by `ImportValidatorTest` and `TransferUseCasesTest`.

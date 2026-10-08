# Input validation (CL-280)

Every value a person types, pastes, imports or dictates is validated by one set of pure-Kotlin rules in
`:domain` (`com.dataloom.checklist.domain.validation`). The UI never invents its own rule: it runs the same
validators while the user types, shows the translated message under the field and disables the primary
action while the form would be refused. The use cases run the validators again when saving, so the import
and AI paths cannot bypass them.

## Layers

| Layer | What it does | Code |
|---|---|---|
| Typing filter (`:domain`, pure) | Removes characters that are never allowed; keeps what the user typed otherwise (spaces stay so a whitespace-only entry can be reported). Applied on every change event, so paste is covered. | `InputText.forField/forTyping/forEmail`, `QuantityInput.sanitize`, `PhoneInput.sanitize` |
| Validator (`:domain`, pure) | Trims, normalizes, rejects blank, too long, wrong shape. Returns typed `ValidationError` codes, never text. | `ChecklistValidator`, `ItemValidator`, `QuantityValidator`, `CategoryValidator`, `UnitValidator`, `ProfileValidator`, `QuantityInput.parse` |
| Use case (`:domain`) | Runs the validator, then the business rules (duplicates, units). The only way to write. | `*UseCases.kt`, `ImportValidator` |
| ViewModel (`:app`) | Calls the filter, runs the validator for a live error, exposes `canSave` / `canCreate` / `canConfirm`. | `LiveErrors.kt`, each `*ViewModel` |
| Composable | Shows the error in `supportingText` with the field's `error` semantics (TalkBack announces it), a `n / max` counter, the right `KeyboardType`, and a disabled primary button. | `FieldSupport.kt`, each screen |

## Character rules for all text

| Rule | Detail |
|---|---|
| Removed | Control characters (Unicode Cc, except line breaks and tab, handled below), bidirectional marks, overrides and isolates (U+061C, U+200E/F, U+202A-E, U+2066-9), zero-width space U+200B, word joiner U+2060, byte-order mark U+FEFF, soft hyphen U+00AD. They are invisible, can reorder or hide text and make a blank-looking value pass "not empty". |
| Kept | ZWJ U+200D and ZWNJ U+200C (needed for Indic conjuncts and emoji sequences), all letters and symbols of every script, emoji. |
| Whitespace | Any Unicode space (NBSP, ideographic space, tab) becomes one plain space; runs collapse to one. Single-line fields turn line breaks (also U+2028/2029) into spaces. Multi-line fields keep line breaks as `\n` (at most one blank line in a row, no trailing spaces before a break). |
| Blank | Empty, only spaces or only removed characters. Required fields reject it; optional fields store `null`. |
| Length | Counted in Unicode code points (an emoji is 1), after cleaning and trimming. While typing the filter allows one code point past the limit, so a long paste still shows "too long" instead of being cut silently, yet the field cannot grow without bound. |

## Field audit

| Field (screen) | Rules | Enforced in | UI behaviour |
|---|---|---|---|
| Checklist title (Create, Rename dialog) | Required; trim, clean; 1-100 | `ChecklistValidator.validateTitle` / `validate` | Live blank and too-long error; counter `n / 100`; Create / Save disabled while invalid |
| Checklist description (Create) | Optional; multi-line; blank becomes null; max 500 | `ChecklistValidator.validate` | Live too-long error; counter; Create disabled while too long |
| Item name (Item editor) | Required; single-line; 1-80 | `ItemValidator.validate` | Live error; counter `n / 80`; Save disabled while invalid |
| Quantity (Item editor, Add items rows) | Optional; digits (any script, stored as 0-9) plus one decimal separator `.` or `,`; no letters, no sign, no exponent (`1e5`), no grouping; more than 0; at most 99999; at most 3 decimals (milli-units) | `QuantityInput.parse` / `sanitize`, `QuantityValidator`, `Quantity.parse` | `KeyboardType.Decimal`; typed or pasted letters and symbols are dropped on change; integer digits capped at 5 and decimals at 3 while typing; separator shown as `.`; live message (not a number / more than zero / up to 99999 / 3 decimals); Save and "Add selected" disabled while the amount is invalid; whole-number units reject fractions on save (`QUANTITY_MUST_BE_WHOLE`) |
| Unit (Item editor) | A unit needs an amount; code must exist | `QuantityValidator`, `ItemValidator` | Picker only, no free text; error under the picker |
| Notes (Item editor) | Optional; multi-line; blank becomes null; max 500 | `ItemValidator.validate` | Live too-long error; counter `n / 500`; Save disabled while too long |
| New custom unit label (Item editor dialog) | Required; single-line; 1-20 | `UnitValidator.validateLabel`; duplicate check in `CreateCustomUnitUseCase` | Live error; counter; Create disabled while invalid |
| Category name (Create checklist and Add categories dialog) | Required; single-line; 1-50; case-insensitive duplicate refused by the use case | `CategoryValidator.validateName`, `CreateCategoryUseCase` | Live error; counter; Create disabled while invalid |
| Section | No text input (a section is a category on a checklist) | n/a | n/a |
| Search (Home, Add items) | Cleaned, capped at 100 code points; blank means no filter | `InputText.forTyping`, `FieldLimits.SEARCH_MAX` | Control and bidi characters removed on change; no error needed |
| Profile name (Settings, first-run setup) | Optional; single-line, whitespace collapsed; max 50 | `ProfileValidator.validate` | Live too-long error; counter; Save / Continue disabled while any field is invalid |
| Profile e-mail | Optional; no whitespace anywhere (removed while typing); one `@`, non-empty local part (max 64), dotted domain without empty labels; max 254; Unicode allowed | `ProfileValidator`, `InputText.forEmail` | `KeyboardType.Email`; spaces removed; live "enter a valid e-mail" |
| Profile phone | Optional; digits (any script, stored as 0-9), optional leading `+`, separators space `-` `(` `)` `.`; 7-15 digits; max 25 characters; letters, `#`, `*` refused | `ProfileValidator`, `PhoneInput.sanitize` | `KeyboardType.Phone`; letters and symbols dropped on change; live digit-count message |
| Profile address | Optional; multi-line, each line trimmed, blank lines dropped; max 200 | `ProfileValidator` | Live too-long error; counter `n / 200` |
| AI command (Checklist detail) | Cleaned, multi-line allowed, max 500 (the assistant's own limit); blank cannot be sent | `InputText.forField`, `FieldLimits.AI_COMMAND_MAX`, `OfflineCommandParser` (rejects over-long), `PlanValidator` (every step re-validated, amounts through `Quantity.parse`) | Send disabled while blank; AI never bypasses use cases |
| Import file / JSON (Settings, Transfer) | At most 10 MB; strict JSON; counts and string lengths capped; every field through the same validators after `TransferText.clean`; quantity strings obey the quantity rules above | `JsonTransferCodec`, `ImportValidator`, `ImportPlanner` | File picker only; a rejected file shows a translated reason and writes nothing |
| Settings switches (AI, language) | No text input | n/a | n/a |
| Dialog buttons / pickers | No text input | n/a | n/a |

## Numeric parsing examples

| Input | Result |
|---|---|
| `12` `1.5` `1,5` `.5` `5.` `00012` | 12.000 / 1.500 / 1.500 / 0.500 / 5.000 / 12.000 |
| `१२` `٣` `౧౨.౫` | 12 / 3 / 12.5 (digits of any script) |
| `12a` `a12` `5kg` `1e5` `-3` `+3` `1.2.3` `1,2,3` `1 2` `½` `NaN` | refused: not a number (while typing the letters are dropped, so `12a` becomes `12`) |
| `0` `0.0` `000` | refused: must be more than zero |
| `1.2345` | refused: at most 3 decimals (`1.50000` is accepted, trailing zeros are ignored) |
| `100000` `99999.001` 5,000 digits | refused: at most 99999 (very long digit strings never overflow) |

`1,500` is read as 1.5, not one thousand five hundred: `,` is a decimal separator in several of the app's
locales and grouping separators are not supported. The amount field never produces them.

## Messages (all seven locales)

New keys: `error_quantity_not_number`, `error_quantity_not_positive`, `error_quantity_too_precise`,
`input_counter`. `error_quantity_invalid` is kept for the range message. Every message is a string
resource; the domain only returns codes.

## Contract changes (additive)

- `ValidationError`: new `QUANTITY_NOT_A_NUMBER`, `QUANTITY_NOT_POSITIVE`, `QUANTITY_TOO_PRECISE`.
- `FieldLimits`: new `SEARCH_MAX`, `AI_COMMAND_MAX`.
- New `InputText`, `QuantityInput` (+ `QuantityParse`), `PhoneInput`.
- `Quantity.parse` now delegates to `QuantityInput` and is stricter (rejects exponents, signs and a stray
  `+`); every value it accepted before that a person could sensibly type is still accepted.
- Validators now also strip control, bidi and zero-width characters and collapse inner whitespace of
  single-line text ("Rice  bag" is stored as "Rice bag").

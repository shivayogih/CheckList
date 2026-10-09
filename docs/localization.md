# Localization

CheckList ships in seven languages and lets the user change language inside the app. Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 9.

| Language | Tag | Native name | Resource folder |
|---|---|---|---|
| English (source) | `en` | English | `values/` |
| Kannada | `kn` | ಕನ್ನಡ | `values-kn/` |
| Hindi | `hi` | हिन्दी | `values-hi/` |
| Tamil | `ta` | தமிழ் | `values-ta/` |
| Telugu | `te` | తెలుగు | `values-te/` |
| Marathi | `mr` | मराठी | `values-mr/` |
| Malayalam | `ml` | മലയാളം | `values-ml/` |

## Status

| Piece | Status |
|---|---|
| String resources in all 7 languages | Implemented (Phase 1, CL-104) |
| In-app language picker with "System default" | Implemented (Phase 1, CL-104) |
| `SupportedLanguages` in `:domain` | Implemented (Phase 1) |
| Key-parity test and lint errors for missing/extra translations | Implemented (Phase 1) |
| Native-speaker review of the six translations | Review document generated: [translation-review.md](translation-review.md) (CL-107, CL-143); the review itself is required before release |
| Plurals for counts, Western-digit number formatting (`LocaleNumbers`) | Implemented (Phase 4, CL-142) |
| Format checks: plural forms, placeholder parity, positional `%N$s`, escaped `%` (`StringFormatTest`) | Implemented (Phase 4, CL-142) |
| Kannada journey test (switch language, UI translated) | Implemented (Phase 7, CL-171) |
| Seeded category and item names in 7 languages | Planned (Phase 2, CL-113) |
| Unit labels from resources (`UnitLabelProvider`) | Planned (Phase 3) |
| Per-script line height, screenshot matrix for all locales | Planned (Phase 4) |
| Pseudo-locales (`en-XA`, `ar-XB`) in dev builds | Planned (Phase 4) |

## How the app language works (implemented)

- AndroidX per-app language: `AppCompatDelegate.setApplicationLocales(...)`, wrapped by `AppLocales` in `:app`.
- Android 13+ stores the choice in the platform `LocaleManager`, and the language also appears in system Settings for the app. Android 8-12: AppCompat stores it, through the `AppLocalesMetadataHolderService` manifest entry with `autoStoreLocales=true`.
- "System default" is an empty locale list. The platform or AppCompat owns the stored value; the app does not keep its own copy (ADR-007).
- `MainActivity` is an `AppCompatActivity` so a language change recreates the screen correctly. The UI is still pure Compose.
- `generateLocaleConfig = true` in `app/build.gradle.kts` plus `res/resources.properties` (`unqualifiedResLocale=en`) generate the locale list for Android 13+ from the `values-*` folders.
- `SupportedLanguages.preferenceFromTags(...)` maps whatever the platform stored to a supported language, ignoring region ("kn-IN" → Kannada) and falling back to System default for anything unsupported.

## Rules for UI text

1. **No user-visible text in Kotlin.** Every label, message and content description is a string resource. Lint reports `HardcodedText` as an error.
2. **Every key exists in all seven files.** Lint (`MissingTranslation`, `ExtraTranslation`) and `StringResourcesTest` fail the build otherwise.
3. **Plurals with `<plurals>`** in `values*/plurals.xml` (one and `other` forms in all seven languages, the CLDR categories they use), read through `progressText()` / `countText()` in `presentation/common/Counts.kt` or `getQuantityString`, never by joining strings.
4. **Positional string placeholders only** (`%1$s`, `%2$s`) so word order can change per language. Numbers are passed as text formatted by `LocaleNumbers` (grouping for the app language, Western digits 0-9 in every language, assumption A-03), never as `%d`, which would give Devanagari digits in Marathi. `UiText` formats `Int`/`Long` arguments this way automatically. A literal `%` is written `%%`, or the string is marked `formatted="false"`.
5. **Brand names** that must not be translated: `translatable="false"` in `values/strings.xml` only (for example `app_name`; the `dev` and `staging` flavors override it).
6. **Simple words.** Write English first, short and plain ("Add item", not "Insert entry"); see [accessibility.md](accessibility.md#plain-language). Translations go in the same PR.
7. **Layouts must stretch.** Tamil and Malayalam are often 30-60% longer than English. No fixed-width text containers; use `start`/`end`, never `left`/`right`.

## Adding a string

1. Add it to `app/src/main/res/values/strings.xml`.
2. Add the same key to the six `values-<tag>/strings.xml` files. If a translation is not ready, use the best available one and open a follow-up issue for native review; never leave the key out.
3. Run `./gradlew :app:testDevDebugUnitTest :app:lintDevDebug`.
4. Regenerate the review document: `python3 tools/l10n/translation_review.py` (`--check` reports whether it is current). It is not gated in CI, so parallel branches that add strings do not conflict; regenerate it before a release review.

## Data localization (Planned)

- **Seeded categories and items** have a stable `canonical_key` and per-locale names in translation tables (see [database.md](database.md)). Search covers the current language, English and transliterated aliases ("akki", "chawal").
- **Saved checklist items** keep the name they were created with. Switching language does not rewrite them (snapshot rule).
- **Units** are stored as codes (`KG`) and labelled from string resources (`unit_kg`).
- **Numbers and dates** use the app locale through `NumberFormat` and `DateTimeFormatter`, with Western Arabic digits in every language (assumption A-03). Implemented: `LocaleNumbers` adds the `nu-latn` Unicode extension to the locale for counts and quantities (up to three decimals); `java.time` formatters already use standard digits (`DecimalStyle.STANDARD`), so the PDF export time needs no extension.

## Adding a language later

For example Bengali, Gujarati, Punjabi, Odia, Assamese or Urdu:

1. `values-xx/strings.xml` with every key.
2. `data/src/main/assets/seed/i18n/xx.json` and a `seed_version` bump (once the seed catalog exists).
3. One entry in `SupportedLanguages` (tag and native name).
4. Screenshot tests for the new locale. Urdu is right-to-left: the `ar-XB` pseudo-locale catches RTL problems early.

No business logic changes.

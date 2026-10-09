# UI design system (CL-260 to CL-269)

The tokens and shared components of the restyle live in `presentation/theme` and `presentation/components`.
This page records the decisions that are not obvious from the code.

## Type scale

The scale is compact so words and sentences sit neatly on the screen. The user's system font size does the
enlarging: every size is in `sp`, so 130 % or 200 % text scales all of it, and the screenshot tests render
every component at 200 % in seven languages and fail on clipped or overflowing text.

Rules: nothing is smaller than **12 sp**; line height is about 1.4 to 1.5 times the size (tall Indic scripts
such as Kannada, Tamil and Malayalam clip with tighter lines); line height is never trimmed
(`LineHeightStyle.Trim.None`). `ContrastTest` fails the build if a style goes below 12 sp.

| Token | Size / line height (sp) | Weight | Used for |
| --- | --- | --- | --- |
| displayLarge / Medium / Small | 26/34, 24/32, 22/30 | Bold | rare, large numbers |
| headlineLarge | 22/30 | Bold | |
| headlineMedium | 20/28 | Bold | onboarding titles, screen headlines |
| headlineSmall | 18/26 | SemiBold | splash app name |
| titleLarge | 18/26 | SemiBold | top bar title, dialog and sheet titles |
| titleMedium | 16/24 | SemiBold | checklist card title |
| titleSmall | 14/22 | SemiBold | |
| bodyLarge | 15/22 | Regular | body text, field text |
| bodyMedium | 14/21 | Regular | secondary text, descriptions |
| bodySmall | 12/18 | Regular | helper text, errors, dates |
| labelLarge | 14/20 | SemiBold | buttons |
| labelMedium | 12/18 | SemiBold | field labels |
| labelSmall | 12/16 | SemiBold | |

Styles from the mockups without a Material slot (`CheckListText`):

| Token | Size / line height (sp) | Weight |
| --- | --- | --- |
| bigCount ("5 of 12 done") | 18/26 | Bold |
| percent ("42%") | 22/30 | Bold |
| stepperValue | 22/30 | Bold |
| progressLabel | 13/18 | SemiBold |
| unitChip | 12/16 | SemiBold |
| chip | 13/18 | Medium |
| sectionHeader | 13/18 | Bold |
| categoryName | 16/24 | Bold |
| rowTitle | 15/22 | Medium |
| subtitle | 12/18 | Regular |
| emojiSmall / emojiLarge | 16/24, 22/30 | Regular |

The scale was made smaller in CL-301 (body 14, titles 16 to 18, headline 20) after the user found the first
one too large on every screen. Some component KDoc still quotes older sizes; this table is the source of truth.

## Compact sizes (CL-301)

Buttons and search field 48 dp, top bar 56 dp, rows 56 dp (item rows 60 dp), text fields 56 dp, chips 36 dp
drawn inside a 48 dp touch box, checkbox 26 dp, stepper 56 dp (48 dp compact). All are minimums (`heightIn`),
so large text still grows them. `Dimens` holds every value.

## Settings

Settings is a grouped list: a `SectionHeader` (General, Your data, AI assistant, About) above a rounded
`SettingsGroup` card on `surfaceContainer`. Each `SettingsRow` / `SettingsSwitchRow` leads with its icon on a
40 dp `primaryContainer` tile; the last row of a group passes `showDivider = false`.

## Launcher icon and splash

* The adaptive foreground is the production artwork scaled to 70 % and moved so the ring's centre sits on the
  canvas centre (the 48 dp keyline), so it is centred in every launcher mask. Dev and staging use the same art
  plus a ribbon layer only.
* Splash: Android 12+ shows the foreground on `splash_background` (deep brand green `#005234`, never white);
  then `BrandSplash` shows the production logo, the app name and the tagline for 900 ms on a cold start only
  (rotation and language changes skip it). Every flavour shows the production logo.

## Touch targets and layout

* Every control has a 48 dp layout size (the tests measure the laid-out size, not the touch-bounds expansion).
  Icon-only actions use `AppIconButton`: a translated `contentDescription`, a long-press tooltip, 48 dp.
* Components wrap instead of cutting text; fixed heights are minimums (`heightIn`).
* Form components only display `errorText` / `helperText` / a length counter. Validation lives in the domain (CL-280).

## Colour

Four palettes: light, dark, high-contrast light and high-contrast dark (`CheckListTheme(highContrast = true)`).
Text pairs reach 4.5:1 (7:1 in high contrast), borders and meaningful icons 3:1; `ContrastTest` checks every pair.

## Fonts and emoji

The app uses system fonts and bundles none. Screenshot tests load Noto Sans for the Indic scripts and a cut-down
Noto Color Emoji from `app/src/test/resources/screenshot-fonts` (SIL OFL 1.1). The folder is not called `fonts`
because Robolectric's native graphics finds its own system fonts through a classpath folder of that name.
The seed catalog uses only emoji that Android 8 and later draw (travel, pooja items and toiletries were changed
for that reason).

## Screenshots

`ComponentScreenshotTest` renders each component in 15 variants (light, dark, 200 % English, and Kannada, Tamil,
Malayalam, Hindi, Telugu, Marathi at 100 % and 200 %). CI records the PNGs as the `screenshots` artifact;
goldens are kept for nine variants in `app/src/test/screenshots`.

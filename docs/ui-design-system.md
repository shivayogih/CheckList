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
| displayLarge / Medium / Small | 28/36, 26/34, 24/32 | Bold | rare, large numbers |
| headlineLarge | 26/34 | Bold | |
| headlineMedium | 24/32 | Bold | onboarding titles |
| headlineSmall | 22/30 | SemiBold | |
| titleLarge | 20/28 | SemiBold | top bar title, dialog and sheet titles |
| titleMedium | 18/26 | SemiBold | checklist card title |
| titleSmall | 16/24 | SemiBold | |
| bodyLarge | 16/24 | Regular | body text, field text |
| bodyMedium | 14/22 | Regular | secondary text, descriptions |
| bodySmall | 12/18 | Regular | helper text, errors, dates |
| labelLarge | 15/22 | SemiBold | buttons |
| labelMedium | 13/18 | SemiBold | field labels |
| labelSmall | 12/16 | SemiBold | |

Styles from the mockups without a Material slot (`CheckListText`):

| Token | Size / line height (sp) | Weight |
| --- | --- | --- |
| bigCount ("5 of 12 done") | 20/28 | Bold |
| percent ("42%") | 26/34 | Bold |
| stepperValue | 26/34 | Bold |
| progressLabel | 14/20 | SemiBold |
| unitChip | 13/18 | SemiBold |
| chip | 14/20 | Medium |
| sectionHeader | 14/20 | Bold |
| categoryName | 18/26 | Bold |
| rowTitle | 16/24 | Medium |
| subtitle | 13/18 | Regular |
| emojiSmall / emojiLarge | 18/26, 24/32 | Regular |

Some component KDoc still quotes the larger sizes of the first mockup-based spec; this table is the source of truth.

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

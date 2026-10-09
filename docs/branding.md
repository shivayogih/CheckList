# App logo and launcher icons

Supplied artwork lives in `/brand` of the project files (master SVG, adaptive layers, PNGs). What the app uses:

| Where | File | Notes |
|---|---|---|
| Adaptive icon | `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Background colour `launcher_background`, foreground `ic_launcher_foreground`, themed layer `ic_launcher_monochrome` |
| Foreground | `res/drawable/ic_launcher_foreground.xml` | Converted from `adaptive_foreground.svg`; the group is scaled to 85% about the centre so the rays stay inside the launcher mask |
| Themed icon (Android 13+) | `res/drawable/ic_launcher_monochrome.xml` | Tick and ring only, one flat colour, shared by all flavors |
| Legacy PNGs | `res/mipmap-*/ic_launcher.png` | Supplied PNGs; unused at minSdk 26 but kept for tooling |
| Splash (Android 12+) | `res/values-v31/themes.xml`, `values-night-v31` | Same foreground on `splash_background` (#FCFDF7 light, #111411 dark) |
| In-app logo | `presentation/components/AppLogo.kt`, `res/drawable/ic_app_logo.xml` | Full icon as a vector; description string `app_logo_description` in seven languages |
| Play Store | `docs/store/ic_launcher_512.png` | 512 x 512 high-res icon (copy of `checklist_icon_512x512.png`) |

## Flavor icons

Every flavor uses the production artwork and the production background (`launcher_background`, #087B4B green, defined once in `app/src/main`). The only difference is a small dark ribbon in the lower part of the foreground, drawn as vector artwork (not text):

| Flavor | Ribbon | Source set |
|---|---|---|
| production | none | `app/src/main` |
| staging | dark pill with "STG" | `app/src/staging/res` |
| dev | dark pill with "DEV" | `app/src/dev/res` |

`dev` and `staging` override only the two adaptive icon XMLs (foreground = production foreground + ribbon, in `ic_launcher_foreground_flavor`). There are no per-flavor colours or per-flavor legacy PNGs; the legacy PNGs of `main` are used. The splash screen (Android 12+) uses the production foreground on `splash_background` in every flavor; there is no flavor-specific splash artwork.

## Where AppLogo is used

`AppLogo` is shown in Settings > About. The Welcome / first-run screen (`WelcomeContent`) shows `AppLogo(size = 96.dp)`.

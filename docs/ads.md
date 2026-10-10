# Ads (CL-370)

Ads are controlled by one build switch per flavor. With the switch off, the build has no ad SDK, no ad
code, no ad layout or empty space, and no INTERNET permission. With it on, AdMob is compiled in.

## The switch

`gradle.properties`:

| Property | Default | Meaning |
|---|---|---|
| `checklist.ads.dev` | `true` | Dev builds show Google's test ads |
| `checklist.ads.staging` | `false` | Tester builds stay ad-free |
| `checklist.ads.production` | `false` | The first India release stays ad-free and offline |

Override for one build: `./gradlew :app:assembleProductionRelease -Pchecklist.ads.production=true`.

`app/build.gradle.kts` turns the switch into `BuildConfig.ADS_ENABLED`, adds `play-services-ads` and the
UMP consent SDK only to flavors that are on, and compiles either `app/src/adsOn` (AdMob, plus a manifest
with the AdMob app ID) or `app/src/adsOff` (no ads) into each variant. CI runs
`tools/checks/ads_manifest.py` after the build to prove ad-free flavors have no INTERNET or AdMob entries.

## IDs

Dev and staging always use Google's test IDs. Production with ads on needs real IDs, which are never
committed: Gradle properties `checklist.admob.app`, `.banner`, `.native`, `.interstitial`, or the
`ADMOB_APP`, `ADMOB_BANNER`, `ADMOB_NATIVE`, `ADMOB_INTERSTITIAL` environment variables (CI secrets).
The build fails if production ads are on and an ID is missing. Never tap real ads on your own phone.

## Where ads appear

| Where | Ad | Code |
|---|---|---|
| Home, bottom | Adaptive banner | `BannerAdSlot` in `HomeScreen` |
| Add items list | One native row after every 15 suggestions | `NativeAdSlot` in `AddItemsScreen` |
| After sharing or saving a PDF | Interstitial on every third export | `rememberPdfExportAd` in `ChecklistDetailScreen` |

Never: onboarding, profile, settings, reminders, while the keyboard is open, while ticking items, and
not at all in the first 3 days after install (`AdPolicy`). The SDK and its consent form start only after
those 3 days, so a new user never sees either. The PDF export count is kept in the settings DataStore
(`AdFrequencyStore`) and never leaves the phone.

## Before turning production ads on

Update the privacy policy, the Play Data Safety form (advertising ID, approximate location and device
data collected by AdMob), the "Contains ads" declaration and the store listing. Draft texts for that
case are in the project's Play folder (`play/policy/privacy-policy-with-ads.md`,
`play/listing/data-safety-with-ads.md`).

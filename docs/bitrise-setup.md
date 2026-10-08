# Bitrise setup for CheckList (Phase 9, CL-190..CL-197)

This guide takes you from "no Bitrise account" to all four workflows in [`bitrise.yml`](../bitrise.yml) running, using free plans only (Bitrise Hobby, Firebase Spark, and the Google Play developer account you already paid for). Read it top to bottom once. Each step says **where to click** and **what name to use**, because `bitrise.yml` only refers to secrets by name.

## 1. What the configuration does

| Workflow | Runs when | What it does | Secrets it needs |
|---|---|---|---|
| `pr` | A pull request **into `main` or `release/*`** is opened or updated (not drafts) | Unit tests, lint, `assembleDevDebug` | none |
| `dev` | Push to `develop` | `assembleDevDebug` → Firebase App Distribution (group `dev-testers`) | Firebase (optional: skipped if missing) |
| `staging` | Push to `release/*` | versionCode check → unit tests → signed **staging APK** → Firebase (`staging-testers`); signed **production AAB** → Play **closed testing** (`alpha`, status `completed`) | keystore, Firebase, Play (each part skipped if its secret is missing) |
| `production` | Tag `vX.Y.Z` (regex `^v\d+\.\d+\.\d+$`) | Tag/version/branch validation → unit tests + release lint → signed production AAB → release notes → Play **production** track as a **draft** | keystore, Play (fails fast if missing) |

PRs into `develop` are **not** built on Bitrise: GitHub Actions checks them for free (architecture section 16.2). That split is what keeps Bitrise inside 300 credits a month.

Nothing reaches Play users automatically: production uploads are drafts, and you press **Release** in Play Console yourself (architecture section 16.4).

### Files

| File | Purpose |
|---|---|
| `bitrise.yml` | The Bitrise configuration (commented for learning) |
| `ci/scripts/version-code.sh` | Prints `versionCode` (or `--name` for `versionName`) from `version.properties`, same formula as Gradle |
| `ci/scripts/validate-version.sh` | Release gate: tag = `v` + versionName, versionCode > every code in `releases.csv`, tag reachable from `origin/main`; `--code-only` for staging |
| `ci/scripts/release-notes.sh` | User-facing notes from `CL-<id>` commit subjects since the previous `v*` tag; max 500 characters (Play limit) |
| `ci/scripts/collect-reports.sh` | Merges JUnit XML for Bitrise Test Reports, packs HTML reports as an artifact |

All scripts are bash with `set -euo pipefail` and pass `shellcheck` 0.11.0. You can run them locally, for example `ci/scripts/version-code.sh --env` or `SKIP_MAIN_CHECK=true ci/scripts/validate-version.sh v1.0.0`.

## 2. What I verified (2026-10-08)

Every fact below was checked on the page named, on the date above. Where a page did **not** confirm something, it says so.

| Topic | Fact used in `bitrise.yml` | Source |
|---|---|---|
| YAML format | `format_version` is required; examples use `'25'`. Top-level keys include `step_bundles`, `workflows`, `meta`, `app`. `trigger_map` is legacy (use target-based triggers in Workflows/Pipelines); `before_run`/`after_run` are legacy (use step bundles). `meta.bitrise.io.stack` / `machine_type_id`. Step `run_if`, `is_always_run`, `is_skippable`. | [Configuration YAML reference](https://docs.bitrise.io/en/bitrise-ci/references/configuration-yaml-reference) |
| Triggers | Triggers live in a `triggers` element of a Workflow or Pipeline: `push` (`branch`, `commit_message`, `changed_files`), `pull_request` (`source_branch`, `target_branch`, `label`, `draft_enabled`, `comment`, ...), `tag` (`name`). Each condition is a string, a `pattern` or a `regex`. All conditions in one item must match; every matching trigger starts a build. Regex examples are single-quoted. | [YAML syntax for build triggers](https://docs.bitrise.io/en/bitrise-ci/run-and-analyze-builds/build-triggers/yaml-syntax-for-build-triggers) and the YAML reference above |
| Step bundles | Referenced as `- bundle::<name>: {}`; bundle `inputs` have defaults, are overridden at the call site, and are read inside the bundle as `$input_name`; bundles can be nested and placed anywhere. | [Step bundles](https://docs.bitrise.io/en/bitrise-ci/workflows-and-pipelines/steps/step-bundles) |
| Step versions | From the official StepLib spec (`https://bitrise-steplib-collection.s3.amazonaws.com/spec.json`): `git-clone` 8.5.1, `set-java-version` 1.3.0 (offers 25/21/17/11/8), `restore-gradle-cache` 3.1.1, `save-gradle-cache` 1.6.1, `android-build` 1.2.0, `sign-apk` 2.0.2, `google-play-deploy` **4.0.0** (2026-08-28), `firebase-app-distribution` 0.12.1, `deploy-to-bitrise-io` 2.26.1, `script` 1.2.1, `custom-test-results-export` 1.3.2. Step pages: [bitrise.io/integrations/steps](https://bitrise.io/integrations/steps). | StepLib spec, downloaded 2026-10-08 |
| google-play-deploy 4.0.0 | Only breaking change: `mapping_file` is no longer set by default ("For bundles: no breaking change in practice"). 3.9.0 made `track` optional. Inputs used: `service_account_json_key_path`, `package_name`, `app_path`, `track`, `status`, `release_name`, `whatsnews_dir` (files named `whatsnew-<locale>`). | [steps-google-play-deploy releases](https://github.com/bitrise-steplib/steps-google-play-deploy/releases), StepLib spec |
| Play deploy recipe | `android-build` → `sign-apk` → `google-play-deploy`, `service_account_json_key_path: $BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL`; tracks internal/alpha/beta/production; "Recommended draft for production and completed for internal test builds." (The recipe still shows `google-play-deploy@3`; 4.x is current.) | [Deploy to Google Play recipe](https://docs.bitrise.io/en/bitrise-ci/workflows-and-pipelines/workflows/workflow-recipes-for-android-projects/android-deploy-to-google-play-internal-alpha-beta-production) |
| Android Sign | Inputs default to `$BITRISEIO_ANDROID_KEYSTORE_URL`, `_PASSWORD`, `_ALIAS`, `_PRIVATE_KEY_PASSWORD`; outputs `BITRISE_SIGNED_APK_PATH` / `BITRISE_SIGNED_AAB_PATH`. | StepLib spec |
| Keystore upload | **Project settings → Code signing → Android tab → Add keystore file**; fields keystore password, key alias, private key password; creates `BITRISEIO_ANDROID_KEYSTORE_URL` (time-limited download URL), `_ALIAS`, `_PASSWORD`, `_PRIVATE_KEY_PASSWORD`. | [Uploading Android keystore files](https://docs.bitrise.io/en/bitrise-ci/code-signing/android-code-signing/uploading-android-keystore-files-to-bitrise) |
| Play service account file | Upload the JSON key to the **Files** section; the example variable is `BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL`. Service account permissions: View app information; Manage production releases, manage testing track releases; Edit store listing, pricing & distribution. | [Deploying Android apps to Bitrise and Google Play](https://docs.bitrise.io/en/bitrise-ci/deploying/android-deployment/deploying-android-apps-to-bitrise-and-google-play) |
| Secrets | PR builds don't get secret values by default ("Expose for pull requests" is off); protected secrets can't be exposed or viewed again (irreversible); **public** Bitrise apps can't expose secrets to PRs; secrets are redacted in logs. | [Secrets](https://docs.bitrise.io/en/bitrise-ci/configure-builds/secrets) |
| Gradle cache | Restore Gradle cache sets the key and paths automatically; use with Save Gradle cache. | StepLib spec; [Android dependencies](https://docs.bitrise.io/en/bitrise-ci/dependencies-and-caching/android-dependencies) |
| Test Reports | Export test results step: set `test_name`, `base_path`; if `base_path` is a single file, `search_pattern: "*"`; JUnit XML supported; Deploy to Bitrise.io uploads results. | [Test reports](https://docs.bitrise.io/en/bitrise-ci/testing/test-reports.html), StepLib spec |
| Hobby plan | Free: 1 private app, 5 concurrencies, 90-minute build timeout, 300 credits/month. Machines on Hobby: Linux Medium, macOS Medium, macOS Large. **The page does not state how many credits a Linux minute costs.** | [Bitrise pricing](https://bitrise.io/pricing) |
| Machine/stack | Linux Medium = machine type id `standard` (4 vCPU, 16 GB). Current stable Linux stack "Ubuntu Noble 24.04 - Bitrise 2025 Edition", id example `ubuntu-noble-24.04-bitrise-2025-android`; "Ubuntu 22.04 for Android & Docker" is frozen and removed April 2027. | [Build machine types](https://docs.bitrise.io/en/bitrise-platform/infrastructure/build-machines/build-machine-types), [Linux stack update policy](https://docs.bitrise.io/en/bitrise-platform/infrastructure/build-stacks/linux-stack-update-policy), [June 2026 Linux update](https://bitrise.io/blog/post/latest-linux-updates-for-june-2026) |
| **First Play upload is manual** | **Confirmed in three places.** Google: "You can only use this API to make changes to an existing app (that has at least one APK uploaded)… you will have to upload at least one APK through the Play Console before you can use this API." Bitrise: "Upload the first AAB or APK manually to Google Play"; the step description says the same. | [Play Developer API: Edits](https://developers.google.com/android-publisher/edits), Bitrise deploy guide above, google-play-deploy step description |
| Play API access | No need to link the developer account to a Google Cloud project any more; create the service account in Google Cloud, then invite its e-mail in Play Console **Users and permissions**. | [Play Developer API: Getting started](https://developers.google.com/android-publisher/getting_started) |
| Play closed testing | New personal accounts (created after 2023-11-13) need a closed test with at least 12 opted-in testers for 14 consecutive days before production access. | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en) (verified in Phase 0) |
| Firebase auth | CI can authenticate with a service account or `login:ci`; service account needs the **Firebase App Distribution Admin** role and a JSON key. (No deprecation of `login:ci` stated.) | [Distribute with Gradle](https://firebase.google.com/docs/app-distribution/android/distribute-gradle), [Distribute with the CLI](https://firebase.google.com/docs/app-distribution/android/distribute-cli) |
| Firebase and AABs | "To upload AABs to App Distribution, you must link your Firebase app to an app in Google Play", same package name, and the app must be published on Play. **So the staging build sends an APK**, because `com.dataloom.checklist.staging` is never on Play. | [Distribute Android apps (AAB)](https://firebase.google.com/docs/app-distribution/android/distribute-console?apptype=aab) |

**Not verified:** the Bitrise credit cost per Linux minute on Hobby (measure it, section 9); whether Play Console still names its default closed track `alpha` in the API (it is the name Bitrise's docs and step use; confirm in step 5.3); the Play API rule that a never-published ("draft") app only accepts `draft` releases (a well-known API error, but not stated on the pages above; the `PLAY_CLOSED_STATUS` setting exists for it).

### Deviations from the original brief (and why)
1. **Staging sends an APK to Firebase, not `bundleStagingRelease`.** Firebase only accepts AABs for apps linked to and published on Play (verified above). Building an AAB nobody can install would waste credits.
2. **`google-play-deploy@4`** instead of `@3` named in the architecture document: 4.0.0 is current, and its only breaking change does not affect AABs.
3. Each `release/*` push is a Play upload, so `staging` first runs `validate-version.sh --code-only`; Play would reject a reused versionCode anyway, this just fails in seconds rather than after the build.

## 3. Create the Bitrise account (Hobby, free)
1. Go to [bitrise.io](https://bitrise.io) and **Sign up with GitHub** using the account that owns `shivayogih/CheckList`.
2. When asked for a plan, choose **Hobby** (free, 300 credits/month). Do not start a paid trial: a trial that ends moves you to Hobby anyway, but a card is not needed.
3. Create a Workspace (any name, e.g. "dataloom").

## 4. Add the repository as a Bitrise project
1. **Add new project** (sometimes "Add new app") → choose **GitHub** → install/authorize the **Bitrise GitHub App** for **only** `shivayogih/CheckList` (not "all repositories").
2. Visibility: choose **Private**. Hobby includes one private app. A *public* Bitrise app shows build logs to everyone and can't give secrets to PR builds; private is safer even though the GitHub repo is public.
3. Default branch: `develop`. Bitrise scans the repo and detects Android; when it offers to create a configuration, choose to **use the `bitrise.yml` in the repository** (look for *Configuration YAML → Store in repository*; exact menu names were not re-verified for this guide). Then the file in Git is the single source of truth and changes go through PRs like code.
4. Stack/machine: `bitrise.yml` already sets `ubuntu-noble-24.04-bitrise-2025-android` on `standard` (Linux Medium). If the Workflow Editor's **Stacks & Machines** shows different names, pick the current Ubuntu Android stack + Linux Medium and update `meta` in `bitrise.yml`.
5. Webhooks: the GitHub App delivers push, PR and tag events automatically. Check **Project settings → Integrations/Webhooks** shows GitHub connected.

### How target-based triggers work (read once)
- Each workflow has its own `triggers:` block. When GitHub sends an event, Bitrise compares it with every workflow's triggers; every match starts a build.
- `pr`: `pull_request` with `target_branch: main` or `target_branch: {regex: '^release/.+$'}` and `draft_enabled: false` (draft PRs don't burn credits).
- `dev`: `push` with `branch: develop`. Note that merging a PR into `develop` *is* a push to `develop`.
- `staging`: `push` with `branch: {regex: '^release/.+$'}`.
- `production`: `tag` with `name: {regex: '^v\d+\.\d+\.\d+$'}` (so `v1.0.0` matches, `v1.0.0-rc1` and `1.0.0` do not).
- To pause a workflow without deleting it, add `enabled: false` to its trigger item. You can still start it by hand: **Start build** → choose branch and workflow.

## 5. Signing: the upload key (needed by `staging` and `production`)
Play App Signing (default for new apps) means Google keeps the real app signing key; we only hold an **upload key**. Losing it is recoverable through Play support; leaking it can be revoked.

1. Create the upload keystore on your computer (once; keep a backup in your password manager, never in Git):
   ```bash
   keytool -genkeypair -v -keystore checklist-upload.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Bitrise: **Project settings → Code signing → Android → Add keystore file**. Upload `checklist-upload.jks` and enter the keystore password, alias `upload`, and private key password. Leave *Custom keystore ID* empty so the names match `bitrise.yml`:
   `BITRISEIO_ANDROID_KEYSTORE_URL`, `BITRISEIO_ANDROID_KEYSTORE_PASSWORD`, `BITRISEIO_ANDROID_KEYSTORE_ALIAS`, `BITRISEIO_ANDROID_KEYSTORE_PRIVATE_KEY_PASSWORD`.
3. If the file has an **Expose for pull requests** toggle, keep it **off**.

## 6. Google Play (needed by `staging` Play upload and `production`)
### 6.1 What must exist first (in this order)
1. The app in Play Console, created with package name **`com.dataloom.checklist`** (permanent once used).
2. A **closed testing** track with your testers (at least 12 people for 14 consecutive days, section 17.3 of the architecture).
3. **The first AAB uploaded manually** in Play Console. Google's API cannot create a new app or make its first upload (verified, section 2).

### 6.2 Get a signed AAB for the manual first upload, without signing anything locally
1. Do steps 4 and 5 (keystore uploaded). Leave the Play secret **out** for now.
2. Create `release/1.0.0` from `develop` and push it. `staging` runs; the Play upload step is skipped (secret missing), but the signed AAB (named like `checklist-production-signed.aab`) is attached to the build under **Artifacts**.
3. Play Console → your app → **Test and release → Testing → Closed testing** → the default track (*Closed testing - Alpha*) → **Create new release** → upload that AAB, add release notes, roll it out for review.
4. Record the upload in `docs/project-management/releases.csv` (`1.0.0,10000,,release/1.0.0,...,alpha,...`) and bump `VERSION_BUILD` to 1 before the next push to `release/1.0.0`. Every upload needs a new versionCode.

### 6.3 Service account for uploads
1. [Google Cloud console](https://console.cloud.google.com/) → create a project (free) → **IAM & Admin → Service Accounts → Create**. No roles are needed in Google Cloud. Create a **JSON key** and download it.
2. Play Console → **Users and permissions → Invite new users** → paste the service account e-mail → app permissions for CheckList: *View app information*, *Manage testing track releases*, *Manage production releases*, *Edit store listing, pricing & distribution*. Invite.
3. Bitrise: **Project settings → Code signing → Files (generic file storage) → Add file**, ID **`SERVICE_ACCOUNT_JSON_KEY`**, upload the JSON. Bitrise exposes it as `BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL`, the name `bitrise.yml` uses. (Bitrise's guide describes copying the file's **Download URL** into a Secret with that name. If your UI does not create the variable automatically, do that, and tick *Replace variables in inputs* only if the value is a `$VARIABLE` reference.)
4. Confirm the closed track's API name: in the Play Console URL or track settings it is `alpha` for the default closed track. If you made a custom closed track, set `PLAY_CLOSED_TRACK` in `bitrise.yml` to its name.
5. Until the first closed-testing release has been reviewed and rolled out in Play Console, set `PLAY_CLOSED_STATUS: draft` in `bitrise.yml`; switch back to `completed` afterwards.

## 7. Firebase App Distribution (needed by `dev` and `staging` distribution)
1. [Firebase console](https://console.firebase.google.com/) → **Add project** (Spark plan, free; Analytics not needed).
2. **Add app → Android** twice: package `com.dataloom.checklist.dev` and `com.dataloom.checklist.staging`. You don't need to add `google-services.json` to the repo for App Distribution. Copy each **App ID** (`1:1234567890:android:...`) from Project settings → General.
3. **App Distribution → Testers & Groups**: create groups with aliases **`dev-testers`** and **`staging-testers`** and add testers' e-mails. (These Firebase testers do not count toward Play's 12-tester rule.)
4. Google Cloud console for the same Firebase project → **Service Accounts → Create** → role **Firebase App Distribution Admin** → create a **JSON key**.
5. Bitrise: **Code signing → Files → Add file**, ID **`FIREBASE_SERVICE_ACCOUNT`** → available as `BITRISEIO_FIREBASE_SERVICE_ACCOUNT_URL`.
6. Bitrise: **Workflow Editor → Secrets → Add**: `FIREBASE_APP_ID_DEV` and `FIREBASE_APP_ID_STAGING` with the App IDs.

## 8. Secrets checklist (protect them, never expose to PRs)

| Name in `bitrise.yml` | Created where | Used by | Protected? | Expose for PRs |
|---|---|---|---|---|
| `BITRISEIO_ANDROID_KEYSTORE_URL` / `_PASSWORD` / `_ALIAS` / `_PRIVATE_KEY_PASSWORD` | Code signing → Android keystore | staging, production | Keep the keystore backup outside Bitrise first | **Off** |
| `BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL` | Code signing → Files, ID `SERVICE_ACCOUNT_JSON_KEY` | staging, production | Yes | **Off** |
| `BITRISEIO_FIREBASE_SERVICE_ACCOUNT_URL` | Code signing → Files, ID `FIREBASE_SERVICE_ACCOUNT` | dev, staging | Yes | **Off** |
| `FIREBASE_APP_ID_DEV`, `FIREBASE_APP_ID_STAGING` | Secrets | dev, staging | Optional (App IDs are identifiers, not credentials) | Off |

- **Protected** makes a secret unreadable forever, even to you: keep your own copy (password manager) before ticking it.
- Never print secrets in a script step; Bitrise redacts known secret values in logs, but don't rely on it.
- Never paste secret values into a PR, an issue, `bitrise.yml`, or an AI chat (architecture 19.5).

## 9. Credits: how they are spent and how to stay under 300/month
- Credits are spent per **build minute**, at a rate set by the machine type. Bitrise's pricing page lists Linux Medium for Hobby but **does not show the credit rate** (checked 2026-10-08). After your first builds, open **Insights → Credits** (or the build page) to see the real number, and write it down in `docs/project-management/builds.csv`.
- macOS machines cost much more; `bitrise.yml` pins Linux Medium. Don't change `machine_type_id`.
- Rough expectations with a warm Gradle cache (cold first builds are slower): `pr` 6–9 min, `dev` 4–6 min, `staging` 12–18 min, `production` 12–18 min.
- Example month: 6 `dev` + 2 `pr` + 4 `staging` + 1 `production` ≈ 30 + 15 + 60 + 15 = **~120 build minutes**. Whether that fits in 300 credits depends on the per-minute rate you measure: at 1 credit/min there is lots of room, at 2 credits/min it is tight, so measure in the first week.

Knobs already built in:
1. GitHub Actions runs all frequent checks; Bitrise `pr` only fires for PRs into `main`/`release/*`, and never for drafts.
2. `dev` runs no tests (the PR already did) and only builds one variant.
3. Gradle dependency cache restored every build, saved only on branch builds.
4. Unit tests run one variant per module, not `./gradlew test` (which builds all six app variants).
5. Shallow clone in `pr`; 50 commits in `dev`.
6. Production fails *before* building when tag, version or secrets are wrong.

If credits run low: set `enabled: false` on the `dev` trigger and start dev builds by hand when testers need one (or batch merges into `develop`), and squash fixes before pushing to `release/*` (each push is a full staging build and a Play upload). Everything is plain Gradle + `ci/scripts/`, so any workflow can move to GitHub Actions if needed (architecture 16.2).

## 10. Release routine (how the workflows fit together)
1. Feature PRs → `develop` (GitHub Actions). Merge → `dev` → Firebase dev testers.
2. Cut `release/1.0.0` from `develop`. Before **every** push to it: bump `VERSION_BUILD` in `version.properties` if this versionCode was already uploaded. Push → `staging` → Firebase staging testers + Play closed testing.
3. After each successful upload, add a row to `docs/project-management/releases.csv` (CI does not commit; architecture 21.2).
4. PR `release/1.0.0` → `main` (Bitrise `pr` + GitHub Actions). Merge.
5. On `main`, make sure the versionCode is **higher than every recorded upload** (bump `VERSION_BUILD` again in the release PR if the closed test used the current code), then tag: `git tag v1.0.0 && git push origin v1.0.0` → `production` → Play production **draft**.
6. Review the draft in Play Console and press **Release** when you're ready (and only after production access is granted).

## 11. First-run checklist (in this order)
1. Bitrise account + private project from the repo (sections 3–4). Push anything to `develop` → `dev` should go green and show "Firebase secrets missing, skipping distribution".
2. Firebase (section 7) → push to `develop` again → testers get an e-mail.
3. Keystore (section 5) → create `release/1.0.0` → `staging` green; download the production AAB artifact.
4. Play app + manual first upload to closed testing (section 6.1–6.2), record it in `releases.csv`, bump `VERSION_BUILD`.
5. Play service account (6.3) → push to `release/1.0.0` → AAB lands on the closed track.
6. Open a PR `release/1.0.0` → `main` → `pr` runs.
7. After the 14-day closed test and production access: merge, tag `v1.0.0`, check the draft.

## 12. Troubleshooting
| Symptom | Likely cause |
|---|---|
| `Check versionCode is new` fails | `VERSION_BUILD` not bumped after a recorded upload. |
| `Validate tag, version and branch` fails | Tag doesn't equal `v` + versionName, versionCode not above `releases.csv`, or the tag was created on a branch other than `main`. |
| Play upload: "Only releases with status draft may be created on draft app" | First release not rolled out in Play Console yet: set `PLAY_CLOSED_STATUS: draft`. |
| Play upload: 403 / permission denied | Service account not invited in Play Console, or missing release permissions (section 6.3). |
| Play upload: version code already used | Same versionCode uploaded before: bump `VERSION_BUILD`. |
| Firebase step: app not found | Wrong `FIREBASE_APP_ID_*`, or the service account is in a different Firebase project. |
| Gradle can't find Android platform 37 | The stack lacks the new SDK platform; AGP normally downloads missing components, otherwise add the *Install missing Android SDK components* step after `setup`. |

## 13. How `bitrise.yml` was validated
- The Bitrise CLI could not be downloaded here (GitHub release downloads for `bitrise-io/bitrise` are blocked by this environment's network policy), so `bitrise validate` was **not** run. Run it yourself once: `bitrise validate -c bitrise.yml` (install from the [Bitrise CLI releases](https://github.com/bitrise-io/bitrise/releases)), or open the Workflow Editor, which validates on load.
- Instead: YAML parsed with PyYAML; validated against SchemaStore's Bitrise schemas (`bitrise.json` + `bitrise-step.json`, which know `triggers`, `step_bundles` and `bundle::` references): **0 errors** (a deliberately broken copy produced the expected errors); every `bundle::` reference resolves; no legacy `trigger_map`/`before_run`/`after_run`; every step version-locked; trigger regexes tested against sample tags/branches; every `run_if` Go template parsed and executed with Bitrise-style helpers (`getenv`, `enveq`, `.IsPR`) for "no secrets" and "all secrets" cases with the expected skip/run result.

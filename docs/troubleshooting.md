# Troubleshooting

Known problems and their fixes. Add an entry whenever something costs you more than a few minutes.

## Builds

### `dl.google.com` is blocked in cloud development sessions

**Symptom:** in a Claude cloud container, Gradle cannot download the Android Gradle Plugin or AndroidX artifacts (connection refused or 403 for `dl.google.com` / `maven.google.com`), so no Android build can run there.

**Cause:** the session's network policy does not allow Google's Maven repository.

**What to do:** do not try to work around it. Push the branch: the `Android build` workflow on GitHub Actions builds every pushed branch, runs unit tests and lint, and builds all three environments. Treat that run as the build verification and link it in the PR. Pure-Kotlin `:domain` logic can still be reasoned about locally, but the authoritative result is the Actions run.

### "Cannot add extension with name 'kotlin'"

**Symptom:** Gradle sync fails with `Cannot add extension with name 'kotlin', as there is an extension already registered with that name` in an Android module.

**Cause:** AGP 9 compiles Kotlin itself ("built-in Kotlin"). Applying `org.jetbrains.kotlin.android` on top registers the `kotlin` extension twice.

**Fix:** remove `org.jetbrains.kotlin.android` (alias `kotlin-android`) from the module's `plugins {}` block and from the catalog. Keep `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization`; they are compiler plugins and still needed. The root `build.gradle.kts` declares `org.jetbrains.kotlin.jvm` with `apply false` to pin the Kotlin version for the whole build.

### kapt is not supported

**Symptom:** applying `kotlin-kapt` (or `org.jetbrains.kotlin.kapt`) fails, or annotation processors such as Room or Hilt do not run.

**Cause:** kapt does not work with AGP 9 built-in Kotlin, and it is in maintenance mode anyway.

**Fix:** use **KSP** (`com.google.devtools.ksp`) for Room and Hilt: `ksp(libs.room.compiler)`, `ksp(libs.hilt.compiler)`. Pick the KSP version that matches the Kotlin version in `libs.versions.toml`.

### Maven Central returns HTTP 429

**Symptom:** dependency resolution fails with `429 Too Many Requests` from `repo.maven.apache.org`, usually on CI or after clearing caches.

**Cause:** rate limiting of anonymous downloads, often from shared CI IP addresses.

**Fix:**

1. Re-run the job. The `setup-gradle` cache means the next attempt downloads much less.
2. Do not clear the Gradle cache on CI without a reason.
3. Locally, retry with `./gradlew --refresh-dependencies` only once, then wait a few minutes.
4. If it keeps happening, check that no build script adds extra repositories (the settings use `FAIL_ON_PROJECT_REPOS`, so modules cannot add their own).

### `version.properties: VERSION_X ... must be in ...`

**Cause:** a version part is outside its range (MINOR and PATCH 0-9, BUILD 0-99). See [release-process.md](release-process.md#versioning-implemented). Do not widen the range without an ADR: it changes the versionCode formula.

### Lint fails with `MissingTranslation` or `ExtraTranslation`

**Cause:** a string key exists in English but not in one of the six translations, or the other way round. `StringResourcesTest` fails for the same reason.

**Fix:** add the key to every `values-<tag>/strings.xml` (see [localization.md](localization.md)). Strings that must not be translated (brand names) get `translatable="false"` in `values/strings.xml` only.

### Lint fails with `HardcodedText`

**Fix:** move the text to `strings.xml` and use `stringResource(...)`. User-visible literals are never allowed in code.

## App behavior

### The language does not change, or resets after restart

- `MainActivity` must stay an `AppCompatActivity`; a plain `ComponentActivity` does not apply per-app locales.
- On Android 12 and lower the choice is stored by AppCompat through the `AppLocalesMetadataHolderService` entry with `autoStoreLocales=true` in the manifest. Do not remove it.
- On Android 13+, the language also appears under system Settings > Apps > CheckList > Language. That is expected.

### The app name shows "CheckList Dev" or "CheckList Staging"

Expected: each flavor overrides `app_name` in `src/dev` and `src/staging` so the three apps can be installed side by side.

## CI

### A workflow run was cancelled

The `Android build` workflow cancels an older run when a newer push arrives on the same branch (`concurrency`). Only the latest run matters.

### Release APKs will not install

CI release builds are unsigned by design. Install `devDebug` for testing; signed builds come from Bitrise from Phase 9.

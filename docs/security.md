# Security and privacy

How CheckList protects user data and the project's secrets. To report a vulnerability, see [SECURITY.md](../SECURITY.md). Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 19.

## Status

| Control | Status |
|---|---|
| Secrets and signing files excluded by `.gitignore` | Implemented (Phase 1) |
| No signing configuration in Git; release builds are unsigned locally and in CI | Implemented (Phase 1) |
| R8 minification and resource shrinking on `release` | Implemented (Phase 1) |
| Only the launcher activity is exported | Implemented (Phase 1) |
| GitHub Actions least privilege: `permissions: {}` per workflow, minimal per-job permissions, `persist-credentials: false` | Implemented (Phase 1; per job since Phase 8, CL-184) |
| Actions pinned to full commit SHAs; downloaded tools (gitleaks, detekt) verified by SHA-256; Python packages pinned | Implemented (Phase 8, CL-184) |
| Gradle wrapper validation on every push | Implemented (Phase 8, CL-184) |
| Dependabot updates, CODEOWNERS, private vulnerability reporting policy | Implemented (Phase 8 groundwork, CL-180) |
| Profile encryption with Tink and Android Keystore (AES-256-GCM, associated data per row and column) | Implemented (Phase 5, CL-151, CL-152) |
| Key-loss and tamper handling: erase, report, never crash | Implemented (Phase 5, CL-152) |
| Backup and device-transfer rules that exclude the profile keyset | Implemented (Phase 5, CL-153) |
| Profile data minimization (every field optional: name, email, phone, address; no locations) and redacted `toString()` | Implemented (Phase 5, CL-150; optional fields and address in CL-250) |
| Release-safe logging wrapper (`AppLog`); ban on `Log.*`, `println`, `print`, `System.out/err` | Implemented (Phase 8, CL-183) |
| Import limits and validation (size, counts, string caps, strict JSON, control-character stripping, all-or-nothing transaction) | Implemented (Phase 6, CL-160) |
| Share via FileProvider with temporary read grants; share files in `cacheDir/exports` only | Implemented (Phase 6, CL-160) |
| gitleaks over the whole history on every push (`Quality` workflow) | Implemented (Phase 8, CL-184) |
| GitHub secret scanning and push protection | Repository settings (owner) |
| Signing and Play credentials in Bitrise protected storage | Planned (Phase 9) |
| HTTPS-only network config, App Check | Planned (Phase 10) |
| Redaction before an optional CI model step sees a report (`enrich.redact`) | Implemented (Phase 11, CL-225); reused for diffs and logs when the review and failure agents arrive |

## Threat model in one paragraph

The app is single-user, offline and has no backend or account. The realistic risks are: personal data leaking through logs, backups, exports, PDFs or AI requests; a lost or stolen unlocked phone; a hostile import file; and secrets leaking from the public repository or CI. Attacks on rooted devices are out of scope.

## Data at rest

- Android already encrypts app storage (file-based encryption). Checklist data is not highly sensitive, so the Room database is **not** additionally encrypted in v1 (ADR-011, no SQLCipher).
- **Revisit trigger:** if a feature starts storing identity numbers, medical details or financial data inside checklist items, move the database to SQLCipher with a Keystore-wrapped passphrase through a tested migration.

### The encrypted profile (implemented, Phase 5)

**What is stored.** Four optional fields: a display name (up to 50 characters), an email, a phone number and a postal address (up to 200 characters, line breaks kept). Every field is optional, including the name (CL-250), so first-run setup can be skipped entirely; a form with every field blank stores nothing, and saving an emptied form in Settings deletes the stored profile. The Phase 0 sketch also listed home/office locations; no feature needs them, so they are not collected and the app asks for no location permission. There is no account, password or OTP and no backend. Validation and normalization live in `ProfileValidator` in `:domain`. **None of it is ever sent to AI**: the AI layer has no access to the profile (see Data leaving the device).

**Payload compatibility.** The address was added to the encrypted JSON payload without a new payload version (`schema_version` stays 1, so there is no schema change and no migration). Every payload key has a default and unknown keys are ignored, so a row written before CL-250 (no address) still decrypts, and a profile without an address encrypts to the same shape as before. `ProfileCipherTest` and `EncryptedProfileRepositoryTest` keep tests for the pre-CL-250 payload. The key-loss, tamper and backup behaviour below is unchanged.

**First-run flag.** Whether the first-run flow was finished is stored as `onboarding_completed` in Preferences DataStore. It is a plain convenience flag, not personal data. The typed profile text is held only in the ViewModel while the form is open and is deliberately not put in `SavedStateHandle`, which the system writes to disk unencrypted; after process death the flow resumes at the same step with empty fields.

**How it is encrypted.** Envelope encryption with Tink (`com.google.crypto.tink:tink-android` 1.23.0):

| Layer | What | Where it lives |
|---|---|---|
| Master key | AES-256-GCM key generated inside the Android Keystore (TEE-backed where available), non-exportable, alias `checklist_profile_master_v1`. No user authentication is required, so a lock-screen change does not invalidate it | Android Keystore; never in app files, never in a backup |
| Data keyset | Tink AES-256-GCM keyset, encrypted by the master key with associated data `checklist/user_profile/keyset/v1` | Private SharedPreferences file `checklist_profile_keyset.xml`, excluded from backup |
| Profile | One JSON payload encrypted by the keyset | `user_profile.enc_payload` (schema v1, row `id = 'me'`) |

- **One ciphertext per row**, as section 5.3 designed: nothing in the profile needs SQL, and one blob does not reveal which fields are filled or how long each is. The v1 table is reused unchanged, so there is **no schema change and no migration**.
- **Associated data binds the ciphertext to where it is stored**: `checklist.db/user_profile/enc_payload/id=<row>/v=<schema_version>/key=<key_alias>`. A blob copied into another row or column, or relabelled with another version or key alias, fails authentication rather than decrypting.
- Keystore calls are slow, so the keyset is unwrapped once per process and the Keystore key is not used for every read.
- Jetpack `security-crypto` (EncryptedSharedPreferences, MasterKey) is deprecated and not used. The Tink APIs used are the current ones: `AndroidKeystore` for the master key, `KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)`, `TinkProtoKeysetFormat.serializeEncryptedKeyset`/`parseEncryptedKeyset` in their `Configuration` overloads and `getPrimitive(RegistryConfiguration.get(), …)`; the build has no Tink deprecation warnings, not the deprecated `AndroidKeysetManager` builder options.
- `UserProfile.toString()` and the internal payload's `toString()` print `***`, so the profile cannot leak through logs, crash messages or test output by accident.

**Failure handling (never crash, never lose data by accident).** `EncryptedProfileRepository` reports problems through `ProfileState` instead of throwing:

| Situation | What happens | UI sees |
|---|---|---|
| Keystore key missing, invalidated (`KeyPermanentlyInvalidatedException`, `UnrecoverableKeyException`) or replaced, so the keyset cannot be unwrapped | Profile row erased, keyset and key destroyed, fresh keys on the next save | `ProfileState.Reset` until a new profile is saved or `AcknowledgeProfileResetUseCase` runs |
| Ciphertext fails authentication (tampering, relabelled row) | Row erased; the keys were fine and are kept | `ProfileState.Reset` |
| Row present but no keys (data restored to a new phone; the keys stayed on the old one) | Row erased; no keys are created just by reading | `ProfileState.Reset` |
| Keystore error that may be temporary (`ProviderException`, `KeyStoreException`) | Nothing deleted; saves return `DomainError.SecureStorageUnavailable` | `ProfileState.Unavailable` |
| Row written by a newer app version | Nothing deleted | `ProfileState.Unavailable` |

Erasing only deletes the row if it still holds the exact ciphertext that failed, so a profile saved in the meantime is never removed. **Clearing the profile** deletes the row and destroys both keys (crypto-shredding): even if old SQLite pages or a backup still hold the ciphertext, nothing can decrypt it. Clearing always works, so it is also the user's way out of `Unavailable`. The reset notice is held in memory; if the process dies before the UI shows it, the user simply finds no profile.

### Backups and device transfer

`android:dataExtractionRules` (Android 12+, cloud backup and device-to-device transfer) and `android:fullBackupContent` (Android 11 and lower) both exclude `sharedpref/checklist_profile_keyset.xml`. `BackupRulesTest` in `:app` keeps the rules and the file name in sync.

- **Why the keyset is excluded:** it is useless without the Keystore key, which never leaves the device, and excluding it means a backup is never paired with any profile key material.
- **Migration safety copy (CL-320):** before a schema upgrade the database file is copied to `noBackupFilesDir/db-backups/`, which Android excludes from backups and device transfer by definition. The copy holds the same ciphertext as the database. [db-migrations.md](db-migrations.md) describes the restore path. Nothing sensitive is excluded by mistake: the rules exclude only the keyset, and `BackupRulesTest` also checks that the database is not excluded.
- **Photos are backed up with the checklists (a deliberate choice):** `filesDir/item_photos` is not excluded, because a user who gets a new phone expects the pictures of their checklists back. The photos are private to the app and already stripped of their location. Android Auto Backup stops at 25 MB per app: a user with many photos can exceed that, and then Android skips the backup of the app data, photos and checklists together. Excluding the photo folder would keep the checklist backup small but silently lose every picture on restore, so the default stays. The same choice is explained to the user in Settings ("Photos stay on your phone"), and `BackupRulesTest` checks that the photo folder is not excluded. A user who wants to be sure can export with photos (a `.zip`) from Settings.
- **Why the database is still backed up:** it holds the checklists, which users expect back on a new phone. Backup rules work on files, not tables, so the encrypted profile row travels inside `checklist.db`, as ciphertext only. No other device can decrypt it; on first read there the app erases it and shows `ProfileState.Reset` so the user can enter the profile again (risk table, section 25).

### Item photos (CL-210 to CL-216)

Photos attached to checklist items are personal data and are handled like the checklists, with extra care because pictures can carry a location.

- **Where they live:** only in app-private storage (`filesDir/item_photos`), never in the shared gallery or on external storage. The database keeps a file name, never a `content://` URI, so a revoked picker grant cannot break a photo.
- **What is stored:** every picture is decoded and re-encoded by `FilePhotoStore` (JPEG quality 80, long edge at most 1600 px, plus a 320 px thumbnail). Re-encoding writes new bytes, so EXIF metadata, including the GPS location, the camera model and the time, is dropped. A test checks that a picture with a GPS tag comes out without it, and that the orientation is applied before the tag is lost.
- **No permissions:** the manifest has no `CAMERA`, `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`. The gallery uses the system Photo Picker (the user hands over only the pictures they select) and the camera uses the system camera app through `ACTION_IMAGE_CAPTURE`. `PhotoManifestTest` fails if one of these permissions ever appears in the merged manifest, for example through a new library.
- **FileProvider:** the one provider only exposes `cacheDir/exports` and `cacheDir/camera`. The camera app writes its picture to a temporary file in `cacheDir/camera`, the app copies it into the photo store and deletes it; leftovers are removed at the next start. `filesDir/item_photos` is never exposed.
- **Not sent to AI:** photos never reach `ChecklistContext` or `ContextSnapshot`, and the `:ai` module has no code that mentions photos. `ContextPhotosTest` proves both.
- **Leaves the phone only when the user says so:** in an export that includes photos (a `.zip`, off by default) or in a PDF that includes photos (off by default). The Settings screen says this in plain words.
- **Hostile import files:** a photo archive is read as a stream, sizes are counted from the bytes actually read, names are checked before anything is written, and every image is re-encoded again on import, so the file never decides what is stored. See [import-export-format.md](import-export-format.md).
- **Backups:** see below.

## Data leaving the device

- No network use until Phase 10; then HTTPS only (`usesCleartextTraffic=false`, network security config).
- No analytics or crash-reporting SDK in v1. If one is added later: opt-in, scrubbed of personal data.
- Exports and PDFs exclude the profile unless the user explicitly ticks it, and then they see a warning. "Include my name" on PDFs is off by default.
- Sharing uses `FileProvider` URIs with temporary read grants. Files are written to `cacheDir/exports` and deleted on the next launch.
- Optional online AI sends only the minimal checklist context: title, category and item keys or names, quantities, locale and allowed unit codes. Never the profile, never notes, never database IDs. On the Gemini free tier Google may use request content to improve its products, so online AI is off by default and the opt-in screen says this plainly. See [ai-automation.md](ai-automation.md).

## Code and logs

- **One logging wrapper, a no-op in release builds (implemented, CL-183).** `AppLog` (`:domain`, `domain/common/AppLog.kt`) does nothing until a sink is installed, and only debug builds install one: `CheckListApplication` calls `AppLog.install(AndroidLogSink)` when `BuildConfig.DEBUG` is true. Messages are lambdas, so in release the text is never even built. `AndroidLogSink` (`:app`, `logging/`) is the only file that may use `android.util.Log`.
- **Enforced in CI:** detekt's `ForbiddenImport` rejects `import android.util.Log`, and `tools/checks/logging_ban.py` rejects `android.util.Log`, `Log.d/i/w/e/v/wtf/println`, `println`/`print`, `System.out`/`System.err` and `printStackTrace()` in every non-test source set of `app`, `data`, `domain` and `ai` (comments and strings are ignored). Android Lint custom checks were not needed: these two checks are free, fast and run without the Android SDK. The profile code logs nothing; never log personal data, checklist contents or AI prompts, even in debug.
- `UserProfile.toString()` prints `***` (implemented, Phase 5). This replaces the planned `@Sensitive` value class: one redacted type is simpler for screens and gives the same protection.
- AI prompts and responses are never logged in release builds.
- `debuggable=false` for release; exported components limited to the launcher activity (and a non-exported `FileProvider`).

## Secrets and signing

| Item | Where it lives | Visible to PR builds |
|---|---|---|
| Upload keystore and passwords | Bitrise Code Signing & Files | No |
| Play service account JSON | Bitrise file storage | No |
| Firebase App Distribution credentials | Bitrise secret | No |
| Gemini key for the optional CI AI summary (`GEMINI_API_KEY`, opt-in, not created yet) | GitHub Actions secret, passed only to the agent steps that use it | Same-repository pushes only; fork PRs never receive secrets |

- **Play App Signing:** Google holds the app signing key. We hold only the upload key, which can be reset through Play support if lost and revoked if leaked.
- Local release builds read signing values from environment variables only. No signing config is committed.
- `.gitignore` blocks `local.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `keystore.properties`, `google-services.json`, `service-account*.json` and `.env`. The app's Firebase config files contain identifiers, not secrets, but production ones stay out of Git anyway.
- The repository is **public**: never commit anything you would not show an interviewer.
- **gitleaks** (8.30.1 CLI, default rules) scans the whole Git history on every push, with redacted output. If it ever reports a real secret: revoke or rotate the secret first, then remove it from history; a false positive goes into a `.gitleaksignore` entry (the finding's fingerprint) with a comment, reviewed in the PR.

## CI supply chain

- Every GitHub Action is pinned to a full commit SHA with its version in a comment; Dependabot updates both.
- Workflows default to `permissions: {}`; each job requests only what it needs (`contents: read`; `security-events: write` only for the detekt SARIF upload; `pull-requests: read` only for the issue-sync lookup). No job can push, comment or merge.
- Checkouts use `persist-credentials: false`, so later steps cannot reuse the token by accident.
- The Gradle wrapper jar is validated on every push.
- Tools downloaded at run time (detekt from Maven Central, gitleaks from its GitHub release) are checked against a pinned SHA-256 before they run.
- PR-controlled values (branch name, title) reach scripts only through environment variables, never through `${{ }}` inside a `run:` script.

## AI and secrets

Never give passwords, API keys, tokens, keystore data or Bitrise/GitHub secrets to any AI tool. The CI agents are rule-based and send nothing anywhere; only the opt-in AI summary sends a report to Gemini, after the redaction pass in `tools/agents/checklist_agents/enrich.py` (tested in `test_workflows_and_cli.py`). Future agents that read diffs and logs use the same pass. AI review is advisory: it cannot approve, merge, push or change branch protection.

## Security review checklist (every PR)

- [ ] No secrets, keys or signing material added; nothing sensitive in test fixtures.
- [ ] No personal data in logs, exceptions, analytics, exports, PDFs or AI context.
- [ ] New permissions justified in the PR; no permission requested before it is needed.
- [ ] New exported components or intents reviewed.
- [ ] Untrusted input (imports, AI output, intents) validated through domain validators.

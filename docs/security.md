# Security and privacy

How CheckList protects user data and the project's secrets. To report a vulnerability, see [SECURITY.md](../SECURITY.md). Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 19.

## Status

| Control | Status |
|---|---|
| Secrets and signing files excluded by `.gitignore` | Implemented (Phase 1) |
| No signing configuration in Git; release builds are unsigned locally and in CI | Implemented (Phase 1) |
| R8 minification and resource shrinking on `release` | Implemented (Phase 1) |
| Only the launcher activity is exported | Implemented (Phase 1) |
| GitHub Actions workflow with `permissions: contents: read` | Implemented (Phase 1) |
| Dependabot updates, CODEOWNERS, private vulnerability reporting policy | Implemented (Phase 8 groundwork, CL-180) |
| Profile encryption with Tink and Android Keystore (AES-256-GCM, associated data per row and column) | Implemented (Phase 5, CL-151, CL-152) |
| Key-loss and tamper handling: erase, report, never crash | Implemented (Phase 5, CL-152) |
| Backup and device-transfer rules that exclude the profile keyset | Implemented (Phase 5, CL-153) |
| Profile data minimization (name, optional email and phone only) and redacted `toString()` | Implemented (Phase 5, CL-150) |
| Release-safe logging wrapper; lint ban on `Log.*` and `println` | Planned (deferred from Phase 5 to the Phase 8 static-analysis work) |
| Import limits and validation | Planned (Phase 6) |
| gitleaks and secret scanning in `pr-checks`; push protection | Planned (Phase 8) |
| Signing and Play credentials in Bitrise protected storage | Planned (Phase 9) |
| HTTPS-only network config, App Check | Planned (Phase 10) |
| Redaction before CI AI agents see diffs or logs | Planned (Phase 11) |

## Threat model in one paragraph

The app is single-user, offline and has no backend or account. The realistic risks are: personal data leaking through logs, backups, exports, PDFs or AI requests; a lost or stolen unlocked phone; a hostile import file; and secrets leaking from the public repository or CI. Attacks on rooted devices are out of scope.

## Data at rest

- Android already encrypts app storage (file-based encryption). Checklist data is not highly sensitive, so the Room database is **not** additionally encrypted in v1 (ADR-011, no SQLCipher).
- **Revisit trigger:** if a feature starts storing identity numbers, medical details or financial data inside checklist items, move the database to SQLCipher with a Keystore-wrapped passphrase through a tested migration.

### The encrypted profile (implemented, Phase 5)

**What is stored.** A display name (required, up to 50 characters) and an optional email and phone number. The Phase 0 sketch also listed a postal address and home/office locations; no v1 feature needs them, so they are not collected and the app asks for no location permission. Validation and normalization live in `ProfileValidator` in `:domain`.

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
- **Why the database is still backed up:** it holds the checklists, which users expect back on a new phone. Backup rules work on files, not tables, so the encrypted profile row travels inside `checklist.db`, as ciphertext only. No other device can decrypt it; on first read there the app erases it and shows `ProfileState.Reset` so the user can enter the profile again (risk table, section 25).

## Data leaving the device

- No network use until Phase 10; then HTTPS only (`usesCleartextTraffic=false`, network security config).
- No analytics or crash-reporting SDK in v1. If one is added later: opt-in, scrubbed of personal data.
- Exports and PDFs exclude the profile unless the user explicitly ticks it, and then they see a warning. "Include my name" on PDFs is off by default.
- Sharing uses `FileProvider` URIs with temporary read grants. Files are written to `cacheDir/exports` and deleted on the next launch.
- Optional online AI sends only the minimal checklist context: title, category and item keys or names, quantities, locale and allowed unit codes. Never the profile, never notes, never database IDs. On the Gemini free tier Google may use request content to improve its products, so online AI is off by default and the opt-in screen says this plainly. See [ai-automation.md](ai-automation.md).

## Code and logs

- One logging wrapper, a no-op in release builds; lint bans `Log.*` and `println` elsewhere (planned, deferred to Phase 8). The profile code logs nothing.
- `UserProfile.toString()` prints `***` (implemented, Phase 5). This replaces the planned `@Sensitive` value class: one redacted type is simpler for screens and gives the same protection.
- AI prompts and responses are never logged in release builds.
- `debuggable=false` for release; exported components limited to the launcher activity (and a non-exported `FileProvider`).

## Secrets and signing

| Item | Where it lives | Visible to PR builds |
|---|---|---|
| Upload keystore and passwords | Bitrise Code Signing & Files | No |
| Play service account JSON | Bitrise file storage | No |
| Firebase App Distribution credentials | Bitrise secret | No |
| Gemini key for CI AI agents | GitHub Actions secret | Same-repository PRs only; fork PRs never receive secrets |

- **Play App Signing:** Google holds the app signing key. We hold only the upload key, which can be reset through Play support if lost and revoked if leaked.
- Local release builds read signing values from environment variables only. No signing config is committed.
- `.gitignore` blocks `local.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `keystore.properties`, `google-services.json`, `service-account*.json` and `.env`. The app's Firebase config files contain identifiers, not secrets, but production ones stay out of Git anyway.
- The repository is **public**: never commit anything you would not show an interviewer.

## AI and secrets

Never give passwords, API keys, tokens, keystore data or Bitrise/GitHub secrets to any AI tool. CI AI agents receive diffs and logs only after a redaction pass. AI review is advisory: it cannot approve, merge, push or change branch protection.

## Security review checklist (every PR)

- [ ] No secrets, keys or signing material added; nothing sensitive in test fixtures.
- [ ] No personal data in logs, exceptions, analytics, exports, PDFs or AI context.
- [ ] New permissions justified in the PR; no permission requested before it is needed.
- [ ] New exported components or intents reviewed.
- [ ] Untrusted input (imports, AI output, intents) validated through domain validators.

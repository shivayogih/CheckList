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
| Profile encryption with Tink and Android Keystore | Planned (Phase 5) |
| Backup rules that exclude the profile | Planned (Phase 5) |
| Release-safe logging wrapper and `@Sensitive` values | Planned (Phase 5) |
| Import limits and validation (size, counts, string caps, strict JSON, control-character stripping, all-or-nothing transaction) | In progress (Phase 6, CL-160) |
| Share via FileProvider with temporary read grants; share files in `cacheDir/exports` only | In progress (Phase 6, CL-160) |
| gitleaks and secret scanning in `pr-checks`; push protection | Planned (Phase 8) |
| Signing and Play credentials in Bitrise protected storage | Planned (Phase 9) |
| HTTPS-only network config, App Check | Planned (Phase 10) |
| Redaction before CI AI agents see diffs or logs | Planned (Phase 11) |

## Threat model in one paragraph

The app is single-user, offline and has no backend or account. The realistic risks are: personal data leaking through logs, backups, exports, PDFs or AI requests; a lost or stolen unlocked phone; a hostile import file; and secrets leaking from the public repository or CI. Attacks on rooted devices are out of scope.

## Data at rest

- Android already encrypts app storage (file-based encryption). Checklist data is not highly sensitive, so the Room database is **not** additionally encrypted in v1 (ADR-011).
- The **profile** (name, email, phone, address, locations) is stored as one AES-256-GCM blob, using Tink with a non-exportable Android Keystore master key (hardware-backed where available). One blob instead of per-column encryption hides field lengths. Jetpack `security-crypto` is deprecated and not used.
- **Revisit trigger:** if a feature starts storing identity numbers, medical details or financial data inside checklist items, move the database to SQLCipher with a Keystore-wrapped passphrase through a tested migration.
- Backups (`dataExtractionRules`, `fullBackupContent`) include checklists but exclude `user_profile` and Tink keysets. Keystore keys do not move to a new device, so a restored profile blob could never be decrypted anyway; the app detects this and asks the user to re-enter the profile.

## Data leaving the device

- No network use until Phase 10; then HTTPS only (`usesCleartextTraffic=false`, network security config).
- No analytics or crash-reporting SDK in v1. If one is added later: opt-in, scrubbed of personal data.
- Exports and PDFs exclude the profile unless the user explicitly ticks it, and then they see a warning. "Include my name" on PDFs is off by default.
- Sharing uses `FileProvider` URIs with temporary read grants. Files are written to `cacheDir/exports` and deleted on the next launch.
- Optional online AI sends only the minimal checklist context: title, category and item keys or names, quantities, locale and allowed unit codes. Never the profile, never notes, never database IDs. On the Gemini free tier Google may use request content to improve its products, so online AI is off by default and the opt-in screen says this plainly. See [ai-automation.md](ai-automation.md).

## Code and logs

- One logging wrapper, a no-op in release builds. Lint bans `Log.*` and `println` elsewhere (Phase 5).
- Profile fields are wrapped in a `@Sensitive` value class whose `toString()` prints `***`.
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

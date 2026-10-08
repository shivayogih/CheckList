# Security policy

CheckList is an offline-first Android app with no backend. It stores checklists on the device and, from Phase 5, an encrypted personal profile. This page explains how to report a vulnerability and what is in scope. The technical design is in [docs/security.md](docs/security.md).

## Supported versions

The app has not been released yet. Once it is on Google Play, only the latest release gets security fixes.

| Version | Supported |
|---|---|
| Latest release on Google Play (from 1.0.0) | Yes |
| Older releases | No: update the app |
| `develop` branch and dev/staging builds | Fixed on `develop` before the next release; no separate patches |

## How to report a vulnerability

**Do not open a public issue, pull request or discussion for a vulnerability.**

1. Go to [Security > Report a vulnerability](https://github.com/shivayogih/CheckList/security/advisories/new) (GitHub private vulnerability reporting). Only the maintainer sees the report.
2. Describe the problem, the affected version or commit, the steps to reproduce, and the impact you expect.
3. You will get an acknowledgement within 7 days. Fixes for confirmed issues are prioritised over feature work, and you will be credited in the advisory unless you ask not to be.

The public [Security hardening issue form](.github/ISSUE_TEMPLATE/security.yml) is only for improvements that are safe to discuss openly, never for something exploitable.

## Scope

In scope:

- The Android app in this repository (all flavors: `dev`, `staging`, `production`).
- Exposure of personal data: the profile, checklist contents in logs, backups, exports, PDFs or AI requests.
- Weaknesses in profile encryption (Android Keystore and Tink, Phase 5).
- Import of hostile JSON files (crashes, resource exhaustion, data corruption, Phase 6).
- Exported components, intents and `FileProvider` URIs that leak data to other apps.
- Secrets or signing material committed to the repository or exposed by CI workflows (GitHub Actions, Bitrise).
- The optional online AI path (Phase 10): sending more data than the documented minimal context, or executing actions without the confirmation the design requires.

Out of scope:

- Attacks that need a rooted or already-compromised device, or physical access to an unlocked phone.
- Exhausting the free Gemini quota from a modified client: the app has no billing account linked, so the worst case is that online AI becomes unavailable (documented tradeoff).
- Vulnerabilities in Android, Google Play services, Firebase or other third-party services themselves (report those to their vendors).
- Missing hardening with no demonstrated impact (open a public hardening issue instead).
- Denial of service against GitHub, Bitrise or Firebase.

## What never to include

In a report, an issue, a PR, a log or a screenshot, never include:

- Passwords, API keys, tokens, keystore files or passwords, Play service account JSON, Firebase credentials, or Bitrise/GitHub secrets, even expired ones. If you found a leaked secret, say where it is, not what it is.
- Real personal data: names, phone numbers, addresses, locations, or exported files containing a profile. Use made-up test data.
- Working exploit code in any public place.

This rule also applies to AI tools: secrets and personal data are never pasted into an AI assistant, and the CI AI agents only receive redacted diffs and logs.

# Testing

What we test, with which tools, and where it runs. Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 18.

## Run the tests

```bash
./gradlew :domain:test :app:testDevDebugUnitTest   # all unit tests today
./gradlew :app:lintDevDebug                        # Android Lint (fails the build on errors)
```

Reports: `*/build/reports/tests/`, `app/build/reports/lint-results-devDebug.html`. On GitHub Actions they are uploaded as the `reports` artifact of every run.

## What exists today (Phase 1, plus Phase 2 in progress)

| Test | Module | What it guards |
|---|---|---|
| `SupportedLanguagesTest` | `:domain` | Tag lookup ("kn-IN" → Kannada), stored-locale mapping, the 7-language list |
| `QuantityTest` | `:domain` (CL-110, in progress) | Exact milli-unit parsing, limits, decimal places |
| `StringResourcesTest` | `:app` | Every supported language has `values-<tag>/strings.xml` with exactly the English translatable keys |
| Android Lint | `:app` | `MissingTranslation`, `ExtraTranslation` and `HardcodedText` are errors |
| `ProfileValidatorTest`, `ProfileUseCasesTest` | `:domain` (Phase 5) | Profile rules and normalization, every error at once, storage failure reported as a value, redacted `toString()` |
| `ProfileCipherTest` | `:data`, Robolectric (Phase 5) | Encryption round trip, no plain text in the blob, random nonces, every flipped byte detected, row/version/key binding |
| `KeysetProfileAeadProviderTest` | `:data`, Robolectric (Phase 5) | Keyset wrapping and reload, lost/replaced/failing master key, destroy, failure classification. A software master key stands in for the Keystore, which Robolectric lacks |
| `EncryptedProfileRepositoryTest` | `:data`, Robolectric (Phase 5) | Save, observe, clear, key loss, tampering, restore without keys, temporary Keystore failure, newer payload version |
| `BackupRulesTest` | `:app` (Phase 5) | Cloud backup, device transfer and legacy Auto Backup all exclude the profile keyset |

## Strategy by layer

| Layer | Tools | What | Runs in | Status |
|---|---|---|---|---|
| Domain unit | JUnit 4, kotlinx-coroutines-test | Use cases, validators, quantity math, localizer fallback, confirmation policy, command validator | `pr-checks` | Started; use cases Planned (Phase 2, CL-111) |
| ViewModel | JUnit, Turbine, fake repositories | State transitions, effects, empty and error states | `pr-checks` | Planned (Phase 3) |
| Room / DAO | Robolectric in-memory database; a small instrumented set | DAOs, foreign-key cascades, FTS, transactions, seed idempotency | `pr-checks` (Robolectric), Bitrise `staging` (instrumented) | Planned (Phase 2, CL-112) |
| Migrations | `MigrationTestHelper` + committed schemas | Every N → N+1 and 1 → latest | `pr-checks` | Planned (Phase 7) |
| Repository | Fakes + Room | Offline behavior, error mapping, consistency after import | `pr-checks` | Planned (Phase 2-7) |
| Import/export | Golden JSON files (`data/src/test/resources/transfer`), fakes, Robolectric Room | Valid, malformed, oversized, future version, hostile references and strings, conflicts, rollback, round trip | `pr-checks` | Implemented (Phase 6, CL-160) |
| AI mapping | JUnit 4 over the real seed catalog and real use cases with in-memory fakes | Parser tables in 7 languages (native script and transliteration), quantity and unit edge cases, validator rejecting bad tool calls, nothing executes without confirmation | `pr-checks` (`:ai:testDebugUnitTest`) | Implemented for the offline parser (Phase 10, CL-208); recorded online responses Planned (CL-211) |
| Compose UI | Compose test rule, Hilt test runner | Journeys J1-J6, navigation, quantity/unit, language switching | Bitrise `staging` (emulator), Robolectric smoke on PRs | Planned (Phase 3, 7) |
| Screenshot | Roborazzi | Key screens × 7 locales × 100% and 200% font × light and dark | `pr-checks` (diff report) | Planned (Phase 4, 7) |
| Accessibility | Semantics assertions, automated accessibility checks | Labels, touch target size, contrast | `pr-checks`, `staging` | Planned (Phase 4, 7) |
| Static analysis | Lint (now), detekt and ktlint (CL-106) | | `pr-checks` | Lint implemented; detekt/ktlint Planned (Phase 8) |

## Rules

- Prefer **fakes over mocks** for repositories. A fake that behaves like the real thing finds more bugs than a mock that returns what the test expects.
- Inject time and IDs (`Clock`, `IdGenerator` in `:domain`) so tests are deterministic. Never use `Thread.sleep`; use `runTest` and virtual time.
- Never skip, `@Ignore` or delete a test to get a green build. A flaky test gets an issue and a fix.
- Test names describe behavior with backticks: `` `adding a duplicate master item offers to increase quantity` ``.
- Every bug fix adds the test that would have caught it.
- Test data is made up. Never use real personal data, even in fixtures.

## Coverage

Coverage is reported, not gated globally (a global gate encourages junk tests). Target: at least 80% line coverage for `:domain`. Reported in CI from Phase 7.

## Manual checks per release (Phase 4 onward)

- TalkBack walkthrough of journeys J1-J6 (script in [accessibility.md](accessibility.md)).
- Each language at 200% font scale, light and dark.
- Airplane mode: every core feature works.

## Results tracking

Test results for CI builds are recorded in [project-management/test-results.csv](project-management/test-results.csv) (one row per suite per build). See [project-management/README.md](project-management/README.md).

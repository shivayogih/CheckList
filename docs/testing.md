# Testing

What we test, with which tools, and where it runs. Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 18.

## Run the tests

```bash
./gradlew :domain:test :data:testDebugUnitTest :ai:testDebugUnitTest :app:testDevDebugUnitTest   # all unit tests
./gradlew :domain:jacocoTestReport :data:createDebugUnitTestCoverageReport :app:createDevDebugUnitTestCoverageReport   # coverage
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
| `DatabaseMigrationTest` | `:data`, Robolectric (Phase 7, CL-173) | Schema v1 is created from the committed export, schemas are numbered without gaps, every version step has a migration in `DatabaseMigrations.ALL`, and v1 data survives migrating to the latest version |
| `AppGraphTest` | `:app`, Robolectric + Hilt (CL-172) | The production Hilt graph builds and runs: singletons, `MainActivity`, every ViewModel including the assisted ones |
| `ChecklistJourneysTest` | `:app`, Robolectric + Hilt + Compose (CL-171) | Create a checklist; add a catalog and a custom item; tick and see progress; edit quantity and unit; delete with Undo |
| `LanguageJourneyTest` | `:app`, Robolectric SDK 32 (CL-171) | Settings → Language → ಕನ್ನಡ stores the app locale and the recreated UI is in Kannada |
| `ScreenAccessibilityTest` | `:app`, Robolectric, font scale 1 and 2 (CL-174) | Every screen: labels, 48dp targets, a heading, checkbox state, no clipped text ([accessibility.md](accessibility.md#audit-results-phase-4)) |
| `ThemeContrastTest` | `:app` (CL-144) | WCAG AA contrast of the light and dark schemes |
| `StringFormatTest`, `LocaleNumbersTest` | `:app` (CL-142) | Plural forms, placeholder parity, positional `%N$s`, escaped `%`; Western digits in all 7 languages |

## Strategy by layer

| Layer | Tools | What | Runs in | Status |
|---|---|---|---|---|
| Domain unit | JUnit 4, kotlinx-coroutines-test | Use cases, validators, quantity math, localizer fallback, confirmation policy, command validator | `pr-checks` | Started; use cases Planned (Phase 2, CL-111) |
| ViewModel | JUnit, Turbine, fake repositories (`app/src/test/.../testing`) | State transitions, effects, empty and error states | `pr-checks` | In progress (Phase 3, CL-133, CL-135): Home, create, add categories, detail, add items, item editor, profile |
| Room / DAO | Robolectric in-memory database; a small instrumented set | DAOs, foreign-key cascades, FTS, transactions, seed idempotency | `pr-checks` (Robolectric), Bitrise `staging` (instrumented) | Planned (Phase 2, CL-112) |
| Migrations | `MigrationTestHelper` + committed schemas (Robolectric, schemas added as unit-test assets through the AGP Variant API) | Every N → N+1 and 1 → latest | `pr-checks` | Scaffolding implemented for v1 (CL-173); add `MIGRATION_N_N+1` to `DatabaseMigrations.ALL` and a data check with each new version |
| Repository | Fakes + Room | Offline behavior, error mapping, consistency after import | `pr-checks` | Planned (Phase 2-7) |
| Import/export | Golden JSON files (`data/src/test/resources/transfer`), fakes, Robolectric Room | Valid, malformed, oversized, future version, hostile references and strings, conflicts, rollback, round trip | `pr-checks` | Implemented (Phase 6, CL-160) |
| AI mapping | JUnit 4 over the real seed catalog and real use cases with in-memory fakes | Parser tables in 7 languages (native script and transliteration), quantity and unit edge cases, validator rejecting bad tool calls, nothing executes without confirmation | `pr-checks` (`:ai:testDebugUnitTest`) | Implemented for the offline parser (Phase 10, CL-208); recorded online responses Planned (CL-211) |
| Compose UI | Compose test rule on Robolectric (SDK 34), `HiltTestApplication`, `HiltComponentActivity` (debug) | Journeys J1-J2 and J4: create, add items, tick, quantity/unit, delete with Undo, Kannada | `pr-checks` (unit tests) | Implemented (Phase 7, CL-171); helpers in `app/src/test/.../testing/ui/` advance Robolectric's clock while waiting; emulator runs Planned |
| Screenshot | Roborazzi | Key screens × 7 locales × 100% and 200% font × light and dark | `pr-checks` (diff report) | Planned (Phase 4, 7) |
| Accessibility | `assertAccessible()` semantics checks, `ThemeContrastTest` | Labels, touch target size, headings, state, clipping at 200%, contrast | `pr-checks` | Implemented (Phase 4/7, CL-144, CL-174) |
| Static analysis | Lint (now), detekt and ktlint (CL-106) | | `pr-checks` | Lint implemented; detekt/ktlint Planned (Phase 8) |

## Rules

- Prefer **fakes over mocks** for repositories. A fake that behaves like the real thing finds more bugs than a mock that returns what the test expects.
- Inject time and IDs (`Clock`, `IdGenerator` in `:domain`) so tests are deterministic. Never use `Thread.sleep`; use `runTest` and virtual time.
- Never skip, `@Ignore` or delete a test to get a green build. A flaky test gets an issue and a fix.
- Test names describe behavior with backticks: `` `adding a duplicate master item offers to increase quantity` ``.
- Every bug fix adds the test that would have caught it.
- Test data is made up. Never use real personal data, even in fixtures.

## Coverage

Coverage is reported, not gated globally (a global gate encourages junk tests). Target: at least 80% line coverage for `:domain`.

Implemented (CL-175) with JaCoCo 0.8.15: Gradle's `jacoco` plugin for `:domain` and AGP's built-in unit-test coverage (`enableUnitTestCoverage` on debug) for `:data` and `:app`, with Robolectric-loaded classes included. The CI `Coverage` step writes a per-module table to the run summary (`tools/ci/coverage_summary.py`) and uploads the XML and HTML reports as the `coverage` artifact. There is no failing threshold.

## Manual checks per release (Phase 4 onward)

- TalkBack walkthrough of journeys J1-J6 (script in [accessibility.md](accessibility.md)).
- Each language at 200% font scale, light and dark.
- Airplane mode: every core feature works.

## Results tracking

Test results for CI builds are recorded in [project-management/test-results.csv](project-management/test-results.csv) (one row per suite per build). See [project-management/README.md](project-management/README.md).

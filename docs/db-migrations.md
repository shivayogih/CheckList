# Database migrations

How the Room database changes between app versions without ever losing a user's data (CL-320). Background: [database.md](database.md), [ADR-002](adr/), tests in [testing.md](testing.md).

## The rules

| Rule | Enforced by |
|---|---|
| **Never** `fallbackToDestructiveMigration*`, `allowDataLossOnRecovery(true)`, `deleteDatabase` in production code | `DatabaseMigrationPolicyTest` (build fails) and `tools/checks/schema_gate.py` (Quality workflow) |
| Every version bump has an explicit `Migration(N, N+1)` in `DatabaseMigrations.ALL`, or an `AutoMigration` with a spec when Room needs one (renames, deletions) | `DatabaseMigrationTest`, `DatabaseMigrationPolicyTest`, gate |
| `exportSchema = true`; `data/schemas/<db>/N.json` is committed for every version, numbered without gaps | Room build, build step "Room schema is committed", gate |
| A schema file that exists on `develop` is **frozen**: it is never edited or deleted. A change is a new version | gate (compares with the base branch) |
| Additive first: new tables, new nullable columns or columns with a default, new indices. Prefer this over any rewrite | review; `MigrationStepTest` fails when a step changes or drops existing rows |
| No data loss: every row and column of the old version is present after the step unless the step is listed in `intentionalChanges` with a reason | `MigrationStepTest` |
| A migration SQL line that contains `DELETE FROM`, `DROP TABLE`, `DROP COLUMN` needs a `// data-safe: <why>` comment | `DatabaseMigrationPolicyTest` |
| Primary keys stay `TEXT` UUID v4, times stay epoch milliseconds, quantities stay `quantity_milli INTEGER` plus `unit_code` | review; fixtures carry such values, so a rewrite that mangles them fails |
| The FTS table `item_search_fts` is derived data: when a step touches `master_item`, `master_item_translation` or `category`, rebuild its rows in the same migration (same transaction) | `MigrationStepTest` queries the index after the chain |
| A step that rebuilds a table (create new, copy, drop old, rename) must recreate its indices, foreign keys and triggers. The `category` name triggers are also re-created on every open (`SchemaTriggers`), which is the safety net | `MigrationStepTest` compares the migrated structure with a fresh install and checks the triggers |
| Catalogue (seed) upgrades are versioned by `seedVersion` in `catalog.json`, upsert by `canonical_key` and never touch user-renamed or hidden rows | `SeedLoaderTest`, `IndiaCatalogSeedTest`, and `MigrationStepTest` (seed applied to a migrated database, twice) |

## What happens on a user's phone

`CheckListDatabase.build` opens the file through `SafeOpenHelperFactory` (`:data`, `local/database`):

1. **Downgrade.** If the file's `user_version` is higher than this app's version (the user installed an older build), the file is **not opened, changed or deleted**. The open fails with `DatabaseFailure.Downgrade`. Installing the newer version again brings everything back. We never write a "down" migration.
2. **Backup.** If the file is older, it is first checkpointed and copied to `noBackupFilesDir/db-backups/pre-migration.db` (+ `pre-migration.version`). The copy is written to a temporary file and moved into place, and only when the source passes `PRAGMA quick_check`: the previous good backup is kept when the file is not healthy. One backup is kept (the last good one). It lives in the no-backup folder because the database itself is already in Auto Backup.
3. **Migrate.** Room runs the steps. The framework opens an upgrade inside **one transaction**, so a failing step (or a failing schema validation) rolls everything back.
4. **Failure.** If opening fails after a backup was made, the helper closes the database and restores the backup (copy next to the file, then an atomic move), and reports `DatabaseFailure.MigrationFailed(from, to, restoredBackup)`. A damaged file that is not an upgrade is reported as `OpenFailed` and **left on disk**; the default "delete the corrupt database" reaction of Android and Room is switched off (`KeepFileOnCorruption`).
5. **No crash loop.** The failure is remembered for the process: every later access throws the same `DatabaseOpenException` immediately instead of retrying and restoring again. `DatabaseHealth.state` (a `StateFlow` of `DatabaseState`) publishes `Ready(migratedFrom)` or `Failed(failure)` for the UI. The next launch tries again from the restored file, so an app update that fixes the migration succeeds without any user action.
6. Everything is logged through `AppLog` (tag `DbMigration`, no personal data). If the backup itself cannot be made (storage full), the upgrade still runs inside its transaction and a warning is logged.

User-facing screen (CL-321): `MainActivity` watches `DatabaseHealth.state` and, on `Failed`, shows `DatabaseProblemScreen` instead of the app. It says in plain words that the lists are safe (migration failed and the pre-update copy was put back), that a newer version saved them (downgrade), or that nothing was deleted (other open failure), in all seven languages. Its only action closes the app, because the next launch retries the open.

## Adding a migration (checklist)

1. Change the entities. Keep it additive. Bump `@Database(version = N+1)` **and** `CheckListDatabase.VERSION` (they are checked to be equal).
2. Build once so KSP writes `data/schemas/<db>/N+1.json`. Commit it. Do not touch `N.json`.
3. Add `MIGRATION_N_N+1` to `DatabaseMigrations` and to `ALL`. Use the SQL from the new JSON (`createSql`). Only `CREATE ... IF NOT EXISTS` and `ALTER TABLE ... ADD COLUMN` for plain additions. For a rebuild: create the new table, `INSERT INTO new SELECT ... FROM old`, drop old with a `// data-safe:` comment, rename, recreate indices and triggers, rebuild FTS rows if needed.
4. Add `N+1 -> ...` to `MigrationFixtures.populate`: the previous version's data plus rows for what is new (raw SQL against the new columns). Never edit older cases.
5. Run `:data:testDebugUnitTest`. `MigrationStepTest` now migrates every version step and the full chain from v1 with this data and checks integrity, foreign keys, kept rows, the exported schema and the structure of a fresh install. Add a dedicated test for anything the step changes on purpose (and list it in `intentionalChanges`).
6. Add a row to the version history below.
7. Seed or catalogue change in the same release? Raise `seedVersion` in `catalog.json`; no schema change is needed.
8. Push. The Quality workflow runs `tools/checks/schema_gate.py`; the build workflow runs the tests and "Room schema is committed".

## Version history

| Step | Release | What changed | Data impact |
|---|---|---|---|
| 1 → 2 | CL-210 item photos | New table `item_photo` and index `(checklist_item_id, position)` | None: no existing row is read or rewritten |

## Backups (Auto Backup and device transfer)

The rules exclude only the wrapped profile keyset (`sharedpref/checklist_profile_keyset.xml`), see [security.md](security.md). The database and the photo folder are backed up, so a restored phone gets its checklists back. After a restore onto a phone with a newer app the normal path above upgrades the restored file; a restore of a newer file onto an older app is the downgrade case (not opened). The pre-migration copy is in `noBackupFilesDir`, which Android never backs up or transfers. `BackupRulesTest` checks that the database is not excluded.

## Limits

- Robolectric and the SQLite bundled with it stand in for devices: the real framework helper and Room code run, but not on API 26 SQLite 3.18. The SQL used by migrations must stay within what API 26 supports (no `ALTER TABLE ... DROP COLUMN` before SQLite 3.35, no `RENAME COLUMN` before 3.25, no `UPSERT` before 3.24; rebuild the table instead).
- A migration that is correct but slow runs on the first launch after the update; keep each step to simple statements and measure large rewrites.

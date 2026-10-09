"""Tests for schema_gate.py. Run: python3 -m pytest tools/checks"""

import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import schema_gate  # noqa: E402

DB_DIR = "data/schemas/com.example.CheckListDatabase"
DATABASE_KT = "data/src/main/kotlin/x/CheckListDatabase.kt"
MIGRATIONS_KT = "data/src/main/kotlin/x/DatabaseMigrations.kt"
FIXTURES_KT = "data/src/test/kotlin/x/MigrationFixtures.kt"


def write(root: Path, name: str, text: str) -> None:
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def schema(root: Path, version: int, extra: str = "") -> None:
    write(root, f"{DB_DIR}/{version}.json", json.dumps({"database": {"version": version, "x": extra}}))


def database(root: Path, version: int) -> None:
    write(root, DATABASE_KT, f"@Database(\n    version = {version},\n)\nclass D {{ companion object {{ const val VERSION = {version} }} }}\n")


def migrations(root: Path, *steps: int) -> None:
    body = "\n".join(f"val M{s} = object : Migration({s}, {s + 1}) {{}}" for s in steps)
    write(root, MIGRATIONS_KT, f"object DatabaseMigrations {{\n{body}\n}}\n")


def repo(root: Path, latest: int = 2) -> None:
    for version in range(1, latest + 1):
        schema(root, version)
    database(root, latest)
    migrations(root, *range(1, latest))
    write(root, FIXTURES_KT, "\n".join(f"{v} -> populate{v}()" for v in range(1, latest + 1)))
    write(root, "docs/db-migrations.md", "| 1 → 2 | photos |\n")


def git(root: Path, *args: str) -> None:
    subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=root, check=True, capture_output=True)


def commit_base(root: Path) -> None:
    git(root, "init", "-q", "-b", "develop")
    git(root, "add", "-A")
    git(root, "commit", "-q", "-m", "base")
    git(root, "checkout", "-q", "-b", "feature")


def test_consistent_repository_passes(tmp_path: Path) -> None:
    repo(tmp_path)
    assert schema_gate.run(tmp_path, None) == []


def test_destructive_fallback_is_reported_but_comments_are_not(tmp_path: Path) -> None:
    repo(tmp_path)
    write(tmp_path, "app/src/main/kotlin/A.kt", "// never fallbackToDestructiveMigration()\n/* deleteDatabase( */\nval ok = 1\n")
    assert schema_gate.run(tmp_path, None) == []
    write(tmp_path, "app/src/main/kotlin/B.kt", "val b = builder.fallbackToDestructiveMigration()\n")
    problems = schema_gate.run(tmp_path, None)
    assert len(problems) == 1 and "B.kt" in problems[0]


def test_missing_migration_for_a_new_version_is_reported(tmp_path: Path) -> None:
    repo(tmp_path)
    schema(tmp_path, 3)
    database(tmp_path, 3)
    problems = schema_gate.run(tmp_path, None)
    assert any("no Migration(2, 3)" in p for p in problems)


def test_version_constant_must_match_latest_schema(tmp_path: Path) -> None:
    repo(tmp_path)
    database(tmp_path, 1)
    assert any("latest exported schema is 2" in p for p in schema_gate.run(tmp_path, None))


def test_gap_in_schema_numbers_is_reported(tmp_path: Path) -> None:
    repo(tmp_path, latest=3)
    (tmp_path / DB_DIR / "2.json").unlink()
    assert any("without gaps" in p for p in schema_gate.run(tmp_path, None))


def test_released_schema_is_frozen(tmp_path: Path) -> None:
    repo(tmp_path)
    commit_base(tmp_path)
    schema(tmp_path, 2, extra="edited")
    problems = schema_gate.run(tmp_path, "develop")
    assert any("2.json is a released schema" in p for p in problems)


def test_new_version_needs_fixture_and_history_row(tmp_path: Path) -> None:
    repo(tmp_path)
    commit_base(tmp_path)
    schema(tmp_path, 3)
    database(tmp_path, 3)
    migrations(tmp_path, 1, 2)
    migrations(tmp_path, 1, 2, 3)
    problems = schema_gate.run(tmp_path, "develop")
    assert any("no case for version 3" in p for p in problems)
    assert any("no row for 2 -> 3" in p for p in problems)

    write(tmp_path, FIXTURES_KT, "1 -> a\n2 -> b\n3 -> c\n")
    write(tmp_path, "docs/db-migrations.md", "| 1 → 2 | a |\n| 2 → 3 | b |\n")
    assert schema_gate.run(tmp_path, "develop") == []


def test_unknown_base_only_runs_the_static_checks(tmp_path: Path) -> None:
    repo(tmp_path)
    assert schema_gate.run(tmp_path, "origin/does-not-exist") == []

#!/usr/bin/env python3
"""CI gate for database migrations (CL-320, docs/db-migrations.md).

Fails when the schema story of the repository is inconsistent, so a new app version can never ship a
database change without a migration and its tests:

1. No production source (app, data, domain, ai) calls a destructive Room fallback or deletes the database.
2. The exported schemas data/schemas/<db>/N.json are numbered 1..N without gaps, each names its own
   version, and N equals both ``@Database(version = N)`` and ``CheckListDatabase.VERSION``.
3. Every step k -> k+1 has a ``Migration(k, k+1)`` in DatabaseMigrations.kt (or an ``AutoMigration``).
4. Against the base branch: an exported schema that already exists there is frozen (not edited, not
   deleted); a *new* schema version needs fixture data for it in MigrationFixtures.kt and a row for the
   step in the version history table of docs/db-migrations.md.

Usage: python3 tools/checks/schema_gate.py [--root .] [--base origin/develop]
Standard library only. Exit code 1 when something is found. Without a resolvable base ref (shallow
clone) only checks 1 to 3 run, and a warning says so.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

MODULES = ("app", "data", "domain", "ai")
FORBIDDEN = (
    "fallbackToDestructiveMigration",
    "allowDataLossOnRecovery(true",
    "deleteDatabase(",
    "deleteDatabaseFile",
)
SCHEMA_GLOB = "data/schemas/*/*.json"
DOC = "docs/db-migrations.md"


def strip_comments(source: str) -> str:
    """Drops block and line comments so documentation may mention the banned names."""
    source = re.sub(r"/\*.*?\*/", " ", source, flags=re.DOTALL)
    return "\n".join(line.split("//", 1)[0] for line in source.splitlines())


def find_one(root: Path, pattern: str) -> Path | None:
    matches = sorted(root.glob(pattern))
    return matches[0] if matches else None


def schema_versions(root: Path) -> dict[int, Path]:
    return {int(p.stem): p for p in root.glob(SCHEMA_GLOB) if p.stem.isdigit()}


def check_sources(root: Path) -> list[str]:
    problems = []
    for module in MODULES:
        for path in sorted((root / module / "src" / "main").rglob("*")):
            if path.suffix not in (".kt", ".java") or not path.is_file():
                continue
            code = strip_comments(path.read_text(encoding="utf-8"))
            for token in FORBIDDEN:
                if token in code:
                    problems.append(f"{path.relative_to(root)}: '{token}' is banned (data loss), see {DOC}")
    return problems


def check_versions(root: Path) -> tuple[list[str], int | None]:
    problems: list[str] = []
    versions = schema_versions(root)
    if not versions:
        return [f"no exported schema found under {SCHEMA_GLOB}"], None
    latest = max(versions)
    if sorted(versions) != list(range(1, latest + 1)):
        problems.append(f"exported schemas are not numbered 1..{latest} without gaps: {sorted(versions)}")
    for number, path in versions.items():
        declared = json.loads(path.read_text(encoding="utf-8")).get("database", {}).get("version")
        if declared != number:
            problems.append(f"{path.relative_to(root)} declares version {declared}, expected {number}")

    database = find_one(root, "data/src/main/**/CheckListDatabase.kt")
    if database is None:
        problems.append("CheckListDatabase.kt not found")
        return problems, latest
    code = strip_comments(database.read_text(encoding="utf-8"))
    annotation = re.search(r"\bversion\s*=\s*(\d+)", code)
    constant = re.search(r"const\s+val\s+VERSION\s*=\s*(\d+)", code)
    if not annotation or int(annotation.group(1)) != latest:
        problems.append(f"@Database(version) is {annotation and annotation.group(1)}, latest exported schema is {latest}")
    if not constant or int(constant.group(1)) != latest:
        problems.append(f"CheckListDatabase.VERSION is {constant and constant.group(1)}, latest exported schema is {latest}")

    migrations = find_one(root, "data/src/main/**/DatabaseMigrations.kt")
    covered: set[tuple[int, int]] = set()
    if migrations is not None:
        covered |= {(int(a), int(b)) for a, b in re.findall(r"Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)", strip_comments(migrations.read_text(encoding="utf-8")))}
    covered |= {(int(a), int(b)) for a, b in re.findall(r"AutoMigration\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)", code)}
    for step in range(1, latest):
        if (step, step + 1) not in covered:
            problems.append(f"schema {step + 1} exists but there is no Migration({step}, {step + 1}) or AutoMigration; see {DOC}")
    return problems, latest


def git(root: Path, *args: str) -> str | None:
    result = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=False)
    return result.stdout if result.returncode == 0 else None


def resolve_base(root: Path, base: str) -> str | None:
    if git(root, "rev-parse", "--verify", "--quiet", f"{base}^{{commit}}") is None:
        return None
    merge_base = (git(root, "merge-base", base, "HEAD") or "").strip()
    head = (git(root, "rev-parse", "HEAD") or "").strip()
    if not merge_base:
        return None
    if merge_base == head:  # on the base branch itself: compare with the previous commit
        parent = git(root, "rev-parse", "--verify", "--quiet", "HEAD^1")
        return parent.strip() if parent else merge_base
    return merge_base


def check_against_base(root: Path, base: str) -> list[str]:
    resolved = resolve_base(root, base)
    if resolved is None:
        print(f"warning: base '{base}' not available, skipping the comparison with the base branch", file=sys.stderr)
        return []
    problems = []
    changes = git(root, "diff", "--name-status", "--no-renames", resolved, "--", "data/schemas") or ""
    added_versions: set[int] = set()
    for line in changes.splitlines():
        status, name = line.split("\t", 1)
        stem = Path(name).stem
        if status == "A" and stem.isdigit():
            added_versions.add(int(stem))
        elif status in ("M", "D"):
            problems.append(f"{name} is a released schema and is frozen ({'edited' if status == 'M' else 'deleted'}); add a new version instead, see {DOC}")
    untracked = git(root, "ls-files", "--others", "--exclude-standard", "--", "data/schemas") or ""
    added_versions |= {int(Path(n).stem) for n in untracked.split() if Path(n).stem.isdigit()}

    fixtures = find_one(root, "data/src/test/**/MigrationFixtures.kt")
    doc = root / DOC
    for version in sorted(added_versions):
        fixture_text = fixtures.read_text(encoding="utf-8") if fixtures else ""
        if not re.search(rf"\b{version}\s*->", fixture_text):
            problems.append(f"schema {version} is new but MigrationFixtures.populate has no case for version {version}")
        doc_text = doc.read_text(encoding="utf-8") if doc.exists() else ""
        if not re.search(rf"\b{version - 1}\s*(?:→|->|to)\s*{version}\b", doc_text):
            problems.append(f"schema {version} is new but the version history in {DOC} has no row for {version - 1} -> {version}")
    return problems


def run(root: Path, base: str | None) -> list[str]:
    problems = check_sources(root)
    version_problems, _ = check_versions(root)
    problems += version_problems
    if base:
        problems += check_against_base(root, base)
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".", type=Path)
    parser.add_argument("--base", default="origin/develop")
    arguments = parser.parse_args()
    problems = run(arguments.root.resolve(), arguments.base)
    for problem in problems:
        print(f"error: {problem}")
    if problems:
        print(f"{len(problems)} database migration problem(s); see {DOC}")
        return 1
    print("database migration gate: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())

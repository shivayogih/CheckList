#!/usr/bin/env python3
"""Bans direct logging and console output in production sources (design 19.3, CL-183).

Every module logs through ``AppLog`` (``:domain``), which does nothing in release builds. This
check fails when Kotlin or Java files under ``<module>/src/main`` (or another non-test source set)
use any of:

* ``android.util.Log`` (import, fully qualified call, or ``Log.d/i/w/e/v/wtf/println``)
* ``println(...)`` / ``print(...)``
* ``System.out`` / ``System.err``
* ``printStackTrace()``

Test source sets (``test``, ``androidTest``, ``testFixtures``...) are not checked. The only allowed
user of ``android.util.Log`` is ``AndroidLogSink.kt``. Comments and string literals are ignored.

Usage: python3 tools/checks/logging_ban.py [repo-root]   (exit code 1 when something is found)
Standard library only, so it runs anywhere Python 3.10+ is installed.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

MODULES = ("app", "data", "domain", "ai")
ALLOWED_FILES = frozenset({"AndroidLogSink.kt"})
TEST_SOURCE_SET = re.compile(r"(?i)test")

RULES: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("android.util.Log is banned; use AppLog", re.compile(r"\bandroid\.util\.Log\b")),
    ("Log.* is banned; use AppLog", re.compile(r"(?<![\w.])Log\s*\.\s*(?:v|d|i|w|e|wtf|println)\s*\(")),
    ("println/print is banned; use AppLog", re.compile(r"(?<![\w.])(?:kotlin\.io\.)?print(?:ln)?\s*\(")),
    ("System.out/System.err is banned; use AppLog", re.compile(r"\bSystem\s*\.\s*(?:out|err)\b")),
    ("printStackTrace() is banned; use AppLog.e", re.compile(r"\.\s*printStackTrace\s*\(")),
)


@dataclass(frozen=True)
class Violation:
    path: str
    line: int
    message: str
    text: str

    def __str__(self) -> str:
        return f"{self.path}:{self.line}: {self.message}: {self.text}"


def strip_comments_and_strings(source: str) -> str:
    """Blanks out comments and string/char literals, keeping line breaks so line numbers stay right."""
    out: list[str] = []
    i, n = 0, len(source)
    while i < n:
        c = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            end = source.find("\n", i)
            end = n if end == -1 else end
            i = end
        elif c == "/" and nxt == "*":
            depth, j = 1, i + 2
            while j < n and depth:  # Kotlin block comments nest
                if source.startswith("/*", j):
                    depth, j = depth + 1, j + 2
                elif source.startswith("*/", j):
                    depth, j = depth - 1, j + 2
                else:
                    j += 1
            out.append(re.sub(r"[^\n]", " ", source[i:j]))
            i = j
        elif source.startswith('"""', i):
            end = source.find('"""', i + 3)
            end = n if end == -1 else end + 3
            out.append('""' + re.sub(r"[^\n]", " ", source[i + 2 : end - 2]) + '""')
            i = end
        elif c in "\"'":
            j = i + 1
            while j < n and source[j] != c and source[j] != "\n":
                j += 2 if source[j] == "\\" else 1
            out.append(c + " " * (j - i - 1) + c)
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def check_source(path: str, source: str) -> list[Violation]:
    if Path(path).name in ALLOWED_FILES:
        return []
    code_lines = strip_comments_and_strings(source).split("\n")
    raw_lines = source.split("\n")
    found: list[Violation] = []
    for number, code in enumerate(code_lines, start=1):
        for message, pattern in RULES:
            if pattern.search(code):
                found.append(Violation(path, number, message, raw_lines[number - 1].strip()))
                break
    return found


def production_sources(root: Path) -> list[Path]:
    files: list[Path] = []
    for module in MODULES:
        src = root / module / "src"
        if not src.is_dir():
            continue
        for source_set in sorted(p for p in src.iterdir() if p.is_dir()):
            if TEST_SOURCE_SET.search(source_set.name):
                continue
            files += sorted(p for p in source_set.rglob("*") if p.suffix in (".kt", ".java") and p.is_file())
    return files


def main(argv: list[str]) -> int:
    root = Path(argv[1] if len(argv) > 1 else ".").resolve()
    violations: list[Violation] = []
    files = production_sources(root)
    for file in files:
        violations += check_source(str(file.relative_to(root)), file.read_text(encoding="utf-8"))
    for violation in violations:
        print(violation)
    print(f"logging_ban: {len(files)} files checked, {len(violations)} violation(s).")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

"""Tests for logging_ban.py. Run: python3 -m pytest tools/checks"""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import logging_ban  # noqa: E402


def messages(source: str, path: str = "app/src/main/kotlin/X.kt") -> list[str]:
    return [v.message for v in logging_ban.check_source(path, source)]


@pytest.mark.parametrize(
    "line",
    [
        "import android.util.Log",
        "android.util.Log.d(TAG, msg)",
        "Log.d(TAG, \"x\")",
        "Log.wtf(TAG, e)",
        "Log . e(TAG, \"x\")",
        "println(\"hello\")",
        "print(value)",
        "kotlin.io.println(x)",
        "System.out.println(x)",
        "System.err.write(bytes)",
        "error.printStackTrace()",
    ],
)
def test_banned_calls_are_reported(line: str) -> None:
    assert len(messages(f"fun f() {{\n    {line}\n}}\n")) == 1


@pytest.mark.parametrize(
    "line",
    [
        "AppLog.d(TAG) { \"fine\" }",
        "AppLog.e(TAG, error) { \"fine\" }",
        "writer.print(page)",
        "canvas.println(x)",
        "val blueprint = blueprint(x)",
        "// println(\"commented out\")",
        "/* Log.d(TAG, x) */",
        "val s = \"println(not code)\"",
        "val t = \"\"\"System.out is only text here\"\"\"",
        "val c = '\"'; val d = \"Log.d(\"",
    ],
)
def test_allowed_code_is_not_reported(line: str) -> None:
    assert messages(f"fun f() {{\n    {line}\n}}\n") == []


def test_nested_block_comments_are_skipped_and_lines_kept() -> None:
    source = "/* outer /* inner */ println(x) */\nval a = 1\nprintln(a)\n"
    found = logging_ban.check_source("a/src/main/A.kt", source)
    assert [v.line for v in found] == [3]


def test_android_log_sink_is_the_only_allowed_file() -> None:
    source = "import android.util.Log\nfun f() = Log.d(\"t\", \"m\")\n"
    assert messages(source, "app/src/main/kotlin/logging/AndroidLogSink.kt") == []
    assert len(messages(source, "app/src/main/kotlin/logging/OtherSink.kt")) == 2


def test_only_production_source_sets_are_scanned(tmp_path: Path) -> None:
    for rel in [
        "app/src/main/kotlin/A.kt",
        "app/src/dev/kotlin/B.kt",
        "app/src/test/kotlin/C.kt",
        "app/src/androidTest/kotlin/D.kt",
        "data/src/testFixtures/kotlin/E.kt",
        "domain/src/main/kotlin/F.java",
        "domain/src/main/resources/G.txt",
    ]:
        file = tmp_path / rel
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text("fun f() = println(1)\n", encoding="utf-8")

    scanned = sorted(str(p.relative_to(tmp_path)) for p in logging_ban.production_sources(tmp_path))

    assert scanned == ["app/src/dev/kotlin/B.kt", "app/src/main/kotlin/A.kt", "domain/src/main/kotlin/F.java"]
    assert logging_ban.main(["logging_ban", str(tmp_path)]) == 1


def test_repository_main_sources_are_clean() -> None:
    root = Path(__file__).resolve().parents[2]
    assert logging_ban.main(["logging_ban", str(root)]) == 0

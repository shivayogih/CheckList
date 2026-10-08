"""Shared fixtures. Tests run from any directory: tools/agents is put on sys.path here."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

import pytest

AGENTS_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = AGENTS_DIR.parents[1]
sys.path.insert(0, str(AGENTS_DIR))

LOCALE_TEXT = {
    "kn": ("ನನ್ನ ಪಟ್ಟಿಗಳು", "ಪುಟ %1$d / %2$d"),
    "hi": ("मेरी सूचियाँ", "पृष्ठ %1$d / %2$d"),
    "mr": ("माझ्या याद्या", "पान %1$d / %2$d"),
    "ta": ("என் பட்டியல்கள்", "பக்கம் %1$d / %2$d"),
    "te": ("నా జాబితాలు", "పేజీ %1$d / %2$d"),
    "ml": ("എന്റെ പട്ടികകൾ", "പേജ് %1$d / %2$d"),
}


def strings_xml(entries: dict[str, str], extra: str = "") -> str:
    body = "\n".join(f'    <string name="{k}">{v}</string>' for k, v in entries.items())
    return f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{body}\n{extra}</resources>\n'


@pytest.fixture
def res_repo(tmp_path: Path) -> Path:
    """A repository with consistent English plus six translations."""
    res = tmp_path / "app/src/main/res"
    (res / "values").mkdir(parents=True)
    (res / "values/strings.xml").write_text(
        strings_xml(
            {"home_title": "My checklists", "pdf_page": "Page %1$d of %2$d"},
            '    <string name="app_name" translatable="false">CheckList</string>\n',
        ),
        encoding="utf-8",
    )
    for tag, (title, page) in LOCALE_TEXT.items():
        (res / f"values-{tag}").mkdir()
        (res / f"values-{tag}/strings.xml").write_text(
            strings_xml({"home_title": title, "pdf_page": page}), encoding="utf-8"
        )
    return tmp_path


def git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args], check=True, capture_output=True, text=True,
        env={**os.environ, "GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "t@example.com",
             "GIT_COMMITTER_NAME": "Test", "GIT_COMMITTER_EMAIL": "t@example.com",
             "GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_NOSYSTEM": "1"},
    ).stdout


@pytest.fixture
def git_repo(tmp_path: Path):
    """An empty git repository plus a helper that commits a file with a subject."""
    repo = tmp_path / "repo"
    repo.mkdir()
    git(repo, "init", "-q", "-b", "develop")
    counter = {"n": 0}

    def commit(subject: str) -> None:
        counter["n"] += 1
        (repo / f"f{counter['n']}.txt").write_text(subject, encoding="utf-8")
        git(repo, "add", ".")
        git(repo, "commit", "-q", "-m", subject)

    return repo, commit

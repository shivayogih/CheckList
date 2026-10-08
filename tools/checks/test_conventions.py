"""Tests for conventions.py. Run: python3 -m pytest tools/checks"""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from conventions import Commit, check, check_branch, check_subject  # noqa: E402


def commit(subject: str, parents: int = 1, author: str = "Claude", email: str = "noreply@anthropic.com") -> Commit:
    return Commit("0123456789abcdef", parents, author, email, subject)


@pytest.mark.parametrize("subject", ["CL-1 Add thing", "CL-181 Add PR checks", "CL-220 Run ADK agents in CI"])
def test_valid_subjects(subject: str) -> None:
    assert check_subject(subject) == (None, None)


@pytest.mark.parametrize(
    "subject",
    ["Add thing", "CL-1 add thing", "CL-1: Add thing", "cl-1 Add thing", "CL- Add", "CL-1Add", "[CL-1] Add", "WIP"],
)
def test_invalid_subjects(subject: str) -> None:
    error, _ = check_subject(subject)
    assert error is not None


def test_long_subject_is_only_a_warning() -> None:
    error, warning = check_subject("CL-1 Add " + "x" * 80)
    assert error is None
    assert warning is not None and "72" in warning


def test_trailers_in_the_body_do_not_matter() -> None:
    # Only the subject line (%s) is read; the body with its trailers never reaches the check.
    message = (
        "CL-181 Add PR convention checks\n\nWhy.\n\n"
        "Co-Authored-By: Example Assistant <noreply@example.com>\n"
        "Claude-Session: https://claude.ai/code/session_example\n"
    )
    subject = message.splitlines()[0]
    assert check([commit(subject)], "feature/CL-181-ci-quality-adk").errors == []


def test_merge_and_dependabot_commits_are_exempt() -> None:
    commits = [
        commit("Merge branch 'develop' into feature/CL-1-x", parents=2),
        commit("Bump kotlin from 2.4.10 to 2.4.20", author="dependabot[bot]"),
        commit("Bump agp", author="someone", email="49699333+dependabot[bot]@users.noreply.github.com"),
        commit("CL-1 Add thing"),
    ]

    result = check(commits, "feature/CL-1-x")

    assert result.errors == []
    assert (result.checked, result.exempt) == (1, 3)


def test_bad_commit_is_reported_with_its_sha() -> None:
    result = check([commit("fix stuff")], "feature/CL-1-x")
    assert len(result.errors) == 1
    assert result.errors[0].startswith("0123456789")


@pytest.mark.parametrize(
    "branch",
    [
        "feature/CL-181-ci-quality-adk",
        "feature/CL-1-x",
        "fix/CL-155-profile-reset-race",
        "hotfix/CL-456-crash-on-start",
        "release/1.0.0",
        "release/10.2.3",
        "feature/CL-210-item-photos",
    ],
)
def test_documented_branch_names(branch: str) -> None:
    assert check_branch(branch) is None


@pytest.mark.parametrize(
    "branch",
    [
        "feature/add-thing",
        "feature/CL-1",
        "feature/CL-1-Add-Thing",
        "feature/CL-1-add_thing",
        "feature/CL-1-add--thing",
        "bugfix/CL-1-x",
        "release/1.0",
        "release/v1.0.0",
        "develop",
        "main",
        "my-branch",
    ],
)
def test_undocumented_branch_names(branch: str) -> None:
    assert check_branch(branch) is not None


def test_dependabot_branches_are_exempt() -> None:
    assert check_branch("dependabot/gradle/androidx-1234") is None
    assert check_branch("anything", pr_author="dependabot[bot]") is None


def test_branch_error_fails_even_with_good_commits() -> None:
    assert len(check([commit("CL-1 Add thing")], "wip").errors) == 1


def test_pr_title_follows_the_subject_rule() -> None:
    good = [commit("CL-1 Add thing")]
    assert check(good, "feature/CL-1-x", title="CL-1 Add thing").errors == []
    assert check(good, "feature/CL-1-x", title="Add thing").errors[0].startswith("PR title:")
    assert check(good, "dependabot/gradle/x", pr_author="dependabot[bot]", title="Bump x").errors == []


def test_read_commits_uses_only_the_subject_of_messages_with_trailers(tmp_path: Path, monkeypatch) -> None:
    import os
    import subprocess

    import conventions

    env = {**os.environ, "GIT_AUTHOR_NAME": "T", "GIT_AUTHOR_EMAIL": "t@example.com", "GIT_COMMITTER_NAME": "T",
           "GIT_COMMITTER_EMAIL": "t@example.com", "GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_NOSYSTEM": "1"}

    def git(*args: str) -> str:
        return subprocess.run(["git", *args], cwd=tmp_path, env=env, check=True, capture_output=True, text=True).stdout

    git("init", "-q", "-b", "develop")
    git("commit", "-q", "--allow-empty", "-m", "CL-1 Base")
    base = git("rev-parse", "HEAD").strip()
    git("commit", "-q", "--allow-empty", "-m",
        "CL-2 Add thing\n\nBody line.\n\nCo-Authored-By: Example Assistant <noreply@example.com>\n"
        "Claude-Session: https://claude.ai/code/session_example")
    monkeypatch.chdir(tmp_path)

    commits = conventions.read_commits(base, "HEAD")

    assert [c.subject for c in commits] == ["CL-2 Add thing"]
    assert check(commits, "feature/CL-2-thing").errors == []

#!/usr/bin/env python3
"""Commit-message and branch-name rules for pull requests (CL-185, docs/branching-strategy.md).

* Every commit subject in the PR matches ``^CL-\\d+ [A-Z]`` (issue ID, space, capitalised
  imperative verb). Only the subject line is checked, so bodies and trailers such as
  ``Co-Authored-By:`` or ``Claude-Session:`` are free-form.
* Merge commits and commits authored by ``dependabot[bot]`` are exempt.
* Subjects longer than 72 characters are reported as a warning (not a failure).
* The PR title follows the same subject rule, because it becomes the squash-merge subject.
* The PR's head branch matches one of the documented patterns: ``feature/CL-<id>-<slug>``,
  ``fix/CL-<id>-<slug>``, ``hotfix/CL-<id>-<slug>``, ``release/X.Y.Z``; Dependabot's
  ``dependabot/...`` branches are exempt.

Usage (GitHub Actions passes the PR's base and head SHAs and head branch):
    python3 tools/checks/conventions.py --base <sha> --head <sha> --branch <name> [--author <login>] [--title <text>]
Prints GitHub annotations and a markdown summary (to $GITHUB_STEP_SUMMARY when set); exit 1 on errors.
Standard library only.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass

SUBJECT = re.compile(r"^CL-\d+ [A-Z]")
MAX_SUBJECT = 72
BRANCH = re.compile(r"^(?:(?:feature|fix|hotfix)/CL-\d+-[a-z0-9]+(?:-[a-z0-9]+)*|release/\d+\.\d+\.\d+)$")
BOT_AUTHORS = {"dependabot[bot]"}
BOT_EMAIL = re.compile(r"^\d+\+dependabot\[bot\]@users\.noreply\.github\.com$")
BOT_BRANCH = re.compile(r"^dependabot/")


@dataclass(frozen=True)
class Commit:
    sha: str
    parents: int
    author: str
    email: str
    subject: str


@dataclass
class Result:
    errors: list[str]
    warnings: list[str]
    checked: int = 0
    exempt: int = 0


def is_exempt(commit: Commit) -> bool:
    return commit.parents > 1 or commit.author in BOT_AUTHORS or bool(BOT_EMAIL.match(commit.email))


def check_subject(subject: str) -> tuple[str | None, str | None]:
    """Returns (error, warning) for one subject line."""
    error = None if SUBJECT.match(subject) else f'"{subject}" does not match ^CL-\\d+ [A-Z] (e.g. "CL-121 Add checklist creation")'
    warning = f'"{subject}" is {len(subject)} characters; keep subjects to {MAX_SUBJECT}' if len(subject) > MAX_SUBJECT else None
    return error, warning


def check_branch(branch: str, pr_author: str = "") -> str | None:
    if BOT_BRANCH.match(branch) or pr_author in BOT_AUTHORS:
        return None
    if BRANCH.match(branch):
        return None
    return (
        f'branch "{branch}" does not match feature/CL-<id>-<slug>, fix/CL-<id>-<slug>, '
        "hotfix/CL-<id>-<slug> or release/X.Y.Z (slug: lowercase words joined by hyphens)"
    )


def check(commits: list[Commit], branch: str, pr_author: str = "", title: str | None = None) -> Result:
    result = Result(errors=[], warnings=[])
    branch_error = check_branch(branch, pr_author)
    if branch_error:
        result.errors.append(branch_error)
    if title is not None and pr_author not in BOT_AUTHORS:
        title_error, title_warning = check_subject(title)
        if title_error:
            result.errors.append(f"PR title: {title_error}")
        if title_warning:
            result.warnings.append(f"PR title: {title_warning}")
    for commit in commits:
        if is_exempt(commit):
            result.exempt += 1
            continue
        result.checked += 1
        error, warning = check_subject(commit.subject)
        if error:
            result.errors.append(f"{commit.sha[:10]}: {error}")
        if warning:
            result.warnings.append(f"{commit.sha[:10]}: {warning}")
    return result


def read_commits(base: str, head: str) -> list[Commit]:
    out = subprocess.run(
        ["git", "log", "--format=%H%x1f%P%x1f%an%x1f%ae%x1f%s", f"{base}..{head}"],
        check=True, capture_output=True, text=True,
    ).stdout
    commits = []
    for line in out.splitlines():
        sha, parents, author, email, subject = line.split("\x1f", 4)
        commits.append(Commit(sha, len(parents.split()), author, email, subject))
    return commits


def to_markdown(result: Result, branch: str) -> str:
    status = "FAILED" if result.errors else "passed"
    lines = [
        "## Commit and branch conventions", "",
        f"**Result:** {status}. Branch `{branch}`; {result.checked} commit(s) checked, {result.exempt} exempt "
        "(merge commits, Dependabot).", "",
    ]
    lines += [f"- **error:** {e}" for e in result.errors]
    lines += [f"- warning: {w}" for w in result.warnings]
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--author", default="", help="PR author login (dependabot[bot] is exempt)")
    parser.add_argument("--title", help="PR title (becomes the squash-merge subject)")
    args = parser.parse_args(argv)

    result = check(read_commits(args.base, args.head), args.branch, args.author, args.title)
    for error in result.errors:
        print(f"::error title=Convention::{error}")
    for warning in result.warnings:
        print(f"::warning title=Convention::{warning}")
    markdown = to_markdown(result, args.branch)
    print(markdown)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(markdown)
    return 1 if result.errors else 0


if __name__ == "__main__":
    sys.exit(main())

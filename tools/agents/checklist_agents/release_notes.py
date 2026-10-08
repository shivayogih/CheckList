"""Release notes from ``CL-`` commits since the last release tag (CL-222).

Same rules as the Bitrise script ``ci/scripts/release-notes.sh`` (Phase 9), re-implemented here so
this agent does not depend on that branch:

* only subjects shaped ``CL-<id> <Summary>`` count; merge commits are skipped;
* engineering work (CI, build, docs, tests, refactors, versions, signing...) and subjects marked
  ``[internal]`` stay out of the user-facing notes; so do issues whose ``issues.csv`` type is not
  ``Feature`` or ``Bug``;
* URLs, e-mail addresses, @mentions and ``(#12)`` PR numbers are removed;
* each note reads ``- <Summary> (CL-<id>)``, duplicates are dropped, at most 15 notes and at most
  500 characters in total (Google Play's limit); with nothing left: ``Bug fixes and improvements.``

On top of the Play text, the agent writes a developer report: every ``CL-`` commit grouped into
new features, fixes and internal work, using the issue type and title from ``issues.csv``.
"""

from __future__ import annotations

import csv
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

from .report import Report, Severity

PLAY_LIMIT = 500
MAX_NOTES = 15
FALLBACK = "Bug fixes and improvements."
ISSUES_CSV = "docs/project-management/issues.csv"

SUBJECT = re.compile(r"^(CL-\d+)\s+(.+)$")
INTERNAL = re.compile(
    r"\[internal\]|\b(ci|bitrise|workflows?|pipelines?|gradle|builds?|lint|detekt|ktlint|tests?|testing|docs?"
    r"|documentation|adr|readme|refactor|chore|architecture|release notes|versions?|versioning|dependenc(y|ies)"
    r"|deps|bump|issue tracker|csv|gitignore|hygiene|secrets?|signing|keystore|proguard|r8|phase [0-9]+"
    r"|agents?|adk|gitleaks|logging)\b",
    re.IGNORECASE,
)
USER_FACING_TYPES = {"Feature", "Bug"}


@dataclass(frozen=True)
class Change:
    issue: str
    summary: str
    sha: str = ""


def git(repo: Path, *args: str) -> str:
    return subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True).stdout


def previous_tag(repo: Path, to_ref: str = "HEAD") -> str | None:
    """Newest ``v*`` tag strictly before ``to_ref`` (so building tag v1.1.0 gives the notes since v1.0.0)."""
    try:
        git(repo, "rev-parse", "--verify", "--quiet", f"{to_ref}^")
        return git(repo, "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", f"{to_ref}^").strip() or None
    except subprocess.CalledProcessError:
        return None


def read_commits(repo: Path, from_ref: str | None, to_ref: str = "HEAD") -> list[tuple[str, str]]:
    revision = f"{from_ref}..{to_ref}" if from_ref else to_ref
    out = git(repo, "log", "--no-merges", "--format=%H%x1f%s", revision)
    return [tuple(line.split("\x1f", 1)) for line in out.splitlines() if "\x1f" in line]  # type: ignore[misc]


def sanitize(summary: str) -> str:
    summary = re.sub(r"https?://\S+", "", summary)
    summary = re.sub(r"[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}", "", summary)
    summary = re.sub(r"(^|\s)@[\w-]+", r"\1", summary)
    summary = re.sub(r"\s*\(#\d+\)", "", summary)
    return " ".join(summary.split())


def parse_changes(commits: list[tuple[str, str]]) -> list[Change]:
    changes = []
    for sha, subject in commits:
        match = SUBJECT.match(subject.strip())
        if match:
            changes.append(Change(match.group(1), match.group(2).strip(), sha))
    return changes


def read_issues(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        return {}
    with path.open(encoding="utf-8", newline="") as handle:
        return {row["Issue ID"]: row for row in csv.DictReader(handle) if row.get("Issue ID")}


def is_user_facing(change: Change, issues: dict[str, dict[str, str]]) -> bool:
    if INTERNAL.search(change.summary):
        return False
    issue_type = issues.get(change.issue, {}).get("Type", "")
    return not issue_type or issue_type in USER_FACING_TYPES


def play_notes(changes: list[Change], issues: dict[str, dict[str, str]], max_notes: int = MAX_NOTES) -> str:
    notes: list[str] = []
    for change in changes:
        if not is_user_facing(change, issues):
            continue
        summary = sanitize(change.summary)
        note = f"- {summary} ({change.issue})"
        if summary and note not in notes:
            notes.append(note)
        if len(notes) >= max_notes:
            break
    text = ""
    for note in notes:
        candidate = f"{text}\n{note}" if text else note
        if len(candidate) > PLAY_LIMIT:
            break
        text = candidate
    return text or FALLBACK


def build_report(
    changes: list[Change], issues: dict[str, dict[str, str]], range_label: str, max_notes: int = MAX_NOTES
) -> tuple[Report, str]:
    report = Report("Release notes")
    text = play_notes(changes, issues, max_notes)
    report.summary.append(f"Range: `{range_label}`, {len(changes)} `CL-` commit(s)")
    report.summary.append(f"Google Play text: {len(text)} of {PLAY_LIMIT} characters")
    if not changes:
        report.add(Severity.WARNING, "no-changes", "No CL- commits in the range", range_label)

    groups: dict[str, list[str]] = {"New": [], "Fixes": [], "Internal": []}
    seen: set[tuple[str, str]] = set()
    for change in changes:
        key = (change.issue, change.summary)
        if key in seen:
            continue
        seen.add(key)
        row = issues.get(change.issue)
        if row is None:
            report.add(Severity.WARNING, "unknown-issue", f"{change.issue} is not in issues.csv", change.sha[:10])
        title = f" ({row['Title']})" if row and row.get("Title") else ""
        line = f"- {change.issue} {sanitize(change.summary)}{title}"
        if not is_user_facing(change, issues):
            groups["Internal"].append(line)
        elif (row and row.get("Type") == "Bug") or (not row and re.match(r"(?i)fix", change.summary)):
            groups["Fixes"].append(line)
        else:
            groups["New"].append(line)

    report.sections.append(("Google Play: what's new (en-US)", f"```text\n{text}\n```"))
    for heading, lines in groups.items():
        report.sections.append((f"{heading} ({len(lines)})", "\n".join(lines) if lines else "_None._"))
    return report, text


def run(repo: Path, to_ref: str = "HEAD", from_ref: str | None = None, max_notes: int = MAX_NOTES) -> tuple[Report, str]:
    start = from_ref or previous_tag(repo, to_ref)
    changes = parse_changes(read_commits(repo, start, to_ref))
    label = f"{start}..{to_ref}" if start else f"{to_ref} (no earlier v* tag: whole history)"
    return build_report(changes, read_issues(repo / ISSUES_CSV), label, max_notes)

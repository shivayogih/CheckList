"""Issue tracker sync checks for ``docs/project-management/issues.csv`` (CL-223).

Errors (fail the job): wrong header, wrong field count, malformed or duplicate IDs, values outside
the allowed lists in docs/project-management/README.md, bad dates, and ``IN_PROGRESS`` rows
without a branch.

Warnings: branch names that break the naming rules, ``IN_PROGRESS`` rows whose branch does not
exist on the remote, and stale rows: still ``IN_PROGRESS``/``CODE_REVIEW``/``CI_*`` although their
branch is already merged into ``develop``/``main`` or GitHub reports the branch's PR as merged.

The git and GitHub lookups are optional inputs, so the rules themselves are pure and testable.
"""

from __future__ import annotations

import csv
import io
import json
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable
from datetime import date
from pathlib import Path

from .report import Report, Severity

ISSUES_CSV = "docs/project-management/issues.csv"

HEADER = [
    "Issue ID", "Title", "Description", "Type", "Priority", "Status", "Assignee", "Branch", "Pull Request",
    "Build Number", "Environment", "Test Status", "AI Review Status", "Created Date", "Updated Date", "Release",
]
STATUSES = [
    "BACKLOG", "TODO", "IN_PROGRESS", "CODE_REVIEW", "CI_RUNNING", "CI_FAILED", "READY_FOR_QA",
    "QA_IN_PROGRESS", "READY_FOR_RELEASE", "RELEASED", "DONE",
]
ACTIVE = {"IN_PROGRESS", "CODE_REVIEW", "CI_RUNNING", "CI_FAILED"}
ALLOWED = {
    "Type": {"Feature", "Bug", "Technical Debt", "Security", "CI/CD", "Documentation", "Task", "Epic"},
    "Priority": {"Low", "Medium", "High", "Critical"},
    "Status": set(STATUSES),
    "Environment": {"", "DEV", "STAGING", "PRODUCTION"},
    "Test Status": {"", "PENDING", "PASSED", "FAILED", "NOT_APPLICABLE"},
    "AI Review Status": {"", "PENDING", "PASSED", "CHANGES_SUGGESTED", "NOT_APPLICABLE"},
}
ISSUE_ID = re.compile(r"^CL-\d+$")
# Branch naming (docs/branching-strategy.md).
BRANCH = re.compile(r"^(?:(?:feature|fix|hotfix)/CL-\d+-[a-z0-9]+(?:-[a-z0-9]+)*|release/\d+\.\d+\.\d+|develop|main)$")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

# branch -> True when GitHub reports a merged PR for it, False when not, None when unknown.
PrLookup = Callable[[str], "bool | None"]


def check(
    csv_text: str,
    remote_branches: set[str] | None = None,
    merged_branches: set[str] | None = None,
    pr_merged: PrLookup | None = None,
) -> Report:
    report = Report("Issue tracker sync")
    rows = list(csv.reader(io.StringIO(csv_text)))
    if not rows:
        report.add(Severity.ERROR, "empty", "issues.csv is empty", ISSUES_CSV)
        return report
    if rows[0] != HEADER:
        report.add(Severity.ERROR, "header", f"Header must be exactly: {', '.join(HEADER)}", f"{ISSUES_CSV}:1")
        return report

    seen: dict[str, int] = {}
    statuses: dict[str, int] = {}
    stale = 0
    for line, fields in enumerate(rows[1:], start=2):
        where = f"{ISSUES_CSV}:{line}"
        if not any(fields):
            continue
        if len(fields) != len(HEADER):
            report.add(Severity.ERROR, "field-count", f"{len(fields)} fields, expected {len(HEADER)}", where)
            continue
        row = dict(zip(HEADER, fields))
        issue = row["Issue ID"]
        where = f"{where} {issue}"
        if not ISSUE_ID.match(issue):
            report.add(Severity.ERROR, "issue-id", f"`{issue}` is not shaped CL-<number>", where)
        elif issue in seen:
            report.add(Severity.ERROR, "duplicate-id", f"{issue} also on line {seen[issue]}", where)
        else:
            seen[issue] = line

        for column, allowed in ALLOWED.items():
            if row[column] not in allowed:
                values = ", ".join(sorted(v for v in allowed if v))
                report.add(Severity.ERROR, "invalid-value", f"{column} `{row[column]}` is not one of: {values}", where)
        statuses[row["Status"]] = statuses.get(row["Status"], 0) + 1
        _check_dates(report, row, where)
        stale += _check_branch(report, row, where, remote_branches, merged_branches, pr_merged)

    report.summary.append(f"{len(seen)} issues; " + ", ".join(f"{s} {statuses[s]}" for s in STATUSES if s in statuses))
    if remote_branches is None:
        report.summary.append("Remote branches not checked (no git remote information)")
    if stale:
        report.summary.append(f"{stale} row(s) look stale: merged work still marked active")
    return report


def _check_dates(report: Report, row: dict[str, str], where: str) -> None:
    parsed = {}
    for column in ("Created Date", "Updated Date"):
        value = row[column]
        if not DATE.match(value):
            report.add(Severity.ERROR, "date", f"{column} `{value}` is not YYYY-MM-DD", where)
            continue
        try:
            parsed[column] = date.fromisoformat(value)
        except ValueError:
            report.add(Severity.ERROR, "date", f"{column} `{value}` is not a real date", where)
    if len(parsed) == 2 and parsed["Updated Date"] < parsed["Created Date"]:
        report.add(Severity.ERROR, "date", "Updated Date is before Created Date", where)


def _check_branch(
    report: Report,
    row: dict[str, str],
    where: str,
    remote_branches: set[str] | None,
    merged_branches: set[str] | None,
    pr_merged: PrLookup | None,
) -> int:
    branch, status = row["Branch"].strip(), row["Status"]
    if not branch:
        if status == "IN_PROGRESS":
            report.add(Severity.ERROR, "no-branch", "IN_PROGRESS needs the Branch column filled in", where)
        return 0
    if not BRANCH.match(branch):
        report.add(Severity.WARNING, "branch-name", f"`{branch}` does not follow docs/branching-strategy.md", where)
    if status not in ACTIVE or branch in ("develop", "main"):
        return 0
    if status == "IN_PROGRESS" and remote_branches is not None and branch not in remote_branches:
        report.add(Severity.WARNING, "branch-missing", f"`{branch}` does not exist on the remote", where)
    merged = merged_branches is not None and branch in merged_branches
    if not merged and pr_merged is not None:
        merged = pr_merged(branch) is True
    if merged:
        report.add(Severity.WARNING, "stale-status", f"`{branch}` is merged but the issue is still {status}", where)
        return 1
    return 0


# --- git and GitHub lookups used by the CLI (not needed by the rules above) ---


def git_remote_branches(repo: Path, remote: str = "origin") -> set[str]:
    out = subprocess.run(
        ["git", "-C", str(repo), "for-each-ref", "--format=%(refname:strip=3)", f"refs/remotes/{remote}"],
        check=True, capture_output=True, text=True,
    ).stdout
    return {line for line in out.splitlines() if line and line != "HEAD"}


def git_merged_branches(repo: Path, targets: tuple[str, ...] = ("develop", "main"), remote: str = "origin") -> set[str]:
    merged: set[str] = set()
    for target in targets:
        result = subprocess.run(
            ["git", "-C", str(repo), "for-each-ref", "--format=%(refname:strip=3)",
             f"--merged=refs/remotes/{remote}/{target}", f"refs/remotes/{remote}"],
            capture_output=True, text=True,
        )
        if result.returncode == 0:
            merged |= {line for line in result.stdout.splitlines() if line not in ("HEAD", target)}
    return merged - set(targets)


def github_pr_lookup(repository: str, token: str, api: str = "https://api.github.com") -> PrLookup:
    """Asks GitHub whether a PR from ``branch`` was merged (catches squash merges git cannot see)."""
    owner = repository.split("/", 1)[0]
    cache: dict[str, bool | None] = {}

    def lookup(branch: str) -> bool | None:
        if branch not in cache:
            query = urllib.parse.urlencode({"state": "closed", "head": f"{owner}:{branch}", "per_page": 20})
            request = urllib.request.Request(
                f"{api}/repos/{repository}/pulls?{query}",
                headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                         "X-GitHub-Api-Version": "2022-11-28"},
            )
            try:
                with urllib.request.urlopen(request, timeout=20) as response:  # noqa: S310 (fixed https host)
                    pulls = json.load(response)
                cache[branch] = any(p.get("merged_at") for p in pulls)
            except (urllib.error.URLError, TimeoutError, ValueError):
                cache[branch] = None
        return cache[branch]

    return lookup

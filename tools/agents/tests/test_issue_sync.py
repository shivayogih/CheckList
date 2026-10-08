from __future__ import annotations

import csv
import io

import pytest
from conftest import REPO_ROOT

from checklist_agents import issue_sync
from checklist_agents.report import Severity

BASE = {
    "Issue ID": "CL-1", "Title": "T", "Description": "D", "Type": "Feature", "Priority": "High",
    "Status": "IN_PROGRESS", "Assignee": "Claude", "Branch": "feature/CL-1-thing", "Pull Request": "",
    "Build Number": "", "Environment": "", "Test Status": "PENDING", "AI Review Status": "NOT_APPLICABLE",
    "Created Date": "2026-10-01", "Updated Date": "2026-10-02", "Release": "1.0.0",
}


def csv_text(*rows: dict[str, str]) -> str:
    out = io.StringIO()
    writer = csv.DictWriter(out, fieldnames=issue_sync.HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return out.getvalue()


def row(**changes: str) -> dict[str, str]:
    return {**BASE, **changes}


def codes(report, severity=None) -> list[str]:
    return sorted(f.code for f in report.findings if severity is None or f.severity == severity)


def test_valid_file_passes() -> None:
    report = issue_sync.check(csv_text(row(), row(**{"Issue ID": "CL-2", "Status": "DONE", "Branch": ""})))
    assert report.findings == []
    assert "2 issues" in report.summary[0]


def test_header_must_match() -> None:
    assert codes(issue_sync.check("Issue ID,Title\nCL-1,T\n")) == ["header"]


def test_field_count() -> None:
    text = csv_text(row()) + "CL-2,only,three\n"
    assert codes(issue_sync.check(text)) == ["field-count"]


def test_duplicate_and_malformed_ids() -> None:
    report = issue_sync.check(csv_text(row(), row(), row(**{"Issue ID": "CL-x"})))
    assert codes(report, Severity.ERROR) == ["duplicate-id", "issue-id"]


@pytest.mark.parametrize(
    "column, value",
    [("Status", "WIP"), ("Type", "Test"), ("Priority", "Urgent"), ("Environment", "QA"),
     ("Test Status", "OK"), ("AI Review Status", "LGTM")],
)
def test_values_outside_the_allowed_lists_are_errors(column: str, value: str) -> None:
    report = issue_sync.check(csv_text(row(**{column: value})))
    assert codes(report) == ["invalid-value"]
    assert column in report.findings[0].message


@pytest.mark.parametrize(
    "created, updated",
    [("2026-13-01", "2026-10-02"), ("01/10/2026", "2026-10-02"), ("2026-10-05", "2026-10-02")],
)
def test_dates(created: str, updated: str) -> None:
    assert codes(issue_sync.check(csv_text(row(**{"Created Date": created, "Updated Date": updated})))) == ["date"]


def test_in_progress_needs_a_branch() -> None:
    assert codes(issue_sync.check(csv_text(row(Branch="")))) == ["no-branch"]


def test_branch_naming_is_a_warning() -> None:
    report = issue_sync.check(csv_text(row(Branch="my-branch")))
    assert codes(report, Severity.WARNING) == ["branch-name"]
    assert not report.failed


def test_in_progress_branch_must_exist_on_the_remote() -> None:
    text = csv_text(row())
    assert codes(issue_sync.check(text, remote_branches={"develop"})) == ["branch-missing"]
    assert issue_sync.check(text, remote_branches={"feature/CL-1-thing"}).findings == []
    assert issue_sync.check(text, remote_branches=None).findings == []  # unknown: not checked


def test_merged_branch_with_an_active_status_is_stale() -> None:
    text = csv_text(row(), row(**{"Issue ID": "CL-2", "Status": "CODE_REVIEW"}), row(**{"Issue ID": "CL-3", "Status": "DONE"}))

    report = issue_sync.check(text, remote_branches={"feature/CL-1-thing"}, merged_branches={"feature/CL-1-thing"})

    assert codes(report) == ["stale-status", "stale-status"]
    assert any("2 row(s) look stale" in line for line in report.summary)


def test_github_lookup_catches_squash_merges() -> None:
    asked: list[str] = []

    def lookup(branch: str) -> bool | None:
        asked.append(branch)
        return True

    report = issue_sync.check(csv_text(row()), merged_branches=set(), pr_merged=lookup)

    assert codes(report) == ["stale-status"]
    assert asked == ["feature/CL-1-thing"]


def test_unknown_github_answer_is_not_stale() -> None:
    assert issue_sync.check(csv_text(row()), pr_merged=lambda _b: None).findings == []


def test_git_helpers_read_remote_refs(git_repo) -> None:
    from conftest import git

    repo, commit = git_repo
    commit("CL-1 Base")
    git(repo, "branch", "feature/CL-1-thing")
    git(repo, "update-ref", "refs/remotes/origin/develop", "develop")
    git(repo, "update-ref", "refs/remotes/origin/feature/CL-1-thing", "feature/CL-1-thing")
    git(repo, "switch", "-q", "-c", "feature/CL-2-other")
    commit("CL-2 Not merged")
    git(repo, "update-ref", "refs/remotes/origin/feature/CL-2-other", "feature/CL-2-other")

    assert issue_sync.git_remote_branches(repo) == {"develop", "feature/CL-1-thing", "feature/CL-2-other"}
    assert issue_sync.git_merged_branches(repo) == {"feature/CL-1-thing"}


def test_repository_issues_csv_has_no_errors() -> None:
    text = (REPO_ROOT / issue_sync.ISSUES_CSV).read_text(encoding="utf-8")
    report = issue_sync.check(text)
    assert [f for f in report.findings if f.severity == Severity.ERROR] == []

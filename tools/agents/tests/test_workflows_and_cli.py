"""The agents run end to end through Google ADK's runner, with no API key and no network."""

from __future__ import annotations

from pathlib import Path

import pytest
from google.adk import Workflow

from checklist_agents import __main__ as cli
from checklist_agents import enrich, workflows


@pytest.fixture(autouse=True)
def no_keys(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in ("GEMINI_API_KEY", "GOOGLE_API_KEY", "GITHUB_TOKEN", "GITHUB_STEP_SUMMARY"):
        monkeypatch.delenv(name, raising=False)


def test_every_agent_is_an_adk_workflow() -> None:
    for factory in workflows.AGENTS.values():
        assert isinstance(factory(), Workflow)


def test_translation_agent_runs_through_adk(res_repo: Path) -> None:
    result = workflows.run_agent("translations", {"repo": str(res_repo)})

    assert result["findings"] == []
    assert result["markdown"].startswith("## Translation consistency")


def test_issue_sync_agent_runs_without_git(tmp_path: Path) -> None:
    from test_issue_sync import csv_text, row

    target = tmp_path / "docs/project-management/issues.csv"
    target.parent.mkdir(parents=True)
    target.write_text(csv_text(row(Status="WIP")), encoding="utf-8")

    result = workflows.run_agent("issue-sync", {"repo": str(tmp_path), "use_git": False})

    assert [f["code"] for f in result["findings"]] == ["invalid-value"]


def test_release_notes_agent_runs_through_adk(git_repo) -> None:
    repo, commit = git_repo
    commit("CL-1 Add sharing")

    result = workflows.run_agent("release-notes", {"repo": str(repo)})

    assert result["play_text"] == "- Add sharing (CL-1)"


def test_cli_writes_summary_and_play_file_and_returns_status(git_repo, tmp_path: Path) -> None:
    repo, commit = git_repo
    commit("CL-1 Add sharing")
    summary, out_dir = tmp_path / "summary.md", tmp_path / "whatsnew"

    code = cli.main(["release-notes", "--repo", str(repo), "--summary", str(summary), "--out-dir", str(out_dir), "--enrich"])

    assert code == 0
    assert (out_dir / "whatsnew-en-US").read_text(encoding="utf-8") == "- Add sharing (CL-1)\n"
    text = summary.read_text(encoding="utf-8")
    assert "## Release notes" in text and "AI summary not enabled" in text


def test_cli_fails_on_errors(res_repo: Path) -> None:
    (res_repo / "app/src/main/res/values-kn/strings.xml").unlink()
    assert cli.main(["translations", "--repo", str(res_repo)]) == 1


def test_enrichment_is_off_without_a_key() -> None:
    assert not enrich.is_enabled({})
    assert not enrich.is_enabled({"GEMINI_API_KEY": "  "})
    assert enrich.is_enabled({"GEMINI_API_KEY": "x"})
    assert enrich.enrich("translations", "## Report", env={}) is None


def test_enrichment_agent_builds_without_network() -> None:
    agent = enrich.build_agent("release-notes", "gemini-flash-latest")
    assert agent.name == "release_notes_reviewer"
    assert "500 characters" in agent.instruction


@pytest.mark.parametrize(
    "secret",
    [
        "AIza" + "B" * 35,
        "ghp_" + "a" * 36,
        "github_pat_" + "a" * 30,
        "AKIA" + "A" * 16,
        "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----",
        "password=hunter22",
    ],
)
def test_redaction_removes_secrets(secret: str) -> None:
    text = enrich.prepare_input(f"before {secret} after")
    assert secret not in text
    assert "REDACTED" in text


def test_long_reports_are_truncated() -> None:
    assert enrich.prepare_input("x" * 30_000).endswith("[report truncated]")

"""The three agents as Google ADK workflows (google-adk 2.x graph workflows).

Each agent is a ``Workflow`` of deterministic function nodes, ``collect -> analyse -> render``,
run by ADK's ``InMemoryRunner``. No node calls a model, so no API key, network or Google Cloud
project is needed. ADK earns its place by giving every agent the same shape, runner, event trail
and session; the optional model step (``enrich.py``) is an ADK ``Agent`` that plugs into the same
runtime when a key is configured.

Node inputs and outputs are plain JSON-friendly values (dicts and strings), because ADK passes a
node's return value to the next node and records it on the event.
"""

from __future__ import annotations

import asyncio
import json
import os
import subprocess
from pathlib import Path
from typing import Any

from google.adk import Event, Workflow
from google.adk.runners import InMemoryRunner
from google.genai import types

from . import issue_sync, release_notes, translations
from .report import Report

APP_NAME = "checklist_ci_agents"
USER_ID = "github-actions"


# --- translation consistency ---


def _translations_collect(node_input: str) -> dict[str, Any]:
    args = json.loads(node_input)
    return {"repo": args["repo"], "res_dir": args.get("res_dir", translations.DEFAULT_RES_DIR)}


def _translations_analyse(node_input: dict[str, Any]) -> dict[str, Any]:
    return translations.check(Path(node_input["repo"]), node_input["res_dir"]).to_dict()


# --- release notes ---


def _release_notes_collect(node_input: str) -> dict[str, Any]:
    args = json.loads(node_input)
    repo = Path(args["repo"])
    to_ref = args.get("to_ref") or "HEAD"
    start = args.get("from_ref") or release_notes.previous_tag(repo, to_ref)
    commits = release_notes.read_commits(repo, start, to_ref)
    return {
        "commits": [list(c) for c in commits],
        "issues": release_notes.read_issues(repo / release_notes.ISSUES_CSV),
        "range": f"{start}..{to_ref}" if start else f"{to_ref} (no earlier v* tag: whole history)",
        "max_notes": int(args.get("max_notes") or release_notes.MAX_NOTES),
    }


def _release_notes_analyse(node_input: dict[str, Any]) -> dict[str, Any]:
    changes = release_notes.parse_changes([tuple(c) for c in node_input["commits"]])  # type: ignore[misc]
    report, text = release_notes.build_report(changes, node_input["issues"], node_input["range"], node_input["max_notes"])
    return {**report.to_dict(), "play_text": text}


# --- issue tracker sync ---


def _issue_sync_collect(node_input: str) -> dict[str, Any]:
    args = json.loads(node_input)
    repo = Path(args["repo"])
    remote = merged = None
    if args.get("use_git", True):
        try:
            remote = sorted(issue_sync.git_remote_branches(repo))
            merged = sorted(issue_sync.git_merged_branches(repo))
        except (OSError, subprocess.CalledProcessError):  # no git or no remote: those checks are skipped
            remote = merged = None
        if remote == []:  # shallow clone without remote refs: nothing to compare against
            remote = merged = None
    return {
        "csv": (repo / issue_sync.ISSUES_CSV).read_text(encoding="utf-8"),
        "remote": remote,
        "merged": merged,
        "github_repository": args.get("github_repository"),
    }


def _issue_sync_analyse(node_input: dict[str, Any]) -> dict[str, Any]:
    # The token is read here, not passed through the graph, so it never appears in ADK events or state.
    token = os.environ.get("GITHUB_TOKEN", "")
    lookup = None
    if node_input.get("github_repository") and token:
        lookup = issue_sync.github_pr_lookup(node_input["github_repository"], token)
    report = issue_sync.check(
        node_input["csv"],
        remote_branches=set(node_input["remote"]) if node_input["remote"] is not None else None,
        merged_branches=set(node_input["merged"]) if node_input["merged"] is not None else None,
        pr_merged=lookup,
    )
    return report.to_dict()


# --- shared ---


def _render(node_input: dict[str, Any]) -> Event:
    markdown = Report.from_dict(node_input).to_markdown()
    return Event(message=markdown, output={**node_input, "markdown": markdown})


def translation_agent() -> Workflow:
    return Workflow(name="translation_agent", edges=[("START", _translations_collect, _translations_analyse, _render)])


def release_notes_agent() -> Workflow:
    return Workflow(name="release_notes_agent", edges=[("START", _release_notes_collect, _release_notes_analyse, _render)])


def issue_sync_agent() -> Workflow:
    return Workflow(name="issue_sync_agent", edges=[("START", _issue_sync_collect, _issue_sync_analyse, _render)])


AGENTS = {
    "translations": translation_agent,
    "release-notes": release_notes_agent,
    "issue-sync": issue_sync_agent,
}


async def _run_async(workflow: Workflow, args: dict[str, Any]) -> dict[str, Any]:
    runner = InMemoryRunner(agent=workflow, app_name=APP_NAME)
    session = await runner.session_service.create_session(app_name=APP_NAME, user_id=USER_ID)
    message = types.Content(role="user", parts=[types.Part(text=json.dumps(args))])
    final: Any = None
    async for event in runner.run_async(user_id=USER_ID, session_id=session.id, new_message=message):
        if event.error_message:
            raise RuntimeError(f"{workflow.name} failed: {event.error_message}")
        if event.output is not None:
            final = event.output
    if not isinstance(final, dict) or "markdown" not in final:
        raise RuntimeError(f"{workflow.name} produced no report")
    return final


def run_agent(name: str, args: dict[str, Any]) -> dict[str, Any]:
    """Runs one agent to completion and returns its report dict (with ``markdown``)."""
    return asyncio.run(_run_async(AGENTS[name](), args))

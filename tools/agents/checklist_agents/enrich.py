"""Optional, opt-in model step that adds an advisory summary to a finished report (CL-225).

It runs only when the repository owner has added a ``GEMINI_API_KEY`` Actions secret (Gemini
Developer API, free tier). Without the key, ``is_enabled()`` is False and nothing is imported from
the model side, sent anywhere or billed. The deterministic report is always complete on its own:
the model never decides whether a job passes, and its text is labelled as advisory.

Before anything leaves the runner, the report goes through ``redact()`` (key and token patterns,
design 19.5). Reports contain only repository data that is public anyway (string resources,
commit subjects, issues.csv), never source diffs or logs.
"""

from __future__ import annotations

import asyncio
import os
import re
from collections.abc import Mapping

DEFAULT_MODEL = "gemini-flash-latest"
MAX_INPUT_CHARS = 20_000
KEY_VARIABLES = ("GEMINI_API_KEY", "GOOGLE_API_KEY")

INSTRUCTIONS = {
    "translations": (
        "You review a translation consistency report for an Android app in English, Kannada, Hindi, Tamil, "
        "Telugu, Marathi and Malayalam. In at most 8 bullet points, say which findings matter most for users "
        "and suggest concrete fixes. Do not invent findings that are not in the report."
    ),
    "release-notes": (
        "You turn a release notes report into friendly Google Play release notes in plain English, at most "
        "500 characters, one short line per change, no issue IDs, no internal or engineering work."
    ),
    "issue-sync": (
        "You review an issue tracker consistency report. In at most 6 bullet points, list the rows to update "
        "first and the status each should probably move to. Do not invent issues."
    ),
}

_REDACTIONS = [
    (re.compile(r"AIza[0-9A-Za-z_\-]{35}"), "[REDACTED_GOOGLE_KEY]"),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b"), "[REDACTED_GITHUB_TOKEN]"),
    (re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"), "[REDACTED_GITHUB_TOKEN]"),
    (re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b"), "[REDACTED_AWS_KEY]"),
    (re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}\b"), "[REDACTED_SLACK_TOKEN]"),
    (re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"), "[REDACTED_PRIVATE_KEY]"),
    (re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"), "[REDACTED_JWT]"),
    (re.compile(r"(?i)\b(password|passwd|secret|token|api[_-]?key)\b(\s*[:=]\s*)\S+"), r"\1\2[REDACTED]"),
]


def is_enabled(env: Mapping[str, str] = os.environ) -> bool:
    return any(env.get(name, "").strip() for name in KEY_VARIABLES)


def redact(text: str) -> str:
    for pattern, replacement in _REDACTIONS:
        text = pattern.sub(replacement, text)
    return text


def prepare_input(markdown: str) -> str:
    text = redact(markdown)
    return text if len(text) <= MAX_INPUT_CHARS else text[:MAX_INPUT_CHARS] + "\n[report truncated]"


def build_agent(kind: str, model: str = DEFAULT_MODEL):
    """The ADK LLM agent for one report kind. Building it makes no network call."""
    from google.adk import Agent  # imported lazily: only the opt-in path needs it

    return Agent(name=f"{kind.replace('-', '_')}_reviewer", model=model, instruction=INSTRUCTIONS[kind])


async def _ask(kind: str, markdown: str, model: str) -> str:
    from google.adk.runners import InMemoryRunner
    from google.genai import types

    agent = build_agent(kind, model)
    runner = InMemoryRunner(agent=agent, app_name="checklist_ci_enrich")
    session = await runner.session_service.create_session(app_name="checklist_ci_enrich", user_id="github-actions")
    message = types.Content(role="user", parts=[types.Part(text=prepare_input(markdown))])
    parts: list[str] = []
    async for event in runner.run_async(user_id="github-actions", session_id=session.id, new_message=message):
        if event.content and event.content.parts and not event.partial:
            parts += [p.text for p in event.content.parts if getattr(p, "text", None)]
    return "\n".join(parts).strip()


def enrich(kind: str, markdown: str, env: Mapping[str, str] = os.environ) -> str | None:
    """Returns an advisory markdown section, or None when disabled or when the model call fails."""
    if not is_enabled(env):
        return None
    if not env.get("GOOGLE_API_KEY") and env.get("GEMINI_API_KEY"):
        os.environ["GOOGLE_API_KEY"] = env["GEMINI_API_KEY"]  # the name google-genai reads
    os.environ.setdefault("GOOGLE_GENAI_USE_VERTEXAI", "FALSE")  # Gemini Developer API, not Vertex AI
    model = env.get("CHECKLIST_AGENT_MODEL", DEFAULT_MODEL)
    try:
        text = asyncio.run(_ask(kind, markdown, model))
    except Exception as error:  # noqa: BLE001 - enrichment is optional and must never fail the job
        return f"_AI summary skipped: {type(error).__name__}._"
    if not text:
        return None
    return f"### AI summary (advisory, {model})\n\n{text}\n"

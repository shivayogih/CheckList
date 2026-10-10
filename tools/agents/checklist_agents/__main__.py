"""Command line for the CI agents.

    cd tools/agents
    python -m checklist_agents translations   --repo ../.. [--out report.md] [--enrich]
    python -m checklist_agents release-notes  --repo ../.. [--to REF] [--from REF] [--max N] [--out-dir DIR]
    python -m checklist_agents issue-sync     --repo ../.. [--no-git] [--github-repository owner/name]

Each run prints the markdown report, appends it to ``$GITHUB_STEP_SUMMARY`` when that is set, and
exits with 1 when the report has errors (warnings never fail). ``--enrich`` adds the optional AI
summary, and does nothing unless ``GEMINI_API_KEY`` is set.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from . import enrich
from .workflows import run_agent


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="checklist_agents", description="CheckList CI agents (Google ADK, rule-based)")
    sub = parser.add_subparsers(dest="agent", required=True)

    def common(p: argparse.ArgumentParser) -> None:
        p.add_argument("--repo", default=".", help="repository root (default: current directory)")
        p.add_argument("--out", help="also write the markdown report to this file")
        p.add_argument("--summary", default=os.environ.get("GITHUB_STEP_SUMMARY"), help="append the report here")
        p.add_argument("--enrich", action="store_true", help="add the opt-in AI summary when GEMINI_API_KEY is set")

    common(sub.add_parser("translations", help="check strings.xml in all 7 languages"))
    notes = sub.add_parser("release-notes", help="build release notes from CL- commits since the last tag")
    common(notes)
    notes.add_argument("--to", dest="to_ref", default="HEAD")
    notes.add_argument("--from", dest="from_ref")
    notes.add_argument("--max", dest="max_notes", type=int, default=15)
    notes.add_argument("--out-dir", help="write <dir>/whatsnew-<locale> for Google Play")
    notes.add_argument("--locale", default="en-US")
    issues = sub.add_parser("issue-sync", help="validate docs/project-management/issues.csv")
    common(issues)
    issues.add_argument("--no-git", action="store_true", help="skip remote branch and merge checks")
    issues.add_argument(
        "--github-repository",
        default=os.environ.get("GITHUB_REPOSITORY"),
        help="owner/name; with GITHUB_TOKEN set, merged PRs are looked up on GitHub",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    repo = str(Path(args.repo).resolve())
    payload: dict[str, object] = {"repo": repo}
    if args.agent == "release-notes":
        payload |= {"to_ref": args.to_ref, "from_ref": args.from_ref, "max_notes": args.max_notes}
    elif args.agent == "issue-sync":
        payload |= {"use_git": not args.no_git, "github_repository": args.github_repository}

    result = run_agent(args.agent, payload)
    markdown = result["markdown"]
    if args.enrich:
        extra = enrich.enrich(args.agent, markdown)
        markdown += "\n" + (extra or "_AI summary not enabled (no GEMINI_API_KEY secret)._\n")

    if args.agent == "release-notes" and args.out_dir:
        out_dir = Path(args.out_dir)
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / f"whatsnew-{args.locale}").write_text(result["play_text"] + "\n", encoding="utf-8")
    if args.out:
        Path(args.out).write_text(markdown, encoding="utf-8")
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as handle:
            handle.write(markdown + "\n")
    print(markdown)
    errors = sum(1 for f in result["findings"] if f["severity"] == "error")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

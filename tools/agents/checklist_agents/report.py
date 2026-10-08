"""Findings and the markdown report every agent produces."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any


class Severity(str, Enum):
    ERROR = "error"  # fails the job
    WARNING = "warning"  # shown, does not fail the job
    INFO = "info"


_LABELS = {Severity.ERROR: "**error**", Severity.WARNING: "warning", Severity.INFO: "info"}


@dataclass(frozen=True)
class Finding:
    severity: Severity
    code: str
    message: str
    location: str = ""


@dataclass
class Report:
    title: str
    findings: list[Finding] = field(default_factory=list)
    summary: list[str] = field(default_factory=list)
    sections: list[tuple[str, str]] = field(default_factory=list)

    def add(self, severity: Severity, code: str, message: str, location: str = "") -> None:
        self.findings.append(Finding(severity, code, message, location))

    def count(self, severity: Severity) -> int:
        return sum(1 for f in self.findings if f.severity == severity)

    @property
    def failed(self) -> bool:
        return self.count(Severity.ERROR) > 0

    def to_markdown(self, max_rows: int = 200) -> str:
        errors, warnings = self.count(Severity.ERROR), self.count(Severity.WARNING)
        status = "FAILED" if errors else ("passed with warnings" if warnings else "passed")
        lines = [f"## {self.title}", "", f"**Result:** {status} ({errors} error(s), {warnings} warning(s))", ""]
        lines += [f"- {line}" for line in self.summary]
        if self.summary:
            lines.append("")
        if self.findings:
            order = {Severity.ERROR: 0, Severity.WARNING: 1, Severity.INFO: 2}
            rows = sorted(self.findings, key=lambda f: (order[f.severity], f.code, f.location))
            lines += ["| Severity | Check | Where | Details |", "|---|---|---|---|"]
            for f in rows[:max_rows]:
                lines.append(f"| {_LABELS[f.severity]} | `{f.code}` | {_cell(f.location)} | {_cell(f.message)} |")
            if len(rows) > max_rows:
                lines.append(f"\n_{len(rows) - max_rows} more finding(s) not shown._")
            lines.append("")
        for heading, body in self.sections:
            lines += [f"### {heading}", "", body.rstrip(), ""]
        return "\n".join(lines).rstrip() + "\n"

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["findings"] = [{**asdict(f), "severity": f.severity.value} for f in self.findings]
        return data

    @staticmethod
    def from_dict(data: dict[str, Any]) -> "Report":
        return Report(
            title=data["title"],
            findings=[Finding(Severity(f["severity"]), f["code"], f["message"], f["location"]) for f in data["findings"]],
            summary=list(data["summary"]),
            sections=[(h, b) for h, b in data["sections"]],
        )


def _cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ") if text else ""

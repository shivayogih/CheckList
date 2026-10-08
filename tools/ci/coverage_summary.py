#!/usr/bin/env python3
"""Prints unit-test line and branch coverage per module as a Markdown table (CL-175).

    python3 tools/ci/coverage_summary.py >> "$GITHUB_STEP_SUMMARY"

Reads the JaCoCo XML reports written by :domain:jacocoTestReport and AGP's
create*UnitTestCoverageReport tasks. Informational only: there is no threshold. Standard library only.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REPORTS = {
    "domain": "domain/build/reports/jacoco/test/jacocoTestReport.xml",
    "data": "data/build/reports/coverage/test/**/*.xml",
    "app": "app/build/reports/coverage/test/**/*.xml",
}


def counters(path):
    """Returns {type: (missed, covered)} from the report's top-level counters."""
    # JaCoCo reports reference a DTD that is not available offline; the parser does not fetch it.
    root = ET.parse(path).getroot()
    return {c.get("type"): (int(c.get("missed")), int(c.get("covered"))) for c in root.findall("counter")}


def percent(pair):
    missed, covered = pair
    total = missed + covered
    return "n/a" if total == 0 else "%.1f%% (%d/%d)" % (100.0 * covered / total, covered, total)


def main():
    lines = ["## Unit-test coverage", "", "| Module | Lines | Branches |", "|---|---|---|"]
    for module, pattern in REPORTS.items():
        matches = sorted(glob.glob(os.path.join(ROOT, pattern), recursive=True))
        if not matches:
            lines.append("| %s | no report | no report |" % module)
            continue
        found = counters(matches[0])
        lines.append("| %s | %s | %s |" % (module, percent(found.get("LINE", (0, 0))), percent(found.get("BRANCH", (0, 0)))))
    lines += ["", "Informational only: no threshold. HTML reports are in the `coverage` artifact."]
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())

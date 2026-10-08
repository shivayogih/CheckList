from __future__ import annotations

from conftest import git

from checklist_agents import release_notes as rn
from checklist_agents.release_notes import Change

ISSUES = {
    "CL-1": {"Issue ID": "CL-1", "Type": "Feature", "Title": "Checklist sharing"},
    "CL-2": {"Issue ID": "CL-2", "Type": "Bug", "Title": "Crash on rotate"},
    "CL-3": {"Issue ID": "CL-3", "Type": "CI/CD", "Title": "Add pipeline"},
}


def test_only_cl_subjects_count() -> None:
    changes = rn.parse_changes([("a", "CL-1 Add sharing"), ("b", "Merge branch 'x'"), ("c", "fix typo"), ("d", "CL-2  Fix crash ")])
    assert changes == [Change("CL-1", "Add sharing", "a"), Change("CL-2", "Fix crash", "d")]


def test_sanitize_removes_links_mails_mentions_and_pr_numbers() -> None:
    text = rn.sanitize("Add export see https://example.com/x by @someone mail a.b@example.org (#12)")
    assert text == "Add export see by mail"


def test_internal_work_and_non_user_issue_types_are_left_out() -> None:
    changes = [
        Change("CL-1", "Add checklist sharing"),
        Change("CL-9", "Add detekt to CI"),
        Change("CL-9", "Update docs for import"),
        Change("CL-9", "Speed up search [internal]"),
        Change("CL-3", "Make lists load faster"),  # CI/CD issue type
        Change("CL-2", "Fix crash on rotate"),
    ]

    text = rn.play_notes(changes, ISSUES)

    assert text == "- Add checklist sharing (CL-1)\n- Fix crash on rotate (CL-2)"


def test_duplicates_are_dropped_and_count_is_capped() -> None:
    changes = [Change("CL-1", "Add sharing")] * 3 + [Change(f"CL-{i}", f"Add feature {i}") for i in range(10, 40)]

    lines = rn.play_notes(changes, {}, max_notes=5).splitlines()

    assert lines[0] == "- Add sharing (CL-1)"
    assert len(lines) == 5


def test_play_limit_of_500_characters_keeps_whole_lines() -> None:
    changes = [Change(f"CL-{i}", "Add " + "x" * 80) for i in range(100, 110)]

    text = rn.play_notes(changes, {})

    assert len(text) <= rn.PLAY_LIMIT
    assert all(line.endswith(")") for line in text.splitlines())


def test_fallback_when_nothing_is_user_facing() -> None:
    assert rn.play_notes([Change("CL-3", "Add CI workflow")], ISSUES) == rn.FALLBACK
    assert rn.play_notes([], {}) == rn.FALLBACK


def test_report_groups_changes_and_flags_unknown_issues() -> None:
    changes = [Change("CL-1", "Add sharing", "a" * 40), Change("CL-2", "Fix crash"), Change("CL-3", "Add pipeline"),
               Change("CL-77", "Fix sync bug", "b" * 40)]

    report, text = rn.build_report(changes, ISSUES, "v1.0.0..HEAD")
    sections = dict(report.sections)

    assert "- CL-1 Add sharing (Checklist sharing)" in sections["New (1)"]
    assert "CL-2 Fix crash" in sections["Fixes (2)"] and "CL-77" in sections["Fixes (2)"]
    assert "CL-3 Add pipeline" in sections["Internal (1)"]
    assert [f.code for f in report.findings] == ["unknown-issue"]
    assert text in sections["Google Play: what's new (en-US)"]


def test_range_starts_after_the_previous_tag(git_repo) -> None:
    repo, commit = git_repo
    commit("CL-1 Add first feature")
    git(repo, "tag", "v1.0.0")
    commit("CL-2 Add second feature")
    commit("Not a CL commit")
    git(repo, "tag", "v1.1.0")
    commit("CL-3 Add third feature")

    assert rn.previous_tag(repo, "HEAD") == "v1.1.0"
    assert rn.previous_tag(repo, "v1.1.0") == "v1.0.0"  # building a tag: notes since the one before
    report, text = rn.run(repo, to_ref="v1.1.0")
    assert text == "- Add second feature (CL-2)"
    assert "v1.0.0..v1.1.0" in report.summary[0]


def test_whole_history_without_tags(git_repo) -> None:
    repo, commit = git_repo
    commit("CL-1 Add first feature")

    assert rn.previous_tag(repo) is None
    _, text = rn.run(repo)
    assert text == "- Add first feature (CL-1)"

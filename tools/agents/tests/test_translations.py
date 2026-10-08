from __future__ import annotations

from pathlib import Path

import pytest
from conftest import REPO_ROOT, strings_xml

from checklist_agents import translations
from checklist_agents.report import Severity


def codes(report, severity=None) -> list[str]:
    return sorted(f.code for f in report.findings if severity is None or f.severity == severity)


def write(repo: Path, tag: str, entries: dict[str, str], extra: str = "") -> None:
    (repo / f"app/src/main/res/values-{tag}/strings.xml").write_text(strings_xml(entries, extra), encoding="utf-8")


def test_consistent_files_pass(res_repo: Path) -> None:
    report = translations.check(res_repo)
    assert report.findings == []
    assert not report.failed


def test_missing_and_extra_keys_are_errors(res_repo: Path) -> None:
    write(res_repo, "kn", {"home_title": "ನನ್ನ ಪಟ್ಟಿಗಳು", "old_key": "ಹಳೆಯ"})

    report = translations.check(res_repo)

    assert codes(report, Severity.ERROR) == ["extra-key", "missing-key"]
    assert all("values-kn" in f.location for f in report.findings)


def test_untranslatable_english_keys_are_not_expected_but_rejected_in_translations(res_repo: Path) -> None:
    write(res_repo, "hi", {"home_title": "मेरी सूचियाँ", "pdf_page": "पृष्ठ %1$d / %2$d", "app_name": "CheckList"})

    report = translations.check(res_repo)

    assert codes(report) == ["extra-key"]


@pytest.mark.parametrize(
    "page, problem",
    [
        ("ಪುಟ %1$d", "dropped %2$d"),
        ("ಪುಟ %1$s / %2$d", "type changed"),
        ("ಪುಟ %1$d / %3$d", "index changed"),
        ("ಪುಟ", "no placeholders"),
    ],
)
def test_placeholder_mismatches_are_errors(res_repo: Path, page: str, problem: str) -> None:
    write(res_repo, "kn", {"home_title": "ನನ್ನ ಪಟ್ಟಿಗಳು", "pdf_page": page})

    assert codes(translations.check(res_repo)) == ["placeholder"], problem


def test_placeholder_order_may_change(res_repo: Path) -> None:
    write(res_repo, "ta", {"home_title": "என் பட்டியல்கள்", "pdf_page": "%2$d இல் பக்கம் %1$d"})

    assert translations.check(res_repo).findings == []


def test_placeholder_normalisation() -> None:
    assert translations.placeholders("Page %1$d of %2$d") == ["1$d", "2$d"]
    assert translations.placeholders("%s and %d, 100%%") == ["1$s", "2$d"]
    assert translations.placeholders("%.1f kg") == ["1$f"]
    assert translations.placeholders("no placeholders") == []


def test_english_left_in_a_translation_is_a_warning(res_repo: Path) -> None:
    write(res_repo, "te", {"home_title": "My checklists", "pdf_page": "పేజీ %1$d / %2$d"})

    report = translations.check(res_repo)

    assert codes(report) == ["untranslated"]
    assert not report.failed  # warnings never fail the job


def test_latin_only_text_in_an_indic_file_is_a_warning(res_repo: Path) -> None:
    write(res_repo, "ml", {"home_title": "Ente pattikakal", "pdf_page": "പേജ് %1$d / %2$d"})

    assert codes(translations.check(res_repo)) == ["latin-only"]


def test_another_script_is_a_warning(res_repo: Path) -> None:
    write(res_repo, "kn", {"home_title": "मेरी सूचियाँ", "pdf_page": "ಪುಟ %1$d / %2$d"})

    report = translations.check(res_repo)

    assert codes(report) == ["wrong-script"]
    assert "Devanagari" in report.findings[0].message


def test_allowlisted_terms_and_placeholders_may_stay_latin(res_repo: Path, tmp_path: Path) -> None:
    allowlist = tmp_path / "allow.json"
    allowlist.write_text('{"terms": ["CheckList", "PDF"], "keys": ["pdf_page"]}', encoding="utf-8")
    write(res_repo, "hi", {"home_title": "CheckList PDF", "pdf_page": "Page %1$d of %2$d"})

    assert translations.check(res_repo, allowlist=allowlist).findings == []


def test_plurals_are_checked_by_name_and_other_placeholders(res_repo: Path) -> None:
    english = res_repo / "app/src/main/res/values/strings.xml"
    english.write_text(
        strings_xml(
            {"home_title": "My checklists", "pdf_page": "Page %1$d of %2$d"},
            '    <plurals name="items"><item quantity="one">One item</item>'
            '<item quantity="other">%1$d items</item></plurals>\n',
        ),
        encoding="utf-8",
    )
    for tag, (title, page) in {"hi": ("मेरी सूचियाँ", "पृष्ठ %1$d / %2$d")}.items():
        write(
            res_repo, tag, {"home_title": title, "pdf_page": page},
            '    <plurals name="items"><item quantity="one">एक चीज़</item>'
            '<item quantity="other">%1$s चीज़ें</item></plurals>\n',
        )

    report = translations.check(res_repo)

    errors = [f for f in report.findings if f.severity == Severity.ERROR]
    # hi: wrong placeholder type in "other"; the other five locales lack the plurals entirely.
    assert sorted(f.code for f in errors) == ["missing-key"] * 5 + ["placeholder"]


def test_missing_locale_and_invalid_xml_are_errors(res_repo: Path) -> None:
    res = res_repo / "app/src/main/res"
    (res / "values-mr/strings.xml").unlink()
    (res / "values-ta/strings.xml").write_text("<resources><string name='x'>", encoding="utf-8")

    assert codes(translations.check(res_repo), Severity.ERROR) == ["invalid-xml", "missing-locale"]


def test_markdown_report_lists_findings(res_repo: Path) -> None:
    write(res_repo, "te", {"home_title": "My checklists"})

    markdown = translations.check(res_repo).to_markdown()

    assert "FAILED" in markdown
    assert "`missing-key`" in markdown and "`untranslated`" in markdown


def test_repository_strings_have_no_errors() -> None:
    report = translations.check(REPO_ROOT)
    assert [f for f in report.findings if f.severity == Severity.ERROR] == []

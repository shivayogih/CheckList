"""Translation consistency rules (CL-221).

Compares every ``values-<tag>/strings.xml`` with the English ``values/strings.xml``:

* missing or extra keys (error) - English keys marked ``translatable="false"`` are not expected;
* placeholder mismatches such as ``%1$s`` vs ``%1$d`` or a dropped ``%2$d`` (error);
* text identical to English left in a translation (warning);
* Latin-only text where the language's own script is expected (warning);
* letters from another Indian script, e.g. Devanagari inside the Kannada file (warning).

Brand names and technical terms that stay in Latin letters (``CheckList``, ``Google Play``,
``PDF``...) are listed in ``config/translation_allowlist.json`` and ignored by the script checks.
"""

from __future__ import annotations

import json
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

from .report import Report, Severity

# Language tag -> (script name, Unicode block). Marathi and Hindi share Devanagari.
SCRIPTS: dict[str, tuple[str, int, int]] = {
    "kn": ("Kannada", 0x0C80, 0x0CFF),
    "hi": ("Devanagari", 0x0900, 0x097F),
    "mr": ("Devanagari", 0x0900, 0x097F),
    "ta": ("Tamil", 0x0B80, 0x0BFF),
    "te": ("Telugu", 0x0C00, 0x0C7F),
    "ml": ("Malayalam", 0x0D00, 0x0D7F),
}
EXPECTED_LOCALES = tuple(SCRIPTS)  # plus English in values/

DEFAULT_RES_DIR = "app/src/main/res"
DEFAULT_ALLOWLIST = Path(__file__).resolve().parent.parent / "config" / "translation_allowlist.json"

# Java/Android format specifiers: %s, %1$s, %d, %.1f, %02d ... ("%%" is a literal percent sign).
_PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")
_LATIN = re.compile(r"[A-Za-z]")


@dataclass(frozen=True)
class Entry:
    key: str  # "name", "name[2]" for arrays, "name#one" for plurals
    text: str
    translatable: bool = True


def parse_strings(path: Path) -> dict[str, Entry]:
    """Reads one strings.xml into key -> Entry. Inline markup is flattened to its text."""
    root = ET.parse(path).getroot()
    entries: dict[str, Entry] = {}
    for element in root:
        name = element.get("name")
        if not name or not isinstance(element.tag, str):
            continue
        translatable = element.get("translatable", "true") != "false"
        if element.tag == "string":
            entries[name] = Entry(name, _text(element), translatable)
        elif element.tag == "plurals":
            for item in element.findall("item"):
                key = f"{name}#{item.get('quantity', '')}"
                entries[key] = Entry(key, _text(item), translatable)
        elif element.tag == "string-array":
            for index, item in enumerate(element.findall("item")):
                key = f"{name}[{index}]"
                entries[key] = Entry(key, _text(item), translatable)
    return entries


def _text(element: ET.Element) -> str:
    return "".join(element.itertext()).strip()


def placeholders(text: str) -> list[str]:
    """Normalized placeholders, e.g. 'Page %1$d of %2$d' -> ['1$d', '2$d']; unnumbered ones get their order."""
    found = []
    position = 0
    for match in _PLACEHOLDER.finditer(text):
        conversion = match.group(2)
        if conversion == "%":
            continue
        position += 1
        index = match.group(1) or str(position)
        found.append(f"{index}${conversion.lower() if conversion in 'SX' else conversion}")
    return sorted(found)


def _plural_base(key: str) -> str | None:
    return key.split("#", 1)[0] if "#" in key else None


def load_allowlist(path: Path = DEFAULT_ALLOWLIST) -> tuple[list[str], set[str]]:
    data = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}
    terms = sorted(data.get("terms", []), key=len, reverse=True)  # longest first: "Google Play" before "Play"
    return terms, set(data.get("keys", []))


def _without_allowed(text: str, terms: list[str]) -> str:
    text = _PLACEHOLDER.sub(" ", text)
    for term in terms:
        text = re.sub(rf"(?<![A-Za-z]){re.escape(term)}(?![A-Za-z])", " ", text)
    return text


def _scripts_in(text: str) -> set[str]:
    found = set()
    for ch in text:
        code = ord(ch)
        for name, start, end in SCRIPTS.values():
            if start <= code <= end:
                found.add(name)
    return found


def check(repo: Path, res_dir: str = DEFAULT_RES_DIR, allowlist: Path = DEFAULT_ALLOWLIST) -> Report:
    report = Report("Translation consistency")
    res = repo / res_dir
    english_path = res / "values" / "strings.xml"
    if not english_path.is_file():
        report.add(Severity.ERROR, "missing-file", "English strings.xml not found", str(english_path.relative_to(repo)))
        return report

    terms, allowed_keys = load_allowlist(allowlist)
    english = parse_strings(english_path)
    expected = {k: e for k, e in english.items() if e.translatable}
    english_plurals = {_plural_base(k) for k in expected if _plural_base(k)}
    report.summary.append(f"English source: {len(english)} entries, {len(expected)} translatable")

    present = sorted(p.parent.name.removeprefix("values-") for p in res.glob("values-*/strings.xml"))
    for tag in EXPECTED_LOCALES:
        if tag not in present:
            report.add(Severity.ERROR, "missing-locale", f"values-{tag}/strings.xml is missing", res_dir)
    for tag in present:
        if tag not in SCRIPTS:
            report.add(Severity.INFO, "unknown-locale", f"values-{tag} is not one of the 7 app languages; only key checks apply", res_dir)

    for tag in present:
        path = res / f"values-{tag}" / "strings.xml"
        rel = str(path.relative_to(repo))
        try:
            translated = parse_strings(path)
        except ET.ParseError as error:
            report.add(Severity.ERROR, "invalid-xml", str(error), rel)
            continue
        counts = _check_locale(report, tag, rel, expected, english_plurals, translated, terms, allowed_keys)
        report.summary.append(f"`{tag}`: {len(translated)} entries; " + ", ".join(f"{v} {k}" for k, v in counts.items()))
    return report


def _check_locale(
    report: Report,
    tag: str,
    rel: str,
    expected: dict[str, Entry],
    english_plurals: set[str | None],
    translated: dict[str, Entry],
    terms: list[str],
    allowed_keys: set[str],
) -> dict[str, int]:
    counts = {"missing": 0, "extra": 0, "placeholder": 0, "untranslated": 0, "script": 0}
    plural_bases = {_plural_base(k) for k in translated if _plural_base(k)}

    for key in expected:
        base = _plural_base(key)
        if base is not None:
            if base not in plural_bases:
                report.add(Severity.ERROR, "missing-key", f"plurals `{base}` is missing", rel)
                plural_bases.add(base)  # report once
                counts["missing"] += 1
        elif key not in translated:
            report.add(Severity.ERROR, "missing-key", f"`{key}` is missing", rel)
            counts["missing"] += 1

    for key, entry in translated.items():
        base = _plural_base(key)
        if (base is None and key not in expected) or (base is not None and base not in english_plurals):
            report.add(Severity.ERROR, "extra-key", f"`{key}` does not exist in English (or is not translatable)", rel)
            counts["extra"] += 1
            continue

        source = _source_for(key, expected)
        if source is not None and placeholders(entry.text) != placeholders(source.text):
            if base is None or key.endswith("#other"):
                report.add(
                    Severity.ERROR,
                    "placeholder",
                    f"`{key}` has {placeholders(entry.text) or 'no placeholders'}, English has {placeholders(source.text) or 'none'}",
                    rel,
                )
                counts["placeholder"] += 1

        if key in allowed_keys or (base is not None and base in allowed_keys):
            continue
        if _script_findings(report, tag, rel, key, entry.text, source, terms):
            counts["untranslated" if source and _same_text(entry.text, source.text) else "script"] += 1
    return counts


def _source_for(key: str, expected: dict[str, Entry]) -> Entry | None:
    if key in expected:
        return expected[key]
    base = _plural_base(key)
    return expected.get(f"{base}#other") if base else None


def _same_text(a: str, b: str) -> bool:
    return " ".join(a.split()).casefold() == " ".join(b.split()).casefold()


def _script_findings(
    report: Report, tag: str, rel: str, key: str, text: str, source: Entry | None, terms: list[str]
) -> bool:
    if tag not in SCRIPTS:
        return False
    script = SCRIPTS[tag][0]
    rest = _without_allowed(text, terms)
    has_latin = bool(_LATIN.search(rest))
    scripts = _scripts_in(text)

    if source is not None and has_latin and _same_text(text, source.text):
        report.add(Severity.WARNING, "untranslated", f"`{key}` is still English: \"{text}\"", rel)
        return True
    if has_latin and script not in scripts:
        report.add(Severity.WARNING, "latin-only", f"`{key}` has no {script} letters: \"{text}\"", rel)
        return True
    foreign = scripts - {script}
    if foreign:
        report.add(Severity.WARNING, "wrong-script", f"`{key}` contains {', '.join(sorted(foreign))} letters", rel)
        return True
    return False

#!/usr/bin/env python3
"""Generates docs/translation-review.md: every UI string and plural with its English source and the
six translations side by side, for native-speaker review (CL-107).

    python3 tools/l10n/translation_review.py           # rewrite the document
    python3 tools/l10n/translation_review.py --check   # exit 1 if the document is out of date

Reads app/src/main/res/values*/strings.xml and plurals.xml. Standard library only.
"""
import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT = os.path.join(ROOT, "docs", "translation-review.md")

# Same order as SupportedLanguages in :domain.
LANGUAGES = [
    ("kn", "Kannada (ಕನ್ನಡ)"),
    ("hi", "Hindi (हिन्दी)"),
    ("ta", "Tamil (தமிழ்)"),
    ("te", "Telugu (తెలుగు)"),
    ("mr", "Marathi (मराठी)"),
    ("ml", "Malayalam (മലയാളം)"),
]


def text_of(element):
    text = "".join(element.itertext())
    # Android escapes: \' and \" are plain quotes for a reader; \n is a line break.
    return text.replace("\\'", "'").replace('\\"', '"').replace("\\n", " / ")


def load(folder):
    """Returns {key: text} in file order: strings as name, plural items as name (quantity)."""
    entries = {}
    for name in ("strings.xml", "plurals.xml"):
        path = os.path.join(RES, folder, name)
        if not os.path.isfile(path):
            continue
        root = ET.parse(path).getroot()
        for element in root:
            if element.tag == "string":
                if element.get("translatable") == "false":
                    continue
                entries[element.get("name")] = text_of(element)
            elif element.tag == "plurals":
                for item in element.findall("item"):
                    entries["%s (%s)" % (element.get("name"), item.get("quantity"))] = text_of(item)
    return entries


def cell(value):
    if value is None:
        return "**MISSING**"
    return value.replace("|", "\\|").strip() or " "


def render():
    english = load("values")
    translations = {tag: load("values-" + tag) for tag, _ in LANGUAGES}
    lines = [
        "# Translation review",
        "",
        "Every UI string and plural of the app with its English source and the six translations, for",
        "native-speaker review before release (CL-107). **Generated** by",
        "`python3 tools/l10n/translation_review.py` from `app/src/main/res/values*/`; do not edit by hand.",
        "Regenerate it whenever strings change (`--check` reports whether it is current).",
        "",
        "## How to review",
        "",
        "1. Check meaning, tone and plain language against the English (see the copy guide in",
        "   [accessibility.md](accessibility.md#plain-language)): short, everyday words for a first-time user.",
        "2. Keep every placeholder exactly as it is (`%1$s`, `%2$s`); you may move it within the sentence.",
        "   Numbers arrive already formatted, with Western digits (0-9) in every language.",
        "3. Plural keys show each form, for example `progress_items_done (one)` and `(other)`.",
        "4. Report fixes as an issue or a pull request that edits `values-<tag>/strings.xml` or",
        "   `plurals.xml`, then regenerate this file.",
        "",
        "Catalogue item and category names are reviewed separately in",
        "[catalog-translation-review.md](catalog-translation-review.md).",
        "",
        "Brand names marked `translatable=\"false\"` (such as `app_name`) are not listed.",
        "",
        "## Strings (%d keys)" % len(english),
        "",
        "| Key | English | " + " | ".join(label for _, label in LANGUAGES) + " |",
        "|---|---|" + "---|" * len(LANGUAGES),
    ]
    for key, source in english.items():
        row = ["`%s`" % key, cell(source)] + [cell(translations[tag].get(key)) for tag, _ in LANGUAGES]
        lines.append("| " + " | ".join(row) + " |")
    extra = sorted({key for tag, _ in LANGUAGES for key in translations[tag] if key not in english})
    if extra:
        lines += ["", "## Keys missing from English", ""] + ["- `%s`" % key for key in extra]
    return "\n".join(lines) + "\n"


def main():
    content = render()
    if "--check" in sys.argv[1:]:
        current = open(OUT, encoding="utf-8").read() if os.path.isfile(OUT) else ""
        if current != content:
            print("docs/translation-review.md is out of date: run python3 tools/l10n/translation_review.py")
            return 1
        print("docs/translation-review.md is up to date")
        return 0
    with open(OUT, "w", encoding="utf-8") as out:
        out.write(content)
    print("Wrote %s" % os.path.relpath(OUT, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())

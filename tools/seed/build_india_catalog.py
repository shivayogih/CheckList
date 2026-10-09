#!/usr/bin/env python3
"""Builds the bundled seed (catalog.json + i18n/*.json) from the India master catalogue (CL-310).

Inputs (all under tools/seed/src_data/):
  master_catalog_india.json   the supplied catalogue (CAT001.., ITM0001..)
  translations/<tag>.json     {categories, items, units: id -> text, review: [{id, why}]} for kn hi ta te mr ml
  legacy_v1/                  the seedVersion 1 seed, kept so released keys never change
Outputs: data/src/main/assets/seed/{catalog.json,i18n/*.json} and docs/catalog-translation-review.md.

Usage: python3 tools/seed/build_india_catalog.py [--check]
Deterministic: running it twice gives identical files. --check exits 1 when outputs are stale.
Standard library only. See docs/seed-catalog.md for the id and upgrade rules.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools/seed/src_data"
SEED = ROOT / "data/src/main/assets/seed"
REVIEW_DOC = ROOT / "docs/catalog-translation-review.md"
TEMPLATES_DOC = ROOT / "docs/catalog-templates-backlog.md"

SEED_VERSION = 3
LOCALES = ["en", "kn", "hi", "ta", "te", "mr", "ml"]
LOCALE_NAMES = {"kn": "Kannada", "hi": "Hindi", "ta": "Tamil", "te": "Telugu", "mr": "Marathi", "ml": "Malayalam"}

# Catalogue unit_id -> app unit code (None = no unit: a task has no quantity).
UNIT_MAP = {
    "kg": "KG", "g": "GRAM", "L": "LITRE", "ml": "MILLILITRE", "dozen": "DOZEN", "pc": "PIECE",
    "pack": "PACK", "box": "BOX", "bottle": "BOTTLE", "pair": "PAIR", "m": "METER", "unit": "NOS",
    "bunch": "BUNCH", "task": None,
}
NEW_UNITS = [{"code": "BUNCH", "allowsDecimal": False, "sortOrder": 130}]

# Seed v1 category key -> catalogue category. The key survives; gifts and decorations merge
# into Events & Celebrations and are retired (hidden once empty).
CATEGORY_KEEP = {
    "groceries": "CAT001", "vegetables": "CAT002", "fruits": "CAT003", "clothing": "CAT023",
    "travel": "CAT022", "documents": "CAT024", "exam": "CAT025", "medicines": "CAT017",
    "toiletries": "CAT012", "electronics": "CAT021", "stationery": "CAT020", "other": "CAT031",
    "pooja_items": "POOJA",
}
RETIRED = {"gifts": "CAT029", "decorations": "CAT029"}
# Not in the master catalogue, but pooja lists are core for the first users: the category stays (key pooja_items).
EXTRA_CATEGORY = "POOJA"

# Seed v1 item key -> catalogue item it is the same thing as (kept key, catalogue data wins).
# Same-name items in the mapped category are matched automatically; these are the synonyms.
ITEM_SYNONYMS = {}
_UNUSED_SYNONYMS = {
    "rice": "ITM0001", "wheat": "ITM0007", "milk": "ITM0146", "tea": "ITM0248", "coffee": "ITM0249",
    "brinjal": "ITM0066", "spinach": "ITM0130", "pants": "ITM0461", "charger": "ITM0433",
    "power_bank": "ITM0434", "pen": "ITM0405", "pencil": "ITM0406", "eraser": "ITM0407",
    "extension_board": "ITM0355", "soap": "ITM0271", "comb": "ITM0291", "bandage": "ITM0367",
    "antiseptic_liquid": "ITM0371", "ors": "ITM0377", "decorative_lights": "ITM0545",
}

ICONS = {
    "CAT001": "🌾", "CAT002": "🥕", "CAT003": "🍎", "CAT004": "🌿", "CAT005": "🥛", "CAT006": "🍗",
    "CAT007": "🍞", "CAT008": "🍪", "CAT009": "🧂", "CAT010": "☕", "CAT011": "🧊", "CAT012": "🧴",
    "CAT013": "🧺", "CAT014": "🧻", "CAT015": "🍽", "CAT016": "🔧", "CAT017": "💊", "CAT018": "🧸",
    "CAT019": "🐕", "CAT020": "✏", "CAT021": "🔌", "CAT022": "🧳", "CAT023": "👕", "CAT024": "📄",
    "CAT025": "📚", "CAT026": "🏃", "CAT027": "🌱", "CAT028": "🚗", "CAT029": "🎉", "CAT030": "🗓",
    "CAT031": "📦",
}
# Seed v1 icons win for kept categories so existing installs do not see a change.
LEGACY_ICON_KEEP = True


def norm(text):
    return re.sub(r"[^a-z0-9]", "", text.lower())


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def dump(path, data):
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    return str(path), text


def split_csv(value):
    return [p.strip() for p in value.split(",") if p.strip()]


def build():
    master = load(SRC / "master_catalog_india.json")
    legacy = load(SRC / "legacy_v1/catalog.json")
    legacy_i18n = {loc: load(SRC / f"legacy_v1/i18n/{loc}.json") for loc in LOCALES}
    tr = {}
    for loc in LOCALES[1:]:
        p = SRC / f"translations/{loc}.json"
        tr[loc] = load(p) if p.is_file() else None

    cat_key = {c: k for k, c in CATEGORY_KEEP.items()}
    for c in master["categories"]:
        cat_key.setdefault(c["category_id"], c["category_id"].lower())

    # Match legacy items to catalogue items.
    by_cat_name = {}
    by_name = {}  # any category: two items with one name would make name search ambiguous
    for it in master["items"]:
        by_cat_name.setdefault((it["category_id"], norm(it["item_name"])), it["item_id"])
        by_name.setdefault(norm(it["item_name"]), it["item_id"])
    en_legacy = legacy_i18n["en"]["items"]
    item_key = {}
    legacy_extra = []
    pending = []
    # Pass 1: same name in the mapped category. Pass 2: same name anywhere (milk moves to Dairy).
    for li in legacy["items"]:
        cat_id = CATEGORY_KEEP.get(li["category"]) or RETIRED[li["category"]]
        legacy_name = norm(en_legacy[li["key"]]["name"])
        target = ITEM_SYNONYMS.get(li["key"]) or by_cat_name.get((cat_id, legacy_name))
        if target and target not in item_key:
            item_key[target] = li["key"]
        else:
            pending.append((li, cat_id, legacy_name))
    for li, cat_id, legacy_name in pending:
        target = by_name.get(legacy_name)
        if target and target not in item_key:
            item_key[target] = li["key"]
        else:
            legacy_extra.append((li, cat_id))
    for it in master["items"]:
        item_key.setdefault(it["item_id"], it["item_id"].lower())

    legacy_aliases = {li["key"]: li for li in legacy["items"]}
    old_icon = {c["key"]: c["icon"] for c in legacy["categories"]}

    # catalog.json
    units = list(legacy["units"]) + NEW_UNITS
    categories = []
    for n, c in enumerate(master["categories"], start=1):
        key = cat_key[c["category_id"]]
        icon = old_icon.get(key) if LEGACY_ICON_KEEP and key in old_icon else ICONS[c["category_id"]]
        categories.append({"key": key, "icon": icon, "sortOrder": n * 10, "catalogId": c["category_id"]})
    categories.append({"key": "pooja_items", "icon": old_icon["pooja_items"], "sortOrder": (len(categories) + 1) * 10})
    items = []
    for it in master["items"]:
        if not it.get("is_active", True):
            continue
        unit = UNIT_MAP[it["default_unit"]]
        old = legacy_aliases.get(item_key[it["item_id"]])
        if old and old["defaultUnit"]:
            unit = old["defaultUnit"]  # released items keep the unit people already use (sugar in kg)
        entry = {"key": item_key[it["item_id"]], "category": cat_key[it["category_id"]], "defaultUnit": unit,
                 "catalogId": it["item_id"]}
        if it["subcategory"]:
            entry["subcategory"] = it["subcategory"]
        tags = split_csv(it["tags"])
        if tags:
            entry["tags"] = tags
        items.append(entry)
    for li, cat_id in legacy_extra:
        items.append({"key": li["key"], "category": cat_key[cat_id], "defaultUnit": li["defaultUnit"]})
    # Released items first: when two names are identical in one language, search ties go to the older item.
    items.sort(key=lambda i: 0 if i["key"] in legacy_aliases else 1)
    catalog = {
        "seedVersion": SEED_VERSION, "units": units, "categories": categories, "items": items,
        "retiredCategories": sorted(RETIRED),
    }

    # i18n + review
    outputs = [dump(SEED / "catalog.json", catalog)]
    review = {loc: [] for loc in LOCALES[1:]}
    en_names = {it["item_id"]: it["item_name"] for it in master["items"]}
    en_cats = {c["category_id"]: c["name"] for c in master["categories"]}
    stats = {"fallback": {}, "reviewed": {}}
    for loc in LOCALES:
        t = tr.get(loc)
        cats, itms = {}, {}
        for c in master["categories"]:
            cid = c["category_id"]
            name = en_cats[cid] if loc == "en" else ((t or {}).get("categories", {}).get(cid))
            if not name:
                name = en_cats[cid]
                review[loc].append((cid, en_cats[cid], name, "fallback: no translation yet, English shown"))
            cats[cat_key[cid]] = name.strip()
        cats["pooja_items"] = legacy_i18n[loc]["categories"]["pooja_items"]
        for it in master["items"]:
            if not it.get("is_active", True):
                continue
            iid = it["item_id"]
            key = item_key[iid]
            name = en_names[iid] if loc == "en" else ((t or {}).get("items", {}).get(iid))
            if not name:
                name = en_names[iid]
                review[loc].append((iid, en_names[iid], name, "fallback: no translation yet, English shown"))
            aliases = []
            if key in legacy_aliases:
                # Released item: its reviewed seedVersion 1 name stays; the catalogue name becomes an alias.
                old = legacy_i18n[loc]["items"][key]
                aliases += [name] + old["aliases"]
                name = old["name"]
            if loc != "en":
                aliases.append(en_names[iid])
            seen, out = {name.casefold()}, []
            for a in aliases:
                a = a.strip()
                if a and a.casefold() not in seen:
                    seen.add(a.casefold())
                    out.append(a)
            itms[key] = {"name": name.strip(), "aliases": out}
        for li, _ in legacy_extra:
            itms[li["key"]] = legacy_i18n[loc]["items"][li["key"]]
        if t:
            for r in t.get("review", []):
                rid = r["id"]
                shown = itms[item_key[rid]]["name"] if rid in item_key else cats.get(cat_key.get(rid, ""), "")
                english = en_names.get(rid) or en_cats.get(rid, "")
                review[loc].append((rid, english, shown, r["why"]))
        outputs.append(dump(SEED / f"i18n/{loc}.json", {"locale": loc, "categories": cats, "items": itms}))

    outputs.append((str(REVIEW_DOC), render_review(review, tr, master, len(items))))
    outputs.append((str(TEMPLATES_DOC), render_templates(master, cat_key)))
    return outputs, review, catalog


def cell(value):
    return str(value).replace("|", "\\|").replace("\n", " ")


def render_review(review, tr, master, item_count):
    lines = [
        "# Catalogue translation review",
        "",
        "Item and category names from the India master catalogue that a native speaker should check",
        "before release (CL-107). **Generated** by `python3 tools/seed/build_india_catalog.py`; do not",
        "edit by hand. Fixes go into `tools/seed/src_data/translations/<tag>.json` (then regenerate)",
        "or, for an urgent fix, straight into `data/src/main/assets/seed/i18n/<tag>.json` plus the same",
        "edit in the source file so the next regeneration keeps it.",
        "",
        "Every name is a machine-assisted draft. The lists below are the ones the translator was",
        "least sure of, plus any `fallback` rows where English is shown for lack of a translation.",
        "",
        f"Catalogue: {len(master['categories'])} categories, {item_count} seed items, 7 languages.",
        "",
        "## How to review",
        "",
        "1. Is it the word people actually say when writing a shopping or packing list?",
        "2. Brand-like or loan words (ORS, LED, Wi-Fi) may stay in Latin script when that is normal.",
        "3. Do not translate Latin-script aliases; they exist so typed English still finds the item.",
        "",
    ]
    for loc in LOCALES[1:]:
        rows = review[loc]
        status = "translations present" if tr.get(loc) else "NO translation file: English fallback"
        lines += [f"## {LOCALE_NAMES[loc]} (`{loc}`): {len(rows)} to review, {status}", ""]
        if rows:
            lines += ["| Id | English | Draft | Why |", "|---|---|---|---|"]
            lines += [f"| `{r[0]}` | {cell(r[1])} | {cell(r[2])} | {cell(r[3])} |" for r in rows]
            lines.append("")
    return "\n".join(lines) + "\n"


def render_templates(master, cat_key):
    names = {c["category_id"]: c["name"] for c in master["categories"]}
    lines = [
        "# Checklist templates backlog (CL-319)",
        "",
        "The India master catalogue ships 15 `checklist_templates`. The app has no templates feature yet",
        "(a template would pre-fill a checklist from category ids), so they are **not** in the seed and",
        "no UI exists for them. They are kept here, generated from the catalogue by",
        "`python3 tools/seed/build_india_catalog.py`, as the backlog for issue CL-319.",
        "",
        "Category ids map to seed keys as described in [seed-catalog.md](seed-catalog.md).",
        "",
        "| Template | Name | Frequency | Categories (seed key) | Guidance |",
        "|---|---|---|---|---|",
    ]
    for t in master["checklist_templates"]:
        cats = ", ".join(f"{names[c]} (`{cat_key[c]}`)" for c in t["category_ids"])
        lines.append(f"| `{t['template_id']}` | {cell(t['name'])} | {t['frequency']} | {cats} | {cell(t['guidance'])} |")
    return "\n".join(lines) + "\n"


def main():
    outputs, review, catalog = build()
    if "--check" in sys.argv[1:]:
        stale = [p for p, t in outputs if not Path(p).is_file() or Path(p).read_text(encoding="utf-8") != t]
        if stale:
            print("stale outputs, run tools/seed/build_india_catalog.py: " + ", ".join(stale))
            return 1
        print("seed outputs are up to date")
        return 0
    for p, t in outputs:
        Path(p).parent.mkdir(parents=True, exist_ok=True)
        Path(p).write_text(t, encoding="utf-8")
    print(f"seedVersion {catalog['seedVersion']}: {len(catalog['categories'])} categories, "
          f"{len(catalog['items'])} items, review rows: " + ", ".join(f"{k} {len(v)}" for k, v in review.items()))
    return 0


if __name__ == "__main__":
    sys.exit(main())

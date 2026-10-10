#!/usr/bin/env python3
"""Validate the bundled master catalog seed (CL-113).

Checks data/src/main/assets/seed/catalog.json and every i18n/<locale>.json against the rules in
docs/seed-catalog.md. Unit codes are read from BuiltInUnits in Units.kt and locales from
SupportedLanguages.kt, so the seed cannot drift from the domain layer.

Usage: python3 tools/seed/validate_seed.py [repo_root]
Exits 0 when the seed is valid, 1 with one message per problem otherwise. Standard library only.
"""

import json
import re
import sys
from pathlib import Path

KEY_RE = re.compile(r"^[a-z][a-z0-9]*(_[a-z0-9]+)*$")

UNITS_KT = "domain/src/main/kotlin/com/dataloom/checklist/domain/model/Units.kt"
LANGUAGES_KT = "domain/src/main/kotlin/com/dataloom/checklist/domain/localization/SupportedLanguages.kt"
SEED_DIR = "data/src/main/assets/seed"


class Report:
    def __init__(self):
        self.errors = []

    def error(self, where, message):
        self.errors.append(f"{where}: {message}")


def load_json(path, report):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        report.error(path, "file not found")
    except json.JSONDecodeError as e:
        report.error(path, f"invalid JSON: {e}")
    return None


def built_in_units(root, report):
    """Returns {code: (allowsDecimal, sortOrder)} parsed from BuiltInUnits."""
    path = root / UNITS_KT
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        report.error(path, "file not found (needed for BuiltInUnits codes)")
        return {}
    pattern = re.compile(
        r'UnitDef\(UnitCode\("([A-Z_]+)"\),\s*allowsDecimal\s*=\s*(true|false),\s*sortOrder\s*=\s*(\d+)\)'
    )
    units = {m.group(1): (m.group(2) == "true", int(m.group(3))) for m in pattern.finditer(text)}
    if not units:
        report.error(path, "no BuiltInUnits definitions found; update the parser in validate_seed.py")
    return units


def supported_locales(root, report):
    path = root / LANGUAGES_KT
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        report.error(path, "file not found (needed for supported locales)")
        return []
    tags = re.findall(r'AppLanguage\(\s*tag\s*=\s*"([a-z]{2,3})"', text)
    if not tags:
        report.error(path, "no AppLanguage tags found; update the parser in validate_seed.py")
    return list(dict.fromkeys(tags))


def check_text(where, value, report):
    if not isinstance(value, str):
        report.error(where, f"must be a string, got {type(value).__name__}")
        return False
    if not value.strip():
        report.error(where, "must not be empty")
        return False
    if value != value.strip():
        report.error(where, f"has leading/trailing whitespace: {value!r}")
        return False
    if "  " in value:
        report.error(where, f"has repeated spaces: {value!r}")
    return True


def check_unique(where, keys, report):
    seen = set()
    for key in keys:
        if key in seen:
            report.error(where, f"duplicate key '{key}'")
        seen.add(key)
    return seen


def check_catalog_ids(where, ids, pattern, report):
    """catalogId is optional, but when present it must match the master catalogue scheme and be unique."""
    present = [i for i in ids if i is not None]
    for i in present:
        if not isinstance(i, str) or not re.fullmatch(pattern, i):
            report.error(where, f"catalogId {i!r} must match {pattern}")
    check_unique(f"{where} catalogId", [i for i in present if isinstance(i, str)], report)


def validate_catalog(path, catalog, units, report):
    """Returns (category keys, item keys) declared in the catalog."""
    if not isinstance(catalog, dict):
        report.error(path, "top level must be an object")
        return set(), set()

    allowed = {"seedVersion", "units", "categories", "items", "retiredCategories"}
    for extra in sorted(set(catalog) - allowed):
        report.error(path, f"unknown top-level field '{extra}'")

    version = catalog.get("seedVersion")
    if not (isinstance(version, int) and not isinstance(version, bool) and version > 0):
        report.error(path, f"seedVersion must be a positive integer, got {version!r}")

    # Units: exactly the BuiltInUnits, same flags and order.
    seed_units = catalog.get("units")
    if not isinstance(seed_units, list):
        report.error(path, "'units' must be a list")
        seed_units = []
    seed_codes = []
    for i, unit in enumerate(seed_units):
        where = f"{path} units[{i}]"
        if not isinstance(unit, dict):
            report.error(where, "must be an object")
            continue
        code = unit.get("code")
        seed_codes.append(code)
        if code not in units:
            report.error(where, f"code {code!r} is not a BuiltInUnits code ({', '.join(units)})")
            continue
        allows_decimal, sort_order = units[code]
        if unit.get("allowsDecimal") is not allows_decimal:
            report.error(where, f"{code} allowsDecimal must be {str(allows_decimal).lower()} as in BuiltInUnits")
        if unit.get("sortOrder") != sort_order:
            report.error(where, f"{code} sortOrder must be {sort_order} as in BuiltInUnits")
    check_unique(f"{path} units", seed_codes, report)
    missing_units = [c for c in units if c not in seed_codes]
    if missing_units:
        report.error(path, f"units missing BuiltInUnits codes: {', '.join(missing_units)}")
    elif seed_codes != list(units):
        report.error(path, f"units must follow BuiltInUnits order: {', '.join(units)}")

    # Categories.
    categories = catalog.get("categories")
    if not isinstance(categories, list) or not categories:
        report.error(path, "'categories' must be a non-empty list")
        categories = []
    category_keys = []
    for i, cat in enumerate(categories):
        where = f"{path} categories[{i}]"
        if not isinstance(cat, dict):
            report.error(where, "must be an object")
            continue
        key = cat.get("key")
        if not isinstance(key, str) or not KEY_RE.match(key):
            report.error(where, f"key {key!r} must be lowercase snake_case")
            continue
        category_keys.append(key)
        check_text(f"{where} ({key}) icon", cat.get("icon"), report)
        sort_order = cat.get("sortOrder")
        if not (isinstance(sort_order, int) and not isinstance(sort_order, bool)):
            report.error(where, f"sortOrder must be an integer, got {sort_order!r}")
    category_set = check_unique(f"{path} categories", category_keys, report)
    check_catalog_ids(f"{path} categories", [c.get("catalogId") for c in categories if isinstance(c, dict)], r"CAT\d{3}", report)
    retired = catalog.get("retiredCategories", [])
    if not isinstance(retired, list) or any(not isinstance(k, str) for k in retired):
        report.error(path, "'retiredCategories' must be a list of keys")
    else:
        for k in retired:
            if k in category_set:
                report.error(path, f"retired category '{k}' is still declared in categories")

    # Items.
    items = catalog.get("items")
    if not isinstance(items, list):
        report.error(path, "'items' must be a list")
        items = []
    item_keys = []
    for i, item in enumerate(items):
        where = f"{path} items[{i}]"
        if not isinstance(item, dict):
            report.error(where, "must be an object")
            continue
        key = item.get("key")
        if not isinstance(key, str) or not KEY_RE.match(key):
            report.error(where, f"key {key!r} must be lowercase snake_case")
            continue
        item_keys.append(key)
        where = f"{where} ({key})"
        if item.get("category") not in category_set:
            report.error(where, f"category {item.get('category')!r} is not a declared category")
        if "defaultUnit" not in item:
            report.error(where, "defaultUnit is required (use null for no unit)")
        tags = item.get("tags", [])
        if not isinstance(tags, list) or any(not isinstance(t, str) or not t.strip() for t in tags):
            report.error(where, "tags must be a list of non-empty strings")
        if "subcategory" in item:
            check_text(f"{where} subcategory", item.get("subcategory"), report)
        unit = item.get("defaultUnit")
        if unit is not None and unit not in units:
            report.error(where, f"defaultUnit {unit!r} is not a BuiltInUnits code")
    item_set = check_unique(f"{path} items", item_keys, report)
    check_catalog_ids(f"{path} items", [i.get("catalogId") for i in items if isinstance(i, dict)], r"ITM\d{4}", report)
    return category_set, item_set


def validate_locale(path, locale, data, category_keys, item_keys, report):
    """Returns the number of aliases in the file."""
    if not isinstance(data, dict):
        report.error(path, "top level must be an object")
        return 0
    if data.get("locale") != locale:
        report.error(path, f"locale field must be '{locale}', got {data.get('locale')!r}")

    categories = data.get("categories")
    if not isinstance(categories, dict):
        report.error(path, "'categories' must be an object")
        categories = {}
    for key in sorted(category_keys - set(categories)):
        report.error(path, f"missing category name for '{key}'")
    for key in sorted(set(categories) - category_keys):
        report.error(path, f"extra category '{key}' not in catalog.json")
    for key, name in categories.items():
        check_text(f"{path} categories.{key}", name, report)

    items = data.get("items")
    if not isinstance(items, dict):
        report.error(path, "'items' must be an object")
        items = {}
    for key in sorted(item_keys - set(items)):
        report.error(path, f"missing item translation for '{key}'")
    for key in sorted(set(items) - item_keys):
        report.error(path, f"extra item '{key}' not in catalog.json")

    alias_count = 0
    for key, entry in items.items():
        where = f"{path} items.{key}"
        if not isinstance(entry, dict):
            report.error(where, "must be an object with 'name' and optional 'aliases'")
            continue
        check_text(f"{where}.name", entry.get("name"), report)
        aliases = entry.get("aliases", [])
        if not isinstance(aliases, list):
            report.error(f"{where}.aliases", "must be a list of strings")
            continue
        seen = set()
        for j, alias in enumerate(aliases):
            if not check_text(f"{where}.aliases[{j}]", alias, report):
                continue
            folded = alias.casefold()
            if folded in seen:
                report.error(f"{where}.aliases", f"duplicate alias {alias!r}")
            seen.add(folded)
            if isinstance(entry.get("name"), str) and folded == entry["name"].casefold():
                report.error(f"{where}.aliases", f"alias {alias!r} repeats the name")
            alias_count += 1
    return alias_count


def main():
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
    report = Report()

    units = built_in_units(root, report)
    locales = supported_locales(root, report)
    seed = root / SEED_DIR
    catalog_path = seed / "catalog.json"
    catalog = load_json(catalog_path, report)

    category_keys, item_keys = set(), set()
    if catalog is not None:
        category_keys, item_keys = validate_catalog(catalog_path, catalog, units, report)

    alias_counts = {}
    i18n = seed / "i18n"
    for locale in locales:
        path = i18n / f"{locale}.json"
        data = load_json(path, report)
        if data is not None:
            alias_counts[locale] = validate_locale(path, locale, data, category_keys, item_keys, report)
    if i18n.is_dir():
        for path in sorted(i18n.glob("*.json")):
            if path.stem not in locales:
                report.error(path, f"locale '{path.stem}' is not in SupportedLanguages")

    if report.errors:
        print(f"Seed catalog INVALID: {len(report.errors)} problem(s)", file=sys.stderr)
        for message in report.errors:
            print(f"  - {message}", file=sys.stderr)
        return 1

    aliases = ", ".join(f"{loc} {n}" for loc, n in alias_counts.items())
    print(
        f"Seed catalog OK: seedVersion {catalog['seedVersion']}, {len(units)} units, "
        f"{len(category_keys)} categories, {len(item_keys)} items, {len(locales)} locales "
        f"(aliases: {aliases})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

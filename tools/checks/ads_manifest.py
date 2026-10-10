#!/usr/bin/env python3
"""Checks the ads build flag (CL-370) against the merged manifests of the built variants.

For every flavor whose ``checklist.ads.<flavor>`` in gradle.properties is false, each merged manifest
of that flavor's built variants must not request INTERNET or AD_ID and must not mention AdMob. Flavors
with ads on must carry the AdMob application ID. Variants that were not built are skipped; finding no
manifest for an ad-free flavor at all is an error, so a moved intermediates path cannot pass silently.

Usage: python3 tools/checks/ads_manifest.py app/build/intermediates
Standard library only; exit 1 on any problem.
"""

from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
FLAVORS = ("dev", "staging", "production")
FORBIDDEN = ("android.permission.INTERNET", "com.google.android.gms.permission.AD_ID", "com.google.android.gms.ads")


def ads_flags(properties: pathlib.Path) -> dict[str, bool]:
    values: dict[str, str] = {}
    for line in properties.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return {flavor: values.get(f"checklist.ads.{flavor}", "false") == "true" for flavor in FLAVORS}


def merged_manifests(intermediates: pathlib.Path, flavor: str) -> list[pathlib.Path]:
    found = []
    for folder in ("merged_manifest", "merged_manifests"):
        base = intermediates / folder
        if base.is_dir():
            found += [p for p in base.glob(f"{flavor}*/**/AndroidManifest.xml") if p.is_file()]
    return found


def main(argv: list[str]) -> int:
    intermediates = pathlib.Path(argv[1] if len(argv) > 1 else "app/build/intermediates")
    flags = ads_flags(ROOT / "gradle.properties")
    errors = []
    for flavor, enabled in flags.items():
        manifests = merged_manifests(intermediates, flavor)
        if not manifests:
            if not enabled:
                errors.append(f"{flavor}: no merged manifest found under {intermediates}")
            continue
        for manifest in manifests:
            text = manifest.read_text(encoding="utf-8")
            if enabled:
                if "com.google.android.gms.ads.APPLICATION_ID" not in text:
                    errors.append(f"{manifest}: ads are on but the AdMob application ID is missing")
            else:
                errors += [f"{manifest}: ads are off but it contains {item}" for item in FORBIDDEN if item in text]
        print(f"{flavor}: ads {'on' if enabled else 'off'}, {len(manifests)} merged manifest(s) checked")
    for error in errors:
        print(f"::error::{error}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

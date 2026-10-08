#!/usr/bin/env bash
# Prints the app version computed from version.properties, using the same
# formula and limits as app/build.gradle.kts (ADR-016, "Option B"):
#   versionCode = MAJOR*10000 + MINOR*1000 + PATCH*100 + BUILD
#   versionName = MAJOR.MINOR.PATCH
#
# Usage:
#   ci/scripts/version-code.sh              # prints the versionCode, e.g. 10000
#   ci/scripts/version-code.sh --name       # prints the versionName, e.g. 1.0.0
#   ci/scripts/version-code.sh --env        # prints VERSION_NAME=... and VERSION_CODE=... lines
#   ci/scripts/version-code.sh --file path/to/version.properties [--name|--env]
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
props_file="${script_dir}/../../version.properties"
mode="code"

usage() {
  sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name) mode="name" ;;
    --env) mode="env" ;;
    --code) mode="code" ;;
    --file)
      [[ $# -ge 2 ]] || { echo "error: --file needs a path" >&2; exit 2; }
      props_file="$2"
      shift
      ;;
    -h | --help) usage; exit 0 ;;
    *) echo "error: unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

[[ -f "$props_file" ]] || { echo "error: version file not found: $props_file" >&2; exit 1; }

# read_part KEY MIN MAX: prints the integer value of KEY, failing when it is
# missing, not a plain number, or outside [MIN, MAX] (same ranges as Gradle).
read_part() {
  local key="$1" min="$2" max="$3" value
  value="$(tr -d '\r' < "$props_file" \
    | sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*\([^[:space:]]*\)[[:space:]]*$/\1/p" \
    | tail -n 1)"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    echo "error: version.properties: ${key} is missing or not a number (got '${value}')" >&2
    return 1
  fi
  value=$((10#$value))
  if ((value < min || value > max)); then
    echo "error: version.properties: ${key}=${value} must be in ${min}..${max}" >&2
    return 1
  fi
  echo "$value"
}

major="$(read_part VERSION_MAJOR 1 9999)"
minor="$(read_part VERSION_MINOR 0 9)"
patch="$(read_part VERSION_PATCH 0 9)"
build="$(read_part VERSION_BUILD 0 99)"

version_code=$((major * 10000 + minor * 1000 + patch * 100 + build))
version_name="${major}.${minor}.${patch}"

case "$mode" in
  code) echo "$version_code" ;;
  name) echo "$version_name" ;;
  env) printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$version_name" "$version_code" ;;
esac

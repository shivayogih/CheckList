#!/usr/bin/env bash
# Runs detekt 2 (Kotlin static analysis, CL-182) over every module's Kotlin sources.
#
# detekt runs as its standalone CLI, not as a Gradle plugin, so it does not depend on the Android
# Gradle Plugin or the Kotlin Gradle plugin version (AGP 9 built-in Kotlin). The CLI jar is
# downloaded once from Maven Central and its SHA-256 is checked before it runs.
#
# Usage:
#   ci/scripts/detekt.sh                    analyse; fails on any finding not in the baseline
#   ci/scripts/detekt.sh --update-baseline  rewrite config/detekt/baseline.xml (pre-existing code only)
# Reports: build/reports/detekt/detekt.{html,sarif}
# Needs: Java 17+, curl, sha256sum (or shasum).
set -euo pipefail

readonly DETEKT_VERSION="2.0.0-alpha.6"
# SHA-256 of detekt-cli-2.0.0-alpha.6-all.jar from Maven Central (its published .sha1 also matches).
readonly DETEKT_SHA256="d46ca62ea4d62769b5d5c3ba94d49fa9b80ba11c7dba74ddb6df7fcc2c19c5fd"
readonly JAR_NAME="detekt-cli-${DETEKT_VERSION}-all.jar"
readonly JAR_URL="https://repo1.maven.org/maven2/dev/detekt/detekt-cli/${DETEKT_VERSION}/${JAR_NAME}"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cache_dir="${DETEKT_CACHE_DIR:-${HOME}/.cache/checklist-detekt}"
jar="${cache_dir}/${JAR_NAME}"
baseline="${root}/config/detekt/baseline.xml"
reports="${root}/build/reports/detekt"

sha256() {
  if command -v sha256sum > /dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

if [[ ! -f "$jar" ]] || [[ "$(sha256 "$jar")" != "$DETEKT_SHA256" ]]; then
  mkdir -p "$cache_dir"
  echo "Downloading ${JAR_NAME}" >&2
  curl -fsSL --retry 3 -o "${jar}.part" "$JAR_URL"
  actual="$(sha256 "${jar}.part")"
  if [[ "$actual" != "$DETEKT_SHA256" ]]; then
    rm -f "${jar}.part"
    echo "error: checksum mismatch for ${JAR_NAME}: ${actual}" >&2
    exit 1
  fi
  mv "${jar}.part" "$jar"
fi

inputs=()
for module in app data domain ai; do
  [[ -d "${root}/${module}/src" ]] && inputs+=("${root}/${module}/src")
done
input_list="$(IFS=:; echo "${inputs[*]}")"

args=(
  --input "$input_list"
  --excludes "**/build/**"
  --build-upon-default-config
  --config "${root}/config/detekt/detekt.yml"
  --language-version 2.4
  --jvm-target 17
  --base-path "$root"
)

if [[ "${1:-}" == "--update-baseline" ]]; then
  java -jar "$jar" "${args[@]}" --baseline "$baseline" --create-baseline
  echo "Baseline written to ${baseline#"$root"/}. Only pre-existing code belongs in it." >&2
  exit 0
fi

mkdir -p "$reports"
[[ -f "$baseline" ]] && args+=(--baseline "$baseline")
java -jar "$jar" "${args[@]}" \
  --report "html:${reports}/detekt.html" \
  --report "sarif:${reports}/detekt.sarif"

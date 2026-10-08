#!/usr/bin/env bash
# Release gate for the Bitrise `production` workflow (and a lighter check for `staging`).
#
# Full mode (default), for a release tag such as v1.0.0:
#   1. the tag has the form vMAJOR.MINOR.PATCH;
#   2. the tag equals "v" + versionName from version.properties;
#   3. the versionCode is greater than every versionCode recorded in
#      docs/project-management/releases.csv (Play rejects reused or lower codes);
#   4. the tagged commit is reachable from origin/main (releases come from main only).
#
# --code-only mode (staging): runs check 3 only, so a release/* push that forgot to
# bump VERSION_BUILD fails in seconds instead of after a full build.
#
# Usage:
#   ci/scripts/validate-version.sh [TAG]          # TAG defaults to $BITRISE_GIT_TAG
#   ci/scripts/validate-version.sh --code-only
#
# Environment (all optional):
#   RELEASES_CSV      path to releases.csv (default docs/project-management/releases.csv)
#   MAIN_BRANCH       release branch on the remote (default main)
#   GIT_REMOTE        remote name (default origin)
#   SKIP_MAIN_CHECK   "true" skips check 4, for local dry runs only
#
# On Bitrise, VERSION_NAME and VERSION_CODE are exported to later steps via envman.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
releases_csv="${RELEASES_CSV:-${repo_root}/docs/project-management/releases.csv}"
main_branch="${MAIN_BRANCH:-main}"
remote="${GIT_REMOTE:-origin}"

code_only=false
tag="${BITRISE_GIT_TAG:-}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --code-only) code_only=true ;;
    -h | --help) sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "error: unknown option: $1" >&2; exit 2 ;;
    *) tag="$1" ;;
  esac
  shift
done

fail() {
  echo "FAILED: $*" >&2
  exit 1
}

version_name="$("${script_dir}/version-code.sh" --name)"
version_code="$("${script_dir}/version-code.sh")"
echo "version.properties: versionName=${version_name} versionCode=${version_code}"

# --- Check 3: versionCode must be greater than every recorded upload. -----------------
# releases.csv columns: Version Name, Version Code, Tag, Release Branch, Issues,
# Build Number, Track, Status, Approved By, Released At. Python's csv module is used
# because the Issues column can contain quoted commas.
last_code=0
if [[ -f "$releases_csv" ]]; then
  last_code="$(python3 -I - "$releases_csv" <<'PY'
import csv
import sys

with open(sys.argv[1], newline="", encoding="utf-8") as handle:
    reader = csv.reader(handle)
    header = [column.strip().lower() for column in next(reader, [])]
    if "version code" not in header:
        sys.exit("releases.csv has no 'Version Code' column")
    index = header.index("version code")
    codes = [0]
    for row in reader:
        if len(row) > index and row[index].strip():
            value = row[index].strip()
            if not value.isdigit():
                sys.exit(f"releases.csv: Version Code '{value}' is not a number")
            codes.append(int(value))
print(max(codes))
PY
)" || fail "could not read ${releases_csv}"
  echo "releases.csv: highest recorded versionCode=${last_code}"
else
  echo "warning: ${releases_csv} not found; treating the highest recorded versionCode as 0" >&2
fi

if ((version_code <= last_code)); then
  fail "versionCode ${version_code} is not greater than ${last_code}, the highest code already recorded in releases.csv. Bump VERSION_BUILD (or the version) in version.properties."
fi
echo "OK: versionCode ${version_code} > ${last_code}"

if command -v envman > /dev/null 2>&1; then
  envman add --key VERSION_NAME --value "$version_name"
  envman add --key VERSION_CODE --value "$version_code"
fi

if [[ "$code_only" == true ]]; then
  exit 0
fi

# --- Checks 1 and 2: tag format and tag == versionName. --------------------------------
[[ -n "$tag" ]] || fail "no tag given (pass one or set BITRISE_GIT_TAG)"
if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  fail "tag '${tag}' does not match vMAJOR.MINOR.PATCH"
fi
if [[ "$tag" != "v${version_name}" ]]; then
  fail "tag '${tag}' does not match versionName '${version_name}' in version.properties (expected v${version_name})"
fi
echo "OK: tag ${tag} matches versionName ${version_name}"

# --- Check 4: the tagged commit must be on origin/main. --------------------------------
if [[ "${SKIP_MAIN_CHECK:-false}" == "true" ]]; then
  echo "warning: SKIP_MAIN_CHECK=true, not checking that ${tag} is on ${remote}/${main_branch}" >&2
  exit 0
fi

cd "$repo_root"
fetch_args=(--quiet --no-tags "$remote" "+refs/heads/${main_branch}:refs/remotes/${remote}/${main_branch}")
if [[ "$(git rev-parse --is-shallow-repository)" == "true" ]]; then
  # A shallow clone cannot prove ancestry; fetch the full history (small repo, a few seconds).
  git fetch --unshallow "${fetch_args[@]}"
else
  git fetch "${fetch_args[@]}"
fi

if tag_commit="$(git rev-parse --verify --quiet "refs/tags/${tag}^{commit}")"; then
  :
else
  echo "warning: tag ${tag} not found locally; using HEAD (the commit Bitrise checked out for the tag)" >&2
  tag_commit="$(git rev-parse HEAD)"
fi

if ! git merge-base --is-ancestor "$tag_commit" "refs/remotes/${remote}/${main_branch}"; then
  fail "tag ${tag} (${tag_commit}) is not reachable from ${remote}/${main_branch}. Release tags must be created on main after the release PR is merged."
fi
echo "OK: ${tag} is on ${remote}/${main_branch}"

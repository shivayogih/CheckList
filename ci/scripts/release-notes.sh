#!/usr/bin/env bash
# Builds user-facing release notes from the commit subjects since the previous
# release tag (vX.Y.Z), for Google Play "What's new" and Firebase App Distribution.
#
# Rules:
#   - only commit subjects of the form "CL-<id> <Summary>" are considered;
#   - merge commits and internal work (CI, build, docs, tests, refactors, tooling,
#     versioning, signing...) are dropped, as is any subject marked [internal];
#   - each note reads "- <Summary> (CL-<id>)"; URLs, e-mail addresses and @mentions
#     are removed so nothing internal leaks into a store listing;
#   - the text is capped at 500 characters (Google Play's limit for release notes);
#   - if nothing user-facing remains: "Bug fixes and improvements."
#
# Usage:
#   ci/scripts/release-notes.sh [--to REF] [--from REF] [--max N] [--out-dir DIR] [--locale en-US]
#     --to       last commit to include (default HEAD)
#     --from     exclusive start (default: the newest v* tag before --to; none = whole history)
#     --max      keep at most N notes (default 15)
#     --out-dir  also write DIR/whatsnew-<locale>, the layout google-play-deploy expects
#     --locale   Play locale for the file name (default en-US)
# The notes are always printed to stdout.
set -euo pipefail

to_ref="HEAD"
from_ref=""
max_notes=15
out_dir=""
locale="en-US"
readonly play_limit=500

while [[ $# -gt 0 ]]; do
  case "$1" in
    --to | --from | --max | --out-dir | --locale)
      [[ $# -ge 2 ]] || { echo "error: $1 needs a value" >&2; exit 2; }
      case "$1" in
        --to) to_ref="$2" ;;
        --from) from_ref="$2" ;;
        --max) max_notes="$2" ;;
        --out-dir) out_dir="$2" ;;
        --locale) locale="$2" ;;
      esac
      shift
      ;;
    -h | --help) sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "error: unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

[[ "$max_notes" =~ ^[0-9]+$ ]] || { echo "error: --max must be a number" >&2; exit 2; }

if [[ -z "$from_ref" ]]; then
  # The newest release tag strictly before --to. "--to^" excludes --to itself, so a
  # build of tag v1.1.0 gets the notes since v1.0.0, not an empty range.
  if git rev-parse --verify --quiet "${to_ref}^" > /dev/null; then
    from_ref="$(git describe --tags --abbrev=0 --match 'v[0-9]*' "${to_ref}^" 2> /dev/null || true)"
  fi
fi

if [[ -n "$from_ref" ]]; then
  range="${from_ref}..${to_ref}"
else
  range="$to_ref"
fi

# Subjects that describe engineering work rather than something a user notices.
internal_pattern='\[internal\]|\b(ci|bitrise|workflows?|pipelines?|gradle|builds?|lint|detekt|ktlint|tests?|testing|docs?|documentation|adr|readme|refactor|chore|architecture|release notes|versions?|versioning|dependenc(y|ies)|deps|bump|issue tracker|csv|gitignore|hygiene|secrets?|signing|keystore|proguard|r8|phase [0-9]+)\b'

notes=()
seen="|"
while IFS= read -r subject; do
  [[ "$subject" =~ ^(CL-[0-9]+)[[:space:]]+(.+)$ ]] || continue
  issue="${BASH_REMATCH[1]}"
  summary="${BASH_REMATCH[2]}"
  if printf '%s\n' "$summary" | grep -Eiq "$internal_pattern"; then
    continue
  fi
  # Strip anything that could identify people or internal systems.
  summary="$(printf '%s\n' "$summary" \
    | sed -E 's#https?://[^[:space:]]+##g; s#[[:alnum:]._%+-]+@[[:alnum:].-]+\.[[:alpha:]]{2,}##g; s#(^|[[:space:]])@[[:alnum:]_-]+#\1#g; s|[[:space:]]*\(#[0-9]+\)||g; s#[[:space:]]+# #g; s#^ ##; s# $##')"
  [[ -n "$summary" ]] || continue
  note="- ${summary} (${issue})"
  [[ "$seen" == *"|${note}|"* ]] && continue
  seen="${seen}${note}|"
  notes+=("$note")
  ((${#notes[@]} < max_notes)) || break
done < <(git log --no-merges --format='%s' "$range")

text=""
for note in "${notes[@]+"${notes[@]}"}"; do
  candidate="${text:+${text}$'\n'}${note}"
  ((${#candidate} <= play_limit)) || break
  text="$candidate"
done
[[ -n "$text" ]] || text="Bug fixes and improvements."

if [[ -n "$out_dir" ]]; then
  mkdir -p "$out_dir"
  printf '%s\n' "$text" > "${out_dir}/whatsnew-${locale}"
  echo "release notes written to ${out_dir}/whatsnew-${locale} (range: ${range})" >&2
fi
printf '%s\n' "$text"

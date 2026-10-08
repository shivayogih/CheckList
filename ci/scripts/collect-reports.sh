#!/usr/bin/env bash
# Gathers what Gradle produced so Bitrise can show it, even when a test or lint failed:
#   - merges every JUnit XML file under */build/test-results/ into one file, which the
#     "Export test results to Test Reports" step (custom-test-results-export) uploads;
#   - packs the HTML test and lint reports into reports.tar.gz in $BITRISE_DEPLOY_DIR,
#     which "Deploy to Bitrise.io" attaches to the build as an artifact.
# On Bitrise it exports CI_JUNIT_FILE (empty when there were no test results).
#
# Usage: ci/scripts/collect-reports.sh [OUTPUT_DIR]   (default build/ci)
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
out_dir="${1:-${repo_root}/build/ci}"
deploy_dir="${BITRISE_DEPLOY_DIR:-${out_dir}/deploy}"
junit_file="${out_dir}/junit/unit-tests.xml"

mkdir -p "${out_dir}/junit" "$deploy_dir"
cd "$repo_root"

mapfile -t xml_files < <(find . -path ./build -prune -o -type f -path '*/build/test-results/*' -name 'TEST-*.xml' -print | sort)

if ((${#xml_files[@]} > 0)); then
  python3 -I - "$junit_file" "${xml_files[@]}" <<'PY'
import sys
import xml.etree.ElementTree as ET

output, inputs = sys.argv[1], sys.argv[2:]
merged = ET.Element("testsuites")
for path in inputs:
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
    merged.extend(suites)
ET.ElementTree(merged).write(output, encoding="utf-8", xml_declaration=True)
print(f"merged {len(inputs)} JUnit files into {output}")
PY
else
  echo "no JUnit results found"
  junit_file=""
fi

mapfile -t report_dirs < <(find . -path ./build -prune -o -type d -path '*/build/reports' -print | sort)
if ((${#report_dirs[@]} > 0)); then
  tar -czf "${deploy_dir}/reports.tar.gz" "${report_dirs[@]}"
  echo "packed ${#report_dirs[@]} report folders into ${deploy_dir}/reports.tar.gz"
fi

if command -v envman > /dev/null 2>&1; then
  envman add --key CI_JUNIT_FILE --value "$junit_file"
fi

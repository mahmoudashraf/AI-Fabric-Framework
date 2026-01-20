#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  evaluate-failsafe-success-rate.sh \
    --reports-dir <path> \
    --scorecard-path <path> \
    [--suite <name>] \
    [--llm <provider>] \
    [--embedding <provider>] \
    [--vector-db <provider>] \
    [--min-success-rate <0..1>] \
    [--min-considered-tests <n>]

Environment fallbacks:
  AI_PROVIDERS_REAL_API_MINIMUM_SUCCESS_RATE (default: 0.85)
  AI_PROVIDERS_REAL_API_MINIMUM_CONSIDERED_TESTS (default: 20)

Exit codes:
  0: Meets thresholds
  2: Below thresholds / insufficient considered tests
  3: No reports found / could not evaluate
USAGE
}

REPORTS_DIR=""
SCORECARD_PATH=""
SUITE=""
LLM=""
EMBEDDING=""
VECTOR_DB=""
MIN_SUCCESS_RATE="${AI_PROVIDERS_REAL_API_MINIMUM_SUCCESS_RATE:-0.85}"
MIN_CONSIDERED_TESTS="${AI_PROVIDERS_REAL_API_MINIMUM_CONSIDERED_TESTS:-20}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reports-dir) REPORTS_DIR="${2:-}"; shift 2 ;;
    --scorecard-path) SCORECARD_PATH="${2:-}"; shift 2 ;;
    --suite) SUITE="${2:-}"; shift 2 ;;
    --llm) LLM="${2:-}"; shift 2 ;;
    --embedding) EMBEDDING="${2:-}"; shift 2 ;;
    --vector-db) VECTOR_DB="${2:-}"; shift 2 ;;
    --min-success-rate) MIN_SUCCESS_RATE="${2:-}"; shift 2 ;;
    --min-considered-tests) MIN_CONSIDERED_TESTS="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$REPORTS_DIR" || -z "$SCORECARD_PATH" ]]; then
  echo "Missing required args: --reports-dir and --scorecard-path" >&2
  usage
  exit 1
fi

python3 - "$REPORTS_DIR" "$SCORECARD_PATH" "$SUITE" "$LLM" "$EMBEDDING" "$VECTOR_DB" "$MIN_SUCCESS_RATE" "$MIN_CONSIDERED_TESTS" <<'PY'
import datetime as _dt
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

reports_dir = sys.argv[1]
scorecard_path = sys.argv[2]
suite = sys.argv[3] or None
llm = sys.argv[4] or None
embedding = sys.argv[5] or None
vector_db = sys.argv[6] or None
min_success_rate = float(sys.argv[7])
min_considered = int(sys.argv[8])

files = sorted(glob.glob(os.path.join(reports_dir, "TEST-*.xml")))
now = _dt.datetime.now(_dt.timezone.utc).isoformat()

totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
if not files:
    os.makedirs(os.path.dirname(scorecard_path), exist_ok=True)
    scorecard = {
        "suite": suite,
        "provider": {"llm": llm, "embedding": embedding, "vectorDb": vector_db},
        "thresholds": {"minimumSuccessRate": min_success_rate, "minimumConsideredTests": min_considered},
        "results": None,
        "decision": {"passed": False, "reason": "No failsafe reports found"},
        "reportsDir": reports_dir,
        "generatedAt": now,
    }
    with open(scorecard_path, "w", encoding="utf-8") as f:
        json.dump(scorecard, f, indent=2, sort_keys=True)
    print(f"[realapi] No failsafe reports found in {reports_dir}")
    sys.exit(3)

def _accumulate_testsuite(ts):
    totals["tests"] += int(ts.attrib.get("tests", 0) or 0)
    totals["failures"] += int(ts.attrib.get("failures", 0) or 0)
    totals["errors"] += int(ts.attrib.get("errors", 0) or 0)
    totals["skipped"] += int(ts.attrib.get("skipped", 0) or 0)

for path in files:
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue

    if root.tag == "testsuite":
        _accumulate_testsuite(root)
    elif root.tag == "testsuites":
        for ts in root.findall("testsuite"):
            _accumulate_testsuite(ts)

considered = max(0, totals["tests"] - totals["skipped"])
failed = totals["failures"] + totals["errors"]
passed = max(0, considered - failed)
success_rate = (passed / considered) if considered else 0.0

meets_min_considered = considered >= min_considered
meets_success = considered > 0 and success_rate >= min_success_rate
passed_gate = meets_min_considered and meets_success

reason_parts = []
if not meets_min_considered:
    reason_parts.append(f"consideredTests {considered} < minimumConsideredTests {min_considered}")
if not meets_success:
    reason_parts.append(f"successRate {success_rate:.3f} < minimumSuccessRate {min_success_rate:.3f}")
reason = "OK" if passed_gate else "; ".join(reason_parts) or "Below threshold"

scorecard = {
    "suite": suite,
    "provider": {"llm": llm, "embedding": embedding, "vectorDb": vector_db},
    "thresholds": {"minimumSuccessRate": min_success_rate, "minimumConsideredTests": min_considered},
    "results": {
        "consideredTests": considered,
        "passed": passed,
        "failed": failed,
        "skipped": totals["skipped"],
        "successRate": success_rate,
    },
    "decision": {"passed": passed_gate, "reason": reason},
    "reportsDir": reports_dir,
    "generatedAt": now,
}

os.makedirs(os.path.dirname(scorecard_path), exist_ok=True)
with open(scorecard_path, "w", encoding="utf-8") as f:
    json.dump(scorecard, f, indent=2, sort_keys=True)

print(
    f"[realapi] suite={suite or 'unknown'} "
    f"passed={passed} failed={failed} skipped={totals['skipped']} considered={considered} "
    f"successRate={success_rate:.3f} minSuccessRate={min_success_rate:.3f} "
    f"minConsidered={min_considered} -> {'PASS' if passed_gate else 'FAIL'} ({reason})"
)

sys.exit(0 if passed_gate else 2)
PY


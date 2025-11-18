#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <signals.json|signals.jsonl> [base-url]" >&2
  exit 1
fi

SIGNAL_FILE=$1
BASE_URL=${2:-"http://localhost:8080"}
ENDPOINT="${BASE_URL%/}/api/ai-behavior/signals/batch"

if [[ ! -f "$SIGNAL_FILE" ]]; then
  echo "Signal file '${SIGNAL_FILE}' does not exist." >&2
  exit 1
fi

if command -v jq >/dev/null 2>&1; then
  if [[ "$SIGNAL_FILE" == *.jsonl ]]; then
    BODY=$(jq -s '.' "$SIGNAL_FILE")
  else
    BODY=$(cat "$SIGNAL_FILE")
  fi
else
  BODY=$(cat "$SIGNAL_FILE")
fi

echo "Replaying signals to ${ENDPOINT}"
curl -sS -X POST \
  -H "Content-Type: application/json" \
  -d "${BODY}" \
  "${ENDPOINT}"
echo

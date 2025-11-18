#!/usr/bin/env bash

set -euo pipefail

SCHEMA_DIR=${1:-"ai-infrastructure-module/ai-infrastructure-behavior/src/main/resources/behavior/schemas"}

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to run schema-doctor." >&2
  exit 1
fi

python3 - <<'PY'
import sys
from pathlib import Path
import yaml

schema_dir = Path(sys.argv[1])
if not schema_dir.exists():
    print(f"Schema directory {schema_dir} does not exist")
    sys.exit(1)

errors = []
seen = set()
for schema_file in sorted(schema_dir.rglob("*.yml")):
    data = yaml.safe_load(schema_file.read_text())
    if not isinstance(data, list):
        errors.append(f"{schema_file}: expected a list of definitions")
        continue
    for entry in data:
        schema_id = entry.get("id")
        if not schema_id:
            errors.append(f"{schema_file}: missing id")
            continue
        if schema_id in seen:
            errors.append(f"duplicate schema id: {schema_id}")
        seen.add(schema_id)
        for attr in entry.get("attributes", []):
            name = attr.get("name")
            if not name:
                errors.append(f"{schema_id}: attribute missing name")
            if attr.get("enumValues"):
                values = attr["enumValues"]
                if any(v is None for v in values):
                    errors.append(f"{schema_id}.{name}: enumValues contain null entries")

if errors:
    print("\n".join(errors))
    sys.exit(1)

print(f"Validated {len(seen)} schema definitions under {schema_dir}")
PY "${SCHEMA_DIR}"

#!/usr/bin/env python3
"""
Schema Doctor – validates behavior schema YAML files and optionally emits a consolidated JSON manifest.

Usage:
  python scripts/schema-doctor.py --schemas ai-infrastructure-module/ai-infrastructure-behavior/src/main/resources/behavior/schemas
  python scripts/schema-doctor.py --schemas ./custom-schemas --emit-json ./out/behavior-schemas.json
"""

import argparse
import json
import sys
from pathlib import Path

try:
    import yaml
except ImportError as exc:  # pragma: no cover - helper script
    sys.exit("PyYAML is required to run schema-doctor (pip install pyyaml)")  # pragma: no cover


def load_definitions(schemas_path: Path):
    definitions = []
    for file_path in schemas_path.rglob("*.yml"):
        definitions.extend(_load_file(file_path))
    for file_path in schemas_path.rglob("*.yaml"):
        definitions.extend(_load_file(file_path))
    return definitions


def _load_file(file_path: Path):
    if not file_path.is_file():
        return []
    data = yaml.safe_load(file_path.read_text(encoding="utf-8")) or []
    if isinstance(data, dict):
        return [data]
    return list(data)


def validate(definitions):
    seen = {}
    errors = []
    for entry in definitions:
        schema_id = entry.get("id")
        if not schema_id:
            errors.append("Schema entry missing 'id'")
            continue
        if schema_id in seen:
            errors.append(f"Duplicate schema id detected: {schema_id} ({seen[schema_id]} / {entry.get('summary')})")
        seen[schema_id] = entry.get("summary", "")

        attributes = entry.get("attributes") or []
        for attr in attributes:
            name = attr.get("name")
            if not name:
                errors.append(f"{schema_id}: attribute missing name")
            if attr.get("required") and attr.get("type") == "array" and not attr.get("maxItems"):
                errors.append(f"{schema_id}.{name}: required array should declare maxItems")
    return errors, seen


def main():
    parser = argparse.ArgumentParser(description="Validate behavior schema YAML descriptors.")
    parser.add_argument("--schemas", type=Path, required=True, help="Directory containing *.yml schema files")
    parser.add_argument("--emit-json", type=Path, help="Optional output file with consolidated schema JSON")
    args = parser.parse_args()

    if not args.schemas.exists():
        sys.exit(f"Schema path {args.schemas} does not exist")

    definitions = load_definitions(args.schemas)
    errors, seen = validate(definitions)

    if errors:
        print("\n".join(errors))
        sys.exit(1)

    print(f"Validated {len(seen)} schema definitions from {args.schemas}")

    if args.emit_json:
        args.emit_json.parent.mkdir(parents=True, exist_ok=True)
        args.emit_json.write_text(json.dumps(definitions, indent=2), encoding="utf-8")
        print(f"Wrote consolidated schema JSON to {args.emit_json}")


if __name__ == "__main__":
    main()

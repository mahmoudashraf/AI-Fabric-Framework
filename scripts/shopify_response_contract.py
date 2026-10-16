#!/usr/bin/env python3
"""Validate the structured storefront result and its declared document evidence."""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any


SUCCESS_RESULT_TYPES = frozenset({
    "INFORMATION_PROVIDED", "ACTION_EXECUTED", "CONFIRMATION_REQUIRED",
    "CLARIFICATION_REQUIRED", "OUT_OF_SCOPE",
})
DOCUMENT_EVIDENCE_PATHS = (
    "sources", "documents", "ragResponse.documents", "actions[].documents",
    "actions[].ragResponse.documents", "actions[].actionResult.data.documents",
)


def _document_count(value: Any) -> int:
    if not isinstance(value, list):
        return 0
    return sum(
        1 for document in value
        if isinstance(document, dict) and any(
            isinstance(document.get(key), str) and bool(document[key].strip())
            for key in ("id", "documentId", "url", "storefrontUrl")
        )
    )


def _values(payload: Any, path: str) -> list[Any]:
    values = [payload]
    for segment in path.split("."):
        array = segment.endswith("[]")
        field = segment[:-2] if array else segment
        children = [value.get(field) for value in values if isinstance(value, dict)]
        values = [item for child in children if isinstance(child, list) for item in child] if array else children
    return values


def _failed_result(value: Any) -> bool:
    if not isinstance(value, dict):
        return False
    return value.get("success") is False or value.get("type") == "ERROR" or value.get("errorCode") not in (None, "")


def validate_response(
    payload: Any,
    grounding_required: bool = False,
    grounding_paths: list[str] | tuple[str, ...] | None = None,
) -> dict[str, Any]:
    body = payload if isinstance(payload, dict) else {}
    paths = DOCUMENT_EVIDENCE_PATHS if grounding_paths is None else grounding_paths
    valid_paths = isinstance(paths, (list, tuple)) and bool(paths) and all(
        isinstance(path, str) and path in DOCUMENT_EVIDENCE_PATHS for path in paths
    )
    evidence = [
        {"path": path, "documents": sum(_document_count(value) for value in _values(body, path))}
        for path in dict.fromkeys(paths)
    ] if valid_paths else []
    result = body.get("result")
    nested = [result, result.get("sanitizedPayload") if isinstance(result, dict) else None]
    for action in body.get("actions", []) if isinstance(body.get("actions"), list) else []:
        if isinstance(action, dict):
            account_auth = action.get("customerAccountAuth")
            auth_challenge = body.get("type") == "INFORMATION_PROVIDED" and (
                action.get("errorCode") == "CUSTOMER_ACCOUNT_AUTH_REQUIRED"
                and (action.get("customerAccountAuthRequired") is True
                     or isinstance(account_auth, dict) and account_auth.get("required") is True)
            )
            # The Bridge's typed sign-in challenge is a normalized policy result, not an executed action.
            nested.extend([
                {"success": action.get("success"), "type": action.get("type")} if auth_challenge else action,
                action.get("actionResult"),
            ])
    checks = {
        "structured_success": body.get("success") is True,
        "structured_result_type": isinstance(body.get("type"), str) and body["type"] in SUCCESS_RESULT_TYPES,
        "no_structured_error": not _failed_result(body) and not any(_failed_result(value) for value in nested),
        "no_error_code": body.get("errorCode") in (None, ""),
        "no_fallback_reason": body.get("fallbackReason") in (None, ""),
        "grounding_requirement_valid": isinstance(grounding_required, bool),
        "grounding_contract_valid": valid_paths,
        "required_grounding_present": grounding_required is False or any(item["documents"] > 0 for item in evidence),
    }
    return {"checks": checks, "groundingEvidence": evidence, "passed": all(checks.values())}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--grounding-required", action="store_true")
    args = parser.parse_args()
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, UnicodeDecodeError):
        print(json.dumps({"passed": False, "error": "INVALID_JSON_RESPONSE"}))
        return 1
    report = validate_response(payload, args.grounding_required)
    print(json.dumps(report, sort_keys=True))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())

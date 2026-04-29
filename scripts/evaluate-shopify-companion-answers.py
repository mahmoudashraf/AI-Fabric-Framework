#!/usr/bin/env python3
"""Evaluate Shopify Companion storefront answers against a deterministic query pack."""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any


def read_json(path: pathlib.Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: pathlib.Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def post_json(url: str, payload: dict[str, Any], shopper_session_id: str, timeout: int) -> tuple[int, dict[str, Any] | str]:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-AI-FABRIC-SHOPPER-SESSION-ID": shopper_session_id,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            try:
                return response.status, json.loads(body)
            except json.JSONDecodeError:
                return response.status, body
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        try:
            return exc.code, json.loads(body)
        except json.JSONDecodeError:
            return exc.code, body


def nested_text(payload: Any, dotted_path: str) -> str:
    current = payload
    for part in dotted_path.split("."):
        if not isinstance(current, dict):
            return ""
        current = current.get(part)
    return current.strip() if isinstance(current, str) else ""


def extract_answer(payload: Any) -> str:
    for path in (
        "result.sanitizedPayload.safeSummary",
        "result.sanitizedPayload.message",
        "result.sanitizedPayload.answer",
        "result.message",
        "message",
        "answer",
    ):
        value = nested_text(payload, path)
        if value:
            return value
    return ""


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip().lower()


def contains_phrase(text: str, phrase: str) -> bool:
    return normalize(phrase) in normalize(text)


def conversation_id(payload: Any) -> str:
    if isinstance(payload, dict):
        value = payload.get("conversationId")
        return value.strip() if isinstance(value, str) else ""
    return ""


def evaluate_query(
    bridge_base_url: str,
    shop_domain: str,
    query: dict[str, Any],
    global_forbidden_claims: list[str],
    global_forbidden_internal_terms: list[str],
    timeout: int,
) -> dict[str, Any]:
    query_id = str(query["queryId"])
    request_payload = {
        "query": query["query"],
        "storefrontContext": query.get("storefrontContext") or {},
    }
    url = (
        bridge_base_url.rstrip("/")
        + "/api/storefront/shops/"
        + urllib.parse.quote(shop_domain, safe="")
        + "/chat/query"
    )
    started = time.time()
    status, response = post_json(url, request_payload, f"first-product-audit-{query_id}", timeout)
    duration_ms = int((time.time() - started) * 1000)
    answer = extract_answer(response)
    forbidden = [
        phrase
        for phrase in [*global_forbidden_claims, *global_forbidden_internal_terms, *query.get("forbiddenClaims", [])]
        if phrase and contains_phrase(answer, phrase)
    ]
    required = [str(value) for value in query.get("requiredConcepts", []) if str(value).strip()]
    missing_required = [value for value in required if not contains_phrase(answer, value)]
    action_execution_terms = [
        "I cancelled",
        "I've cancelled",
        "I refunded",
        "I've refunded",
        "I changed your order",
        "I updated your order",
        "refund has been processed",
    ]
    unsafe_action_execution = [
        phrase for phrase in action_execution_terms if contains_phrase(answer, phrase)
    ]
    checks = {
        "http_status_ok": status == 200,
        "conversation_id_present": bool(conversation_id(response)),
        "answer_non_empty": bool(answer),
        "forbidden_claims_absent": not forbidden,
        "required_concepts_present": not missing_required,
        "no_action_execution_language": not unsafe_action_execution,
    }
    passed = all(checks.values())
    return {
        "queryId": query_id,
        "surface": query.get("surface"),
        "tierProfile": query.get("tierProfile"),
        "expectedBehavior": query.get("expectedBehavior"),
        "status": status,
        "durationMs": duration_ms,
        "conversationIdPresent": bool(conversation_id(response)),
        "answerLength": len(answer),
        "answerPreview": answer[:500],
        "checks": checks,
        "forbiddenHits": forbidden,
        "missingRequiredConcepts": missing_required,
        "unsafeActionExecutionHits": unsafe_action_execution,
        "failureCategory": failure_category(checks, status, forbidden, missing_required, unsafe_action_execution),
        "passed": passed,
    }


def failure_category(
    checks: dict[str, bool],
    status: int,
    forbidden: list[str],
    missing_required: list[str],
    unsafe_action_execution: list[str],
) -> str | None:
    if all(checks.values()):
        return None
    if status != 200:
        return "backend_issue"
    if forbidden:
        return "forbidden_claim_or_internal_language"
    if unsafe_action_execution:
        return "entitlement_or_governed_action_issue"
    if missing_required:
        return "grounding_or_helpfulness_issue"
    return "answer_quality_issue"


def write_audit_markdown(path: pathlib.Path, results: list[dict[str, Any]], decision: str) -> None:
    passed = sum(1 for result in results if result["passed"])
    lines = [
        "# Shopify Companion Answer Quality Audit",
        "",
        f"Decision: `{decision}`",
        "",
        f"Queries passed: {passed}/{len(results)}",
        "",
        "| Query | Surface | Tier | Result | Failure category |",
        "|---|---|---|---|---|",
    ]
    for result in results:
        lines.append(
            "| {queryId} | {surface} | {tierProfile} | {status} | {failureCategory} |".format(
                queryId=result["queryId"],
                surface=result.get("surface") or "",
                tierProfile=result.get("tierProfile") or "",
                status="PASS" if result["passed"] else "FAIL",
                failureCategory=result.get("failureCategory") or "",
            )
        )
    lines.extend(["", "## Failures", ""])
    failures = [result for result in results if not result["passed"]]
    if not failures:
        lines.append("No deterministic answer-quality failures were detected.")
    for result in failures:
        lines.extend([
            f"### {result['queryId']}",
            "",
            f"- Failure category: `{result.get('failureCategory')}`",
            f"- HTTP status: `{result['status']}`",
            f"- Forbidden hits: `{', '.join(result['forbiddenHits']) or 'none'}`",
            f"- Missing required concepts: `{', '.join(result['missingRequiredConcepts']) or 'none'}`",
            f"- Answer preview: {result['answerPreview'] or '(empty)'}",
            "",
        ])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bridge-base-url", required=True)
    parser.add_argument("--shop-domain", required=True)
    parser.add_argument("--query-pack", required=True, type=pathlib.Path)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    parser.add_argument("--timeout", type=int, default=45)
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    pack = read_json(args.query_pack)
    target_pack_path = args.out / "answer-quality-query-pack.json"
    target_pack_path.write_text(json.dumps(pack, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    results = [
        evaluate_query(
            args.bridge_base_url,
            args.shop_domain,
            query,
            [str(value) for value in pack.get("globalForbiddenClaims", [])],
            [str(value) for value in pack.get("globalForbiddenInternalTerms", [])],
            args.timeout,
        )
        for query in pack.get("queries", [])
    ]
    decision = "PASS" if results and all(result["passed"] for result in results) else "FAIL"
    write_json(
        args.out / "answer-quality-results.json",
        {
            "schemaVersion": 1,
            "productRef": pack.get("productRef", "shopify-companion"),
            "targetStore": args.shop_domain,
            "decision": decision,
            "passedQueries": sum(1 for result in results if result["passed"]),
            "totalQueries": len(results),
            "results": results,
        },
    )
    write_audit_markdown(args.out / "answer-quality-audit.md", results, decision)
    for result in results:
        print(
            "QUERY_RESULT "
            + json.dumps(
                {
                    "queryId": result["queryId"],
                    "surface": result.get("surface"),
                    "tierProfile": result.get("tierProfile"),
                    "expectedBehavior": result.get("expectedBehavior"),
                    "passed": result["passed"],
                    "httpStatus": result["status"],
                    "failureCategory": result.get("failureCategory"),
                    "answerLength": result["answerLength"],
                    "answerPreview": result["answerPreview"],
                    "checks": result["checks"],
                    "forbiddenHits": result["forbiddenHits"],
                    "missingRequiredConcepts": result["missingRequiredConcepts"],
                    "unsafeActionExecutionHits": result["unsafeActionExecutionHits"],
                },
                sort_keys=True,
            )
        )
    print(f"Answer quality decision: {decision} ({sum(1 for result in results if result['passed'])}/{len(results)} passed)")
    return 0 if decision == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())

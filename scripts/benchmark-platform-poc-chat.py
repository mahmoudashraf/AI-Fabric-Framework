#!/usr/bin/env python3
"""
Benchmark authenticated platform POC chat latency for one or more deployments.

This script uses the same `/api/deployments/{id}/poc-chat/query` path that the
Platform POC UI uses, captures the emitted `traceSummary`, and prints either a
human-readable table or a JSON payload suitable for deeper analysis.
"""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import math
import os
import statistics
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


DEFAULT_QUERIES: list[tuple[str, str]] = [
    ("hello", "hello"),
    ("alienware", "tell me about Alienware m18 R2"),
    ("gaming", "analyze and summarize high performance laptops for gaming"),
    ("policy", "summarize return policy"),
]


@dataclass(frozen=True)
class DeploymentTarget:
    deployment_id: str
    label: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Benchmark platform POC chat latency using live traceSummary metrics."
    )
    parser.add_argument(
        "--platform-base-url",
        default=os.environ.get("PLATFORM_BASE_URL") or os.environ.get("PLATFORM_PUBLIC_BASE_URL"),
        help="Platform base URL, for example https://ai-fabric-framework-production-324f.up.railway.app",
    )
    parser.add_argument(
        "--email",
        default=os.environ.get("PLATFORM_LOGIN_EMAIL"),
        help="Platform login email. Required unless a valid --cookie-jar is already available.",
    )
    parser.add_argument(
        "--password",
        default=os.environ.get("PLATFORM_LOGIN_PASSWORD"),
        help="Platform login password. Required unless a valid --cookie-jar is already available.",
    )
    parser.add_argument(
        "--cookie-jar",
        default=os.environ.get("PLATFORM_COOKIE_JAR"),
        help="Optional cookie jar path. If omitted, a temporary jar is created.",
    )
    parser.add_argument(
        "--deployment",
        action="append",
        required=True,
        help="Deployment target in the form dep-xxxxx or dep-xxxxx:Label. Repeat for multiple deployments.",
    )
    parser.add_argument(
        "--auth-path",
        default="PLATFORM_PRIVATE",
        choices=["PLATFORM_PRIVATE", "PUBLIC_AUTHENTICATED", "PUBLIC_ANONYMOUS"],
        help="POC auth path to use for the benchmark.",
    )
    parser.add_argument(
        "--runs",
        type=int,
        default=2,
        help="Number of runs per query per deployment. Use 5-10 for stronger confidence.",
    )
    parser.add_argument(
        "--query",
        action="append",
        help="Custom query in the form id:::text. Repeat to override the default query set.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=180,
        help="Per-request HTTP timeout in seconds.",
    )
    parser.add_argument(
        "--pause-ms",
        type=int,
        default=0,
        help="Optional pause between requests in milliseconds.",
    )
    parser.add_argument(
        "--format",
        default="table",
        choices=["table", "json"],
        help="Output format.",
    )
    return parser.parse_args()


def normalize_base_url(raw: str | None) -> str:
    if not raw:
        raise SystemExit("Missing --platform-base-url (or PLATFORM_BASE_URL).")
    return raw[:-1] if raw.endswith("/") else raw


def parse_deployments(raw_values: list[str]) -> list[DeploymentTarget]:
    targets: list[DeploymentTarget] = []
    for raw in raw_values:
        deployment_id, label = (raw.split(":", 1) + [""])[:2]
        deployment_id = deployment_id.strip()
        label = label.strip() or deployment_id
        if not deployment_id:
            raise SystemExit(f"Invalid --deployment value: {raw!r}")
        targets.append(DeploymentTarget(deployment_id=deployment_id, label=label))
    return targets


def parse_queries(raw_values: list[str] | None) -> list[tuple[str, str]]:
    if not raw_values:
        return DEFAULT_QUERIES
    queries: list[tuple[str, str]] = []
    for raw in raw_values:
        if ":::" not in raw:
            raise SystemExit(f"Invalid --query value: {raw!r}. Expected id:::text")
        query_id, query_text = raw.split(":::", 1)
        query_id = query_id.strip()
        query_text = query_text.strip()
        if not query_id or not query_text:
            raise SystemExit(f"Invalid --query value: {raw!r}. Expected id:::text")
        queries.append((query_id, query_text))
    return queries


def ensure_cookie_jar(path: str | None) -> str:
    if path:
        return path
    fd, temp_path = tempfile.mkstemp(prefix="platform-poc-bench-", suffix=".cookies.txt")
    os.close(fd)
    return temp_path


def build_opener(cookie_jar_path: str) -> tuple[urllib.request.OpenerDirector, http.cookiejar.MozillaCookieJar]:
    jar = http.cookiejar.MozillaCookieJar(cookie_jar_path)
    if os.path.exists(cookie_jar_path):
        try:
            jar.load(ignore_discard=True, ignore_expires=True)
        except Exception:
            pass
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    return opener, jar


def login(base_url: str, opener: urllib.request.OpenerDirector, jar: http.cookiejar.MozillaCookieJar, email: str | None, password: str | None, timeout_seconds: int) -> None:
    if not email or not password:
        return
    payload = json.dumps({"email": email, "password": password}).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url}/api/platform/auth/login",
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with opener.open(request, timeout=timeout_seconds) as response:
        if response.status != 200:
            raise SystemExit(f"Platform login failed with HTTP {response.status}")
        response.read()
    jar.save(ignore_discard=True, ignore_expires=True)


def fetch_json(opener: urllib.request.OpenerDirector, request: urllib.request.Request, timeout_seconds: int) -> Any:
    with opener.open(request, timeout=timeout_seconds) as response:
        return json.load(response)


def nearest_rank_percentile(values: list[float], percentile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile * len(ordered)))
    return ordered[rank - 1]


def average(values: list[int | float]) -> float | None:
    if not values:
        return None
    return round(statistics.mean(values), 1)


def median(values: list[int | float]) -> float | None:
    if not values:
        return None
    return round(statistics.median(values), 1)


def summarize_numeric(run_records: list[dict[str, Any]], key: str) -> dict[str, float | None]:
    values = [value for record in run_records if isinstance((value := record.get(key)), (int, float))]
    return {
        "avg": average(values),
        "median": median(values),
        "p95": nearest_rank_percentile(values, 0.95),
        "min": min(values) if values else None,
        "max": max(values) if values else None,
    }


def benchmark_deployment(
    base_url: str,
    opener: urllib.request.OpenerDirector,
    target: DeploymentTarget,
    queries: list[tuple[str, str]],
    auth_path: str,
    runs: int,
    timeout_seconds: int,
    pause_ms: int,
) -> dict[str, Any]:
    poc_request = urllib.request.Request(
        f"{base_url}/api/deployments/{urllib.parse.quote(target.deployment_id)}/poc",
        headers={"Accept": "application/json"},
        method="GET",
    )
    poc_workspace = fetch_json(opener, poc_request, timeout_seconds)
    run_records: list[dict[str, Any]] = []

    for run_index in range(1, runs + 1):
        for query_id, query_text in queries:
            payload = json.dumps({"query": query_text, "authPath": auth_path}).encode("utf-8")
            request = urllib.request.Request(
                f"{base_url}/api/deployments/{urllib.parse.quote(target.deployment_id)}/poc-chat/query",
                data=payload,
                headers={"Content-Type": "application/json", "Accept": "application/json"},
                method="POST",
            )
            started = time.perf_counter()
            try:
                response = fetch_json(opener, request, timeout_seconds)
                wall_ms = round((time.perf_counter() - started) * 1000)
                trace = response.get("traceSummary") or {}
                result = response.get("result") or {}
                run_records.append(
                    {
                        "run": run_index,
                        "queryId": query_id,
                        "query": query_text,
                        "wallMs": wall_ms,
                        "resultType": trace.get("resultType") or result.get("type"),
                        "success": trace.get("success", response.get("success")),
                        "message": trace.get("message") or result.get("message"),
                        "documentCount": trace.get("documentCount"),
                        "runtimeRequestDurationMs": trace.get("runtimeRequestDurationMs"),
                        "pipelineDurationMs": trace.get("pipelineDurationMs"),
                        "extractionProcessingTimeMs": trace.get("extractionProcessingTimeMs"),
                        "extractionProviderProcessingTimeMs": trace.get("extractionProviderProcessingTimeMs"),
                        "retrievalProcessingTimeMs": trace.get("retrievalProcessingTimeMs"),
                        "embeddingProcessingTimeMs": trace.get("embeddingProcessingTimeMs"),
                        "embeddingProviderProcessingTimeMs": trace.get("embeddingProviderProcessingTimeMs"),
                        "responseGenerationProcessingTimeMs": trace.get("responseGenerationProcessingTimeMs"),
                        "responseGenerationProviderProcessingTimeMs": trace.get("responseGenerationProviderProcessingTimeMs"),
                        "responseGenerationPath": trace.get("responseGenerationPath"),
                        "searchProcessingTimeMs": trace.get("searchProcessingTimeMs"),
                        "vectorSpaces": trace.get("vectorSpaces") or [],
                        "candidateVectorSpaces": trace.get("candidateVectorSpaces") or [],
                        "stepDurationsMs": trace.get("stepDurationsMs") or {},
                    }
                )
            except urllib.error.HTTPError as exc:
                body = exc.read().decode("utf-8", errors="replace")
                wall_ms = round((time.perf_counter() - started) * 1000)
                run_records.append(
                    {
                        "run": run_index,
                        "queryId": query_id,
                        "query": query_text,
                        "wallMs": wall_ms,
                        "resultType": "REQUEST_ERROR",
                        "success": False,
                        "message": f"HTTP {exc.code}: {body[:400]}",
                    }
                )
            if pause_ms > 0:
                time.sleep(pause_ms / 1000.0)

    query_summaries: dict[str, Any] = {}
    for query_id, _query_text in queries:
        subset = [record for record in run_records if record["queryId"] == query_id]
        query_summaries[query_id] = {
            "resultTypes": sorted({record["resultType"] for record in subset}),
            "responseGenerationPaths": sorted(
                {record["responseGenerationPath"] for record in subset if record.get("responseGenerationPath")}
            ),
            "wallMs": summarize_numeric(subset, "wallMs"),
            "runtimeRequestDurationMs": summarize_numeric(subset, "runtimeRequestDurationMs"),
            "pipelineDurationMs": summarize_numeric(subset, "pipelineDurationMs"),
            "extractionProcessingTimeMs": summarize_numeric(subset, "extractionProcessingTimeMs"),
            "extractionProviderProcessingTimeMs": summarize_numeric(subset, "extractionProviderProcessingTimeMs"),
            "retrievalProcessingTimeMs": summarize_numeric(subset, "retrievalProcessingTimeMs"),
            "embeddingProcessingTimeMs": summarize_numeric(subset, "embeddingProcessingTimeMs"),
            "embeddingProviderProcessingTimeMs": summarize_numeric(subset, "embeddingProviderProcessingTimeMs"),
            "responseGenerationProcessingTimeMs": summarize_numeric(subset, "responseGenerationProcessingTimeMs"),
            "responseGenerationProviderProcessingTimeMs": summarize_numeric(subset, "responseGenerationProviderProcessingTimeMs"),
            "searchProcessingTimeMs": summarize_numeric(subset, "searchProcessingTimeMs"),
            "documentCount": summarize_numeric(subset, "documentCount"),
            "runs": subset,
        }

    return {
        "deploymentId": target.deployment_id,
        "label": target.label,
        "displayName": (poc_workspace.get("deployment") or {}).get("displayName"),
        "vectorDb": (poc_workspace.get("indexing") or {}).get("vectorDb"),
        "totalVectors": (poc_workspace.get("indexing") or {}).get("totalVectors"),
        "countsByEntityType": (poc_workspace.get("indexing") or {}).get("countsByEntityType") or {},
        "authPath": auth_path,
        "runsPerQuery": runs,
        "queries": query_summaries,
    }


def fmt_number(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    if isinstance(value, float):
        return f"{value:.1f}"
    return str(value)


def print_table(report: list[dict[str, Any]], query_order: list[tuple[str, str]]) -> None:
    for deployment in report:
        header = f"{deployment['label']} [{deployment['deploymentId']}]"
        print(header)
        print(f"  vectorDb={deployment['vectorDb']} totalVectors={deployment['totalVectors']} counts={deployment['countsByEntityType']}")
        for query_id, query_text in query_order:
            summary = deployment["queries"][query_id]
            print(f"  {query_id}: {query_text}")
            print(
                "    "
                f"resultTypes={summary['resultTypes']} "
                f"paths={summary['responseGenerationPaths'] or ['-']} "
                f"docs.avg={fmt_number(summary['documentCount']['avg'])}"
            )
            print(
                "    "
                f"wall.avg={fmt_number(summary['wallMs']['avg'])}ms "
                f"runtime.avg={fmt_number(summary['runtimeRequestDurationMs']['avg'])}ms "
                f"pipeline.avg={fmt_number(summary['pipelineDurationMs']['avg'])}ms"
            )
            print(
                "    "
                f"extract.avg={fmt_number(summary['extractionProcessingTimeMs']['avg'])}ms "
                f"extract.provider.avg={fmt_number(summary['extractionProviderProcessingTimeMs']['avg'])}ms "
                f"retrieval.avg={fmt_number(summary['retrievalProcessingTimeMs']['avg'])}ms"
            )
            print(
                "    "
                f"embedding.avg={fmt_number(summary['embeddingProcessingTimeMs']['avg'])}ms "
                f"embedding.provider.avg={fmt_number(summary['embeddingProviderProcessingTimeMs']['avg'])}ms "
                f"search.avg={fmt_number(summary['searchProcessingTimeMs']['avg'])}ms"
            )
            print(
                "    "
                f"generation.avg={fmt_number(summary['responseGenerationProcessingTimeMs']['avg'])}ms "
                f"generation.provider.avg={fmt_number(summary['responseGenerationProviderProcessingTimeMs']['avg'])}ms"
            )
        print()


def main() -> None:
    args = parse_args()
    base_url = normalize_base_url(args.platform_base_url)
    deployments = parse_deployments(args.deployment)
    queries = parse_queries(args.query)
    cookie_jar_path = ensure_cookie_jar(args.cookie_jar)
    if not ((args.email and args.password) or (os.path.exists(cookie_jar_path) and os.path.getsize(cookie_jar_path) > 0)):
        raise SystemExit(
            "Authentication required: provide --email/--password (or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD), "
            "or point --cookie-jar at an existing authenticated session jar."
        )
    opener, jar = build_opener(cookie_jar_path)
    login(base_url, opener, jar, args.email, args.password, args.timeout_seconds)

    report = [
        benchmark_deployment(
            base_url=base_url,
            opener=opener,
            target=target,
            queries=queries,
            auth_path=args.auth_path,
            runs=args.runs,
            timeout_seconds=args.timeout_seconds,
            pause_ms=args.pause_ms,
        )
        for target in deployments
    ]

    output = {
        "platformBaseUrl": base_url,
        "authPath": args.auth_path,
        "runsPerQuery": args.runs,
        "queries": [{"id": query_id, "text": query_text} for query_id, query_text in queries],
        "deployments": report,
    }
    if args.format == "json":
        json.dump(output, sys.stdout, indent=2)
        print()
        return
    print_table(report, queries)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Provision and verify the dealership public document-knowledge source set."""

from __future__ import annotations

import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


DOCUMENTS = (
    (
        "warranty-and-aftercare-policy.txt",
        "warranty-aftercare",
        "How long is the Northfield standard used vehicle warranty?",
        ("90 days", "3,000 miles"),
    ),
    (
        "vehicle-reservation-and-deposit-policy.txt",
        "reservation-deposit",
        "What is the Northfield vehicle reservation amount and how long is the hold?",
        ("GBP 99", "48-hour"),
    ),
    (
        "test-drive-requirements-policy.txt",
        "test-drive",
        "What age and driving licence history are required for a Northfield test drive?",
        ("21", "12 months"),
    ),
    (
        "collection-delivery-and-handover-operations.json",
        "collection-delivery",
        "What does local vehicle delivery cost and how far does it cover?",
        ("25 miles", "GBP 49"),
    ),
    (
        "customer-care-and-complaints-policy.txt",
        "customer-care-complaints",
        "When will Northfield acknowledge and respond to a complaint?",
        ("two working days", "10 working days"),
    ),
    (
        "showroom-accessibility-and-opening-hours.txt",
        "showroom-accessibility",
        "What are Northfield Riverside weekday opening hours and accessibility facilities?",
        ("09:00", "step-free"),
    ),
)


class VerificationFailure(RuntimeError):
    pass


class PlatformClient:
    def __init__(self, base_url: str, api_key_header: str, api_key: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key_header = api_key_header
        self.api_key = api_key

    def request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        expected: tuple[int, ...] = (200,),
        idempotency_key: str | None = None,
    ) -> dict:
        payload = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Accept": "application/json", self.api_key_header: self.api_key}
        if payload is not None:
            headers["Content-Type"] = "application/json"
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=payload,
            method=method,
            headers=headers,
        )
        last_error: Exception | None = None
        for attempt in range(1, 5):
            try:
                with urllib.request.urlopen(request, timeout=90) as response:
                    status = response.status
                    raw = response.read().decode("utf-8")
                parsed = json.loads(raw) if raw else {}
                if status not in expected:
                    raise VerificationFailure(f"{method} {path} returned HTTP {status}")
                return parsed
            except urllib.error.HTTPError as error:
                detail = error.read().decode("utf-8", errors="replace")[:500]
                if error.code in {502, 503, 504} and attempt < 4:
                    last_error = error
                    time.sleep(3)
                    continue
                raise VerificationFailure(
                    f"{method} {path} returned HTTP {error.code}: {detail}"
                ) from error
            except (urllib.error.URLError, TimeoutError) as error:
                last_error = error
                if attempt < 4:
                    time.sleep(3)
                    continue
                break
        raise VerificationFailure(f"{method} {path} failed: {last_error}")


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise VerificationFailure(f"Missing required environment value: {name}")
    return value


def secret(name: str) -> str:
    file_path = os.environ.get(f"{name}_FILE", "").strip()
    if file_path:
        return pathlib.Path(file_path).read_text(encoding="utf-8").strip()
    return required(name)


def source_path(api_base: str, source_id: str, suffix: str = "") -> str:
    encoded = urllib.parse.quote(source_id, safe="")
    return f"{api_base}/sources/{encoded}{suffix}"


def discover_all(client: PlatformClient, api_base: str, dataset_id: str) -> dict[str, dict]:
    sources: dict[str, dict] = {}
    cursor: str | None = None
    for _ in range(20):
        body: dict[str, object] = {"datasetId": dataset_id, "limit": 200}
        if cursor:
            body["cursor"] = cursor
        page = client.request("POST", f"{api_base}/discover", body)
        for item in page.get("sources", []):
            reference = item.get("objectReference")
            if reference:
                sources[reference] = item
        cursor = page.get("nextCursor")
        if not cursor:
            return sources
    raise VerificationFailure("Document discovery exceeded 20 pages")


def wait_active(client: PlatformClient, api_base: str, source_id: str) -> dict:
    for attempt in range(60):
        client.request(
            "POST",
            source_path(api_base, source_id, "/reconcile"),
            {},
            idempotency_key=f"dealership-doc-reconcile-{source_id}-{attempt}-{uuid.uuid4()}",
        )
        detail = client.request("GET", source_path(api_base, source_id))
        source = detail.get("source", {})
        if source.get("status") == "ACTIVE" and source.get("activeVersion") is not None:
            active_version = source["activeVersion"]
            active_manifest = next(
                (
                    manifest
                    for manifest in detail.get("manifests", [])
                    if manifest.get("sourceVersion") == active_version
                    and manifest.get("state") == "ACTIVE"
                ),
                None,
            )
            if active_manifest:
                index_work = [
                    work
                    for work in active_manifest.get("work", [])
                    if work.get("operation") == "INDEX"
                ]
                if index_work and all(work.get("terminal") and work.get("successful") for work in index_work):
                    return detail
        if source.get("status") in {"FAILED", "DELETED"}:
            raise VerificationFailure(
                f"Source {source_id} reached unexpected state {source.get('status')}: "
                f"{source.get('failureCode') or source.get('failureMessage')}"
            )
        time.sleep(2)
    raise VerificationFailure(f"Source {source_id} did not become active")


def verify_retrieval(
    client: PlatformClient,
    api_base: str,
    source_id: str,
    query: str,
    expected_terms: tuple[str, ...],
) -> int:
    for _ in range(15):
        proof = client.request("POST", f"{api_base}/retrieval-proof", {"query": query, "limit": 10})
        evidence = [
            item for item in proof.get("evidence", []) if item.get("sourceId") == source_id
        ]
        content = "\n".join(str(item.get("content", "")) for item in evidence).lower()
        if evidence and all(term.lower() in content for term in expected_terms):
            return len(evidence)
        time.sleep(2)
    raise VerificationFailure(
        f"Retrieval proof for {source_id} did not contain: {', '.join(expected_terms)}"
    )


def main() -> int:
    platform_base_url = required("PLATFORM_BASE_URL")
    api_key = secret("PLATFORM_API_KEY")
    api_key_header = os.environ.get("PLATFORM_API_KEY_HEADER", "X-PLATFORM-API-KEY").strip()
    deployment_id = required("DOCUMENT_DEPLOYMENT_ID")
    dataset_id = os.environ.get("DOCUMENT_DATASET_ID", "document-knowledge").strip()
    prefix = os.environ.get("DOCUMENT_OBJECT_PREFIX", "").strip().strip("/")
    report_path = os.environ.get("DOCUMENT_REPORT_PATH", "").strip()

    client = PlatformClient(platform_base_url, api_key_header, api_key)
    encoded_deployment = urllib.parse.quote(deployment_id, safe="")
    api_base = f"/api/deployments/{encoded_deployment}/document-knowledge"

    workspace = client.request("GET", f"/api/deployments/{encoded_deployment}/workspace")
    if not workspace.get("documentKnowledgeConfigured") or not workspace.get("documentKnowledgeLive"):
        raise VerificationFailure("Document Knowledge is not configured and live for the deployment")
    connector = client.request("GET", f"{api_base}/connector")
    if not connector.get("ready"):
        raise VerificationFailure(f"Document connector is not ready: {connector.get('errorCode')}")

    discovered = discover_all(client, api_base, dataset_id)
    expected_references = {
        f"{prefix}/{filename}" if prefix else filename for filename, *_ in DOCUMENTS
    }
    missing = sorted(expected_references.difference(discovered))
    if missing:
        raise VerificationFailure(f"Expected source files were not discovered: {', '.join(missing)}")

    results: list[dict] = []
    for filename, category, query, expected_terms in DOCUMENTS:
        object_reference = f"{prefix}/{filename}" if prefix else filename
        print(f"VERIFY: {filename}", flush=True)
        source = client.request(
            "POST",
            f"{api_base}/sources",
            {
                "datasetId": dataset_id,
                "objectReference": object_reference,
                "visibility": "tenant",
                "metadata": {
                    "sourceCategory": category,
                    "publicationStatus": "PUBLIC_APPROVED",
                },
            },
            expected=(200, 201),
            idempotency_key=f"dealership-doc-register-{category}-{uuid.uuid4()}",
        )
        source_id = source.get("sourceId")
        if not source_id:
            raise VerificationFailure(f"Registration returned no sourceId for {filename}")

        preview = client.request("GET", source_path(api_base, source_id, "/preview"))
        if preview.get("chunkCount", 0) < 1 or preview.get("documentCount", 0) < 1:
            raise VerificationFailure(f"Preview returned no content for {filename}")

        client.request(
            "POST",
            source_path(api_base, source_id, "/index"),
            {},
            expected=(200, 202),
            idempotency_key=f"dealership-doc-index-{category}-{uuid.uuid4()}",
        )
        detail = wait_active(client, api_base, source_id)
        evidence_count = verify_retrieval(client, api_base, source_id, query, expected_terms)
        results.append(
            {
                "filename": filename,
                "sourceId": source_id,
                "sourceCategory": category,
                "activeVersion": detail["source"]["activeVersion"],
                "evidenceCount": evidence_count,
                "query": query,
            }
        )
        print(
            f"PASS: {filename} active v{detail['source']['activeVersion']} with {evidence_count} matching evidence result(s)",
            flush=True,
        )

    report = {
        "schemaVersion": "loomai-dealership-document-knowledge-verification-v1",
        "status": "PASS",
        "deploymentId": deployment_id,
        "datasetId": dataset_id,
        "connectorType": connector.get("connectorType"),
        "verifiedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "sources": results,
    }
    if report_path:
        path = pathlib.Path(report_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: verified {len(results)} approved dealership document sources")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except VerificationFailure as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)

#!/usr/bin/env python3
"""Verify the hosted synthetic Auto Trader public-document contract profile.

This is deliberately narrower than an Auto Trader sandbox or production gate.
It proves that one immutable LoomAI deployment can consume the reviewed public
wire contract through its deployment-local connector, reconcile signed change
signals by fetching current stock, and expose the result to anonymous chat.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable


EVIDENCE_SCHEMA = "loomai-autotrader-public-contract-hosted-canary-v1"
CLAIM = "PUBLIC_DOCUMENT_CONTRACT_CANARY_VERIFIED"
SOURCE_ID = "autotrader-dealership-stock-source"
WEBHOOK_SOURCE_ID = "autotrader-stock-events"
ENTITY_TYPE = "dealer-vehicle"


class CanaryFailure(RuntimeError):
    pass


@dataclass(frozen=True)
class Response:
    status: int
    headers: dict[str, str]
    body: Any


class HttpClient:
    def request(
        self,
        method: str,
        url: str,
        *,
        headers: dict[str, str] | None = None,
        json_body: Any | None = None,
        form_body: dict[str, str] | None = None,
        timeout: float = 30,
    ) -> Response:
        request_headers = dict(headers or {})
        data: bytes | None = None
        if json_body is not None:
            data = json.dumps(json_body, separators=(",", ":")).encode("utf-8")
            request_headers.setdefault("Content-Type", "application/json")
        elif form_body is not None:
            data = urllib.parse.urlencode(form_body).encode("utf-8")
            request_headers.setdefault("Content-Type", "application/x-www-form-urlencoded")
        request = urllib.request.Request(
            url,
            data=data,
            headers=request_headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                raw = response.read()
                return Response(
                    response.status,
                    {key.lower(): value for key, value in response.headers.items()},
                    self._decode(raw),
                )
        except urllib.error.HTTPError as error:
            raw = error.read()
            return Response(
                error.code,
                {key.lower(): value for key, value in error.headers.items()},
                self._decode(raw),
            )

    @staticmethod
    def _decode(raw: bytes) -> Any:
        if not raw:
            return None
        text = raw.decode("utf-8", errors="replace")
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return text[:1000]


class HostedCanary:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.http = HttpClient()
        self.checks: list[dict[str, Any]] = []
        self.started_at = now_iso()
        self.release: dict[str, Any] = {}
        self.fixture: dict[str, Any] = {}
        self.baseline_count: int | None = None
        self.added_count: int | None = None
        self.restored_count: int | None = None
        self.canary_stock_id = f"CANARY-{int(time.time())}"
        self.canary_event_ids: list[str] = []
        self.created_stock = False

    def run(self) -> dict[str, Any]:
        try:
            self.verify_health()
            token = self.verify_provider_contract()
            self.verify_platform_release()
            self.verify_integration_contract()
            self.verify_current_record_fetch(token)
            if not self.args.confirm_mutation:
                raise CanaryFailure("--confirm-mutation is required for the hosted mutation canary")
            self.verify_targeted_reconciliation()
            status = "PASSED"
            error = None
        except Exception as exception:
            status = "FAILED"
            error = str(exception)
        finally:
            cleanup_error = self.cleanup_canary_stock()
            if cleanup_error:
                status = "FAILED"
                error = f"{error}; cleanup: {cleanup_error}" if error else f"cleanup: {cleanup_error}"

        evidence = {
            "schemaVersion": EVIDENCE_SCHEMA,
            "status": status,
            "claim": CLAIM if status == "PASSED" else None,
            "startedAt": self.started_at,
            "completedAt": now_iso(),
            "scope": {
                "deploymentId": self.args.deployment_id,
                "runtimeHost": host(self.args.runtime_base_url),
                "connectorHost": host(self.args.connector_base_url),
                "simulatorHost": host(self.args.simulator_base_url),
                "providerProfile": "autotrader",
                "sourceId": SOURCE_ID,
                "webhookSourceId": WEBHOOK_SOURCE_ID,
                "entityType": ENTITY_TYPE,
            },
            "fixture": self.fixture,
            "release": self.release,
            "counts": {
                "baselineVectors": self.baseline_count,
                "afterTargetedUpsert": self.added_count,
                "afterTargetedDelete": self.restored_count,
            },
            "checks": self.checks,
            "error": error,
            "nonClaims": [
                "This is not an official Auto Trader sandbox.",
                "This is not Auto Trader certification, endorsement, or production approval.",
                "No real advertiser, vehicle, credential, or customer data was used.",
                "Partner grants, data rights, sandbox validation, and production go-live evidence remain open.",
            ],
        }
        write_json(Path(self.args.evidence_out), evidence)
        return evidence

    def verify_health(self) -> None:
        for name, url in (
            ("runtime_readiness", f"{self.args.runtime_base_url}/actuator/health/readiness"),
            ("connector_readiness", f"{self.args.connector_base_url}/actuator/health/readiness"),
            ("simulator_readiness", f"{self.args.simulator_base_url}/actuator/health/readiness"),
        ):
            response = self.http.request("GET", url)
            self.expect(name, response.status == 200 and value(response.body, "status") == "UP", {
                "httpStatus": response.status,
                "status": value(response.body, "status"),
            })

        status = self.simulator_control("GET", "/internal/control/status")
        accounts = status.body.get("accounts", []) if isinstance(status.body, dict) else []
        auto_trader = next(
            (
                account
                for account in accounts
                if account.get("profile") == "autotrader"
                and str(account.get("accountId")) == self.args.advertiser_id
            ),
            None,
        )
        fixture = status.body.get("fixture", {}) if isinstance(status.body, dict) else {}
        self.expect("simulator_fixture_scope", status.status == 200 and auto_trader is not None, {
            "httpStatus": status.status,
            "fixtureVersion": fixture.get("fixtureVersion"),
            "advertiserPresent": auto_trader is not None,
        })
        self.fixture = {
            "fixtureVersion": fixture.get("fixtureVersion"),
            "resetId": fixture.get("resetId"),
            "resetAt": fixture.get("resetAt"),
            "advertiserId": self.args.advertiser_id,
        }

    def verify_provider_contract(self) -> str:
        invalid = self.http.request(
            "POST",
            f"{self.args.simulator_base_url}/authenticate",
            form_body={"key": "invalid", "secret": "invalid"},
        )
        self.expect("authenticate_rejects_invalid_credentials", invalid.status == 401, {
            "httpStatus": invalid.status,
        })

        authenticated = self.http.request(
            "POST",
            f"{self.args.simulator_base_url}/authenticate",
            form_body={"key": self.args.provider_key, "secret": self.args.provider_secret},
        )
        body = authenticated.body if isinstance(authenticated.body, dict) else {}
        token = str(body.get("access_token") or "")
        self.expect(
            "authenticate_exact_public_contract",
            authenticated.status == 200
            and set(body) == {"access_token", "expires_at"}
            and bool(token)
            and bool(body.get("expires_at")),
            {
                "httpStatus": authenticated.status,
                "responseKeys": sorted(body),
                "tokenPresent": bool(token),
                "expiryPresent": bool(body.get("expires_at")),
            },
        )

        stock = self.provider_stock(token, page=1, page_size=2)
        stock_body = stock.body if isinstance(stock.body, dict) else {}
        records = stock_body.get("results", [])
        valid_records = all(self.valid_stock_record(record) for record in records)
        self.expect(
            "stock_page_exact_public_contract",
            stock.status == 200
            and set(stock_body) == {"results", "totalResults"}
            and len(records) == 2
            and int(stock_body.get("totalResults") or 0) >= len(records)
            and valid_records,
            {
                "httpStatus": stock.status,
                "responseKeys": sorted(stock_body),
                "pageRecordCount": len(records),
                "totalResults": stock_body.get("totalResults"),
                "allRecordsBoundToAdvertiser": valid_records,
            },
        )
        primary_image = value(records[0], "media", "images") if records else None
        primary_image = primary_image[0] if isinstance(primary_image, list) and primary_image else {}
        image_href = str(primary_image.get("href") or "") if isinstance(primary_image, dict) else ""
        media = self.http.request("GET", image_href) if image_href else Response(0, {}, None)
        self.expect(
            "stock_primary_media_public_delivery",
            bool(image_href)
            and host(image_href) == host(self.args.simulator_base_url)
            and media.status == 200
            and media.headers.get("content-type") == "image/webp"
            and "public" in media.headers.get("cache-control", "")
            and "immutable" in media.headers.get("cache-control", ""),
            {
                "imageIdPresent": bool(primary_image.get("imageId")) if isinstance(primary_image, dict) else False,
                "mediaHost": host(image_href),
                "httpStatus": media.status,
                "contentType": media.headers.get("content-type"),
                "cacheControl": media.headers.get("cache-control"),
            },
        )
        return token

    def verify_platform_release(self) -> None:
        response = self.platform("GET", f"/api/deployments/{self.args.deployment_id}/releases")
        releases = response.body if isinstance(response.body, list) else []
        release = releases[0] if releases else {}
        self.expect(
            "immutable_deployment_release_verified",
            response.status == 200
            and release.get("status") == "APPLIED_VERIFIED"
            and release.get("verificationStatus") == "PASSED"
            and (not self.args.expected_version_id or release.get("deploymentVersionId") == self.args.expected_version_id)
            and (not self.args.expected_source_artifact_id or release.get("sourceArtifactId") == self.args.expected_source_artifact_id),
            {
                "httpStatus": response.status,
                "releaseId": release.get("id"),
                "releaseStatus": release.get("status"),
                "verificationStatus": release.get("verificationStatus"),
                "deploymentVersionId": release.get("deploymentVersionId"),
                "sourceArtifactId": release.get("sourceArtifactId"),
            },
        )
        details = release.get("provisioningDetails", {}) if isinstance(release, dict) else {}
        self.release = {
            "releaseId": release.get("id"),
            "deploymentVersionId": release.get("deploymentVersionId"),
            "sourceArtifactId": release.get("sourceArtifactId"),
            "sourceGitCommit": details.get("sourceGitCommit"),
            "imageDigest": details.get("imageDigest"),
            "verificationRunId": release.get("verificationRunId"),
        }

    def verify_integration_contract(self) -> None:
        response = self.platform("GET", f"/api/deployments/{self.args.deployment_id}/integrations")
        body = response.body if isinstance(response.body, dict) else {}
        sources = body.get("sources", [])
        webhooks = body.get("webhooks", [])
        source = next((item for item in sources if item.get("sourceId") == SOURCE_ID), {})
        webhook = next((item for item in webhooks if item.get("sourceId") == WEBHOOK_SOURCE_ID), {})
        state = source.get("state", {}) if isinstance(source, dict) else {}
        counts = state.get("counts", {}) if isinstance(state, dict) else {}
        self.expect(
            "deployment_local_provider_source_ready",
            response.status == 200
            and source.get("enabled") is True
            and source.get("targetedRecordFetchEnabled") is True
            and source.get("connectionProfileRef") == "autotrader-stock-provider"
            and source.get("protectedResourceBindingRef") == "autotrader-dealership-account"
            and state.get("status") == "COMPLETED"
            and int(counts.get("failedWorkCount") or 0) == 0,
            {
                "httpStatus": response.status,
                "sourceId": source.get("sourceId"),
                "status": state.get("status"),
                "sourceCount": counts.get("sourceCount"),
                "indexedCount": counts.get("indexedCount"),
                "failedWorkCount": counts.get("failedWorkCount"),
                "targetedRecordFetchEnabled": source.get("targetedRecordFetchEnabled"),
            },
        )
        self.expect(
            "deployment_local_notification_contract_ready",
            webhook.get("enabled") is True
            and webhook.get("method") == "PUT"
            and webhook.get("signatureHeader") == "AutoTrader-Signature"
            and webhook.get("reconciliationStrategy") == "FETCH_CURRENT_RECORD"
            and webhook.get("reconcileDataSourceRef") == SOURCE_ID
            and webhook.get("publicUrl") == f"{self.args.connector_base_url}/integrations/webhooks/{WEBHOOK_SOURCE_ID}",
            {
                "sourceId": webhook.get("sourceId"),
                "method": webhook.get("method"),
                "signatureHeader": webhook.get("signatureHeader"),
                "reconciliationStrategy": webhook.get("reconciliationStrategy"),
                "reconcileDataSourceRef": webhook.get("reconcileDataSourceRef"),
                "publicHost": host(str(webhook.get("publicUrl") or "")),
            },
        )

    def verify_current_record_fetch(self, token: str) -> None:
        listing = self.provider_stock(token, page=1, page_size=1)
        records = listing.body.get("results", []) if isinstance(listing.body, dict) else []
        stock_id = records[0].get("metadata", {}).get("stockId") if records else None
        target = self.provider_stock(token, page=1, page_size=1, stock_id=stock_id)
        target_records = target.body.get("results", []) if isinstance(target.body, dict) else []
        self.expect(
            "stock_id_current_record_fetch",
            target.status == 200
            and len(target_records) == 1
            and target_records[0].get("metadata", {}).get("stockId") == stock_id
            and self.valid_stock_record(target_records[0]),
            {
                "httpStatus": target.status,
                "requestedStockId": stock_id,
                "resultCount": len(target_records),
            },
        )

    def verify_targeted_reconciliation(self) -> None:
        self.reconcile_source()
        self.baseline_count = self.vector_count()

        upsert = self.simulator_control(
            "PUT",
            f"/internal/control/accounts/autotrader/{quote(self.args.advertiser_id)}/vehicles/{quote(self.canary_stock_id)}",
            {
                "id": self.canary_stock_id,
                "make": "Northstar",
                "model": "Trail E",
                "derivative": "Hosted Contract Canary",
                "year": 2026,
                "priceMinor": 4299500,
                "currency": "GBP",
                "fuelType": "Electric",
                "bodyStyle": "SUV",
                "transmission": "Automatic",
                "mileage": 12,
                "state": "active",
            },
        )
        self.created_stock = upsert.status == 200
        self.expect("simulator_control_upsert", self.created_stock and value(upsert.body, "operation") == "UPSERT", {
            "httpStatus": upsert.status,
            "operation": value(upsert.body, "operation"),
        })

        event = self.emit_event("VALID", self.canary_stock_id)
        event_id = str(value(event.body, "eventId") or "")
        self.canary_event_ids.append(event_id)
        attempts = value(event.body, "attempts") or []
        self.expect("signed_put_notification_accepted", event.status == 200 and attempt_statuses(attempts) == [200], {
            "controlHttpStatus": event.status,
            "deliveryStatuses": attempt_statuses(attempts),
            "eventId": event_id,
        })
        evidence = self.await_event(event_id)
        self.expect("targeted_current_record_reconciliation_completed", evidence.get("status") == "COMPLETED" and evidence.get("targetedRecord") is True, {
            "eventId": event_id,
            "status": evidence.get("status"),
            "targetedRecord": evidence.get("targetedRecord"),
            "attemptCount": evidence.get("attemptCount"),
        })
        self.added_count = self.await_vector_count(self.baseline_count + 1)
        self.expect("targeted_upsert_changed_one_vector", self.added_count == self.baseline_count + 1, {
            "baseline": self.baseline_count,
            "actual": self.added_count,
        })

        self.verify_notification_rejections()
        self.verify_duplicate_and_delayed_events()
        if not self.args.skip_chat:
            self.verify_anonymous_chat()

        self.delete_canary_stock()
        self.restored_count = self.await_vector_count(self.baseline_count)
        self.expect("targeted_delete_restored_vector_count", self.restored_count == self.baseline_count, {
            "baseline": self.baseline_count,
            "actual": self.restored_count,
        })

    def verify_notification_rejections(self) -> None:
        expected = {
            "WRONG_SIGNATURE": 401,
            "MALFORMED": 400,
            "WRONG_RESOURCE": 422,
        }
        for variant, expected_status in expected.items():
            response = self.emit_event(variant, self.canary_stock_id)
            statuses = attempt_statuses(value(response.body, "attempts") or [])
            self.expect(f"notification_{variant.lower()}_rejected", statuses == [expected_status], {
                "deliveryStatuses": statuses,
                "expectedStatus": expected_status,
            })

    def verify_duplicate_and_delayed_events(self) -> None:
        duplicate = self.emit_event("DUPLICATE", self.canary_stock_id)
        duplicate_id = str(value(duplicate.body, "eventId") or "")
        self.canary_event_ids.append(duplicate_id)
        statuses = attempt_statuses(value(duplicate.body, "attempts") or [])
        evidence = self.await_event(duplicate_id)
        self.expect("duplicate_notification_is_idempotent", statuses == [200, 200] and int(evidence.get("duplicateCount") or 0) >= 1, {
            "deliveryStatuses": statuses,
            "eventId": duplicate_id,
            "duplicateCount": evidence.get("duplicateCount"),
            "status": evidence.get("status"),
        })

        delayed = self.emit_event("DELAYED", self.canary_stock_id)
        delayed_id = str(value(delayed.body, "eventId") or "")
        self.canary_event_ids.append(delayed_id)
        delayed_evidence = self.await_event(delayed_id)
        self.expect(
            "delayed_notification_fetches_current_record",
            attempt_statuses(value(delayed.body, "attempts") or []) == [200]
            and delayed_evidence.get("status") == "COMPLETED"
            and delayed_evidence.get("targetedRecord") is True
            and self.vector_count() == self.baseline_count + 1,
            {
                "eventId": delayed_id,
                "status": delayed_evidence.get("status"),
                "targetedRecord": delayed_evidence.get("targetedRecord"),
                "vectorCount": self.vector_count(),
            },
        )

    def verify_anonymous_chat(self) -> None:
        origin_headers = {"Origin": self.args.browser_origin}
        bootstrap = self.http.request(
            "POST",
            f"{self.args.runtime_base_url}/api/public/chat/session",
            headers=origin_headers,
            json_body={},
        )
        bootstrap_body = bootstrap.body if isinstance(bootstrap.body, dict) else {}
        token = str(bootstrap_body.get("accessToken") or bootstrap_body.get("token") or "")
        self.expect("anonymous_runtime_session_bootstrap", bootstrap.status == 200 and bootstrap_body.get("success") is True and bool(token), {
            "httpStatus": bootstrap.status,
            "success": bootstrap_body.get("success"),
            "tokenPresent": bool(token),
            "shellConfigPresent": isinstance(bootstrap_body.get("shellConfig"), dict),
        })
        query = self.http.request(
            "POST",
            f"{self.args.runtime_base_url}/api/chat/me/query",
            headers={**origin_headers, "Authorization": f"Bearer {token}"},
            json_body={
                "query": "Find the Northstar Trail E Hosted Contract Canary and give its exact price, mileage, fuel and transmission.",
                "mode": "executor",
                "position": "search",
                "conversationId": f"autotrader-contract-canary-{int(time.time())}",
            },
            timeout=90,
        )
        query_body = query.body if isinstance(query.body, dict) else {}
        actions = query_body.get("actions", [])
        action_names = [item.get("action") for item in actions if isinstance(item, dict)]
        answer = str(query_body.get("answer") or query_body.get("safeSummary") or "")
        action_records = [
            record
            for action in actions
            if isinstance(action, dict)
            for record in (value(action, "actionResult", "data", "results") or [])
            if isinstance(record, dict)
        ]
        canary_record = next(
            (record for record in action_records if record.get("stockId") == self.canary_stock_id),
            None,
        )
        structured_facts_exact = (
            isinstance(canary_record, dict)
            and float(canary_record.get("priceGbp") or -1) == 42995.0
            and int(canary_record.get("mileage") or -1) == 12
            and canary_record.get("fuelType") == "Electric"
            and canary_record.get("transmission") == "Automatic"
        )
        answer_price_present = re.search(r"(?:£|GBP\s*)?42[,.\s]?995(?:\.0+)?", answer, re.IGNORECASE) is not None
        answer_mileage_present = re.search(
            r"(?:mileage\D{0,20}12\b|\b12\s*(?:miles?|mi)\b)",
            answer,
            re.IGNORECASE,
        ) is not None
        answer_facts_present = (
            "Northstar Trail E" in answer
            and answer_price_present
            and answer_mileage_present
            and "Electric" in answer
            and "Automatic" in answer
        )
        documents = value(query_body, "ragResponse", "documents") or []
        self.expect(
            "anonymous_chat_uses_provider_action_and_indexed_evidence",
            query.status == 200
            and query_body.get("success") is True
            and "dealership_search_inventory" in action_names
            and structured_facts_exact
            and answer_facts_present
            and len(query_body.get("sources", [])) > 0
            and len(documents) > 0,
            {
                "httpStatus": query.status,
                "success": query_body.get("success"),
                "type": query_body.get("type"),
                "actionNames": action_names,
                "sourcesCount": len(query_body.get("sources", [])),
                "documentsCount": len(documents),
                "answerContainsCanary": "Northstar Trail E" in answer,
                "structuredFactsExact": structured_facts_exact,
                "answerPricePresent": answer_price_present,
                "answerMileagePresent": answer_mileage_present,
                "answerFuelPresent": "Electric" in answer,
                "answerTransmissionPresent": "Automatic" in answer,
                "providerRequestId": query_body.get("providerRequestId"),
            },
        )

        media_query = self.http.request(
            "POST",
            f"{self.args.runtime_base_url}/api/chat/me/query-once",
            headers={**origin_headers, "Authorization": f"Bearer {token}"},
            json_body={
                "query": "Find the indexed Northstar Trail E Hosted Contract Canary and return its current provider facts.",
                "mode": "thinker",
                "position": "search",
                "context": {
                    "vectorSpace": ENTITY_TYPE,
                    "entityType": ENTITY_TYPE,
                    "preferredVectorSpaces": [ENTITY_TYPE],
                },
            },
            timeout=90,
        )
        media_body = media_query.body if isinstance(media_query.body, dict) else {}
        action_records = [
            record
            for action in media_body.get("actions", [])
            if isinstance(action, dict)
            for record in (value(action, "actionResult", "data", "results") or [])
            if isinstance(record, dict)
        ]
        action_media = [
            images[0]
            for record in action_records
            for images in [value(record, "media", "images") or []]
            if isinstance(images, list) and images
        ]
        action_media = [
            image
            for image in action_media
            if isinstance(image, dict)
            and bool(image.get("imageId"))
            and host(str(image.get("href") or "")) == host(self.args.simulator_base_url)
        ]
        media_documents = value(media_body, "ragResponse", "documents") or []
        indexed_media = [
            document.get("metadata", {})
            for document in media_documents
            if isinstance(document, dict)
            and isinstance(document.get("metadata"), dict)
            and document.get("metadata", {}).get("imageId")
            and host(str(document.get("metadata", {}).get("imageUrl") or "")) == host(self.args.simulator_base_url)
        ]
        self.expect(
            "anonymous_chat_surfaces_provider_media_in_action_and_rag",
            media_query.status == 200
            and media_body.get("success") is True
            and bool(action_media)
            and bool(indexed_media),
            {
                "httpStatus": media_query.status,
                "success": media_body.get("success"),
                "actionMediaCount": len(action_media),
                "indexedMediaDocumentCount": len(indexed_media),
                "mediaHost": host(self.args.simulator_base_url),
                "providerRequestId": media_body.get("providerRequestId"),
            },
        )

    def delete_canary_stock(self) -> None:
        if not self.created_stock:
            return
        deleted = self.simulator_control(
            "DELETE",
            f"/internal/control/accounts/autotrader/{quote(self.args.advertiser_id)}/vehicles/{quote(self.canary_stock_id)}?purge=false",
        )
        self.expect("simulator_control_tombstone", deleted.status == 200 and value(deleted.body, "operation") == "TOMBSTONE", {
            "httpStatus": deleted.status,
            "operation": value(deleted.body, "operation"),
        })
        event = self.emit_event("VALID", self.canary_stock_id)
        event_id = str(value(event.body, "eventId") or "")
        self.canary_event_ids.append(event_id)
        attempts = attempt_statuses(value(event.body, "attempts") or [])
        evidence = self.await_event(event_id)
        self.expect("signed_delete_notification_completed", attempts == [200] and evidence.get("status") == "COMPLETED", {
            "deliveryStatuses": attempts,
            "eventId": event_id,
            "status": evidence.get("status"),
        })
        self.created_stock = False

    def cleanup_canary_stock(self) -> str | None:
        if not self.created_stock:
            return None
        try:
            deleted = self.simulator_control(
                "DELETE",
                f"/internal/control/accounts/autotrader/{quote(self.args.advertiser_id)}/vehicles/{quote(self.canary_stock_id)}?purge=false",
            )
            if deleted.status != 200:
                return f"simulator tombstone returned HTTP {deleted.status}"
            event = self.emit_event("VALID", self.canary_stock_id)
            statuses = attempt_statuses(value(event.body, "attempts") or [])
            if statuses != [200]:
                return f"cleanup notification statuses were {statuses}"
            event_id = str(value(event.body, "eventId") or "")
            self.await_event(event_id)
            if self.baseline_count is not None:
                self.await_vector_count(self.baseline_count)
            self.created_stock = False
            return None
        except Exception as exception:
            return str(exception)

    def provider_stock(
        self,
        token: str,
        *,
        page: int,
        page_size: int,
        stock_id: str | None = None,
    ) -> Response:
        query: dict[str, str | int] = {
            "advertiserId": self.args.advertiser_id,
            "lifecycleState": "FORECOURT",
            "page": page,
            "pageSize": page_size,
        }
        if stock_id:
            query["stockId"] = stock_id
        return self.http.request(
            "GET",
            f"{self.args.simulator_base_url}/stock?{urllib.parse.urlencode(query)}",
            headers={"Authorization": f"Bearer {token}"},
        )

    def valid_stock_record(self, record: Any) -> bool:
        if not isinstance(record, dict):
            return False
        metadata = record.get("metadata", {})
        adverts = value(record, "adverts", "retailAdverts", "advertiserAdvert") or {}
        images = value(record, "media", "images") or []
        primary_image = images[0] if isinstance(images, list) and images else {}
        return (
            set(record) == {"advertiser", "metadata", "vehicle", "adverts", "features", "media"}
            and value(record, "advertiser", "advertiserId") == self.args.advertiser_id
            and bool(metadata.get("stockId"))
            and bool(metadata.get("searchId"))
            and metadata.get("lifecycleState") == "FORECOURT"
            and adverts.get("status") == "PUBLISHED"
            and isinstance(primary_image, dict)
            and bool(primary_image.get("imageId"))
            and host(str(primary_image.get("href") or "")) == host(self.args.simulator_base_url)
        )

    def reconcile_source(self) -> None:
        response = self.platform(
            "POST",
            f"/api/deployments/{self.args.deployment_id}/integrations/sources/{SOURCE_ID}/reconcile",
            {},
            timeout=120,
        )
        body = response.body if isinstance(response.body, dict) else {}
        counts = body.get("counts", {})
        self.expect("complete_baseline_reconciliation", response.status == 200 and body.get("status") == "COMPLETED" and int(counts.get("failedWorkCount") or 0) == 0, {
            "httpStatus": response.status,
            "status": body.get("status"),
            "sourceCount": counts.get("sourceCount"),
            "indexedCount": counts.get("indexedCount"),
            "deletedCount": counts.get("deletedCount"),
            "failedWorkCount": counts.get("failedWorkCount"),
        })

    def emit_event(self, variant: str, stock_id: str) -> Response:
        return self.simulator_control(
            "POST",
            f"/internal/control/accounts/autotrader/{quote(self.args.advertiser_id)}/events",
            {
                "variant": variant,
                "targetUrl": f"{self.args.connector_base_url}/integrations/webhooks/{WEBHOOK_SOURCE_ID}",
                "vehicleId": stock_id,
                "eventId": "ignored-by-autotrader-contract",
            },
        )

    def await_event(self, event_id: str) -> dict[str, Any]:
        if not event_id:
            raise CanaryFailure("simulator response did not include a connector eventId")

        def load() -> dict[str, Any] | None:
            response = self.platform(
                "GET",
                f"/api/deployments/{self.args.deployment_id}/integrations/webhooks/{WEBHOOK_SOURCE_ID}/events",
            )
            events = response.body if isinstance(response.body, list) else []
            return next((item for item in events if item.get("eventId") == event_id), None)

        return poll(load, lambda item: bool(item) and item.get("status") in {"COMPLETED", "DEAD_LETTER", "REJECTED"}, 30, 0.5)

    def vector_count(self) -> int:
        response = self.platform("GET", f"/api/deployments/{self.args.deployment_id}/poc")
        count = value(response.body, "indexing", "countsByEntityType", ENTITY_TYPE)
        if response.status != 200 or count is None:
            raise CanaryFailure(f"runtime vector count is unavailable (HTTP {response.status})")
        return int(count)

    def await_vector_count(self, expected: int) -> int:
        return poll(self.vector_count, lambda count: count == expected, 40, 0.5)

    def platform(
        self,
        method: str,
        path: str,
        json_body: Any | None = None,
        *,
        timeout: float = 30,
    ) -> Response:
        return self.http.request(
            method,
            f"{self.args.platform_base_url}{path}",
            headers={self.args.platform_api_key_header: self.args.platform_api_key},
            json_body=json_body,
            timeout=timeout,
        )

    def simulator_control(self, method: str, path: str, json_body: Any | None = None) -> Response:
        return self.http.request(
            method,
            f"{self.args.simulator_base_url}{path}",
            headers={"X-Simulator-Control-Key": self.args.simulator_control_key},
            json_body=json_body,
            timeout=30,
        )

    def expect(self, name: str, passed: bool, details: dict[str, Any]) -> None:
        self.checks.append({
            "name": name,
            "status": "PASSED" if passed else "FAILED",
            "details": details,
        })
        print(f"{'PASS' if passed else 'FAIL'} {name}")
        if not passed:
            raise CanaryFailure(name)


def poll(
    loader: Callable[[], Any],
    complete: Callable[[Any], bool],
    attempts: int,
    delay_seconds: float,
) -> Any:
    last: Any = None
    for _ in range(attempts):
        last = loader()
        if complete(last):
            return last
        time.sleep(delay_seconds)
    raise CanaryFailure(f"poll timed out; last bounded state={bounded(last)}")


def value(body: Any, *path: str) -> Any:
    current = body
    for segment in path:
        if not isinstance(current, dict):
            return None
        current = current.get(segment)
    return current


def attempt_statuses(attempts: Any) -> list[int]:
    if not isinstance(attempts, list):
        return []
    return [int(item.get("status") or 0) for item in attempts if isinstance(item, dict)]


def bounded(item: Any) -> str:
    try:
        return json.dumps(item, separators=(",", ":"), sort_keys=True)[:500]
    except TypeError:
        return str(item)[:500]


def quote(value_to_quote: str) -> str:
    return urllib.parse.quote(value_to_quote, safe="")


def host(url: str) -> str | None:
    return urllib.parse.urlparse(url).hostname


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def secret(name: str, required: bool = True) -> str:
    direct = os.environ.get(name, "").strip()
    if direct:
        return direct
    file_name = os.environ.get(f"{name}_FILE", "").strip()
    if file_name:
        loaded = Path(file_name).read_text(encoding="utf-8").strip()
        if loaded:
            return loaded
    if required:
        raise SystemExit(f"Set {name} or {name}_FILE.")
    return ""


def normalized_url(value_to_normalize: str) -> str:
    return value_to_normalize.strip().rstrip("/")


def write_json(path: Path, value_to_write: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value_to_write, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--platform-base-url", default=os.environ.get("PLATFORM_BASE_URL", ""))
    result.add_argument("--runtime-base-url", default=os.environ.get("RUNTIME_BASE_URL", ""))
    result.add_argument("--connector-base-url", default=os.environ.get("CONNECTOR_BASE_URL", ""))
    result.add_argument("--simulator-base-url", default=os.environ.get("SIMULATOR_BASE_URL", ""))
    result.add_argument("--deployment-id", default=os.environ.get("PLATFORM_DEPLOYMENT_ID", "dep-f023c863"))
    result.add_argument("--advertiser-id", default=os.environ.get("SIMULATOR_AUTOTRADER_ADVERTISER_ID", ""))
    result.add_argument("--platform-api-key-header", default=os.environ.get("PLATFORM_API_KEY_HEADER", "X-PLATFORM-API-KEY"))
    result.add_argument("--expected-version-id", default=os.environ.get("EXPECTED_DEPLOYMENT_VERSION_ID", ""))
    result.add_argument("--expected-source-artifact-id", default=os.environ.get("EXPECTED_SOURCE_ARTIFACT_ID", ""))
    result.add_argument("--browser-origin", default=os.environ.get("BROWSER_ORIGIN", "https://loomai.pro"))
    result.add_argument("--evidence-out", default=os.environ.get("EVIDENCE_OUT", "/tmp/autotrader-public-contract-hosted-canary.json"))
    result.add_argument("--confirm-mutation", action="store_true")
    result.add_argument("--skip-chat", action="store_true")
    return result


def main() -> int:
    args = parser().parse_args()
    args.platform_base_url = normalized_url(args.platform_base_url)
    args.runtime_base_url = normalized_url(args.runtime_base_url)
    args.connector_base_url = normalized_url(args.connector_base_url)
    args.simulator_base_url = normalized_url(args.simulator_base_url)
    required_values = {
        "PLATFORM_BASE_URL": args.platform_base_url,
        "RUNTIME_BASE_URL": args.runtime_base_url,
        "CONNECTOR_BASE_URL": args.connector_base_url,
        "SIMULATOR_BASE_URL": args.simulator_base_url,
        "SIMULATOR_AUTOTRADER_ADVERTISER_ID": args.advertiser_id,
    }
    missing = [name for name, configured in required_values.items() if not configured]
    if missing:
        raise SystemExit("Missing required values: " + ", ".join(missing))
    args.platform_api_key = secret("PLATFORM_API_KEY")
    args.simulator_control_key = secret("SIMULATOR_CONTROL_API_KEY")
    args.provider_key = secret("SIMULATOR_AUTOTRADER_API_KEY")
    args.provider_secret = secret("SIMULATOR_AUTOTRADER_API_SECRET")

    evidence = HostedCanary(args).run()
    print(json.dumps({
        "status": evidence["status"],
        "claim": evidence["claim"],
        "evidence": args.evidence_out,
        "checks": len(evidence["checks"]),
        "failed": len([item for item in evidence["checks"] if item["status"] == "FAILED"]),
        "error": evidence["error"],
    }, indent=2))
    return 0 if evidence["status"] == "PASSED" else 1


if __name__ == "__main__":
    sys.exit(main())

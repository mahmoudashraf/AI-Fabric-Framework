#!/usr/bin/env python3
"""Hosted verification for LoomAI's deployment-local external HTTP substrate.

This runner deliberately uses a separately hosted, neutral vehicle-provider
simulator. Passing it is evidence for HOSTED_GENERIC_SUBSTRATE_VERIFIED only;
it is not evidence of compatibility with any named provider.
"""

from __future__ import annotations

import argparse
import base64
import copy
import http.cookiejar
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_DIR = ROOT / "verification-support/external-vehicle-provider-simulator/fixtures/marketplace"


class VerificationFailure(RuntimeError):
    pass


@dataclass(frozen=True)
class HttpResult:
    status: int
    body: Any
    raw: str
    headers: dict[str, str]


class JsonClient:
    def __init__(
        self,
        base_url: str,
        *,
        default_headers: dict[str, str] | None = None,
        cookies: bool = False,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.default_headers = dict(default_headers or {})
        handlers: list[Any] = []
        if cookies:
            handlers.append(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.opener = urllib.request.build_opener(*handlers)

    def request(
        self,
        method: str,
        path: str,
        body: Any = None,
        *,
        form: dict[str, str] | None = None,
        headers: dict[str, str] | None = None,
        timeout: int = 180,
    ) -> HttpResult:
        if body is not None and form is not None:
            raise ValueError("A request cannot contain both JSON and form bodies.")
        url = path if path.startswith("http://") or path.startswith("https://") else self.base_url + path
        request_headers = {"Accept": "application/json", **self.default_headers, **(headers or {})}
        payload = None
        if form is not None:
            payload = urllib.parse.urlencode(form).encode("utf-8")
            request_headers["Content-Type"] = "application/x-www-form-urlencoded"
        elif body is not None:
            payload = json.dumps(body, separators=(",", ":")).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(url, data=payload, headers=request_headers, method=method)
        try:
            with self.opener.open(request, timeout=timeout) as response:
                raw = response.read().decode("utf-8", errors="replace")
                return HttpResult(
                    response.status,
                    parse_json(raw),
                    raw,
                    {key.lower(): value for key, value in response.headers.items()},
                )
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8", errors="replace")
            return HttpResult(
                error.code,
                parse_json(raw),
                raw,
                {key.lower(): value for key, value in error.headers.items()},
            )
        except (urllib.error.URLError, TimeoutError) as error:
            raise VerificationFailure(f"HTTP {method} {redacted_url(url)} failed: {type(error).__name__}") from error


def parse_json(raw: str) -> Any:
    if not raw.strip():
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return None


def redacted_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, "", ""))


def env(name: str, default: str = "", *, required: bool = False) -> str:
    file_path = os.environ.get(f"{name}_FILE", "").strip()
    value = Path(file_path).read_text(encoding="utf-8").strip() if file_path else os.environ.get(name, default).strip()
    if required and not value:
        raise VerificationFailure(f"Set {name} or {name}_FILE.")
    return value


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_instant(value: Any, label: str) -> datetime:
    try:
        normalized = re.sub(
            r"(\.\d{6})\d+(?=Z$|[+-]\d{2}:\d{2}$)",
            r"\1",
            str(value),
        ).replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
    except (TypeError, ValueError) as error:
        raise VerificationFailure(f"{label} is not a valid timestamp.") from error
    require(parsed.tzinfo is not None, f"{label} has no timezone.")
    return parsed


def require_status(result: HttpResult, expected: Iterable[int], label: str) -> Any:
    expected_set = set(expected)
    if result.status not in expected_set:
        detail = result.body if isinstance(result.body, dict) else {"responseClass": type(result.body).__name__}
        raise VerificationFailure(f"{label} returned HTTP {result.status}: {bounded_json(detail)}")
    return result.body


def bounded_json(value: Any, limit: int = 1000) -> str:
    rendered = json.dumps(value, separators=(",", ":"), sort_keys=True, default=str)
    return rendered if len(rendered) <= limit else rendered[:limit] + "..."


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationFailure(message)


def replace_placeholders(value: Any, replacements: dict[str, str]) -> Any:
    if isinstance(value, dict):
        return {key: replace_placeholders(item, replacements) for key, item in value.items()}
    if isinstance(value, list):
        return [replace_placeholders(item, replacements) for item in value]
    if isinstance(value, str):
        for token, replacement in replacements.items():
            value = value.replace(token, replacement)
    return value


def canonical(value: Any) -> str:
    return json.dumps(value, separators=(",", ":"), sort_keys=True)


def postgres_restore_image(database_image: str) -> str:
    postgres_restore_client_package(database_image)
    return "alpine:3.22"


def postgres_restore_client_package(database_image: str) -> str:
    match = re.search(r"(?:^|/)postgres:(\d+)", database_image)
    require(match is not None, "Coolify restore target PostgreSQL major version is unavailable.")
    major = int(match.group(1))
    require(major in {15, 16, 17}, f"PostgreSQL {major} has no approved Alpine 3.22 restore client.")
    return f"postgresql{major}-client"


def coolify_restore_compose(backup_path: str, database_image: str) -> str:
    require(
        backup_path.startswith("/data/coolify/backups/")
        and "\n" not in backup_path
        and "\r" not in backup_path,
        "Coolify restore path is outside the managed backup directory.",
    )
    volume = json.dumps(f"{backup_path}:/backup/input.dmp:ro")
    image = json.dumps(postgres_restore_image(database_image))
    client_package = postgres_restore_client_package(database_image)
    return f"""services:
  restore:
    image: {image}
    restart: \"no\"
    command:
      - /bin/sh
      - -c
      - >-
        set +e;
        apk add --no-cache {client_package};
        client_status=$$?;
        if [ \"$${{client_status}}\" -ne 0 ]; then
          touch /tmp/restore-failed;
          echo \"LOOMAI_RESTORE_CLIENT_FAILED_EXIT=$${{client_status}}\";
        else
          pg_restore --clean --if-exists --single-transaction --exit-on-error --no-owner --no-acl --host \"$${{PGHOST}}\" --username \"$${{PGUSER}}\" --dbname \"$${{PGDATABASE}}\" /backup/input.dmp;
          restore_status=$$?;
          if [ \"$${{restore_status}}\" -eq 0 ]; then
            touch /tmp/restore-complete;
            echo LOOMAI_RESTORE_COMPLETED;
          else
            touch /tmp/restore-failed;
            echo \"LOOMAI_RESTORE_FAILED_EXIT=$${{restore_status}}\";
          fi;
        fi;
        sleep 3600
    environment:
      PGHOST: ${{RESTORE_DB_HOST}}
      PGUSER: ${{RESTORE_DB_USER}}
      PGPASSWORD: ${{RESTORE_DB_PASSWORD}}
      PGDATABASE: ${{RESTORE_DB_NAME}}
    volumes:
      - {volume}
    networks:
      - coolify
    healthcheck:
      test: [\"CMD-SHELL\", \"test -f /tmp/restore-complete\"]
      interval: 2s
      timeout: 2s
      retries: 300
      start_period: 2s
networks:
  coolify:
    external: true
"""


@dataclass
class ProfileSpec:
    key: str
    template_plugin_id: str
    data_plugin_id: str
    data_source_id: str
    webhook_source_id: str
    knowledge_source_id: str
    vector_space: str
    account_field: str
    account_id: str
    baseline_source_count: int | None
    baseline_upsert_count: int
    secret_values: dict[str, str]
    secret_names: dict[str, str]
    template_version: str = ""
    data_plugin_version: str = ""
    deployment_id: str = ""
    install_id: str = ""
    version_one_id: str = ""
    version_two_id: str = ""
    connector_handle_id: str = ""
    runtime_handle_id: str = ""
    database_handle_id: str = ""
    database_uuid: str = ""
    webhook_url: str = ""


class HostedCanary:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.platform = JsonClient(args.platform_url, cookies=True)
        self.simulator = JsonClient(args.simulator_url)
        self.coolify = JsonClient(
            args.coolify_url,
            default_headers={"Authorization": f"Bearer {args.coolify_token}"},
        ) if args.coolify_url and args.coolify_token else None
        self.deployments: list[str] = []
        self.managed_secrets: list[str] = []
        self.backups: list[tuple[str, str]] = []
        self.coolify_restore_services: list[str] = []
        self.phase = "initialization"
        self.evidence: dict[str, Any] = {
            "schemaVersion": "loomai-hosted-generic-substrate-evidence-v1",
            "startedAt": utc_now(),
            "status": "RUNNING",
            "platformBaseUrl": redacted_url(args.platform_url),
            "simulatorBaseUrl": redacted_url(args.simulator_url),
            "sourceCommit": args.source_commit,
            "targetProfileId": args.target_profile_id,
            "checks": [],
            "deployments": [],
        }
        suffix = secrets.token_hex(4).upper()
        self.profiles = [
            ProfileSpec(
                key="a",
                template_plugin_id="mkp-template-neutral-vehicle-profile-a-verification",
                data_plugin_id="mkp-data-neutral-vehicle-profile-a-verification",
                data_source_id="verification-vehicle-source-a",
                webhook_source_id="verification-vehicle-events-a",
                knowledge_source_id="verification-vehicles-a",
                vector_space="verification-vehicle-a",
                account_field="accountId",
                account_id=args.profile_a_account,
                baseline_source_count=4,
                baseline_upsert_count=4,
                secret_values={
                    "providerKey": args.profile_a_key,
                    "providerSecret": args.profile_a_secret,
                    "webhookSecret": args.profile_a_webhook_secret,
                },
                secret_names={
                    "providerKey": f"MANAGED_NEUTRAL_CANARY_A_PROVIDER_KEY_{suffix}",
                    "providerSecret": f"MANAGED_NEUTRAL_CANARY_A_PROVIDER_SECRET_{suffix}",
                    "webhookSecret": f"MANAGED_NEUTRAL_CANARY_A_WEBHOOK_SECRET_{suffix}",
                },
            ),
            ProfileSpec(
                key="b",
                template_plugin_id="mkp-template-neutral-vehicle-profile-b-verification",
                data_plugin_id="mkp-data-neutral-vehicle-profile-b-verification",
                data_source_id="verification-vehicle-source-b",
                webhook_source_id="verification-vehicle-events-b",
                knowledge_source_id="verification-vehicles-b",
                vector_space="verification-vehicle-b",
                account_field="ownerRef",
                account_id=args.profile_b_account,
                baseline_source_count=None,
                baseline_upsert_count=4,
                secret_values={
                    "apiKey": args.profile_b_api_key,
                    "webhookSecret": args.profile_b_webhook_secret,
                },
                secret_names={
                    "apiKey": f"MANAGED_NEUTRAL_CANARY_B_API_KEY_{suffix}",
                    "webhookSecret": f"MANAGED_NEUTRAL_CANARY_B_WEBHOOK_SECRET_{suffix}",
                },
            ),
        ]

    def record(self, name: str, **details: Any) -> None:
        safe = {key: value for key, value in details.items() if value is not None}
        self.evidence["checks"].append({"name": name, "status": "PASSED", "at": utc_now(), **safe})
        print(f"PASS: {name}", flush=True)

    def run(self) -> None:
        try:
            self.login()
            self.verify_simulator()
            self.publish_fixtures()
            self.create_managed_secrets()
            for profile in self.profiles:
                self.create_deployment(profile)
            for profile in self.profiles:
                self.apply_deployment(profile)
            self.verify_isolation()
            for profile in self.profiles:
                self.verify_baseline(profile)
            self.verify_mutation_and_deletion()
            self.verify_faults()
            self.verify_webhooks()
            self.verify_restart(self.profiles[0])
            self.verify_release_rollback(self.profiles[1])
            self.verify_backup_restore(self.profiles[0])
            if self.args.keep_resources:
                raise VerificationFailure("--keep-resources prevents required hard-decommission evidence.")
            self.phase = "cleanup"
            self.cleanup(require_complete=True)
            if self.args.skip_backup_restore:
                raise VerificationFailure("--skip-backup-restore prevents the hosted claim gate from passing.")
            self.evidence["status"] = "PASSED"
            self.evidence["claim"] = "HOSTED_GENERIC_SUBSTRATE_VERIFIED"
        except Exception as error:
            self.evidence["status"] = "FAILED"
            self.evidence["failedPhase"] = self.phase
            self.evidence["failureClass"] = type(error).__name__
            if not self.args.keep_resources:
                try:
                    self.cleanup(require_complete=False)
                except Exception as cleanup_error:
                    self.evidence["cleanupFailureClass"] = type(cleanup_error).__name__
            raise
        finally:
            self.evidence["completedAt"] = utc_now()
            self.args.evidence_file.parent.mkdir(parents=True, exist_ok=True)
            self.args.evidence_file.write_text(json.dumps(self.evidence, indent=2) + "\n", encoding="utf-8")

    def login(self) -> None:
        self.phase = "platform login"
        body = require_status(
            self.platform.request(
                "POST",
                "/api/platform/auth/login",
                {"email": self.args.platform_email, "password": self.args.platform_password},
            ),
            {200},
            "platform login",
        )
        require(isinstance(body, dict) and body.get("authenticated") is True, "Platform login was not authenticated.")
        self.record("platform login")

    def simulator_control(self, method: str, path: str, body: Any = None, expected: set[int] | None = None) -> Any:
        result = self.simulator.request(
            method,
            path,
            body,
            headers={"X-Simulator-Control-Key": self.args.simulator_control_key},
            timeout=150,
        )
        return require_status(result, expected or {200}, f"simulator control {path}")

    def verify_simulator(self) -> None:
        self.phase = "simulator contract"
        health = require_status(
            self.simulator.request("GET", "/actuator/health/readiness"), {200}, "simulator readiness"
        )
        require(isinstance(health, dict) and health.get("status") == "UP", "Simulator readiness is not UP.")
        reset = self.simulator_control("POST", "/internal/control/reset")
        require(reset.get("fixtureVersion") == self.args.fixture_version, "Simulator fixture version is unexpected.")
        status = self.simulator_control("GET", "/internal/control/status")
        accounts = {(item.get("profile"), item.get("accountId")) for item in status.get("accounts", [])}
        require(("profile-a", self.args.profile_a_account) in accounts, "Profile A account is absent from simulator status.")
        require(("profile-b", self.args.profile_b_account) in accounts, "Profile B account is absent from simulator status.")
        self.evidence["fixture"] = {
            "version": reset.get("fixtureVersion"),
            "resetId": reset.get("resetId"),
        }
        self.record("independent simulator readiness and deterministic reset", fixtureVersion=reset.get("fixtureVersion"))

    def fixture(self, name: str) -> dict[str, Any]:
        raw = json.loads((FIXTURE_DIR / name).read_text(encoding="utf-8"))
        host = urllib.parse.urlsplit(self.args.simulator_url).hostname or ""
        return replace_placeholders(raw, {
            "__SIMULATOR_BASE_URL__": self.args.simulator_url.rstrip("/"),
            "__SIMULATOR_HOST__": host,
        })

    def ensure_publisher(self) -> str:
        publishers = require_status(
            self.platform.request("GET", "/api/marketplace/publishers"), {200}, "publisher list"
        )
        publisher = next((item for item in publishers if item.get("slug") == "loom-internal-verification"), None)
        if publisher is None:
            publisher = require_status(
                self.platform.request("POST", "/api/marketplace/publishers", {
                    "slug": "loom-internal-verification",
                    "displayName": "LoomAI Internal Verification",
                    "contactEmail": "verification@loomai.pro",
                }),
                {201},
                "publisher create",
            )
        publisher_id = publisher.get("id")
        require(bool(publisher_id), "Verification publisher has no id.")
        verified = require_status(
            self.platform.request(
                "PUT",
                f"/api/marketplace/publishers/{urllib.parse.quote(publisher_id)}/verification",
                {"verificationStatus": "VERIFIED", "status": "ACTIVE"},
            ),
            {200},
            "publisher verification",
        )
        require(verified.get("verificationStatus") == "VERIFIED", "Verification publisher is not verified.")
        return publisher_id

    def publish_manifest(self, manifest: dict[str, Any], publisher_id: str) -> None:
        plugin_id = manifest["pluginId"]
        version = manifest["version"]
        encoded_id = urllib.parse.quote(plugin_id)
        encoded_version = urllib.parse.quote(version)
        existing = self.platform.request("GET", f"/api/marketplace/plugins/{encoded_id}/versions/{encoded_version}")
        if existing.status == 200:
            require(existing.body.get("status") == "PUBLISHED", f"Existing {plugin_id}@{version} is not published.")
            require(
                canonical(existing.body.get("manifest")) == canonical(manifest),
                f"Published immutable manifest differs for {plugin_id}@{version}; publish a new fixture version.",
            )
            return
        require_status(existing, {404}, f"marketplace version lookup {plugin_id}@{version}")
        submission = require_status(
            self.platform.request(
                "POST",
                f"/api/marketplace/publishers/{urllib.parse.quote(publisher_id)}/submissions",
                {"pluginSlug": plugin_id.removeprefix("mkp-"), "releaseChannel": "GA", "manifest": manifest},
            ),
            {201},
            f"submission create {plugin_id}@{version}",
        )
        plugin_version_id = submission.get("pluginVersionId")
        require(bool(plugin_version_id), f"Submission did not return a plugin version id for {plugin_id}.")
        validated = require_status(
            self.platform.request(
                "POST",
                f"/api/marketplace/submissions/{urllib.parse.quote(plugin_version_id)}/validate",
                {"reviewNotes": "Internal hosted generic-substrate verification fixture."},
            ),
            {200},
            f"submission validate {plugin_id}@{version}",
        )
        require(validated.get("status") == "VALIDATED", f"Manifest validation did not pass for {plugin_id}.")
        published = require_status(
            self.platform.request(
                "POST",
                f"/api/marketplace/submissions/{urllib.parse.quote(plugin_version_id)}/publish",
                {"reviewNotes": "Internal verification only; not a customer or named-provider package."},
            ),
            {200},
            f"submission publish {plugin_id}@{version}",
        )
        require(published.get("status") == "PUBLISHED", f"Manifest publication did not pass for {plugin_id}.")

    def publish_fixtures(self) -> None:
        self.phase = "marketplace fixture publication"
        publisher_id = self.ensure_publisher()
        manifests = [
            self.fixture("profile-a-data.json"),
            self.fixture("profile-b-data.json"),
            self.fixture("profile-a-template.json"),
            self.fixture("profile-b-template.json"),
        ]
        versions = {manifest["pluginId"]: manifest["version"] for manifest in manifests}
        for profile in self.profiles:
            profile.template_version = versions.get(profile.template_plugin_id, "")
            profile.data_plugin_version = versions.get(profile.data_plugin_id, "")
            require(bool(profile.template_version), f"Profile {profile.key} template fixture version is empty.")
            require(bool(profile.data_plugin_version), f"Profile {profile.key} DATA fixture version is empty.")
        for manifest in manifests:
            self.publish_manifest(manifest, publisher_id)
        self.record("immutable internal DATA and TEMPLATE fixtures published", fixtureCount=len(manifests))

    def create_managed_secrets(self) -> None:
        self.phase = "deployment secret setup"
        for profile in self.profiles:
            for field, name in profile.secret_names.items():
                result = require_status(
                    self.platform.request(
                        "PUT",
                        f"/api/platform/secrets/managed/{urllib.parse.quote(name)}",
                        {"value": profile.secret_values[field]},
                    ),
                    {200},
                    f"managed secret create {name}",
                )
                require(result.get("present") is True, f"Managed secret {name} is not present after create.")
                self.managed_secrets.append(name)
        self.record("deployment-scoped provider secret references created", secretReferenceCount=len(self.managed_secrets))

    def create_deployment(self, profile: ProfileSpec) -> None:
        self.phase = f"profile {profile.key} deployment bootstrap"
        deployment = require_status(
            self.platform.request(
                "POST",
                f"/api/marketplace/templates/{urllib.parse.quote(profile.template_plugin_id)}/bootstrap",
                {
                    "pluginVersion": profile.template_version,
                    "name": f"Neutral External Provider Canary {profile.key.upper()} {int(time.time())}",
                    "environment": "staging",
                    "templateId": "custom-start-from-scratch",
                    "vectorProvisioningMode": "LOCAL_MANAGED",
                },
            ),
            {201},
            f"profile {profile.key} template bootstrap",
        )
        profile.deployment_id = deployment.get("id", "")
        require(bool(profile.deployment_id), f"Profile {profile.key} deployment id is empty.")
        self.deployments.append(profile.deployment_id)
        installs = require_status(
            self.platform.request("GET", f"/api/deployments/{profile.deployment_id}/marketplace-installs"),
            {200},
            f"profile {profile.key} install list",
        )
        install = next((item for item in installs if item.get("pluginId") == profile.data_plugin_id), None)
        require(install is not None, f"Profile {profile.key} required DATA install is absent.")
        profile.install_id = install.get("id", "")
        updated = require_status(
            self.platform.request(
                "PUT",
                f"/api/deployments/{profile.deployment_id}/marketplace-installs/{profile.install_id}",
                {
                    "pluginVersion": profile.data_plugin_version,
                    "status": "ENABLED",
                    "config": {profile.account_field: profile.account_id},
                    "secretRefs": profile.secret_names,
                },
            ),
            {200},
            f"profile {profile.key} DATA install configure",
        )
        require(updated.get("status") == "ENABLED", f"Profile {profile.key} DATA install is not enabled.")
        require(updated.get("readinessStatus") == "READY", f"Profile {profile.key} DATA install is not ready.")
        self.evidence["deployments"].append({
            "profile": profile.key,
            "deploymentId": profile.deployment_id,
            "dataPluginId": profile.data_plugin_id,
            "templatePluginId": profile.template_plugin_id,
        })
        self.record(f"profile {profile.key} isolated deployment bootstrapped", deploymentId=profile.deployment_id)

    def active_draft(self, profile: ProfileSpec) -> dict[str, Any]:
        return require_status(
            self.platform.request("GET", f"/api/deployments/{profile.deployment_id}/draft"),
            {200},
            f"profile {profile.key} active draft",
        )

    def publish_draft(self, profile: ProfileSpec) -> str:
        draft = self.active_draft(profile)
        draft_id = draft.get("id", "")
        validation = require_status(
            self.platform.request("POST", f"/api/deployment-drafts/{draft_id}/validate"),
            {200},
            f"profile {profile.key} draft validation",
        )
        require(validation.get("publishReady") is True, f"Profile {profile.key} draft is not publish-ready: {bounded_json(validation)}")
        version = require_status(
            self.platform.request("POST", f"/api/deployment-drafts/{draft_id}/publish"),
            {200, 201},
            f"profile {profile.key} draft publish",
        )
        version_id = version.get("id", "")
        require(bool(version_id), f"Profile {profile.key} published version id is empty.")
        return version_id

    def source_artifact_id(self) -> str:
        if self.args.source_artifact_id:
            return self.args.source_artifact_id
        artifacts = require_status(
            self.platform.request("GET", "/api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime"),
            {200},
            "runtime source artifact list",
        )
        matching = [item for item in artifacts if item.get("gitCommitSha") == self.args.source_commit]
        require(matching, f"No runtime source artifact is registered for commit {self.args.source_commit}.")
        matching.sort(key=lambda item: item.get("createdAt") or "", reverse=True)
        return matching[0].get("id", "")

    def apply_version(self, profile: ProfileSpec, version_id: str) -> str:
        query = urllib.parse.urlencode({
            "targetProfileId": self.args.target_profile_id,
            "sourceArtifactId": self.source_artifact_id(),
        })
        release = require_status(
            self.platform.request(
                "POST",
                f"/api/deployments/{profile.deployment_id}/apply/{version_id}?{query}",
            ),
            {200, 201},
            f"profile {profile.key} version apply",
        )
        release_id = release.get("id", "")
        require(bool(release_id), f"Profile {profile.key} release id is empty.")
        self.wait_until(
            f"profile {profile.key} release {release_id}",
            lambda: self.release_state(profile, release_id),
            lambda state: state.get("status") == "APPLIED_VERIFIED" and state.get("verificationStatus") == "PASSED",
            timeout=self.args.release_timeout,
            terminal=lambda state: state.get("status") in {
                "FAILED",
                "APPLIED_VERIFICATION_FAILED",
                "ROLLED_BACK",
                "CANCELLED",
            },
        )
        return release_id

    def release_state(self, profile: ProfileSpec, release_id: str) -> dict[str, Any]:
        releases = require_status(
            self.platform.request("GET", f"/api/deployments/{profile.deployment_id}/releases"),
            {200},
            f"profile {profile.key} releases",
        )
        return next((item for item in releases if item.get("id") == release_id), {})

    def apply_deployment(self, profile: ProfileSpec) -> None:
        self.phase = f"profile {profile.key} release apply"
        profile.version_one_id = self.publish_draft(profile)
        release_id = self.apply_version(profile, profile.version_one_id)
        self.refresh_resource_handles(profile)
        self.record(
            f"profile {profile.key} release applied and verified",
            deploymentId=profile.deployment_id,
            versionId=profile.version_one_id,
            releaseId=release_id,
        )

    def refresh_resource_handles(self, profile: ProfileSpec) -> None:
        resources = require_status(
            self.platform.request(
                "GET",
                f"/api/deployment-provider/resources?deploymentId={profile.deployment_id}&refresh=true",
            ),
            {200},
            f"profile {profile.key} resource handles",
        )
        by_kind = {item.get("resourceKind"): item for item in resources}
        for kind in ("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE"):
            require(kind in by_kind, f"Profile {profile.key} is missing provider resource {kind}.")
        profile.runtime_handle_id = by_kind["APPLICATION"].get("id", "")
        profile.connector_handle_id = by_kind["CONNECTOR_APPLICATION"].get("id", "")
        profile.database_handle_id = by_kind["RUNTIME_POSTGRES_DATABASE"].get("id", "")
        profile.database_uuid = by_kind["RUNTIME_POSTGRES_DATABASE"].get("providerResourceUuid", "")
        require(bool(profile.database_uuid), f"Profile {profile.key} Coolify database UUID is empty.")

    def verify_isolation(self) -> None:
        self.phase = "provider resource isolation"
        token = require_status(
            self.simulator.request(
                "POST",
                "/api/profile-a/authenticate",
                form={"key": self.args.profile_a_key, "secret": self.args.profile_a_secret},
            ),
            {200},
            "profile A token exchange",
        ).get("access_token", "")
        require(bool(token), "Profile A token exchange returned no token.")
        a_to_b = self.simulator.request(
            "GET",
            f"/api/profile-a/vehicles?account={urllib.parse.quote(self.args.profile_b_account)}&page=1&pageSize=2",
            headers={"Authorization": f"Bearer {token}"},
        )
        require_status(a_to_b, {403}, "profile A cross-account denial")
        b_to_a = self.simulator.request(
            "GET",
            f"/api/profile-b/accounts/{urllib.parse.quote(self.args.profile_a_account)}/vehicles?limit=2",
            headers={"X-Simulator-Api-Key": self.args.profile_b_api_key},
        )
        require_status(b_to_a, {403}, "profile B cross-account denial")
        self.record("provider credentials deny cross-account reads")

    def integration_overview(self, profile: ProfileSpec) -> dict[str, Any]:
        return require_status(
            self.platform.request("GET", f"/api/deployments/{profile.deployment_id}/integrations"),
            {200},
            f"profile {profile.key} integration overview",
        )

    def source_state(self, profile: ProfileSpec) -> dict[str, Any]:
        return require_status(
            self.platform.request(
                "GET",
                f"/api/deployments/{profile.deployment_id}/integrations/sources/{profile.data_source_id}",
            ),
            {200},
            f"profile {profile.key} integration source state",
        )

    def reconcile(self, profile: ProfileSpec, expected: set[int] | None = None) -> HttpResult:
        return self.platform.request(
            "POST",
            f"/api/deployments/{profile.deployment_id}/integrations/sources/{profile.data_source_id}/reconcile",
            timeout=180,
        )

    def assert_completed_source(self, profile: ProfileSpec, *, expected_source_count: int | None = None) -> dict[str, Any]:
        source = self.source_state(profile)
        state = source.get("state") or {}
        counts = state.get("counts") or {}
        require(state.get("status") == "COMPLETED", f"Profile {profile.key} source is not COMPLETED: {bounded_json(state)}")
        require(int(counts.get("failedWorkCount") or 0) == 0, f"Profile {profile.key} has failed indexing work.")
        require(
            int(counts.get("acceptedWorkCount") or 0) == int(counts.get("completedWorkCount") or 0),
            f"Profile {profile.key} accepted/completed work counts differ.",
        )
        if expected_source_count is not None:
            actual_source_count = int(counts.get("sourceCount") or 0)
            require(
                actual_source_count == expected_source_count,
                f"Profile {profile.key} source count differs: expected {expected_source_count}, got {actual_source_count}.",
            )
        return source

    def assert_completed_upsert_history(self, profile: ProfileSpec, source: dict[str, Any]) -> None:
        work = source.get("work") if isinstance(source.get("work"), list) else []
        completed_upserts = sum(
            1
            for item in work
            if isinstance(item, dict)
            and item.get("operation") == "UPSERT"
            and item.get("status") == "COMPLETED"
        )
        require(
            completed_upserts >= profile.baseline_upsert_count,
            f"Profile {profile.key} completed upsert history differs: "
            f"expected at least {profile.baseline_upsert_count}, got {completed_upserts}.",
        )

    def query(self, profile: ProfileSpec, question: str, *, attempts: int = 8) -> dict[str, Any]:
        last: dict[str, Any] = {}
        for attempt in range(1, attempts + 1):
            result = self.platform.request(
                "POST",
                f"/api/deployments/{profile.deployment_id}/poc-widget/chat/me/query?authPath=PLATFORM_PRIVATE",
                {
                    "query": question,
                    "conversationId": f"neutral-{profile.key}-{int(time.time())}-{attempt}",
                    "mode": "thinker",
                    "context": {
                        "vectorSpace": profile.vector_space,
                        "entityType": profile.vector_space,
                    },
                },
                timeout=180,
            )
            if result.status == 200 and isinstance(result.body, dict):
                last = result.body
                rag_response = last.get("ragResponse") if isinstance(last.get("ragResponse"), dict) else {}
                documents = [
                    document
                    for collection in (
                        last.get("sources"),
                        rag_response.get("documents"),
                        rag_response.get("sources"),
                    )
                    if isinstance(collection, list)
                    for document in collection
                    if isinstance(document, dict)
                ]
                sources = {
                    metadata.get("knowledgeSourceId")
                    for document in documents
                    if isinstance((metadata := document.get("metadata")), dict)
                }
                if profile.knowledge_source_id in sources:
                    return last
            time.sleep(8)
        raise VerificationFailure(f"Profile {profile.key} query did not return deployment-local source evidence: {bounded_json(last)}")

    def verify_baseline(self, profile: ProfileSpec) -> None:
        self.phase = f"profile {profile.key} baseline indexing"
        first = self.reconcile(profile)
        require_status(first, {200}, f"profile {profile.key} baseline reconcile")
        source = self.assert_completed_source(profile, expected_source_count=profile.baseline_source_count)
        self.assert_completed_upsert_history(profile, source)
        counts = (source.get("state") or {}).get("counts") or {}
        second = self.reconcile(profile)
        require_status(second, {200}, f"profile {profile.key} idempotent reconcile")
        self.assert_completed_source(profile)
        self.query(profile, "Which electric vehicles are present in this approved verification inventory?")
        overview = self.integration_overview(profile)
        webhook = next((item for item in overview.get("webhooks", []) if item.get("sourceId") == profile.webhook_source_id), None)
        require(webhook is not None and webhook.get("publicUrl", "").startswith("https://"), f"Profile {profile.key} public webhook URL is unavailable.")
        profile.webhook_url = webhook["publicUrl"]
        self.record(
            f"profile {profile.key} baseline, idempotency, indexing, and retrieval",
            sourceCount=counts.get("sourceCount"),
            completedWorkCount=counts.get("completedWorkCount"),
        )

    def mutate_vehicle(self, profile: ProfileSpec, vehicle_id: str, vehicle: dict[str, Any]) -> None:
        self.simulator_control(
            "PUT",
            f"/internal/control/accounts/profile-{profile.key}/{urllib.parse.quote(profile.account_id)}/vehicles/{urllib.parse.quote(vehicle_id)}",
            vehicle,
        )

    def verify_mutation_and_deletion(self) -> None:
        self.phase = "update delete and scheduled convergence"
        profile_a, profile_b = self.profiles
        self.mutate_vehicle(profile_a, "veh-a-1001", {
            "id": "veh-a-1001", "make": "Northstar", "model": "Atlas", "derivative": "Canary Aurora Edition",
            "year": 2025, "priceMinor": 3912500, "currency": "GBP", "fuelType": "electric",
            "bodyStyle": "suv", "transmission": "automatic", "mileage": 4200, "state": "active",
        })
        require_status(self.reconcile(profile_a), {200}, "profile A update reconcile")
        self.assert_completed_source(profile_a, expected_source_count=4)
        self.query(profile_a, "Tell me about the Northstar Atlas Canary Aurora Edition.")
        self.simulator_control(
            "DELETE",
            f"/internal/control/accounts/profile-a/{urllib.parse.quote(profile_a.account_id)}/vehicles/veh-a-1004?purge=true",
        )
        require_status(self.reconcile(profile_a), {200}, "profile A absence deletion reconcile")
        source_a = self.assert_completed_source(profile_a, expected_source_count=3)
        require(int(((source_a.get("state") or {}).get("counts") or {}).get("deletedCount") or 0) == 1, "Profile A absence deletion was not indexed.")
        self.simulator_control(
            "DELETE",
            f"/internal/control/accounts/profile-b/{urllib.parse.quote(profile_b.account_id)}/vehicles/veh-b-2004",
        )
        require_status(self.reconcile(profile_b), {200}, "profile B tombstone reconcile")
        source_b = self.assert_completed_source(profile_b, expected_source_count=1)
        require(int(((source_b.get("state") or {}).get("counts") or {}).get("deletedCount") or 0) == 1, "Profile B tombstone deletion was not indexed.")

        prior_success = (self.source_state(profile_a).get("state") or {}).get("lastSuccessAt")
        self.mutate_vehicle(profile_a, "veh-a-1099", {
            "id": "veh-a-1099", "make": "Solace", "model": "Beacon", "derivative": "Missed Event Repair",
            "year": 2025, "priceMinor": 2888000, "currency": "GBP", "fuelType": "electric",
            "bodyStyle": "hatchback", "transmission": "automatic", "mileage": 1200, "state": "active",
        })
        self.wait_until(
            "scheduled missed-event reconciliation",
            lambda: self.source_state(profile_a),
            lambda source: (source.get("state") or {}).get("status") == "COMPLETED"
                and (source.get("state") or {}).get("lastSuccessAt") != prior_success
                and int((((source.get("state") or {}).get("counts") or {}).get("sourceCount") or 0)) == 4,
            timeout=120,
        )
        self.query(profile_a, "Which vehicle is labelled Missed Event Repair?")
        self.record("updates, absence deletion, field tombstone, and scheduled missed-event repair")

    def set_fault(self, profile: ProfileSpec, mode: str, remaining: int, delay_ms: int = 0) -> None:
        self.simulator_control(
            "PUT",
            f"/internal/control/accounts/profile-{profile.key}/{urllib.parse.quote(profile.account_id)}/fault",
            {"mode": mode, "remaining": remaining, "delayMs": delay_ms},
            {204},
        )

    def clear_fault(self, profile: ProfileSpec) -> None:
        self.set_fault(profile, "NONE", 1)

    def expect_fault(self, profile: ProfileSpec, mode: str, expected_classes: set[str], *, remaining: int = 4, delay_ms: int = 0) -> None:
        self.set_fault(profile, mode, remaining, delay_ms)
        result = self.reconcile(profile)
        require(result.status >= 400, f"Fault {mode} unexpectedly reconciled successfully.")
        source = self.source_state(profile)
        state = source.get("state") or {}
        require(state.get("status") == "FAILED", f"Fault {mode} did not leave a FAILED source state.")
        require(state.get("errorClass") in expected_classes, f"Fault {mode} was classified as {state.get('errorClass')}.")
        self.clear_fault(profile)
        require_status(self.reconcile(profile), {200}, f"fault {mode} recovery reconcile")
        self.assert_completed_source(profile)

    def verify_faults(self) -> None:
        self.phase = "provider failure matrix"
        profile_a, profile_b = self.profiles
        self.expect_fault(profile_b, "UNAUTHORIZED", {"AUTHENTICATION_REQUIRED"})
        self.expect_fault(profile_b, "FORBIDDEN", {"CAPABILITY_DENIED", "RESOURCE_ACCESS_DENIED"})
        self.expect_fault(profile_b, "RATE_LIMITED", {"RATE_LIMITED"})
        self.expect_fault(profile_b, "UNAVAILABLE", {"SERVICE_UNAVAILABLE"})
        self.expect_fault(profile_b, "MALFORMED_RESPONSE", {"MALFORMED_RESPONSE"}, remaining=1)
        before = self.source_state(profile_a)
        before_success = (before.get("state") or {}).get("lastSuccessAt")
        self.expect_fault(profile_a, "PARTIAL_PAGE", {"MALFORMED_RESPONSE"}, remaining=1)
        after = self.assert_completed_source(profile_a, expected_source_count=4)
        require(int((((after.get("state") or {}).get("counts") or {}).get("deletedCount") or 0)) == 0, "Partial page caused deletion drift.")
        require((after.get("state") or {}).get("lastSuccessAt") != before_success, "Partial-page recovery did not complete.")
        self.expect_fault(profile_b, "TIMEOUT", {"TIMEOUT", "SERVICE_UNAVAILABLE"}, remaining=2, delay_ms=30000)
        self.record("authentication, authorization, rate, unavailable, malformed, partial, and timeout failures recover fail-closed")

    def emit_event(self, profile: ProfileSpec, variant: str, event_id: str) -> dict[str, Any]:
        return self.simulator_control(
            "POST",
            f"/internal/control/accounts/profile-{profile.key}/{urllib.parse.quote(profile.account_id)}/events",
            {"variant": variant, "targetUrl": profile.webhook_url, "vehicleId": "veh-a-1001" if profile.key == "a" else "veh-b-2001", "eventId": event_id},
        )

    def webhook_events(self, profile: ProfileSpec) -> list[dict[str, Any]]:
        return require_status(
            self.platform.request(
                "GET",
                f"/api/deployments/{profile.deployment_id}/integrations/webhooks/{profile.webhook_source_id}/events",
            ),
            {200},
            f"profile {profile.key} webhook events",
        )

    def wait_event(self, profile: ProfileSpec, event_id: str, statuses: set[str], timeout: int = 120) -> dict[str, Any]:
        return self.wait_until(
            f"profile {profile.key} webhook event {event_id}",
            lambda: next((item for item in self.webhook_events(profile) if item.get("eventId") == event_id), {}),
            lambda event: event.get("status") in statuses,
            timeout=timeout,
        )

    def verify_webhooks(self) -> None:
        self.phase = "signed webhook lifecycle"
        profile = self.profiles[1]
        valid_id = f"evt-valid-{secrets.token_hex(4)}"
        delivery = self.emit_event(profile, "VALID", valid_id)
        require(all(item.get("status") == 202 for item in delivery.get("attempts", [])), "Valid signed event was not acknowledged with 202.")
        self.wait_event(profile, valid_id, {"COMPLETED"})

        duplicate_id = f"evt-duplicate-{secrets.token_hex(4)}"
        duplicate = self.emit_event(profile, "DUPLICATE", duplicate_id)
        require([item.get("status") for item in duplicate.get("attempts", [])] == [202, 200], "Duplicate event acknowledgement contract changed.")
        duplicate_state = self.wait_event(profile, duplicate_id, {"COMPLETED"})
        require(int(duplicate_state.get("duplicateCount") or 0) >= 1, "Duplicate event was not counted.")

        for variant in ("DELAYED", "OUT_OF_ORDER"):
            event_id = f"evt-{variant.lower()}-{secrets.token_hex(4)}"
            delivery = self.emit_event(profile, variant, event_id)
            require(delivery.get("attempts", [{}])[0].get("status") == 202, f"{variant} event was not accepted.")
            self.wait_event(profile, event_id, {"COMPLETED"})

        overview_before = self.integration_overview(profile)
        webhook_before = next(item for item in overview_before.get("webhooks", []) if item.get("sourceId") == profile.webhook_source_id)
        rejected_before = int((webhook_before.get("counts") or {}).get("rejected") or 0)
        rejected_ids: list[str] = []
        expected_status = {"WRONG_SIGNATURE": 401, "WRONG_RESOURCE": 403, "MALFORMED": 400}
        for variant, status in expected_status.items():
            event_id = f"evt-rejected-{variant.lower()}-{secrets.token_hex(4)}"
            rejected_ids.append(event_id)
            delivery = self.emit_event(profile, variant, event_id)
            require(delivery.get("attempts", [{}])[0].get("status") == status, f"{variant} event rejection status changed.")
        events = self.webhook_events(profile)
        require(not any(item.get("eventId") in rejected_ids for item in events), "Rejected event payload identity was retained.")
        overview_after = self.integration_overview(profile)
        webhook_after = next(item for item in overview_after.get("webhooks", []) if item.get("sourceId") == profile.webhook_source_id)
        rejected_after = int((webhook_after.get("counts") or {}).get("rejected") or 0)
        require(rejected_after >= rejected_before + len(rejected_ids), "Rejected event aggregate did not increase.")

        replay = require_status(
            self.platform.request(
                "POST",
                f"/api/deployments/{profile.deployment_id}/integrations/webhooks/{profile.webhook_source_id}/events/{valid_id}/replay",
            ),
            {202},
            "manual webhook replay",
        )
        require(replay.get("status") == "REPLAY_QUEUED", "Manual replay was not queued.")
        replayed = self.wait_event(profile, valid_id, {"COMPLETED"})
        require(int(replayed.get("replayCount") or 0) >= 1, "Manual replay count was not retained.")

        dead_id = f"evt-dead-letter-{secrets.token_hex(4)}"
        self.set_fault(profile, "UNAVAILABLE", 20)
        dead_delivery = self.emit_event(profile, "VALID", dead_id)
        require(dead_delivery.get("attempts", [{}])[0].get("status") == 202, "Dead-letter test event was not accepted.")
        self.wait_event(profile, dead_id, {"DEAD_LETTER"}, timeout=150)
        self.clear_fault(profile)
        require_status(
            self.platform.request(
                "POST",
                f"/api/deployments/{profile.deployment_id}/integrations/webhooks/{profile.webhook_source_id}/events/{dead_id}/replay",
            ),
            {202},
            "dead-letter manual replay",
        )
        recovered = self.wait_event(profile, dead_id, {"COMPLETED"}, timeout=120)
        require(int(recovered.get("replayCount") or 0) >= 1, "Dead-letter replay was not recorded.")
        self.record("TLS-originated signed events, duplicate, ordering, rejection, replay, and dead-letter recovery")

    def provider_action(self, handle_id: str, action: str, reason: str) -> None:
        require_status(
            self.platform.request(
                "POST",
                f"/api/deployment-provider/resources/{handle_id}/{action}",
                {"reason": reason},
            ),
            {200},
            f"provider resource {action}",
        )

    def wait_resource_running(self, handle_id: str, timeout: int = 300) -> dict[str, Any]:
        return self.wait_until(
            f"provider resource {handle_id} running",
            lambda: require_status(
                self.platform.request("GET", f"/api/deployment-provider/resources/{handle_id}/status"),
                {200},
                "provider resource status",
            ),
            lambda state: any(token in str(state.get("observedStatus") or state.get("status") or "").lower() for token in ("running", "healthy")),
            timeout=timeout,
        )

    def wait_resource_stopped(self, handle_id: str, timeout: int = 300) -> dict[str, Any]:
        return self.wait_until(
            f"provider resource {handle_id} stopped",
            lambda: require_status(
                self.platform.request("GET", f"/api/deployment-provider/resources/{handle_id}/status"),
                {200},
                "provider resource status",
            ),
            lambda state: any(token in str(state.get("observedStatus") or state.get("status") or "").lower() for token in ("stopped", "exited")),
            timeout=timeout,
        )

    def verify_restart(self, profile: ProfileSpec) -> None:
        self.phase = "connector restart persistence"
        before_source = self.source_state(profile)
        before = before_source.get("state") or {}
        before_success = before.get("lastSuccessAt")
        before_work = before_source.get("work") if isinstance(before_source.get("work"), list) else []
        retained_work_id = next(
            (str(item.get("workId")) for item in before_work if isinstance(item, dict) and item.get("workId")),
            "",
        )
        require(bool(before_success), "Connector restart precondition has no successful durable source state.")
        require(bool(retained_work_id), "Connector restart precondition has no durable indexing work history.")
        self.provider_action(profile.connector_handle_id, "restart", "Hosted neutral canary durable-state verification.")
        self.wait_resource_running(profile.connector_handle_id)
        after_source = self.wait_until(
            "connector completed source state after restart",
            lambda: self.source_state(profile),
            lambda source: (source.get("state") or {}).get("status") == "COMPLETED",
            timeout=300,
        )
        after = after_source.get("state") or {}
        after_work = after_source.get("work") if isinstance(after_source.get("work"), list) else []
        after_work_ids = {
            str(item.get("workId")) for item in after_work
            if isinstance(item, dict) and item.get("workId")
        }
        require(retained_work_id in after_work_ids, "Connector restart did not preserve durable indexing work history.")
        require(after.get("sourceVersion") == before.get("sourceVersion"), "Connector restart changed source version state.")
        require(after.get("cursor") == before.get("cursor"), "Connector restart changed the completed source cursor.")
        require(
            parse_instant(after.get("lastSuccessAt"), "Post-restart source success checkpoint")
            >= parse_instant(before_success, "Pre-restart source success checkpoint"),
            "Connector restart regressed the durable source success checkpoint.",
        )
        require(
            int(((after.get("counts") or {}).get("sourceCount") or 0))
            == int(((before.get("counts") or {}).get("sourceCount") or 0)),
            "Connector restart changed the durable source record count.",
        )
        require_status(self.reconcile(profile), {200}, "post-restart reconcile")
        self.assert_completed_source(profile)
        self.record(
            "connector restart preserves durable sync state",
            deploymentId=profile.deployment_id,
            scheduledReconcileAdvancedCheckpoint=after.get("lastSuccessAt") != before_success,
        )

    def verify_release_rollback(self, profile: ProfileSpec) -> None:
        self.phase = "immutable release rollback"
        draft = self.active_draft(profile)
        shell = copy.deepcopy(draft.get("shellConfig") or {})
        greeting = shell.setdefault("greeting", {})
        greeting["message"] = "Temporary canary revision used to prove immutable rollback."
        updated = require_status(
            self.platform.request(
                "PUT",
                f"/api/deployment-drafts/{draft.get('id')}",
                {"shellConfig": shell},
            ),
            {200},
            "temporary rollback revision update",
        )
        require((updated.get("shellConfig") or {}).get("greeting", {}).get("message", "").startswith("Temporary"), "Temporary revision was not saved.")
        profile.version_two_id = self.publish_draft(profile)
        self.apply_version(profile, profile.version_two_id)
        self.apply_version(profile, profile.version_one_id)
        require_status(self.reconcile(profile), {200}, "post-rollback reconcile")
        self.assert_completed_source(profile)
        self.record(
            "older immutable deployment version reapplied and verified",
            deploymentId=profile.deployment_id,
            fromVersionId=profile.version_two_id,
            restoredVersionId=profile.version_one_id,
        )

    def coolify_backup(self, database_uuid: str) -> tuple[str, dict[str, Any]]:
        require(self.coolify is not None, "Coolify API credentials are required for backup/restore evidence.")
        created = require_status(
            self.coolify.request(
                "POST",
                f"/api/v1/databases/{database_uuid}/backups",
                {
                    "frequency": "0 0 1 1 *",
                    "enabled": True,
                    "save_s3": False,
                    "dump_all": False,
                    "backup_now": True,
                    "database_backup_retention_amount_locally": 1,
                    "timeout": 600,
                },
            ),
            {201},
            "Coolify database backup create",
        )
        backup_uuid = (created or {}).get("uuid", "")
        require(bool(backup_uuid), "Coolify backup create returned no configuration UUID.")
        self.backups.append((database_uuid, backup_uuid))
        execution = self.wait_until(
            "Coolify database backup execution",
            lambda: self.coolify_backup_execution(database_uuid, backup_uuid),
            lambda item: str(item.get("status") or "").lower() in {"success", "succeeded", "completed"}
                and int(item.get("size") or 0) > 0,
            timeout=600,
            terminal=lambda item: str(item.get("status") or "").lower() in {"failed", "error"},
        )
        return backup_uuid, execution

    def coolify_backup_execution(self, database_uuid: str, backup_uuid: str) -> dict[str, Any]:
        body = require_status(
            self.coolify.request(
                "GET", f"/api/v1/databases/{database_uuid}/backups/{backup_uuid}/executions"
            ),
            {200},
            "Coolify database backup executions",
        )
        executions = body.get("executions", []) if isinstance(body, dict) else body or []
        return executions[0] if executions else {}

    def backup_server_path(self, execution: dict[str, Any]) -> str:
        for key in ("path", "location", "local_path", "filename"):
            candidate = str(execution.get(key) or "").strip()
            if candidate.startswith("/"):
                return candidate
        message = str(execution.get("message") or "")
        match = re.search(r"(/data/coolify/backups/[^\s'\"]+)", message)
        require(match is not None, "Coolify backup execution did not expose an absolute local restore path.")
        return match.group(1)

    def coolify_restore_target(self, database_uuid: str) -> dict[str, str]:
        require(self.coolify is not None, "Coolify API credentials are required for database restore.")
        database = require_status(
            self.coolify.request("GET", f"/api/v1/databases/{database_uuid}"),
            {200},
            "Coolify restore database metadata",
        )
        require(isinstance(database, dict), "Coolify restore database metadata is not an object.")
        destination = database.get("destination") or {}
        server = destination.get("server") or {}
        environment_id = database.get("environment_id")
        require(environment_id is not None, "Coolify restore database has no environment id.")

        projects = require_status(
            self.coolify.request("GET", "/api/v1/projects"),
            {200},
            "Coolify restore project discovery",
        )
        placements: list[tuple[str, str]] = []
        for project in projects or []:
            project_uuid = str(project.get("uuid") or "")
            if not project_uuid:
                continue
            environments = require_status(
                self.coolify.request("GET", f"/api/v1/projects/{project_uuid}/environments"),
                {200},
                "Coolify restore environment discovery",
            )
            for environment in environments or []:
                if str(environment.get("id")) == str(environment_id):
                    placements.append((project_uuid, str(environment.get("uuid") or "")))
        require(len(placements) == 1, "Coolify restore database placement was not uniquely resolvable.")

        project_uuid, environment_uuid = placements[0]
        target = {
            "projectUuid": project_uuid,
            "environmentUuid": environment_uuid,
            "serverUuid": str(server.get("uuid") or ""),
            "destinationUuid": str(destination.get("uuid") or ""),
            "host": database_uuid,
            "user": str(database.get("postgres_user") or ""),
            "password": str(database.get("postgres_password") or ""),
            "database": str(database.get("postgres_db") or ""),
            "image": str(database.get("image") or ""),
        }
        require(
            all(target.get(key) for key in (
                "projectUuid", "environmentUuid", "serverUuid", "destinationUuid",
                "host", "user", "password", "database",
            )),
            "Coolify restore database metadata is incomplete or sensitive read access is unavailable.",
        )
        return target

    def coolify_restore_service_state(self, service_uuid: str) -> dict[str, Any]:
        require(self.coolify is not None, "Coolify API credentials are required for database restore.")
        result = self.coolify.request("GET", f"/api/v1/services/{service_uuid}")
        if result.status == 404:
            return {"state": "absent", "statuses": []}
        body = require_status(result, {200}, "Coolify restore helper status")
        statuses = [
            str(item.get("status") or "").lower()
            for item in [*(body.get("applications") or []), *(body.get("databases") or [])]
            if isinstance(item, dict)
        ]
        application_uuids = [
            str(item.get("uuid") or "")
            for item in (body.get("applications") or [])
            if isinstance(item, dict) and item.get("uuid")
        ]
        if statuses and all(status.startswith("running:healthy") for status in statuses):
            state = "complete"
        elif statuses and all(status.startswith(("exited", "dead")) for status in statuses):
            state = "failed"
        else:
            state = "starting"
        return {"state": state, "statuses": statuses, "applicationUuids": application_uuids}

    def coolify_restore_service_logs(self, state: dict[str, Any], redactions: Iterable[str]) -> str:
        require(self.coolify is not None, "Coolify API credentials are required for database restore.")
        log_chunks: list[str] = []
        for application_uuid in state.get("applicationUuids") or []:
            result = self.coolify.request(
                "GET", f"/api/v1/applications/{application_uuid}/logs?lines=120"
            )
            if result.status == 200 and isinstance(result.body, dict):
                log_chunks.append(str(result.body.get("logs") or ""))
        logs = "\n".join(log_chunks)
        for secret in sorted({item for item in redactions if item}, key=len, reverse=True):
            logs = logs.replace(secret, "[REDACTED]")
        return logs

    def delete_coolify_restore_service(self, service_uuid: str) -> None:
        require(self.coolify is not None, "Coolify API credentials are required for database restore.")
        result = self.coolify.request(
            "DELETE",
            f"/api/v1/services/{service_uuid}?delete_configurations=true&delete_volumes=true"
            "&docker_cleanup=true&delete_connected_networks=true",
        )
        require_status(result, {200, 202, 204, 404}, "Coolify restore helper deletion")
        if result.status != 404:
            self.wait_until(
                "Coolify restore helper removal",
                lambda: self.coolify_restore_service_state(service_uuid),
                lambda state: state.get("state") == "absent",
                timeout=300,
                interval=5,
            )
        if service_uuid in self.coolify_restore_services:
            self.coolify_restore_services.remove(service_uuid)

    def coolify_ephemeral_restore(self, database_uuid: str, path: str) -> None:
        require(self.coolify is not None, "Coolify API credentials are required for database restore.")
        target = self.coolify_restore_target(database_uuid)
        compose = coolify_restore_compose(path, target["image"])
        created = require_status(
            self.coolify.request("POST", "/api/v1/services", {
                "project_uuid": target["projectUuid"],
                "environment_uuid": target["environmentUuid"],
                "server_uuid": target["serverUuid"],
                "destination_uuid": target["destinationUuid"],
                "name": f"loomai-hosted-restore-{secrets.token_hex(4)}",
                "description": "Ephemeral private PostgreSQL restore helper for LoomAI hosted verification.",
                "docker_compose_raw": base64.b64encode(compose.encode("utf-8")).decode("ascii"),
                "instant_deploy": False,
            }),
            {201},
            "Coolify restore helper create",
        )
        service_uuid = str((created or {}).get("uuid") or "")
        require(bool(service_uuid), "Coolify restore helper create returned no UUID.")
        self.coolify_restore_services.append(service_uuid)
        try:
            env_result = self.coolify.request(
                "PATCH",
                f"/api/v1/services/{service_uuid}/envs/bulk",
                {"data": [
                    {"key": "RESTORE_DB_HOST", "value": target["host"], "is_literal": True},
                    {"key": "RESTORE_DB_USER", "value": target["user"], "is_literal": True},
                    {
                        "key": "RESTORE_DB_PASSWORD",
                        "value": target["password"],
                        "is_literal": True,
                        "is_shown_once": True,
                    },
                    {"key": "RESTORE_DB_NAME", "value": target["database"], "is_literal": True},
                ]},
            )
            if env_result.status != 201:
                raise VerificationFailure(
                    f"Coolify restore helper environment update returned HTTP {env_result.status}."
                )
            require_status(
                self.coolify.request("POST", f"/api/v1/services/{service_uuid}/start"),
                {200, 202},
                "Coolify restore helper start",
            )
            started_at = time.monotonic()
            observed_running = False
            last: dict[str, Any] = {}
            while time.monotonic() - started_at < 600:
                last = self.coolify_restore_service_state(service_uuid)
                statuses = last.get("statuses") or []
                observed_running = observed_running or any(
                    str(status).startswith("running") for status in statuses
                )
                logs = self.coolify_restore_service_logs(last, target.values())
                if "LOOMAI_RESTORE_COMPLETED" in logs:
                    return
                if "LOOMAI_RESTORE_FAILED_EXIT=" in logs or "LOOMAI_RESTORE_CLIENT_FAILED_EXIT=" in logs:
                    raise VerificationFailure(
                        f"Coolify restore helper reported failure: {bounded_json(logs, 2000)}"
                    )
                if last.get("state") == "failed" and (
                    observed_running or time.monotonic() - started_at >= 180
                ):
                    raise VerificationFailure(
                        f"Coolify restore helper exited before completion: {bounded_json(last)}"
                    )
                time.sleep(5)
            raise VerificationFailure(f"Coolify restore helper timed out: {bounded_json(last)}")
        finally:
            self.delete_coolify_restore_service(service_uuid)

    def coolify_restore(self, database_uuid: str, path: str) -> str:
        result = self.coolify.request(
            "POST",
            f"/api/v1/databases/{database_uuid}/imports",
            {"source": "server", "path": path, "dump_all": False, "replace_existing": True},
            timeout=180,
        )
        if result.status == 404:
            self.coolify_ephemeral_restore(database_uuid, path)
            return "COOLIFY_EPHEMERAL_PG_RESTORE"
        require_status(result, {202}, "Coolify database restore request")
        body = result.body if isinstance(result.body, dict) else {}
        activity_id = body.get("id") or body.get("activity_id") or body.get("activityId")
        if activity_id is None:
            location = result.headers.get("location", "")
            match = re.search(r"/imports/(\d+)$", location)
            activity_id = match.group(1) if match else None
        require(activity_id is not None, "Coolify database restore did not expose an activity id.")
        state = self.wait_until(
            "Coolify database restore execution",
            lambda: require_status(
                self.coolify.request("GET", f"/api/v1/databases/{database_uuid}/imports/{activity_id}"),
                {200},
                "Coolify database restore status",
            ),
            lambda item: str(item.get("status") or "").lower() in {"success", "succeeded", "completed", "finished"}
                and int(item.get("exit_code") or 0) == 0,
            timeout=600,
            terminal=lambda item: str(item.get("status") or "").lower() in {"failed", "error"}
                or (item.get("finished_at") is not None and int(item.get("exit_code") or 0) != 0),
        )
        require(int(state.get("exit_code") or 0) == 0, "Coolify restore exited nonzero.")
        return "COOLIFY_NATIVE_IMPORT_API"

    def verify_backup_restore(self, profile: ProfileSpec) -> None:
        self.phase = "deployment database backup restore"
        if self.args.skip_backup_restore:
            self.evidence["checks"].append({
                "name": "deployment database backup and restore",
                "status": "SKIPPED",
                "at": utc_now(),
                "reason": "explicit development-only skip",
            })
            return
        before = self.source_state(profile).get("state") or {}
        before_success = before.get("lastSuccessAt")
        backup_uuid, execution = self.coolify_backup(profile.database_uuid)
        backup_path = self.backup_server_path(execution)
        self.mutate_vehicle(profile, "veh-a-1099", {
            "id": "veh-a-1099", "make": "Solace", "model": "Beacon", "derivative": "After Backup Mutation",
            "year": 2025, "priceMinor": 2899000, "currency": "GBP", "fuelType": "electric",
            "bodyStyle": "hatchback", "transmission": "automatic", "mileage": 1300, "state": "active",
        })
        require_status(self.reconcile(profile), {200}, "post-backup mutation reconcile")
        changed_success = (self.source_state(profile).get("state") or {}).get("lastSuccessAt")
        require(bool(changed_success) and changed_success != before_success, "Post-backup source state did not advance.")

        self.provider_action(profile.runtime_handle_id, "stop", "Pause runtime writes for database restore rehearsal.")
        self.provider_action(profile.connector_handle_id, "stop", "Pause connector writes for database restore rehearsal.")
        self.wait_resource_stopped(profile.runtime_handle_id)
        self.wait_resource_stopped(profile.connector_handle_id)
        restore_method = self.coolify_restore(profile.database_uuid, backup_path)
        self.provider_action(profile.runtime_handle_id, "start", "Resume runtime after database restore rehearsal.")
        self.provider_action(profile.connector_handle_id, "start", "Resume connector after database restore rehearsal.")
        self.wait_resource_running(profile.runtime_handle_id)
        self.wait_resource_running(profile.connector_handle_id)
        self.wait_until(
            "integration operations after database restore",
            lambda: self.platform.request("GET", f"/api/deployments/{profile.deployment_id}/integrations/sources/{profile.data_source_id}"),
            lambda result: result.status == 200 and isinstance(result.body, dict),
            timeout=300,
        )
        restored = self.source_state(profile).get("state") or {}
        restored_success = restored.get("lastSuccessAt")
        require(bool(restored_success), "Connector source state was unavailable after database restore.")
        require(
            restored_success != changed_success,
            "Connector source state still exposes the exact post-backup mutation checkpoint.",
        )
        require_status(self.reconcile(profile), {200}, "post-restore convergence reconcile")
        self.assert_completed_source(profile)
        self.record(
            "Coolify PostgreSQL backup, destructive restore, and source convergence",
            deploymentId=profile.deployment_id,
            backupConfigurationId=backup_uuid,
            backupSize=int(execution.get("size") or 0),
            restoreMethod=restore_method,
            restoredCheckpointObserved=restored_success == before_success,
        )

    def wait_until(
        self,
        label: str,
        fetch: Callable[[], Any],
        complete: Callable[[Any], bool],
        *,
        timeout: int,
        interval: int = 8,
        terminal: Callable[[Any], bool] | None = None,
    ) -> Any:
        deadline = time.monotonic() + timeout
        last: Any = None
        while time.monotonic() < deadline:
            last = fetch()
            if complete(last):
                return last
            if terminal is not None and terminal(last):
                raise VerificationFailure(f"{label} reached a terminal failure: {bounded_json(last)}")
            time.sleep(interval)
        raise VerificationFailure(f"{label} timed out: {bounded_json(last)}")

    def cleanup(self, *, require_complete: bool) -> None:
        cleanup_failures: list[str] = []
        for service_uuid in list(reversed(self.coolify_restore_services)):
            try:
                self.delete_coolify_restore_service(service_uuid)
            except Exception:
                cleanup_failures.append(f"restore-helper:{service_uuid}")
        self.coolify_restore_services.clear()

        for database_uuid, backup_uuid in list(reversed(self.backups)):
            if self.coolify is None:
                continue
            result = self.coolify.request(
                "DELETE", f"/api/v1/databases/{database_uuid}/backups/{backup_uuid}"
            )
            if result.status not in {200, 204, 404}:
                cleanup_failures.append(f"backup:{backup_uuid}:http-{result.status}")
        self.backups.clear()

        deleted_deployments: list[str] = []
        for deployment_id in list(reversed(self.deployments)):
            archive = self.platform.request("POST", f"/api/deployments/{deployment_id}/archive")
            if archive.status not in {200, 404, 409}:
                cleanup_failures.append(f"archive:{deployment_id}:http-{archive.status}")
                continue
            if archive.status != 404:
                try:
                    self.wait_until(
                        f"deployment archive {deployment_id}",
                        lambda deployment_id=deployment_id: self.platform.request(
                            "GET", "/api/deployments?includeArchived=true"
                        ),
                        lambda result, deployment_id=deployment_id: result.status == 200 and any(
                            item.get("id") == deployment_id and item.get("status") == "ARCHIVED"
                            for item in (result.body or [])
                            if isinstance(item, dict)
                        ),
                        timeout=180,
                        interval=5,
                    )
                except Exception:
                    cleanup_failures.append(f"archive-status:{deployment_id}")
                    continue
            deletion = self.platform.request(
                "DELETE",
                f"/api/deployments/{deployment_id}",
                {"hardDelete": True, "reason": "Hosted neutral external-provider canary cleanup."},
            )
            if deletion.status not in {200, 202, 204, 404}:
                cleanup_failures.append(f"delete:{deployment_id}:http-{deletion.status}")
                continue
            operation_id = deletion.body.get("id") if isinstance(deletion.body, dict) else None
            if operation_id:
                try:
                    self.wait_until(
                        f"deployment deletion {deployment_id}",
                        lambda operation_id=operation_id: require_status(
                            self.platform.request(
                                "GET", f"/api/platform/notifications/deployment-deletions/{operation_id}"
                            ),
                            {200},
                            "deployment deletion status",
                        ),
                        lambda item: item.get("status") == "SUCCEEDED",
                        timeout=600,
                        terminal=lambda item: item.get("status") == "FAILED",
                    )
                except Exception:
                    cleanup_failures.append(f"delete-operation:{deployment_id}")
                    continue
            try:
                self.wait_until(
                    f"provider resources absent for {deployment_id}",
                    lambda deployment_id=deployment_id: self.platform.request(
                        "GET", f"/api/deployment-provider/resources?deploymentId={deployment_id}&refresh=true"
                    ),
                    lambda result: result.status == 404 or (
                        result.status == 200 and not [
                            item for item in (result.body or [])
                            if item.get("status") not in {"DELETED", "RETIRED"}
                        ]
                    ),
                    timeout=300,
                    interval=8,
                )
            except Exception:
                cleanup_failures.append(f"provider-resources:{deployment_id}")
                continue
            deleted_deployments.append(deployment_id)
        self.deployments.clear()

        def present_secret_names() -> set[str]:
            secrets_state = self.platform.request("GET", "/api/platform/secrets")
            require_status(secrets_state, {200}, "managed secret cleanup readback")
            return {
                item.get("name") for item in (secrets_state.body or [])
                if isinstance(item, dict) and item.get("present") is True
            }

        try:
            present_names = self.wait_until(
                "deployment managed-secret cleanup",
                present_secret_names,
                lambda names: not any(name in names for name in self.managed_secrets),
                timeout=180,
                interval=5,
            )
        except Exception:
            present_names = present_secret_names()
        uncleared = [name for name in self.managed_secrets if name in present_names]
        for name in uncleared:
            self.platform.request("DELETE", f"/api/platform/secrets/managed/{urllib.parse.quote(name)}")
        if uncleared:
            cleanup_failures.append(f"managed-secrets-not-auto-cleared:{len(uncleared)}")
        self.managed_secrets.clear()

        self.evidence["cleanup"] = {
            "deletedDeploymentIds": deleted_deployments,
            "providerResourcesAbsent": not any(item.startswith("provider-resources:") for item in cleanup_failures),
            "managedSecretsAutoCleared": not bool(uncleared),
            "failures": cleanup_failures,
        }
        if require_complete and cleanup_failures:
            raise VerificationFailure(f"Hosted canary cleanup was incomplete: {', '.join(cleanup_failures)}")
        if require_complete:
            self.record("temporary deployments, provider resources, backups, and fixture credentials removed")


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keep-resources", action="store_true", default=env("KEEP_CANARY_RESOURCES", "false").lower() == "true")
    parser.add_argument("--skip-backup-restore", action="store_true", default=env("SKIP_CANARY_BACKUP_RESTORE", "false").lower() == "true")
    args = parser.parse_args()
    args.platform_url = env("PLATFORM_BASE_URL", required=True)
    args.platform_email = env("PLATFORM_LOGIN_EMAIL", required=True)
    args.platform_password = env("PLATFORM_LOGIN_PASSWORD", required=True)
    args.simulator_url = env("SIMULATOR_BASE_URL", required=True)
    args.simulator_control_key = env("SIMULATOR_CONTROL_API_KEY", required=True)
    args.fixture_version = env("SIMULATOR_FIXTURE_VERSION", "external-vehicle-provider-v1")
    args.profile_a_account = env("SIMULATOR_PROFILE_A_ACCOUNT_ID", required=True)
    args.profile_a_key = env("SIMULATOR_PROFILE_A_KEY", required=True)
    args.profile_a_secret = env("SIMULATOR_PROFILE_A_SECRET", required=True)
    args.profile_a_webhook_secret = env("SIMULATOR_PROFILE_A_WEBHOOK_SECRET", required=True)
    args.profile_b_account = env("SIMULATOR_PROFILE_B_ACCOUNT_ID", required=True)
    args.profile_b_api_key = env("SIMULATOR_PROFILE_B_API_KEY", required=True)
    args.profile_b_webhook_secret = env("SIMULATOR_PROFILE_B_WEBHOOK_SECRET", required=True)
    args.coolify_url = env("COOLIFY_BASE_URL")
    args.coolify_token = env("COOLIFY_API_TOKEN")
    if not args.skip_backup_restore:
        require(bool(args.coolify_url and args.coolify_token), "Coolify credentials are required unless --skip-backup-restore is explicit.")
    args.target_profile_id = env("DEPLOYMENT_TARGET_PROFILE_ID", "dtp-coolify-staging-behavior")
    args.source_artifact_id = env("DEPLOYMENT_SOURCE_ARTIFACT_ID")
    args.source_commit = env("EXPECTED_SOURCE_COMMIT", required=True)
    args.release_timeout = int(env("CANARY_RELEASE_TIMEOUT_SECONDS", "3600"))
    evidence_default = f"/tmp/loomai-hosted-generic-substrate-{int(time.time())}.json"
    args.evidence_file = Path(env("CANARY_EVIDENCE_FILE", evidence_default)).expanduser().resolve()
    return args


def main() -> int:
    args = arguments()
    canary = HostedCanary(args)
    try:
        canary.run()
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        print(f"Evidence: {args.evidence_file}", file=sys.stderr)
        return 1
    print(f"PASS: hosted generic external-provider substrate verification")
    print(f"Evidence: {args.evidence_file}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

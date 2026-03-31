#!/usr/bin/env python3
import argparse
import json
import os
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import HTTPCookieProcessor, Request, build_opener

import yaml


class PlatformApiClient:
    def __init__(self, base_url: str, email: str, password: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.email = email
        self.password = password
        self.opener = build_opener(HTTPCookieProcessor())

    def login(self) -> None:
        self.post_json(
            "/api/platform/auth/login",
            {
                "email": self.email,
                "password": self.password,
            },
        )

    def get_json(self, path: str):
        return self._request("GET", path, None)

    def post_json(self, path: str, payload=None):
        return self._request("POST", path, payload)

    def put_json(self, path: str, payload):
        return self._request("PUT", path, payload)

    def _request(self, method: str, path: str, payload):
        url = urljoin(self.base_url + "/", path.lstrip("/"))
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(
            url,
            data=body,
            headers={
                "Accept": "application/json",
                "Content-Type": "application/json",
            },
            method=method,
        )
        try:
            with self.opener.open(request, timeout=60) as response:
                data = response.read()
                if not data:
                    return None
                return json.loads(data.decode("utf-8"))
        except HTTPError as exc:
            payload_text = exc.read().decode("utf-8", errors="replace")
            try:
                payload_json = json.loads(payload_text)
                message = payload_json.get("message") or payload_json.get("error") or payload_json.get("errorCode") or payload_text
            except json.JSONDecodeError:
                message = payload_text
            raise RuntimeError(f"HTTP {exc.code} for {method} {url}: {message}") from exc
        except URLError as exc:
            raise RuntimeError(f"Request failed for {method} {url}: {exc.reason}") from exc


def parse_bool(value: str, flag_name: str) -> bool:
    normalized = value.strip().lower()
    if normalized in {"true", "1", "yes", "y", "on"}:
        return True
    if normalized in {"false", "0", "no", "n", "off"}:
        return False
    raise argparse.ArgumentTypeError(f"{flag_name} must be true or false.")


def ensure_absolute_http_url(value: str, label: str) -> None:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise RuntimeError(f"{label} must be an absolute http(s) URL: {value}")


def ensure_file(path: Path, label: str) -> None:
    if not path.is_file():
        raise RuntimeError(f"{label} not found: {path}")


def load_yaml_file(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def print_validation(validation: dict) -> None:
    print(
        f"Validation result: publishReady={validation['publishReady']} "
        f"errors={validation['errorCount']} warnings={validation['warningCount']}"
    )
    for issue in validation.get("issues", []):
        print(
            f"  - [{issue['severity']}] {issue['section']} {issue['code']} "
            f"at {issue['path']}: {issue['message']}"
        )


def stricter_apply_preflight(preflight: dict) -> None:
    failed = [check for check in preflight.get("checks", []) if check.get("status") == "FAILED"]
    if failed:
        summary = " | ".join(f"{check['key']}: {check['message']}" for check in failed)
        raise RuntimeError(f"Railway preflight failed: {summary}")

    artifact_probe = next(
        (
            check
            for check in preflight.get("checks", [])
            if check.get("key") == "public_base_url_probe"
        ),
        None,
    )
    if artifact_probe is None or artifact_probe.get("status") != "PASSED":
        raise RuntimeError(
            "Refusing --apply because platform public artifact delivery is not proven reachable. "
            "Check PLATFORM_PUBLIC_BASE_URL and the /api/platform/overview probe."
        )


def normalize_entity_config(entity_config: dict, vector_dimensions: int) -> dict:
    root = dict(entity_config or {})
    ai_config = dict(root.get("ai-config") or {})
    if int(ai_config.get("vector-dimensions") or 0) <= 0:
        ai_config["vector-dimensions"] = vector_dimensions
    root["ai-config"] = ai_config
    root.setdefault("ai-entities", {})
    return root


def normalize_routing_config(routing_config: dict, args) -> dict:
    root = dict(routing_config or {})
    connector = dict(root.get("connector") or {})
    inbound_auth = dict(connector.get("inbound-auth") or {})
    inbound_auth["allow-unauthenticated"] = args.connector_allow_unauthenticated
    api_key = dict(inbound_auth.get("api-key") or {})
    api_key["enabled"] = args.connector_api_key_enabled
    api_key["header"] = args.connector_api_key_header
    api_key["value"] = args.connector_api_key_value
    inbound_auth["api-key"] = api_key
    connector["inbound-auth"] = inbound_auth

    upstream = dict(connector.get("upstream") or {})
    upstream["base-url"] = args.upstream_base_url
    upstream_auth = dict(upstream.get("auth") or {})
    upstream_auth.setdefault("type", "NONE")
    upstream_auth.setdefault("header", "Authorization")
    upstream_auth.setdefault("value", "")
    upstream["auth"] = upstream_auth
    connector["upstream"] = upstream
    root["connector"] = connector

    authz = dict(root.get("authz") or {})
    authz["enabled"] = args.authz_enabled
    authz["path"] = args.authz_path
    authz_upstream = dict(authz.get("upstream") or {})
    authz_upstream["base-url"] = args.authz_upstream_base_url
    authz_upstream_auth = dict(authz_upstream.get("auth") or {})
    authz_upstream_auth.setdefault("type", "NONE")
    authz_upstream_auth.setdefault("header", "Authorization")
    authz_upstream_auth.setdefault("value", "")
    authz_upstream["auth"] = authz_upstream_auth
    authz["upstream"] = authz_upstream
    root["authz"] = authz

    root.setdefault("actions", {})
    return root


def normalize_security_config(existing_security: dict, args) -> dict:
    security = dict(existing_security or {})
    security.setdefault("authzMode", "REMOTE_HTTP")
    security.setdefault("adminApiKeyEnabled", True)
    security["connectorApiKeyEnabled"] = args.connector_api_key_enabled
    if args.authz_enabled:
        security["authzBaseUrl"] = args.authz_upstream_base_url
    return security


def release_terminal(release: dict) -> bool:
    if release.get("status") in {"APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED", "FAILED"}:
        return True
    if release.get("verificationStatus") == "FAILED":
        return True
    if release.get("provisioningStatus") == "FAILED":
        return True
    return False


def default_repo_root() -> Path:
    return Path(__file__).resolve().parents[3]


def build_arg_parser() -> argparse.ArgumentParser:
    repo_root = default_repo_root()
    parser = argparse.ArgumentParser(
        description="Create a new platform deployment from the ecommerce demo YAML config."
    )
    parser.add_argument(
        "--platform-base-url",
        default=os.getenv("PLATFORM_BASE_URL", "http://localhost:8088"),
    )
    parser.add_argument(
        "--email",
        default=os.getenv("PLATFORM_EMAIL", "admin@example.com"),
    )
    parser.add_argument(
        "--password",
        default=os.getenv("PLATFORM_PASSWORD", "AdminPass123"),
    )
    parser.add_argument(
        "--name",
        default=os.getenv("DEPLOYMENT_NAME") or f"Ecommerce Demo Restored {time.strftime('%Y%m%d-%H%M%S', time.gmtime())}",
    )
    parser.add_argument(
        "--environment",
        default=os.getenv("DEPLOYMENT_ENVIRONMENT", "dev"),
    )
    parser.add_argument(
        "--template-id",
        default=os.getenv("DEPLOYMENT_TEMPLATE_ID", "dev-openai-lucene"),
    )
    parser.add_argument(
        "--actions-file",
        type=Path,
        default=Path(
            os.getenv(
                "DEMO_ACTIONS_FILE",
                str(repo_root / "Real_Apps/ecommerce-store/deploy/runtime/config/ai-actions.yml"),
            )
        ),
    )
    parser.add_argument(
        "--entities-file",
        type=Path,
        default=Path(
            os.getenv(
                "DEMO_ENTITIES_FILE",
                str(repo_root / "Real_Apps/ecommerce-store/deploy/runtime/config/ai-entity-config.yml"),
            )
        ),
    )
    parser.add_argument(
        "--routing-file",
        type=Path,
        default=Path(
            os.getenv(
                "DEMO_ROUTING_FILE",
                str(repo_root / "Real_Apps/ecommerce-store/deploy/rest-connector/actions-routing.yml"),
            )
        ),
    )
    parser.add_argument(
        "--vector-dimensions",
        type=int,
        default=int(os.getenv("DEMO_VECTOR_DIMENSIONS", "512")),
    )
    parser.add_argument(
        "--upstream-base-url",
        default=os.getenv(
            "DEMO_UPSTREAM_BASE_URL",
            "https://ai-fabric-framework-production-a247.up.railway.app",
        ),
    )
    parser.add_argument(
        "--authz-upstream-base-url",
        default=os.getenv("DEMO_AUTHZ_UPSTREAM_BASE_URL"),
    )
    parser.add_argument(
        "--authz-path",
        default=os.getenv("DEMO_AUTHZ_PATH", "/api/authz/check"),
    )
    parser.add_argument(
        "--authz-enabled",
        type=lambda value: parse_bool(value, "--authz-enabled"),
        default=parse_bool(os.getenv("DEMO_AUTHZ_ENABLED", "true"), "DEMO_AUTHZ_ENABLED"),
    )
    parser.add_argument(
        "--connector-allow-unauthenticated",
        type=lambda value: parse_bool(value, "--connector-allow-unauthenticated"),
        default=parse_bool(
            os.getenv("DEMO_CONNECTOR_ALLOW_UNAUTHENTICATED", "false"),
            "DEMO_CONNECTOR_ALLOW_UNAUTHENTICATED",
        ),
    )
    parser.add_argument(
        "--connector-api-key-enabled",
        type=lambda value: parse_bool(value, "--connector-api-key-enabled"),
        default=parse_bool(
            os.getenv("DEMO_CONNECTOR_API_KEY_ENABLED", "true"),
            "DEMO_CONNECTOR_API_KEY_ENABLED",
        ),
    )
    parser.add_argument(
        "--connector-api-key-header",
        default=os.getenv("DEMO_CONNECTOR_API_KEY_HEADER", "X-AIFABRIC-API-KEY"),
    )
    parser.add_argument(
        "--connector-api-key-value",
        default=os.getenv("DEMO_CONNECTOR_API_KEY_VALUE", "${CONNECTOR_API_KEY}"),
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        default=parse_bool(os.getenv("DEMO_APPLY", "false"), "DEMO_APPLY"),
    )
    parser.add_argument(
        "--no-apply",
        dest="apply",
        action="store_false",
    )
    parser.add_argument(
        "--wait-timeout-seconds",
        type=int,
        default=int(os.getenv("DEMO_APPLY_TIMEOUT_SECONDS", "600")),
    )
    parser.add_argument(
        "--poll-interval-seconds",
        type=int,
        default=int(os.getenv("DEMO_APPLY_POLL_SECONDS", "5")),
    )
    return parser


def main() -> int:
    parser = build_arg_parser()
    args = parser.parse_args()

    args.authz_upstream_base_url = args.authz_upstream_base_url or args.upstream_base_url

    ensure_file(args.actions_file, "Actions file")
    ensure_file(args.entities_file, "Entities file")
    ensure_file(args.routing_file, "Routing file")
    ensure_absolute_http_url(args.platform_base_url, "Platform base URL")
    ensure_absolute_http_url(args.upstream_base_url, "Connector upstream base URL")
    if args.authz_enabled:
        ensure_absolute_http_url(args.authz_upstream_base_url, "Authz upstream base URL")

    client = PlatformApiClient(args.platform_base_url, args.email, args.password)
    print(f"Logging into platform at {args.platform_base_url} as {args.email}...")
    client.login()

    if args.apply:
        print("Checking Railway preflight before apply...")
        preflight = client.get_json("/api/platform/provisioning/railway/preflight")
        stricter_apply_preflight(preflight)
        print("Railway preflight passed with artifact probe readiness.")

    actions_config = load_yaml_file(args.actions_file)
    entity_config = normalize_entity_config(load_yaml_file(args.entities_file), args.vector_dimensions)
    routing_config = normalize_routing_config(load_yaml_file(args.routing_file), args)

    print(f"Creating deployment '{args.name}'...")
    deployment = client.post_json(
        "/api/deployments",
        {
            "name": args.name,
            "environment": args.environment,
            "templateId": args.template_id,
        },
    )
    deployment_id = deployment["id"]

    draft = client.get_json(f"/api/deployments/{deployment_id}/draft")
    draft_id = draft["id"]
    security_config = normalize_security_config(draft.get("securityConfig"), args)
    provider_config = draft.get("providerConfig")

    print(f"Updating draft {draft_id} with ecommerce demo config...")
    client.put_json(
        f"/api/deployment-drafts/{draft_id}",
        {
            "actionsConfig": actions_config,
            "entityConfig": entity_config,
            "routingConfig": routing_config,
            "providerConfig": provider_config,
            "securityConfig": security_config,
        },
    )

    validation = client.post_json(f"/api/deployment-drafts/{draft_id}/validate")
    print_validation(validation)
    if int(validation["errorCount"]) > 0:
        raise RuntimeError("Draft validation failed. Fix the reported issues before publishing.")

    version = client.post_json(f"/api/deployment-drafts/{draft_id}/publish")
    version_id = version["id"]
    print(f"Published {version['versionLabel']} ({version_id}).")

    plan = client.get_json(f"/api/deployments/{deployment_id}/versions/{version_id}/railway-plan")
    print(f"Plan mode: {plan['mode']}")
    print(
        f"Runtime service: {plan['services']['runtime']['serviceName']} -> {plan['services']['runtime']['baseUrl']}"
    )
    print(
        f"REST connector service: {plan['services']['restConnector']['serviceName']} -> "
        f"{plan['services']['restConnector']['baseUrl']}"
    )

    if not args.apply:
        print("Apply skipped. Re-run with --apply when PLATFORM_PUBLIC_BASE_URL is real and Railway provisioning is ready.")
        print(f"Deployment: {deployment_id}")
        print(f"Draft: {draft_id}")
        print(f"Version: {version_id}")
        return 0

    release = client.post_json(f"/api/deployments/{deployment_id}/apply/{version_id}")
    release_id = release["id"]
    print(f"Apply requested: {release_id}")

    deadline = time.time() + args.wait_timeout_seconds
    while True:
        releases = client.get_json(f"/api/deployments/{deployment_id}/releases")
        current = next((release_item for release_item in releases if release_item["id"] == release_id), None)
        if current is None:
            raise RuntimeError(f"Release disappeared while polling: {release_id}")

        print(
            f"Release {release_id}: status={current['status']} "
            f"provisioning={current['provisioningStatus']} "
            f"verification={current['verificationStatus']} "
            f"step={current['currentStepKey']}"
        )

        if release_terminal(current):
            if current["status"] == "APPLIED_VERIFIED":
                print("Deployment applied and verified.")
                return 0

            print(f"Release ended in failure state: {current['status']}", file=sys.stderr)
            if current.get("currentStepDescription"):
                print(f"Current step: {current['currentStepDescription']}", file=sys.stderr)
            if current.get("errorMessage"):
                print(f"Error: {current['errorMessage']}", file=sys.stderr)
            print("Check logs with:", file=sys.stderr)
            print(
                f"  GET /api/deployments/{deployment_id}/railway-logs?releaseId={release_id}&service=runtime&source=deployment",
                file=sys.stderr,
            )
            print(
                f"  GET /api/deployments/{deployment_id}/railway-logs?releaseId={release_id}&service=restConnector&source=deployment",
                file=sys.stderr,
            )
            return 1

        if time.time() >= deadline:
            raise RuntimeError(
                f"Timed out waiting for release {release_id} after {args.wait_timeout_seconds} seconds."
            )

        time.sleep(args.poll_interval_seconds)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)

# ProdUS LoomAI Staging Deployment Dev Guide

Status: current staging guide, last updated 2026-06-01.

This guide records the commands, tools, scripts, and operational checks used to create, configure, redeploy, and verify the ProdUS LoomAI staging deployment.

Do not paste raw secrets into this file. Runtime API keys, HMAC signing secrets, Platform API keys, Coolify API tokens, and MCP API keys must stay in private secret stores or local `0600` temp files.

The current raw ProdUS staging auth material is recorded only in `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md` under `2026-05-20 ProdUS Direct Runtime Auth Material (Private)`.

## 1. Staging Deployment Summary

| Field | Value |
| --- | --- |
| Platform deployment id | `dep-7706fafb` |
| Platform deployment name | `ProdUS AI Enablement Staging` |
| Stable consumer/customer id | `produs-staging` |
| Runtime base URL | `http://dep-7706fafb.46.224.145.148.sslip.io` |
| Runtime template | `dev-openai-qdrant` |
| Runtime curated module | `default` |
| Runtime supported modes | `thinker` for analysis/read-only help, `executor` for governed actions |
| Active version | `ver-e55296b1` |
| Latest applied release | `rel-2d0807c7` |
| Runtime Coolify app | `runtime-dep-7706fafb` / `m14c2kdq3qsc2hnofr84wge2` |
| Connector Coolify app | `rest-connector-dep-7706fafb` / `f8v02rd1luusupszsnbrny7i` |
| Vectorization runner Coolify app | `vectorization-runner-dep-7706fafb` / `fm2pdlbk55tjx6gmh4xqo9t7` |
| ProdUS backend app | `produs-backend-staging` / `jk3n39yatabf8zc9sn5nknj9` |
| ProdUS frontend app | `produs-frontend-staging` / `wfvdve1ezt7vixejye4bhrgl` |
| Target integration | `BACKEND_MEDIATED_PRIVATE_RUNTIME` |
| Runtime auth mode | `PRIVATE_RUNTIME_ASSERTION` |

Runtime direct private path is verified. ProdUS MCP API-key auth is enabled on staging; unauthenticated `/mcp` calls fail closed and authenticated calls return the LoomAI productization tools. The read-only ProdUS MCP Marketplace action bundle and the confirmed `produs.productization_project.create` action bundle are published, installed, applied, and visible in runtime actions overview.

Catalog export update on 2026-06-01:

- ProdUS MCP discovery for `produs-staging` returned `ready=true` with 19 tools after ProdUS added `produs.catalog.export`.
- LoomAI imported and published `mkp-action-produs-productization-read-mcp@0.1.1`.
- Deployment install `mpi-6a4605e4` was updated from `0.1.0` to `0.1.1`.
- Deployment version `ver-37ca6cc2` / label `v10` was published and applied through release `rel-68c38e15`.
- Verification `vrf-55a0bfc1` passed with `28 passed, 0 failed, 1 skipped`.
- Runtime action catalog now has 10 ProdUS actions and includes `produs_catalog_export`.
- Explicit runtime smoke through `/api/chat/me/query-once` with `mode=thinker` executed `produs_catalog_export` and returned a grounded answer.

Stable private-runtime audience update on 2026-06-01:

- Deployment version `ver-e55296b1` / label `v11` was published and applied through release `rel-2d0807c7`.
- Release `rel-2d0807c7` finished `APPLIED_VERIFIED`; latest verification `vrf-7b9ffb3d` passed.
- Runtime assignment discovery now returns `privateRuntimeIssuer=produs-staging-backend`, `privateRuntimeAudience=produs-staging`, `privateRuntimeAudienceMode=CONSUMER_ID`, and `externalIntegrationReady=true`.
- ProdUS should sign private runtime assertions with `aud=produs-staging`. Keep `deploymentId=dep-7706fafb` in the assertion payload as audit/debug metadata.
- Runtime still accepts `dep-7706fafb` as a transition audience so existing staging clients are not broken during rollout, but new ProdUS integration code should not depend on the deployment id for `aud`.
- Live smoke verified `GET /api/chat/me/auth-context` and `POST /api/chat/me/query-once` using issuer `produs-staging-backend` and audience `produs-staging`.

Active runtime assignment discovery:

- ProdUS backend can discover the currently assigned runtime with `GET /api/public/consumers/produs-staging/runtime-assignment`.
- Use returned `endpoints.chatQueryUrl`, `endpoints.queryOnceUrl`, `endpoints.suggestionsUrl`, `endpoints.authContextUrl`, and `cacheTtlSeconds` instead of hardcoding the runtime URL in application code.
- Treat `deploymentId` as audit metadata, not as the route source of truth.
- Current assignment returns `privateRuntimeAudience=produs-staging` and `externalIntegrationReady=true`; sign direct private runtime assertions with `aud=produs-staging`.

Managed ProdUS safe-knowledge vectorization is also live. The runtime prompt artifact sets `ragSimilarityThreshold=0.2`, `ragMaxDocumentsUsedForContext=8`, and `ragMaxContextChars=7000` for this deployment so retrieved ProdUS catalog records ground answers reliably.

Default curated runtime pack status on 2026-05-31:

- Deployment version `ver-b0c54807` was published and applied through release `rel-37d07c7c`.
- Release `rel-37d07c7c` finished `APPLIED_VERIFIED` with verification `PASSED` on target profile `dtp-coolify-staging`.
- Live smoke confirmed `/api/chat/me/query` and `/api/chat/me/query-once` both accept `mode=thinker` and echo `mode=thinker`.
- Live smoke confirmed `/api/chat/me/query` still runs retrieval and returned a `rag-*` provider request id.

Runtime code deployment `jz8ntc2b03kmllnpfn43esa7` deployed commit `22fa7fb48` on 2026-05-22 for the implementation smoke. Follow-up deployment `kpx28b02ryukztitqvem2399` deployed commit `969f87dfb` after the status documentation update. Live smoke verified `/api/chat/me/query-once` returns a one-time answer without creating a conversation record, while `/api/chat/me/query` still creates the expected persisted conversation.

Transient provider file URL input support is implemented in LoomAI runtime/core/provider modules. ProdUS project creation should send owner-approved selected documents as `context.documents[].temporaryAccessUrl` to `/api/chat/me/query-once`; runtime redacts the URL, does not persist or index file content, and requires `documentUsage` evidence from the selected provider. See `Final_Documentation/Development_Guides/RUNTIME_TRANSIENT_PROVIDER_FILE_URL_INPUTS_GUIDE.md`.

Confirmed project creation action status on 2026-05-25:

- Plugin: `mkp-action-produs-productization-project-create-mcp@0.1.1`.
- Install id: `mpi-47247a04`.
- Deployment version: `ver-f9069ce5`.
- Applied release: `rel-623c91a0`, status `APPLIED_VERIFIED`, verification `PASSED`.
- Runtime action: `produs_productization_project_create`.
- MCP tool: `produs.productization_project.create`.
- Schema hash: `sha256:6a64c636165a0e6c92e7fefd41fad8e53132f411f2aa7d107a992c6e517867c0`.
- Negative live execution proof reached ProdUS MCP and failed closed with `Project creation intent not found`; schema drift was `OK`.
- Positive creation proof still requires a real owner-approved ProdUS `runtimeActionPayload` from `POST /api/products/ai-assisted/analyze`.

## 2. Tools Used

Local tools:

```bash
curl
jq
python3
rg
sed
git
```

Tracked scripts and references:

```text
scripts/verify-coolify-provider.sh
scripts/verify-marketplace-install-flow.sh
scripts/run-platform-deployment-verification.sh
scripts/verify-vector-deployment.sh
Final_Documentation/Development_Guides/COOLIFY_HETZNER_ADMINISTRATION_GUIDE.md
Final_Documentation/Development_Guides/PRIVATE_RUNTIME_CUSTOMER_INTEGRATION_GUIDE.md
Final_Documentation/Development_Guides/RUNTIME_TRANSIENT_PROVIDER_FILE_URL_INPUTS_GUIDE.md
doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_5_LOOMAI_CANONICAL_RUNTIME_BRIDGE_CONTRACT_STANDARDIZATION_PLAN.md
/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md
/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/planning/Scanners-AI-integration/LOOMAI_STAGING_DIRECT_RUNTIME_REQUEST.md
```

Local private material expected by the commands:

```text
/tmp/coolify_api_tokens.env
```

The token file is expected to define `COOLIFY_STAGING_BASE_URL` and `COOLIFY_STAGING_API_TOKEN`. Keep it mode `0600`.

## 3. Platform Deployment Creation Flow

The deployment is a normal Platform deployment using the existing template system. Use this flow to recreate the deployment from Platform API if needed.

Create or confirm Platform deployment:

```bash
PLATFORM_BASE_URL="https://loomai-platform-backend.46.224.145.148.sslip.io"
PLATFORM_API_KEY="<platform-operator-or-admin-api-key>"

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/deployments" \
  --data '{
    "name": "ProdUS AI Enablement Staging",
    "environment": "staging",
    "templateId": "dev-openai-qdrant",
    "curatedModuleId": "default",
    "vectorProvisioningMode": "MANAGED_CLOUD_CLUSTER",
    "customerId": "produs-staging"
  }'
```

Read the draft:

```bash
curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/draft" \
  | jq '{id, deploymentId, status}'
```

Publish the draft:

```bash
DRAFT_ID="<draft-id>"

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/deployment-drafts/${DRAFT_ID}/publish" \
  --data '{}'
```

Apply a version through the Coolify target profile:

```bash
VERSION_ID="<published-version-id>"

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/apply/${VERSION_ID}" \
  --data '{"targetProfileId":"dtp-coolify-staging"}'
```

Monitor release and hosted verification:

```bash
curl -fsS -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/releases" | jq '.[0]'

curl -fsS -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/verification-runs" | jq '.[0]'
```

## 4. Coolify App Lookup

Load Coolify credentials:

```bash
set -euo pipefail
source /tmp/coolify_api_tokens.env
COOLIFY_BASE_URL="${COOLIFY_STAGING_BASE_URL:-http://46.224.145.148:8000}"
```

List related apps:

```bash
curl -fsS \
  -H "Authorization: Bearer ${COOLIFY_STAGING_API_TOKEN}" \
  -H "Accept: application/json" \
  "${COOLIFY_BASE_URL}/api/v1/applications" \
  | jq -r '.[]? | [.uuid, .name, (.fqdn // .domains // ""), (.git_branch // ""), (.status // "")] | @tsv' \
  | rg 'dep-7706fafb|ProdUS|produs|7706fafb'
```

Expected relevant runtime row:

```text
m14c2kdq3qsc2hnofr84wge2  runtime-dep-7706fafb  http://dep-7706fafb.46.224.145.148.sslip.io  Platform-V10  running:healthy
```

## 5. Runtime Env Configuration

The runtime app must have these values. Secret values must already exist in Coolify or Platform-managed secret resolution and must never be printed.

```bash
APP_UUID="m14c2kdq3qsc2hnofr84wge2"

env_json="$(curl -fsS \
  -H "Authorization: Bearer ${COOLIFY_STAGING_API_TOKEN}" \
  -H "Accept: application/json" \
  "${COOLIFY_BASE_URL}/api/v1/applications/${APP_UUID}/envs")"

printf '%s' "${env_json}" | jq -r '
  [.[] | select((.key=="AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY"
    or .key=="AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"
    or .key=="AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS"
    or .key=="AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES"
    or .key=="AI_FABRIC_RUNTIME_TRANSIENT_FILE_URL_ALLOWED_HOSTS") and (.is_preview|not)) |
    {
      key,
      length: ((.value // "")|length),
      valuePresent: ((.value // "")|length > 0),
      hasProdusIssuer: ((.value // "")|contains("produs-staging-backend")),
      hasStableProdusAudience: ((.value // "")|contains("produs-staging")),
      hasTransitionDeploymentAudience: ((.value // "")|contains("dep-7706fafb"))
    }
  ]'
```

Patch non-secret runtime env rows. For the ProdUS deployment, keep `produs-staging-backend` as the external integration issuer and avoid reintroducing `platform-consumer-bridge` as an accepted issuer, because assignment discovery chooses the preferred issuer from this runtime configuration.

```bash
patch_json="$(printf '%s' "${env_json}" | jq -c '
  def envmap: map({(.key): (.value // "")}) | add;
  def csv_add($raw; $item):
    (($raw // "") | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0)) + [$item] | unique | join(","));
  def csv_remove($raw; $item):
    (($raw // "") | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0 and . != $item)) | unique | join(","));
  envmap as $e |
  {data: [
    {key:"AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE", value:"VERIFIED_CONTEXT_REQUIRED", is_preview:false, is_literal:true, is_multiline:false, is_shown_once:false},
    {key:"AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER", value:"X-AIFABRIC-RUNTIME-API-KEY", is_preview:false, is_literal:true, is_multiline:false, is_shown_once:false},
    {key:"AI_FABRIC_RUNTIME_PRIVATE_AUTHORIZATION_HEADER", value:"X-AIFABRIC-RUNTIME-AUTHORIZATION", is_preview:false, is_literal:true, is_multiline:false, is_shown_once:false},
    {key:"AI_FABRIC_RUNTIME_PRIVATE_TOKEN_SCHEME", value:"Bearer", is_preview:false, is_literal:true, is_multiline:false, is_shown_once:false},
    {key:"AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS", value:csv_add(csv_remove($e.AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS; "platform-consumer-bridge"); "produs-staging-backend"), is_preview:false, is_literal:true, is_multiline:false, is_shown_once:false},
    {key:"AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES", value:csv_add(csv_add($e.AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES; "produs-staging"); "dep-7706fafb"), is_preview:false, is_literal:true, is_multiline:false, is_shown_once:false},
    {key:"AI_FABRIC_RUNTIME_TRANSIENT_FILE_URL_ALLOWED_HOSTS", value:"produs-api-staging.46.224.145.148.sslip.io", is_preview:false, is_literal:true, is_multiline:false, is_shown_once:false}
  ]}
')"

curl -fsS \
  -X PATCH \
  -H "Authorization: Bearer ${COOLIFY_STAGING_API_TOKEN}" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  --data "${patch_json}" \
  "${COOLIFY_BASE_URL}/api/v1/applications/${APP_UUID}/envs/bulk" >/dev/null
```

Trigger a real redeploy and capture the deployment UUID:

```bash
deployment_json="$(curl -fsS \
  -H "Authorization: Bearer ${COOLIFY_STAGING_API_TOKEN}" \
  -H "Accept: application/json" \
  "${COOLIFY_BASE_URL}/api/v1/applications/${APP_UUID}/start?force=true&instant_deploy=true")"

DEPLOYMENT_UUID="$(printf '%s' "${deployment_json}" | jq -r '.deployment_uuid')"
printf 'deployment queued: %s\n' "${DEPLOYMENT_UUID}"
```

Poll deployment completion and runtime health:

```bash
for i in $(seq 1 120); do
  dep_json="$(curl -fsS \
    -H "Authorization: Bearer ${COOLIFY_STAGING_API_TOKEN}" \
    -H "Accept: application/json" \
    "${COOLIFY_BASE_URL}/api/v1/deployments/${DEPLOYMENT_UUID}")"
  dep_status="$(printf '%s' "${dep_json}" | jq -r '.status // ""')"
  finished_at="$(printf '%s' "${dep_json}" | jq -r '.finished_at // ""')"
  health="$(curl -fsS --max-time 4 \
    http://dep-7706fafb.46.224.145.148.sslip.io/actuator/health 2>/dev/null \
    | jq -r '.status // empty' 2>/dev/null || true)"

  printf 'poll=%s deployment=%s finished=%s health=%s\n' \
    "$i" "$dep_status" "${finished_at:-no}" "${health:-pending}"

  if [[ "$dep_status" == "finished" && "$health" == "UP" ]]; then
    break
  fi
  if [[ "$dep_status" == "failed" || "$dep_status" == "cancelled" ]]; then
    exit 1
  fi
  sleep 5
done
```

## 6. Private Runtime Assertion Contract

ProdUS must call runtime with:

```http
X-AIFABRIC-RUNTIME-API-KEY: <deployment-scoped-runtime-api-key>
X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer rpa1.<base64url-json-payload>.<base64url-hmac-sha256-signature>
```

Confirmed implementation details:

- Base64url encoding is unpadded.
- HMAC input is exactly the base64url payload segment.
- Signature algorithm is HMAC-SHA256.
- `exp` must be an ISO-8601 UTC instant string accepted by `Instant.parse`, for example `2026-05-20T12:00:00Z`.
- Numeric epoch seconds are not accepted by the current runtime.
- Current runtime has no explicit clock-skew grace window; token expiry must be greater than the runtime clock.
- `subjectType=ANONYMOUS_SESSION` requires `sub == sessionId`.
- Missing `chat:query` rejects query with `403`.
- Missing `chat:query` also rejects one-time query with `403`.
- Missing `chat:suggestions` rejects suggestions with `403`.
- Supplying both public `Authorization` and private `X-AIFABRIC-RUNTIME-AUTHORIZATION` is rejected with `400`.

Generate a smoke token locally without printing secrets:

```bash
env_json="$(curl -fsS \
  -H "Authorization: Bearer ${COOLIFY_STAGING_API_TOKEN}" \
  -H "Accept: application/json" \
  "${COOLIFY_BASE_URL}/api/v1/applications/${APP_UUID}/envs")"

export LOOMAI_RUNTIME_API_KEY="$(printf '%s' "${env_json}" \
  | jq -r '.[] | select(.key=="AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY" and (.is_preview|not)) | .value' | tail -1)"

export LOOMAI_ASSERTION_SIGNING_SECRET="$(printf '%s' "${env_json}" \
  | jq -r '.[] | select(.key=="AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY" and (.is_preview|not)) | .value' | tail -1)"

LOOMAI_PRIVATE_ASSERTION="$(python3 - <<'PY'
import base64, hashlib, hmac, json, os
from datetime import datetime, timezone, timedelta

payload = {
    "sub": "produs-smoke-user",
    "subjectType": "END_USER",
    "authMode": "PRIVATE_RUNTIME_BACKEND_MEDIATED",
    "callerType": "TRUSTED_BACKEND",
    "sessionId": "produs-smoke-session",
    "deploymentId": "dep-7706fafb",
    "customerId": "produs-staging",
    "tenantId": "produs-smoke-tenant",
    "iss": "produs-staging-backend",
    "aud": "produs-staging",
    "exp": (datetime.now(timezone.utc) + timedelta(minutes=10)).isoformat().replace("+00:00", "Z"),
    "scopes": ["chat:query", "chat:suggestions", "chat:conversations"],
}

def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")

payload_segment = b64(json.dumps(payload, separators=(",", ":")).encode())
signature = hmac.new(
    os.environ["LOOMAI_ASSERTION_SIGNING_SECRET"].encode(),
    payload_segment.encode(),
    hashlib.sha256,
).digest()
print("rpa1." + payload_segment + "." + b64(signature))
PY
)"
```

## 7. Direct Runtime Smoke Commands

Auth context:

```bash
curl -fsS \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${LOOMAI_RUNTIME_API_KEY}" \
  -H "X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ${LOOMAI_PRIVATE_ASSERTION}" \
  "http://dep-7706fafb.46.224.145.148.sslip.io/api/chat/me/auth-context" \
  | jq '{subjectId, subjectType, authMode, callerType, sessionId, deploymentId, customerId, tenantId, issuer, audiences, grantedScopes}'
```

Expected field names are `subjectId`, `issuer`, `audiences`, and `grantedScopes`.

Query:

```bash
curl -fsS \
  -H "Content-Type: application/json" \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${LOOMAI_RUNTIME_API_KEY}" \
  -H "X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ${LOOMAI_PRIVATE_ASSERTION}" \
  -X POST \
  "http://dep-7706fafb.46.224.145.148.sslip.io/api/chat/me/query" \
  --data '{
    "query": "What can you help me with for productization?",
    "conversationId": "produs-direct-runtime-smoke",
    "mode": "thinker",
    "position": "productization",
    "context": {
      "pageType": "owner-product-workspace",
      "actorRole": "PRODUCT_OWNER",
      "productStage": "PROTOTYPE"
    }
  }' \
  | jq '{success,type,conversationId,mode,position,hasAnswer:(.answer|type=="string" and length>0),providerRequestId, actionsType:(.actions|type), sourcesType:(.sources|type), suggestionsType:(.suggestions|type)}'
```

One-time query:

Use this endpoint for page helpers, inline analysis, smoke checks, and any answer that must not create conversation history. Request and response shape match `/api/chat/me/query`; it uses the same `chat:query` scope.

```bash
curl -fsS \
  -H "Content-Type: application/json" \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${LOOMAI_RUNTIME_API_KEY}" \
  -H "X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ${LOOMAI_PRIVATE_ASSERTION}" \
  -X POST \
  "http://dep-7706fafb.46.224.145.148.sslip.io/api/chat/me/query-once" \
  --data '{
    "query": "Which package template is appropriate for launch readiness?",
    "conversationId": "produs-direct-runtime-query-once-smoke",
    "mode": "thinker",
    "position": "productization",
    "context": {
      "pageType": "owner-product-workspace",
      "actorRole": "PRODUCT_OWNER",
      "productStage": "PROTOTYPE"
    }
  }' \
  | jq '{success,type,conversationId,mode,position,hasAnswer:(.answer|type=="string" and length>0),providerRequestId, actionsType:(.actions|type), sourcesType:(.sources|type), suggestionsType:(.suggestions|type)}'
```

Suggestions:

```bash
curl -fsS \
  -H "Content-Type: application/json" \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${LOOMAI_RUNTIME_API_KEY}" \
  -H "X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ${LOOMAI_PRIVATE_ASSERTION}" \
  -X POST \
  "http://dep-7706fafb.46.224.145.148.sslip.io/api/chat/me/suggestions" \
  --data '{
    "content": "Owner is reviewing product launch blockers",
    "maxSuggestions": 4
  }' \
  | jq '{success, count:(.suggestions|length), suggestionsType:(.suggestions|type)}'
```

Negative auth checks:

```bash
curl -sS -o /tmp/produs-missing-key-response.json -w '%{http_code}\n' \
  -H "X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ${LOOMAI_PRIVATE_ASSERTION}" \
  "http://dep-7706fafb.46.224.145.148.sslip.io/api/chat/me/auth-context"
```

Expected: `401`.

## 8. Runtime API Contract Answers For ProdUS

Chat query:

- `mode=thinker` is the default read-only/analysis path and should be echoed back after the deployment is applied with the `default` curated module.
- `mode=executor` is available for governed action execution. Use it only when the UX is intentionally action-capable.
- Do not use `support_assistant`, `support_deep`, or `support_operator` for ProdUS. Those modes belong to the support curated pack and are not the default-pack contract.
- `position=productization` is accepted and echoed back; it is useful as a routing/context signal.
- Use `/api/chat/me/query` for chat panels with stable conversation history.
- Use `/api/chat/me/query-once` for one-time answers. Runtime treats `conversationId` as correlation only on this endpoint, skips persisted chat-memory loading, and skips conversation turn recording. Do not send a `persistConversation` flag; choose the endpoint by UX intent.
- Product-specific context belongs under canonical `context`.
- Do not send top-level `userId`, `ownerId`, `tenantId`, `sessionId`, or `storefrontContext`; identity comes from the private assertion.
- `providerRequestId` is the canonical response field for trace correlation.
- Prefer `answer` for display and keep `safeSummary` as equivalent/fallback safe copy.

Action/error evidence:

- Runtime preserves `actions[].errorCode` and `actions[].actionResult.errorCode` when action/tool failures carry machine codes.
- UI branches must use `errorCode`, `fallbackReason`, or structured action evidence, not English text matching.

Current `type` values from the runtime orchestration enum:

```text
ACTION_EXECUTED
ACTION_DENIED
INFORMATION_PROVIDED
CONFIRMATION_REQUIRED
CLARIFICATION_REQUIRED
OUT_OF_SCOPE
COMPOUND_HANDLED
ERROR
```

Suggestions:

- Current runtime `SuggestionsRequest` supports `content`, `attachments`, and `maxSuggestions`.
- It does not currently accept `conversationId` or `context` on `/api/chat/me/suggestions`; unexpected fields are rejected.
- If ProdUS wants suggestion context, include safe concise text in `content` or add safe attachments.

Auth context:

- Endpoint is live at `/api/chat/me/auth-context`.
- Response uses `subjectId`, `issuer`, `audiences`, and `grantedScopes`.

Rate limits and retries:

- No explicit runtime rate-limit headers are currently emitted by this deployment.
- Recommended ProdUS backend timeout: `8000ms`.
- Recommended retry policy: no retry for auth/client errors; one short retry for network/`5xx` failures on read-only query/suggestions; never retry side-effecting confirmed actions without idempotency.

## 9. MCP Discovery Commands

Check ProdUS backend:

```bash
curl -fsS https://produs-api-staging.46.224.145.148.sslip.io/health | jq .
```

Check allowlist without the MCP key:

```bash
curl -sS -o /tmp/produs-tool-allowlist-noauth.json -w '%{http_code}\n' \
  https://produs-api-staging.46.224.145.148.sslip.io/loomai/tool-allowlist
```

Expected result: `401` with `PRODUS_MCP_AUTH_REQUIRED`.

Check allowlist with the MCP key from the private handoff or `/tmp/produs_mcp_api_key.secret`:

```bash
curl -fsS \
  -H "X-MCP-API-KEY: $(cat /tmp/produs_mcp_api_key.secret)" \
  https://produs-api-staging.46.224.145.148.sslip.io/loomai/tool-allowlist | jq .
```

Expected result: `200`, `ready=true`, and 18 tools.

Check unauthenticated MCP discovery:

```bash
curl -sS -o /tmp/produs-mcp-noauth.json -w '%{http_code}\n' \
  -H "Content-Type: application/json" \
  -X POST \
  https://produs-api-staging.46.224.145.148.sslip.io/mcp \
  --data '{"jsonrpc":"2.0","id":"smoke","method":"tools/list","params":{}}'
```

Expected result: `401` with `PRODUS_MCP_AUTH_REQUIRED`.

Authenticated Marketplace discovery target shape:

```bash
curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/marketplace/mcp/discover" \
  --data '{
    "serverRef": "produs-staging",
    "server": {
      "transport": "STREAMABLE_HTTP",
      "endpointUrl": "https://produs-api-staging.46.224.145.148.sslip.io/mcp",
      "auth": {
        "mode": "API_KEY_HEADER_SECRET",
        "headerName": "X-MCP-API-KEY",
        "secretRef": "MCP_SECRET_PRODUS_STAGING_MCP_API_KEY"
      }
    },
    "trace": {
      "environment": "staging",
      "source": "produs-ai-enablement"
    },
    "allowedTools": [],
    "gatewayServiceRef": "mcp-execution-gateway"
  }'
```

`GET /loomai/tool-allowlist` and `POST /mcp` both require `X-MCP-API-KEY` in the current staging deployment.

## 10. Managed Safe Knowledge Vectorization

Current status: live and verified on 2026-05-21.

ProdUS exposes a backend-only cursor export endpoint:

```text
GET https://produs-api-staging.46.224.145.148.sslip.io/api/ai/loomai/knowledge-export?cursor=<opaque-cursor>&limit=<page-size>
Authorization: Bearer <produs-owned-export-token>
```

LoomAI owns the managed vectorization run lifecycle for this export:

```text
ProdUS safe export endpoint
  -> LoomAI Platform source connection
  -> managed vectorization runner
  -> runtime /api/ai/data-sync/batch
  -> Qdrant vector index
  -> runtime retrieval over DATA-plugin source handles
```

Live Platform configuration:

| Field | Value |
| --- | --- |
| Source connection | `vcn-a9bb577d` |
| Source adapter | `REST_API` |
| Source status | `READY` |
| Source base URL | `https://produs-api-staging.46.224.145.148.sslip.io` |
| Source path | `/api/ai/loomai/knowledge-export` |
| Auth mode | `BEARER` |
| Token secret ref | `MANAGED_PRODUS_SAFE_KNOWLEDGE_EXPORT_TOKEN_DEP_DEP_7706FAFB` |
| Pagination | cursor, `cursor`, `limit`, page size `100` |
| Items path | `records` |
| Vector space field | `vectorSpace` |
| Plan | `vpl-33b42e24` |
| Active revision | `vpr-d9e4b704`, revision `2` |
| Runner mode | `PLATFORM_MANAGED_AUTO` |
| Runner registration | `vrr-cb21c848`, `ACTIVE`, `CURRENT` |
| Latest successful run | `vrn-39e54227` |

Plan mapping:

```json
{
  "entityMappings": {
    "produs-safe-knowledge": {
      "recordIdField": "id",
      "recordVersionField": "metadata.sourceRecordVersion",
      "targetEntityTypeField": "vectorSpace",
      "metadataStaticValues": {
        "datasetId": "produs-safe-knowledge",
        "exportVersion": "produs-safe-knowledge-v1"
      },
      "metadataStaticValuesByTargetEntityType": {
        "<vectorSpace>": {
          "knowledgeSourceHandleRef": "<deployment DATA plugin handle ref>",
          "knowledgeSourceId": "<deployment DATA source id>",
          "knowledgeSourceDatasetRef": "<deployment DATA dataset ref>"
        }
      }
    }
  }
}
```

The per-vector-space static metadata is required because the runtime shared-index retriever filters records by the installed DATA plugin source handle. Do not remove it when rotating the export source or re-creating the plan.

Latest bootstrap/reindex evidence:

```text
run: vrn-39e54227
status: COMPLETED
processed: 157
succeeded: 157
failed: 0
checkpoints: 2 pages
failureBuckets: []
```

Checkpoint details:

- page 1: `100` records, `hasMore=true`
- page 2: `57` records, `hasMore=false`

Observed source discovery counts:

```text
service-category: 8
service-module: 75
service-dependency: 18
package-template: 12
milestone-template: 12
case-pattern: 12
acceptance-criteria-template: 1
evidence-template: 1
ai-capability-contract: 6
scanner-tool-description: 10
team-profile: 1
solo-expert-profile: 1
```

Operational commands:

```bash
curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/vectorization" \
  | jq '{sourceConnection, plan, runner, recentRuns}'

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/vectorization/runs/vrn-39e54227" \
  | jq '{run, checkpoints, failureBuckets}'
```

Reset/reindex verification on 2026-05-21:

- Governed Platform runtime vector reset removed `164` vectors from the Qdrant runtime index.
- Runtime indexing overview immediately after reset reported `totalVectors=0`.
- Managed vectorization run `vrn-39e54227` reindexed the ProdUS export successfully with `157` processed, `157` succeeded, and `0` failed.
- Runtime indexing overview after reindex reported `totalVectors=157`, including `service-module=75`, `package-template=12`, `team-profile=1`, and `solo-expert-profile=1`.
- Runtime vector metadata still includes `datasetId=produs-safe-knowledge`, `exportVersion=produs-safe-knowledge-v1`, and DATA plugin source handle metadata.
- Live retrieval checks after reindex still returned grounded answers, and runtime diagnostics showed nonzero successful results for all ProdUS DATA sources.

Known hygiene follow-up: ProdUS staging Coolify currently has duplicate `LOOMAI_SAFE_KNOWLEDGE_EXPORT_TOKEN` env rows. LoomAI stored only one non-empty value in the managed Platform secret, but the duplicate rows should be cleaned on the ProdUS app to avoid operator confusion during future rotation.

## 11. Verification Results From 2026-05-20 Through 2026-05-25

Runtime/Coolify:

- Runtime app env patched for explicit private runtime headers and ProdUS issuer.
- Coolify deployment queued and finished.
- Runtime health returned `UP`.

Direct private runtime:

- `GET /api/chat/me/auth-context`: passed with issuer `produs-staging-backend`.
- `POST /api/chat/me/query`: passed with canonical response, non-empty answer, and `providerRequestId`.
- `POST /api/chat/me/suggestions`: passed with four suggestions.
- Missing runtime API key: `401`.
- Wrong issuer: `401`.

ProdUS service:

- `GET /health`: `200`.
- `GET /loomai/tool-allowlist` without API key: `401`, `PRODUS_MCP_AUTH_REQUIRED`.
- `GET /loomai/tool-allowlist` with API key: `200`, `ready=true`, 18 tools.
- `POST /mcp tools/list` without API key: `401`, `PRODUS_MCP_AUTH_REQUIRED`.
- `POST /mcp tools/list` with API key: `200`, 18 tools.

Marketplace/read-action deployment:

- Marketplace MCP discovery for `produs-staging`: `ready=true`, 19 tools.
- Published plugin: `mkp-action-produs-productization-read-mcp@0.1.1`.
- Installed on deployment `dep-7706fafb` as an enabled `ACTION` plugin with `READY` readiness and active entitlement.
- Published deployment version: `ver-37ca6cc2` / `v10`.
- Applied release: `rel-68c38e15`, status `APPLIED_VERIFIED`.
- Runtime `/api/admin/actions/overview`: 9 ProdUS read actions loaded, including `produs_catalog_export`.
- Runtime `POST /api/chat/me/query`: passed after apply with canonical response and `providerRequestId`.
- Runtime `POST /api/chat/me/suggestions`: passed after apply with four suggestions.

Marketplace/confirmed project creation action deployment:

- Published plugin: `mkp-action-produs-productization-project-create-mcp@0.1.1`.
- Installed on deployment `dep-7706fafb` as `mpi-47247a04`, status `ENABLED`, readiness `READY`, live state `LIVE`.
- Published deployment version: `ver-f9069ce5`.
- Applied release: `rel-623c91a0`, status `APPLIED_VERIFIED`, verification `PASSED`.
- Runtime `/api/admin/actions/overview`: 9 ProdUS actions loaded, including `produs_productization_project_create`.
- Runtime action is `WRITE_ONLY`, `sideEffectLevel=MUTATING`, `confirmationRequired=false`, `groundingEligible=false`, and `readActionResolutionEligible=false`.
- Required params are `creationIntentId`, `consentToken`, `idempotencyKey`, `productName`, `summary`, and `businessStage`.
- Hidden/backend-supplied params are `creationIntentId`, `consentToken`, `idempotencyKey`, and `analysisProviderRequestId`.
- Negative MCP Gateway execution proof reached `produs.productization_project.create`, matched schema hash `sha256:6a64c636165a0e6c92e7fefd41fad8e53132f411f2aa7d107a992c6e517867c0`, returned schema drift `OK`, and failed closed with `Project creation intent not found`.
- Positive creation proof remains pending until ProdUS supplies a real owner-approved `runtimeActionPayload`.

Managed vectorization and retrieval:

- Runtime version `ver-0b3324cd` applied through release `rel-579d7fce`, status `APPLIED_VERIFIED`, verification `PASSED`.
- Runtime prompt artifact loaded from the Platform version URL and contains `ragSimilarityThreshold=0.2`, `ragMaxDocumentsUsedForContext=8`, and `ragMaxContextChars=7000`.
- Vectorization runner `vectorization-runner-dep-7706fafb` is registered as `ACTIVE` and `CURRENT`.
- Reindex run `vrn-39e54227` completed with `157/157` records succeeded and no failures after a governed runtime vector reset.
- Runtime indexing overview shows ProdUS vectors in the dedicated spaces, including `service-module=75`, `package-template=12`, `team-profile=1`, and `solo-expert-profile=1`.
- Retrieval diagnostics after live queries show nonzero successful search results for ProdUS DATA plugin sources, including `service-module`, `package-template`, `team-profile`, and `solo-expert-profile`.
- Live query checks returned grounded answers for API security review, CI/CD plus dependency risk, launch-readiness package template, and public team/solo expert recommendations.

Imported read actions:

```text
produs_catalog_search
produs_product_list
produs_package_inspect
produs_workspace_inspect
produs_scan_status
produs_finding_inspect
produs_evidence_list
produs_milestone_review_evidence
```

Only one confirmed mutation action is imported: `produs_productization_project_create`. Additional mutation MCP tools remain deferred and require separate reviewed confirmed-action manifests because the generic MCP importer creates read-only action definitions.

Confirmed project-creation action import flow used:

```bash
PLATFORM_BASE_URL="https://loomai-platform-backend.46.224.145.148.sslip.io"
PLATFORM_API_KEY="<platform-admin-api-key>"

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/marketplace/publishers/loom/submissions" \
  --data @/tmp/produs-project-create-action-submission.json

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/marketplace/submissions/<plugin-version-id>/validate" \
  --data '{"reviewNotes":"ProdUS confirmed project creation action manifest for staging."}'

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/marketplace/submissions/<plugin-version-id>/publish" \
  --data '{"reviewNotes":"ProdUS confirmed project creation action manifest for staging."}'

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/marketplace-installs" \
  --data '{"pluginId":"mkp-action-produs-productization-project-create-mcp","pluginVersion":"0.1.1","config":{},"secretRefs":{}}'

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/deployment-drafts/<draft-id>/publish" \
  --data '{"notes":"Apply ProdUS confirmed project creation action."}'

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/apply/<version-id>" \
  --data '{"targetProfileId":"dtp-coolify-staging"}'
```

Negative execution-path proof used the MCP Gateway `/api/internal/mcp/actions/execute` endpoint with the compiled action config, existing `produs-staging` MCP server ref, and intentionally invalid `creationIntentId`/`consentToken`. Expected result is not project creation; expected result is a fail-closed ProdUS validation error after schema hash and MCP routing succeed.

## 12. ProdUS Safe Knowledge DATA Plugin

Status: verified live on 2026-05-21.

Published plugin:

```text
mkp-data-produs-safe-knowledge@0.1.1
```

Installed/applied state:

- Deployment: `dep-7706fafb`
- Install: enabled, `READY`, live, free entitlement active
- Applied release: `rel-f17c4793`
- Release status: `APPLIED_VERIFIED`
- Verification status: `PASSED`
- Marketplace dataset sync: 14 datasets synchronized, including the 12 ProdUS datasets plus the existing help-center/policy datasets

ProdUS vector spaces registered by the plugin:

```text
service-category
service-module
service-dependency
package-template
ai-capability-contract
milestone-template
acceptance-criteria-template
evidence-template
scanner-tool-description
case-pattern
team-profile
solo-expert-profile
```

Map `TEAM_PROFILE` records to `team-profile` and `SOLO_EXPERT_PROFILE` records to `solo-expert-profile`. The older temporary mapping into `case-pattern` is no longer valid for staging.

Creation flow used:

```bash
PLATFORM_BASE_URL="https://loomai-platform-backend.46.224.145.148.sslip.io"
PLATFORM_API_KEY="<platform-admin-api-key>"

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/marketplace/publishers/loom/submissions" \
  --data @/tmp/produs-data-plugin-submission.json

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/marketplace/submissions/<plugin-version-id>/validate" \
  --data '{"reviewNotes":"ProdUS safe knowledge DATA plugin for staging vector-space enablement."}'

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/marketplace/submissions/<plugin-version-id>/publish" \
  --data '{"reviewNotes":"ProdUS safe knowledge DATA plugin for staging vector-space enablement."}'

curl -fsS \
  -H "X-PLATFORM-API-KEY: ${PLATFORM_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  "${PLATFORM_BASE_URL}/api/deployments/dep-7706fafb/marketplace-installs" \
  --data '{"pluginId":"mkp-data-produs-safe-knowledge","pluginVersion":"0.1.1","config":{},"secretRefs":{}}'
```

After install, publish/apply the modified deployment draft through `dtp-coolify-staging` using the normal commands in Section 3.

Runtime smoke checks used:

```bash
curl -fsS \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${LOOMAI_RUNTIME_API_KEY}" \
  http://dep-7706fafb.46.224.145.148.sslip.io/api/ai/data-sync/vector-spaces

curl -fsS \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${LOOMAI_RUNTIME_API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST \
  http://dep-7706fafb.46.224.145.148.sslip.io/api/ai/data-sync/batch \
  --data @/tmp/produs-data-plugin-smoke-upsert.json
```

Observed results:

- `/api/ai/data-sync/vector-spaces`: returned all 12 ProdUS vector spaces with `missing=[]`.
- Platform-internal smoke batch: 10 upserts succeeded and 10 deletes succeeded.
- ProdUS-shaped `SYSTEM_PROCESS` smoke batch: one `service-module` upsert succeeded and its cleanup delete succeeded.
- Dedicated profile-space smoke batch: `team-profile` and `solo-expert-profile` upserts succeeded and cleanup deletes succeeded.
- Live data-sync response now includes `providerRequestId` plus `totalOperations`, `succeededOperations`, and `failedOperations`.
- Temporary retrieval smoke: a synthetic `service-module` record was indexed, answered through `POST /api/chat/me/query` with a grounded answer and provider request id, then deleted.

ProdUS safe knowledge sync must use canonical `trace + operations`; do not send the old top-level `environment/source/records` shape. The sync trace should use:

```json
{
  "subjectId": "system:produs-safe-knowledge-sync",
  "subjectType": "SYSTEM_PROCESS",
  "authMode": "PRIVATE_RUNTIME_BACKEND_MEDIATED",
  "callerType": "SYSTEM_PROCESS",
  "deploymentId": "dep-7706fafb",
  "customerId": "produs-staging",
  "issuer": "produs-staging-backend",
  "grantedScopes": ["data-sync:upsert"]
}
```

Known operational follow-up: Platform apply can overwrite manually added accepted private assertion issuers. After this apply, Coolify runtime env was patched back to include `produs-staging-backend` and `platform-produs-data-plugin-smoke`, then the runtime was redeployed and health returned `UP`. This should be hardened in Platform env rendering so future applies preserve registered private runtime issuers.

## 12.5 2026-06-10 `dep-53f9ca56` Vector Store Recovery

ProdUS staging is now routed to `dep-53f9ca56`; older references to `dep-7706fafb` are historical and should not be used for current staging verification.

Incident root cause: `dep-53f9ca56` pointed at a stale Qdrant endpoint that returned `404 page not found` for collection APIs. The runtime app was healthy, but vector upserts failed because the configured vector backend was no longer usable.

Recovery performed:

- Moved `dep-53f9ca56` to the healthy Qdrant cluster with collection prefix `cus_3b201f0d__ten_c134590e__`.
- Applied Platform version `ver-908e3888`; latest successful recovery release was `rel-962bcdea`.
- Verified direct ProdUS-style `rpa1` private runtime auth against runtime auth/admin endpoints.
- Verified the ProdUS safe DATA plugin sources and all expected vector spaces are installed and READY.
- Ran managed vectorization bootstrap `vrn-8c8e870d` and reindex `vrn-35109ab3`; both processed 190 records with 190 successes and 0 failures.
- Added missing Qdrant keyword payload indexes on ProdUS prefixed collections for common source/filter fields.

Current runtime vector counts:

```text
totalVectors=195
service-module=90
service-category=10
service-dependency=23
package-template=15
milestone-template=15
case-pattern=15
scanner-tool-description=10
ai-capability-contract=7
evidence-template=2
acceptance-criteria-template=1
team-profile=1
solo-expert-profile=1
faq-article=3
support-policy=2
```

Direct Qdrant proof: service-module vector search returns `service-module:api-security-review`, `service-module:security-fix-sprint`, `service-module:security-readiness-review`, `service-module:security-patching`, and `service-module:dependency-security-review`.

Remaining caveat: `/api/chat/me/query` retrieval still does not surface the expected ProdUS `service-module` sources for the smoke query `API security review`. Live runtime retrieval returned either the seeded help-center FAQ or zero sources even though direct Qdrant search returns the correct service modules. Treat this as a framework/runtime shared-index retrieval or orchestration follow-up, not an active vector-store outage.

Secondary caveat: Platform vectorization overview still reports `OUT_OF_DATE` / `INDEXED_OUTPUT_DRIFT` after successful runs because the active config hash and last successful indexed-output hash differ. Live vector counts and Qdrant records are correct.

## 13. Rollback

If direct runtime auth breaks after enabling ProdUS:

1. Set `LOOMAI_ENABLED=false` in ProdUS backend env to restore ProdUS local fallback.
2. Keep the LoomAI runtime running; do not rotate secrets during incident triage unless compromise is suspected.
3. Verify runtime health:

```bash
curl -fsS http://dep-7706fafb.46.224.145.148.sslip.io/actuator/health | jq .
```

4. Rerun `/api/chat/me/auth-context` with a fresh short-lived `rpa1` token.
5. Check Coolify deployment status and logs:

```bash
curl -fsS \
  -H "Authorization: Bearer ${COOLIFY_STAGING_API_TOKEN}" \
  -H "Accept: application/json" \
  "${COOLIFY_BASE_URL}/api/v1/applications/m14c2kdq3qsc2hnofr84wge2/logs?lines=100"
```

6. If the latest env change caused failure, restore previous issuer/audience/header env rows from Coolify history or private ops notes and redeploy.

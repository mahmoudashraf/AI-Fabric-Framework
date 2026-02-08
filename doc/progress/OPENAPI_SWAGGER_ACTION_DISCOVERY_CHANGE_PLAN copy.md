# OpenAPI/Swagger Action Discovery + Capability Inspection — Change Plan

## Status
Proposed

## Why this document exists
Many teams already have internal/external services documented via **OpenAPI/Swagger**. Today, to let the Orchestrator use those capabilities, integrators must hand-write `ActionHandler`s + metadata (slow, inconsistent, and error-prone).

This change plan proposes a safe-by-default way for the Orchestrator to:
1) **Read OpenAPI specs** (file/URL)
2) **Expose endpoints as AI actions** (with strong gates)
3) Provide **LLM-assisted inspection** of “what can this API do?” and “what does this endpoint return?”

---

## Problem
- Action onboarding cost is high: every endpoint requires custom code + metadata shaping.
- The LLM can’t easily “see” API capabilities unless we manually document them into prompts.
- Large API surfaces can overwhelm intent extraction prompts and degrade action selection quality.
- Even when endpoints are known, action parameter filling is fragile without schemas/examples.

---

## Goals
- **Discoverability**: Load one or more OpenAPI specs and expose their operations as Orchestrator actions.
- **Inspection**: Enable “capability exploration” (search operations, describe request/response shapes) with LLM help.
- **Safety-by-default**:
  - network access is explicitly allowlisted
  - write operations require confirmation (and can be disabled entirely)
  - response payloads are bounded + sanitized
- **Provider-agnostic**: Works regardless of LLM provider (OpenAI/Cohere/Gemini/Anthropic/Azure).
- **No app lock-in**: Apps can override naming, descriptions, and credential injection via configuration/SPI.

---

## Non-goals
- Building a full API gateway or SDK generator.
- Auto-executing high-risk operations without explicit confirmation and policy checks.
- Dynamically fetching arbitrary OpenAPI specs from untrusted URLs at runtime.
- “Unlimited tools” in the prompt (we must keep prompts bounded).

---

## Proposed approach (high level)
Introduce an “OpenAPI Tools” capability composed of:
1) **Spec registry**: load/validate/normalize OpenAPI documents
2) **Operation catalog**: turn OpenAPI operations into deterministic action metadata
3) **Invoker**: execute a chosen operation safely (HTTP client + auth + rate limits + bounds)
4) **Inspector**: endpoints/actions to explore capabilities (search + describe + examples)

This can live as a new module (recommended name): `ai-infrastructure-openapi-actions` (Community), optionally with enterprise extensions for advanced auth/policy.

---

## Design details

### 1) Configuration (safe defaults)
Introduce a new config block (names illustrative):

- `ai.openapi-actions.enabled=false` (default off)
- `ai.openapi-actions.sources[]` (0..N):
  - `name` (required, stable identifier)
  - `specUrl` (optional) / `specPath` (optional) — exactly one required
  - `baseUrl` (optional override; otherwise use OpenAPI servers[0])
  - `allowedHosts[]` (required for URLs; fail-closed)
  - `includeTags[]` / `excludeTags[]` (optional)
  - `includePaths[]` / `excludePaths[]` (optional glob/regex)
  - `allowedMethods=["GET"]` (default GET-only)
  - `exposeMode=INVOKE_GENERIC | PER_OPERATION | HYBRID` (default `INVOKE_GENERIC`)
  - `maxOperationsExposed=50` (default; protects prompts)

Execution bounds:
- `ai.openapi-actions.http.timeout-ms=8000`
- `ai.openapi-actions.http.max-response-bytes=200_000`
- `ai.openapi-actions.http.max-json-depth=20`
- `ai.openapi-actions.http.max-array-items=50`

LLM payload bounds (when sending results back for grounded generation):
- `ai.openapi-actions.llm.max-facts-chars=12_000`
- `ai.openapi-actions.llm.sanitize=true`

### 2) Capability inspection (how the LLM explores)
Provide either:

**A) Web endpoints (human + tooling)**
- `GET /api/ai/openapi/sources`
- `GET /api/ai/openapi/operations?source=&tag=&q=&method=`
- `GET /api/ai/openapi/operations/{operationKey}` (request/response schema summary + examples)

**B) Orchestrator actions (LLM can use them)**
- `openapi.search_operations(query, source?, tag?, limit?)` (read-only)
- `openapi.describe_operation(operationKey)` (read-only; returns required params + request/response shape)
- `openapi.invoke(operationKey, params)` (may be read or write depending on operation)

Recommended v1: ship **both** (endpoints for developers + actions for LLM flows).

### 3) Exposing operations as actions (three options)

**Option 1 — Generic invoker only (recommended for v1)**
- Register a small, stable action set:
  - `openapi.search_operations`
  - `openapi.describe_operation`
  - `openapi.invoke`
- Pros: prompt stays small; scales to large specs; avoids thousands of actions.
- Cons: multi-step workflows become multi-turn unless/until we add a bounded agent loop.

**Option 2 — One action per operation**
- Create an `ActionHandler` per exposed operation:
  - action name: `openapi.<source>.<operationId>` (or method+path fallback)
  - params derived from OpenAPI parameter + requestBody schema
- Pros: best UX for intent extraction (LLM picks a concrete action).
- Cons: can bloat prompts; many beans; harder to manage for large APIs.

**Option 3 — Hybrid**
- Expose only operations that are explicitly marked with vendor extensions:
  - `x-ai-expose: true`
  - `x-ai-name`, `x-ai-description`, `x-ai-required-params`, `x-ai-example-request`, `x-ai-example-response`
- Everything else remains accessible via `openapi.invoke` (if allowed).

Recommended: **Option 1 now**, **Option 3 later** for curated “best endpoints”.

### 4) Action metadata mapping (OpenAPI → AIActionMetaData)
For each operation, derive:
- `name`: stable, deterministic, collision-free
- `description`: use `summary` + `description` (bounded)
- `category`: source name + first tag (e.g., `billing.subscriptions`)
- `parameters`: flattened list describing location + type:
  - `userId`: `in=path,type=string,required=true`
  - `status`: `in=query,type=string,required=false,enum=[ACTIVE,CANCELED]`
  - `body`: `in=body,type=json,schemaRef=CreateSubscriptionRequest`
- `requiredParameters`: path params + required query params + required body fields (bounded; schema-depth limited)

### 5) Invocation (OpenAPI operation executor)
Implement a generic executor that:
- resolves `operationKey` → operation spec (method, path, servers/baseUrl)
- builds URL (path params + query params)
- builds request body (JSON) and headers
- injects credentials via an SPI (see below)
- executes via existing `HttpClient` abstraction
- returns `ActionResult` with:
  - status code, timing, truncated response
  - parsed JSON summary (object keys + counts)
  - correlation/audit id

### 6) Credentials & auth (SPI, not prompts)
Add an SPI to avoid leaking secrets into prompts:
- `OpenApiCredentialProvider`:
  - `Optional<Map<String,String>> headersFor(OrchestrationContext ctx, OpenApiOperation op)`
  - (optional) per-source selection
- Support common patterns via config:
  - API key header
  - bearer token (from context or env)
  - basic auth (discouraged; allowed only if explicitly enabled)

### 7) Safety / governance (must be enforced in code)
**Fail-closed gates**
- deny invocation if source disabled / host not allowlisted
- deny methods not allowlisted (`GET` only by default)
- deny operations that match denylist patterns (e.g., `/admin/*`, `/internal/*`)
- enforce rate limits/budgets per source (future enhancement if not already present)

**Confirmation**
- any non-GET methods default to `requiresConfirmation() = true`
- optionally “force confirmation” for sensitive GET endpoints too (config)

**PII / compliance**
- sanitize action results before they are sent to the LLM for post-action generation
- never include auth headers/tokens in any logs or LLM facts

**Response bounds**
- strict max bytes + max JSON depth + max array items
- return summaries when truncated (include `truncated=true` + reason)

---

## How “LLM help” fits (practically)
Smart behavior comes from two places:
1) The LLM uses `openapi.search_operations` / `openapi.describe_operation` to discover and validate shapes.
2) For final responses, use existing “facts-only” grounded generation by shaping an LLM-safe facts map from:
   - operationKey
   - request summary (no secrets)
   - response summary (bounded + sanitized)

This keeps decisions explainable and avoids “LLM guessing” without facts.

---

## Testing strategy
### Unit tests
- OpenAPI parsing + normalization
- Action name derivation + collision handling
- Required parameter extraction (path/query/body)
- Policy gates (hosts/methods/path denylist)
- Response bounding + truncation summaries

### Integration tests
- WireMock (or similar) stub server with an OpenAPI spec:
  - verify `openapi.invoke` makes correct calls
  - verify confirmation gating for POST/PUT/DELETE
  - verify sanitization + bounded facts sent to post-action generation

### Real API tests (optional, keys-only)
- A tiny public safe spec (or internal sandbox) to validate end-to-end wiring.

---

## Rollout plan
1) Ship module behind `ai.openapi-actions.enabled=false`
2) Add a Real App demo that loads a small spec and exposes 2–3 safe GET endpoints
3) Add docs: configuration, allowlists, and security requirements
4) Expand to hybrid “curated per-operation actions” via `x-ai-*` extensions if needed

---

## Acceptance criteria (v1)
- Can load an OpenAPI spec (file or URL) and list operations via an endpoint and/or action.
- Can describe an operation (required params + request/response summary) in a bounded format.
- Can invoke allowed operations safely with:
  - host + method allowlists enforced
  - confirmation required for writes
  - bounded + sanitized outputs
- Works across LLM providers (no provider-specific tool calling required).

---

## Open questions
- Do we want v1 to support “per-operation actions”, or only the generic `openapi.invoke` toolset?
- Where should OpenAPI specs live for production: bundled resources, config URL, or a governance-managed store?
- What is the minimal auth support needed in Community vs Enterprise?
- Should OpenAPI operations be indexed into a vector space for INFORMATION queries (“what endpoints exist for X”)?


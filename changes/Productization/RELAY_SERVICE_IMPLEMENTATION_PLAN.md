# AI Fabric Relay Service — Implementation Plan (V1)

This document is a **build plan** for shipping an official **AI Fabric Relay** service that implements the **Customer Connector API** inside a customer environment (VPC/on‑prem), enabling safe action execution + optional documents-only retrieval without exposing internal systems publicly.

References:
- Architecture + semantics: `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`
- OpenAPI contract (must implement): `changes/Productization/customer-connector-api.openapi.yml`
- Relay spec + deployment requirements: `changes/Productization/RELAY_IMPLEMENTATION_AND_DEPLOYMENT_GUIDE.md`

---

## Status (as of 2026-02-13)

- **Implemented in code (V1):** `ai-infrastructure-module/ai-infrastructure-relay`
- Redis-backed nonce/idempotency/rate-limit stores are implemented and configurable (`relay.store.backend=REDIS`; default stays `IN_MEMORY`).
- Not shipped yet (packaging/ops): Dockerfile + Compose/Helm examples, and OpenAPI contract conformance tests.
- Remaining hardening (planned): optional mTLS inbound auth (enterprise) and additional operational controls.

---

## 0) Goals / Non-goals

### 0.1 Goals (V1)
- Ship a **runnable Relay** (Docker image + jar) that implements:
  - `POST /actions/execute`
  - `POST /retrieval/search` (optional but included in V1 since connectors already support it)
- **Fail-closed security** by default:
  - inbound request authentication (HMAC recommended)
  - replay protection (timestamp + nonce)
  - SSRF-safe routing (no arbitrary URLs from AI Fabric)
  - strict request validation (schema + size bounds)
- “Least headache” deployment:
  - single binary/container
  - file-based config + environment variables for secrets
  - sane defaults for timeouts and logs
- Provide an “internal integration seam” so customers can:
  - map `actionId → internal endpoint`, or
  - forward to a **single internal dispatcher** service

### 0.2 Non-goals (V1)
- Not a place to define the action contract (params/confirmation text) — that remains in AI Fabric action catalogs.
- Not an LLM/orchestration component.
- Not a multi-tenant SaaS in V1 (Relay is designed as a **single-tenant, customer-owned** component).
- Not a universal API gateway (keep the surface area minimal).

---

## 1) Deliverables

### 1.1 Code artifacts
- New module/app: `ai-infrastructure-module/ai-infrastructure-relay/` (Spring Boot app)
- Docker image build:
  - local: `Dockerfile`
  - CI: publish step (optional later)

### 1.2 Documentation artifacts
- “How to deploy”: extend `changes/Productization/RELAY_IMPLEMENTATION_AND_DEPLOYMENT_GUIDE.md`
- “How to configure”: add config schema + examples in this plan and the deployment guide
- “How AI Fabric calls it”: keep `changes/Productization/customer-connector-api.openapi.yml` as the contract of record

---

## 2) Service Architecture (V1)

### 2.1 External API (contract)
Implement the OpenAPI contract exactly:
- `POST /actions/execute` → returns `ActionResult`
- `POST /retrieval/search` → returns `RetrievalSearchResponse`

Do not add required fields or change shapes (additive-only fields are OK).

### 2.2 Inbound auth options (configurable, fail-closed)
Support both, but recommend HMAC:
1) API key header (simple, weaker)
2) HMAC signature headers:
   - `X-AIFABRIC-TIMESTAMP`
   - `X-AIFABRIC-NONCE`
   - `X-AIFABRIC-SIGNATURE`

Implementation rules:
- If HMAC is enabled, requests missing any required HMAC header are rejected.
- Timestamp must be within `maxClockSkewSeconds`.
- Nonce must be unique within `nonceTtlSeconds` (prevents replay).

### 2.3 SSRF-safe routing patterns
Support both safe patterns:

**Pattern A (recommended): mapping**
- Relay config contains an allowlist:
  - `actionId → {method, url, timeoutMs, headersTemplate?}`
- Only mapped actions can be executed.

**Pattern B: single dispatcher**
- Relay forwards every action execution to one internal service URL.
- Internal service routes by `actionId`.

Explicitly avoid:
- accepting or constructing arbitrary URLs from the inbound request payload.

### 2.4 Outbound call semantics (internal services)
Relay → internal service request:
- Body: forward the same `ActionExecuteRequest` (or a narrowly-scoped mapped shape).
- Headers: forward trace context in headers too (for easy auth/logging):
  - `X-AIFABRIC-REQUEST-ID`
  - `X-AIFABRIC-CONVERSATION-ID`
  - `X-AIFABRIC-USER-ID`
  - `X-AIFABRIC-SESSION-ID`
- Timeouts:
  - connect + read timeouts required (configurable per action; defaults apply)

Relay must return:
- a valid `ActionResult` / `RetrievalSearchResponse` on success or handled failure
- deterministic error codes on infrastructure failures (`TIMEOUT`, `SERVICE_UNAVAILABLE`, `ACTION_EXECUTION_FAILED`)

---

## 3) Data Protection / Logging / Limits (V1 must-haves)

### 3.1 Request size and parsing bounds (fail-closed)
- Enforce maximum body size (example default: 256KB) with a configurable limit.
- Reject unknown top-level fields if the OpenAPI contract disallows them (`additionalProperties: false`).
- Reject invalid JSON with a protocol error response.

### 3.2 Rate limiting (defense in depth)
Implement:
- per-user rate limit
- per-action rate limit

On limit:
- return `success=false`, `errorCode=RATE_LIMITED`
- include `data.retryAfterSeconds` when possible

Recommended implementation:
- token bucket with fixed window config (simple)
- storage:
  - in-memory (dev / single instance)
  - Redis (recommended for HA)

### 3.3 Audit logging (PII-safe)
Log minimum fields:
- timestamp
- `actionId`
- `trace.requestId`
- `trace.userId` (stable, non-PII)
- `success` + `errorCode`
- latency

Never log:
- raw `params`
- secrets (api keys, tokens)

Provide structured JSONL sink:
- `stdout` (container-friendly)
- optional file sink with rotation (later)

---

## 4) Idempotency (required for safe retries)

### 4.1 Behavior
For requests with `idempotencyKey`:
- If key is new → execute and store result.
- If key exists with same request fingerprint → return stored result.
- If key exists with different fingerprint → return `IDEMPOTENCY_CONFLICT`.

Fingerprint recommendation:
- stable hash of `{actionId, canonicalJson(params)}`.

### 4.2 Storage options (configurable)
- In-memory (dev only)
- Redis (recommended default)
- SQL table (optional)

Defaults:
- `ttlHours: 48`

---

## 5) Implementation Phases (recommended order)

### Phase 1 — Scaffold + contract compliance
- Create Spring Boot app module: `ai-infrastructure-module/ai-infrastructure-relay`.
- Implement controllers:
  - `POST /actions/execute`
  - `POST /retrieval/search`
- Define request/response DTOs mirroring OpenAPI schemas.
- Implement strict JSON validation:
  - required top-level fields present
  - `trace` required
- Add base error mapping to `ActionResult`/`RetrievalSearchResponse`.

Acceptance:
- Contract tests validate payload shapes against `customer-connector-api.openapi.yml`.

### Phase 2 — Inbound authentication + replay protection
- Implement:
  - API key auth (optional)
  - HMAC verification (recommended)
  - timestamp skew check
  - nonce cache with TTL

Acceptance:
- Unit tests for valid/invalid signatures, skew rejection, nonce replay rejection.

### Phase 3 — SSRF-safe routing + outbound execution
- Implement routing patterns:
  - Pattern A mapping
  - Pattern B dispatcher
- Implement outbound HTTP client with:
  - per-route timeouts
  - bounded headers allowlist (avoid header injection)
  - response parsing into framework contracts

Acceptance:
- Integration tests with a stub internal service.

### Phase 4 — Idempotency store
- Implement idempotency storage + fingerprinting.
- Integrate into action execution path.

Acceptance:
- Same key returns same result.
- Different params with same key returns `IDEMPOTENCY_CONFLICT`.

### Phase 5 — Rate limiting + audit logging
- Implement per-user and per-action limits.
- Implement JSON structured audit logs (PII-safe).

Acceptance:
- Load test (local) shows stable behavior under throttling.

### Phase 6 — Operational hardening
- Health/readiness endpoints.
- Metrics (basic):
  - request count by `actionId`, success/failure
  - latency buckets
  - rate limited count
- TLS guidance and deployment templates:
  - docker-compose sample
  - Kubernetes manifest example (optional)

Acceptance:
- “Customer quickstart” can deploy and successfully run connector calls end-to-end.

---

## 6) Configuration Schema (V1)

Example (`relay-config.yml`):

```yaml
relay:
  auth:
    apiKey:
      enabled: false
      header: X-AIFABRIC-API-KEY
      valueEnv: AIFABRIC_RELAY_API_KEY
    hmac:
      enabled: true
      timestampHeader: X-AIFABRIC-TIMESTAMP
      nonceHeader: X-AIFABRIC-NONCE
      signatureHeader: X-AIFABRIC-SIGNATURE
      secretEnv: AIFABRIC_HMAC_SECRET
      maxClockSkewSeconds: 300
      nonceTtlSeconds: 600

  limits:
    maxBodyBytes: 262144

  rateLimits:
    perUser:
      windowSeconds: 60
      maxRequests: 100
    perAction:
      create_purchase_order:
        windowSeconds: 60
        maxRequests: 10

  idempotency:
    enabled: true
    ttlHours: 48
    store: redis # inMemory | redis | sql
    redis:
      uriEnv: REDIS_URI

  routing:
    mode: mapping # mapping | dispatcher
    dispatcher:
      url: http://internal-dispatcher:8080/actions/execute
      timeoutMs: 5000
    actions:
      create_purchase_order:
        method: POST
        url: http://internal-api:8080/orders
        timeoutMs: 5000
      cancel_purchase_order:
        method: POST
        url: http://internal-api:8080/orders/cancel
        timeoutMs: 3000
    retrieval:
      url: http://internal-search:8080/retrieval/search
      timeoutMs: 3000
```

Rules:
- In `mapping` mode, missing `routing.actions[actionId]` rejects the request (`ACTION_NOT_SUPPORTED`).
- `routing.retrieval.url` is required only if `POST /retrieval/search` is enabled.

---

## 7) Testing Strategy

### 7.1 Unit tests
- HMAC verification (known vectors)
- nonce replay rejection
- idempotency behavior
- rate limiting counters
- routing allowlist behavior

### 7.2 Integration tests
- stub internal action service + retrieval service (WireMock or Testcontainers)
- verify request forwarding + trace propagation
- verify error mapping on timeouts and non-2xx

### 7.3 Security tests (minimum)
- reject requests with unexpected fields
- reject oversize payloads
- ensure no request bodies are logged in audit logs

---

## 8) Rollout / Versioning

### 8.1 Versioning
- Relay implements `customer-connector-api.openapi.yml` version `0.1.x` initially.
- Contract changes must be backwards compatible (additive fields only) or versioned (`/v2/...` later).

### 8.2 Adoption path
- Customers can start with:
  - their own connector directly (no Relay), then later
  - adopt Relay for higher security and private routing

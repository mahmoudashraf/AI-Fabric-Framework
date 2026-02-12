# AI Fabric Relay — Implementation & Deployment Guide (Planned)

The **AI Fabric Relay** is an optional customer-side component that implements the **Customer Connector API** inside the customer environment (VPC/on‑prem), so internal systems do not need to be exposed publicly.

Reference:
- Architecture + contracts: `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`
- OpenAPI spec: `changes/Productization/customer-connector-api.openapi.yml`

---

## Status (as of 2026-02-12)

- **Not implemented yet in code.** This document is the implementation + deployment specification.
- The AI Fabric runtime already supports calling a connector endpoint (so the Relay can be introduced without changing orchestration semantics).

---

## 1) What the Relay does

The Relay is responsible for:
- authenticating AI Fabric requests (recommended: HMAC)
- enforcing rate limits (defense in depth)
- audit logging (PII-safe)
- SSRF-safe routing (`actionId → internal endpoint` or single dispatcher)
- forwarding user/trace context for authorization and tracing
- returning a valid `ActionResult` to AI Fabric

The Relay is not responsible for:
- defining action contracts (params, required fields, confirmation text)
- LLM prompting or orchestration logic

---

## 2) Security model (must-have)

### 2.1 Authentication chain (defense in depth)

```
User → AI Fabric (authenticates user/session)
     → Relay (verifies AI Fabric signature)
     → Internal service (re-authorizes user)
```

Rule:
- internal services MUST re-authorize using forwarded user identity/claims.

### 2.2 Inbound request verification (HMAC recommended)

Recommended headers:
- `X-AIFABRIC-TIMESTAMP` (epoch seconds)
- `X-AIFABRIC-NONCE` (unique per request)
- `X-AIFABRIC-SIGNATURE` (base64 HMAC-SHA256 over `timestamp + "\n" + nonce + "\n" + body`)

Relay must verify:
- signature correctness
- timestamp is within `maxClockSkewSeconds` (replay protection)
- nonce uniqueness within TTL (optional but recommended)

### 2.3 Rate limiting (required)

Enforce:
- per-user limits
- per-action limits

On limit, return:
- `success=false`, `errorCode=RATE_LIMITED`
- optional `data.retryAfterSeconds`

### 2.4 Audit logging (required; PII-safe)

Log fields (minimum):
- timestamp
- `actionId`
- `trace.requestId`
- stable `trace.userId` (avoid email/phone/address)
- outcome: success/failure + `errorCode`
- latency

Do not log:
- full params bodies
- sensitive values (addresses, emails, tokens, payment info)

### 2.5 Network security

AI Fabric → Relay:
- TLS required
- HMAC verification required when exposed over the internet
- optional: mTLS later

Relay → internal services:
- customer choice, but TLS + service auth is recommended

---

## 3) Routing patterns (SSRF-safe)

### Pattern A (recommended): explicit mapping

Relay config maps `actionId` to internal endpoint:
- prevents SSRF (no URLs from AI Fabric)
- explicit allowlist (only declared actionIds can be routed)

### Pattern B: single dispatcher

Relay forwards to one internal endpoint:
- the internal dispatcher routes by `actionId`
- still SSRF-safe (single allowlisted URL)

Avoid:
- accepting arbitrary URLs from AI Fabric

---

## 4) Configuration schema (example)

```yaml
# relay-config.yml
server:
  port: 8443
  tls:
    enabled: true
    certPath: /etc/relay/tls/cert.pem
    keyPath: /etc/relay/tls/key.pem

aiFabric:
  hmacSecretEnv: AIFABRIC_HMAC_SECRET
  maxClockSkewSeconds: 300

rateLimits:
  perUser:
    windowSeconds: 60
    maxRequests: 100
  perAction:
    create_purchase_order:
      windowSeconds: 60
      maxRequests: 10

actions:
  # Pattern A: mapping
  create_purchase_order:
    endpoint: http://internal-api:8080/orders
    method: POST
    timeoutMs: 5000
  cancel_purchase_order:
    endpoint: http://internal-api:8080/orders/cancel
    method: POST
    timeoutMs: 3000

  # Pattern B: single dispatcher (alternative)
  # dispatcher:
  #   endpoint: http://internal-api:8080/actions
  #   method: POST
  #   timeoutMs: 5000

audit:
  enabled: true
  destination: file
  path: /var/log/relay/audit.jsonl
  retentionDays: 90
```

---

## 5) Deployment options

### 5.1 Docker (single instance)

- Run the relay as a container in the customer VPC.
- Expose only the relay port; keep internal services private.

### 5.2 Docker Compose (dev / quickstart)

Use Compose to bring up:
- relay
- redis (optional, for idempotency/nonce store)
- internal dispatcher (optional)

### 5.3 Kubernetes (production)

Recommended:
- Deployment + Service
- Ingress with TLS
- ConfigMap/Secret for relay config + HMAC secret
- Horizontal Pod Autoscaler (optional)

---

## 6) Acceptance tests (must pass)

- Reject invalid/missing signature (401/403)
- Reject requests outside clock skew window
- Rate limit by user + by action
- Audit log written for every request (no PII)
- SSRF-safe routing (no dynamic URLs)
- Correctly forwards `trace` and preserves requestId correlation
- Returns valid `ActionResult` for both success and failure


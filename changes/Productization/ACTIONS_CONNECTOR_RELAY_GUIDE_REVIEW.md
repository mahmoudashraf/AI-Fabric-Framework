# Actions Connector and Relay Guide - Validation Review

**Document**: `ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`
**Review Date**: February 11, 2026
**Reviewer**: AI Fabric Framework Team
**Status**: Approved with Required Additions
**Overall Rating**: 9/10

---

## Executive Summary

The Actions Connector and Relay Guide represents **excellent strategic and architectural documentation** that will enable AI Fabric to scale across diverse customer environments. The core vision is sound, the productization strategy is brilliant, and the alignment with framework philosophy is exceptional.

**Key Strengths**:
- ✅ Perfect alignment with greenfield philosophy and fail-closed security
- ✅ Language-agnostic architecture enables broader market adoption
- ✅ Clear monetization boundaries (sell optimizations, not domain logic)
- ✅ Concrete, realistic examples

**Required Additions**:
- ⚠️ Error response contract and standard error codes
- ⚠️ Idempotency implementation specifications
- ⚠️ Relay security model depth

**Recommendation**: Implement blocking items before Beta release.

---

## Table of Contents

1. [Philosophy Alignment Analysis](#philosophy-alignment-analysis)
2. [Security Model Review](#security-model-review)
3. [Critical Issues and Questions](#critical-issues-and-questions)
4. [Productization Strategy Fit](#productization-strategy-fit)
5. [Detailed Recommendations](#detailed-recommendations)
6. [Implementation Checklist](#implementation-checklist)

---

## Philosophy Alignment Analysis

### ✅ Greenfield Mindset

**Document Quote**:
> "There is no fundamental conflict between annotation-based actions and connector-executed actions. Unify the contract and orchestration semantics."

**Philosophy Match**: Perfect embodiment of "If we were designing this today with perfect knowledge, what would we build?"

The decision to unify local and remote actions under a single contract, rather than maintaining separate systems, demonstrates greenfield thinking at its best.

### ✅ Fail-Closed Security

**Document Quote**:
> "If an action name is registered twice (local + connector, or connector + connector): **Fail fast** at registration/startup. No silent overrides."

**Philosophy Match**: Exactly what the framework demands - "fail at startup over fail in production."

**Additional Evidence**:
- Idempotency required for write operations
- HMAC-signed requests (recommended)
- Explicit SSRF prevention guidance

### ✅ Separation of Concerns

**Document Quote**:
> "The orchestrator should not care *where* the action runs. It only cares that an `ActionResult` is returned in the framework contract."

**Philosophy Match**: Clean separation between "what orchestrator needs to know" and "how actions execute."

This enables:
- Local Java handlers (in-process)
- HTTP connectors (out-of-process)
- Same orchestration semantics

### ✅ Respecting Intelligence

The document respects both:
1. **LLM Intelligence**: Orchestrator decides what action to call based on user intent
2. **Customer Intelligence**: Customers implement actions in their preferred stack

**Philosophy Match**: "Trust creates better code" - framework defines interfaces, users implement what they need.

---

## Security Model Review

### ✅ Excellent: SSRF Prevention

**Document Quote** (Section 5.2):
> "Avoid: A relay that accepts arbitrary URLs from AI Fabric (SSRF risk)"

**Analysis**: This is critical security guidance. The document explicitly warns against the most common vulnerability in relay architectures.

**Recommended Patterns**:
1. **Allowlist approach**: Relay maps `actionId → endpoint`
2. **Single dispatcher**: Relay forwards all to one internal endpoint

Both patterns prevent SSRF by never accepting URLs from AI Fabric.

### ✅ Good: Authentication Options

**Document Lists**:
- Static API keys
- HMAC-signed requests (recommended)
- mTLS (enterprise future)

**Analysis**: Pragmatic progression from simple (API keys) to secure (HMAC) to enterprise-grade (mTLS).

### ⚠️ Needs Depth: Authentication Chain

**Missing**:
1. How does user identity flow through the chain?
   ```
   User → AI Fabric → Relay → Internal Service
   ```
2. Who is responsible for authorization at each stage?
3. Should internal services re-authorize, or trust AI Fabric?

**Recommendation**: Add explicit guidance that internal services MUST re-authorize.

### ⚠️ Needs Depth: Rate Limiting

**Missing**:
- Per-user action limits
- Per-action rate limits
- Burst allowances
- Rate limit error responses

**Recommendation**: Add rate limiting specifications to prevent abuse.

### ⚠️ Needs Depth: Audit Logging

**Missing**:
- What must be logged?
- Where (Relay? Internal service? Both?)
- Retention requirements
- PII handling in logs

**Recommendation**: Add audit logging requirements for compliance.

---

## Critical Issues and Questions

### 1. Action Registration Lifecycle (HIGH PRIORITY)

**Current State**: Section 3 describes file-based and DB-backed registration but doesn't specify lifecycle.

**Critical Questions**:

1. **When does collision detection happen?**
   - At startup? (Good - fail fast)
   - At runtime? (Bad - too late to fail fast)
   - Per source? (Unclear behavior)

2. **Who validates action schemas?**
   - Framework at boot? (Good)
   - Connector at runtime? (Duplicates logic)
   - Both? (Redundant but safer)

3. **How are confirmation message templates rendered?**
   ```yaml
   confirmationMessage: "Create purchase order for {{quantity}} × {{sku}}?"
   ```
   - Where does rendering happen?
   - What if `{{shippingAddress}}` is sensitive? Auto-redacted?
   - What's the escaping strategy (prevent injection)?

**Recommended Addition**:

```markdown
## Action Registration Lifecycle (New Section 3.4)

### Discovery Phase (Startup)

1. **Source Loading Order**:
   - Load from annotations (`@AIAction`)
   - Load from files (`ai-actions.yml`)
   - Load from database (if configured)

2. **Validation Steps**:
   - Action names: `^[a-z][a-z0-9_]*$` (snake_case only)
   - Categories: optional, alphanumeric
   - Parameters: validate required + pattern rules
   - Confirmation templates: validate placeholders match params

3. **Collision Detection**:
   - Build unified action registry
   - If duplicate name found: **fail startup**
   - Error message: "Action 'create_purchase_order' registered twice: local + connector"

4. **Template Safety**:
   - Validate placeholders: `{{param}}` must exist in action params
   - Auto-redact sensitive params: If param has `sensitive: true`, exclude from confirmation
   - Example: `{{shippingAddress}}` → `{{shippingAddress|redacted}}`

### Runtime Contract

- Orchestrator uses **validated catalog only**
- Connector receives **pre-validated parameters**
- No runtime schema changes (restart required)
- Template rendering uses **escape-by-default** strategy
```

### 2. Error Response Contract (BLOCKING)

**Current State**: Section 4.1 shows success response but **no error specification**.

**Critical Questions**:

1. **How does connector signal different failure types?**
   - SKU not found vs payment failed vs network error
   - Which are retriable?
   - How does UI differentiate?

2. **User-facing error messages**:
   - Can connector provide custom messages?
   - Should AI Fabric generate user-friendly explanations?

3. **Error data structure**:
   - Should match success structure?
   - How to pass error details to UI?

**Recommended Addition**:

```markdown
## Error Response Contract (Add to Section 4.1)

### Error Response Structure

```json
{
  "success": false,
  "errorCode": "INSUFFICIENT_INVENTORY",
  "message": "SKU-123 is out of stock",
  "retriable": false,
  "data": {
    "sku": "SKU-123",
    "availableQuantity": 0,
    "nextRestockDate": "2026-02-15"
  }
}
```

### Standard Error Codes

| Error Code | Description | Retriable | User Action |
|------------|-------------|-----------|-------------|
| `INVALID_PARAMETER` | Bad input format/value | No | Fix parameter |
| `INSUFFICIENT_INVENTORY` | Out of stock | No | Choose different item |
| `PAYMENT_FAILED` | Payment processing failed | Yes | Try different payment |
| `SERVICE_UNAVAILABLE` | Temporary backend failure | Yes | Retry later |
| `UNAUTHORIZED` | Auth/permission issue | No | Check permissions |
| `TIMEOUT` | Service timeout | Yes | Retry |
| `RATE_LIMITED` | Too many requests | Yes | Wait then retry |
| `BUSINESS_RULE_VIOLATION` | Custom business logic failed | No | See message |

### Retry Semantics

**Framework Behavior**:
- `retriable: true` → Orchestrator may retry with exponential backoff
- `retriable: false` → Orchestrator returns error to user immediately
- Max retries: 3 (configurable)
- Backoff: 1s, 2s, 4s

**Connector Requirements**:
- Must set `retriable` field accurately
- Must provide clear `message` for user
- May include `data` with error context
```

### 3. Idempotency Specification (BLOCKING)

**Current State**: Section 4.2 mentions idempotency but lacks implementation details.

**Critical Questions**:

1. **Who generates `idempotencyKey`?**
   - AI Fabric? (Good - controlled)
   - Client? (Bad - can be manipulated)

2. **What's the key format?**
   ```
   "act_01H..."  // UUID? ULID? Timestamp-based?
   ```

3. **How long must connector remember keys?**
   - 24 hours? (Reasonable)
   - Forever? (Impractical)
   - Session-based? (Insufficient)

4. **What happens on duplicate?**
   - Return cached result? (Good)
   - Return error? (Bad - not idempotent)
   - Execute again? (Bad - defeats purpose)

**Recommended Addition**:

```markdown
## Idempotency Implementation (Replace Section 4.2)

### Key Generation

**AI Fabric generates** idempotency keys using ULID format:
- Format: `act_{ulid}` (26 character ULID)
- Example: `act_01HQRS123456789ABCDEFGHJK`
- Properties: Sortable, globally unique, timestamp-based

**Why ULID?**
- Sortable (contains timestamp)
- URL-safe (no special chars)
- Collision-resistant
- Database-friendly (indexed efficiently)

### Connector Storage Requirements

1. **Store Key + Result**:
   ```
   Key: "act_123"
   Result: { success: true, data: {...} }
   TTL: 24 hours
   ```

2. **On Duplicate Request**:
   - Lookup key in cache/database
   - If found: Return **same result** (not error)
   - Add header: `X-Idempotent: true`
   - Status: Still `200 OK`

3. **TTL (Time To Live)**:
   - Minimum: 24 hours
   - Recommended: 48 hours (safety buffer)
   - Rationale: Covers retry storms, network issues, user session recovery

### Example Flow

**Request 1** (First execution):
```bash
POST /actions/execute
{
  "actionId": "create_purchase_order",
  "idempotencyKey": "act_01HQRS123",
  "params": { "sku": "SKU-123", "quantity": 1 }
}
```
**Response 1**:
```json
HTTP 200 OK
{
  "success": true,
  "message": "Purchase order created",
  "data": { "orderRef": "PO-456" }
}
```

**Request 2** (Duplicate, 5 minutes later):
```bash
POST /actions/execute
{
  "actionId": "create_purchase_order",
  "idempotencyKey": "act_01HQRS123",  // Same key
  "params": { "sku": "SKU-123", "quantity": 1 }
}
```
**Response 2** (Cached):
```json
HTTP 200 OK
X-Idempotent: true

{
  "success": true,
  "message": "Purchase order created",
  "data": { "orderRef": "PO-456" }  // Same orderRef
}
```

### Storage Implementation Options

**Option 1: Redis** (Recommended)
```
SET act_01HQRS123 '{"success":true,...}' EX 86400
```
- Pros: TTL built-in, fast, scales horizontally
- Cons: Requires Redis infrastructure

**Option 2: Database Table**
```sql
CREATE TABLE idempotency_keys (
  key VARCHAR(30) PRIMARY KEY,
  result JSONB NOT NULL,
  expires_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_expires ON idempotency_keys(expires_at);
```
- Pros: ACID guarantees, no extra infra
- Cons: Slower than Redis, requires cleanup job

**Option 3: In-Memory** (Development Only)
```java
private final ConcurrentHashMap<String, CachedResult> cache;
```
- Pros: Simple, no dependencies
- Cons: Lost on restart, not distributed

### Edge Cases

**Different params, same key**:
- Should not happen (AI Fabric generates unique keys)
- If detected: Return error `IDEMPOTENCY_CONFLICT`

**Key expired**:
- Execute as new request
- Generate new result
- Store with new TTL
```

### 4. Reserved List Keys Validation (MEDIUM PRIORITY)

**Current State**: Section 4.1 mentions reserved keys but lacks validation rules.

**Problem**:
```json
{
  "success": true,
  "data": {
    "_items": "user-defined value"  // Conflict with framework!
  }
}
```

**Recommended Addition**:

```markdown
## Payload Contract Validation (Add to Section 4.1)

### List Payload Rules

If `data` contains `_items` key:
1. **MUST** be an array (not string, not object)
2. **MUST** contain `_count` field (integer)
3. **MAY** contain `_totalCount` (integer, for pagination)
4. **MAY** contain `_cursor` (string, for cursor-based pagination)
5. **MUST NOT** use other `_` prefixed keys

### Framework Validation (At Boot)

```java
public void validateActionPayload(String actionName, Map<String, Object> data) {
    if (data.containsKey("_items")) {
        Object items = data.get("_items");
        if (!(items instanceof List)) {
            throw new IllegalArgumentException(
                "Action '" + actionName + "': _items must be an array, got: "
                + items.getClass().getSimpleName()
            );
        }

        if (!data.containsKey("_count")) {
            throw new IllegalArgumentException(
                "Action '" + actionName + "': _count is required when _items is present"
            );
        }

        Object count = data.get("_count");
        if (!(count instanceof Number)) {
            throw new IllegalArgumentException(
                "Action '" + actionName + "': _count must be a number"
            );
        }
    }
}
```

### Connector Best Practices

**✅ Good: Namespace custom fields**
```json
{
  "success": true,
  "data": {
    "customerData": {
      "items": [...]  // Custom field, not _items
    }
  }
}
```

**❌ Bad: Use reserved prefix**
```json
{
  "success": true,
  "data": {
    "_items": "custom value"  // Conflicts with framework
  }
}
```

**Rule**: Avoid `_` prefix for custom fields. Framework reserves `_` prefix for standard fields.
```

### 5. Relay Security Model (HIGH PRIORITY)

**Current State**: Section 5.2 describes two relay patterns but lacks security depth.

**Critical Missing Pieces**:
1. User context forwarding (userId, trace)
2. Rate limiting per user
3. Audit logging requirements
4. Network security (TLS)

**Recommended Addition**:

```markdown
## Relay Security Model (Replace Section 5)

### Authentication Chain

```
User Request
    ↓
AI Fabric (verifies user session)
    ↓ [HMAC-signed request]
Relay (verifies AI Fabric signature)
    ↓ [Customer auth: API key / OAuth / mTLS]
Internal Service (re-authorizes user)
```

**Critical Principle**: Internal services MUST re-authorize.
Never trust AI Fabric's authorization alone. Defense in depth.

### User Context Forwarding

Relay MUST forward user identity and trace context:

```json
{
  "actionId": "create_purchase_order",
  "params": { "sku": "SKU-123", "quantity": 1 },
  "idempotencyKey": "act_01HQRS123",
  "trace": {
    "userId": "user-456",           // For authorization
    "conversationId": "chat-789",   // For audit trail
    "requestId": "req-012",         // For tracing
    "sessionId": "sess-345"         // For session context
  }
}
```

**Why Forward?**
- Internal service can enforce **its own authorization**
- Audit logs show **which user** performed action
- Distributed tracing connects **request flow**

### Rate Limiting

Relay enforces per-user action limits:

```yaml
# Relay config
rateLimits:
  perUser:
    windowSeconds: 60
    maxRequests: 100
  perAction:
    create_purchase_order:
      windowSeconds: 60
      maxRequests: 10
```

**Why at Relay?**
- Prevents abuse even if AI Fabric is compromised
- Protects internal services from overload
- Per-user limits prevent single user from DoS

**Rate Limit Error Response**:
```json
{
  "success": false,
  "errorCode": "RATE_LIMITED",
  "message": "Too many requests. Try again in 45 seconds.",
  "retriable": true,
  "data": {
    "retryAfterSeconds": 45
  }
}
```

### Audit Logging Requirements

Relay MUST log all requests:

**Log Fields**:
- Timestamp (ISO 8601)
- User ID (not PII like email)
- Action ID
- Request ID (for tracing)
- Response status (success/failure)
- Error code (if failure)
- Latency (milliseconds)

**Example Log Entry**:
```json
{
  "timestamp": "2026-02-11T15:30:45.123Z",
  "userId": "user-456",
  "actionId": "create_purchase_order",
  "requestId": "req-012",
  "status": "success",
  "latencyMs": 234
}
```

**What NOT to Log**:
- PII (email, phone, address)
- Sensitive params (password, payment info)
- Full request/response bodies

**Retention**: 90 days minimum (compliance requirement)

### Network Security

**AI Fabric → Relay**:
- TLS 1.3 (mandatory)
- HMAC-signed requests (mandatory)
- Optional: mTLS (enterprise)

**Relay → Internal Services**:
- TLS recommended (customer's choice)
- Authentication: API key / OAuth / mTLS
- Network isolation: Relay in customer VPC

### Relay Configuration Example

```yaml
# relay-config.yml
server:
  port: 8443
  tls:
    enabled: true
    cert: /etc/relay/cert.pem
    key: /etc/relay/key.pem

aiFabric:
  baseUrl: https://api.aifabric.com
  hmacSecret: ${HMAC_SECRET}  # From environment

actions:
  # Pattern A: Explicit mapping
  create_purchase_order:
    endpoint: http://internal-api:8080/orders
    method: POST
    timeout: 5s

  cancel_purchase_order:
    endpoint: http://internal-api:8080/orders/cancel
    method: POST
    timeout: 3s

  # Pattern B: Single dispatcher (alternative)
  # dispatcher:
  #   endpoint: http://internal-api:8080/actions
  #   method: POST

rateLimits:
  perUser:
    windowSeconds: 60
    maxRequests: 100

audit:
  enabled: true
  destination: file
  path: /var/log/relay/audit.log
  retention: 90d
```
```

---

## Productization Strategy Fit

### ✅ Excellent: Language-Agnostic Adoption

**Strategic Win**: This architecture removes the "rewrite in Java" barrier.

**Before**:
```
Customer: "We use Node.js/Python/Ruby"
Sales: "You need to rewrite actions in Java"
Customer: "That's 6 months of work. Pass."
```

**After**:
```
Customer: "We use Node.js/Python/Ruby"
Sales: "Keep your stack. Implement this HTTP contract."
Customer: "That's 2 weeks. Let's do it."
```

**Market Impact**: 10x larger addressable market.

### ✅ Excellent: Clear Monetization Tiers

| Tier | Local Actions | Connector | Relay | Features |
|------|--------------|-----------|-------|----------|
| **Community** | ✅ | ❌ | ❌ | Annotations only |
| **Pro** | ✅ | ✅ | Basic | HTTP connector + simple relay |
| **Enterprise** | ✅ | ✅ | Advanced | mTLS, HA, multi-region |

**Why This Works**:
- Community: Developers evaluate locally
- Pro: Small teams adopt with existing stack
- Enterprise: Large orgs get security + scale

### ✅ Excellent: Curated Packs Remain Transparent

**Document Quote**:
> "Curated packs / licensing provide: Modes + prompt optimizations + routing defaults.
> They do **not** hardcode business logic and do **not** require domain-specific entities."

**Why This Matters**:
- Packs are **configuration overlays** (transparent)
- Not **hidden business logic** (proprietary)
- Customers own their action catalog
- No vendor lock-in on domain logic

**This is the right monetization boundary**: Sell optimizations, not domain models.

### ✅ Excellent: Integrator-Friendly

**Repeatable Pattern**:
1. Define customer's action catalog (YAML/DB)
2. Implement connector (customer's stack)
3. Deploy relay (Docker/K8s)
4. Configure curated pack (commerce/support)
5. Done

**Consulting Leverage**: Each integration follows same pattern.

---

## Detailed Recommendations

### Blocking (Must Fix Before Implementation)

#### 1. Add Comprehensive Error Contract (Section 4.1)

**Add**:
- Standard error codes table
- Retry semantics
- Error response structure
- User-facing message guidance

**Why Blocking**: Without this, every connector will invent different error handling, making orchestration impossible to standardize.

#### 2. Complete Idempotency Specification (Section 4.2)

**Add**:
- Key format (ULID)
- TTL requirements (24-48 hours)
- Duplicate handling (return cached result)
- Storage options (Redis/DB/in-memory)

**Why Blocking**: Idempotency is critical for write operations. Ambiguous spec will lead to incorrect implementations.

#### 3. Expand Relay Security Model (Section 5)

**Add**:
- User context forwarding requirements
- Rate limiting specifications
- Audit logging requirements
- Network security (TLS mandatory)

**Why Blocking**: Security cannot be added later. Must be designed in from the start.

### High Priority (Before Beta)

#### 4. Add Action Registration Lifecycle (Section 3)

**Add**:
- Discovery phase steps
- Collision detection timing
- Schema validation rules
- Template safety (sensitive param redaction)

**Why High Priority**: Registration lifecycle ambiguity will cause startup failures and security issues.

#### 5. Add Reserved Keys Validation (Section 4.1)

**Add**:
- List payload rules
- Framework validation logic
- Connector best practices

**Why High Priority**: Payload conflicts will cause runtime errors. Better to validate at boot.

### Medium Priority (Before GA)

#### 6. Add Connector Implementation Guide

**Create New Document**: `CUSTOMER_CONNECTOR_IMPLEMENTATION_GUIDE.md`

**Contents**:
- Step-by-step tutorial
- Example implementations (Node.js, Python, Go)
- Testing tools
- Common pitfalls

**Why Medium Priority**: Helps customers implement connectors correctly, reduces support burden.

#### 7. Add Relay Deployment Guide

**Create New Document**: `RELAY_DEPLOYMENT_GUIDE.md`

**Contents**:
- Docker Compose example
- Kubernetes manifests
- Network topology diagrams
- Configuration reference

**Why Medium Priority**: Makes relay deployment reproducible, reduces deployment errors.

#### 8. Create OpenAPI Specification

**Create File**: `customer-connector-api.openapi.yml`

**Why Medium Priority**: Machine-readable spec enables:
- Code generation (client/server stubs)
- Validation tools
- API documentation sites

---

## Implementation Checklist

### Phase 1: Document Updates (Before Implementation)

- [ ] **Section 3.4**: Add Action Registration Lifecycle
  - [ ] Discovery phase
  - [ ] Collision detection
  - [ ] Schema validation
  - [ ] Template safety

- [ ] **Section 4.1**: Expand Error Response Contract
  - [ ] Error response structure
  - [ ] Standard error codes table
  - [ ] Retry semantics
  - [ ] User-facing messages

- [ ] **Section 4.1**: Add Reserved Keys Validation
  - [ ] List payload rules
  - [ ] Framework validation
  - [ ] Best practices

- [ ] **Section 4.2**: Complete Idempotency Specification
  - [ ] Key format (ULID)
  - [ ] TTL requirements
  - [ ] Duplicate handling
  - [ ] Storage options
  - [ ] Edge cases

- [ ] **Section 5**: Expand Relay Security Model
  - [ ] Authentication chain
  - [ ] User context forwarding
  - [ ] Rate limiting
  - [ ] Audit logging
  - [ ] Network security
  - [ ] Configuration example

### Phase 2: Companion Documents (Before Beta)

- [ ] Create `CUSTOMER_CONNECTOR_IMPLEMENTATION_GUIDE.md`
  - [ ] Step-by-step tutorial
  - [ ] Node.js example
  - [ ] Python example
  - [ ] Go example
  - [ ] Testing tools

- [ ] Create `RELAY_DEPLOYMENT_GUIDE.md`
  - [ ] Docker Compose
  - [ ] Kubernetes manifests
  - [ ] Configuration reference
  - [ ] Network diagrams

- [ ] Create `customer-connector-api.openapi.yml`
  - [ ] Request/response schemas
  - [ ] Error codes
  - [ ] Examples

### Phase 3: Reference Implementation (Before Beta)

- [ ] Build reference Relay implementation
  - [ ] Action routing (both patterns)
  - [ ] HMAC verification
  - [ ] Rate limiting
  - [ ] Audit logging
  - [ ] Health checks

- [ ] Build reference connectors
  - [ ] Node.js/Express
  - [ ] Python/FastAPI
  - [ ] Go/Gin

### Phase 4: Testing Tools (Before Beta)

- [ ] Connector validation tool
  - [ ] Schema validation
  - [ ] Idempotency testing
  - [ ] Error handling tests
  - [ ] Performance tests

- [ ] Relay testing tool
  - [ ] HMAC signature validator
  - [ ] Rate limit simulator
  - [ ] Audit log verifier

---

## Conclusion

### What This Document Gets Right

1. **Strategic Vision**: Language-agnostic architecture that enables 10x market expansion
2. **Philosophy Alignment**: Greenfield mindset, fail-closed security, separation of concerns
3. **Monetization Clarity**: Sell optimizations, not domain logic
4. **Concrete Examples**: Realistic YAML showing exactly what to implement
5. **Extension Through Trust**: Framework defines interfaces, customers implement

### What Needs Completion

The missing pieces are **implementation specifications**, not design flaws:
- Error handling details
- Idempotency mechanics
- Security model depth

These are critical for correct implementation but don't invalidate the core architecture.

### Final Assessment

**Rating: 9/10**

**Production Ready**: ✅ After implementing blocking items

This document represents **thoughtful, principled architecture** that will scale AI Fabric across diverse customer environments. With the recommended additions, it will be ready for Beta release.

The core insight—unifying local and remote actions under a single contract—is **architecturally sound** and **strategically brilliant**.

---

## Appendix: Quick Reference

### Document Status Summary

| Section | Status | Required Changes |
|---------|--------|------------------|
| 0. Goal | ✅ Complete | None |
| 1. Concepts | ✅ Complete | None |
| 2. Unifying Actions | ✅ Complete | None |
| 3. Action Catalog | ⚠️ Needs Addition | Add registration lifecycle |
| 4. Connector API | ⚠️ Needs Addition | Add error contract, idempotency details |
| 5. Relay | ⚠️ Needs Expansion | Add security model depth |
| 6. Confirmations | ✅ Complete | None |
| 7. Documents Retrieval | ✅ Complete | None |
| 8. Product Outcomes | ✅ Complete | None |

### Blocking Issues Summary

1. **Error Response Contract**: Required for standard error handling
2. **Idempotency Specification**: Required for write operation safety
3. **Relay Security Model**: Required for production deployments

### Timeline Recommendation

- **Week 1**: Complete blocking documentation updates
- **Week 2**: Create companion guides and OpenAPI spec
- **Week 3**: Build reference implementations
- **Week 4**: Create testing tools
- **Week 5**: Internal review and validation
- **Week 6**: Beta release

---

**Reviewed by**: AI Fabric Framework Team
**Next Review**: After blocking items implemented
**Contact**: For questions about this review, see project maintainers

---

*"In a world of compromise, we choose correctness. This document is 90% there—let's finish it right."*

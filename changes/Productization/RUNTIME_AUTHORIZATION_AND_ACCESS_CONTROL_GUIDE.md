# Runtime Authorization & Access Control (Product Deployments)

Status: draft (2026-03-25)

This document explains how **production runtime deployments** should implement authorization, and how to connect the runtime to a **customer-owned user/authorization API** or a **configuration-driven policy file**.

Related docs:
- `changes/Productization/DATA_SYNC_PUSH_API_GUIDE.md` (Data Sync uses the same access-control hook)
- `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md` (trace and defense-in-depth patterns)

---

## 0) TL;DR

- AI Fabric Runtime does **not** ship your business authorization rules.
- A production deployment should provide a Spring bean implementing `com.ai.infrastructure.access.policy.EntityAccessPolicy` (authorization decisions).
- Optionally provide `com.ai.infrastructure.chat.spi.ChatSessionAccessControlPolicy` (conversation-level rules).
- Dev/test: keep permissive defaults enabled (`ai.fabric.runtime.dev-defaults.enabled=true` or `AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED=true`).
- Production: disable dev defaults and provide a real `EntityAccessPolicy`.

---

## 1) Where Authorization Is Enforced

The runtime uses a single authorization hook (`EntityAccessPolicy`) via `AIAccessControlService`.

### 1.1 Chat / Orchestration Gate (runtime requests)

- Enforced by: `AccessControlStep` -> `AIAccessControlService` -> `EntityAccessPolicy`

Typical access request:
- `resourceId`: `rag:intent`
- `operationType`: `READ`
- `userId`: authenticated user id (if present)
- `sessionId`: anonymous session id (when userId is absent)

### 1.2 Data Sync Ingestion Gate (indexing/upserts/deletes)

- Enforced by: `DataSyncService` -> `AIAccessControlService` -> `EntityAccessPolicy`

Typical access request:
- `resourceId`: `vectorSpace:<entityType>` (example: `vectorSpace:product`)
- `operationType`: `WRITE` for upserts, `DELETE` for deletes
- `trace.userId`: required by the push API and becomes the actor id for authorization

### 1.3 Runtime Admin Endpoints

- `/api/admin/*` endpoints are protected separately via `app.admin.api-key`.
- In production: configure `APP_ADMIN_API_KEY` and send `X-ADMIN-API-KEY: <value>` (or override `app.admin.api-key-header`).

---

## 2) Who Should Provide `EntityAccessPolicy`

The **host application** that deploys the runtime service must provide it.

Concretely, this means:
- Your "runtime product" build/image should include a Spring `@Bean` (or `@Component`) implementing `EntityAccessPolicy`.
- The connector demo runtime config enables dev defaults and is not a production model.

---

## 3) What The Policy Receives (Inputs You Can Authorize On)

The hook signature is:

```java
boolean canUserAccessEntity(String userId, Map<String, Object> entity);
```

The `entity` map is built by `AIAccessControlService` and typically contains:
- `resourceId` (String)
- `operationType` (String)
- `timestamp` (LocalDateTime)
- `context` (String, optional)
- `purpose` (String, optional)
- `metadata` (Map, optional)
- `userAttributes` (Map, optional)

Examples:

Chat gate example:
- `resourceId=rag:intent`
- `operationType=READ`
- `metadata.entryPoint=RAG_ORCHESTRATOR`

Data Sync example:
- `resourceId=vectorSpace:product`
- `operationType=WRITE`
- `metadata.vectorSpace=product`
- `metadata.entityId=SKU-123`
- `metadata.tenantId=<from trace.metadata.tenantId if supplied>`

---

## 4) Option A: Authorization From A Config File (Allowlist/Rules)

Use this when:
- you want a simple "system user" allowlist (sync jobs, backfills), or
- you want a minimal first production cut (no external dependency).

### 4.1 Example policy file (YAML)

Add a file to your runtime deployment (mounted or baked) such as:

```yaml
app:
  authz:
    rules:
      - userId: "system_shopify_sync"
        resources: ["vectorSpace:*"]
        operations: ["WRITE", "DELETE"]
      - userId: "support_agent"
        resources: ["rag:intent"]
        operations: ["READ"]
```

### 4.2 Implementation sketch

Implement `EntityAccessPolicy` and load config using `@ConfigurationProperties`:

```java
@Component
@RequiredArgsConstructor
public class ConfigFileEntityAccessPolicy implements EntityAccessPolicy {
  private final AuthzRulesProperties rules;

  @Override
  public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
    String resourceId = Objects.toString(entity.get("resourceId"), "");
    String op = Objects.toString(entity.get("operationType"), "");
    return rules.allows(userId, resourceId, op);
  }
}
```

Recommended behavior:
- Deny by default.
- Treat missing/blank `userId` as denied.
- Keep rules small and auditable.

---

## 5) Option B: Call A Customer Authorization API (Recommended For Product)

Use this when:
- you already have a user/role/tenant service, or
- you need fine-grained and centrally managed authorization.

### 5.1 Suggested HTTP contract

Runtime (policy hook) calls your internal service:

- `POST /api/authz/check`

Request:

```json
{
  "userId": "u_123",
  "resourceId": "vectorSpace:review",
  "operationType": "WRITE",
  "metadata": { "tenantId": "t_1", "entityId": "801" }
}
```

Response:

```json
{ "granted": true, "reason": "OK" }
```

### 5.2 Implementation sketch (fail-closed)

Implement `EntityAccessPolicy` as an HTTP client:
- Short timeouts.
- Deny on timeout/5xx/unparseable response.
- Consider caching (tenant role checks are often stable for minutes).

Pseudo-implementation outline:

```java
@Component
@RequiredArgsConstructor
public class RemoteAuthzEntityAccessPolicy implements EntityAccessPolicy {
  private final RestClient restClient;

  @Override
  public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
    AuthzRequest req = AuthzRequest.from(userId, entity);
    try {
      AuthzResponse resp = restClient.post().uri("/api/authz/check").body(req).retrieve().body(AuthzResponse.class);
      return resp != null && resp.granted();
    } catch (Exception ex) {
      return false; // fail-closed
    }
  }
}
```

Operational guidance:
- Add metrics: total checks, denies, timeouts, 5xx.
- Add bounded retries only when safe.
- Cache "grant/deny" for a short TTL to protect your authz service.

---

## 6) Multi-Tenant Considerations

### 6.1 Data Sync

For ingestion, prefer passing tenant context explicitly:
- Set `trace.metadata.tenantId` in the push request.
- Enforce tenant boundaries in `EntityAccessPolicy` using `entity.metadata.tenantId`.

### 6.2 Chat

For chat requests, do not trust a client-supplied `userId` field as your sole auth mechanism.

Recommended production pattern:
- Authenticate at the edge (JWT/session).
- In a server-side filter/interceptor, populate the runtime's user identity (and tenant id) into the orchestration context.
- Ensure your `EntityAccessPolicy` denies cross-tenant access.

---

## 7) Deployment Modes (What To Configure)

### 7.1 Demo / Test

Goal: keep the runtime working without needing a custom authz service yet.

Runtime env vars:

```bash
AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED=true

# Optional: protect /api/admin/* endpoints (indexing overview, action catalog overview, clear-vectors)
APP_ADMIN_API_KEY="dev-admin-key"
APP_ADMIN_API_KEY_HEADER="X-ADMIN-API-KEY"
```

Notes:
- With dev defaults enabled, runtime registers an allow-all `EntityAccessPolicy` if you did not provide one.
- If you do not set `APP_ADMIN_API_KEY`, runtime admin endpoints are public (dev/test convenience). Do not ship that in production.

### 7.2 Production

Goal: explicit authorization with least-privilege and defense in depth.

Runtime env vars:

```bash
AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED=false

# Protect /api/admin/* endpoints
APP_ADMIN_API_KEY="<strong-secret>"
APP_ADMIN_API_KEY_HEADER="X-ADMIN-API-KEY"
```

Production checklist:
- Provide a real `EntityAccessPolicy` (config-file rules or remote authz API call).
- Enforce authentication at the edge (JWT/session) and do not trust client-supplied `userId` without verification.
- Restrict `/api/admin/*` at the network layer if possible (in addition to API key auth).
- Consider fail-closed behavior for misconfiguration: ensure missing/broken authz hooks do not produce 500s (return a deterministic deny instead via exception handling).

---

## 8) Common Failure Modes

- Everything becomes "Access denied by policy": no `EntityAccessPolicy` present and dev defaults disabled. Fix: ship a real policy or enable dev defaults in non-prod only.
- Data Sync returns `ACCESS_DENIED`: `trace.userId` missing or policy denies `vectorSpace:<space>` writes. Fix: provide `trace.userId` and grant WRITE/DELETE where appropriate.

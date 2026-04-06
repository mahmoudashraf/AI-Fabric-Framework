# PR #148 Review: Platform v3

**Branch:** `Platform-V3` → `main`
**Reviewed:** 2026-04-06
**Files changed:** 446 | **Additions:** +62,073 | **Deletions:** -1,431 | **Commits:** 102
**CI Status:** 10/12 passed, 2 in progress at review time

---

## Scope

This PR introduces enterprise multi-tenancy and a vectorization layer on top of the V2 platform:

**Wave 4 Track A — Multi-Tenancy:**
- Customer and tenant entities with hierarchical isolation
- Customer-scoped user access (`CUSTOMER_ADMIN` role)
- Tenant-scoped shared vector storage (Pinecone namespaces, Qdrant payloads, Weaviate tenants, Milvus partitions)
- Tenant binding governance and migration workflows

**Wave 4 Track B — Vectorization Layer:**
- Vectorization plans, source connections, runs, checkpoints, failure buckets
- Runner registration with token-based auth (SHA-256 hashed, 256-bit SecureRandom)
- Runner sessions with heartbeat-based lease management
- Vectorization verification (tenant isolation, reindex coverage)

**Wave 4 Track D — Provider Secret Overrides:**
- Deployment-scoped secret overrides with three-tier resolution (override → managed → global)
- Secret binding catalog with cleanup policies (KEEP, DELETE_ON_HARD_DELETE, DELETE_WHEN_UNREFERENCED)
- Paired secret support (e.g., Milvus username + password)

**Infrastructure:**
- Async deployment deletion (soft + hard) with audit trail
- AI widget (IIFE/ESM/CJS, deployed to GitHub Pages)
- Platform regression workflows (code + live admin)
- DB migrations V16-V23

---

## Critical Issues

### 1. Deployment Deletion Lacks Explicit ADMIN Authorization
- `DELETE /deployments/{id}` supports both soft and hard deletes but uses only class-level `@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")`.
- A `PLATFORM_OPERATOR` or `CUSTOMER_ADMIN` can trigger hard deletion.
- Fix: add `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`.

### 2. Provider Secret Binding Operations Lack ADMIN Authorization
- `GET/PUT/DELETE /deployments/{id}/provider-secret-bindings` — manages which secrets are bound to a deployment — class-level auth only.
- Fix: add `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` to all three methods.

### 3. Version Application and Draft Publishing Lack ADMIN Authorization
- `POST /deployments/{id}/apply/{versionId}` triggers production deployment.
- `POST /deployments/{id}/draft/publish` creates deployment versions.
- Both class-level auth only.

### 4. Remediation Execution Lacks ADMIN Authorization
- `POST /deployments/{id}/remediation/{actionKey}` — can trigger redeploy, restart, hard delete, Railway ops — class-level auth only.

---

## High-Severity Issues

### 5. Scope Token Collision in Tenant Vector Handles
- `TenantScopedVectorHandleResolver` normalizes customer/tenant names into scope tokens. Different customer+tenant combinations that normalize identically would share vector storage.
- Fix: add unique constraint on generated scope tokens, or include raw IDs.

### 6. Auth Disabled Mode Still Grants PLATFORM_ADMIN
- `PlatformApiKeyAuthenticationFilter`: when `properties.enabled()` is `false`, all requests get `PLATFORM_ADMIN`. Flagged in V1, still unfixed.

### 7. Missing CASCADE Delete on Vectorization Checkpoints/Failures
- `V19__vectorization_layer_foundation.sql`: checkpoint and failure bucket FKs don't specify `ON DELETE CASCADE`. Records orphan when runs are deleted.

### 8. Async Deletion Race Window
- Between `queueDeleteDeployment()` releasing the row lock and the async executor acquiring it in `transitionToRunning()`, a second delete request could be queued.
- Fix: unique constraint on `(deployment_id, status)` for active statuses.

### 9. Archive/Restore Inconsistency
- `POST /deployments/{id}/archive` and `/restore` use class-level auth only, while similar mutations (source, guardrails) require `PLATFORM_ADMIN`.

---

## Medium-Severity Issues

### 10. Secret References JSON in Plaintext
- `V19` stores `secret_references_json` in plaintext — reveals which secrets exist.

### 11. Widget Lacks Subresource Integrity (SRI)
- `deploy-widget.yml` serves widget from GitHub Pages with no SRI attribute.

### 12. Oversized Services
| File | Lines |
|------|-------|
| `VectorizationVerificationService.java` | 1,173 |
| `VectorizationService.java` | 966 |
| `PlatformCustomerTenantService.java` | 666 |

### 13. No Retry for Failed Async Deletions
- Failed deletions are permanently marked FAILED with no retry mechanism.

---

## Positive Observations

- **Tenant isolation**: strong multi-layer enforcement across customer access service, deployment access, and vector scoping
- **Runner auth**: excellent — 256-bit tokens, SHA-256 hashed, session leases, expiration tracking, revocation on rotation
- **Secret scope isolation**: three-tier resolution with deployment-scoped queries and reference-counted cleanup
- **DB schemas V16-V23**: well-designed with proper FKs, unique constraints, indexes, idempotent migrations
- **No credential logging**: verification stores only metadata, `assertNoInlineSecrets()` blocks plaintext in configs
- **Deletion audit**: comprehensive — tracks requester, role, reason, hard-delete flag, results, errors
- **CI**: includes new platform code regression gate workflow for PR validation

---

## Unresolved from V1/V2

- [ ] Directory typo `Platfrom/`
- [ ] Hardcoded credentials in config files
- [ ] CSRF disabled with session auth
- [ ] Session cookie `secure=false` default
- [ ] No rate limiting on auth endpoints
- [ ] Race conditions: approval consumption, admin guardrails, hosted verification dispatch, release recovery
- [ ] Weak password policy (10 chars)
- [ ] Password reset audit gap
- [ ] Implicit controller authorization (now worse with `CUSTOMER_ADMIN` in class-level)

---

## Summary Checklist

### Must fix before merge:
- [ ] Add `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` to delete, apply, publish, remediation, secret binding endpoints
- [ ] Add unique constraint or ID-based tokens in `TenantScopedVectorHandleResolver` to prevent collisions
- [ ] Add `ON DELETE CASCADE` to vectorization checkpoint/failure FK constraints
- [ ] Add unique constraint on `(deployment_id, status)` for async deletion operations
- [ ] Make archive/restore endpoints require ADMIN

### Should fix before production:
- [ ] Add SRI to widget script tags
- [ ] Add retry/manual recovery for failed async deletions
- [ ] Split oversized service classes
- [ ] Address auth-disabled bypass (startup guard in production)
- [ ] Address all unresolved V1/V2 issues

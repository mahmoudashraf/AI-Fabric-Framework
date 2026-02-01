# Enterprise Policy Management (DB + Routing + Governance) — Change Plan

## Status
Draft (pending implementation)

## Context / Dependencies
This plan builds on:
- `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md` (server-authoritative position→mode→policy)
- `changes/OPTIMIZATION_PROFILES_AND_DETERMINISTIC_RAG_INTEGRATION_PLAN.md` (profiles + deterministic flags)
- `changes/ENTERPRISE_PLUGGABLE_OPTIMIZATIONS_AND_PROMPT_MANAGEMENT_PLAN.md` (policy store SPI + enterprise seam)

## Goal
Provide an enterprise “policy control plane” to manage:
- profiles and modes (information mode, prompt mode, history limits, RAG limits),
- routing rules per tenant/app/environment/position,
- **action governance** (allowlists/denylists by action/category/access mode),
- safe rollout and audit.

## Non-goals
- Letting clients unlock privileged modes/actions by passing `mode`/`position`.
- Replacing the pipeline with custom enterprise logic (enterprise provides configuration + stores, not hidden behavior).

---

## 1) Module layout (Enterprise)

Proposed artifact:
- `ai-enterprise-policy-management`

Provides:
- `EnterpriseOrchestrationPolicyStore` (DB-backed policy store)
- `EnterpriseRoutingRulesStore` (DB-backed routing rules)
- `EnterprisePolicyManagementApi` (admin endpoints)

---

## 2) Data model (DB schema)

### 2.1 Tables (minimum viable)

1) `orchestration_policy`
- `id` (UUID)
- `profile` (string)
- `mode` (string, nullable)
- `position` (string, nullable)
- `information_mode` (string)
- `prompt_mode` (string)
- `config_json` (json/text) — bounded policy fields (history, attachments, RAG limits, etc.)
- `status` (enum: `DRAFT`, `APPROVED`, `ARCHIVED`)
- `created_at`, `created_by`, `approved_at`, `approved_by`

2) `routing_rule`
Server-authoritative routing for a request “scope”:
- `id` (UUID)
- `tenant_id` (nullable)
- `app_id` (nullable)
- `environment` (nullable)
- `position` (nullable)
- `mode` (nullable) — optional allowlist
- `target_profile` (string)
- `target_mode` (string, nullable)
- `priority` (int)
- `active` (bool)

3) `action_governance_rule`
- `id` (UUID)
- `tenant_id` / `app_id` / `environment` / `mode` (nullable)
- `action_name` (nullable)
- `category` (nullable)
- `access_mode` (nullable) — `READ`, `WRITE_ONLY`, `READ_WRITE`
- `decision` (enum: `ALLOW`, `DENY`)
- `priority` (int)
- `active` (bool)

4) `policy_audit_event`
- same pattern as prompt audit.

### 2.2 Constraints
- only `APPROVED` policies can be routed-to as active (strict mode recommended)
- routing must be deterministic (priority + tie-break rules)

---

## 3) Resolution logic (server-authoritative)

### 3.1 Routing precedence (example)
Given `(tenantId, appId, env, position)`:
1) match most specific rule (all fields) by highest `priority`
2) then progressively drop scope fields (position, env, app, tenant)
3) fall back to YAML/default pack routing (only if explicitly configured)

### 3.2 Policy merge rules
Policy effective = (profile defaults) + (mode overrides) + (explicit policy config)

Enterprise store supplies:
- policy objects
- routing rules

Apps can configure:

```yaml
ai:
  orchestration:
    policy-store: enterprise-db
```

---

## 4) Action governance (no client spoofing)

Policy must support deterministic action decisions:
- if an action is not allowlisted for the effective mode, it is denied
- denial must be explicit in the orchestration result (fail-closed)

This enables enterprise workflows like:
- “Support mode can execute `cancel_subscription` but not `create_purchase_order`”
- “Landing mode can only execute READ actions”

---

## 5) Admin API (REST)

Base path (example):
- `/api/admin/policies`

### 5.1 Policies
- `POST /policies` (create draft policy)
- `POST /policies/{id}/approve`
- `GET /policies?...`

### 5.2 Routing rules
- `PUT /routing-rules` (upsert)
- `GET /routing-rules?...`

### 5.3 Action governance
- `PUT /action-governance` (upsert)
- `GET /action-governance?...`

### 5.4 Audit
- `GET /audit?...`

---

## 6) Testing strategy

### 6.1 Unit tests
- routing rule specificity + priority resolution
- policy merge determinism
- governance evaluation determinism

### 6.2 Integration tests
- DB store + routing + governance end-to-end:
  - landing position denies WRITE actions
  - cart position allows commerce WRITE actions
  - per-tenant override changes behavior

---

## 7) Rollout sequence
1) Stabilize OSS policy object + position/mode routing (foundation).
2) Implement enterprise DB store for policies + routing (this plan).
3) Implement action governance rules.
4) Add experiments module (A/B on policies) as separate plan.


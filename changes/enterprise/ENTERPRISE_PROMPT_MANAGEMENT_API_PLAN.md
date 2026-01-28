# Enterprise Prompt Management (DB + Admin API) — Change Plan

## Status
Draft (pending implementation)

## Context / Dependencies
This plan assumes the OSS foundations exist and are stable:
- Prompt template externalization (classpath): `changes/PROMPT_TEMPLATES_EXTERNALIZATION_CHANGE_PLAN.md`
- Prompt store SPI + renderer: `changes/ENTERPRISE_PLUGGABLE_OPTIMIZATIONS_AND_PROMPT_MANAGEMENT_PLAN.md`
- Mode/profile routing (server-authoritative): `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md`

## Goal
Provide an **enterprise-grade** prompt management capability that allows operators to:
- create and version prompt templates,
- review/approve changes,
- scope prompt versions per tenant/app/environment/position/mode/provider variant,
- audit and roll back safely,
without forking code or redeploying the application for every prompt tweak.

## Non-goals
- Building a full UI in this repo (API-first; UI can be separate).
- “Latest prompt by default” behavior in production.
- Allowing client requests to pick arbitrary prompt versions (server-owned routing only).

## Design Principles (must-haves)
- **Fail-closed:** invalid/missing/unsafe templates must not silently change behavior.
- **Deterministic precedence:** explicit, documented override order.
- **Auditable:** every change is attributed, diffable, and timestamped.
- **Reproducible:** prompt rendering always records `templateId` + version + provider variant in metadata/debug.
- **Scoped:** prompt overrides can be limited to a tenant/app/environment/mode/position.

---

## 1) Module layout (Enterprise)

Proposed artifact:
- `ai-enterprise-prompt-management`

Provides:
- `EnterprisePromptTemplateStore` (DB-backed implementation of `PromptTemplateStore`)
- `EnterprisePromptManagementApi` (admin endpoints)
- optional: `EnterprisePromptBootstrapper` (initial seed from classpath into DB)

---

## 2) Data model (DB schema)

### 2.1 Tables (minimum viable)

1) `prompt_template`
- `id` (UUID)
- `namespace` (string, e.g. `intent-extraction`)
- `name` (string, e.g. `multi-step/classify`)
- `provider_variant` (string, e.g. `openai`, `anthropic`, `default`)
- `created_at`, `created_by`

2) `prompt_template_version`
- `id` (UUID)
- `template_id` (FK → `prompt_template.id`)
- `version` (string, e.g. `v1`, `v2`)
- `status` (enum: `DRAFT`, `APPROVED`, `ARCHIVED`)
- `content` (text)
- `sha256` (string)
- `created_at`, `created_by`
- `approved_at`, `approved_by` (nullable)
- `change_note` (text, nullable)

3) `prompt_assignment`
Maps a prompt template version to an “execution scope”.
- `id` (UUID)
- `template_version_id` (FK → `prompt_template_version.id`)
- `tenant_id` (string, nullable) — null means “global”
- `app_id` (string, nullable)
- `environment` (string, nullable) — e.g. `dev`, `staging`, `prod`
- `position` (string, nullable)
- `mode` (string, nullable)
- `purpose` (string, required) — e.g. `intent-extraction.multi-step.classify`
- `active` (bool)
- `created_at`, `created_by`

4) `prompt_audit_event`
- `id` (UUID)
- `event_type` (enum: `CREATE_TEMPLATE`, `CREATE_VERSION`, `APPROVE_VERSION`, `SET_ASSIGNMENT`, `ROLLBACK_ASSIGNMENT`, `ARCHIVE_VERSION`)
- `actor` (string)
- `timestamp`
- `details_json` (json/text)

### 2.2 Constraints (important)
- (`namespace`, `name`, `provider_variant`) unique in `prompt_template`
- (`template_id`, `version`) unique in `prompt_template_version`
- only `APPROVED` versions can be assigned as `active=true`

---

## 3) Resolution logic (server-authoritative)

### 3.1 Store selection
Enterprise-enabled apps configure:

```yaml
ai:
  prompts:
    store: enterprise-db
```

Fail-closed options:
- `strict`: app fails startup if DB is unreachable or assignments are invalid
- `fallback`: allowed only when explicitly enabled (never implicit)

### 3.2 Assignment precedence (deterministic)
Given an execution request `(tenantId, appId, env, position, mode, purpose, providerVariant)`:
1) Exact match (all fields)
2) Drop `position`
3) Drop `mode`
4) Drop `environment`
5) Drop `app_id`
6) Drop `tenant_id` (global)
7) Fall back to classpath default (only if configured)

Provider variant rules:
- Prefer exact `provider_variant`
- Else fall back to `default` provider variant for same template/purpose

---

## 4) Admin API (REST)

Base path (example):
- `/api/admin/prompts`

### 4.1 Templates
- `POST /templates`
  - create a template identity (`namespace`, `name`, `providerVariant`)
- `GET /templates?namespace=&name=&providerVariant=`

### 4.2 Versions
- `POST /templates/{templateId}/versions`
  - create `DRAFT` version with `version`, `content`, `changeNote`
- `POST /versions/{versionId}/approve`
- `POST /versions/{versionId}/archive`
- `GET /templates/{templateId}/versions`

### 4.3 Assignments
- `PUT /assignments`
  - upsert assignment for `(purpose, tenant/app/env/position/mode/providerVariant)` → `templateVersionId`
- `GET /assignments?...`
- `POST /assignments/{assignmentId}/rollback`
  - sets assignment to a previous approved version (explicit version id required)

### 4.4 Audit
- `GET /audit?since=&until=&actor=&eventType=`

Security/RBAC is out of scope for this plan, but the API must be designed to plug into:
- API gateway / SSO / RBAC filters
- audit actor attribution

---

## 5) Observability + safety

### 5.1 Metadata surfaced in debug snapshots
For every LLM call, include:
- `prompt.templatePurpose`
- `prompt.templateId`
- `prompt.templateVersion`
- `prompt.providerVariant`
- `prompt.sha256`

### 5.2 Validation gates (before activation)
- renderer validates required placeholders
- size limits (max chars) per template purpose
- “JSON-only” enforcement flags per purpose

Activation must fail if validation fails.

---

## 6) Test strategy

### 6.1 Unit tests
- assignment precedence resolution
- renderer placeholder validation (fail-closed)
- cannot assign non-approved version

### 6.2 Integration tests
- start DB (Testcontainers)
- seed template + versions + assignment
- verify orchestration uses assigned version and records metadata
- verify rollback changes effective version deterministically

---

## 7) Rollout sequence
1) Ship OSS prompt SPI + classpath store (foundation).
2) Implement enterprise DB store + admin API (this plan).
3) Add audit + approval enforcement.
4) Add experiments module (A/B + budgets) as separate plan.


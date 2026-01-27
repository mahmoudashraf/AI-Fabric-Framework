# Enterprise “Pluggable Optimizations” + Prompt Management — Implementation Plan

## Status
Proposed

## What this plan covers
This plan defines a clean OSS→Enterprise seam for:
1) **Prompt template externalization** (core capability, OSS-friendly).
2) **Enterprise prompt management** (DB-backed, audited, per-tenant, UI-driven).
3) **Enterprise “optimizations” as pluggable policies** (mode/profile/policy routing per tenant/app/position).

It is intentionally aligned with the framework philosophy:
- Greenfield: remove dead/legacy prompt patterns rather than supporting them forever.
- Contracts over heuristics: deterministic configuration and safe fallbacks.
- Fail-closed security: do not allow clients to unlock privileged modes/actions by spoofing inputs.

Related plans/docs:
- `changes/PROMPT_TEMPLATES_EXTERNALIZATION_CHANGE_PLAN.md` (prompt files + versioning)
- `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md` (position→mode→policy, server-authoritative)
- `changes/OPTIMIZATION_PROFILES_AND_DETERMINISTIC_RAG_INTEGRATION_PLAN.md` (profiles + deterministic integration)

---

## Motivation (why we should do this)
Framework users need:
- fast iteration on prompts and orchestration behavior without forking code,
- safe rollouts (version pinning, controlled experiments),
- strong governance (audit trails, approvals, RBAC),
- multi-tenant control (different policies per product/tenant/environment).

OSS should provide:
- the **interfaces (SPIs)** and safe defaults,
- a simple classpath/YAML implementation,
- deterministic tests and observability.

Enterprise can monetize:
- operations tooling (prompt management UI, approvals, audit, A/B, per-tenant overrides),
- deeper governance & policy routing,
- enterprise integrations (SSO, RBAC, compliance exports).

This keeps “core intelligence” open and monetizes “ops + governance + scale”.

---

## High-level architecture

### 1) Separate “what” from “where”
Core defines contracts:
- **Prompt templates**: how a prompt is identified, versioned, rendered, validated.
- **Orchestration policies**: how behavior is described, merged, and applied.
- **Mode/position routing**: how a request maps to an effective policy.

Implementations decide storage:
- OSS default: classpath resources + application YAML.
- Enterprise: DB-backed stores + admin APIs + UI + auditing.

### 2) Core SPIs (OSS)
Add small, stable interfaces in core:

#### Prompt templates
- `PromptTemplateId` (module + name + version + providerVariant)
- `PromptTemplate` (raw template text + required placeholders + metadata)
- `PromptRenderer` (renders template with placeholder validation, fail-closed)
- `PromptTemplateStore` (load template by id)

OSS implementations:
- `ClasspathPromptTemplateStore`
- `DefaultPromptRenderer` (strict placeholder validation, bounded output checks)

#### Orchestration policy
- `OrchestrationPolicy` (single object used by pipeline steps)
- `OrchestrationPolicyStore` (load policy/mode definitions)
- `OrchestrationPolicyResolver` (merge profile defaults + mode overrides + request signals)

OSS implementations:
- `YamlOrchestrationPolicyStore` (application.yml driven)
- `DefaultOrchestrationPolicyResolver`

---

## Enterprise module(s) (monetizable, pluggable)
Deliver enterprise functionality as separate Maven artifacts (separate module or separate repo):

### Module A — `ai-enterprise-prompt-management`
Capabilities:
- DB-backed `PromptTemplateStore` implementation (Postgres recommended).
- Admin REST API for:
  - CRUD templates (create version, deprecate version, rollback)
  - per-provider variants (openai/anthropic/gemini)
  - per-tenant/per-app overrides
  - approvals workflow (draft → review → approved → active)
  - audit export (who changed what/when; diffs)
- Optional UI (separate frontend) or integrate with existing admin console.

### Module B — `ai-enterprise-policy-management`
Capabilities:
- DB-backed `OrchestrationPolicyStore` and “mode routing” rules:
  - map `position`→`mode`, `app`→`profile`, tenant-specific overrides
  - action allowlists per mode (by action/category/accessMode)
  - safety defaults (confirmation policies, rate limits)
- Admin REST API + UI for:
  - policy CRUD
  - routing rules
  - approvals + audit

### Module C — `ai-enterprise-experiments` (optional)
Capabilities:
- A/B testing:
  - prompt version experiments
  - policy experiments
  - bucket by user/session/tenant
- Guardrails:
  - max cost/latency budgets per experiment
  - automatic rollback on SLO violation

---

## Configuration model (OSS + Enterprise)

### OSS: classpath + YAML (simple, deterministic)
- Prompts live in resources (as in `changes/PROMPT_TEMPLATES_EXTERNALIZATION_CHANGE_PLAN.md`)
- Policy/modes configured in `application.yml`

### Enterprise: DB-backed with safe fallbacks
Add a single selector:

```yaml
ai:
  prompts:
    store: classpath | enterprise-db
  orchestration:
    policy-store: yaml | enterprise-db
```

Fail-closed rules:
- If enterprise store fails (DB down), either:
  - **fail startup** (strict production), or
  - fallback to classpath/yaml **only if explicitly enabled** (avoid silent behavior drift).

---

## Prompt template model (details)

### 1) Versioning rules
- Template IDs must include version: `v1`, `v2`, etc.
- “Active version” is a config/policy choice; never “latest by default” in production.
- Provider variants are explicit: `v1-openai`, `v1-anthropic`, etc.

### 2) Rendering safety (core)
`PromptRenderer` must:
- validate required placeholders are present (fail-closed)
- reject unknown placeholders in strict mode (prevents typos)
- enforce bounded output size (avoid accidental huge prompts)
- optionally strip unsafe user-provided inputs (PII module integration, if enabled)

### 3) Observability
Attach deterministic metadata (not user-visible):
- `prompt.templateId`
- `prompt.version`
- `prompt.providerVariant`
- `policy.profile/mode`

This must be included in debug snapshots and realapi reports.

---

## Orchestration policy model (details)

### Policy is a bundle, not micro-flags
The policy object should own:
- `informationMode` (LLM_DRIVEN vs DETERMINISTIC_RAG_GENERATE)
- `promptMode` (FULL_CONTRACT vs MINIMAL_FOR_RAG)
- attachments enablement + constraints
- working set enablement
- history windows/limits
- RAG thresholds and fan-out limits
- action allowlists and confirmation defaults

### Precedence rules (must be deterministic)
1) profile defaults
2) mode overrides
3) request position → mode routing (server-side)
4) explicit overrides (if allowed) for specific app/tenant environments

Security:
- request-provided `mode` is treated as a *hint*; server must allowlist it.

---

## Packaging / monetization strategy (recommended)

### Keep core OSS-friendly
Core ships:
- SPIs
- classpath/yaml implementations
- deterministic tests

### Monetize ops + governance
Enterprise modules ship:
- DB-backed stores
- approval workflows
- audit trails
- tenant routing
- experiment framework

This avoids the anti-pattern of “secret better orchestration” and instead sells what enterprises pay for:
control, governance, safety, and operational convenience.

---

## Implementation roadmap (phased)

### Phase 1 — OSS: prompt externalization SPI + classpath implementation
Deliver `changes/PROMPT_TEMPLATES_EXTERNALIZATION_CHANGE_PLAN.md`:
- replace hardcoded prompts with versioned resources
- add renderer validation
- add unit tests for template loading/rendering

### Phase 2 — OSS: policy object + resolver used by pipeline steps
Deliver `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md`:
- resolve effective policy per request (profile + mode + position)
- pipeline reads policy, not scattered flags
- emit effective policy metadata

### Phase 3 — OSS: “store” SPI and fallback semantics
- introduce `PromptTemplateStore` and `OrchestrationPolicyStore` interfaces
- wire classpath/yaml as default stores
- allow selecting store implementation via config

### Phase 4 — Enterprise: DB-backed stores (no UI yet)
- implement DB schema + migrations
- implement `EnterpriseDbPromptTemplateStore`
- implement `EnterpriseDbPolicyStore`
- implement caching with TTL + invalidation

### Phase 5 — Enterprise: admin APIs + audit
- add admin endpoints
- add audit log schema (append-only)
- add RBAC hooks (Spring Security integration)

### Phase 6 — Enterprise: UI + experiments (optional)
- UI for templates/policies/routing
- A/B experiments + rollback tooling

---

## Testing strategy

### OSS
- Unit tests:
  - placeholder validation (missing → fail)
  - version selection is deterministic
  - policy resolver precedence
- Integration tests:
  - realapi suites pin prompt/policy versions
  - confirm effective policy metadata is present

### Enterprise
- Store integration tests:
  - migrations apply
  - CRUD works
  - audit entries created
  - fallback behavior correct when DB is unavailable (per config)
- Security tests:
  - client cannot request privileged mode unless allowed
  - RBAC enforced on admin APIs

---

## Acceptance criteria
1) OSS users can externalize prompts and pin versions without enterprise modules.
2) OSS users can define modes/policies in YAML and route by position deterministically.
3) Enterprise users can manage prompts/policies via DB with audit and approvals.
4) Effective prompt/policy versions are observable and testable.


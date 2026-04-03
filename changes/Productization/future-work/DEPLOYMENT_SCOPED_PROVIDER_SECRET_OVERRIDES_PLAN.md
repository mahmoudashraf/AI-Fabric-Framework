# Deployment-Scoped Provider Secret Overrides Plan

Status: planning document (2026-04-03)

This document describes a future platform capability for deployment-scoped provider secret overrides.

The goal is to let operators keep global platform secrets as the default model, while allowing selected deployments to use dedicated credentials when needed.

Typical examples:

- one deployment uses a customer-owned OpenAI key
- another deployment uses the platform-owned OpenAI key
- one Pinecone deployment uses a dedicated project key
- one enterprise customer requires isolated provider credentials per environment or account

This is an optional extension of the current secret model, not a replacement for it.

---

## 1) Current State

Today the platform uses a mixed secret model:

- most provider secrets are global platform secrets
- some platform-managed resources generate deployment-scoped managed secrets

Examples of current global secrets:

- `OPENAI_API_KEY`
- `AZURE_OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`
- `COHERE_API_KEY`
- `GEMINI_API_KEY`
- `PINECONE_API_KEY`
- `QDRANT_CLOUD_MANAGEMENT_API_KEY`
- `WEAVIATE_API_KEY`
- `ZILLIZ_CLOUD_API_KEY`

Examples of current deployment-scoped managed secrets:

- platform-generated Pinecone runtime API key aliases
- platform-generated Qdrant Cloud database API keys
- platform-generated Milvus/Zilliz runtime usernames/passwords

So today:

- two normal OpenAI deployments will share the same `OPENAI_API_KEY`
- changing that key changes the source secret globally
- deployments typically pick up the change on the next apply/redeploy, not instantly

This is workable for a platform-owned operator model, but it is too coarse for multi-customer isolation and customer-provided credentials.

---

## 2) Product Goal

Add a platform capability that allows a deployment to override selected provider secrets without changing the global platform defaults.

The desired model is:

- global platform secret remains the default fallback
- deployment may opt into a deployment-scoped provider secret reference
- raw secret values are never stored directly in deployment draft config
- the deployment stores only a secret reference or alias

This should support use cases like:

- customer A OpenAI key on deployment A
- customer B OpenAI key on deployment B
- shared platform key on internal/demo deployments
- environment-specific vendor credentials

---

## 3) Why This Matters

This feature improves:

- customer isolation
- enterprise credential ownership
- vendor cost attribution
- rotation flexibility
- safer onboarding of customer-managed provider accounts

It also lowers operational blast radius:

- rotating one customer deployment’s key should not disturb unrelated deployments

This is especially important once the platform is used across:

- multiple customers
- multiple environments
- multiple billing owners
- mixed platform-managed and customer-managed deployments

---

## 4) Non-Goals

This feature should **not**:

- allow raw provider credentials to be stored in draft JSON
- replace platform-wide default secrets
- remove managed deployment-generated secret flows
- require every deployment to define dedicated provider secrets
- become a general arbitrary secret templating engine in phase 1

The first version should stay narrow and predictable.

---

## 5) Recommended Secret Model

The clean model is:

### 5.1 Secret scopes

- `GLOBAL_PLATFORM`
  - current platform secret store entries like `OPENAI_API_KEY`
- `DEPLOYMENT_OVERRIDE`
  - deployment-specific provider credential aliases
- `DEPLOYMENT_MANAGED`
  - platform-generated deployment secrets for managed resources

### 5.2 Resolution precedence

For a provider credential required during provisioning/runtime:

1. deployment-scoped provider override
2. deployment-managed secret if the platform generated one for that vendor path
3. global platform secret
4. environment fallback only where explicitly allowed

This precedence should be explicit and auditable.

### 5.3 Draft representation

Draft config should not store the secret value.

Instead it should store something like:

- `providerSecretOverrides.openai = DEPLOYMENT_OPENAI_API_KEY`
- or a provider-specific alias like:
  - `openaiApiKeySecretName = DEPLOYMENT_OPENAI_API_KEY`

The important property is:

- the draft references a secret name
- the value remains in the secret store

---

## 6) Recommended Platform UX

### 6.1 Secrets workspace

The `Secrets` workspace should evolve from:

- global catalog only

to:

- global secret defaults
- optional deployment-scoped overrides

Recommended UI:

- tab 1: `Global defaults`
- tab 2: `Deployment overrides`

For a selected deployment, show provider override rows like:

- `OpenAI API Key override`
- `Pinecone API Key override`
- `Azure OpenAI API Key override`

Each row should show:

- scope
- secret name
- source
- present/missing
- last updated
- actor

### 6.2 Providers workspace

The `Providers` page should show effective credential resolution without exposing raw values.

Examples:

- `OpenAI credential source: deployment override`
- `OpenAI credential source: global platform default`
- `Pinecone credential source: deployment-managed`

This gives operators clarity without exposing secret material.

### 6.3 Verification and diagnostics

Verification should report:

- which credential scope was used
- whether the referenced secret existed
- whether the deployment is still using global fallback

This belongs in:

- provider connectivity diagnostics
- release verification evidence
- secret usage summaries

---

## 7) Recommended Data Model

There are two reasonable ways to model this.

### Option A: Extend the platform secret table with scope fields

Add fields like:

- `scopeType`
  - `GLOBAL_PLATFORM`
  - `DEPLOYMENT_OVERRIDE`
  - `DEPLOYMENT_MANAGED`
- `deploymentId` nullable
- `secretPurpose` optional

Pros:

- one secret system
- one audit system
- one query model

Cons:

- existing table semantics become broader

### Option B: Separate deployment secret table

Add a second table for deployment-scoped secrets.

Pros:

- clearer isolation
- simpler permission boundaries

Cons:

- duplicate logic for resolution, audit, and UI

Recommendation:

- prefer **Option A**
- keep one secret store model with explicit scope and ownership metadata

---

## 8) Security And Governance

This feature must be governed carefully.

### 8.1 Required rules

- raw values never appear in draft/config artifacts
- only secret references appear in deployment config
- deployment-scoped secret writes are audited with actor + timestamp
- secret reads remain value-hidden in UI
- deployment-scoped overrides are limited by role

### 8.2 Access control

Recommended:

- platform admin can create/update any deployment override
- deployment admin may create/update overrides only for assigned deployments
- deployment viewer/operator cannot view raw values

### 8.3 Delete behavior

If an override is removed:

- deployment falls back to global default if available
- if no global default exists, validation should block publish/apply

This fallback behavior must be explicit in UI.

---

## 9) Provider Coverage

Phase 1 should focus on provider secrets that are most likely to need per-deployment isolation.

Recommended first set:

- `OPENAI_API_KEY`
- `AZURE_OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`
- `COHERE_API_KEY`
- `GEMINI_API_KEY`
- `PINECONE_API_KEY`
- `WEAVIATE_API_KEY`
- `QDRANT_API_KEY`
- optional `MILVUS_USERNAME` / `MILVUS_PASSWORD`

Not all secrets need overrides on day 1, but the model should support them.

Management-plane secrets like:

- `QDRANT_CLOUD_MANAGEMENT_API_KEY`
- `ZILLIZ_CLOUD_API_KEY`

may remain global longer, unless platform-managed vendor resources become customer-specific per deployment.

---

## 10) Provisioning And Runtime Behavior

Provisioning logic should resolve the effective secret at apply time using the defined precedence.

For Railway-backed deployments:

- generated env vars should continue to use `${secret:...}` placeholders internally
- but the resolved secret name may now point at a deployment-scoped override rather than a global default

Managed resource provisioning should continue to generate deployment-scoped secrets where needed.

This means the model becomes:

- provider-selected secret reference
- secret resolution
- env generation
- deploy

without leaking raw credentials into deployment configs.

---

## 11) Secret Usage And Audit

The current secret usage view should evolve to show:

- secret scope
- effective resolution source
- fallback chain
- actor and timestamp for last override change

Examples:

- `OPENAI_API_KEY`
  - source: `DEPLOYMENT_OVERRIDE`
  - effective secret: `DEPLOYMENT_OPENAI_API_KEY`
- `PINECONE_API_KEY`
  - source: `GLOBAL_PLATFORM`
  - effective secret: `PINECONE_API_KEY`

This makes support and incident debugging far easier.

---

## 12) Migration Strategy

This feature should be additive.

Initial migration posture:

- existing deployments continue using global platform secrets unchanged
- deployments only use overrides when an operator explicitly creates and selects one
- no existing deployment should break after the feature is introduced

This keeps rollout risk low.

---

## 13) Recommended Implementation Sequence

### Phase 1: Secret scope foundation

1. extend the secret model to support secret scope and optional deployment ownership
2. preserve current global secret behavior as-is
3. add audit support for scoped secret writes

### Phase 2: Deployment override references

4. add deployment-level provider secret reference fields
5. add effective secret resolution precedence
6. update secret usage and diagnostics

### Phase 3: UI support

7. add deployment override management in the `Secrets` workspace
8. show effective secret source in `Providers`
9. add validation and support messaging

### Phase 4: Verification and governance

10. update release verification to report effective secret scope
11. add drift/fallback checks
12. add cleanup behavior for deployment-scoped secrets when deployments are hard deleted

---

## 14) Relationship To Managed Vector Secrets

This plan must align with the existing managed secret pattern, not conflict with it.

Current managed vector provisioning already creates deployment-specific secrets such as:

- managed Pinecone runtime key aliases
- managed Qdrant database API keys
- managed Milvus credentials

So this feature is conceptually a generalization of a pattern the platform already uses.

The difference is:

- today deployment-scoped secrets are mostly platform-generated for managed vector resources
- future state should also allow deployment-scoped operator-provided provider credentials

---

## 15) Recommended Product Position

The product should present this as:

- `Global default credential`
- `Optional deployment override`

not as:

- an all-or-nothing secret isolation model

That preserves the simple default path while enabling enterprise isolation where needed.

---

## 16) Completion Criteria

This capability is complete when:

- deployments can optionally use dedicated provider credentials
- global platform secrets remain the default fallback
- raw secret values never appear in draft config or published artifacts
- effective secret scope is visible in UI and diagnostics
- secret changes are audited by actor and timestamp
- hard delete can safely clean deployment-scoped operator overrides and managed deployment secrets
- existing deployments continue working without migration edits

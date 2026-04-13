# Beta Release Scope & Gates — Change Plan

## Status
Draft

## Why this document exists
The repo has a strong set of modules, but “beta” needs an explicit definition so we can ship on time without scope creep.

This plan defines:
- What we are shipping as **Beta**
- What is explicitly **not required for Beta**
- Concrete **go/no-go** release gates

## Beta definition (proposed)
**Beta = public preview**: feature-complete for the intended core workflows, but API surface may still change.

### Beta promises
- Core modules build and run reliably with pinned versions.
- At least one “happy path” end-to-end works with documented setup:
  - Provider: `onnx` or `openai`
  - Vector DB: `lucene` or `memory`
  - App sample: at least one under `Real_Apps/`
- No secrets in repo; sample configs use environment variables.
- Clear Community vs Enterprise boundaries in build + docs (monorepo).

### Not required for Beta (explicitly)
- 80%+ coverage across *all* modules (keep it as GA target).
- Publishing every provider/vector-db to Maven Central (ship Community first; Enterprise can be “source available” without public artifacts).
- Enterprise-only features like multi-tenancy/RBAC/SSO implementations (can be stubbed as future plans).

## Scope for Beta (proposed)

### Community (Beta must-have)
Buildable + documented:
- `ai-infrastructure-module/ai-infrastructure-core`
- `ai-infrastructure-module/ai-infrastructure-indexing`
- `ai-infrastructure-module/ai-infrastructure-rag`
- `ai-infrastructure-module/ai-infrastructure-pii`
- `ai-infrastructure-module/ai-infrastructure-web` (if “REST-first” is part of the beta story)
- Providers:
  - `ai-infrastructure-module/providers/ai-infrastructure-onnx-starter`
  - `ai-infrastructure-module/providers/ai-infrastructure-provider-openai`
  - `ai-infrastructure-module/providers/ai-infrastructure-provider-rest` (optional, but useful)
- Vector DBs:
  - `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-lucene`
  - `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-memory`
- At least 1 Real app demo that matches the beta story (chat or RAG).

### Enterprise (Beta may include, but must be isolated)
If kept in-repo for monorepo, it must not leak into Community build artifacts:
- Providers: Azure / Anthropic / Cohere / Gemini
- Vector DBs: Pinecone / Qdrant / Weaviate / Milvus

## Go/No-Go Gates (Beta)
### Gate A — Build matrix (required)
- [ ] `mvn -f ai-infrastructure-module/pom.xml -Pcommunity test` passes
- [ ] `mvn -f ai-infrastructure-module/pom.xml -Penterprise test` passes (optional for beta if enterprise is “source only”, but recommended)

### Gate B — Dependency boundaries (required)
- [ ] Community build has **zero** compile/runtime dependency on enterprise-only modules
- [ ] Enterprise modules may depend on Community modules (one-way dependency)

### Gate C — Documentation (required)
- [ ] Community quick start works end-to-end (copy/paste)
- [ ] Community vs Enterprise feature table exists and is accurate

### Gate D — Security hygiene (required)
- [ ] Secret scan passes (no API keys, tokens, private URLs)
- [ ] Example configs use `${ENV_VAR}` placeholders

## Deliverables
- A beta tag (proposed): `v0.1.0-beta.1` (or keep `1.0.0-beta.1` if you want marketing alignment)
- A short release note:
  - what’s supported
  - what’s experimental
  - migration expectations


# Shipping Process & Distribution Options

## Status
Draft (review requested)

## Purpose
Describe **how AI Fabric is shipped** (artifacts and release steps) and the **supported distribution options** for:
- framework/library usage (BYO app)
- runnable runtime usage (hosted/self-hosted)
- future managed control-plane provisioning (Phase E)

This document is intentionally **implementation-aware** (matches today’s module layout) and **forward-looking** (calls out planned work).

---

## Definitions

### “Ship the product”
Shipping means producing **versioned artifacts** and **clear docs** that let a developer:
1) depend on AI Fabric modules (Maven)
2) run a reference “runtime” (Jar/Docker)
3) choose/plug a vector DB and an LLM provider
4) enable optional capabilities (actions, relay, retrieval connector) without forking

### “Core”
Core is the **request-handling runtime chain**:
`HTTP API → Orchestrator → (Retrieval + Actions + Confirmation) → Response`

Today the golden-path API contract is `POST /api/chat/query` documented in:
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

---

## What we ship (artifacts)

### A) Maven libraries (the platform)
Ship versioned Maven artifacts from `ai-infrastructure-module/`:
- **Core orchestration**: `ai-fabric-core`
- **Web layer**: `ai-fabric-web` (REST controllers for AI Fabric)
- **RAG**: `ai-infrastructure-rag`
- **Providers**: OpenAI/Azure/Anthropic/… provider modules
- **Vector DB implementations**: Lucene/Qdrant/Pinecone/…
- **Optional capability modules** (opt-in):
  - actions connector / registry / liquibase runner
  - retrieval connector (documents-only)
  - relay service
  - chat session persistence
  - governance / pii

Notes:
- The repo currently contains a **draft naming/coordinates cleanup** plan:
  - `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md`
  Until that is finalized, prefer depending on **starters** where possible.

### B) Starters (recommended consumption path)
Ship “convenience starters” that define safe defaults:
- `ai-fabric-provider-starter`: “core + provider-only baseline”
- `ai-fabric-starter`: “core + indexing (+ optional safety modules)”

Future (recommended):
- `ai-fabric-runtime-starter`: “core + web request API + runtime defaults”
  - goal: consumers add a single dependency and expose `/api/chat/query`

### C) Curated packs (the sellable value)
Ship curated packs as separate artifacts (prompt bundles + mode defaults), activated by:
- `ai.curated.pack=<pack>`

Example loader:
- `ai-infrastructure-module/ai-infrastructure-core/.../CuratedPackEnvironmentPostProcessor.java`

This is the natural place for “curated + license unlocks modes”:
- packs remain domain-agnostic (modes, prompts, constraints)
- licensing gates *pack activation* (planned, not required for v0)

### D) Runnable distributions

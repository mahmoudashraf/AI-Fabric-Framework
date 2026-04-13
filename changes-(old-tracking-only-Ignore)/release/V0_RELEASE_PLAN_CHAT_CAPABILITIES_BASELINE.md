# v0 Release Plan — Chat Capabilities Baseline (Developer-Friendly Preview)

**Purpose:** ship a credible, developer-friendly `v0.x` release that is easy to run end-to-end, using the existing **Chat Capabilities Demo** as the “golden path”.

This plan is intentionally narrow to maximize reliability, reduce surface area, and create strong public evidence (tag + release notes + working demo + docs).

Primary demo app:
- `Real_Apps/chat-capabilities-demo`

Related release planning docs (existing):
- `changes/release/BETA_RELEASE_SCOPE_AND_GATES.md`
- `changes/release/MONOREPO_COMMUNITY_ENTERPRISE_SEPARATION_CHANGE_PLAN.md`
- `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md`

Quickstart (what developers should run):
- `docs/V0_QUICKSTART.md`

---

## Status (as of 2026-02-13)

- Quickstart doc added: `docs/V0_QUICKSTART.md`
- Build gate verified (in repo environment):
  - `mvn -f ai-infrastructure-module/pom.xml -DskipTests install`
  - `mvn -f Real_Apps/chat-capabilities-demo/pom.xml test`
- Pending for the actual public release:
  - tag + release notes
  - short demo video link
  - final “supported set” statement in root README

---

## 0) Release definition (what “v0” means)

`v0.x` = **public preview**:
- the end-to-end workflow is stable and documented
- APIs/config are “best effort stable” but can change
- only a subset of modules/providers are **officially supported**

**Why v0 (now):**
- produces an artifact you can point to (tag + release notes + demo)
- forces a stable contract and developer experience
- avoids the endless “productization without a stable core”

---

## 1) Golden path (what must work in v0)

### 1.1 End-to-end user story
From a clean machine:
1) Build/install framework modules locally
2) Run the demo app
3) Use the documented HTTP requests to:
   - start a chat session
   - perform retrieval over a product catalog
   - execute at least one action from chat
   - confirm/reject actions using the confirmation flow

### 1.2 Supported providers (v0)
To minimize integration complexity and support load:
- **LLM + embeddings:** OpenAI (only)
- **Vector DB:** Lucene (embedded)

Everything else is **not supported in v0** (may remain in repo, but not promised).

Why:
- The demo already assumes OpenAI and Lucene.
- This keeps the support matrix tiny and reproducible.

---

## 2) What to release (official “supported set”)

This is the **supported set** for `v0.x`. It is what we test, document, and stand behind.

### 2.1 Framework modules (required)
- `ai-infrastructure-module/ai-infrastructure-core`
  - orchestration core, actions model, safety semantics
- `ai-infrastructure-module/ai-infrastructure-rag`
  - retrieval + indexing service layer
- `ai-infrastructure-module/ai-infrastructure-indexing`
  - indexing coordination primitives (used by demo)
- `ai-infrastructure-module/ai-infrastructure-chat-session`
  - chat sessions + conversation recording/pipeline steps
- `ai-infrastructure-module/ai-infrastructure-governance`
  - currently required by `Real_Apps/chat-capabilities-demo` (keep scope minimal for v0)
- `ai-infrastructure-module/ai-infrastructure-web`
  - REST endpoints that are part of the “framework story” (if not strictly required by demo, keep but don’t expand scope)

### 2.2 Starters + curated packs (required)
- `ai-infrastructure-module/ai-fabric-starter`
  - “one dependency” starter used by real apps
- `ai-infrastructure-module/ai-fabric-provider-starter` (optional; keep if it simplifies setup)
- Curated pack(s):
  - `ai-infrastructure-module/curated/ai-curated-commerce` (required for chat demo)
  - `ai-infrastructure-module/curated/ai-curated-default` (optional but recommended as baseline)

### 2.3 Provider + Vector DB modules (required)
- `ai-infrastructure-module/providers/ai-infrastructure-provider-openai`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-lucene`

### 2.4 Demo application(s) (required)
- `Real_Apps/chat-capabilities-demo`
  - this is the v0 “golden path” application
  - must run from README instructions
  - must expose Swagger/OpenAPI and the `/api/chat/query` contract documented in:
    - `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

---

## 3) What NOT to release (explicitly out-of-scope for v0 support)

These can remain in the repo, but they are **not supported** (no guarantees, no docs, no release gate).

### 3.1 Enterprise / extended providers (out-of-scope)
- `ai-infrastructure-module/providers/ai-infrastructure-provider-azure`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-anthropic`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-cohere`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-gemini`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-rest` (optional; keep in repo, but do not promise)
- `ai-infrastructure-module/providers/ai-infrastructure-onnx-starter` (optional; keep in repo, but do not promise)

### 3.2 External vector databases (out-of-scope)
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-qdrant`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-weaviate`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-milvus`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-pinecone`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-memory` (could be supported, but not needed for the golden path)

Rationale:
- they expand the support matrix dramatically (networking, auth, vendor quirks)
- they are better positioned as a post-v0 milestone

### 3.3 Advanced modules (out-of-scope)
- `ai-infrastructure-module/ai-infrastructure-relationship-query`
- `ai-infrastructure-module/ai-infrastructure-migration`
- `ai-infrastructure-module/ai-infrastructure-behavior`

Note:
- The current chat demo depends on `ai-infrastructure-governance`. For v0 scope we keep it in the **supported set**,
  but do not expand its surface area beyond what the demo uses.

### 3.4 Integration testing modules (not part of v0 deliverable)
- `ai-infrastructure-module/integration-Testing/*`

These are valuable internally but should not be “part of the product” narrative for v0.

---

## 4) Release packaging strategy (what we publish)

We want developer-friendly, but the fastest credible v0 path is:

### 4.1 v0 deliverables (minimum)
1) Git tag: `v0.1.0` (or `v0.1.0-preview.1`)
2) GitHub release notes:
   - supported matrix
   - quickstart
   - known limitations
3) Demo video link (3–5 minutes)
4) Demo app instructions verified end-to-end

### 4.2 Artifact publishing (optional for v0.1)
Publishing Maven artifacts is great, but it may be risky if coordinates are inconsistent.

Known risk:
- `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md` indicates artifactId vs BOM/DM mismatches.

Two acceptable approaches:
- **Approach A (safe for v0.1):** do NOT publish to Maven Central; instruct “build from source” for now.
- **Approach B (better DX, higher risk):** publish only the supported subset after fixing coordinates for that subset.

Recommendation for a 3-week window:
- ship v0.1 with Approach A if needed
- schedule v0.2 for publishing after coordinate cleanup + boundary enforcement

---

## 5) Release gates (go/no-go)

### Gate 1 — Clean “golden path” run (required)
The following works from scratch (documented):
- build framework
- run `Real_Apps/chat-capabilities-demo`
- complete the demo HTTP flow with OpenAI configured

### Gate 2 — No public foot-guns (required)
- No secrets committed (keys/tokens)
- No obvious “broken docs” in the public tree (e.g., merge conflict markers)
- Demo defaults are safe and fail-closed (no debug PII leakage)

### Gate 3 — Minimal documentation (required)
- `README.md` includes the v0 quickstart story (or links to it)
- `Real_Apps/chat-capabilities-demo/README.md` is accurate
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md` matches the demo API

### Gate 4 — Build sanity (required)
- `mvn -f ai-infrastructure-module/pom.xml -DskipTests install` succeeds
- `mvn -f Real_Apps/chat-capabilities-demo/pom.xml test` succeeds (or tests are explicitly documented as “none”)

---

## 6) v0 narrative (what we say publicly)

Position v0 as:
- “A framework to build AI apps with deterministic orchestration, retrieval, and safe action execution.”
- “This preview ships one complete reference app: chat + RAG + actions + confirmations.”

Avoid promising:
- every provider
- every vector DB
- enterprise governance/compliance workflows
- a marketplace

---

## 7) Suggested timeline (3 weeks)

Week 1:
- finalize supported set (Section 2)
- make golden path README perfect
- decide governance dependency (in demo or not)

Week 2:
- harden demo (rate limits, safe defaults)
- record demo video
- write release notes

Week 3:
- tag v0.1.0
- publish release
- collect early feedback (issues, stars, testimonials)

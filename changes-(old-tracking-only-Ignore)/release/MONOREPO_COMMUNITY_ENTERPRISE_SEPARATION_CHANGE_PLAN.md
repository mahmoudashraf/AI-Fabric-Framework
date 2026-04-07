# Monorepo Community/Enterprise Separation — Change Plan

## Status
Draft

## Context (current repo structure)
The Maven “root” is `ai-infrastructure-module/pom.xml` (multi-module build).

Providers live in:
- `ai-infrastructure-module/providers/*`

Vector DB modules live in:
- `ai-infrastructure-module/victor-databases/*`

## Goals
- Make **Community** the default build and dependency graph.
- Keep **Enterprise** in the monorepo, but:
  - clearly licensed and documented as Enterprise
  - excluded from Community build, docs, and published artifacts (unless explicitly enabled)
- Provide a simple, repeatable build switch:
  - `-Pcommunity` (default)
  - `-Penterprise` (includes enterprise modules)

## Non-goals (for this change plan)
- Implementing new enterprise features (multi-tenancy/RBAC/SSO).
- Rewriting architecture or moving to separate repos.

## Proposed separation policy (matches current code)

### Community (Apache 2.0) — default build
Core/framework modules:
- `ai-infrastructure-module/ai-infrastructure-core`
- `ai-infrastructure-module/ai-infrastructure-indexing`
- `ai-infrastructure-module/ai-infrastructure-rag`
- `ai-infrastructure-module/ai-infrastructure-pii`
- `ai-infrastructure-module/ai-infrastructure-governance` (keep SPI-only if possible)
- `ai-infrastructure-module/ai-infrastructure-web`
- `ai-infrastructure-module/ai-infrastructure-chat-session`
- `ai-infrastructure-module/ai-infrastructure-relationship-query`
- `ai-infrastructure-module/ai-infrastructure-migration`
- `ai-infrastructure-module/ai-infrastructure-behavior` (decide: community vs enterprise; document whichever you pick)

Providers:
- `ai-infrastructure-module/providers/ai-infrastructure-onnx-starter`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-openai`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-rest`

Vector DBs:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-lucene`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-memory`

### Enterprise (BSL 1.1) — opt-in profile
Providers:
- `ai-infrastructure-module/providers/ai-infrastructure-provider-azure`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-anthropic`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-cohere`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-gemini`

Vector DBs:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-pinecone`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-qdrant`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-weaviate`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-milvus`

## Implementation plan (Maven)

### 1) Add profile-scoped module lists
In `ai-infrastructure-module/pom.xml`:
- Make `<modules>` contain **Community only**
- Add `<profiles><profile id="enterprise"><modules>...</modules></profile></profiles>` containing the Enterprise modules

Acceptance criteria:
- `mvn -f ai-infrastructure-module/pom.xml -Pcommunity -DskipTests install` builds only Community modules
- `mvn -f ai-infrastructure-module/pom.xml -Penterprise -DskipTests install` builds Community + Enterprise modules

### 2) Add boundary enforcement
Add Maven Enforcer rules (or equivalent) so Community cannot depend on Enterprise modules:
- Ban Enterprise artifactIds from Community modules
- Optionally require Enterprise modules use a distinct Maven `groupId` (recommended long-term)

Acceptance criteria:
- If a Community module adds an Enterprise dependency, the build fails with a clear error.

### 3) Documentation separation (monorepo)
Create split docs structure (minimal, scalable):
- `docs/community/*` — only community modules/providers/vector DBs
- `docs/enterprise/*` — enterprise providers/vector DBs and commercial notes

Acceptance criteria:
- Community docs never require Enterprise modules to complete quick start.

### 4) License separation (monorepo)
Make licensing boundaries unambiguous in-repo:
- `LICENSE` (Community Apache 2.0)
- `enterprise/LICENSE` (Enterprise BSL 1.1)
- Clear per-directory statement for:
  - `ai-infrastructure-module/providers/*`
  - `ai-infrastructure-module/victor-databases/*`

Acceptance criteria:
- A reader can tell, from directory + docs, which license applies before using code.

## Suggested “better” physical layout (optional, future)
If you want stronger separation without leaving the monorepo:
- Move enterprise code under explicit folders:
  - `ai-infrastructure-module/providers/enterprise/*`
  - `ai-infrastructure-module/victor-databases/enterprise/*`
  - `ai-infrastructure-module/providers/community/*`
  - `ai-infrastructure-module/victor-databases/community/*`

This reduces accidental mixing, makes licensing clearer, and keeps Maven module paths self-describing.


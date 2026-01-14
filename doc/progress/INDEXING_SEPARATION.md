# Indexing Separation (Core vs Indexing Module)

## Goal

Separate **indexing runtime** (queue, workers, scheduler, aspect) out of `ai-fabric-core` into
`ai-infrastructure-indexing`, while keeping **annotations + scanners** in core.

Key constraints:
- Provider-only apps (no vector/search/indexing) must boot.
- Indexing/migration apps should continue to work via auto-configuration.
- This repo is greenfield; backward compatibility is not required.

## Current Plan / TODOs

1. Inventory current indexing couplings
2. Define minimal core indexing API surface
3. Move indexing runtime into `ai-infrastructure-indexing`
4. Refactor auto-config and starters
5. Update dependents and tests
6. Verify build (`mvn verify`) + smoke Real_Apps

## Notes / Decisions (as we implement)

- Keep user-facing annotations in core (examples: `@AICapable`, `@AIProcess`, `@EnableAIInfrastructure`).
- Keep scanners in core (example: `AnnotationFieldScanner`) but ensure they don’t force indexing beans.
- Move runtime-only indexing classes out of core:
  - AOP aspect (`AICapableAspect`)
  - queue + workers + coordinator + schedulers
  - indexing queue persistence (entity/repo) and indexing properties
- Core auto-configuration must not reference indexing-only classes (no `@ConditionalOnClass(AICapableAspect.class)`).

## Status

- Started: 2026-01-13
- In progress

### Implemented (Checkpoint 1)

- Added core API enum: `com.ai.infrastructure.indexing.api.IndexingStrategy`
- Updated annotations to use the API enum:
  - `@AICapable`, `@AIProcess`
- Moved indexing runtime out of `ai-fabric-core` into `ai-infrastructure-indexing`:
  - `com.ai.infrastructure.aspect.AICapableAspect`
  - `com.ai.infrastructure.indexing.*` (coordinator/queue/workers/etc.)
  - indexing queue persistence + indexing properties + cleanup helpers
- Updated core auto-config to remove indexing-only references (no `@ConditionalOnClass(AICapableAspect.class)` and no indexing properties)
- Updated migration module to depend on indexing module and only auto-configure when indexing queue is present
- Verified:
  - `cd ai-infrastructure-module && mvn verify`
  - `cd ai-infrastructure-module && mvn -DskipTests install` (needed for Real_Apps builds)
  - `Real_Apps/sub-management-hub` and `Real_Apps/sub-management-hub-simple` build + boot-smoke

### Follow-ups (Docs)

- Updated core docs to use `IndexingStrategy.BATCH` instead of the non-existent `DEFERRED`.

### Follow-ups (DX)

- Added `com.ai.fabric:ai-fabric-provider-starter` for provider-only/core-only setups.

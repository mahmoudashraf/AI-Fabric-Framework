# Maven Artifact Coordinates & Naming Fixes — Change Plan

## Status
Draft

## Problem
Some modules’ `artifactId`s and the parent `dependencyManagement` entries do not match, which will break:
- published dependency coordinates
- BOM/dependency management usage
- docs that instruct users to depend on an artifactId that doesn’t exist

Examples observed:
- `ai-infrastructure-module/providers/ai-infrastructure-provider-openai/pom.xml` uses `artifactId=ai-fabric-provider-openai`
- `ai-infrastructure-module/providers/ai-infrastructure-provider-azure/pom.xml` uses `artifactId=ai-infrastructure-provider-azure`
  while the parent lists `ai-fabric-provider-azure`
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-lucene/pom.xml` uses `artifactId=ai-infrastructure-vector-lucene`
  while the parent lists `ai-fabric-vector-lucene`

## Goals
- Make every published artifact coordinate deterministic and consistent.
- Ensure README snippets match real artifactIds.
- Prepare clean Community vs Enterprise publication paths.

## Non-goals
- Renaming Java packages as part of this change (only Maven coordinates + docs).

## Proposed standard (pick one, then apply everywhere)
### Option A (recommended): align to folder/module names
Use artifactIds that match module folder names:
- Providers: `ai-infrastructure-provider-openai`, `ai-infrastructure-provider-azure`, etc.
- Vector DBs: `ai-infrastructure-vector-lucene`, `ai-infrastructure-vector-qdrant`, etc.
- Keep existing “core/starter” naming as-is.

### Option B: keep `ai-fabric-*` branding everywhere
Rename all modules to `ai-fabric-provider-*` and `ai-fabric-vector-*` and update folder names accordingly.
This is a bigger change (more churn), but consistent branding.

## Change plan (assuming Option A)
1) Inventory current module artifactIds vs their folder names
- [ ] Providers under `ai-infrastructure-module/providers/*`
- [ ] Vector DBs under `ai-infrastructure-module/victor-databases/*`

2) Fix parent `dependencyManagement` entries to match actual artifactIds
- [ ] Update provider entries
- [ ] Update vector DB entries

3) Fix README/docs dependency snippets
- [ ] Root `README.md`
- [ ] Any module READMEs referencing old artifactIds

4) Add a lightweight verification script/CI check
- [ ] A CI step that parses module `pom.xml` files and ensures:
  - `dependencyManagement` only references existing module artifactIds
  - README snippets reference existing artifactIds

## Acceptance criteria
- A user can copy/paste dependencies from `README.md` and `mvn` resolves them successfully.
- `mvn -f ai-infrastructure-module/pom.xml -DskipTests install` completes with no missing artifacts.


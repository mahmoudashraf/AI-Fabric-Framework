# AI Fabric 0.5.2 Build And Release-Gate Evidence

Evidence date: 2026-07-31
Branch: `Platform-V11`

No secret values are recorded here.

## Framework Publication

```text
Security fix: 7055dda
Release commit: ada4580664e70937cb8ca9c36d62aeec4c39e2c2
Tag: ai-fabric-framework-v0.5.2
GitHub Release: published 2026-07-31T00:47:29Z
Maven Central BOM: HTTP 200
Maven Central core: HTTP 200
Maven Central execution: HTTP 200
```

The release tag and remote `main` resolve to the release commit. Git ancestry
proves that the release contains the trusted-retrieval security correction.

## Platform Migration

The private runtime:

- consumes the published `0.5.2` BOM;
- uses `IndexingWorkQuery` and sanitized `IndexingWorkStatus` for per-work
  reconciliation;
- keeps private authorization and bounded response projection;
- retains aggregate queue diagnostics because the framework has no public
  queue-summary contract; and
- enables the specialist only with the corrected trusted retrieval boundary.

Platform deployment defaults, compiler output, deployment-version metadata,
runtime metadata, and product worker dependencies target `0.5.2` directly.

## Verification

An empty Maven repository was created under `/tmp` and used for every private
consumer build. No framework checkout was installed into that repository.

```text
Private infrastructure reactor: BUILD SUCCESS
Modules: parent, actions connector, runtime, relay
Tests: 206 passed; 0 failed; 0 errors; 0 skipped

Private product reactor: BUILD SUCCESS
Modules: parent, integration core, vectorization core,
         vectorization runner, embedding worker
Tests: 32 passed; 0 failed; 0 errors; 0 skipped

Platform backend: BUILD SUCCESS
Tests: 729 passed; 0 failed; 0 errors; 0 skipped
Container coverage: PostgreSQL/Testcontainers passed

Combined private verification: 967 tests passed
```

The runtime dependency tree resolves every `io.github.loom-ai-labs` artifact at
`0.5.2`. The embedding worker resolves its framework dependencies at `0.5.2`.
The packaged runtime boot archive contains:

```text
ai-fabric-execution-0.5.2.jar
ai-fabric-retrieval-connector-0.5.2.jar
ai-fabric-rag-0.5.2.jar
ai-fabric-data-sync-0.5.2.jar
ai-fabric-indexing-0.5.2.jar
ai-fabric-chat-session-0.5.2.jar
```

All other packaged AI Fabric libraries also resolve at `0.5.2`; no mismatched
framework library is present.

## Hosted Staging Migration

The staging Platform backend deployed exact private commit `da615464d` as
Coolify deployment `g489aux7gsdjopnpq99ruokt`. Public health returned `UP`.

Before reapplying the canonical ecommerce and marketplace deployments,
config-only exports were created with zero secret values:

```text
ecommerce: dexp-84d6e0e2 / dxb-a7d9a27c
marketplace: dexp-116eb80e / dxb-da882fe6
```

Starting both applies together exhausted the staging Coolify environment API
rate limit. Both releases failed before activation and the previous verified
runtimes remained live. Ecommerce was then retried alone:

```text
deployment: dep-c5b5fe23
version: ver-6bb580e9
release: rel-3dd64039
runtime Coolify deployment: edoatk5awsme909elldg9sxb
source commit: da615464d269add945e1261cb7d14a74157c175f
live framework readback: 0.5.2
live deployment-version readback: ver-6bb580e9
manual post-build verification: vrf-f76796dd
result: 28 passed, 0 failed, 1 skipped
```

This run exposed a Platform release-integrity defect before the runtime build
finished:

- the Coolify provider returned immediately when the previous container was
  already healthy instead of polling the newly returned deployment UUID;
- post-deploy verification correctly failed against the stale runtime;
- late-success recovery then selected the passed `PRE_APPLY` verification and
  briefly promoted the release incorrectly.

The correction now:

- always polls a returned Coolify deployment UUID to a terminal successful
  state;
- refreshes and requires application readiness after deployment completion;
- fails closed on interrupted or timed-out waits; and
- permits late-success promotion only from `POST_APPLY` or `MANUAL_RERUN`
  verification, never `PRE_APPLY`.

Focused regression tests passed `24/24`. The complete Platform backend suite
then passed `729/729`. The correction was deployed before any remaining hosted
promotion work.

The canonical managed profile also needed its generic `document` entity
enabled so the bounded deployment-knowledge specialist had a valid,
product-neutral evidence space. That correction is private commit
`304688ca4`.

Fresh config-only exports were created immediately before the successful
reapply. They contain no secret values:

```text
ecommerce: dexp-3855bbc5 / dxb-555692b7
marketplace: dexp-6e6dcad1 / dxb-f9f69834
```

Both canonical deployments then reached `APPLIED_VERIFIED`:

```text
ecommerce
  deployment: dep-c5b5fe23
  version: ver-0fb5b1ff
  release: rel-f38d9ec8

marketplace
  deployment: dep-d99b3252
  version: ver-ac71cb67
  release: rel-2a62a6a7
```

Both runtimes report AI Fabric `0.5.2`. Their live vector-space catalogs are:

```text
document, policy, product, review
```

## Specialist Isolation And Boundary Canaries

Unique specialist evidence was imported into each deployment:

```text
ecommerce: canary-052-tenant-a-orchid
marketplace: canary-052-tenant-b-cobalt
```

Own-deployment retrieval returned the expected private evidence:

- ecommerce returned its Tuesday `09:17 UTC` ORCHID fact;
- marketplace returned its Friday `16:43 UTC` COBALT fact.

Cross-deployment requests returned `INSUFFICIENT_EVIDENCE` and did not attach
the other deployment's evidence. Direct runtime authorization negatives passed
on both runtimes:

```text
valid signed request: HTTP 200
missing runtime assertion: HTTP 401
missing specialist scope: HTTP 403
signed request missing tenant: HTTP 403
```

The canary documents were deleted with the targeted batch-delete API. Both
deletions completed `1/1`; post-cleanup evidence counts are zero and neither
marker remains retrievable.

## Vectorization Convergence Correction

A fresh full gate found that a successful reindex could remain
`OUT_OF_DATE`. `VectorizationService.createRunForDeployment` queued work
without persisting the current active deployment's indexed-output hash. Run
completion therefore compared the result with a stale target hash.

Private commit `eb47df423` now snapshots the active version hash when the run
is created. Its focused regression test and the complete Platform backend
suite passed:

```text
Platform backend: 730 passed; 0 failed; 0 errors; 0 skipped
```

The staging Platform backend deployed exact commit
`eb47df4235c1af9c751b489768d5c7271d58fea0` as Coolify deployment
`mzf9jm8khjro8625607e4yrf`. Public health returned `UP`.

Post-deploy reindex evidence:

```text
marketplace run: vrn-a937da90, COMPLETED, plan IN_SYNC
ecommerce run: vrn-8d393266, COMPLETED, plan IN_SYNC
```

For both plans, current, last-success, and revision indexed-output hashes
match.

## Hosted Staging Full Platform Release Gate

The stored Partner verification credential had expired during the first fresh
run. Only the dedicated release-gate test user's temporary credential was
rotated; the new value remains in private secret storage. The standalone
Partner suite `vsr-c82cea17` then passed.

Final full suite:

```text
suite: full-platform-release-readiness
run: vsr-4dc6c08f
status: PASSED
blocking stages: 13 passed, 0 failed
release gate: READY
gate expiry: 2026-07-31T15:47:59.848341Z
```

The optional non-blocking `qdrant-hosted-verification` stage remains failed for
legacy rollout `dep-d24b9a5d`, which still uses entity contract
`AI_ENTITY_CONFIG_V0_3` and correctly reports `MIGRATION_REQUIRED`. It is not
the active canonical or ProdUS path and does not invalidate the `0.5.2`
release gate.

## Production Platform And ProdUS Completion

ProdUS was backed up at the deployment-config boundary before the production
exercise. No Coolify-wide backup or destructive provider reset was used:

```text
export: dexp-96e0c996
bundle: dxb-d77a3642
import: dimp-9bbf3ef1
draft: drf-810710ae, revision 10
```

The restored V04 deployment completed its production release:

```text
deployment: dep-f6abfa06
version: ver-aaec416e, v9, AI Fabric 0.5.2
entity contract: AI_ENTITY_CONFIG_V0_4
release: rel-3b4a8338, APPLIED_VERIFIED
verification: vrf-a1675b36, PASSED
target: dtp-coolify-production
```

Runtime, connector, vectorization runner, and runtime PostgreSQL resources are
healthy. Runtime liveness, readiness, and aggregate health all return HTTP
`200` / `UP`. The managed Zilliz/Milvus index contains 203 vectors across 14
ProdUS knowledge spaces. Live retrieval returned `service-module` evidence for
"API security review" and `package-template` evidence for launch/security
hardening.

The scoped assignment readback remains:

```text
consumer: produs-staging
deployment: dep-f6abfa06
external integration ready: true
issuer: produs-staging-backend
audience: produs-staging
audience mode: CONSUMER_ID
cache TTL: 300 seconds
```

Two production control-plane defects were corrected during closure:

1. Generated indexing state from the older deployment used an incompatible
   queue identifier shape. A startup guard now repairs the generated state
   before normal runtime use.
2. Explicit Coolify reconciliation changed a managed service's target profile
   but retained its stale `environmentScope`. Reconciliation now derives the
   service scope from the selected profile. The complete clean Platform
   backend suite passed 733 tests with zero failures or errors.

The tested source tree was published on `Platform-V11` as commit `de7bd045`.
Production Platform backend Coolify deployment `g11gyn0pmxwr5gbessqomsxm`
finished from that exact commit and public health returned `UP`.

The MCP execution gateway was then reconciled against
`dtp-coolify-production`. Readback confirms:

```text
environment scope: production
source branch: Platform-V11
health: READY
drift: NO_DRIFT
probe: HTTP 200
```

## Production Full Platform Release Gate

The production gate exposed real, independent issues in sequence rather than
having its assertions weakened:

- canonical ecommerce and marketplace builds were retried serially after
  concurrent Maven builds saturated the four-core staging host;
- Shopify expectations were aligned with the live Starter-tier posture;
- MCP target-profile drift was repaired by the scope correction above; and
- only the dedicated release-gate Supabase user's expired JWT was refreshed.

The standalone Partner Enablement proof passed as `vsr-49aeefa6`. The final
full production suite completed as follows:

```text
suite: full-platform-release-readiness
run: vsr-e18452e5
status: PASSED
blocking stages: 13 passed, 0 failed
marketplace hosted checks: 41 passed
ecommerce hosted checks: 43 passed
release gate: READY
gate expiry: 2026-08-01T01:10:50.094128Z
```

The optional legacy Qdrant stage remains non-blocking
`MIGRATION_REQUIRED`. All temporary workstation rules were removed from
Hetzner firewalls `10915120` and `10918233` after deployment; server-to-server
and public web access rules were preserved.

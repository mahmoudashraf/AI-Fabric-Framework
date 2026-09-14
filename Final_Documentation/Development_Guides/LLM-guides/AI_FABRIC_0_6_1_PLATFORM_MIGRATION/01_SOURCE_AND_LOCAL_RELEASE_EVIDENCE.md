# AI Fabric 0.6.1 Source And Local Release Evidence

Evidence date: 2026-09-14
Branch: `Platform-V11`
Pre-migration private commit: `9a429fcb16e1231f0d3a2d225c917fc02102b965`

No secret values are recorded here.

## Framework Publication

```text
Release tag: ai-fabric-framework-v0.6.1
Release commit: bf6d19eed5ed0a8d8085db7cc02e0505e9973e65
Maven Central BOM: resolved
Maven Central core: resolved
Maven Central execution: resolved
```

The annotated release tag dereferences to the documented immutable release
commit. A new empty Maven repository under `/tmp` recorded every consumed
`0.6.1` framework POM and JAR with repository ID `central`; no sibling checkout
or locally installed framework artifact participated in the proof.

## Direct Migration

The private source moved directly from `0.5.2` to `0.6.1` in:

- the runtime/service BOM property;
- the private product/embedding-worker BOM property;
- Platform deployment compiler defaults;
- immutable deployment-version defaults;
- generated runtime environment;
- Platform application configuration and tests; and
- the public Loom AI Labs framework product version, quickstart, and release
  link.

No intermediate-release deployment path or compatibility behavior was added.
Historical migration evidence retains its original versions because it remains
an audit record rather than active configuration.

## Required First-Rollout Posture

The runtime application configuration and Docker customer template now expose:

```text
AI_EXECUTION_OUTPUT_FINALIZATION_MAX_ATTEMPTS=1
AI_EXECUTION_SPECIALIST_CHAINS_ENABLED=false
```

The Platform-generated runtime environment emits both values explicitly, and
its unit test verifies them. Runtime Spring context evidence shows specialist
chain auto-configuration does not match while the property is false.

The base migration does not add a chain database table, chain secrets, chain
definition, chain endpoint, or product claim.

## Protected-Work Baseline

The checked runtime profile uses process-local `IN_MEMORY` async execution and
disables receipts, reviews, input waits, plans, and conversation managers. The
private deployment-knowledge route executes synchronously. The canonical
staging ecommerce and marketplace deployments are on AI Fabric `0.5.2` before
the rollout and have no enabled specialist-chain configuration or chain state.

Production state remains authoritative and must be inventoried immediately
before a future production rollout. This local/staging statement is not a
claim about uninspected production overrides.

## Verification

All tests ran without Maven test-skipping flags.

```text
Private infrastructure reactor: BUILD SUCCESS
Modules: parent, generic REST connector, runtime, relay
Tests: 209 passed; 0 failed; 0 errors; 0 skipped

Private product reactor: BUILD SUCCESS
Modules: parent, integration core, vectorization core,
         vectorization runner, embedding worker
Tests: 32 passed; 0 failed; 0 errors; 0 skipped

Platform backend: BUILD SUCCESS
Tests: 733 passed; 0 failed; 0 errors; 0 skipped

Combined private verification: 974 passed
```

The empty-cache infrastructure and product reactors also completed
successfully under Java 21. The framework-consuming services use Spring Boot
4.1.0. The Platform control-plane backend remains on its existing Spring Boot
3.2.0 line because it does not consume AI Fabric runtime libraries; changing
that independent application baseline is not part of this release migration.

## Dependency And Package Identity

The runtime dependency tree resolves these and all other
`io.github.loom-ai-labs` dependencies at `0.6.1`:

```text
ai-fabric-core
ai-fabric-rag
ai-fabric-chat-session
ai-fabric-data-sync
ai-fabric-indexing
ai-fabric-actions-connector
ai-fabric-retrieval-connector
ai-fabric-execution
```

The packaged runtime contains only `0.6.1` AI Fabric libraries, including:

```text
BOOT-INF/lib/ai-fabric-execution-0.6.1.jar
BOOT-INF/lib/ai-fabric-retrieval-connector-0.6.1.jar
BOOT-INF/lib/ai-fabric-rag-0.6.1.jar
BOOT-INF/lib/ai-fabric-data-sync-0.6.1.jar
BOOT-INF/lib/ai-fabric-indexing-0.6.1.jar
BOOT-INF/lib/ai-fabric-chat-session-0.6.1.jar
```

The packaged embedding worker contains only the expected `0.6.1` provider,
core, curated-default, ONNX, and Spring AI provider libraries.

## Container Build Gate

The three repository-root Docker build paths used by the hosted rollout were
built locally after clearing only a corrupted disposable BuildKit cache:

```text
ai-infrastructure-module/ai-fabric-runtime/Dockerfile: passed
ai-fabric-product/ai-fabric-vectorization-runner/deploy/container/Dockerfile: passed
Platfrom/backend/Dockerfile: passed
```

The resulting runtime image compiled against Maven Central `0.6.1`, the
vectorization runner compiled its private product wrapper successfully, and
the Platform backend image preserved its independent Spring Boot `3.2.0`
control-plane baseline. No Docker images, volumes, databases, or application
data were removed during cache repair.

## Public Site Gate

The public site was verified with Node `22.23.2`:

```text
Astro diagnostics: 0 errors, 0 warnings, 0 hints
Static build: 20 pages
Content graph: 2 products, 6 experiments, 5 research items
Static smoke: 19 routes
Browser smoke: passed with responsive screenshots and accessibility checks
```

## Hosted Baseline

Before deployment, the staging Platform reports:

```text
ecommerce dep-c5b5fe23: AI Fabric 0.5.2, APPLIED_VERIFIED
marketplace dep-d99b3252: AI Fabric 0.5.2, APPLIED_VERIFIED
release gate: STALE; last successful run expired
```

That stale gate is expected before a new release. It must not be reused as
`0.6.1` acceptance evidence. A fresh hosted deployment and release-gate run are
required.

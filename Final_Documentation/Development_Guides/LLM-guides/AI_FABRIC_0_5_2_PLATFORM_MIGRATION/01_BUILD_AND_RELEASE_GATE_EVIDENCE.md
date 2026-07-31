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

## Hosted Staging Progress

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
then passed `729/729`. The corrected Platform backend must deploy before the
second hosted runtime canary or full release suite is allowed to run.

## Remaining Hosted Evidence

The consumer build gate is complete. Final hosted evidence must include:

- hosted two-tenant and two-deployment isolation;
- missing tenant and missing scope failures;
- provider, Data Sync, retrieval, chat, and existing release regressions; and
- a fresh `full-platform-release-readiness` result.

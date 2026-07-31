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
Tests: 728 passed; 0 failed; 0 errors; 0 skipped
Container coverage: PostgreSQL/Testcontainers passed

Combined private verification: 966 tests passed
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

## Remaining Hosted Evidence

The consumer build gate is complete. Final hosted evidence must include:

- hosted two-tenant and two-deployment isolation;
- missing tenant and missing scope failures;
- provider, Data Sync, retrieval, chat, and existing release regressions; and
- a fresh `full-platform-release-readiness` result.

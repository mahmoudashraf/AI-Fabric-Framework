# Platform AI Fabric 0.6.1 Migration

Status: **SUPPORTED FLEET UPGRADED; CANONICAL GATES PASSED; SHOPIFY RETRIEVAL EXCEPTION OPEN**

This is the one-way direct Platform consumer migration from AI Fabric `0.5.2`
to `0.6.1`. No `0.5.3` or `0.6.0` deployment is performed between them, and
no compatibility reader, duplicate framework contract, or version fallback is
introduced.

## Base Upgrade Scope

- consume only `io.github.loom-ai-labs:ai-fabric-bom:0.6.1`
- retain Java 21 and Spring Boot 4.1.x in framework-consuming services
- preserve chat, RAG, Data Sync, indexing, actions, confirmation, memory,
  direct specialist, fixed-plan, and deployment-knowledge behavior
- set `ai.execution.output-finalization.max-attempts=1`
- set `ai.execution.specialist-chains.enabled=false`
- regress corrected tenant/deployment retrieval and exact MCP binding
- resolve artifacts from Maven Central in an empty dependency cache
- deploy and verify staging before any production rollout

No chain table or chain secret is required for this base upgrade.

## Separate Optional Scope

Bounded multi-specialist chains are available in AI Fabric `0.6.1`, but are not
enabled by this migration. A later adoption must define one exact-version,
read-only LoomAI canary chain; provision reviewed JDBC state and stable private
encryption/fingerprint secrets; and pass the full chain security, replay,
restart, cancellation, retention, and attribution matrix.

Existing chat, RAG, fixed plans, direct specialists, actions, receipts, reviews,
and Smart Brain operations must not be rebuilt as chains merely because the
new contract exists.

## Gates

| Gate | Status | Evidence |
| --- | --- | --- |
| Immutable framework publication | Passed | Tag dereferences to `bf6d19eed5ed0a8d8085db7cc02e0505e9973e65`; BOM, core, and execution resolve from Maven Central |
| Direct source migration | Passed | Active private source/config/tests target `0.6.1`; no intermediate release path was added |
| First-rollout settings | Passed | Runtime defaults, customer template, and generated deployment env force one output attempt and keep chains disabled |
| Empty-cache private builds | Passed | Infrastructure `209/209`, product `32/32`, Platform backend `733/733`; combined `974/974` |
| Dependency convergence | Passed | Framework-consuming dependency trees resolve only `0.6.1` |
| Packaged runtime identity | Passed | Runtime contains `ai-fabric-execution-0.6.1.jar` and all packaged AI Fabric libraries are `0.6.1` |
| Coolify-equivalent container builds | Passed | Platform backend, runtime, and vectorization-runner Dockerfiles build successfully from the repository root |
| Public framework page | Passed | Node 22 Astro check/build/content/static/browser smoke completed |
| Staging protected-work baseline | Passed | Canonical runtimes use in-memory async work with receipts/reviews/waits/plans/managers disabled; no chain state is enabled |
| Final private source | Passed | `Platform-V11` commit `d9edc5816696a2b35ff727c49eecc004e11a6820` is pushed and deployed to the relevant staging and production Platform services |
| Staging Platform services | Passed | Backend, Platform UI, and Partner UI completed exact-commit deployments and return healthy public responses |
| Production Platform services | Passed | Backend, Platform UI, Partner UI, public site, Shopify Bridge, and MCP Gateway are on their latest applicable source and return healthy public responses |
| Supported runtime fleet | Passed | Staging marketplace/ecommerce/Shopify and production ProdUS/marketplace/ecommerce/Shopify all run AI Fabric `0.6.1`; every active release is verified |
| ProdUS migration | Passed | Assignment resolves to `dep-f6abfa06`; corrected projection reindex completed `198/198`; grounded retrieval returned `service-module:api-security-review` with exact tenant/deployment metadata |
| Canonical staging checks | Passed | Platform admin `vsr-8340e479`, marketplace install `vsr-c146ea22`, and canonical hosted suite `vsr-b823bb56` passed |
| Canonical production checks | Passed | Platform admin `vsr-f509d987`, marketplace install `vsr-d069622f`, and canonical hosted suite `vsr-381645f1` passed |
| Partner and Thinker checks | Passed | Staging Partner/Thinker `vsr-b0db4019` / `vsr-4bbd0014` and production `vsr-35cff4f2` / `vsr-f3eba2a1` passed |
| Aggregate full release gate | Accepted exception, not green | Staging `vsr-605c9b21` and production `vsr-ddb39f72` passed stages 1-8 and failed only the explicitly deferred Shopify first-product retrieval-quality stage; do not report either run as `PASSED` |
| Public demo portfolio | Passed | All ten framework-consuming demos return HTTP `200`, report AI Fabric `0.6.1`, and identify release commit `bf6d19eed5ed0a8d8085db7cc02e0505e9973e65` |
| Custom LoomAI DNS | External action required | Namecheap authoritative DNS still points the apex and four production hostnames to parking address `18.204.152.241`; Coolify's custom-domain configuration is already present |
| Optional chain adoption | Out of base scope | Separate migration, secrets, canary, and owner approval |

See `01_SOURCE_AND_LOCAL_RELEASE_EVIDENCE.md` for source, local, public-demo,
and final hosted evidence. Shopify retrieval remains an acknowledged product
quality defect to address after the version rollout. The accepted exception
does not rewrite failed aggregate gate records or weaken tenant/deployment
isolation. Optional historical Qdrant verification remains non-blocking and is
not part of the active supported path.

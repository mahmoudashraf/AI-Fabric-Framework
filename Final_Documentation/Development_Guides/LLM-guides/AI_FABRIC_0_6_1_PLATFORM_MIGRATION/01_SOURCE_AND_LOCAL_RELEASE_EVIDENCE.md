# AI Fabric 0.6.1 Source And Local Release Evidence

Evidence date: 2026-09-14 through 2026-09-15
Branch: `Platform-V11`
Pre-migration private commit: `9a429fcb16e1231f0d3a2d225c917fc02102b965`
Final private commit: `d9edc5816696a2b35ff727c49eecc004e11a6820`

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

## Subsequent Private Regression Evidence

Further migration corrections were committed through private source commit
`5aee89dedcf574f8f1754bf76015df74708fc2ae`. After those corrections, the
following tests passed without test-skipping flags:

```text
Platform backend: 735 passed; 0 failed; 0 errors; 0 skipped
Shopify Bridge: 281 passed; 0 failed; 0 errors; 0 skipped
Focused Shopify storefront behavior: 49 passed
Focused Shopify vectorization projection: 9 passed
```

These later counts supplement the original source-gate record above. They do
not rewrite its historical `974/974` result.

## Public Demo Portfolio Evidence

The public AI Fabric demo portfolio is distinct from the LoomAI Platform
release gate. On 2026-09-14, all ten framework-consuming demo backends returned
HTTP `200` and actuator status `UP`, reported AI Fabric `0.6.1`, and reported
the immutable framework release commit
`bf6d19eed5ed0a8d8085db7cc02e0505e9973e65`:

```text
AI Shopping Experience
Account Resolver
Behavior Signals
Tenant Guard
Privacy Shield
Live Data Sync
Agentic Action Resolver
Deployment Knowledge Guard
Incident Investigation Room
MCP Operations Assistant
```

The separate MCP Operations reference server also returned actuator status
`UP` and the same source commit. It reports `aiFabricVersion=n/a` because it is
the companion remote MCP service rather than a framework-consuming demo.

This proves that the public demos consumed the new release. It does not, by
itself, prove that the private Platform migration or its customer deployment
release gate is complete.

## Initial Hosted Platform Rollout

The staging Platform application was pinned to exact private commit
`5aee89dedcf574f8f1754bf76015df74708fc2ae`. Coolify deployment
`x580979qxc076oqhe7btwrvj` completed, application readback reported
`running:healthy`, and the public health endpoint reported `UP`.

The controlled Shopify companion deployment now has this verified identity:

```text
consumer: shopify-shopping-companion-test
deployment: dep-8c3e7259
version: ver-4c5795ff (revision 39, AI Fabric 0.6.1)
release: rel-c2df15e0
verification: vrf-6c5a6c35
output finalization attempts: 1
specialist chains: disabled
```

The deployment configuration was backed up before apply using export
`dexp-7a83cdc0` and bundle `dxb-f33f54f`. No secret values are included in
this evidence document.

The staging Shopify Bridge was reconciled through the Platform, and Coolify
deployment `nc9ssqgo1bh4ex3it1kq3yb5` completed from the same exact private
commit. Runtime, connector, runner, bridge, and production MCP gateway health
checks passed.

The canonical staging verification deployments were also published and
applied successfully on `0.6.1`:

```text
ecommerce: dep-c5b5fe23, ver-2b9837cc (v9), rel-c97540a2, APPLIED_VERIFIED
marketplace: dep-d99b3252, ver-98290f5a (v10), rel-58dfa3eb, APPLIED_VERIFIED
```

Both runtime health endpoints return `UP`. Their successful apply state proves
deployment completion, while the hosted behavior stages that follow the
Shopify gate failure still require a fresh complete release-gate run.

## Initial Hosted Blocker (Superseded)

Shopify vectorization reconcile run `vrn-9c4613a6` completed successfully:

```text
processed: 81
succeeded: 81
failed: 0
store status: IN_SYNC
```

Nevertheless, the live product FAQ canary still returned zero sources and
documents. Direct Qdrant inspection showed the product collection and Nimbus
product vectors exist, but those vectors do not carry
`knowledgeSourceHandleRef`. The active deployment source filter correctly
requires the exact compiled Shopify product source handle, so source-isolated
retrieval rejects otherwise relevant hits.

The coordinated product-side correction is:

1. compile `knowledgeSourceHandleRef` as optional vector metadata for
   marketplace DATA-plugin entity types;
2. resolve each installed dataset's exact compiled handle in the Shopify
   vectorization plan;
3. project that value through generic `metadataStaticValues` during indexing;
4. publish/apply the corrected deployment version and reindex; and
5. verify nonzero product FAQ sources/documents before rerunning the full gate.

AI Fabric `0.6.1` already supports and filters this metadata. The defect is in
LoomAI's marketplace/vectorization projection, so no private framework fork,
text-matched retrieval rule, compatibility path, or invented framework
endpoint is appropriate.

Full release-gate run `vsr-0eaae0e8` passed stages 1-8 and failed stage 9. The
earlier `thinker_deep` incompatibility was corrected by mapping that external
Shopify UI mode to the current framework `thinker` mode. The remaining product
FAQ retrieval failure above still blocks a green gate. Production Platform
deployment and release announcement remain prohibited until the correction,
hosted isolation canaries, and a fresh full gate all pass.

The restriction above records the state at that checkpoint. It was superseded
when the owner explicitly accepted Shopify retrieval as a post-upgrade quality
defect and requested completion of the version rollout. It remains historical
evidence and must not be read as the current fleet status.

## Final Private Source And Platform Rollout

The final private branch is `Platform-V11` at pushed commit
`d9edc5816696a2b35ff727c49eecc004e11a6820`. It includes managed core-service
deployment status readback and the generic ecommerce vector boundary metadata
alignment. Focused regressions passed, and the complete Platform backend suite
passed `737` tests with zero failures or errors.

The final exact-commit Coolify deployments completed:

```text
staging backend:      v7ry1li8ix6ylfiva0tb2svn
staging Platform UI:  o10do5bjs1xyxomak1gu1cry
staging Partner UI:   dt8n9iknxtizpp8eis0kjajo
production backend:   o1z2boikv6rnxzvitmyrtea5
production Platform UI: xb6re18ifzaomxeu4njq6130
production Partner UI:  t4jsuexzw3fc4r8xjryh2dmv
production public site: v8oi7d5wqtbihnhjis8dk2n1
```

The production Shopify Bridge is on commit `07381e5d8`, the latest applicable
bridge source; there is no bridge-tree change between that commit and
`d9edc5816`. The production MCP Gateway is deployed from `d9edc5816`. The
ecommerce store had no applicable source change and did not require a no-op
redeploy.

Fifteen supported public health surfaces returned HTTP `200`: staging and
production Platform backends and UIs, the production public site, Shopify
Bridge, MCP Gateway, ProdUS, both canonical staging runtimes, all three
canonical production runtimes, and the production Shopify runtime.

## Final Managed Runtime Fleet

All active supported deployments use AI Fabric `0.6.1` and verified V04
releases:

| Environment | Workload | Deployment | Version | Release |
| --- | --- | --- | --- | --- |
| Staging | Marketplace | `dep-d99b3252` | `ver-feae8351` (v11) | `rel-0263ef7c` |
| Staging | Ecommerce | `dep-c5b5fe23` | `ver-80022ce0` (v10) | `rel-6dc15f2a` |
| Staging | Shopify companion | `dep-8c3e7259` | `ver-4c5795ff` (v39) | `rel-c2df15e0` |
| Production | ProdUS | `dep-f6abfa06` | `ver-795421c9` (v12) | `rel-f7c2758a` |
| Production | Marketplace | `dep-f772d1a4` | `ver-9bc41425` (v23) | `rel-01ae1e89` |
| Production | Ecommerce | `dep-f8492dcc` | `ver-b419794e` (v18) | `rel-caa9c4ea` |
| Production | Shopify companion | `dep-8c3e7259` | `ver-ed8c7547` (v15) | `rel-db66380e` |

Historical failed, duplicate, and provider-validation records were not revived.
ProdUS rollback deployment `dep-53f9ca56` was deliberately left untouched.

## ProdUS Boundary And Retrieval Repair

Before changing ProdUS, the active deployment received a config-only export:

```text
export: dexp-ccae58f7
bundle: dxb-30c88471
included secret values: 0
```

AI Fabric `0.6.1` correctly requires tenant and deployment boundaries during
RAG retrieval. Existing ProdUS vectors predated those boundaries. The first
managed reindex also exposed a product contract mismatch: ProdUS supplies
top-level `title` and `body`, while the active projection required a searchable
`content` field and lacked explicit field mappings.

The final contract accepts required `title` and optional `body`/`content` for
all twelve ProdUS entity types. The managed vectorization revision
`vpr-b1cc0892` maps approved source fields explicitly and injects `tenantId`
and `deploymentId` only from verified runner authority. It does not trust
source-supplied boundary metadata.

Verification completed:

```text
one-record canary vrn-450f188f: 1/1
full reindex vrn-d9d6ec7a: 198/198
service-module vectors: 90
package-template vectors: 15
```

Every resulting vector has searchable content and exact tenant
`ten-bf9f61fb` plus deployment `dep-f6abfa06`. Retrieval request
`rag-1b354b6b-270d-430d-8eda-dab87472a016` returned a grounded API Security
Review answer with source `service-module:api-security-review` and the same
boundary metadata.

Assignment discovery now resolves consumer `produs-staging` to
`dep-f6abfa06`, uses backend-mediated private runtime assertions with issuer
`produs-staging-backend` and audience `produs-staging`, reports
`externalIntegrationReady=true`, and has a 300-second cache TTL.

## Canonical Boundary Reindex

The canonical document, product, policy, and review contracts now require
deployment-bound vector metadata. Fresh reindexes passed:

```text
staging ecommerce vrn-893040be: 3/3
production marketplace vrn-d4ae42d3: 3/3
production ecommerce vrn-bfdad4aa: 3/3
```

Direct vector inspection confirmed exact tenant/deployment boundaries for all
three active collections.

## Final Verification Evidence

Fresh checks against the final private source passed:

| Environment | Suite | Run | Result |
| --- | --- | --- | --- |
| Staging | Platform admin | `vsr-8340e479` | Passed |
| Staging | Marketplace install | `vsr-c146ea22` | Passed |
| Staging | Canonical hosted path | `vsr-b823bb56` | Passed |
| Production | Platform admin | `vsr-f509d987` | Passed |
| Production | Marketplace install | `vsr-d069622f` | Passed |
| Production | Canonical hosted path | `vsr-381645f1` | Passed |
| Staging | Partner | `vsr-b0db4019` | Passed |
| Staging | Thinker | `vsr-4bbd0014` | Passed |
| Production | Partner | `vsr-35cff4f2` | Passed |
| Production | Thinker | `vsr-f3eba2a1` | Passed |

The canonical hosted suites passed shared inference, inventory, marketplace,
and ecommerce checks. The optional historical Qdrant stage reports
`MIGRATION_REQUIRED` and remains explicitly non-blocking.

Partner verification initially failed because the separate Partner Auth
Supabase project was inactive and its dedicated gate JWT had expired. The
existing project was restored, the existing gate identity was refreshed in
place through private secret handling, and all four standalone Partner/Thinker
runs above passed. No new test identity was created.

## Accepted Shopify Retrieval Exception

The aggregate full gates are intentionally not recorded as green:

```text
staging:    vsr-605c9b21, stages 1-8 passed, Shopify first-product stage failed
production: vsr-ddb39f72, stages 1-8 passed, Shopify first-product stage failed
```

Both runs passed Shopify companion and Shopify MCP coverage. Their remaining
failures are first-product answer quality: missing expected travel grounding
and internal-language echo in selected prompts. The owner explicitly deferred
Shopify retrieval correction until after the version upgrade. Independent
Partner and Thinker suites passed, so those downstream capabilities were not
left unverified.

This exception permits the completed `0.6.1` version rollout. It does not turn
either aggregate run into `PASSED`, waive isolation controls, or authorize a
claim that the full Platform release gate is green.

## Public Demo Portfolio

All ten framework-consuming public demos return HTTP `200`, report AI Fabric
`0.6.1`, and identify immutable release commit
`bf6d19eed5ed0a8d8085db7cc02e0505e9973e65`. The MCP reference server is also
healthy and reports the same source commit; `aiFabricVersion=n/a` is expected
because it is a remote MCP service rather than a framework consumer.

The chat-capabilities demo retains persistent Lucene retrieval proof while its
restart-local source database counts are empty. That demo data-state signal is
not a version mismatch and is not Platform release evidence.

## External DNS Action

Namecheap remains authoritative for `loomai.pro`. As verified on 2026-09-15,
the apex plus `api`, `console`, `partners`, and `shopify-bridge` still resolve
to parking address `18.204.152.241`; the subdomains also publish placeholder
AAAA value `::`. HTTPS therefore fails before requests reach Coolify.

Coolify already has the intended custom domains. The remaining owner action is
to replace the Namecheap records with production A `46.225.162.106` and AAAA
`2a01:4f8:1c18:c04::1`, remove conflicting parking/redirect records, wait for
authoritative propagation, and then verify certificate issuance and HTTPS.

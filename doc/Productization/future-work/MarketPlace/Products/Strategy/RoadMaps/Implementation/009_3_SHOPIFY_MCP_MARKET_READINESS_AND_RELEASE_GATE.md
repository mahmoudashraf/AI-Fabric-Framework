# 009.3 Shopify MCP Market Readiness And Release Gate

Status: staging release gate passed on 2026-05-06. Staging is the active target. Production is not deployed by this plan.

Parent plans:

- [009 Shopify MCP-First Implementation Sequence](009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md)
- [009.1 Marketplace Config-Driven MCP Capability Architecture](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md)
- [009.2 MCP Execution Gateway Extraction Plan](009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md)
- [008 Controlled Design Partner Launch And Market Proof](008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md)

Roadmap phase: `009.3` - convert the implemented Shopify MCP-first capability into a claim-safe, release-gated, design-partner-ready Shopify product.

---

## Purpose

Plans 009, 009.1, and 009.2 made the core execution path real:

```text
Marketplace ACTION plugin
  -> compiled runtime action catalog
  -> Shopify host boundary when needed
  -> managed MCP Execution Gateway
  -> Shopify MCP tools/list and tools/call
  -> normalized action evidence
```

009.3 defines the market-readiness gate for that product. It is not a new architecture layer. It is the readiness contract that decides whether the Shopify MCP product can be shown to design partners with controlled claims.

The target market posture is:

```text
Design-partner ready: yes, after staging release gate passes.
Broad self-serve production launch: no, until production deployment, PR/review gates, app-review posture, self-serve packaging, and Customer Account/Checkout auth gates are completed.
```

---

## Marketable Product Boundary

The product that can go to market first is:

**Shopify MCP-first AI commerce assistant with governed action evidence.**

Launch-safe capabilities:

- storefront product discovery
- Shopify Storefront MCP catalog search through `search_catalog`
- policy and FAQ retrieval through Storefront MCP
- guided shopping workflows that keep Marketplace plugin catalog as product truth
- governed Bridge handoff for Shopify-specific store, billing, package, and session checks
- MCP Gateway execution with schema drift checks and normalized `MCP_TOOL_RESULT` evidence
- Platform-managed staging deployment, health checks, drift checks, logs, restart, force recreate, and release-gate evidence

Claims to avoid until separately verified:

- full public Shopify App Store readiness
- fully self-serve onboarding for all merchants
- live Customer Account MCP execution
- live Checkout MCP execution
- terminal checkout automation
- payment, refund, or protected customer data automation without explicit approvals and auth material

---

## Public Self-Serve Launch Pending Items

These items do not block controlled design-partner shipping, but they do block a broad public Shopify App Store / self-serve launch claim:

- Merge PR `#156` (`Platform v8`) or otherwise land its production-intended work. Current posture at the time of this note: open, mergeable, and still carrying the Platform V8 MCP/Coolify/product-service release work.
- Resolve the open PR `#156` P1 review thread for Coolify structured upstream errors: Coolify transport failures must return the intended structured `502` / `COOLIFY_UPSTREAM_FAILURE` contract instead of falling through as generic `500` responses.
- Resolve the open PR `#156` P2 review thread for MCP Gateway upstream timeouts: configured connect/read timeouts must be enforced by the MCP Streamable HTTP client so stalled MCP servers cannot tie up gateway request threads beyond configured limits.
- Keep higher-tier public claims gated until Shopify Customer Account MCP and Checkout MCP have the required external Shopify auth/security material, protected customer data posture, credentials, and live `tools/list` / safe `tools/call` evidence.
- Finish merchant-facing self-serve packaging: onboarding path, pricing/package copy, support policy, install/recovery guidance, merchant documentation, App Store listing/review collateral, and a clear public escalation process.

---

## Release Gate Definition

The release blocker is the Platform `full-platform-release-readiness` suite.

009.3 requires the full suite to include these Shopify/MCP stages:

- `shopify-companion-verification`
- `shopify-mcp-gateway-verification`
- `shopify-first-product-readiness-audit`

The suite still includes the generic Marketplace install-flow gate. In staging, that stage must keep the default `dev-openai-qdrant` template and shared Qdrant draft patch because shared-index Marketplace DATA plugins require `vectorStoragePosture=SHARED` on a shared-storage-capable provider. Inside the hosted full suite it runs with `MARKETPLACE_INSTALL_FLOW_APPLY_RELEASE=false`, so it proves template bootstrap, ACTION/DATA/INFERENCE plugin install, compile, publish, artifact generation, and cleanup without applying a new heavyweight runtime on the shared staging host. Runtime apply and live query evidence remain release-blocking in the later canonical hosted verification stages.

The `shopify-mcp-gateway-verification` stage must prove:

- Platform health is `UP`
- managed MCP Gateway product-service record is active, secret-configured, healthy, and has no blocking drift
- managed Shopify Bridge product-service record is active, secret-configured, healthy, and has no blocking drift
- MCP Gateway `/api/admin/overview` rejects missing internal key
- MCP Gateway `/api/admin/overview` accepts the managed internal key
- Marketplace MCP discovery uses the Gateway and sees Shopify `search_catalog`
- Gateway can run `tools/list` against Shopify Storefront MCP at `/api/mcp`
- Gateway can execute `shopify_search_catalog` and return normalized `MCP_TOOL_RESULT`
- Bridge MCP readiness checks Shopify Storefront MCP `/api/mcp` with the live tool set needed by the first product
- Bridge delegated action execution returns normalized `MCP_TOOL_RESULT`

The gate is intentionally stricter than local unit tests. It validates deployed service wiring, managed secrets, endpoint selection, and release evidence.

---

## Staging Pass Criteria

Staging is ready for controlled design-partner activity when all of the following are true:

- latest `Platform-V8` commit is deployed to staging Platform backend
- staging MCP Gateway is reconciled/restarted after deploy
- staging Shopify Bridge is reconciled/restarted after deploy
- public health checks pass for Platform, Runtime, Shopify Bridge, and MCP Gateway
- `scripts/verify-shopify-mcp-gateway.sh` passes against staging
- `full-platform-release-readiness` passes and `/api/verification-suites/release-gate` returns `READY`
- `/api/shopify/readiness-audit/latest` returns a fresh design-partner-ready state or a fresh equivalent full-gate Shopify readiness stage
- evidence paths and run ids are recorded in `CODEX_WORKING_CONTEXT.md`

---

## External Gates

These are not implementation gaps, but they block stronger claims:

### Storefront UCP Catalog Endpoint

The first product release gate uses Shopify Storefront MCP `/api/mcp` because it is live on the staging shop and exposes `search_catalog`. The stronger `/api/ucp/mcp` catalog endpoint remains gated until the target shop serves that endpoint without storefront-password redirects and the configured UCP agent profile is verified in live `tools/list` and `tools/call`.

### Customer Account MCP

Required before claiming live customer-account capability:

- Shopify Customer Account OAuth/PKCE configuration
- protected customer data posture and approval where required
- customer access token/session binding in the product host
- live `tools/list` and `tools/call` evidence against the staging store
- denial evidence when customer token/session is missing

### Checkout MCP

Required before claiming live checkout capability:

- Shopify Checkout MCP client credentials
- checkout partner/security readiness
- explicit terminal-operation enablement policy if terminal checkout actions are tested
- live `tools/list` and safe `tools/call` evidence
- denial evidence when checkout credentials or terminal-operation approval is missing

---

## Product Packaging

Design-partner package name:

```text
Shopify Companion MCP Starter
```

Claim-safe positioning:

```text
AI shopping assistant for Shopify storefront discovery and guided commerce, using Shopify MCP with governed execution and release evidence.
```

Operator-facing proof package:

- release gate run id
- Shopify first-product readiness audit summary
- MCP Gateway verification summary
- Bridge readiness summary
- Marketplace plugin/action inventory
- known external gates for Customer Account and Checkout MCP

---

## Verification Commands

Local checks before deploy:

```bash
bash -n scripts/verify-shopify-mcp-gateway.sh
bash -n scripts/verify-platform-code-regression.sh
mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformVerificationSuiteServiceTest,PlatformVerificationSuiteScriptContextServiceTest test
mvn -f product-services/mcp-execution-gateway-service/pom.xml -q test
mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=McpActionExecutionGatewayTest,ShopifyBridgeActionExecutionServiceTest,ShopifyBridgeSupportReadinessServiceTest test
```

Staging checks after deploy:

```bash
scripts/verify-shopify-mcp-gateway.sh
```

Then run the Platform suite:

```text
full-platform-release-readiness
```

Do not print raw secrets while running these checks.

---

## Completion Criteria

009.3 is complete when:

- this readiness plan is committed and pushed
- the new `shopify-mcp-gateway-verification` stage is live on staging
- staging `full-platform-release-readiness` passes with the MCP Gateway stage included
- the latest release gate reports `READY`
- the Shopify readiness audit reflects fresh pass evidence
- launch claims are limited to design-partner readiness until production and external Shopify auth gates are completed

---

## Release Gate Remediation Log

### 2026-05-06

- Staging Platform backend was deployed through Coolify to commit `3fde4faf8` after the release gate exposed canonical runtime authorization drift. The canonical verification fleet now uses `ALLOW_VERIFIED` runtime authz for Platform-managed verification rollouts while preserving connector route-level authorization.
- Canonical rollouts were repaired through `/api/deployments/verification-rollouts/recreate` for `ecommerce`, `qdrant`, `pinecone`, `milvus`, and `weaviate`. Final rollout inventory showed all canonical deployments `APPLIED_VERIFIED`, `ACTIVE`, and `verificationReady=true`.
- The earlier qdrant hosted verification failure was rechecked directly with hosted run `hvr-dd2d009e`, which passed: `PASS: All checks completed. (43 passes, 2 warnings)`.
- The earlier Shopify Bridge delegated MCP 502 was replayed against live staging. Direct MCP Gateway action execution and Bridge delegated execution both returned HTTP `200`, `success=true`, and normalized `MCP_TOOL_RESULT` evidence for `shopify_search_catalog`.
- Targeted Platform suite `shopify-mcp-gateway-verification` passed as run `vsr-ce3a7a61`. Its Bridge delegated MCP action stage passed through the platform-hosted runner.
- Full Platform release gate passed as run `vsr-dc3204cf`, completed at `2026-05-06T01:38:50Z`.
- `/api/verification-suites/release-gate` returned `READY=true` / `status=READY`; the recorded freshness window expires at `2026-05-06T13:38:50Z`.
- Hosted verification evidence in the full suite:
  - marketplace hosted run `hvr-05692359`: `PASS: All checks completed. (42 passes, 2 warnings)`.
  - ecommerce hosted run `hvr-002dcf32`: `PASS: All checks completed. (43 passes, 2 warnings)`.
  - qdrant hosted run `hvr-d224a9a4`: `PASS: All checks completed. (43 passes, 2 warnings)`.
- 009.3 staging status is design-partner ready for the claim-safe product boundary in this document. Production launch and stronger Customer Account / Checkout MCP claims remain gated by the external Shopify auth, protected-data, checkout, and production deployment requirements listed above.

### 2026-05-05

- `full-platform-release-readiness` passed through the new Shopify MCP Gateway stage and then failed in the Shopify first-product readiness audit because the store chat path was still blocked by deployment release posture.
- The active Shopify Companion deployment had already published the MCP action catalog, but Coolify still retained stale preview-scoped env rows for the same action-catalog/version keys. The runtime consumed those stale preview rows and served the previous vector/action configuration even after a normal-env reapply passed.
- Platform Coolify env writes now update normal env rows through bulk update and preview env rows through the single-env update endpoint, matching Coolify's key+preview behavior.
- Release gating now treats retryable marketplace dataset runtime `404` responses as a longer deployment-settle window and preserves the runtime error body in Platform failures, so operators can distinguish runtime warmup from real vector-space/config defects.
- The next live gate exposed a storefront chat failure where the runtime executed `shopify_search_catalog` but the MCP Gateway rejected the action because `shopDomain` was missing from trace metadata. Bridge storefront chat now always forwards the resolved shop domain as a sanitized `shopify-storefront-context` attachment, and runtime connector execution promotes that metadata into MCP Gateway trace.
- The active Storefront MCP bundle also still carried UCP catalog aliases from earlier drafts. A new migration resets the live storefront read MCP bundle to the standard Shopify Storefront MCP `/api/mcp` tools that are actually available on the staging shop: `shopify_search_catalog`, `shopify_get_product_details`, and `shopify_search_policies`.
- Canonical hosted verification then exposed a stale Weaviate provider default. The `weaviate` rollout still used the old Railway-hosted Weaviate endpoint, so pre-apply provider connectivity failed before runtime apply. Platform now treats Weaviate verification as environment/private-configuration driven through `PLATFORM_VERIFICATION_WEAVIATE_HOST` or `WEAVIATE_HOST`; the stale Java default was removed.
- Staging Platform Coolify env was updated with the current Weaviate Cloud REST host from the private handoff. The update triggered a Platform redeploy, after which the staging Hetzner/Coolify host became unresponsive and required provider-level recovery.
- This 2026-05-05 blocker was superseded by the 2026-05-06 pass evidence above: staging recovered, canonical rollouts were repaired, `full-platform-release-readiness` passed, and `release-gate=READY`.

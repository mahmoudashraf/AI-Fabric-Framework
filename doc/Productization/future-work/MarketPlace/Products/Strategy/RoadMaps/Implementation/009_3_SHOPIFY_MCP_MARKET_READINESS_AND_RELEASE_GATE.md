# 009.3 Shopify MCP Market Readiness And Release Gate

Status: in progress on 2026-05-05. Staging is the active target. Production is not deployed by this plan.

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
Broad self-serve production launch: no, until production deployment, app-review posture, and Customer Account/Checkout auth gates are completed.
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

## Release Gate Definition

The release blocker is the Platform `full-platform-release-readiness` suite.

009.3 requires the full suite to include these Shopify/MCP stages:

- `shopify-companion-verification`
- `shopify-mcp-gateway-verification`
- `shopify-first-product-readiness-audit`

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

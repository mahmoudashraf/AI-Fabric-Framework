# 009.3 Shopify MCP Market Readiness And Release Gate

Status: staging release gate passed on 2026-05-08. Latest full pass is `vsr-4a50d909`, completed at `2026-05-08T22:41:28.687806Z`; release-gate freshness expires at `2026-05-09T10:41:28.687806Z`. Staging is the active target. Production is not deployed by this plan.

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
- Keep the PR `#156` P1/P2 review fixes release-gated: Coolify transport failures must continue returning the structured `502` / `COOLIFY_UPSTREAM_FAILURE` contract, and MCP Gateway Streamable HTTP connect/read timeouts must remain wired into the HTTP request factory.
- Keep Platform release/verification async work off a single-thread bottleneck. Release execution and verification-suite execution must use bounded parallel executors so one slow Coolify or repair call cannot starve unrelated platform operations.
- Keep higher-tier public claims gated until Shopify Customer Account MCP and Checkout MCP have the required external Shopify auth/security material, protected customer data posture, credentials, storefront readiness, and live `tools/list` / safe `tools/call` evidence. Customer Account MCP now has read-only staging `tools/call` proof. Checkout MCP credentials now exist in staging and Shopify token exchange succeeds, but live Checkout MCP remains gated until the staging store serves `/api/ucp/mcp` without storefront-password redirects.
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

- Shopify Customer Account OAuth/PKCE configuration in Bridge staging env
- protected customer data posture and approval where required
- customer access token/session binding in the product host through the registered Bridge callback
- live `tools/list` and `tools/call` evidence against the staging store
- denial evidence when customer token/session is missing

Prepared platform/Bridge behavior:

- Customer Account MCP actions remain Marketplace `ACTION` plugins, but Bridge fails closed with `CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED` until OAuth/PKCE posture is configured.
- Bridge implements the Customer Account OAuth/PKCE backend endpoints `/api/customer-auth/start`, `/api/customer-auth/callback`, and `/api/customer-auth/session`.
- Bridge encrypts Customer Account token material at rest and stores only a shop-scoped HMAC of the shopper session identifier for lookup.
- After posture is configured, Bridge fails closed with `CUSTOMER_ACCOUNT_AUTH_REQUIRED` until a customer OAuth access token is bound to the shopper session.
- Shopify Bridge action execution resolves the bound customer OAuth access token server-side by `shopDomain` plus shopper session; action params, browser payloads, and inbound trace fields are not accepted as token sources.
- MCP Gateway can attach the bound customer OAuth access token to Customer Account MCP requests using `CUSTOMER_OAUTH_PKCE`.
- Live staging proof now exists for the read-only order-status boundary: a real customer browser login bound a shopper session, and Shopify Customer Account MCP `get_most_recent_order_status` / `get_order_status` returned normalized `MCP_TOOL_RESULT` evidence through Bridge and the MCP Gateway. The Marketplace bundle intentionally exposes only those live-observed Customer Account MCP tools until additional Customer Account tools are proven through live discovery and safe `tools/call`.

Credential/config intake names:

- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_ENABLED=true`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED=true`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_ID`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_REDIRECT_URI`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES=customer-account-mcp-api:full`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STATE_TTL`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SESSION_TTL`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT`
- staging test customer login flow that can complete the registered `/api/customer-auth/callback` flow and bind the token to the shopper session

### Checkout MCP

Required before claiming live checkout capability:

- Shopify Checkout MCP client credentials
- staging storefront access that does not redirect `/api/ucp/mcp` to the password page
- checkout partner/security readiness
- explicit terminal-operation enablement policy if terminal checkout actions are tested
- live `tools/list` and safe `tools/call` evidence
- denial evidence when checkout credentials or terminal-operation approval is missing

Prepared platform/Gateway behavior:

- Checkout MCP actions remain Marketplace `ACTION` plugins, but Bridge fails closed with `CHECKOUT_MCP_NOT_CONFIGURED` until managed checkout client credentials are configured.
- MCP Gateway supports `SHOPIFY_AGENTIC_CLIENT_CREDENTIALS` and uses Shopify's JSON token request to `https://api.shopify.com/auth/access_token`.
- Platform-managed MCP Gateway provisioning maps configured checkout credentials into `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID` and `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET` for gateway-only secret resolution.
- Checkout UCP uses direct JSON-RPC `tools/call` through the MCP Gateway with the UCP agent profile in tool arguments and the Shopify-required `Shopify-Buyer-IP` header derived from server request context. The Gateway does not add a storefront password cookie or other password-page bypass.
- Terminal checkout tools remain disabled unless `SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED=true`.
- Staging credential proof as of 2026-05-08: the provided Checkout MCP Catalog credentials are stored as Platform secrets, the managed MCP Gateway and Shopify Bridge were reconciled successfully, and Shopify's token endpoint issues an agentic access token. Direct POSTs to both `shopping-companion-test.myshopify.com/api/ucp/mcp` and `shop-staging.loomai.pro/api/ucp/mcp` currently return HTTP `302` to `/password` because the staging online store has storefront password protection enabled (`onlineStore.passwordProtection.enabled=true`). A local diagnostic using the storefront password cookie plus `Shopify-Buyer-IP` reached Checkout MCP and returned a governed UCP result for a safe invalid `get_checkout` id, proving the remaining managed-path blocker is storefront password protection rather than agent credentials or buyer IP semantics.

Credential/config intake names:

- Platform secrets:
  - `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID`
  - `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`
- Managed service env produced after both secrets exist:
  - `SHOPIFY_BRIDGE_CHECKOUT_MCP_ENABLED=true`
  - `MCP_GATEWAY_ENVIRONMENT_SECRET_RESOLUTION_ENABLED=true`
  - `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID`
  - `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET`
- Optional only for approved staging terminal tests:
  - `SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED=true`

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

### 2026-05-08

- After protected customer data usage was selected in the Shopify Partner portal for the dev-store install, a real staging customer browser login completed through `/api/customer-auth/start` and `/api/customer-auth/callback`.
- Session binding proof: `/api/customer-auth/session` for the fresh shopper session returned `configured=true` and `authenticated=true`.
- First post-login action attempt exposed stale MCP Gateway deployment code: `CUSTOMER_OAUTH_PKCE` was rejected as unsupported. Reconciled the managed `mcp-execution-gateway` product service through Platform; the new Coolify deployment became healthy and the gateway accepted the customer OAuth auth mode.
- Live Customer Account MCP proof then passed through Bridge -> MCP Gateway -> Shopify Customer Account MCP:
  - `get_most_recent_order_status` returned HTTP `200`, `success=true`, normalized `MCP_TOOL_RESULT`, and Shopify tool text `No orders found for this customer.`
  - `get_order_status` with `order_number=1001` returned HTTP `200`, `success=true`, normalized `MCP_TOOL_RESULT`, and Shopify tool text `Order not found with number: 1001`.
- The earlier Marketplace Customer Account MCP bundle contained unverified tool aliases (`get_customer_orders`, `lookup_order`, and return-request tools). Those were removed from the product catalog. The bundle now exposes only the live-observed read-only Customer Account MCP tools: `shopify_get_most_recent_order_status` and `shopify_get_order_status`.
- Added migration `V92__shopify_customer_account_mcp_live_tool_names.sql` so already-deployed Platform databases converge to the same live-observed Customer Account MCP action catalog.
- Deployment remediation: an initial commit changed already-applied migration `V83`, which staging correctly rejected with a Flyway checksum mismatch. The fix restored `V83` unchanged and kept the deployed-catalog change in additive migration `V92` only.
- Staging deploy from commit `996785fa7` completed through Coolify deployment `ateasu96dnfetqysbd0ku4l0`; Platform health returned `UP`, and the live Marketplace endpoint returned Customer Account MCP plugin version `1.0.1` with only `shopify_get_most_recent_order_status` and `shopify_get_order_status`.
- Post-deploy bound-token proof passed again through Bridge -> MCP Gateway -> Shopify Customer Account MCP for both live catalog tools. The staging test customer has no orders, so Shopify returned successful MCP envelopes with no-order/not-found tool text.
- Final staging deploy from branch HEAD commit `edc8d5b61` completed through Coolify deployment `dbudzzhqpe2bpq67irxit9jk`; Platform health returned `UP`.
- The Partner Supabase JWT was refreshed again from local private test-account material and stored back into Platform secret `PARTNER_SUPABASE_JWT` without printing the token.
- Fresh full release gate `vsr-a3069cb1` passed with all 14 stages green. `/api/verification-suites/release-gate` returned `READY=true`, `status=READY`, completed `2026-05-08T21:52:05.687947Z`, and expires `2026-05-09T09:52:05.687947Z`.
- Checkout MCP remains externally gated because `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID` and `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET` are still missing from Platform secrets.

### 2026-05-07

- A fresh full staging gate (`vsr-640bca52`) was rerun after the Customer Account MCP custom-domain unblock and failed at `partner-enablement-verification` because the stored Partner Supabase JWT was expired/invalid.
- The Partner Supabase JWT was refreshed from local private Supabase test-account material and written back to Platform secret `PARTNER_SUPABASE_JWT` without printing the token.
- Targeted `partner-enablement-verification` then passed as run `vsr-7013bc78`.
- Fresh `full-platform-release-readiness` passed as run `vsr-b71dbec2`, completed at `2026-05-07T00:18:10Z`; `/api/verification-suites/release-gate` returned `READY=true` / `status=READY` with freshness expiry `2026-05-07T12:18:10Z`.
- `vsr-b71dbec2` passed all 14 release stages: shared inference health, Platform admin live regression, canonical rollout inventory, managed vector provider verification, Coolify provider verification, Marketplace install flow, Shopify Companion verification, Shopify MCP Gateway verification, Shopify first-product readiness audit, Partner enablement, Thinker resolver readiness, Marketplace hosted verification, ecommerce hosted verification, and Qdrant hosted verification.
- Hosted verification evidence from `vsr-b71dbec2`: marketplace passed with 42 passes / 2 warnings, ecommerce passed with 43 passes / 2 warnings, and Qdrant passed with 43 passes / 2 warnings.
- Shopify Storefront MCP remains live for the first product path: Bridge readiness is ready for Storefront MCP and the `shopify_search_catalog` path is covered by the passing `shopify-mcp-gateway-verification` stage.
- Customer Account MCP staging config/OAuth start is complete, including the custom storefront domain `shop-staging.loomai.pro`; the remaining claim gate is a real customer browser login through Shopify-hosted auth and a bound-token safe `tools/call`.
- Checkout MCP remains externally gated because `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID` and `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET` are still missing from Platform secrets.
- Production was not deployed or claimed by this pass.

### 2026-05-06

- Latest staging release-gate evidence after Platform admin-key rotation: `full-platform-release-readiness` passed as run `vsr-90ca64ba`, completed at `2026-05-06T22:36:55Z`; `/api/verification-suites/release-gate` returned `READY=true` / `status=READY` with freshness expiry `2026-05-07T10:36:55Z`.
- Hosted verification evidence from `vsr-90ca64ba`: marketplace `hvr-b885536b` passed with 42 passes / 2 warnings, ecommerce `hvr-8a0d4ce5` passed with 43 passes / 2 warnings, and qdrant `hvr-551c1c39` passed with 43 passes / 2 warnings.
- Staging Platform backend was also deployed to commit `b911222ac` for the Partner readiness-pack cleanup. `V91__normalize_low_cost_shopify_readiness_pack.sql` applied live: `LOW_COST` now uses `starter-launch-readiness`.
- Coolify staging Platform backend env scope was repaired for non-preview redeploys. Required DB env, release-suite URL env, `PLATFORM_PUBLIC_BASE_URL`, Shopify Bridge URL/shop/product-service refs, Weaviate host, and release-gate support settings were written as normal runtime env rows, not only preview rows.
- Platform DB-backed signing secrets required by canonical rollout checks were restored through the Platform Secrets API. Values remain in private/operator material only; tracked docs must record only secret names and readiness state.
- The staging `PLATFORM_ADMIN_API_KEY` was rotated from the earlier weak operator value, stored in Coolify/private handoff only, and verified against live `/api/platform/secrets` before rerunning the full gate.
- A first post-rotation full run (`vsr-e31e820b`) failed at `marketplace-hosted-verification` with a Qdrant Cloud control-plane 401. Direct live provider-connectivity for the same marketplace deployment then returned `qdrant_cloud_control_plane=READY`, and the rerun `vsr-90ca64ba` passed all 14 stages. No code change was required for that final Qdrant probe; the remaining operational rule is to recheck Qdrant management/data-plane secret freshness before each release gate.
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
- External auth gates are prepared up to credential intake: Bridge returns explicit Customer Account and Checkout MCP gate errors, MCP Gateway supports Customer Account token pass-through and Shopify Checkout client-credentials JSON token exchange, Platform managed product provisioning maps checkout credentials to gateway-only `MCP_SECRET_...` env names, and release/verification async executors were widened to avoid single-thread platform starvation.
- Bridge Customer Account OAuth/PKCE implementation was added after the initial 009.3 pass: `/api/customer-auth/start` performs Customer Account OIDC discovery and PKCE redirect, `/api/customer-auth/callback` exchanges the code with confidential-client auth, Customer Account token material is encrypted at rest in `shopify_customer_account_sessions`, and MCP action execution resolves bound tokens server-side by shopper session without trusting inbound trace token fields. MCP Gateway HTTP calls and Customer Account discovery/token calls now use explicit configured connect/read timeouts. Stronger customer-account product claims still require protected-data approval confirmation plus live customer login and safe `tools/call` evidence on staging.
- Shopify Bridge staging was then deployed to commit `2c3c4306a` and Customer Account MCP env was configured in Coolify staging. Live probes now show Bridge health `UP`, storefront bootstrap HTTP `200` with Customer Account auth URLs, `/api/customer-auth/session` HTTP `200` with `configured=true`, `/api/customer-auth/start` HTTP `302` to Shopify Customer Account OAuth with the registered staging callback, and `shopify_get_customer_orders` HTTP `409` / `CUSTOMER_ACCOUNT_AUTH_REQUIRED` before shopper login.
- The staging unblock exposed duplicate Coolify env rows on the Bridge app. Platform now hardens `CoolifyApiClient.updateEnvironmentVariables(...)` by reading back env rows after writes and deleting older duplicates for the updated key plus preview scope, preventing stale retained rows from masking runtime env values on later managed applies.
- Storefront MCP was rechecked after the Customer Account env change: `shopify_search_catalog` still returned HTTP `200`, `success=true`, and normalized `MCP_TOOL_RESULT` evidence through Bridge -> MCP Gateway -> Shopify MCP.
- Current remaining Customer Account gate is no longer missing endpoint/config. It is the final real customer login and bound-token `tools/call` proof. Checkout MCP remains gated by separate Shopify checkout credentials/readiness.
- A fresh full gate later exposed stale Partner release-gate auth and package-readiness drift. The Partner Supabase JWT was refreshed from local private Supabase test material and stored back in the Platform secret `PARTNER_SUPABASE_JWT` without printing the token.
- Staging Platform backend now has a dedicated `PLATFORM_ADMIN_API_KEY` runtime env in Coolify for release-gate operations. The key is stored only in local/private operator material and in Coolify, not in tracked docs.
- The test shop was reconciled through Platform APIs only: `order-lookup` was removed from enabled storefront surfaces, billing was recorded as `FREE/ACTIVE`, and Free package reconciliation job `spj-b7f16d23` completed `READY`.
- Partner verification pack selection now ignores unknown configured pack ids before applying governed-surface detection, and `V91__normalize_low_cost_shopify_readiness_pack.sql` normalizes LOW_COST profile rows from old `shopify-companion-free-readiness` to `starter-launch-readiness`.
- Targeted `partner-enablement-verification` passed after cleanup as run `vsr-5ccfa1a2`.

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

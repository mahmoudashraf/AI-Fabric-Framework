# 010.4 Shopify Companion Indexing Architecture Cleanup Plan

Status: implemented, locally verified, deployed to staging, and live staging source-freshness verified

Created: 2026-05-16

Parent plans:

- [010 GTM And Partner Portal Launch Readiness](010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md)
- [010.1 Shopify Companion UI Launch Readiness](010_1_SHOPIFY_COMPANION_UI_LAUNCH_READINESS.md)
- [010.2 Shopify Companion Two-Mode Surface Simplification](010_2_SHOPIFY_COMPANION_TWO_MODE_SURFACE_SIMPLIFICATION.md)
- [010.3 Shopify Companion Query Speed, Accuracy, And Reliability Optimization Plan](010_3_SHOPIFY_COMPANION_QUERY_SPEED_ACCURACY_RELIABILITY_OPTIMIZATION_PLAN.md)
- [009.3 Shopify MCP Market Readiness And Release Gate](009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md)

Related implementation areas:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationQueueSchedulerService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreDocumentSyncService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/web/ShopifyAdminController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeVectorizationSourceService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeStoreSyncService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeStoreAdminService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`
- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`
- `ai-fabric-product/ai-fabric-vectorization-runner`
- `ai-fabric-product/ai-fabric-vectorization-core`

## Executive Decision

Loom Companion indexing must be cleaned up around one production architecture:

```text
Shopify Admin API
  -> Shopify Bridge source-adapter endpoints
  -> Platform vectorization plan and runner
  -> Runtime data-sync / vector index
  -> Storefront chat retrieval and answer generation
```

Shopify remains the source of truth for catalog, inventory, pricing, policies, content, customer-owned resources, and order state. Bridge must not become a store catalog database. Platform must not maintain a second full Shopify content copy as an indexing prerequisite. Runtime can keep a derived retrieval index because it is the serving cache used by search, RAG, and answer generation, but that index must be refreshed from Shopify-backed source adapters.

The implementation now uses the vectorization-source path as the normal Loom Companion indexing path. Manual reindex and automatic live indexing no longer call the older document push sync path before creating vectorization runs. Reindex is the canonical merchant-facing freshness operation.

## Implementation Result

Implemented on 2026-05-16:

- Manual `index-all`, `reindex-all`, `reindex-selected`, and `vectorize-now` Platform flows create vectorization runs directly from the Bridge source-adapter configuration.
- Automatic live indexing dispatches grouped Shopify change events into vectorization runs without a legacy `/run-sync` preflight.
- Storefront readiness and widget-live promotion no longer require the historical `SYNCED` document-sync status; they only block on source readiness, verified release, widget failure, or failed derived-index verification.
- Merchant UI copy and primary actions now use `Refresh knowledge` / `Reindex` language instead of teaching merchants to run `Sync now`.
- Legacy document sync remains available as a compatibility/operator repair path only; it is not part of normal merchant reindexing or auto live indexing.
- Development and merchant guides now document Shopify Admin API through Bridge source endpoints as the indexing source of truth and Runtime vector data as a derived retrieval index.

Live proof completed on 2026-05-16:

- Staging product: `MetroTab 11 5G Tablet`, product id `gid://shopify/Product/7939427008595`.
- Shopify Admin source change: first variant price changed from `679.00` to `681.00`.
- Platform reindex-only run: `vrn-4826fc3b`, status `COMPLETED`, completed at `2026-05-16T08:57:45.462588Z`.
- Storefront chat query: `What is the price of the MetroTab 11 5G Tablet?`
- Storefront chat answer: `The price of the MetroTab 11 5G Tablet is $681.00. This product is currently available.`
- Retrieved evidence document included `Price range: 681.0 USD` and variant price `681.00 USD`.
- Recent Platform and Bridge runtime logs checked after the proof showed zero `/run-sync`, `/documents/sync`, or `runSync` mentions.

## Problem Statement

Merchant and operator behavior is currently confusing:

- The embedded app exposes `Sync now` and separate vectorization operations.
- Operators can say "reindex", but the code still runs a legacy document sync first.
- Auto live indexing also runs the legacy sync before dispatching a vectorization run.
- The old sync path pushes full normalized Shopify documents into Platform/runtime, while the new vectorization path pulls from Shopify through Bridge source endpoints.
- Inventory and availability freshness is easy to misdiagnose because "Sync now" and "Reindex" look like separate required steps.

This is not the desired production posture. For Loom Companion, the merchant action should be "Refresh knowledge" or "Reindex", and that action should read live Shopify data through the source adapter path.

## Current State Inventory

### Canonical Shopify Source

Shopify Admin API is the canonical source for:

- product title, body, handle, images, variants, price, availability, inventory, and product metadata
- collections
- store pages
- policies
- articles
- metaobjects
- customer account and order data when the proper Shopify protected data and customer-account authorization gates are satisfied

The staging investigation on 2026-05-16 showed the newly fed electronics catalog products were actually sold out in Shopify:

- `totalInventory=0`
- variant `inventoryQuantity=0`
- `availableForSale=false`
- `inventoryPolicy=DENY`

That result was a Shopify source-state issue, not a vector index hallucination.

### Legacy Push Sync Flow

The legacy sync flow is:

```text
Merchant UI / Bridge admin
  -> Bridge ShopifyBridgeStoreSyncService
  -> Shopify Admin GraphQL
  -> Platform /api/shopify/stores/{shop}/documents/sync
  -> ShopifyStoreDocumentSyncService
  -> Runtime data-sync batch
  -> platform_shopify_store_documents tracking
```

Important code references:

- Merchant endpoint: `ShopifyMerchantController#syncNow`, `POST /api/app/store/sync-now`
- Bridge admin endpoint: `ShopifyBridgeAdminController#runSync`, `POST /api/admin/stores/{shopDomain}/run-sync`
- Bridge service: `ShopifyBridgeStoreSyncService`
- Platform client: `PlatformShopifyStoreClient#syncDocuments`
- Platform endpoint: `ShopifyAdminController#syncDocuments`, `POST /api/shopify/stores/{shopDomain}/documents/sync`
- Platform service: `ShopifyStoreDocumentSyncService#sync`
- Tracking table: `platform_shopify_store_documents`

This flow builds a full document batch and pushes it into Platform/runtime. Platform tracks document fingerprints and stale deletions. It is useful as historical compatibility, but it should not be the merchant-facing source of truth for current Loom Companion reindexing.

### Vectorization Source Flow

The newer vectorization flow is:

```text
Merchant UI / Platform operation
  -> Platform ShopifyStoreVectorizationService
  -> Vectorization source connection configured as REST_API
  -> Vectorization runner
  -> Bridge /api/admin/stores/{shop}/vectorization-source/{entityType}
  -> Shopify Admin API
  -> Runtime data-sync / vector target
```

Important code references:

- Merchant endpoints:
  - `POST /api/app/store/vectorization/index-all`
  - `POST /api/app/store/vectorization/reindex-all`
  - `POST /api/app/store/vectorization/reindex-selected`
  - `POST /api/app/store/vectorization/vectorize-now`
- Platform source config builder: `ShopifyStoreVectorizationService#buildDatasetConfig`
- Product source path: `/api/admin/stores/{shop}/vectorization-source/product`
- Support-policy source path: `/api/admin/stores/{shop}/vectorization-source/support-policy`
- Bridge controller: `ShopifyBridgeAdminController#vectorizationSource`
- Bridge source service: `ShopifyBridgeVectorizationSourceService`
- Runner: `VectorizationRunExecutor`
- REST source adapter: `RestApiVectorizationSourceAdapter`
- Runtime writer: `ConnectorDataSyncTargetWriter`

This is the correct direction. Bridge remains the Shopify auth/source boundary, Platform owns plan/run orchestration, and Runtime stores the derived retrieval target.

### Current Mixing That Must Be Removed

Manual vectorization previously ran legacy sync first. This has been removed:

- `ShopifyStoreVectorizationService#runManualIndexAction`
- removed call: `bridgeAdminClient.runSync(store)`
- current behavior: `vectorizationService.createRunForTrustedCaller(...)`

Automatic live indexing previously ran legacy sync first. This has been removed:

- `ShopifyStoreVectorizationQueueSchedulerService`
- removed call: `bridgeAdminClient.runSync(store)`
- current behavior: event grouping and vectorization dispatch

This makes vectorization freshness depend on an old document-push step that should not exist in the main indexing path.

## Target Architecture

### Responsibility Boundaries

Shopify:

- canonical merchant source data
- inventory and availability truth
- customer/order truth behind Shopify authorization gates

Shopify Bridge:

- Shopify app install and credential boundary
- Shopify Admin API and Customer Account auth boundary
- source-adapter endpoints for vectorization
- webhook receipt and event forwarding
- merchant UI proxy to Platform APIs
- no durable full catalog copy

Platform:

- store connection and deployment metadata
- package/profile/policy configuration
- vectorization source connection definitions
- selected data categories and field policy
- run orchestration, status, evidence, and audit
- no durable full Shopify catalog copy required for reindex

Vectorization Runner:

- pulls pages from Bridge source endpoints on demand
- maps source records into runtime data-sync records
- records run progress, failures, and evidence

Runtime:

- derived vector/search index
- retrieval evidence
- answer generation
- no claim of being the canonical Shopify store database

### Allowed Persistence

Allowed durable data:

- Shopify connection metadata and credential secret references
- source connection config
- vectorization plan, selected categories, field policy, runs, events, and evidence
- runtime derived index records needed for retrieval
- audit events and support evidence
- short-lived session/customer-owned resource references where explicitly approved by the owned-user resource design

Not allowed as the main indexing architecture:

- Bridge storing a durable full duplicate catalog
- Platform requiring a full document push before every reindex
- merchant-facing "Sync now" as a required freshness step
- automatic live indexing failing only because legacy sync failed before vectorization started

## Implementation Slices

### Slice 0: Add Regression Tests For The Current Smell

Status: implemented and focused-test verified.

Goal:

- Lock the desired behavior before changing code.

Required tests:

- Manual `index-all` does not call `ShopifyBridgeAdminClient#runSync`.
- Manual `reindex-all` does not call `ShopifyBridgeAdminClient#runSync`.
- Manual `reindex-selected` does not call `ShopifyBridgeAdminClient#runSync`.
- Auto live indexing does not call `ShopifyBridgeAdminClient#runSync`.
- Manual reindex still creates a vectorization run with the expected source categories.
- Bridge source endpoint is still configured as the vectorization source.

Candidate files:

- `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationServiceTest.java`
- `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationQueueSchedulerServiceTest.java`

### Slice 1: Remove Legacy Sync From Manual Vectorization

Status: implemented.

Goal:

- `index-all`, `reindex-all`, and `reindex-selected` should enqueue vectorization runs without calling `/run-sync`.

Implementation:

- Remove `bridgeAdminClient.runSync(store)` from `ShopifyStoreVectorizationService#runManualIndexAction`.
- Keep `reconcile(store.getShopDomain())` as the source connection and policy preparation step.
- If a preflight is needed, use source readiness semantics, not document sync semantics. Candidate options:
  - rely on runner source discovery and fail the run with source-fetch evidence
  - add a lightweight source preflight that checks Bridge credentials and source endpoint reachability without syncing documents
  - use existing source-preflight support if it already produces merchant-safe diagnostics
- Preserve audit event `SHOPIFY_STORE_VECTORIZATION_MANUAL_RUN_TRIGGERED`.
- Make failure messaging say the reindex could not read Shopify source data, not that sync failed.

Acceptance:

- Manual reindex creates a vectorization run directly.
- No Platform or Bridge logs show `/documents/sync` or `/run-sync` for manual vectorization.
- If Bridge credentials are missing, the vectorization run or preflight fails with merchant-safe guidance.

### Slice 2: Remove Legacy Sync From Automatic Live Indexing

Status: implemented.

Goal:

- Webhook-driven auto indexing should enqueue vectorization from events without a legacy pre-sync step.

Implementation:

- Remove `bridgeAdminClient.runSync(store)` from `ShopifyStoreVectorizationQueueSchedulerService`.
- Keep debounce, eligibility, minimum interval, and source-category grouping.
- Source failures should be captured by the vectorization run, or by a lightweight source preflight if one is explicitly introduced.
- Event failure reason should reference source reachability/auth, not "Shopify bridge sync could not complete".

Acceptance:

- Product webhook event can dispatch an auto vectorization run without `/run-sync`.
- A source outage marks the vectorization run or event with clear source-read failure evidence.
- Other eligible events are not blocked by a legacy sync failure.

### Slice 3: Clean Merchant UI Semantics

Status: implemented for the embedded merchant UI primary flows. The legacy API export remains in the UI API module for compatibility, but the app shell no longer wires it into normal merchant actions.

Goal:

- Merchants should see one clear freshness model.

Implementation:

- Remove or hide `Sync now` from primary merchant UI.
- Rename merchant-facing vectorization actions:
  - `Refresh knowledge` for normal incremental freshness
  - `Reindex products` for product-only full refresh
  - `Reindex all enabled data` for complete enabled-source refresh
- Keep legacy sync only in an internal/operator section if still needed temporarily.
- If retained, label it as `Legacy document sync` with internal-only copy.
- Ensure the embedded Shopify app copy says reindex reads current Shopify catalog/policy data through the Loom app connection.

Candidate files:

- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`

Acceptance:

- Merchant launch/admin page no longer implies "Sync now" is required before reindex.
- No dummy success state is shown for refresh/reindex.
- Failed refresh returns merchant-safe guidance plus operator-safe diagnostics.

### Slice 4: Lock Platform API Semantics

Status: implemented for normal indexing paths. Legacy `/documents/sync` remains as a compatibility/operator endpoint and is not called by merchant reindexing or automatic live indexing.

Goal:

- Platform APIs should make vectorization the canonical indexing operation.

Implementation:

- Treat these as the supported indexing APIs:
  - `/api/shopify/stores/{shop}/vectorization/index-all`
  - `/api/shopify/stores/{shop}/vectorization/reindex-all`
  - `/api/shopify/stores/{shop}/vectorization/reindex-selected`
  - vectorization run APIs under the deployment/vectorization model
- Keep `/api/shopify/stores/{shop}/documents/sync` as legacy/internal only while migration is in progress.
- Add a config guard for legacy document sync if needed:
  - default disabled for new Shopify Companion installs
  - enabled only for compatibility or operator repair
- Add audit names that make the distinction obvious:
  - `SHOPIFY_STORE_VECTORIZATION_MANUAL_RUN_TRIGGERED`
  - `SHOPIFY_STORE_LEGACY_DOCUMENT_SYNC_REQUESTED`

Acceptance:

- New installs and normal merchant flows do not call `/documents/sync`.
- Any legacy sync use is explicit, audited, and internal.

### Slice 5: Data Cleanup And Migration

Status: partially implemented. Normal vectorization runs no longer require legacy document sync, so the normal path stops growing legacy tracking rows. Physical cleanup of existing historical rows remains deferred until after staging live proof and rollback-window review.

Goal:

- Stop growing legacy document tracking state from the normal reindex path and define cleanup.

Implementation:

- Ensure `platform_shopify_store_documents` is not written during vectorization runs.
- Keep uninstall cleanup safe:
  - runtime derived index cleanup
  - legacy document tracking cleanup where historical rows exist
- Add an operator cleanup operation or migration plan for stale legacy rows after live proof.
- Do not drop the table until compatibility reads are removed and rollback window is closed.

Acceptance:

- Existing stores keep working.
- New vectorization runs do not create or update legacy document tracking rows.
- Stale legacy rows can be audited and removed safely.

### Slice 6: Source Freshness And Evidence

Status: existing vectorization run evidence remains the supported evidence surface. This cleanup did not add a new evidence schema; source-freshness live proof is still required to validate the evidence end-to-end on staging.

Goal:

- Make freshness observable and prove source-of-truth behavior.

Implementation:

- Vectorization run evidence should include:
  - source endpoint used
  - source category/entity type
  - page count and record count
  - Shopify source timestamps where available
  - selected field policy
  - runtime write result
- UI should show:
  - last successful refresh
  - last failed refresh
  - which enabled data sources are indexed
  - whether the data source is Shopify API-backed
- Logs/audit should distinguish:
  - source fetch failure
  - mapping failure
  - runtime write failure
  - indexing timeout

Acceptance:

- Merchant and support can tell whether product availability in chat came from the latest indexed Shopify source.
- Operator can trace a bad answer to source data, mapping, retrieval, or generation.

### Slice 7: Live Staging Proof

Status: passed on staging on 2026-05-16.

Goal:

- Prove `Reindex` alone refreshes Shopify-derived answers.

Required live proof:

1. Pick a staging product on `shopping-companion-test.myshopify.com`.
2. Change price, availability, or inventory in Shopify Admin.
3. Do not press legacy `Sync now`.
4. Run product-only reindex from the merchant UI or Platform API.
5. Confirm vectorization run completes.
6. Query the storefront chat for that product.
7. Confirm answer evidence reflects the updated Shopify source state.
8. Confirm logs/audit show no `/run-sync` or `/documents/sync` call during the proof.

Evidence to save:

- vectorization run ID
- storefront query payload and response
- audit event IDs
- source record excerpt with no secrets
- before/after Shopify inventory or availability state

## Release Gates

### 010.4_INDEXING_ARCHITECTURE_READY

Status: passed by local code verification on 2026-05-16.

Pass criteria:

- Manual vectorization paths do not call legacy sync.
- Auto live indexing paths do not call legacy sync.
- Merchant UI makes reindex the primary freshness operation.
- Legacy document sync is hidden from merchant launch flows or clearly internal-only.
- Shopify API-backed Bridge source endpoints are the active source for product and support-policy vectorization.
- Tests prove no hidden `/run-sync` dependency remains in normal paths.

### 010.4_SOURCE_FRESHNESS_LIVE_PROOF

Status: passed on staging on 2026-05-16.

Pass criteria:

- A real staging Shopify product inventory or availability change is reflected in chat after reindex only.
- No manual legacy sync is used.
- Runtime evidence remains grounded and shopper-safe.
- Operator evidence shows source fetch, mapping, runtime write, and run completion.

### 010.4_LEGACY_SYNC_CONTAINED

Status: passed for normal merchant and auto-indexing paths. Legacy endpoints still exist for compatibility/operator repair and historical-row cleanup remains deferred.

Pass criteria:

- `/documents/sync` and `/run-sync` are not part of merchant-facing reindex.
- Any remaining legacy sync entry point is internal, guarded, audited, and documented as compatibility.
- A cleanup plan exists for old `platform_shopify_store_documents` rows.

## Verification Results

2026-05-16 local verification:

- Passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyStoreVectorizationServiceTest,ShopifyStoreVectorizationQueueSchedulerServiceTest,ShopifyStoreReadinessEvaluatorTest,ShopifyStoreWidgetServiceTest test`
- Passed: `npm --prefix product-services/shopify-bridge-service/ui run build`
- Passed: `mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyBridgeVectorizationSourceServiceTest,ShopifyBridgeStoreAdminServiceTest,ShopifyMerchantControllerTest,ShopifyBridgeMerchantStoreServiceTest test`
- Passed: `mvn -f ai-fabric-product/pom.xml -q -pl ai-fabric-vectorization-core,ai-fabric-vectorization-runner -am test`
- Passed: `mvn -f product-services/shopify-bridge-service/pom.xml -q test`
- Passed: `mvn -f Platfrom/backend/pom.xml -q test`
- Passed: `git diff --check`
- Passed: `bash -n scripts/verify-shopify-companion.sh`
- Passed: source grep check confirmed normal Platform vectorization services no longer reference `bridgeAdminClient` or `runSync`.
- Passed: UI grep check confirmed the embedded merchant app no longer wires `run-sync`, `handleSyncNow`, `canSyncNow`, or `Sync now` copy.

2026-05-16 staging verification:

- Deployed commit: `e34c6c85b` on `Platform-V9`.
- Passed: Platform backend health returned `{"status":"UP"}`.
- Passed: Shopify Bridge staging health returned `{"status":"UP","groups":["liveness","readiness"]}`.
- Passed: Shopify Admin GraphQL source update changed `MetroTab 11 5G Tablet` price to `681.00`.
- Passed: Platform reindex-only call `POST /api/shopify/stores/shopping-companion-test.myshopify.com/vectorization/reindex-selected` created run `vrn-4826fc3b`.
- Passed: vectorization run `vrn-4826fc3b` completed and Platform summary returned `syncState=IN_SYNC`.
- Passed: storefront chat query returned the updated `$681.00` answer with product evidence containing `Price range: 681.0 USD` and variant price `681.00 USD`.
- Passed: Coolify runtime logs for Platform backend and Shopify Bridge showed zero `/run-sync`, `/documents/sync`, or `runSync` mentions in the checked post-proof window.
- Evidence files saved under `/tmp`: `loomai-0104-shopify-products-before.json`, `loomai-0104-shopify-price-update.json`, `loomai-0104-platform-reindex-selected-start.json`, `loomai-0104-platform-vectorization-poll.json`, `loomai-0104-chat-price-query.json`, `loomai-0104-chat-price-response.json`, `loomai-0104-platform-logs.json`, `loomai-0104-bridge-logs.json`.

The direct standalone vectorization module commands listed below require reactor dependencies to be built first in this workspace. Use the reactor command above for the reliable verification path.

## Verification Commands

Focused Platform tests:

```bash
mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyStoreVectorizationServiceTest test
mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyStoreVectorizationQueueSchedulerServiceTest test
mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyStoreDocumentSyncServiceTest test
```

Focused Bridge tests:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyBridgeVectorizationSourceServiceTest test
mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyBridgeStoreAdminServiceTest test
mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyMerchantControllerTest test
```

Vectorization runner tests:

```bash
mvn -f ai-fabric-product/ai-fabric-vectorization-core/pom.xml -q test
mvn -f ai-fabric-product/ai-fabric-vectorization-runner/pom.xml -q test
```

UI verification:

```bash
npm --prefix product-services/shopify-bridge-service/ui run build
```

Release scripts:

```bash
bash -n scripts/verify-shopify-companion.sh
bash scripts/verify-shopify-companion.sh
```

Live staging proof:

```text
1. Change staging product inventory/availability in Shopify Admin.
2. Run product reindex only.
3. Query storefront chat.
4. Check vectorization run evidence.
5. Check audit/logs for absence of legacy sync calls.
```

## Security And Reliability Rules

- Platform must not use raw Shopify Admin tokens directly for source reads. Bridge remains the Shopify credential boundary.
- Source endpoint calls must continue to use the Bridge admin shared-secret path and secret references, not plaintext config.
- Reindex failure messages must not expose tokens, secret refs, internal URLs, tenant IDs, or provider internals to merchants.
- Vectorization source URLs must remain fixed/generated by Platform for known Bridge endpoints, not merchant-provided.
- Rate limiting and debounce must stay in the event scheduler.
- Page size and cursor pagination must remain bounded.
- Runtime public responses must expose sanitized evidence only.

## Documentation Updates

Updated during implementation:

- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_MCP_FIRST_AND_GATEWAY_DEVELOPMENT_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`

Completed documentation changes:

- Define `Refresh knowledge` / `Reindex` as the merchant freshness operation.
- Document that Shopify Admin API through Bridge source endpoints is the source for indexing.
- Mark `Sync now` as legacy/internal if retained.
- Explain that runtime vector data is a derived retrieval index, not the source of truth.
- Add a troubleshooting section for sold-out products:
  - check Shopify variant inventory and availability first
  - then run product reindex
  - then verify chat evidence

## Implementation Decisions

Decisions applied:

- Hide merchant `Sync now` from the embedded app primary flow.
- Do not run pre-sync before vectorization.
- Use runner source discovery for normal proof; add a lightweight source preflight only if a later UI requirement needs faster failure feedback.
- Keep legacy tables and endpoints for one release window, but stop using them from normal merchant and auto-indexing paths.
- Keep product-only and all-data reindex controls available; use `Refresh knowledge` as the simple merchant-facing action.

## Definition Of Done

010.4 code cleanup is complete when:

- Manual and automatic Loom Companion indexing use the vectorization-source path only.
- Merchant UI no longer teaches users that `Sync now` is required.
- Legacy document sync is contained, guarded, audited, and not part of normal release gates.
- Tests and docs reflect the new architecture.

010.4 live release gate is complete; it was proven on staging when:

- Shopify inventory/availability updates are proven live through reindex-only staging proof.
- Operator evidence confirms no `/run-sync` or `/documents/sync` call happened during that proof.

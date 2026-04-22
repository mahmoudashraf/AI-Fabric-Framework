# Platform Next LLM Session Context Dump

Use this file as the committed, sanitized handoff for the next LLM session.

This is not a secret store.
Do not put raw credentials here.
If live access is required, use the private handoff file separately and keep it out of commits.

## 1. Current Repo State

- Repo root: `/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo`
- Branch: `Platform-V5`
- HEAD: `71196b5efccd765550a010de4ec8c88eada88f1b`
- Date captured: `2026-04-20`
- Worktree status at capture: clean

Important branch note:

- the older sanitized snapshot on this branch referred to `HEAD = 9ba2fd1b737c6bbac76ca1150d36bbc21137eced` on `2026-04-17`
- that older branch-specific snapshot is stale for current `HEAD` assumptions
- this branch now includes newer Shopify Companion product and indexing-planning docs from `2026-04-18` through `2026-04-19`
- do not treat the earlier post-action webhook-policy thread as the default next implementation topic for this branch

## 2. Current Marketplace Baseline On This Branch

Authoritative index:

- `doc/Productization/future-work/MarketPlace/README.md`

Current supported public marketplace plugin types:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

Required interpretation:

- marketplace is a control-plane composition layer
- installs compile into deployment drafts and published versions
- publish and apply remain mandatory before live behavior changes
- runtime and shell do not load arbitrary third-party code
- only runtime-backed contracts should be productized through marketplace

## 3. Relevant Current Guides

Start here for current implementation context:

1. `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
2. `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_PLATFORM_PRODUCT_PHILOSOPHY.md`
3. `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`
4. `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
5. `doc/Productization/future-work/MarketPlace/README.md`
6. `doc/Productization/future-work/MarketPlace/Products/README.md`
7. `doc/Productization/future-work/MarketPlace/Products/PRODUCT_DIRECTION_DECISION_RECORD.md`
8. `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md`
9. `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
10. `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md`
11. `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_VECTORIZATION_TRIGGER_PLAN.md`
12. `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
13. `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md`

Only use the private handoff doc if live credentials are required:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

That private file is operationally useful but intentionally not safe for normal committed handoff usage.

## 4. What Was Just Reviewed In This Session

The active work in this session was:

- orient to the main LLM/platform operating guides
- review the current Shopify Companion product docs
- review the Shopify Companion indexing-trigger plan in detail
- verify the current code surfaces named by the indexing-trigger plan

Current user instruction at the point of this handoff:

- understand the Shopify Companion indexing-trigger work
- do not implement it until the user explicitly asks

## 5. Current Product Direction On This Branch

The current Shopify V1 posture is:

- `Shopify Companion`
- read-first shopping companion
- evidence-backed product and policy assistant
- not a transaction bot

Important product rules:

- Shopify remains a thin first-party consumer of the existing platform
- no Shopify-specific runtime fork
- no new public marketplace plugin type is required for launch
- storefront identity should resolve through stable `consumerId`
- default launch auth posture remains:
  - browser -> Shopify Bridge -> private runtime
- merchant UI must stay bounded:
  - source-category selection
  - preflight
  - indexing and live-update controls
  - storefront activation
  - diagnostics

The merchant should not be asked to manage:

- raw plugin installs
- deployment drafts or versions
- source-connection JSON
- vectorization plan JSON
- secrets
- runner sessions or low-level concurrency

## 6. Current Shopify Companion Baseline On This Branch

Current main product surfaces:

- platform backend:
  - deployment lifecycle
  - marketplace composition
  - Shopify store binding records
  - vectorization connections, plans, runs, and runner registration
- Shopify Bridge service:
  - Shopify install/auth/session handling
  - merchant embedded admin app
  - storefront bootstrap/query/suggestions/event routes
  - normalized vectorization-source endpoints
- theme app extension:
  - storefront launcher and shopper assistant surface

Current vectorization shape:

- source connection adapter type: `REST_API`
- source auth mode: `API_KEY`
- source auth header: `X-BRIDGE-API-KEY`
- source data provider: Shopify Bridge admin endpoints

Current source-category mapping:

- `products` -> `product`
- `collections` -> `product`
- `pages` -> `support-policy`
- `policies` -> `support-policy`

Current required plugin baseline for the Shopify Companion vectorization path:

- `mkp-action-shopify-companion-read`
- `mkp-inference-shared-embeddings`
- `mkp-data-shopify-catalog`
- `mkp-data-shopify-policies`

Current bounded merchant actions that are actually wired now:

- `Reconcile deployment support`
- `Index all enabled data`

Current important gap:

- the product/docs are moving toward `Index`, `Reindex`, and `Live updates`
- the currently wired manual run surface is still the single bounded `vectorize-now` flow for the current enabled entity scope

## 7. Current Verified Code-Level Status

The current code-backed state is:

1. Shopify content/config webhooks are classified in:
   - `product-services/shopify-bridge-service/.../ShopifyWebhookService.java`
2. Those webhooks record state and attempt incremental sync:
   - current behavior is still sync-invalidation oriented, not live-index-intent oriented
3. The platform marks the store `NOT_SYNCED` on webhook-driven invalidation:
   - `Platfrom/backend/.../ShopifyStoreWebhookService.java`
4. Manual indexing is currently triggered through:
   - `Platfrom/backend/.../ShopifyStoreVectorizationService.java`
5. Current execution config is explicitly manual:
   - `triggerMode = SHOPIFY_ADMIN_MANUAL`
6. Shopify document sync already removes stale runtime documents during sync sweeps:
   - `Platfrom/backend/.../ShopifyStoreDocumentSyncService.java`
7. The vectorization runner target writer still emits `UPSERT` only:
   - `ai-fabric-product/.../ConnectorDataSyncTargetWriter.java`
8. Deployment-level reindex semantics already remain version-based through:
   - `Platfrom/backend/.../VectorizationIndexedOutputHashService.java`
   - `Platfrom/backend/.../DeploymentConfigCompiler.java`
   - `Platfrom/backend/.../DeploymentService.java`

Important practical interpretation:

- deployment-snapshot changes are already handled as publish/apply/reindex concerns
- Shopify source-object changes are not yet modeled as a durable live-indexing event pipeline

## 8. Agreed Next Focus: Shopify Companion Indexing Trigger Plan

The current next implementation topic is:

- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_VECTORIZATION_TRIGGER_PLAN.md`

The plan separates three classes of change:

1. deployment-snapshot changes
   - remain draft-backed and reindex-backed
2. Shopify source-object changes
   - should drive indexing against the active deployment snapshot without publish/apply
3. operational runtime changes
   - should not themselves trigger reindex

Merchant-facing target behavior:

- manually index all enabled data
- manually reindex selected entity families
- manually reindex all enabled entity families
- enable live auto indexing per Shopify source family
- choose create/delete/update trigger types
- choose update sensitivity:
  - `ANY_UPDATE`
  - `INDEXED_FIELDS_ONLY`
  - `SELECTED_INDEXED_FIELDS`

Important product wording rule:

- `sync` is internal
- merchant language should be `Index`, `Reindex`, and `Live updates`

## 9. Current Gaps Between Target Plan And Current Code

The following pieces appear to be future work rather than already-landed behavior:

- no persisted `ShopifyStoreVectorizationPolicy` model yet
- no Shopify-specific dirty-event queue / coalescer / dead-letter flow yet
- no sparse indexed-object ledger with indexed-output fingerprints yet
- no indexed-field-aware update evaluation yet
- no targeted manual `Reindex selected types` endpoint yet
- no true object-level incremental `DELETE` support in the vectorization runner yet

The trigger plan should therefore be read as real implementation work, not as already-complete behavior.

## 10. Recommended Immediate Next Sequence

If the next session starts implementation on the indexing-trigger work, use this order:

1. finish the manual merchant-control surface so the product language and backend actions align
2. add persisted store-level trigger policy
3. add a platform-owned dirty-event queue with dedupe, lease, retry, and dead-letter behavior
4. split the pipeline into stable roles:
   - event ingestor
   - coalescer
   - intent dispatcher
   - run enqueuer
5. keep the first auto mode on current safe primitives:
   - webhook -> internal refresh/coalesce -> scoped indexing run
6. derive effective indexed fields from the active deployment snapshot instead of inventing a second field-definition system
7. add the sparse indexed-object ledger and indexed-output fingerprint comparison
8. only later extend the runner for true object-level `UPSERT` and `DELETE`

## 11. Where To Look In Code First

Primary code areas for the indexing-trigger work:

- bridge webhook intake:
  - `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/webhook/`
- merchant bridge routes and platform client:
  - `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/`
  - `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/client/platform/`
- merchant UI:
  - `product-services/shopify-bridge-service/ui/src/`
- platform Shopify domain:
  - `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/`
- platform vectorization domain:
  - `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/`
- vectorization runner:
  - `ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/vectorization/runner/`

Useful current verification scripts:

- `scripts/verify-shopify-companion.sh`
- `scripts/verify-shopify-companion-uninstall.sh`
- `scripts/run-shopify-companion-rollout.sh`

Useful current API checks:

- `GET /api/shopify/stores/{shopDomain}/vectorization`
- `POST /api/shopify/stores/{shopDomain}/vectorization/reconcile`
- `POST /api/shopify/stores/{shopDomain}/vectorization/vectorize-now`
- `GET /api/deployments/{deploymentId}/vectorization`
- `GET /api/deployments/{deploymentId}/vectorization/runs/{runId}`

## 12. Explicit Non-Goals For The Next Session

Do not expand scope unless the user asks:

- no new public marketplace plugin type for Shopify indexing triggers
- no Shopify-specific runtime fork
- no full Shopify content mirror in platform or bridge databases
- no broker-first/Kafka-first rewrite before the business semantics exist
- no removal of deployment-snapshot reindex semantics
- no merchant exposure of drafts, versions, secrets, or vectorization JSON internals
- no implementation of the indexing-trigger plan until the user explicitly requests code changes

## 13. Session Safety Notes

- this file is safe to commit
- do not copy raw secrets into this file
- if live validation is needed, use the private handoff only as an operational credential source, not as a design reference
- when debugging live Shopify Companion issues, keep platform control plane, Shopify Bridge, and storefront/theme-extension failures separated

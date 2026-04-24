# Platform Verification Resources Map

Use this guide when a future LLM session needs one place to understand where verification truth lives, which document to open next, and which code or workflow entrypoint actually owns the behavior.

This file is safe to commit.
Do not put raw secrets here.
Keep live credentials only in the private handoff companion file.

Related references:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_RESTART_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_CONTEXT_DUMP.md`
- `Final_Documentation/Development_Guides/GITHUB_ACTIONS_VERIFICATION_SUITE_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

## 1. Fast Routing

Open these resources by intent:

- Restart verification from scratch: `PLATFORM_VERIFICATION_RESTART_GUIDE.md`
- Understand the platform-owned release suite and `/verification-ops`: `PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`
- Debug a mismatch between release evidence, hosted verification, and direct repo checks: `PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
- Understand the verification philosophy and verification-layer model: `PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
- Recover the exact branch/session baseline and current Shopify verification pointers: `PLATFORM_NEXT_LLM_SESSION_CONTEXT_DUMP.md`
- Run or debug Shopify Companion verification specifically: `SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- Understand the GitHub workflow layer and manual dispatch order: `GITHUB_ACTIONS_VERIFICATION_SUITE_GUIDE.md`
- Recover live URLs, current env values, rollout notes, or live credentials: `PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

## 2. Verification Truth Model

The verification model is intentionally layered. The same deployment can look green in one lane and red in another.

- Local code verification: proves the changed code compiles, builds, and passes the smallest relevant tests.
- Release verification: proves the live runtime or connector has loaded the expected published version state.
- Hosted verification: proves the product-managed live behavior from the platform-owned runner path.
- Direct live verification: proves behavior by running the repo scripts directly against live services.
- Canonical rollout verification: proves behavior on known-good live fixtures that the platform can recreate.

Primary sources for this model:

- `PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
- `PLATFORM_VERIFICATION_RESTART_GUIDE.md`
- `PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
- `PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

## 3. LLM-Guides Inventory

| File | Role | Open when | Safety |
| --- | --- | --- | --- |
| `PLATFORM_VERIFICATION_RESTART_GUIDE.md` | Main operational runbook | You need script order, env classes, starting defaults, or recovery flow | Safe to commit |
| `PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md` | Architecture and control-plane design | You need suite keys, UI/API/backend ownership, or platform-vs-GitHub parity context | Safe to commit |
| `PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md` | Failure triage guide | You need to separate release evidence, hosted runner issues, auth issues, or live runtime issues | Safe to commit |
| `PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md` | Product and verification philosophy | You need the verification-layer definitions or the codebase standard for what counts as done | Safe to commit |
| `PLATFORM_NEXT_LLM_SESSION_CONTEXT_DUMP.md` | Branch/session baseline snapshot | You need recent Shopify baseline, code areas to inspect, or the currently useful verification scripts | Safe to commit |
| `PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md` | Private operational companion | You need live credentials, live URLs, rollout notes, or the exact current verification profile details | Private and sensitive |
| `AI_FABRIC_FRAMEWORK_PHILOSOPHY.md` | General framework philosophy | You need broader system philosophy, not the verification runbook | Safe to commit |
| `AI_FABRIC_PLATFORM_PRODUCT_PHILOSOPHY.md` | Product philosophy | You need product-direction context that may influence verification expectations | Safe to commit |
| `AI_LLM_CODE_GENERATION_GUIDE.md` | Coding workflow guidance | You need implementation guidance, not verification ops | Safe to commit |
| `CODE_REVIEW_PROMPT.md` | Review prompt/reference | You need review framing, not verification ownership | Safe to commit |

## 4. Platform Verification Entry Points

### 4.1 UI surfaces

- `/verification`: deployment-scoped verification surface for one deployment.
- `/verification-ops`: platform-admin orchestration surface for suite runs, canonical rollout health, release gate summary, and hosted verification operations.
- `Platfrom/ui/src/pages/VerificationPage.tsx`: deployment-level verification page.
- `Platfrom/ui/src/pages/VerificationOpsPage.tsx`: control-plane release suite page.
- `Platfrom/ui/src/components/HostedVerificationRunHistory.tsx`: hosted verification run history and step output rendering.
- `Platfrom/ui/src/pages/VectorizationPage.tsx`: adjacent platform-admin vectorization verification surface.
- `Platfrom/ui/src/api/platformApi.ts`: API client definitions for release verification, hosted verification, rollout inventory, release-gate summary, and suite dispatch.

### 4.2 Admin APIs

Primary release and hosted verification APIs:

- `GET /api/deployments/{deploymentId}/verification-runs`
- `POST /api/deployments/{deploymentId}/verification-runs/recheck`
- `GET /api/deployments/{deploymentId}/hosted-verifications`
- `POST /api/deployments/{deploymentId}/hosted-verifications`
- `GET /api/deployments/{deploymentId}/hosted-verification-context`
- `GET /api/deployments/verification-rollouts`
- `POST /api/deployments/verification-rollouts/recreate`
- `GET /api/verification-suites`
- `GET /api/verification-suites/runs`
- `GET /api/verification-suites/runs/{runId}`
- `GET /api/verification-suites/release-gate`
- `POST /api/verification-suites/{suiteKey}/runs`

Important suite-dispatch behavior:

- `POST /api/verification-suites/{suiteKey}/runs` returns `CONFLICT` when the same suite already has a queued or running execution.
- when that happens, do not keep redispatching; inspect `GET /api/verification-suites/runs` and poll the active run id instead.
- `GET /api/verification-suites/release-gate` is the top-level operator signal for `full-platform-release-readiness`; use it after the run settles instead of inferring readiness from partial run history alone.

Adjacent vectorization verification APIs:

- `GET /api/deployments/{deploymentId}/vectorization`
- `GET /api/deployments/{deploymentId}/vectorization/runs/{runId}`
- `GET /api/deployments/{deploymentId}/vectorization/verifications`
- `POST /api/deployments/{deploymentId}/vectorization/verifications`

### 4.3 Backend source files

Platform suite subsystem:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteCatalog.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteExecutionService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteScriptContextService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/PlatformVerificationSuiteController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/PlatformVerificationSuiteProperties.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/AsyncExecutionConfig.java`
- `Platfrom/backend/src/main/resources/db/migration/V56__platform_verification_suite_runs.sql`

Hosted and release verification services:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationExecutionService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationContextService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationLogParser.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseVerificationService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentVerificationRolloutService.java`

## 5. Current Platform-Owned Suite Inventory

Current suite keys:

- `full-platform-release-readiness`
- `canonical-release-readiness`
- `platform-admin-live-regression`
- `managed-vector-provider-verification`
- `marketplace-install-flow`
- `shopify-companion-verification`

Current ordered stages for `full-platform-release-readiness`:

1. shared inference service health
2. platform admin live regression
3. canonical rollout inventory
4. managed vector provider verification
5. marketplace install flow
6. Shopify Companion verification
7. marketplace hosted verification
8. ecommerce hosted verification
9. qdrant hosted verification
10. pinecone hosted verification
11. milvus hosted verification
12. weaviate hosted verification

Current release-gate statuses:

- `READY`
- `RUNNING`
- `FAILED`
- `STALE`
- `MISSING`

High-value live operator patterns from `2026-04-24`:

- if the Shopify verification store is intentionally blocked on order-read scope approval, dispatch the full suite with explicit `shopifyCompanionExpectations` instead of assuming `storefrontReady=true`
- if Shopify storefront query traffic suddenly starts failing with nested action errors while the bridge itself is healthy, inspect the published Shopify Companion `ai-actions.yml` before blaming runtime auth or connector transport
  - the strongest signal is: direct bridge `POST /api/admin/stores/{shopDomain}/actions/execute` succeeds when you send a real `query`, but the public shopper query path fails because the live action catalog no longer exposes `query` for `list_products` or `search_products`
- if the published artifact is wrong on the active deployment version, remember that runtime action metadata is loaded and cached at startup
  - patching the platform DB artifact alone is not enough for immediate live recovery; restart or redeploy the runtime service after the artifact is corrected
- `SHOPIFY_BRIDGE_ADMIN_API_KEY` in the Shopify verification scripts is the live `SHOPIFY_BRIDGE_SHARED_SECRET`
  - `APP_ADMIN_API_KEY` is not the correct credential for bridge admin endpoints
- if a canonical hosted deployment fails with:
  - `runtime_config_matches_expected`
  - `runtime_prompt_config_matches_expected`
  - `runtime_knowledge_sources_match_expected`
  - `runtime_shell_config_matches_expected`
  - `runtime_actions_match_expected`
  and the details show runtime artifact URLs coming from an older deployment version than the active release, treat that as stale canonical rollout state first
- for that stale canonical rollout case, refresh only the affected rollout key instead of resetting the whole fleet:
  - use `scripts/resolve-verification-rollouts.sh` with `REQUIRED_ROLLOUT_KEYS` narrowed to the failing key and `ALLOW_ROLLOUT_MUTATION=true`
  - or call `POST /api/deployments/verification-rollouts/recreate` for that key
- after repairing one canonical rollout, rerun that deployment’s hosted verification directly before rerunning the full suite
- long healthy stages exist:
  - `marketplace-install-flow` is often the slowest script stage in the full suite
  - `ecommerce-hosted-verification` also takes materially longer than the smaller vector provider hosted stages
  - do not treat those stages as stalled too early if status is still progressing cleanly

Primary ownership source:

- `PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`

Operational runbook source:

- `PLATFORM_VERIFICATION_RESTART_GUIDE.md`

## 6. Repo Script Map

Control-plane and rollout inventory:

- `scripts/resolve-verification-rollouts.sh`: resolves canonical rollout deployment ids and can optionally recreate missing or unready rollouts.
- `scripts/run-platform-state-verification-suite.sh`: sequential umbrella runner across the main live verification surfaces.

Useful live-verification pattern:

- narrow `REQUIRED_ROLLOUT_KEYS` when one canonical deployment is bad and the rest of the fleet is healthy
- example:
  - `REQUIRED_ROLLOUT_KEYS="pinecone" ALLOW_ROLLOUT_MUTATION=true bash scripts/resolve-verification-rollouts.sh`
- this was the quickest governed fix when the canonical Pinecone deployment was serving artifact URLs from an older version while the active release expected a newer version

Deployment verification wrappers and profiles:

- `scripts/run-platform-deployment-verification.sh`: fetches hosted verification context from the platform and runs the correct deployment verification profile locally.
- Supported profiles: `ecommerce`, `marketplace-runtime`, `vector`.

Useful live-verification pattern:

- if one hosted deployment stage fails inside the full suite, use this wrapper directly against that deployment id before rerunning the whole suite
- this gives the full failure payload, including release-evidence mismatches that are summarized more tersely in suite stage output

Underlying deployment verification scripts:

- `scripts/verify-ecommerce-deployment.sh`: ecommerce store, runtime, connector, and platform release-alignment verification.
- `scripts/verify-vector-deployment.sh`: vector-backed runtime, connector, indexing, provider connectivity, and optional platform alignment verification.

Platform and provider verification:

- `scripts/verify-platform-admin-regression.sh`: platform-admin auth, admin-only APIs, assignment visibility, inference-service operations, consumer resolution, and canonical rollout checks.
- `scripts/verify-managed-vector-providers.sh`: direct Pinecone, Qdrant Cloud, Zilliz Cloud, and Weaviate verification.
- `scripts/verify-marketplace-install-flow.sh`: end-to-end plugin install, publish, apply, and retrieval proof on a temporary validation deployment.

Shopify Companion verification:

- `scripts/verify-shopify-companion.sh`: non-destructive live Shopify Companion verification.
- `scripts/run-shopify-companion-rollout.sh`: platform-side bootstrap, source preflight, and go-live progression.
- `scripts/verify-shopify-companion-uninstall.sh`: destructive uninstall verification for a disposable store mapping only.

Useful live Shopify checks from the latest storefront repair:

- public shopper query:
  - `POST /api/storefront/shops/{shopDomain}/chat/query`
- bridge admin overview:
  - `GET /api/admin/overview`
- direct bridge action execution:
  - `POST /api/admin/stores/{shopDomain}/actions/execute`
- published action artifact:
  - `GET /api/deployments/{deploymentId}/versions/{versionId}/artifacts/ai-actions.yml`

Important credential distinction:

- `SHOPIFY_BRIDGE_ADMIN_API_KEY` should be the bridge shared secret currently mounted as `SHOPIFY_BRIDGE_SHARED_SECRET`
- do not substitute `APP_ADMIN_API_KEY` for bridge admin verification

Safety model:

- Read-only by default: deployment verification, hosted verification, and standard Shopify verification.
- Temporary resource creation with cleanup: managed provider verification and marketplace install-flow verification.
- Explicitly destructive: Shopify uninstall verification and any rollout mutation mode you intentionally enable.

## 7. GitHub Workflow Map

Repo-side code and build gates:

- `.github/workflows/platform-v2-verification.yml`: platform backend tests, UI build, and shell syntax checks.
- `.github/workflows/parent-verify.yml`: AI infrastructure module Maven verify lane.

Live verification workflows:

- `.github/workflows/deployment-verification.yml`: one deployment, one verification profile, using platform-hosted context resolution.
- `.github/workflows/managed-vector-provider-verification.yml`: provider control-plane verification.
- `.github/workflows/platform-state-verification-suite.yml`: sequential all-in-one live verification runner over the main platform surfaces.
- `.github/workflows/shopify-companion-verification.yml`: Shopify `verify`, `rollout`, and `uninstall_verify` modes.

Main workflow guide:

- `Final_Documentation/Development_Guides/GITHUB_ACTIONS_VERIFICATION_SUITE_GUIDE.md`

Current relationship to the platform-owned suite:

- GitHub Actions is now secondary confirmation for the release-verification estate.
- `/verification-ops` and the platform-owned suite are the primary human-operated release gate.
- Local code regression and broader CI retirement remain separate work from the platform-owned live suite.

## 8. Shopify Verification Resource Map

Primary docs:

- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_DESIGN_PARTNER_ROLLOUT_CHECKLIST.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_APP_REVIEW_GUIDE.md`

Primary scripts and workflow:

- `scripts/verify-shopify-companion.sh`
- `scripts/run-shopify-companion-rollout.sh`
- `scripts/verify-shopify-companion-uninstall.sh`
- `.github/workflows/shopify-companion-verification.yml`

Relevant code areas:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/`
- `product-services/shopify-bridge-service/ui/src/`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/`
- `ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/vectorization/runner/`

Relevant live APIs called out in the session context dump:

- `GET /api/shopify/stores/{shopDomain}/vectorization`
- `POST /api/shopify/stores/{shopDomain}/vectorization/reconcile`
- `POST /api/shopify/stores/{shopDomain}/vectorization/vectorize-now`
- `GET /api/deployments/{deploymentId}/vectorization`
- `GET /api/deployments/{deploymentId}/vectorization/runs/{runId}`

## 9. Private Handoff Boundary

The private companion file is intentionally separate because it contains sensitive operational material.

## 10. Recent Live-Proven Flow

The most useful live recovery sequence from the latest release-gate work was:

1. run the full suite with `allowControlPlaneRepair=true`
2. if Shopify is intentionally pending scope approval, include explicit `shopifyCompanionExpectations`
3. if a Shopify shopper query now fails with nested `ACTION_EXECUTED` transport errors, compare:
   - the public shopper query path
   - direct bridge `/actions/execute` with an explicit `query`
   - direct bridge `/actions/execute` with empty `params`
4. if direct bridge works only when `query` is present, inspect the published `ai-actions.yml` for missing `params` on `list_products` or `search_products`
5. repair the source migration and, for immediate live recovery on an already-published version, repair the active deployment version artifact and draft so `actions_config_json`, `actions_artifact_yaml`, and `manifest_json` are coherent
6. restart or redeploy the runtime service because the connector action catalog is cached at startup
7. rerun `scripts/verify-shopify-companion.sh` with the truthful pending-scope expectations and the correct bridge shared secret
8. only then rerun the full suite
9. if the suite later fails on one provider hosted deployment, inspect that deployment with:
   - `GET /api/deployments/{deploymentId}/hosted-verifications`
   - `bash scripts/run-platform-deployment-verification.sh`
10. if the failure is stale canonical rollout state, recreate only that rollout key
11. rerun the deployment-scoped hosted verification for that deployment
12. rerun the full suite
13. confirm `GET /api/verification-suites/release-gate` returns `READY`

Private file:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

Use it for:

- live URLs and current environment defaults
- current verification credentials and tokens
- current rollout notes
- current verification profile details
- current next-session startup procedure

High-value non-secret sections in that file:

- `4.2 Main verification and rollout concepts`
- `8. Scripts and their purposes`
- `9. GitHub workflows relevant to this session`
- `10. Canonical verification rollouts`
- `17. What the verification profiles actually check`
- `19. Recommended next-session starting procedure`

Rules:

- Do not copy secrets or tokens out into committed docs.
- Do not treat the private handoff as the architecture source of truth.
- Use it as an operational companion to the committed guides, not as a substitute for them.

## 10. Recommended Reading Order By Task

Need a full release-readiness pass:

1. `PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
2. `PLATFORM_VERIFICATION_RESTART_GUIDE.md`
3. `PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`
4. `/verification-ops`
5. the specific live script or workflow for the failing stage

Need to debug a red verification signal:

1. `PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
2. release verification APIs
3. hosted verification APIs
4. direct repo scripts
5. rollout inventory and release-gate summary

Need Shopify Companion verification:

1. `SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
2. `PLATFORM_NEXT_LLM_SESSION_CONTEXT_DUMP.md`
3. `scripts/verify-shopify-companion.sh`
4. `scripts/run-shopify-companion-rollout.sh`
5. `.github/workflows/shopify-companion-verification.yml`

Need to extend the platform verification product:

1. `PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`
2. backend suite and hosted verification services
3. `VerificationOpsPage.tsx` and `platformApi.ts`
4. `PLATFORM_VERIFICATION_RESTART_GUIDE.md`
5. this map

## 11. Maintenance Rule

Update this map when any of the following changes:

- a new suite key or suite stage is added or removed
- a new verification script or workflow is introduced
- verification ownership moves between GitHub Actions and the platform control plane
- a new verification page, API, or backend service becomes the operator-facing source of truth
- Shopify verification adds or removes a primary doc, workflow, or script
- the private handoff section layout changes enough that the section references in this map become stale

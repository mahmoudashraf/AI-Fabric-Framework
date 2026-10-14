# 010.36 Admin And Operator UI Capability Baseline

- Captured: 2026-10-14, before shared-editor extraction or customer-shell redesign.
- Source commit: `5206acd97423ee857b5a60c1fe370633bf3fd5a9`.
- Evidence level: **source inventory, not a new hosted/browser verification**.
- Scope: existing `Platfrom/ui` console, its configuration/operational controls,
  API contracts, and selected role/access policy source files.
- Frozen snapshot: [2026-10-14-admin-operator-source-baseline.json](../../../../../../../../verification-support/platform-ui-capability-baseline/2026-10-14-admin-operator-source-baseline.json).
- Snapshot SHA-256: `bdafd2c9bc28ef15d7de35d4eebb959e9938aafb76cbc49f8d415aeb9ef318b3`.
- Capture/comparison tool: [capture.mjs](../../../../../../../../verification-support/platform-ui-capability-baseline/capture.mjs).
- Governing plan: [010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md),
  especially the P0-P5 functional baseline and P6 UI-only rollback boundary.

## 1. Preservation Contract

Customer self-service adds a guided experience; it must not remove existing
authorized staff capabilities. Navigation may move or labels may improve, but
every capability below needs an explicit post-extraction destination and
verification result. Preserve configuration fields, actions, failure feedback,
permission restrictions, and operational evidence, not merely page titles.

The frozen source snapshot contains 37 routes including the root redirect,
34 navigation entries, 20 deployment-scoped paths, 1,577 source-declared
controls, 323 exported API functions/helpers, 312 functions with direct HTTP
contracts, and hashes for 58 UI/access-policy files. Counts are structural
inventory, not 1,577 distinct features or a claim that all requests succeed.
The captured source scopes have no tracked modifications relative to this
commit. Other repository work is not included or reverted.

The JSON records route/component mappings, navigation predicates, API symbol
references and line locations, field/button/menu/dialog controls and their
declared handlers/disabled states, HTTP method/path expressions, and source
hashes. Dynamic/server-supplied fields, browser state, external widgets, and
backend service authorization still require inspection and runtime tests.

Keep this snapshot immutable. After P0-P5 adds/verifies functional changes,
capture a separate dated pre-extraction baseline and record approved deltas.
Do not overwrite the original to conceal a missing capability or preserve a
known bug merely for numerical parity.

## 2. Role And Navigation Baseline

`AppShell`, `PlatformAuthController`, and `DeploymentAccessService` define
different layers. Sidebar visibility is not permission to execute an API.

- `PLATFORM_ADMIN` receives the current global staff administration flags.
  Preserve its global operations and deployment configuration capabilities.
- A normal `PLATFORM_OPERATOR` does not receive admin-only navigation,
  customer-management navigation, or the user-directory flag from the current
  session summary. Shared deployment pages remain subject to backend access.
- Operator **session** access depends on its deployment assignments and their
  viewer/operator/editor/admin rank. Operator **API-key** authentication maps
  to global deployment-editor access in the current source. Test these as
  different identities; do not infer equal rights from the role name alone.
- `/behavior-operations` is shown for Agentic Specialist Team/Smart Brain;
  `/document-knowledge` and `/integrations` depend on declared capability.
- `/customers` and `/ai-workspaces` require `canManageCustomers` for navigation;
  `/users` requires `canManageUserDirectory`.
- `/human-review` navigation is specifically customer-admin session-only.
  Preserve this distinction from staff Approvals and external Human Inbox.
- The twelve admin-only navigation entries are Inference Services, Product
  Services, Partner Privileges, Shopify Profiles/Launch/Readiness/Stores,
  Thinker Resolver, Runtime Artifacts, Verification Ops, Notifications, and
  Platform Diagnostics. Do not expose them to customers as part of extraction.
- `/activity` is reachable through workspace links rather than a sidebar item;
  `/access` redirects to deployment-scoped `/users`. Root redirects to
  `/deployments`. Preserve useful deep links and deployment context.
- Current route declarations do not themselves implement a role guard per
  page. Strengthening this in P0-P5/P6 is permitted, but must not grant new API
  rights or accidentally remove an authorized staff path. Auth-disabled
  rendering is not evidence of production-role access.

## 3. Shared Shell And Workspace

**STAFF-001: Authentication and shell.** Session/password and configured staff
API-key sign-in, loading/rejection/error states, identity/role/auth-mode display,
session refresh, and sign-out/key clearing. Customer sign-in remains session
based; this baseline does not approve shared staff credentials for customers.

**STAFF-002: Deployment workspace.** Persistent deployment selection through
`deploymentId` deep links, saved workspace preferences, unresolved/not-found
state, selected deployment/role guidance, editor-buffer versus saved-draft versus
published/live state, rollout/diagnostic/POC primary actions, and links to
runtime Swagger/auth/connector and vectorization integration status.

**STAFF-003: Shared provider operations.** Target profile listing/preflight,
profile activation/default changes and guarded Coolify placement updates;
resource inventory, actual status/logs/URLs, start/stop/restart/delete commands,
confirmation/reason/result feedback, and asynchronous refresh. Keep placement
editing staff-authorized and apply the new staging-only policy independently.

**STAFF-004: Shared feedback and verification history.** Query/mutation errors,
pending/disabled/read-only controls, empty/loading states, workspace links,
hosted-run history/results, and destructive-action confirmations. Extraction
must not replace durable server status with optimistic success.

## 4. Deployment Lifecycle And Configuration

**STAFF-005: Deployments (`/deployments`).** Template/behavior/curated-module
creation and customer/tenant binding; search/filter/selection and saved list
preferences; archive/restore/permanent deletion, approval-aware deletion and
bulk operations; tenant-binding change/migration preview; canonical rollout
selection/create/apply/cleanup/hard reset and ecommerce rollout; hosted
verification dispatch; Railway workspace orphan review/guarded cleanup; links
to releases, diagnostics, POC, prompts, access, and deployed services.

**STAFF-006: Overview (`/overview`).** Draft/published/live source-of-truth,
service configuration/navigation, integration/ownership/security summaries,
production-readiness scorecard, action/entity/routing/prompt/manifest views,
published prompt and POC-session posture, and contextual service/API links.

**STAFF-007: Activity (`/activity`).** Deployment activity/audit feed, refresh,
filters/preferences, and release/configuration/operation evidence context.

**STAFF-008: Behavior (`/behavior`).** Activation/channels and immutable behavior
contract, specialist-bundle/chain prerequisites, execution extensions, saved
behavior config, Smart Brain trigger/schedule/event-bound/delivery settings;
exact-candidate market-readiness approval/renewal/withdrawal with evidence,
expiry, composition/image identities, and review notes.

**STAFF-009: Behavior Runs (`/behavior-operations`).** Agentic durable execution
submit/status/replay/cancel and Smart Brain trigger/status/replay/cancel, input
and identity fields, operation IDs/results/failures. Capability-conditional.

**STAFF-010: Actions (`/actions`).** Actions-config editor/save/reset and typed
action/parameter/security preview; webhook targets, linked action policies,
timeouts/attempts/signing references, deliveries/status/retry; connector
upstream/auth/inbound/API-key and per-action route configuration/save/reset.

**STAFF-011: Knowledge (`/knowledge`).** Entity configuration/migration
preview/apply, vector dimensions, indexing/projection/post-index-analysis,
searchable and metadata field definitions, preprocessing/limits/priorities,
types/formats/destinations/required/PII options, marketplace ownership,
save/reset and knowledge summary. Preserve individual supported fields from
the JSON controls ledger, not only this grouped description.

**STAFF-012: Document Knowledge (`/document-knowledge`).** S3/demo-folder storage
binding and target selection, discovery/paging/register with approval/audience,
source preview/index/refresh/reconcile/removal, active/candidate revision/work
and failure evidence, retrieval proof, retention/cleanup. Customer original
objects remain untouched; capability/role conditions and format limits remain.

**STAFF-013: External Integrations (`/integrations`).** Integration/source
status and reconciliation, webhook replay, conversation-channel/handover
dispatch retry/expiry, and authorized provider/operator-inbox navigation.

**STAFF-014: Prompts (`/prompts`).** Curated baseline selection/apply, prompt
bundle and retrieval/generation/suggestions limits, draft save/reset,
published-baseline comparison, prompt revisions/create/restore, POC hot
apply/clear, and saved-versus-overlay query preview. Keep temporary session
overlays distinct from published configuration.

**STAFF-015: Providers (`/providers`).** LLM/embedding/vector strategies and
provision/storage/tenant/runtime/connector bindings; configured provider
fallback; orchestration/generation model overrides and bounds; supported
OpenAI/Anthropic/Azure/Cohere/Gemini/ONNX and vector-vendor settings;
draft validation, secret-reference readiness, vendor connectivity probes,
current-edit versus saved probe posture, and save/reset.

**STAFF-016: Security (`/security`).** Security configuration/save/reset,
governance/secret usage/audit/preflight, masked secret presence and authorized
write/clear operations, deployment overrides, provider-purpose bindings,
guardrail edits/reset. Do not lose field controls or introduce secret readback.

**STAFF-017: Revisions (`/revisions`).** Configuration diff, draft publish,
version/release history and immutable source override/compatible artifacts,
target/provisioning-plan review, approval-aware apply, reconcile/status;
bundle export preview/download and import upload/preview/draft creation with
secret/resource mappings. Existing Apply is not the completed new promotion UX.

**STAFF-018: Diagnostics (`/diagnostics`).** Preflight, activity/release and
verification evidence, provider resources/log sources/project links,
live-versus-declared source of truth/integration status, remediation options,
and guarded remediation dispatch/results. Shared provider panel is STAFF-003.

## 5. Customer, App, Access, And Approval Management

**STAFF-019: Customers (`/customers`).** Customer create/edit; tenants/create/
edit and shared vector-handle review/purge of detached history; consumers
create/edit/delete, bind/rebind, exact deployment selection, and binding
history. Preserve customer scope and active-assignment restrictions.

**STAFF-020: AI Workspaces (`/ai-workspaces`).** Installation/app list, create/
edit draft, pack/connection/profile/consumer selection, standalone/embedded
delivery, app slug/domain, branding/tools/page/presentation/media settings,
public/mixed/private authentication configuration and Supabase public settings;
activate/disable/delete-draft, domain/route verification, readiness refresh,
public app/installer/integration information. Do not duplicate this editor.

**STAFF-021: User Access (`/users`, `/access` redirect).** User create/edit/status/
role/customer binding, password reset, selected deployment assignment grant/
update/remove, access overview, and workspace navigation. Staff and scoped
customer directory permissions differ; delegated staff must remain bounded.

**STAFF-022: Approvals (`/approvals`).** Deployment/version/action approval
request creation, pending/history details, approve/reject and status. Preserve
separation from runtime Human Review and conversation handover.

**STAFF-023: Human Review (`/human-review`).** Customer-reviewer inbox/task
detail, action-proposal evidence, approve/reject with explicit decision dialog
and feedback. This route is inventoried for completeness, not as an admin
sidebar entitlement or a Basic Inbox human-chat implementation.

## 6. Marketplace And Managed Services

**STAFF-024: Marketplace (`/marketplace`).** Plugin/category/publisher/version
browse; template bootstrap; scoped install/update/enable/disable/resolve/remove,
compiler impact and entitlement edits; inference service open/reconcile/scale;
publisher create/verification posture and version submission/validate/publish/
reject. Staff publication controls must not disappear with customer navigation.

**STAFF-025: Inference Services (`/inference-services`).** Service inventory/
detail/dependents/activity/health, reconcile/restart/scale, secret rotation,
guarded recreate, and external-service links. Admin-only sidebar.

**STAFF-026: Product Services (`/product-services`).** Register a service with
source/build/target/replica bindings; inspect health/overview/dependents/activity,
reconcile/scale/restart, set immutable frontend release, rotate secret, clear
linkage/recreate, guarded decommission; provider deployments/logs and Shopify
billing/webhook/store-binding inspection/configuration. Admin-only sidebar.

**STAFF-027: Runtime Artifacts (`/runtime-artifacts`).** Immutable build
registration, source/image/behavior/extension/contract requirements,
artifact-channel promotion, and catalogue status. Not customer activation.

**STAFF-028: Partner Privileges (`/partner-privileges`).** Search partner members,
change role/status, toggle reviewed privileges, effective permission and
last-login display, and mutation errors. Admin-only, including inline controls
without a Save button.

## 7. Verification, POC, And Operations

**STAFF-029: Verification (`/verification`).** Draft validation and target
preflight, publish/apply readiness issues and corrective links, release/local/
hosted run history, canonical rollout context, selected pack/run/write-mode
settings, and platform-hosted verification dispatch/result inspection.

**STAFF-030: Verification Ops (`/verification-ops`).** Suite catalogue/runs/
release-gate evidence, source artifacts/target/canonical rollout selection,
ordered suite dispatch and explicit repair settings, canonical recreation,
deployment-only or per-rollout hosted verification, inference-health/reconcile,
run progress/stages and diagnostics. Admin-only; scoped gates do not prove the
full release suite green.

**STAFF-031: Vectorization (`/vectorization`).** Source connection and sync plan
save, test/discover/preview; bootstrap/reindex/refresh/defer/manual-confirm/
clear-override sync controls; runner-token rotation and provisioning/compatibility
status; run queue/details/pause/resume/retry/cancel; verification scenarios and
results including tenant-shared isolation. Preserve plan and operation fields.

**STAFF-032: POC (`/poc`).** Private/anonymous/authenticated auth-path selection,
runtime identity/scopes and prompt-session posture, conversation query/history/
suggestions/reset, JSON/template-sample dataset import preview/validation/run,
guarded vector clear, scenarios/create/delete, widget open/close/reset and
deployed-bundle/integration details. The real widget remains its own contract.

**STAFF-033: Platform Diagnostics (`/platform-diagnostics`).** Platform summary/
logs, shared core-service status/open/health/deploy/restart and managed-product
service reconcile/restart. Preserve privileged operational access, not merely
read-only dashboard cards.

**STAFF-034: Notifications (`/notifications`).** Deletion-operation notifications,
filters/refresh/detail context, open-deployment links, and explicit retrigger
of running cleanup. Backend lifecycle/ownership guards remain mandatory.

**STAFF-035: Thinker Resolver (`/thinker-resolver`).** Deployment readiness/
controls, capture/inspect/export sessions, support-escalation kill switch,
proposal creation, policy decisions/dry run/execute and execution history.
Current source capability, not an endorsement of an unverified broad AI repair.

## 8. Shopify Administration

**STAFF-036: Shopify Profiles (`/shopify-package-profiles`).** Profile list/
options, create/duplicate/edit/reset/save, activate/disable and links to store,
Marketplace, inference, and verification surfaces.

**STAFF-037: Shopify Launch (`/shopify-launch-readiness`).** Profile/readiness
audit/full-gate/suite evidence and refresh. Preserve modeled launch blockers.

**STAFF-038: Shopify Readiness (`/shopify-readiness-audit`).** Readiness-audit
state/result display and explicit suite dispatch/progress. Admin-only.

**STAFF-039: Shopify Stores (`/shopify-stores`).** Store registration/config,
customer-account settings, source preflight/live or reviewed override,
bootstrap/package reconcile/publish-apply, platform binding/provisioning/
governed-action inspection, billing/partner access/revoke and mapping delete;
vectorization reconciliation/index/reindex selected families, automatic failure
retry, live-update policy, and event replay. Full query/session/action regression
remains required separately from this source snapshot.

## 9. Comparison And Extraction Acceptance

For each `STAFF-001` through `STAFF-039`, the extraction evidence must record:
original route/source, new route/shared component, role/auth-mode/deployment
assignment, supported field/action mapping, positive/negative browser checks,
source/artifact/run identity, and any approved intentional change. Record
`PRESERVED`, `MOVED_EQUIVALENT`, `ADDED`, or `APPROVED_REMOVAL`; unresolved
`MISSING` or `NOT_VERIFIED` blocks staff-parity closure. Counts alone cannot
approve equivalence. A hidden customer control must stay available to its
authorized staff identity.

Run the static comparison from the repository root:

```sh
node verification-support/platform-ui-capability-baseline/capture.mjs --compare verification-support/platform-ui-capability-baseline/2026-10-14-admin-operator-source-baseline.json
node --test verification-support/platform-ui-capability-baseline/capture.test.mjs
```

To record P5's newly verified functional baseline, use `--output` with a new
filename. The tool refuses to overwrite an existing snapshot. Comparison flags
route/API/reference/HTTP-contract/navigation/scope/source changes for review;
component extraction legitimately changes hashes, so the report is not a
semantic or browser parity verdict. API helpers with no UI references are not
silently promoted into implemented UI capabilities.

Before P6, complete the selected staff baseline flows with real admin and
assigned operator sessions, relevant API-key checks, authorized destructive
fixtures, and template-conditional pages. Afterwards repeat role-aware browser
flows, draft/save/publish/apply and error/retry, shared-editor data persistence,
deep links, diagnostics and negative permission checks. Prove rollback to the
compatible pre-extraction console without backend or data rollback. Do not
execute cleanup or write operations on existing customers merely to populate
this register.

Current result: source snapshot/comparison and the three capture-tool tests
pass; all future extraction parity and live baseline checks remain pending.
No UI feature, configuration, deployment, or customer resource changed here.

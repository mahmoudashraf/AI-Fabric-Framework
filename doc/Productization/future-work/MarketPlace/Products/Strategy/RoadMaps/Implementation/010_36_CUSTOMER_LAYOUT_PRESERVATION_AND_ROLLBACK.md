# 010.36 Customer Layout Preservation And Rollback

Date: 2026-10-23. Governing plan: [010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md).

## Functional Baseline

The staging-to-production customer integration journey passed before this layout
series. The same Engage app serves production release `rel-8c7930cc` on AI Fabric
0.8.41. See the execution record for explicit answer-quality findings and deferred
retirement/fencing work. This is not a claim that those follow-ups are closed.

The separate functional UI commit is
`d3f7f9a90f7a629869871181869dd9e86ccb5a16`. CI39894435610 succeeded, UI only;
backend publication was skipped. Its immutable UI image is
`sha256:bc181aa01ec6752f19ac68b7baccfbf0a89ab0bce85157fc064d62539f80b347`.
Both servers verified that image without changing saved/effective configuration,
domains, backend or customer runtime. Actual production CUSTOMER_ADMIN browser
verification passed both readiness refresh and storage placement refresh, with
the app assignment, allocation identity and expiry unchanged.

Evidence: `/private/tmp/01044-readiness-ui-rollout-20261023/` and
`/private/tmp/01044-readiness-ui-live-r2-20261023/report.actual.json`.
The first live harness's expired-proof diagnostic read is retained separately;
it was not a failed UI command or a reason to weaken admission checks.

## Frozen Inventory

The original 14 October snapshot remains immutable. A separate committed-source
snapshot immediately before the layout series is
[2026-10-23-pre-customer-layout.json](../../../../../../../../verification-support/platform-ui-capability-baseline/2026-10-23-pre-customer-layout.json).
It was generated from exact committed Git objects, excluding pending layout and
unrelated working-tree changes. SHA-256:
`378a4ba43a6b42be5000947faf1cd614852613e0ea365573d43848c9f2cc37c7`.

It records37 routes,34 navigation entries,70 source files,1,685 declared controls,
374 API exports and354 API exports with HTTP contracts. Counts are source
inventory, not independent successful mutations. The pending layout comparison
removes zero routes/API exports/references or HTTP contracts and does not change
deployment-scoped paths. The unrelated dirty Runtime Artifacts default is not
part of the layout publication.

## Capability Destinations

All original STAFF IDs retain their existing staff destinations. No API or
configuration editor is copied into a second portal. The customer shell composes
the existing role-aware pages and customer components.

- STAFF-001: authentication provider, App and shared AppShell; staff auth unchanged.
- STAFF-002: DeploymentWorkspaceContext and DeploymentWorkspaceHeader; staff header retained.
- STAFF-003: DeploymentProviderOperationsPanel and its existing callers.
- STAFF-004: existing query/mutation feedback and verification-history controls.
- STAFF-005: DeploymentsPage, `/deployments`; staff component unchanged.
- STAFF-006: OverviewPage, `/overview`.
- STAFF-007: ActivityPage, `/activity`.
- STAFF-008: BehaviorPage, `/behavior`.
- STAFF-009: BehaviorOperationsPage, `/behavior-operations`; behavior condition retained.
- STAFF-010: ActionsPage, `/actions`.
- STAFF-011: KnowledgePage, `/knowledge`.
- STAFF-012: DocumentKnowledgePage, `/document-knowledge`; same storage/source editor.
- STAFF-013: IntegrationsPage, `/integrations`; handover-only workspaces may also expose it.
- STAFF-014: PromptsPage, `/prompts`.
- STAFF-015: ProvidersPage, `/providers`.
- STAFF-016: SecurityPage, `/security`.
- STAFF-017: RevisionsPage, `/revisions`.
- STAFF-018: DiagnosticsPage, `/diagnostics`.
- STAFF-019: CustomersPage, `/customers`.
- STAFF-020: AIWorkspacesPage, `/ai-workspaces`; same app/configuration editor.
- STAFF-021: UsersPage and AccessPage redirect, `/users` and `/access`.
- STAFF-022: ApprovalsPage, `/approvals`.
- STAFF-023: HumanReviewPage, `/human-review`; original customer-session condition retained.
- STAFF-024: MarketplacePage, `/marketplace`.
- STAFF-025: InferenceServicesPage, `/inference-services`.
- STAFF-026: ProductServicesPage, `/product-services`.
- STAFF-027: RuntimeArtifactsPage, `/runtime-artifacts`; no layout change.
- STAFF-028: PartnerPrivilegesPage, `/partner-privileges`.
- STAFF-029: VerificationPage, `/verification`.
- STAFF-030: VerificationOpsPage, `/verification-ops`.
- STAFF-031: VectorizationPage, `/vectorization`.
- STAFF-032: PocPage, `/poc`.
- STAFF-033: PlatformDiagnosticsPage, `/platform-diagnostics`.
- STAFF-034: PlatformNotificationsPage inside NotificationsPage, `/notifications`;
  original staff controls/permission checks retained, separate customer notice view.
- STAFF-035: ThinkerResolverPage, `/thinker-resolver`.
- STAFF-036: ShopifyPackageProfilesPage, `/shopify-package-profiles`.
- STAFF-037: ShopifyLaunchReadinessPage, `/shopify-launch-readiness`.
- STAFF-038: ShopifyReadinessAuditPage, `/shopify-readiness-audit`.
- STAFF-039: ShopifyStoresPage, `/shopify-stores`.

The role-aware shared-shell browser gate passes33 admin entries,18 operator
entries and seven customer entries, including mobile fit, keyboard menu opening,
tab navigation, Escape and focus restoration. Customer route/admission flow and
18 selection/credential-scope scenarios pass. These local fixtures do not claim
a fresh execution of every destructive staff operation. Existing backend
authorization remains authoritative.

## UI-Only Rollback

Keep the functional commit/image above and the frozen inventory independently
of the layout commit. The layout series must contain only UI composition,
navigation, responsive presentation and their tests. No backend, runtime,
template, secret, database, source binding or consumer activation change belongs
in it.

To reject the new layout, redeploy the previous immutable UI image through the
existing Coolify UI applications, retaining their runtime configuration and
domains. Do not roll back the backend or customer runtime, reapply a template,
reindex data, recreate an app, or move the consumer assignment. Verify UI health,
runtime configuration and the same production app/release/assignment afterward.
The previous UI was verified against this exact production deployment immediately
before the layout series. A later live post-layout rollback rehearsal is not
claimed by this source/compatibility evidence.

## Final Hosted Verification

23 October04:15UTC: final layout source
`e12d9e571761297ce7e509a35de15c1daff29f4d`, immutable image
`sha256:ea100732145a3c60d26c026b0d0a2172a963e0ff6f22a184943a0f45cf87bb64`,
is VERIFIED on staging and production. CI39898095450 succeeded/UI only.
Production job `nnrzgh3nsedqvhvgm8fzpt4p`; staging job
`limu783qk4cmqgg4rdx1akv8`. Runtime config, domains and app assignment are
preserved. No backend, customer runtime, template or data mutation was performed.

The actual production customer browser passes all seven views at desktop1440
and mobile390x844, staff-route denial, explicit placement/readiness refresh,
keyboard focus and logout. Optional governed Action Reviews is NOT enabled by
this pilot's active extensions; its unavailable400 remains visible and cannot
render as a successful empty inbox. This does not affect verified conversation
handover/Basic Inbox. Initial stale409 reads and earlier failed mobile runs are
retained, not hidden. Final committed-source parity preserves374 API contracts,
all staff paths and39 mapped capability destinations.

Receipt hashes and exact evidence scope are preserved in the
[sanitized acceptance record](../../../../../../../../verification-support/productization-01036/customer-journey-acceptance-20261023.json).
Hosted verification is complete for this bounded scope. User visual/workflow
approval and a post-layout live rollback rehearsal are still open. The previous
functional UI was verified against this same production app before layout;
that compatibility result is not a later rollback rehearsal or certification of
every destructive staff operation.

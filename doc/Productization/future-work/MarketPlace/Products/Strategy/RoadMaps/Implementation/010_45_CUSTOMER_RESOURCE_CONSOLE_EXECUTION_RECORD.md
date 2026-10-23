# 010.45 Customer Resource Console Execution Record

Date: 2026-10-23. Plan: [010.45](010_45_CUSTOMER_RESOURCE_CONSOLE_PROGRESSIVE_UI_PLAN.md).

## Current Product-Milestone Execution

Owner requested phased, tracked implementation. Existing source/live evidence
below is retained; it does not close these product-composition milestones.

- M1 SOURCE QUALIFIED / PUBLICATION PENDING: product-focused Home over existing
  owned offers/apps/resources; model, actual-component, scope and staff checks pass.
- M2 PENDING: concise dealership setup using existing simulator/storage controls.
- M3 REUSE / QUALIFICATION PENDING: product-linked Engage setup and supported edits.
- M4 PENDING: fresh new-UI staging-to-production customer journey.
- M5 PENDING: product-scoped day-to-day tasks using existing components.
- M6 IN PROGRESS (bounded prerequisite): separate operational-read access from
  expired placement admission proofs; preserve all write/ownership protections.
- M7 DEFERRED: Shopify product onboarding after dealership/Engage acceptance.

Each phase must record changes, actual checks, source/publication status and a
review link. No generic management/recovery/draining workstreams are authorized.

### M1 - Product Home Source Qualification

2026-10-23 18:48 UTC: added product-led Home using explicit reviewed experience-pack
codes, not deployment names or keyword matching. Multiple offers remain explicit
choices; no latest-version inference. Existing active resources link to the same
inspectors/apps. Draft/disabled/unused resources remain inspectable in a separate
history section, while active app issues stay visible. No new entity, permissions,
request or configuration service was introduced. Staff shell/theme unchanged.

Checks: production build;6 model tests;18 customer-scope regressions;33 admin/18
operator parity; actual-component suite with19 scenarios and37 viewport acceptance
screenshots plus M1 light/dark/mobile captures. Zero browser errors or escaped
requests. The first mobile assertion measured an inspector's closing animation;
it now waits for the panel to be hidden and the resize transition to settle.
A second assertion counted before async catalogue loading; it now waits for the
actual heading. A scope-suite timeout under concurrent local load passed on rerun.
No product guard was weakened. Original failed logs remain in `/private/tmp/01045-m1-*`.

Source report: `verification-support/customer-console-01045/product-m1/`.
Review: https://normally-solar-educated-stickers.trycloudflare.com/phase-m1/index.html
(offline fixture transport, not production acceptance). Commit/publication and
live M1 results follow in a separate entry; M2-M6 are not closed by these tests.

M6 prerequisite source is also ready for main review: bounded operational GETs
retain current ownership/entitlement/reviewed target checks without requiring a
fresh admission lease. All writes/source commands still require fresh evidence.
207 focused backend tests pass; no backend publication or live effect yet.

## Current Scope

Customer-only implementation. Admin/operator `AppShell.tsx` and
`platformTheme.ts` remain byte-identical to the pre-U1 baseline. No backend,
framework, authentication relaxation or customer business-system provision is
part of these source phases. UI-only staging publication follows local
qualification; hosted and production acceptance remain separate gates. Review
links use an explicitly labelled offline fixture transport, not live customer
data or production proof.

## Owner Clarification - Same Account, Advanced Presentation

2026-10-23: the owner wants progressively deeper detail/configuration and
customer-supported capabilities for the SAME account, not a new customer role
or a move into the internal staff console. Per-resource Advanced is preferred
over a global expansion that crowds every page. This is now explicit in010.45
sections2/4 and U3/U6 acceptance. No role redesign is authorized by this request.

Follow-up implemented and locally qualified at16:45 UTC: resource inspectors
provide Overview / Configuration / Advanced, expanded shared editors provide
Configuration / Advanced, and the nested app form uses the same section state.
All views remain mounted so depth/theme changes preserve inputs. The guided
editor no longer offers a navigation escape labelled All setup controls.
View preferences contain only a validated display section, never authority.
Critical resource notices also appear inside the app modal. Customer app
Disable/Delete requires explicit confirmation and current scope/version;
staff default presentation is preserved.

The SAME-session dealership and Shopify tests verify retained drafts, disabled
saves without reviewed-profile authority, cancelled destructive actions with no
command, and invalid local preferences without privilege changes. Document
credentials remain masked, unsaved and absent from URL/local preferences across
depth changes. Retrieval proof stays in common configuration; detailed manifests,
retention and integration authority/history are Advanced. Shared errors stay
visible outside hidden sections. These are UI/source checks, NOT live acceptance.

Latest passing report: `verification-support/customer-console-01045/advanced-view/`.
TypeScript/production build, all18 customer scope regressions and33 admin/18
operator staff navigation parity pass. The actual-component suite includes37
viewport/theme screenshots plus Advanced snapshots. Initial run failed only on
a selector matching both visible and intentionally mounted hidden support text;
the assertion was scoped to the visible panel, without changing product guards.
Original failure remains `/private/tmp/01045-advanced-check.log`.

### Pre-Publication Review Corrections

First scoped source `f39fe0caa` is committed/pushed; UI-only CI39990587287 passed,
backend skipped. That image was NOT deployed: focused review found three P2 UI
scope/presentation defects. The follow-up rejects an editor URL whose inspected
resource and deployment differ, locks an embedded document/integration release
to the guarded outer selection, and shows handover delivery/callback failures
outside all section tabs. Staff release selection remains unchanged. New tests
use two owned deployments, disabled inner selectors and failed delivery evidence.

The corrected actual-component suite passed17:03 UTC with zero errors/escaped
requests and37 viewport screenshots;18 scope regressions, staff33/18 parity and
full UI build also pass. Its first run had a test-only exact-name mismatch because
MUI includes the selected release in the combobox name; the assertion now matches
the label prefix and still requires the selector to be disabled. Original result
retained at `/private/tmp/01045-reviewed-browser.log`. Corrected source publication
and actual hosted checks follow; no resource command or native deployment has
occurred at this checkpoint.

## U0 - Complete Design Artifacts

Product-generalization and Shopify private-adapter requirements are explicit.
Light/Dark/System is in the customer-only contract. Updated prototype supports
theme choice and the gallery includes three dark layouts (26 screenshots total).
Ten desktop page layouts and basic mobile preview checks pass with zero HTTP
requests/errors. The initial layout direction was accepted; remaining visual
review does not imply approval of new capability/backend authority.

## U1 - Source And Offline Preview Passed

Added `CustomerConsoleShell.tsx` and `CustomerConsoleTheme.tsx`. The actual App
selects the new shell only for CUSTOMER_ADMIN after existing session guards.
The staff rendering path, routes, original shell and theme remain unchanged.
Device-local Light/Dark/System follows system changes, preserves unsaved inputs
and does not change deployment/authentication/configuration. Existing editor
models and APIs are not forked.

Focused TypeScript compilation passes. Actual shell tests pass dealership and
Shopify fixtures, themes, preference reload/system changes, modal theming, basic
mobile and non-session/staff denial. Zero escaped/API requests. Staff navigation
fixture passes33 admin/18 operator entries; original seven-entry AppShell customer
baseline also passes. These are source/local checks, not all staff mutations.

Retained U1 review build: `designs/010_45_customer_console/phase-u1/index.html`.
Its fixture resource content is not the U2 resource implementation. U1 reports
under `/private/tmp/01045-u1-*`; public mobile proof is under
`/private/tmp/01045-prototype-phone-share/u1-public-proof.json`.

Baseline SHA256: staff shell
`8dd2be7615bfc7b7e888c4b429b2f4044e99c96e7a0d1281557be1ad8753f638`;
staff theme `e2a7f631324e78d424bd022a19f5631f9cdece2a52f573d36632dbe62d7637a5`.

## U2 - Source And Offline Transport Checks Passed

Added typed product-neutral resource adapters, `CustomerResourceSurface.tsx` and
connected `CustomerResourcesPage.tsx`. New customer Home, Deployments and Engage
views use existing customer portfolio/catalogue/app reads. Release reads occur
only for an inspected deployment known in that customer's portfolio. No mutation
is coupled to resource selection. App fields are projected explicitly; no secret
configuration is displayed. Private Shopify connection mode remains distinct.

Warnings precede details. Configuration state is not mislabelled runtime health.
Resource/target-release selection is exact; unknown IDs do not trigger another
resource selection or foreign release request. Failed app/release reads are
errors, not empty-success states. Search is ordinary UI list filtering, not an
AI semantic decision. Existing management remains reachable through explicit
`view=manage`, pending U3 panel integration/U4 guided presentation.

TypeScript and offline actual-reader checks pass: dealership + Shopify, warning
and private-mode preservation, GET-only transport, reload/Back, basic mobile,
first setup, failed read, unknown resource, failed release, exact-release denial.
Original first test failed because it sought a button rather than the actual
RouterLink; fixture selector corrected without changing assertions or guards.
Original failed report remains `/private/tmp/01045-u2-1792758892063/report.actual.json`.

Review build: `designs/010_45_customer_console/phase-u2/index.html`.
Reports `/private/tmp/01045-u2-*`; build logs `/private/tmp/01045-u2-build*.log`.
The fixture fetch implementation exists ONLY under `customer-console-preview`;
it is not imported by the production UI and rejects non-GET operations.

## U3 - Source And Offline Configuration Checks Passed

Expanded resource panels reuse the existing Engage, deployment, Document
Knowledge and Integrations editors. Knowledge/Connections add safe document,
binding and source summaries with explicit owned deployment/target-release
selection. Configuration presence is not presented as runtime health. Missing
reads and unknown IDs remain visible errors; no alternative target is selected.

Customer-only data routing supports a proper navigation blocker while a
configuration panel is open. Back, cross-page Back and panel close require an
explicit discard or keep-editing decision. Refresh uses the native unsaved-change
warning. Theme changes preserve the mounted form. Staff retains BrowserRouter,
AppShell and platformTheme. Existing full management remains available through
`view=manage`; the forms and command/secret policies are not duplicated.

TypeScript passes. Actual U3 component/reader fixture checks pass both product
contracts, shared app editor draft retention, exact Knowledge/Connections scope,
masked empty secret input, denied save without authority, failed document read,
and foreign-scope denial. All 18 existing customer scope regressions pass with
updated navigation selectors and explicit shared-management links, including
one-time secret invalidation and delayed completions across actor, deployment,
release, target and ABA transitions. The first unknown-scope assertion expected
the inner page alert; the existing outer unresolved-scope guard correctly caught
it first. Assertion now checks that guard without removing the no-foreign-fetch
requirement. Original failed report remains in `/private/tmp/01045-u3-1792761187218`.

Review build: `designs/010_45_customer_console/phase-u3/index.html`.
Passing report `/private/tmp/01045-u3-1792761276291/report.actual.json`;
scope log `/private/tmp/01045-u3-scope-current.log`. This is not hosted customer
acceptance; the offline fixture rejects every mutation. A reviewed production
customer journey/publication remains U6 work.

## U4 - Guided Source Ready; Hosted Journey Gate Pending

The existing customer deployment console now has an optional guided presentation:
Template, Configure, Customer staging, Production and Staging retention. Resource
creation links preserve the exact entitled offer. Configuration fields/commands,
authorization, idempotency, candidate-bound readiness and explicit activation are
the original shared implementation. The complete management view remains intact.
Setup stages are URL presentation state, not a second persisted workflow engine.

Only the current stage is visible, while forms stay mounted across stage changes.
Back/sidebar exit has explicit discard protection. Navigation starts no publish,
apply, replay or activation command. The production stage shows the server's
candidate-bound blockers and keeps activation disabled when not ready. Required
binding-slot summaries are not fabricated readiness: slots without a reusable
customer editor are labelled reviewed-offer bindings rather than given invented
configuration authority.

Offline U4 tests pass shared regression cases plus stage visibility, retained
inputs, exit protection, blocked activation and no deployment command from stage
navigation. Review: `designs/010_45_customer_console/phase-u4/index.html`.
Report `/private/tmp/01045-u4-check.log` (2026-10-23T13:25:20Z). This is NOT the
required hosted staging-to-production acceptance. That remains open for U6;
no live resource or assignment has been changed by this UI work.

## U5 - Supported Capability Source And Offline Checks Passed

Added a separate customer Human support view using the existing target-scoped
handover projection and shared operator controls. Sidebar visibility uses the
selected authorized workspace's installed handover flag; target selection and
server authorization remain necessary. Direct links to uninstalled support show
an unavailable state, disable management and do not request handover operations.
Read-only summaries use existing delivery evidence, not fabricated Inbox metrics.
Inbox URLs are constrained to safe HTTPS links. Action reviews remain a distinct
customer-owned deployment scope and reuse the existing decision screen, keyed on
that scope to clear prior selection/decision state.

Customer Activity still exposes the existing staging lifecycle notices, not a
promised complete audit stream. Fixed its failed-read state so it cannot also
display a green empty-success notice. Account/team retains existing scoped
controls. Behaviour pages were not exposed or qualified in the new customer UI:
no navigation is offered and direct links deny without requesting those pages'
APIs. The later code-inspection correction below confirms customer-capable APIs
already exist; this omission is not evidence of a blanket missing contract.

U5 fixtures pass installed/uninstalled Human support, failed support reads,
denied review reads, failed notices without false success, and denied staff
Behaviour navigation, alongside U2/U3/U4 checks. Original first fixture used an
incorrect handover route; fixed ONLY the fixture to match the existing API's
`/integrations/conversation-handover` route. No production endpoint was changed.
Passing log `/private/tmp/01045-u5-check2.log` (2026-10-23T13:35:26Z).
Review build: `designs/010_45_customer_console/phase-u5/index.html`.

## U6 - Local Acceptance Checkpoint Before Hosted Publication

Full production UI build and TypeScript pass. The current Vite bundle warning
is about the existing single-chunk application size (about 2.13 MB uncompressed,
536 kB gzip), not a build failure. No unrelated code-splitting refactor was added.
Actual customer component tests cover nine pages at1440x900 and1280x800 in both
themes plus a basic390px long-name resource case:37 acceptance screenshots,
alongside shared-editor, scope and failure-state scenarios. Zero page errors or
escaped network calls. First setup has four primary navigation entries; owned
resources or read errors reveal the operational navigation, not a local unlock.
Account/team presents safe current-session details and reuses the existing team
editor only when its server-projected permission allows it. No role is inferred
from the page label. Customer action approvals are titled Action reviews.

Staff shell/theme hashes remain equal to the baseline. Staff parity fixture
passes33 admin/18 operator entries, original seven-entry legacy customer shell,
mobile navigation and focus return. All18 customer scope/secret regressions pass.
These are not a rerun of every staff mutation or a formal accessibility audit.

Durable sanitized reports are in `verification-support/customer-console-01045/`:
`source-ui-acceptance.actual.json` (2026-10-23T13:52:45Z),
`staff-parity.actual.json`, `customer-scope.actual.log` and `ui-build.actual.log`.
Screenshots from the final run remain `/private/tmp/01045-u6-1792763540609/`.
The working-tree build is a compile check, NOT a publishable clean release:
unrelated owner changes such as RuntimeArtifactsPage's default must be excluded
from a scoped UI publication. They were not reverted or staged.

Latest review build: `designs/010_45_customer_console/phase-u6/index.html`.
The gallery links every retained phase. At this checkpoint the U6 HOSTED gate was OPEN: owner visual
review, clean scoped source publication, UI-only staging rollout, actual customer
journey, production rollout and independent rollback verification. No current
production/staging UI, backend, runtime, customer assignment or data had changed.
No framework release/test matrix is required for this visual-only work.

## UI-Only Publication And Live Acceptance - 17:21 UTC

The owner explicitly requested commit, deployment and live verification. Source
`f39fe0caa` and corrective `430b875a73502e0829372c7ba89916489e7f0be7` are committed
and pushed on Platform-V11. CI39992178465 passed/UI-only/backend skipped. The
corrected clean Git-source build passes, excluding the owner's unrelated
RuntimeArtifactsPage edit. The FIRST image was never deployed.

Both UI apps now serve image
`sha256:b12869b659112baa3eeb7b418c1f382aa6bc83823ba35a9fb26edadb1f68f9a5`,
with native revision/digest and healthy container checks. Staging job
`vt7fjo6kcfku1xqla3qagfpn`, production job `k130t9llayxp0ju90pkv5068`, both
finished. Saved/effective environment, domains and runtime API configuration
are preserved. No backend/framework/customer-runtime deployment occurred.

Live URLs:
- Production customer/staff console: https://console.loomai.pro/home
- Staging console: https://loomai-platform-ui.46.224.145.148.sslip.io

Actual staging staff API-key sign-in, existing navigation and logout pass with
zero API/browser errors. The live account exposes30 entries because it has no
selected capability-enabled workspace; the first harness incorrectly assumed
the full33-entry fixture. Corrected harness waits for loaded navigation and
checks critical existing destinations/role separation. No product change was
made to satisfy that assertion; original failed run is retained privately.

The real production CUSTOMER_ADMIN password session passes nine pages in both
themes, same-account retained draft/depth/theme/discard checks, locked embedded
release, basic390px navigation, staff-route denial and logout. Readbacks confirm
all3 ACTIVE policy documents,1 integration source, and enabled handover with a
reachable connector. The same production app/release/revision/URL/status remains
unchanged. No business action, handover conversation, admission reservation,
binding write, production switch or deployment command was replayed.

Final result is **PASS_WITH_OPTIONAL_ACTION_REVIEW_UNAVAILABLE**, not an
unqualified full-platform green. The active template includes conversation
handover, not HUMAN_REVIEW; that separate action-approval API truthfully returns
400. The UI retains its unavailable/error state rather than an empty-success
inbox. Human-support operational reads themselves pass.

### Existing Placement-Freshness Caveat

Initial live rendering checks passed, but operational source/integration/handover
reads returned409 `PLACEMENT_EVIDENCE_STALE`. This was not ignored in acceptance.
Using the existing **Verify storage target placement** UI, the owner session
explicitly refreshed the SAME production and staging target checks (two200
responses). App assignment stayed unchanged, no infrastructure was created.
The final strengthened run requires successful operational reads and permits
only the documented optional action-review400. It passed17:21:21 UTC with zero
browser errors or blocked resource commands.

The placement checks currently last15 minutes (this refresh expires17:34 UTC).
Expiry can therefore recur on read-only customer pages; this is an EXISTING
backend authorization/usability follow-up, not solved by this visual release.
Review separating ownership-checked operational reads from short-lived placement
admission proofs, or a clearly authorized renewal contract. Do not silently
auto-renew, extend policies or weaken guards in the UI. Original stale-read
evidence and the explicit refresh are retained in the durable hosted record.
The diagnostic retirement GET without its required target selector returned401;
that incomplete diagnostic is not treated as a session regression or acceptance
proof. The final normal target-scoped UI reads pass.

Sanitized live reports: `verification-support/customer-console-01045/hosted-20261023/`.
Private screenshots/session-independent helper journals remain under
`/private/tmp/01045-hosted-production-1792776040807/` and
`/private/tmp/01045-ui-rollout-20261023/`; no passwords/cookies were committed.
Staff AppShell/theme SHA256 still match the pre-extraction baseline. UI-only
rollback remains the prior live `e12d9e571761297ce7e509a35de15c1daff29f4d` image;
no post-layout live rollback rehearsal was performed in this request.

## Product-Oriented Scope Amendment

The owner wants a small product-focused console, not another generic management
surface comparable in scope to the earlier recovery/draining work. Section6 of
the canonical010.45 plan now tracks M1-M7: product Home, dealership setup, Engage
management, test/go-live, daily operations, bounded release closure, then Shopify.
Existing U0-U6 source work remains the reusable foundation and evidence; the new
milestones are not all newly completed just because those components exist.

Already delivered: resource Home/inspectors/themes, reviewed dealership setup
and simulator integration, S3 policy indexing, Engage editors/delivery, guided
staging/promotion controls and Human support. Remaining work is focused product
composition, required edit/journey verification and placement-read usability.
No app/backend change or fresh live run is performed by this plan amendment.

Correction to the earlier blanket contract-gap claim: Behaviour operations
already accept CUSTOMER_ADMIN with deployment access checks; the existing
deployment activity API checks ownership too. Their missing new-console exposure
is not proof of missing backend APIs. Full Behaviour management, a unified audit
centre and universal integration editors are deferred as PRODUCT SCOPE, not
declared backend blockers. Inspect any required editor/operation individually.
Only concrete blockers to M1-M6 justify a narrow addition; do not reopen broad
configuration, recovery, draining or infrastructure workstreams.

## Remaining Acceptance And Follow-Ups

- U4 HOSTED GATE OPEN: verify the actual supported customer staging/go-live
  journey through the published UI without substituting fixture results.
- U6 LIVE UI CHECKS/PUBLICATION PASSED: owner visual acceptance and a live
  post-layout UI rollback rehearsal remain open. Broader end-to-end actions,
  sign-in/handover conversation and a new staging-to-production promotion were
  not replayed for this UI-only request; earlier010.36 evidence remains distinct.
- Existing15-minute placement-freshness coupling on operational reads needs a
  separate backend/UX decision. Do not call this release full market readiness.

## Deferred Product Scope, Not Blanket Missing-API Claims

- Generic customer Behaviour UI: existing customer-capable APIs can be reused if
  a concrete product needs a bounded operation; internal readiness approvals stay
  staff-only. No full Behaviour management centre is needed for M1-M6.
- Unified customer audit/activity aggregation: current lifecycle notices and the
  existing deployment activity reader are not a complete cross-resource audit
  centre; that expansion is not required for this product release.
- Offer binding slots without an existing customer editor: do not invent a
  self-service secret/profile form from identifiers alone. Existing reviewed
  bindings and backend readiness stay authoritative. New integration onboarding
  UX must preserve profile approval and exact target/version authorization. Reuse
  existing editors first and add only the selected product's required contribution.

Phone review uses a temporary public, unauthenticated Cloudflare tunnel from
the owner's Mac. It serves only allowlisted design/build assets. The Mac must
stay online/awake. No private repository/auth path or live API is exposed.

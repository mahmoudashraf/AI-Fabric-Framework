# 010.45 Customer Resource Console Execution Record

Date: 2026-10-23. Plan: [010.45](010_45_CUSTOMER_RESOURCE_CONSOLE_PROGRESSIVE_UI_PLAN.md).

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
controls. Behaviour management has no safe customer contract: no navigation is
offered, direct links deny without staff API reads, and the missing projection
is deferred rather than granting authority through a UI change.

U5 fixtures pass installed/uninstalled Human support, failed support reads,
denied review reads, failed notices without false success, and denied staff
Behaviour navigation, alongside U2/U3/U4 checks. Original first fixture used an
incorrect handover route; fixed ONLY the fixture to match the existing API's
`/integrations/conversation-handover` route. No production endpoint was changed.
Passing log `/private/tmp/01045-u5-check2.log` (2026-10-23T13:35:26Z).
Review build: `designs/010_45_customer_console/phase-u5/index.html`.

## U6 - Local Acceptance Passed; Hosted Publication Open

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
The gallery links every retained phase. U6 HOSTED gate remains OPEN: owner visual
review, clean scoped source publication, UI-only staging rollout, actual customer
journey, production rollout and independent rollback verification. No current
production/staging UI, backend, runtime, customer assignment or data changed.
No framework release/test matrix is required for this visual-only work.

## Remaining Phases

- U4 HOSTED GATE OPEN: verify the actual supported customer staging/go-live
  journey through the published UI without substituting fixture results.
- U6 OPEN: desktop/theme/real customer acceptance, independent UI rollback and
  reviewed staging/production publication. No live redesign is claimed yet.

## Explicitly Deferred Capability Contracts

- Customer Behaviour definition/operations projection and mutations: staff-only
  APIs are not reused as customer authority.
- Unified customer audit/activity aggregation: current lifecycle notices are
  retained and labelled accurately.
- Offer binding slots without an existing customer editor: do not invent a
  self-service secret/profile form from identifiers alone. Existing reviewed
  bindings and backend readiness stay authoritative. New integration onboarding
  UX must preserve profile approval and exact target/version authorization.

Phone review uses a temporary public, unauthenticated Cloudflare tunnel from
the owner's Mac. It serves only allowlisted design/build assets. The Mac must
stay online/awake. No private repository/auth path or live API is exposed.

# 010.45 Progressive Customer Resource Console UI Plan

Date: 2026-10-23. Status: SUPPORTED CUSTOMER UI IMPLEMENTED AND DEPLOYED TO BOTH
SERVERS; LIVE UI CHECKS PASSED WITH DOCUMENTED OPERATIONAL CAVEATS.
Owner visual acceptance, a new full promotion replay and live rollback rehearsal
remain distinct from the UI checks. See the execution record for exact limits.
Owner scope refinement: the remaining delivery is PRODUCT-ORIENTED, using
milestones M1-M7 in section6. The deployed resource console is its reusable
foundation, not a requirement to expose every backend capability as a page.
Parent: [010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md).
Actual phase status and evidence: [execution record](010_45_CUSTOMER_RESOURCE_CONSOLE_EXECUTION_RECORD.md).

## 1. Decision And Scope

Create a customer-only, product-oriented console using the Railway/Coolify
resource presentation already implemented. Customers start with Dealership AI
(AutoTrader integration) or Engage Apps; Shopify AI follows later. Inside a
product they see its apps, deployment/environment, relevant knowledge and
connections, and inspect status/problems in a sliding panel. Configuration is
secondary to understanding what is running, what needs attention and what to do
next. A backend feature does not automatically deserve a customer navigation item.

This proposal replaces the AWS-inspired CUSTOMER layout direction in010.36
section10.9. It does not replace the resource model, shared APIs/editors, security
or UI-only rollback boundary. The current admin/operator layout, navigation,
theme, capabilities and workflows stay unchanged. No global theme replacement.

The original [clickable visual prototype](designs/010_45_customer_console/index.html)
is retained alongside [actual-component phase review builds](designs/010_45_customer_console/gallery.html).
Implementation now exists in the Platform UI source. Review builds use fictional
offline data and do not expose real authentication, secrets or live mutations.
They are not a deployed production redesign or proof of a hosted promotion.

## 2. Product Model: Simple First, Deeper When Relevant

Progressive disclosure is derived from owned resources, actual lifecycle,
reviewed template capabilities and server-authorized permissions. It is not a
gamified unlock, a browser-local onboarding counter, or an excuse to hide faults.

### Same Account, Simple And Advanced Views

Owner clarification, 2026-10-23: the intended distinction is presentation depth
for the SAME customer account/session, not customer-admin versus customer-operator
roles. No new customer role or per-deployment role redesign is required by this
UI plan. The existing LoomAI staff interface is not the customer's Advanced view.

- Simple is the first-use default: resources, critical status, common settings
  and the next useful action. It must not become a permanently reduced product.
- Prefer Overview / Configuration / Advanced within each resource inspector.
  Deeper settings and customer-supported operational controls are discoverable
  there or in an expanded shared editor, without filling the main navigation.
- Advanced may expose more detail and configuration/commands already authorized
  for that customer. It never grants permissions, switches identity, introduces
  staff roles, or opens internal provider/administration screens.
- Missing advanced customer functionality requires a bounded customer API/editor
  contribution with the existing ownership/entitlement checks, not a grant of
  PLATFORM_OPERATOR or an unrestricted staff page embedded in the customer UI.
- Remember expansion/view preference locally if useful. Prefer per-resource
  disclosure over a global switch that simultaneously crowds every page. This
  preference is not a deployment setting, saved secret or authorization input.
- Critical errors, warnings, unknown/stale state and unsafe-change confirmations
  remain visible in either view. Switching depth preserves resource/target,
  unsaved inputs and app assignment; it performs no mutation or automatic replay.

The goal is one progressively detailed customer console with access to its
supported capabilities, not separate basic/technical portals or copied forms.

### Product-Oriented Release Boundary

The next milestones simplify the existing UI; they do not introduce another
configuration engine, product database, permission system or deployment workflow.
Compose the product experience from existing reviewed templates, plugins,
deployments and Engage installations. Keep the same customer identity and shared
editors; product-specific onboarding may contribute a bounded step where needed.

Dealership AI and Engage Apps are the current customer entry points. An Engage
app can deliver a dealership experience OR be used independently; do not impose
a new one-to-one product/app relationship. Shopify is a later milestone, not a
currently qualified customer-console launch claim. Clearly distinguish the
AutoTrader simulator from a real partner-authorized connection.

Do not expand this release into generic Behaviour management, a comprehensive
audit centre, universal integration editors, infrastructure topology, recovery
dashboards or worker-draining controls. Reuse an existing authorized function
only when a concrete product journey needs it. Record difficult edge cases for
later instead of making them new release workstreams. Critical failures still
remain visible, and ordinary ownership/secret/destructive-action protections stay.

### First setup

- Home contains the next concrete step and template choices, not empty metrics.
- Navigation starts with Home, Deployments, Engage apps and Account & team.
- Setup offers required Knowledge/Connections inline before first deployment;
  those pages remain reachable when they are a prerequisite, even before resources
  are running. Existing resources are never hidden by resetting onboarding.
- Ask for a deployment name, entitled template/target and required bindings.
  Advanced settings are collapsed. Do not ask the customer to select a server,
  create infrastructure products, or copy internal IDs.

### Staging ready

- Home prioritizes the staging deployment, test app and remaining go-live checks.
- Knowledge, Connections and enabled human capabilities become persistent
  navigation when installed/relevant. Their setup entry remains discoverable.
- Open-app and continue-setup actions are more prominent than all configuration.
- Production remains a reviewed action, never the default side effect of saving.

### Operating

- Show production and staging resources, active app assignment, immediate issues
  and recent relevant operations. With several deployments, group by deployment
  and customer project; a project is a UI grouping, not a new backend entity.
- Optional Human support, Action reviews and Behaviours appear only for installed
  capabilities AND authorized operations. An eligible uninstalled capability can
  be discovered through Add capability without pretending it is active.
- Users may expand advanced details deliberately. Do not remove basic actions or
  move the same action unpredictably after each successful step.

One generic experience must work for dealerships, Shopify and other customers.
Dealership names in the prototype are fixture data, not component logic.

### Product-Generalization Contract

The shared shell, resource canvas/list, inspector, warnings, environments and
progressive navigation must not depend on dealership, vehicle or Shopify names.
Compose them from existing authorized template/plugin/installation metadata and
typed resource projections. Labels, icons, required setup, experience packs,
related resources and available commands come from reviewed definitions and
actual lifecycle/permissions, never keyword detection or model-generated UI code.

Adding a product normally adds a reviewed template, plugin bindings, experience
pack and any product-specific onboarding contribution, not a different console.
Use a bounded frontend registry for approved special editors when existing
metadata is insufficient; do not invent arbitrary remote component execution.
A genuinely new capability may require one new reusable editor/resource adapter,
documented and tested independently. No claim that every integration is zero-code.

For Shopify the same Deployments/Engage/Knowledge/Connections views represent a
shopping assistant, storefront workspace, product/policy sources and a store
binding. Shopify-specific install/OAuth/private-adapter steps remain explicit
integration contributions. Preserve the existing private authentication path;
never substitute the dealership's anonymous flow or expose staff merchant
administration merely to reuse its page. Missing customer authority remains a
separately identified gap. Human/review/behaviour views remain capability-driven.

Acceptance must use at least dealership and Shopify/generic fixtures with the
same components. Different names alone are not sufficient: source types, access
mode, setup and optional capabilities must differ. Fixture tests are structural
UI proof, not live Shopify integration acceptance. UI appearance must not change
deployment, app authentication or customer data ownership.

## 3. Information Architecture

Product-oriented target (M1-M5): product-led Home/entry points, Engage apps, and
Account & team form the simple starting navigation. Dealership setup and daily
operations reveal only that product's relevant knowledge, connection, human
support and environment controls. Exact labels/grouping can be refined without
new backend entities or separate dealership/Shopify consoles.

The inventory below describes the reusable resource views already implemented;
it is NOT a requirement to show all of them globally or simultaneously. Existing
deep links and authorized advanced settings must remain reachable when grouping
them under a product. Generic Behaviour/audit/editor expansion is outside this
product release unless a specific M1-M6 acceptance case requires a bounded part.

Primary navigation:

- Home: next step, affected resources, health and most recent operation.
- Deployments: resource canvas/list, target environments and release state.
- Engage apps: app resources, URLs, readiness and current deployment assignment.

Contextual navigation:

- Knowledge & data: indexed DATA sources and document knowledge, clearly distinct.
- Connections: provider/integration/storage/channel bindings and their ownership.
- Human support: conversation handover and the authorized operator Inbox entry.
- Action reviews: governed action approval, distinct from human conversation.
- Behaviours: enabled conversational/specialist/event-plan configuration and runs.

Utility navigation:

- Activity & alerts: customer-visible operations and lifecycle notices, not global
  infrastructure logs or an invented full audit feed.
- Account & team: own customer profile, members and authorized access settings.

Keep global customer context separate from resource-scoped target context. A
deployment selection shows Production or Customer staging AND its release. The
control plane being production does not make every selected target production.

## 4. Shared Resource Inspector

Selecting a resource opens a right inspector without losing the underlying
overview, search, scroll or environment selection. Desktop target width440px;
an expanded detail view may use640-800px for a real editor. At narrow widths it
becomes a full-width panel, not a compressed desktop sidebar.

Information order, consistent across resource types:

1. Resource name, kind, target/release and explicit status.
2. Blocking error, warning or unknown/stale observation, above the fold.
3. One primary action that matches the actual state and permission.
4. Critical facts: active assignment, app URL, last successful operation, index
   freshness, pending decision or affected dependency as applicable.
5. Overview / Configuration / Advanced tabs; scoped Activity is available where
   supported. Related resources are links, not extra permanent navigation.
6. Detailed bindings, approved configuration, internal IDs, authorized evidence
   and destructive actions in explicit advanced areas. Secrets remain write-only.

Every issue has severity, affected scope, observation time and a supported
remediation/deep link. Multiple issues are ordered by impact, not hidden behind
an aggregate green badge. A healthy runtime does not imply an app can sign in,
its sources are fresh, or production promotion is ready.

Do not invent health: absent evidence is Unknown, a failed read is a visible
error, stale checks are Stale. A domain warning may leave the default app URL
usable; show that distinction. Retrieval/indexing failure remains visible even
if the runtime container is healthy. Counts are omitted when not authorized or
not available; never convert an unavailable Inbox into zero work.

Panels are addressable via resource/target IDs in the URL. Reload/Back restores
the selected resource; closing restores the originating selection/focus. Only
one main inspector at a time. Dirty edits require explicit discard/save, and
cross-customer/target changes clear or fence incompatible drafts as today.

## 5. Page Layout Contracts

### Home

M1 now governs this layout: product entry/resume and the active product's real
issues come first. Reuse the existing resource views below inside that grouping;
do not create another global resource dashboard beside a new product dashboard.

First-use version: welcome, template choices and short setup steps. Operating
version: issues first, a compact deployment/app resource map, then recent relevant
operations. No revenue claims, vanity charts or raw infrastructure dashboard.
The prototype includes both initial and operating versions.

### Deployments

Header: deployment name, target selector, reviewed release and primary action.
Canvas: runtime, Engage relationship and installed capabilities. External customer
systems have dashed outlines and an External label; they are NOT services owned
or provisioned by the deployment. Optional managed AI-state/Inbox services can be
shown as AI-owned dependencies, never as inventory/CRM databases.
Inspector: lifecycle/verification first, binding and source readiness next,
release progress/activity below. List view is available for many resources and
simple mobile navigation; the canvas does not require zoom/drag to operate.

### Engage Apps

Compact resource tiles show name, address, active target/release, access mode
and warnings. Selecting a tile opens current assignment and user-facing issues
first. Tabs reuse branding/experience, authentication and installation editors.
The app URL remains stable when a verified consumer assignment changes.
Changing a draft does not silently activate it or move its runtime assignment.

### Knowledge & Data

Top-level operational list separates live DATA sources from document files.
Show last sync/index state and failures before chunking/storage settings.
Document inspector shows source location, target, active indexed version and
indexing work. Read-only customer storage remains customer-owned; no file upload
or managed object-storage promise is introduced by the design. Versioning/source
replacement semantics stay under the existing document lifecycle.

### Connections

Resources show bound deployment/target, owner, type and verified condition.
Selecting a provider/storage/channel opens critical verification and binding
details. Credentials are write-only and never displayed/recovered in panels.
Provider accounts, customer systems, object storage and vector integrations are
not conflated with Platform-owned infrastructure. Reuse the reviewed binding
contracts; do not offer unrestricted URLs, server terminals or a global vault.

### Human Support

Show handover enabled state, channel, target and how authorized operators reach
the Inbox. Read-only queue counts/previews are shown only if a real scoped
channel reader is available; otherwise show Open Inbox without fabricated counts.
Basic Inbox remains its existing product/service boundary. Building another
operator messaging backend or relaying all customer chat through Platform is
not part of this layout. The prototype's queue is a future UI projection fixture,
not a claim that a customer queue-summary API exists today.

### Action Reviews

Show only when HUMAN_REVIEW is installed and customer decision authority exists.
Task list plus right inspector: action, trusted target, required decision,
expiry/version and confirmation. Existing approve/reject lifecycle stays intact.
On the current handover-only pilot this navigation is absent; an authorized
direct link shows Not enabled, not an API-error-as-empty-inbox state. The
prototype enables a separate fictional review fixture to show the intended page.

### Behaviours

Future design reference, deferred from the M1-M6 product release. Existing
customer-capable operation APIs do not require a generic Behaviour page now.

Resource list: installed Conversational, Specialist team or Smart Brain profile,
trigger/entrypoint, readiness and latest known run. Inspector: approved definition,
limits, specialist/plan composition and scoped execution history. Parallel and
sequential steps are rendered from typed configuration, not guessed from text.
This does not allow arbitrary action/event writes or broaden framework policy.
The current CUSTOMER_ADMIN routes do NOT include Behaviour pages. Customer
read/write authorization and safe projection must be checked separately before
exposure. Staff approval/readiness controls remain staff-only. Initial delivery
may show an authorized read-only profile; unsupported controls are omitted.

### Activity & Alerts

Filter existing authorized release/verification/source operations and customer
staging lifecycle notices by resource/target. Selecting an event opens its real
operation and evidence. Do not call combined partial feeds a complete audit log.
No staff/global logs, polling-driven model calls or automatic mutation retries.

### Account & Team

Business identity, own membership and roles first. Existing authorized invite/
member controls stay scoped; do not invent arbitrary customer roles or allow
operator credentials to become customer access. Billing screens or new pricing
controls are outside this design. UI preferences cannot change permissions.

### Creation And Promotion

Create: template -> required connections/storage -> review -> existing staging
apply. Short, resumable operation progress replaces one enormous form.
Promote: exact staging source/candidate -> independent production prerequisites ->
verification -> explicit app activation. Show blockers with direct remediation,
existing operation IDs and current evidence. No staging vector/conversation copy.
Show staging retention/expiry limitations honestly rather than a fake completed
cleanup checklist. No new wizard-state engine or silently extended allocation.

## 6. Product Milestones And Implementation History

### Current Product-Oriented Delivery Milestones

Status baseline: recorded source430b875a7 and hosted UI checks of23 October,
not a new live verification performed by this documentation amendment. Existing
010.36 hosted behaviour is distinguished from acceptance through the NEW UI.

#### M1 - Product-Focused Home - LIVE VERIFIED

Goal: start with Dealership AI or Engage Apps, see existing products/apps, open
the experience and understand the next important issue.
Already: deployment/app Home, first-use entitled template choices, resource
inspectors, app addresses/readiness, errors and both themes are live.
Implemented: product-led entry points grouped by explicit reviewed experience-pack
metadata, Engage Apps entry, exact-offer setup links, current app/deployment links,
and separately inspectable inactive deployments/app drafts. Active app warnings
remain visible. No offer version is selected by name or guessed recency. Local
model checks, customer scope regressions, staff parity and actual-component
desktop/mobile/both-theme checks pass. Source `e798d09d0` is published on both
servers; real customer Home/inspector/theme/mobile and staging staff checks pass.
App assignment/configuration unchanged. Shopify fixtures preserve reuse, not M7.
Done when customers can enter/resume their product without a broad infrastructure
menu. This is a presentation change over existing resources, not a new entity.

#### M2 - Guided Dealership Setup - PARTIAL

Goal: dealership template -> AutoTrader/simulator -> policies -> create/link
Engage app -> preview. No dealership business database is created.
Already: entitled template and guided deployment setup, simulator-backed inventory
integration, customer-owned S3 policy indexing, app/assignment configuration.
Last hosted reads confirmed3 ACTIVE policies and1 inventory source; earlier
010.36 proved the underlying customer journey.
Implemented source: a product task list in each owned deployment inspector links
existing setup, declared connection/storage editors, Engage assignment, staging
and go-live stages. Operational tasks require an explicit target release and keep
that exact scope in navigation. No new commands or duplicated forms. Local build,
20 actual-component scenarios and18 scope checks pass. Publication and the fresh
customer command journey are separate gates; M2/M3 UI source `b18189c2c` is now
live on both servers. Exact-release task/policy navigation passed with a real
customer. The fresh customer command journey remains open.
Finish the simulator path first; real AutoTrader partner approval/credentials
remain a separate integration gate, not a reason to invent another bridge.

#### M3 - Engage App Management - SAVED COMMANDS LIVE VERIFIED

Goal: create/link an app, use approved branding/tools/access settings, preview
it, obtain its script or standalone address, and understand connection problems.
Already: shared creation/configuration, reviewed experience/auth profiles,
domains, installation/standalone delivery, assignment/readiness, guarded editors.
Same-account Configuration/Advanced, retained drafts, both themes and the
existing production app assignment are qualified.
Remaining: customer-friendly product-linked entry/copy and bounded verification
of actual supported branding/tools/access edits. Read-only acceptance did not
live-test every branding field or save path. Reuse the existing app service.
Product-linked creation is now source-qualified: exact requested assignment,
explicit choice for multiple active assignments, visible unknown/foreign denial.
4 model and21 actual-component scenarios pass. Hosted saves/edits remain open.
UI source `b18189c2c` is live on both servers. Actual customer explicit-assignment
and unavailable-assignment creation/cancel checks pass without resource changes;
these do not count as a successful save.

Next delivery `7a1c41598` is now live on both servers: app commands explicitly
refresh the existing exact-target placement check and show failures inside the
dialog with inputs retained. Backend authority is unchanged. Real customer
create/reload/edit/reload/delete passes using a temporary draft; cleanup and the
existing app/assignment/published releases are verified. Security origin changes
remain in the editable deployment draft, and the existing hostname route worker
briefly propagates changes before readiness returns. See the execution record
for both original failures and the passing results. Customer edits stay within
the reviewed profile/allowed app fields; arbitrary branding/tools/auth JSON is
not newly enabled. New-app activation belongs to M4. Representative supported
saves are proven, not every domain/profile option or uninterrupted delivery
during a shared-domain change. The newly observed shared-generation readiness
coupling is explicitly tracked under M6; the original app is currently ready.

#### M4 - Test And Go Live - PARTIAL

Goal: open customer staging, try inventory/policies/actions/human handover,
understand blockers, then explicitly promote and switch the same app.
Already: guided staging/production steps, candidate/readiness checks, explicit
activation and independent production indexing. The underlying same-app
staging-to-production journey passed earlier010.36 acceptance.
Remaining: one fresh complete journey through the new product-focused UI; U4's
hosted gate remains open. Reuse existing promotion/cold replacement without
adding recovery, draining, parallel-version operation or vector copying.

#### M5 - Product-Scoped Daily Operations - PARTIAL

Goal: open app/inbox, update policies, inspect synchronization/important failures,
and manage supported branding/access from the relevant product.
Already: Knowledge/Connections controls and inspectors, Human support/Inbox
entry, app management, failure notices and safe release selection. Hosted reads
proved documents, inventory integration and reachable human handover; the
UI-only run did not replay a booking or a human conversation.
Remaining: organize useful tasks around each product, verify necessary daily
edits, and remove status-read friction. Product-scoped task links and real policy/
sync/handover reads are now available; live daily edits remain open. Generic Behaviour management, full audit
and universal integration editors are NOT prerequisites.

#### M6 - Verify And Close This Product Release - PARTIAL

Already: clean build,18 customer scope regressions, staff parity, offline
dealership/Shopify fixtures, UI publication to both servers, nine real customer
views/both themes, retained drafts, release locking, basic mobile and logout.
Remaining: the M2-M5 command journey, owner visual acceptance and the bounded
existing UI rollback check. Recurring15-minute operational-read expiry is now
resolved by backend`c82bb707d`, live on both servers. Final wider customer UI run
passes with only the explicitly unavailable optional action-review extension;
no renewal/probe or write-policy weakening. Earlier failed run is preserved.
Keep this a narrow product usability/
authorization task, not a capacity/recovery/infrastructure-management redesign.
Record harder edge cases as follow-ups; no full framework matrix for UI changes.
Owner-accepted operational limitation from M3: creating/removing an unrelated app
hostname temporarily makes an unchanged existing hostname fail APP_DELIVERY
because the route store requires the entire shared generation to be current.
Public bootstrap/manifest use that readiness. The owner explicitly accepts the
brief propagation window; it is not a defect/blocker for this release and does
not require a routing change. Preserve the observation, current authority and
removal checks. Continue M4 rather than opening a routing/recovery workstream.
Done means a real customer completes the supported dealership/simulator journey
without staff database edits or unadvertised configuration steps. Representative
main cases suffice; do not attempt every possible integration/edge case.

#### M7 - Add Shopify To The Same Console - LATER

Already: Shopify integration/private-adapter foundations and reusable Engage
components; offline fixtures preserve the different private access mode.
Remaining: customer installation/store connection, product setup and shopping
settings using the same M1-M5 patterns, then a bounded live Shopify journey.
Fixtures are not proof this milestone is delivered. Do not rebuild existing
Shopify backend capabilities just to provide product-oriented UI.

### Delivery Order And Stop Rule

Use M1 -> M2/M3 -> M4 -> M5 -> M6, finishing existing portions by reuse, not
reimplementation. M7 follows dealership/Engage acceptance. Do not open parallel
workstreams for deferred generic management pages. A blocker needs an exact
product acceptance case and the smallest fix. Staff layout stays unchanged.
Status must distinguish implemented source, live reads and completed customer
commands; neither mocks nor an older UI run closes a new journey gate.

### Earlier U-Phases: Foundation And Existing Gates

U0-U6 retain source/history and technical acceptance mapping. They do not
override M1-M7's current product scope. Broader prototype capability layouts are
future references, not a release backlog to implement now.

#### U0 - Approve The Visual Contract

Deliver this prototype, page/state coverage and data-contract map. Review first
setup, populated resource view, warning-first app/deployment inspectors and
optional capability pages. Gate: owner accepts the direction. No production UI
or backend change is part of U0.
Include light/dark previews and dealership/Shopify fixtures. The initial light
dealership direction was accepted by the owner; remaining visual refinements
are not permission to change staff UI or weaken customer access.

#### U1 - Isolate The Customer Shell

Add a CUSTOMER_ADMIN-only shell/composition boundary under existing auth. Retain
the current staff AppShell, routes, theme and side effects unchanged. Define
stable resource selection/target URL handling and shared inspector behavior.
Reuse models/query keys and editor scope fencing. Gate: exact staff baseline
comparison, existing customer denial/scope tests and desktop shell smoke pass.
Add the customer-scoped Light / Dark / System theme provider and device preference
without changing the staff provider, global palette or authentication.

#### U2 - Read-Only Resource Home, Deployments And Apps

Compose actual customer-owned deployment/release/app reads into the resource
overview/list. Prioritize issues and assignment evidence. Add first-setup and
staging-ready variants. Inspectors initially link to existing supported editors.
Gate: every displayed status has an identified source/time, no fabricated counts,
deep-link/Back/target changes work and no read triggers a mutation.
Resource adapters must pass both dealership and Shopify/generic fixture cases;
no domain-specific branching belongs in the shared shell or inspector.

#### U3 - Bring Shared Configuration Into Inspectors

Reuse existing Engage and deployment settings in focused tabs. Add Knowledge/
Connections inspectors with safe binding summaries, explicit save/discard and
draft/active distinctions. Keep complex editors as expanded detail views where
needed. Gate: current validation, secret write-only handling, unsaved edits and
cross-target scope regressions pass; staff views remain visually unchanged.
Use the same customer account for Simple and Advanced. Audit existing Manage /
All setup controls entry points against the per-resource disclosure contract;
do not treat the staff console as Advanced. Qualify discoverability and retained
drafts without changing role/session or widening backend authority. The owner's
clarification is a presentation acceptance item, not a new role implementation.

#### U4 - Guided Creation And Go-Live

Compose the existing customer template/prerequisite/apply/verify/activate steps.
Keep current proof-renewal controls explicit. Connect issues to the exact
configuration step without automatically replaying an operation. Gate: one
complete supported customer staging -> production -> same-app journey through
the new UI, with existing readiness/authority and independent indexing intact.

#### U5 - Capability-Specific Customer Views

Add Human support, Action reviews and Behaviours only as authorized and installed.
Keep Human support separate from action approvals. Prove capability visibility
and direct-link denial independently. Customer exposure must use verified
ownership checks, not a layout permission bypass. Some Behaviour APIs ALREADY
accept CUSTOMER_ADMIN; unexposed pages do not prove a missing backend contract.
Only a confirmed missing operation warrants a bounded backend change. Generic
Behaviour management is deferred from M1-M6, not a prerequisite for their
release. No framework release is required by this visual plan alone.

#### U6 - Desktop Acceptance And Reversible Publication

Test desktop1440x900 and compact laptop1280x800 as first-class. Verify all pages,
drawers, long values, warning/error/unknown/loading states, first setup, multiple
resources, dirty edits and exact activation dialogs. Recheck staff routes and
settings against the frozen baseline; retain a compatible immutable UI rollback.
Publish UI-only staging first, inspect, then production with configuration and
app assignments preserved. Gate: owner visual/workflow acceptance and bounded
hosted journey pass. Do not require a new full-framework matrix for presentation.
Review both themes, theme persistence/system changes and unchanged staff visual
output. Theme changes must not discard dirty edits, restart operations or switch
resource/target/app assignment. Live Shopify regression is bounded to affected
shared contracts, not implied by offline product fixtures.
Verify Simple/Advanced with the SAME customer session: common tasks stay uncluttered,
advanced customer controls remain reachable, warnings stay visible, drafts and
target selection survive switching, and neither view can call staff-only APIs.

## 7. Appearance, Mobile And Accessibility

### Light, Dark And System

Provide Light / Dark / System for the customer console. Default System follows
the device preference; an explicit choice persists locally as a UI preference,
not customer deployment configuration. Both themes have the same capabilities,
layout, severity semantics and information density. Theme changes never save
configuration, change authentication or override an Engage app's own branding.

Use semantic MUI tokens for canvas, surfaces, borders, text, controls, warnings,
errors, selection and status. Dark mode uses quiet neutral charcoal surfaces,
not blue/purple gradients or glow. Drawers, dialogs, popovers, tables, inputs,
disabled/loading/unknown states, code/evidence and shared editors must inherit
the customer theme; do not invert screenshots or copy forms per theme.

Scope the theme provider/CSS to CUSTOMER_ADMIN rendering. Staff routes, existing
AppShell and platformTheme stay unchanged. Avoid global body-class styles that
leak into staff screens. Both theme variants need desktop screenshots and basic
phone navigation checks. The light-only initial prototype is not evidence that
dark mode has been implemented or verified; record actual phase evidence.

### Desktop, Basic Mobile And Accessibility

Desktop is first-class. Basic mobile at390px must permit navigation, resource
selection, reading critical issues, closing panels and reaching main actions.
Large configuration surfaces can use a dedicated full-width view or an explicit
desktop recommendation. Do not spend this phase perfecting dense canvas/forms
or claiming full mobile feature parity. No page-wide accidental overflow.

A formal accessibility/WCAG audit, full assistive-technology matrix and exhaustive
keyboard parity are deferred by owner direction, not claimed certified. Retain
ordinary engineering basics: native labelled buttons/inputs, readable contrast,
status text as well as color, visible focused controls, Escape-to-close and
no trapped/hidden destructive commands. This is not a new release-blocking audit.

## 8. Existing-Code Mapping And Gaps

- `Platfrom/ui/src/App.tsx` already splits CUSTOMER_ADMIN routes from staff.
  Use this boundary, not a new frontend application.
- `src/layout/AppShell.tsx` has the current seven customer navigation entries
  and the staff navigation. Preserve staff rendering when introducing the new
  customer-only shell; do not alter shared CSS globally.
- `components/customer/CustomerDeploymentConsole.tsx` owns customer offers,
  admission, setup and production-readiness actions. Reuse those contracts.
- `pages/AIWorkspacesPage.tsx` owns existing app/configuration/assignment work.
- `pages/DocumentKnowledgePage.tsx` and `IntegrationsPage.tsx` own document and
  connection configuration. DATA/index and document state must stay distinct.
- `pages/HumanReviewPage.tsx` is governed action review, not live agent chat.
- `pages/BehaviorPage.tsx` and `BehaviorOperationsPage.tsx` exist on staff routes;
  safe CUSTOMER_ADMIN UI exposure is NOT implemented by their existence.
  `DeploymentBehaviorOperationsController` already accepts CUSTOMER_ADMIN and its
  service checks deployment ownership/operator access. Readiness approvals remain
  staff-only. Do not describe all Behaviour APIs as missing or staff-only.
- `pages/NotificationsPage.tsx` customer branch currently exposes staging
  lifecycle notices, not a complete per-customer activity/audit aggregate.
  `/api/deployments/{deploymentId}/activity` already checks deployment access in
  `DeploymentActivityService`; a full cross-resource aggregate is a different,
  deferred scope. Reuse a bounded existing feed if a product needs it.
- Declared offer binding slots do not imply a customer form exists for every
  kind. Inspect and reuse slot-specific editors/APIs before claiming a backend
  gap; implement only contributions needed for the selected product.
- `workspace/DeploymentWorkspaceContext.tsx`, editor buffers and `platformApi.ts`
  remain authoritative shared context/models; no second configuration store.

These gaps must be inspected during their phase. Do not promise new customer
mutation authority, Inbox metrics, audit completeness or cost/usage telemetry
because the prototype depicts a fictional future projection.

## 9. Explicit Non-Goals

- No admin/operator redesign, new UI framework, Platform DB migration or runtime
  deployment for a visual change.
- No business/inventory/CRM database provisioned beside an AI deployment.
- No vector transfer, customer-managed-storage replacement, public-search access,
  raw credential display, automatic action replay or semantic text matching.
- No new billing service, universal observability backend, operator chat backend,
  drag/drop infrastructure provisioning or unrestricted service marketplace.
- No simultaneous legacy/new runtime support or graceful-worker-fencing expansion.
- No generic Behaviour-management centre, full audit centre, universal connector
  editor, topology canvas or recovery dashboard as an M1-M6 release requirement.
- No production deployment until this design is reviewed and implementation is
  explicitly started.

## 10. Visual Deliverables And References

Open [the interactive layouts](designs/010_45_customer_console/index.html).
The design toolbar switches First setup / Staging ready / Operating and exposes
all proposed pages as review fixtures. Click resource tiles/rows to inspect,
switch inspector tabs, expand advanced details, open the creation flow, and
review production promotion. All changes are preview-local only.

Reference patterns: [Railway project canvas](https://docs.railway.com/projects)
and [service selection/settings](https://docs.railway.com/services); Coolify
[resource operations](https://coolify.io/docs/applications/operations/overview)
and [context-sensitive settings](https://coolify.io/docs/applications/configuration/general).
Borrow resource-first selection, contextual detail and operation visibility,
not their deployment ownership or complete infrastructure feature set.

Screenshot exports are generated by the adjacent verification script. Design
preview tests are not live customer acceptance or proof of unavailable APIs.

U0 deliverables: ten page layouts, three lifecycle variants, shared resource
inspectors, app/handover/behaviour configuration, creation/promotion/decision
overlays, and the [static screenshot gallery](designs/010_45_customer_console/gallery.html).
Offline Playwright checks cover all ten desktop page/inspector layouts, compact
laptop layout, basic mobile navigation/width, local preview interactions and
zero HTTP requests. Exact results are in the adjacent
`preview-verification.actual.json`. This closes design-artifact preparation only;
owner design approval and every live implementation phase remain open.

Implementation qualification: U1 shell/theme; U2 resources; U3 shared configuration;
U4 guided presentation (hosted journey still open); U5 supported customer
capabilities. U6 local viewport/theme/source qualification is distinct from
owner visual acceptance, a scoped UI-only deployment, hosted customer proof and
rollback verification. Consult the execution record before claiming completion.

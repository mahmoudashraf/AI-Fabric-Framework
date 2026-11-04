# Platform Cleanup And Live Customer Journey Audit

Started: 4 November 2026. Status: IN PROGRESS.

## Owner Request

Remove unused drafts and non-running Platform resources, then exercise the
customer journeys through the live UI and resolve reproducible defects.

## Safety And Scope

- Inventory current references before deletion. Preserve productized templates,
  live customer assignments, shared services, approved source storage, ProdUS,
  Shopify and framework demos. A stopped resource is not automatically unused.
- Use the normal archive/deletion lifecycle for confirmed disposable fixtures.
  Record exact IDs and terminal results. Never delete shared dependencies by name.
- Keep the unfinished 010.59 document-storage qualification separate from
  disposable drafts. No framework release or recovery redesign is implied.
- UI actions are live-browser evidence. Read-only API/native checks supplement
  them but do not substitute for a successful customer UI journey.
- Record failed attempts and incomplete coverage honestly; no blanket zero-error
  claim based on page loading or a single happy-path run.

## Checklist

- [x] Current staging/production inventory and reference map.
- [ ] Confirmed disposable drafts/test resources removed and terminally verified.
- [ ] Public products, Kit catalogue and authentication entry/recovery navigation.
- [ ] Kit creation, address validation/preview, setup and edit/reset navigation.
- [ ] Deployment configuration, integrations and document upload/indexing.
- [ ] Staging publication, launch/readiness and Engage connection.
- [ ] Engage branding/capability preview, embed/standalone and domain controls.
- [ ] Production promotion, activation and assignment continuity.
- [ ] End-user inventory/knowledge, follow-up, attachment and action journeys.
- [ ] Desk handover/reply/return and support request/My Requests journeys.
- [ ] Account/theme/sign-out, basic phone navigation and empty/error states.
- [ ] Fixes retested, final inventory/evidence recorded, task-only changes committed.

## Results

Historical acceptance is context, not a fresh pass for this audit.

### Cleanup Cycle 1

Before cleanup: staging 40 deployment records, production 51. Removed 19 empty
configuration-only fixtures through the existing archive/delete operation and
observed every receipt SUCCEEDED. Removed three never-active production app
drafts and the two associated abandoned Kits. No direct SQL deletion or native
service/database removal. Exact private snapshots and receipts are retained.

Staging deployment IDs removed: dep-db9976e3, dep-2bcc8c44, dep-a9fbb2e4,
dep-fc33edc4, dep-21baf7fd, dep-34ca281c, dep-49b45875, dep-cb2fe53c,
dep-6640aa2d, dep-08ac088a, dep-abb47160.

Production IDs removed: dep-30ea5174, dep-f3b5b977, dep-4e084db3, dep-4466d153,
dep-628c5848, dep-61abaf50, dep-59b4d5ef, dep-fa0df093.

Two app-cleanup defects reproduced: disabled apps were forbidden by the normal
deletion API, and unused unbound drafts required a deployment binding. Bounded
fixes retain customer ownership, normal authorization/locks/audit, active-app
protection and owned domain/route cleanup. Backend focused tests: 114 passed,
zero failures/errors/skips. Console deletion guard now allows cleanly unbound
cleanup only for explicit deletion; creation/activation still need connections.
Its confirmation explains permanent app/address removal, not document or
conversation deletion. Commits 7fdb9ba135 and 3c1fa8e523 were published;
backend 3c1fa8e523 was native-source/health verified on both servers.

### Live UI Observations

- Public catalogue and dismissible dealership MAX preview passed.
- Engage: connected a verified staging assignment without reassigning it;
  branding/contextual tools/phone preview and draft save/reopen passed. Temporary
  app `awi_pub_ae82edd559d464657ffdd49fb14d616b` is tracked for cleanup.
- Fresh Kit: starting experience, optional managed AI-state choice, branding,
  invalid address feedback, valid full app-link preview, review and creation
  passed. Kit kit-46bbdf7c-fff2-49fd-9394-40aff5aa8682 owns dep-a0a6ac0a and
  app awi_pub_8d628c084b4caf7c847b57fa0c588caa. UI publication created
  ver-45fedc31. No runtime applied or paid infrastructure created for this Kit.
- Production Northfield: anonymous inventory search and a compound follow-up
  comparing the same cars with approved delivery policy passed. No knowledge-
  domain question. Protected action opened sign-in; cancelling did not execute.
  Normal fixture-owner sign-in passed. Trusted item attachment selected This
  vehicle, and Browse remained usable while the item stayed attached.
- Console Support: real request form submission passed without ACCESS_DENIED;
  its opening details appeared in chat and AI answered from approved guides.
  Domain/readiness guidance was accurate, but the answer focused mainly on
  custom domains and omitted part of the requested website-embed setup. Treat
  this as PARTIAL semantic coverage, not a complete answer-quality pass.

### Fresh Simulator Setup Blocker

The new Kit has no simulator account/receiver approvals or bindings. Existing
Northfield has instance-only credentials and a receiver fixed to its own
deployment/tenant. Neither v2 nor the new v3 bootstrap creates a simulator
account. Current documented integration is attended: operator prepares the
account/receiver, staff approves exact scope, customer binds credentials.

This is a missing self-service simulator supply workflow, not an approval-lookup
failure. Do not copy Northfield credentials, repoint its receiver, bypass account
scope or add a per-deployment business database. Completing that fresh launch
requires isolated account/scoped receiver provisioning in shared external
services, then the existing generic integration contracts. The UI now offers
integration help instead of an unexplained administrator dead end. Full fresh
staging/promotion acceptance remains NOT PASSED.

### Cleanup Cycle 2

The two staging unbound drafts and 17 of 22 disabled test apps were deleted by
the normal owned-installation API. Every original ACTIVE app remained ACTIVE.
Five disabled legacy records were retained after explicit 403/409 responses:
two refer to inconsistent legacy customer scope; three require an editable
draft even though disabled installations are excluded from origin projection.
A focused correction is in qualification, not an authorization override.

The two disposable apps created during this audit, the never-deployed Kit,
dep-a0a6ac0a and its sole test version ver-45fedc31 were also removed. The
deletion receipt del-0155d5a5 reached SUCCEEDED. No runtime, customer source
bucket or business-system database was created or removed for this fixture.

### Handover And Operational UI

Northfield production runtime remains source ce9025736, framework 0.8.45,
release rel-d587186a. Support/Acme runtime provenance is separate; the newer
Platform control-plane image does not silently upgrade customer runtimes.

- Normal Desk SSO as the existing assigned support/reviewer fixture succeeded.
  A separate owner-only identity correctly could not read the support queue;
  no role grant was added to bypass this boundary.
- Conversation chat-89caf677-0d95-496f-ab04-c40984830e25 transferred through an
  AI action, appeared in Desk, was claimed, received a human reply in Engage,
  sent a visitor reply back to Desk, and returned through explicit agent
  confirmation. AI then answered the delivery follow-up from approved policy.
- Callback form bug reproduced: boolean consent was converted to a string by
  MessageBubble; structured submission was rejected for its JSON type. The
  framework correctly preserved the incomplete draft. No framework fix needed.
- Cancellation succeeded in the runtime, but the old form stayed editable.
  Generic widget corrections use typed parameters and structured cancellation
  markers, not answer-text matching. Focused tests/live retest pending.
- Connection status for the current production release showed COMPLETED,
  CURRENT, six source/six indexed stock records, zero failed work and preflight
  READY. Viewing/closing its configuration did not save or publish changes.
- Fresh owner authentication, account directory and password/magic-link entry
  navigation passed. Email delivery/password replacement were not exercised.
- Action Reviews exposed an actual legacy global-credential mismatch; the
  current release-scoped reviewer contract returned 200 with an empty inbox.
  Repair is limited to the existing authority/endpoint services.
- Customer Activity & alerts called an endpoint removed by earlier separation
  work. Its obsolete customer entry/API is being retired; administrator cleanup
  notifications are preserved. This is not a reason to restore admission code.
- Desk's Northfield Tickets view returned its unavailable-workspace message;
  clean empty review state and conversation support do not prove tickets pass.

Live screenshots and final terminal inventory will be added after retesting.

### Fix Qualification Checkpoint

- Widget: ten typed-value/state unit regressions; browser callback submission,
  cancellation, confirmation/execution history reload, existing submission and
  confirmation-replay smokes; typecheck and library/embed/installer build passed.
- Backend clean focused run: 52 installation-service, 34 PostgreSQL route
  reconciliation and 17 release-scoped review-service tests passed, zero
  failures/errors/skips. An earlier concurrent-build run had missing generated
  inner classes; that run is not counted as a pass. The clean serialized build
  resolved the build-output collision without weakening assertions.
- UI: 15 productization/API contract tests passed, including selected-target
  review reads/decisions and removal of the retired notices API. Production
  build passed; existing bundle-size/runtime-config warnings remain.
- Fresh fixture-owner support request SUP-5587AE64-879 was saved and its opening
  message delivered; AI accurately explained revision/preview/index/reconcile/
  ACTIVE/verification from approved guidance and identified missing live checks.
  Resolve reached RESOLVED; reopen is being verified. A different signed-in
  identity did not see another requester's requests in the same customer scope.
- Phone-width qualification is not claimed: the browser viewport capability
  acknowledged the override but the actual DOM stayed 1280x720, including in a
  temporary tab. Override reset and temporary tab closed. This is a tooling
  limitation, not evidence of a website responsive-layout defect.

Fixes are pending immutable-image publication and live retest at this checkpoint.

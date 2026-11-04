# Platform Cleanup And Live Customer Journey Audit

Started: 4 November 2026. Final checkpoint: 19:50 UTC.
Status: CLEANUP AND BOUNDED FIXES RELEASED; FULL CUSTOMER-JOURNEY QUALIFICATION
NOT CLOSED. The final acceptance section supersedes earlier interim checkpoints.

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
- [x] Confirmed disposable drafts/test resources removed and terminally verified.
- [x] Public products, Kit catalogue and authentication entry/recovery navigation.
- [ ] Complete fresh Kit setup through applied runtime (creation/publication passed).
- [ ] Deployment configuration and new document upload/indexing end to end.
- [ ] Fresh staging launch/readiness (existing Engage connection passed).
- [ ] Complete Engage domain flow (branding/preview/embed/standalone passed).
- [ ] Production promotion, activation and assignment continuity.
- [x] Northfield inventory/knowledge, follow-up, attachment and confirmed callback.
- [x] Desk handover/reply/return and support request/My Requests lifecycle.
- [x] Account/theme, Northfield sign-out and basic phone navigation.
- [x] Bounded fixes retested and live source/health verified on both hosts.
- [x] Final inventory/evidence recorded and task-only changes committed.

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
- At this intermediate checkpoint the viewport override appeared ineffective
  on the inspected tab. Later testing found it applied to the active Desk tab;
  actual 390x844 Console and Desk checks are recorded below. Full physical-phone
  qualification is still not claimed.

The fixes were pending publication at this intermediate checkpoint. Publication
and final live retest are complete as follows.

## Final Acceptance

### Released Sources And Verification

- Platform backend and Console on BOTH hosts: immutable source
  `d3be8a1ce576ae47415f0478a039001f0c28bc42`. Core CI `41889523508` passed.
- Shared Desk backend/UI on BOTH hosts: immutable source
  `b2fb6a74f78ecd78bc6c5dc1693b072803aa1f3d`. Full Desk CI `41895938461`
  passed, including PostgreSQL and browser tests. OCI/native revision and
  healthy running container were checked separately from pipeline success.
- Native Coolify receipts: staging Console `bzmce9f6phys0insngaii35i`, backend
  `kwgyrnxdp522aac73eszt5ju`, Desk `okezsaqyhvszq1h746m9fxg5`; production
  Console `izb212cxh0bvtcb0ms6pfdgz`, backend `ceswphb8h3iqi3u91cc6e7vv`,
  Desk `j11i7ynlvan7ybgz84lbrpmx`.
- Focused backend: clean 103 installation/route/review-service tests and 45
  review-controller tests passed. UI productization/API15 and production build
  passed. Widget typed/state10, browser submission/cancel/history/replay,
  typecheck and full library/embed/installer builds passed.
- Desk ticket-capability UI8 and service2 passed. An initial full Desk build
  failed because an old-schema migration fixture used today's binding helper.
  The fixture now seeds the exact historical schema, with no production SQL
  fallback. Final focused migration/capability6 passed on H2 AND PostgreSQL;
  full Desk CI then passed. No failed run is represented as successful.
- No framework release, runtime upgrade, secret rotation, semantic keyword
  matcher, hidden retrieval fallback or weakened authorization was introduced.

### Final Cleanup Inventory

- Removed 22 deployment configuration records through the normal lifecycle;
  every successful removal has a terminal SUCCEEDED receipt. Removed 29 apps:
  five original unused drafts, 22 disabled test apps and two audit-created apps.
- Additional removed deployments after cycle1: `dep-a0a6ac0a` (audit Kit,
  `del-0155d5a5`), `dep-8d062ef3` (`del-d79be204`) and `dep-8192b6ca`
  (`del-71de6f28`). The latter two had no runtime release/native handle/app
  reference; their obsolete consumer links were removed through normal APIs.
- All 22 disabled app records were eventually removed. The final correction
  uses administrative cleanup for disabled legacy consumer records; normal
  customer ownership checks remain. Disabled installations are already absent
  from origin projection, so deletion no longer requires rewriting a draft.
- Four staging fixtures are archived with evidence retained: `dep-6e29bab9`,
  `dep-70b3d2c1`, `dep-7dba73bf`, `dep-81452b83`. The first normal deletion
  (`del-2610df6a`) failed on the database invariant "Released external vector
  binding is permanently fenced". No trigger bypass or direct deletion.
- Final staging: 29 deployment records, eight nonarchived; three ACTIVE apps,
  one intentional Shopify DRAFT, zero DISABLED apps. Production: 42 deployment
  records, 14 nonarchived; five ACTIVE apps, one concurrent customer DRAFT,
  zero DISABLED apps. Archived records are included in these totals.
- Every original ACTIVE app remained ACTIVE. A new customer draft created at
  18:59:58 UTC during the audit (`dep-0fbedfb9`,
  `kit-ef964cb5-4022-4b1f-bac7-7528409bb601`) was detected and preserved,
  rather than mistaken for an audit fixture.
- Preserved the external pilot setup, Support references, productized templates,
  current assignments, shared/native dependencies, approved S3 sources, Shopify,
  ProdUS and framework demos. This is Platform configuration cleanup, NOT an
  assertion that every stopped Coolify service or database is safe to destroy.
- Two old deletion jobs remain unchanged: production `del-cbeee9b3` /
  `dep-772416fd` QUEUED since 21 April; staging `del-ca59d4fe` / `dep-b67174e8`
  RUNNING since 5 May. They were not blindly replayed.

### Final Live UI Results

- **Northfield standalone:** anonymous search, same-car follow-up with approved
  delivery policy, protected-action sign-in/cancel, normal fixture sign-in,
  trusted result attachment, Browse/context switching and rich vehicle detail
  passed. No knowledge-domain clarification or conversation-access loop.
- **Typed callback:** the actual Name/Phone/boolean Consent form submitted,
  final confirmation executed one fictional demo write and rendered structured
  receipt `NFM-2DD5730F` at 19:03:35 UTC, status NEW, 2025 Aster E1. Phone
  `07700900900` was synthetic. Reload retained the receipt with zero editable
  old forms and zero confirmation buttons. A prior cancelled draft also stayed
  retired. No action text was used to infer terminal state.
- **History/isolation:** five owned conversations loaded. Northfield sign-out
  cleared the authenticated conversation, attachments and receipt and returned
  to fresh anonymous Browse. Console/Desk test identities were signed out before
  ending the audit; temporary tabs were closed.
- **Handover:** conversation `chat-89caf677-0d95-496f-ab04-c40984830e25`
  completed AI-requested transfer, assigned-agent claim, human-to-visitor reply,
  visitor-to-agent reply and explicit return to AI. AI answered the policy
  follow-up afterward. Delivery was real, not a fabricated callback receipt.
- **Action Reviews:** both selected Staging and Production now show Inbox clear,
  not Unauthorized. Calls use the selected target's verified release and scoped
  runtime assertion, not global credentials. No review decision was fabricated
  merely to populate an empty inbox.
- **Support:** `SUP-5587AE64-879` saved, opened its real AI conversation and
  answered the approved document-update procedure accurately. Resolve ->
  RESOLVED -> reopen OPEN -> cancel CANCELLED passed. Cancelled conversation
  became read-only. Required Subject validation and requester isolation passed.
  Email notifications remained off. Earlier `SUP-FD651503-A23` answered domain
  guidance but omitted part of embedding; retain PARTIAL semantic coverage.
- **Embedded dealership:** inventory and Aster detail pages loaded with photos.
  Attach-page did not open MAX. Two bounded page attachments survived navigation
  and MAX expansion. Anonymous compound stock/delivery answer was correct.
  Docked input had no MAX tools; MAX had no attach-current-page icon. Browse
  remained usable with context. Selecting two actual result cards submitted
  the comparison; answer was correct text/RAG, not the custom comparison view.
  Removing the last attachment automatically returned to Browse.
- **Configuration:** address validation/full-link preview, temporary Kit
  creation/publication, connected assignment, branding/capability preview,
  account/theme and configuration open/close passed. Production source status
  showed CURRENT/COMPLETED, six source/six indexed stock records, zero failures;
  three approved policies ACTIVE. Viewing did not change live assignments.
- **Desk:** Northfield Tickets is correctly disabled with its not-configured
  explanation, rather than polling a missing ticket space. Owner-only support
  access remained denied. Configured ticketing passed service/browser tests;
  a configured live ticket inbox was not newly qualified under a support agent.
- **Phone-sized UI:** actual DOM 390x844 verified. Console Home, navigation,
  My kits, Engage list and app detail inspector passed without horizontal
  overflow. Desk navigation and capability state checked at that size. Viewport
  reset requested and temporary phone tab closed. Physical iPhone/Safari and
  exhaustive keyboard/accessibility qualification are not claimed.

### Still Open / Not Qualified By This Audit

1. Fresh simulator Kit apply and full staging -> production -> app activation
   remain blocked by the attended isolated simulator account/receiver supply
   workflow described above. UI creation/publication is not full self-service.
2. Custom-domain activation and Shopify private-adapter regression were not
   rerun end to end. Password/magic-link/recovery entry points were checked,
   not email delivery or replacement of a real account credential.
3. Acme's older idle standalone session cleared its local identity on sign-out,
   but reported that provider logout could not be confirmed and tokens remain
   valid until expiry. Cause was not proven in this run; no revocation or
   successful provider logout claim. Northfield and Console/Desk sign-out passed.
4. Historical Northfield delivery warning is an old Oct26 closed-conversation
   USER_MESSAGE/PROVIDER_HTTP_404, not the successful audit conversation. It was
   retained rather than erased to make the dashboard appear green.
5. Four archived evidence-owning fixtures and two historical queued/running
   deletion jobs remain. Their cleanup is not silently reported successful.
6. Separate 010.59 storage qualification: all 13 selected Support guides indexed
   ACTIVE and staging verification passed, but production promotion was not
   completed because the consumer is bound to a different exact release. The
   bucket/uploads/readers were preserved. No vector transfer or live assignment
   override was performed.

### Evidence

Screenshots in this directory: `ai-after-handover.png`,
`embedded-dealership-after-navigation.png`, `production-review-inbox.png`,
`support-request-lifecycle.png`, `desk-ticket-capability.png`,
`phone-customer-kits.png`, `phone-workspace-details.png`.
`callback-history-retired-forms.png` shows retired historical forms after reload,
not the final receipt itself; the receipt result is recorded above. Private
full inventories and terminal receipts stay in ignored private storage and are
not published with credentials or customer source contents.

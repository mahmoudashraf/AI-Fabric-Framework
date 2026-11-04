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

## Owner Review And Follow-Up, 4 November 2026

This section supersedes the earlier requirement for a simulator account per
fresh Kit and the earlier release-blocker classification of domains/Shopify.

### 1. Shared Simulator Accepted

- One existing shared simulator service and synthetic advertiser account are
  enough. No per-customer simulator account factory is needed for this demo.
- Real deployments will replace that external endpoint/credentials with the
  customer's actual AutoTrader or dealership API through the existing plugins.
- Correct deployment bindings and webhook/callback routing still need to be
  configured and verified; sharing the account does not prove that all new
  Kit routes are already configured. Do not share customer conversations,
  documents, Desk assignments or write authority across unrelated customers.
- This is a scope correction, not evidence of a new successful fresh Kit apply.

### 2. Promotion Was Not Proven Broken

Two different incomplete checks were previously described too broadly:

1. The cleanup audit's new Kit never reached an applied staging runtime. It
   therefore could not complete a fresh staging-to-production UI journey.
   There was no failed production promotion operation for that Kit.
2. The separate 010.59 Support storage canary did have a verified staging
   release (`rel-2ab687cf`), but its selected consumer/app connection was still
   assigned to the old production release (`rel-09c92f90`). The promotion-plan
   API returned 409 because the supplied staging candidate was not the
   connection's exact bound release.

The check is in `DeploymentPromotionCandidateService.resolve`: it compares
deployment, release and target against the selected consumer binding. It is
not a missing worker-fencing capability, capacity check, vector transfer,
framework problem or inability to deploy to production.

For a new staging-first journey, use the connection that is actually assigned
to the verified staging release, promote it, verify production and explicitly
switch the app connection. Do not send a staging candidate with a connection
already assigned to a different production release. Existing production
deployments can instead use the normal publish/apply production-upgrade path;
staging promotion is not mandatory. No live app was rebound to staging merely
to make this test pass. The outstanding item is correct-sequence qualification,
not a demonstrated need for a promotion redesign.

### 3. Custom Domains Accepted; Customer Steps Documented

Owner accepts the previously qualified domain capability. A new full domain
rehearsal is not a release blocker for this task. This is owner acceptance of
prior verification, not a fabricated fresh pass. The external Engage setup
guide now has an explicit customer/integrator checklist: exact hostname ->
Console-provided TXT/CNAME -> Verify and prepare -> inspect/apply a required
deployment update if offered -> configure external auth redirects if needed ->
Use verified update -> readiness -> explicit activation -> customer URL check.
Customers do not need Coolify or a manually selected Engage environment.

### 4. Shopify Deferred

Owner explicitly defers Shopify private-adapter regression. It is not a blocker
for dealership/Support work and is not newly qualified by this decision.

### 5. Acme Logout: Confirmed Root Cause, Fix Not Yet Applied

Read-only Supabase Management API auth logs establish the actual event, not an
inference from the generic browser warning:

- At `2026-11-04T19:43:59Z`, `/logout` returned 403, `error_code=bad_jwt`,
  error `token has invalid claims: token is expired`.
- Auth event ID `015d4c31-193d-4c8c-bc57-c23f54765031`, request ID
  `01a1a816-f4fb-7755-a497-748b63ed39ba` match the exact browser error time.
- Another logout at 19:44:15 returned 204. The provider was not generally down;
  do not treat that other request as proof that the failed Acme session ended.
- Private full logs remain in ignored mode-0600 storage. No JWT, refresh token,
  service key, IP address or private transcript is copied into this report.

Source cause: `workspace-apps/standalone-ai-workspace/src/main.ts` captures the
current session in `signOut`, immediately clears local identity/private state,
then calls `/auth/v1/logout?scope=local` with `current.access_token`. It does not
call the expiry/refresh logic used by normal authenticated requests. After an
idle token expiry, Supabase therefore rejects the logout before revoking the
provider session. Current regression tests cover 503/network timeout, but not
this expired-access-token logout path.

Security impact has two distinct parts:

1. Local sign-out did occur: the workspace, stored credential and private
   conversation state were removed. A subsequent local visitor does not simply
   inherit that signed-in chat. No cross-user transcript leak was observed.
2. Provider revocation did NOT complete. The provider session's refresh token
   might still be usable; an expired access JWT does not itself prove that the
   long-lived refresh capability ended. This is a real logout implementation
   defect, not merely an inconvenient error message.

Separately, even successful Supabase logout does not immediately revoke an
already-issued, unexpired access JWT. That is the documented stateless-token
contract. Existing runtime tokens are capped at 300 seconds and cannot outlive
the provider token; the broker currently checks signature/issuer/audience/expiry,
not an online session-revocation ledger. Do not claim instant JWT revocation or
expand this focused defect into an unreviewed runtime/session-ledger redesign.

Proposed bounded correction, not implemented or deployed in this follow-up:
clear local authority immediately; if necessary obtain a fresh access token
using the SAME retained provider session only for local revocation; verify the
same subject, never persist/reinstall that refreshed identity; then request
`scope=local`. Keep provider calls bounded, handle an in-flight refresh without
restoring authentication, and still report genuine network/provider failure.
Do not use global logout, privileged browser credentials, anonymous fallback or
silently classify any 401/403 as successful revocation. Add expired-token,
refresh-failure, refresh/logout-race and fresh-session regression coverage, then
prove refresh is refused after real provider logout. No framework change needed.

Sources: [Supabase sign-out](https://supabase.com/docs/guides/auth/signout),
[session and JWT revocation contract](https://supabase.com/docs/guides/auth/sessions).

### 6. Force / Hard Deletion: Existing Contract And Gaps

- Record deletion exists at `DELETE /api/deployments/{id}` for authorized
  deployment administrators, including an owning CUSTOMER_ADMIN. It requires
  archived state, no bound consumer, no active apply and no queued/running
  deletion operation.
- `hardDelete: true` additionally removes tracked owned provider infrastructure
  before deleting Platform records. It is restricted to PLATFORM_ADMIN and is
  NOT an override of those guards, customer ownership or database constraints.
  External customer services are not automatically deleted.
- The staff UI already shows a red irreversible-deletion warning, bold deployment
  name, exact-name typing, an optional infrastructure-deletion checkbox and a
  hard-delete reason. However, typed-name/reason-length confirmation checks are
  UI-only. The backend request has no typed-name confirmation field.
- The customer resource console does not currently expose deployment archive,
  permanent delete or infrastructure hard-delete controls. Therefore "customer/
  integrator force deletion with a strong confirmation" is NOT fully available.
- The fenced fixture cannot be fixed by ticking hard delete: an immutable
  RELEASED_UNUSED_TRANSFER vector binding and its transfer ledger still hold
  references to deployment resource handles. Preserve that audit/fence evidence;
  a future narrow schema/lifecycle fix should allow record removal without
  reviving/reassigning released capacity or deleting external vector storage.
- Read-only live readback confirms production `del-cbeee9b3` is still QUEUED,
  `hard_delete=false`; staging `del-ca59d4fe` is RUNNING, `hard_delete=true`;
  staging `del-2610df6a` is FAILED, `hard_delete=false`. Staging also contains a
  historical copy of the queued record. No delete/retry/retrigger was performed.
- Existing failed-operation retry and running-hard-delete retrigger are admin
  operations; they replay cleanup, not force past a fence. The queued historical
  row is not made retriggerable just because its age is large.

Recommended follow-up: reuse the existing lifecycle in customer/integrator UI,
enforce typed confirmation server-side, and define exact owned-resource removal
scope. Correct the retained-ledger relation separately. A blanket "ignore all
guards" flag or disabled SQL trigger is not the proposed solution. This owner
request was verification, so no destructive action or new force API was added.

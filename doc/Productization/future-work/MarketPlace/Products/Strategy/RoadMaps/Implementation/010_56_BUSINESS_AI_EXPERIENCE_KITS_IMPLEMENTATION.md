# 010.56 Business AI Experience Kits

Date: 2 November 2026
Status: Implemented, deployed and core production UI qualified; original test-fixture cleanup complete

## Product Contract

Two business starting points: AI Customer Experience and Dealership AI
Experience. Shared LoomAI Desk is optional for either, now or later. Kits connect
existing Platform deployments, Engage apps and optional Desk; they do not create
a new runtime type, customer business database or release mechanism. Templates
provide editable defaults, not lifetime restrictions.

Visual references are the latest reviewed assets under
`output/kit-layer-ui-concepts-20261102/v2/`: the dark Console chooser and guided
setup, and light public homepage. The original customer kit overview supplies the
resource relationship/inspector layout, with the revised kit names. Match these
layouts, palette, media and spacing; illustrative facts/statuses never become
claims about connected customer services. Existing admin/operator layout stays.

## Full Stack Scope

- Persist the public catalogue and customer kit/resource relationships in the
  existing Platform database. Store no external credentials in kit metadata.
- Starting a kit creates an owned editable deployment configuration, canonical
  consumer and draft Engage installation atomically and idempotently. It does
  not publish/apply, activate, call AI or provision paid resources.
- Use approved, available template compositions and existing ownership checks.
  Missing access/integrations remain visible; no borrowed canary credentials.
- Guided setup persists actual app branding/delivery settings and kit choices.
  Preview reflects those settings. Website and standalone can both be selected.
- My Kits reads real linked resources and readiness. Reuse existing editors,
  document/source connections, Desk enable/agent portal, versions, apply,
  promotion and explicit app activation. No duplicate source of truth.
- Public site: business experiences first, Platform/Engage/Desk next, framework
  and Chat UI foundations beneath; preserve individual-product navigation.
- Customer data/storage rights, source credentials, DNS and staffed agents remain
  real prerequisites. A simulator-backed kit is labelled simulator, never a
  certified real AutoTrader connection.

## Phases

- [x] K1: Catalogue, kit persistence, owner-scoped API, atomic/idempotent linked
  draft creation, editable experience settings and focused backend tests.
- [x] K2: Console chooser/detail/setup and My Kits/resource inspector, real API
  wiring, preview, auth return continuity, dark/light and basic narrow layout.
- [x] K3: Public site business-kit layer and concise experience pages.
- [x] K4: Integration tests, exact-source builds/deployments, actual customer UI
  create/reload/edit/resource navigation and readiness/permission failure checks.
- [x] K5: Documentation, cleanup and accurate external/review prerequisites.
  Guide and final UI evidence are written. Earlier qualifier apps, bindings and
  archived deployment/kit metadata are removed through the corrected normal
  deletion path. One intentional configuration-only review kit remains,
  without services or funded allocations.

No framework change is authorized. Any demonstrated gap is proposed separately.
Work remains product-focused; no new capacity/recovery/draining architecture.

## Implementation Evidence

### Local Qualification, 2 November 2026

- Owner-scoped catalogue/create/read/update API and V222 migration implemented.
  Atomic linked draft creation reuses the existing bootstrap, consumer binding
  and Engage services. Both channels use separate installations sharing one
  consumer/deployment. Only kit metadata cascades on qualified deployment delete.
- Kit suite: 33 passed, none skipped, including real PostgreSQL migration,
  constraints, transaction rollback and metadata-only deletion cascade.
- Existing bootstrap, consumer configuration and installation suites: 139 passed,
  none skipped. These protect the reused product paths.
- Console production build and focused kit/auth checks passed. Public-site
  production build, Astro checks and existing content/static smoke passed.
- Draft preference for Desk is deliberately distinguished from an enabled Desk
  connection; the existing Human Support flow performs actual connection and
  assignment after a verified deployment exists.
- Public and authenticated kits, previews and resource links are implemented.
  The initial staged UI created and edited the dealership kit with both delivery
  channels. Final production browser qualification is recorded below.

### Hosted Rollout And API Qualification

- Implementation `9ea92aa91a8d3c386acc71b6828a39b47be8e7f5` is live in both
  backends and the public site. Core image workflow `41486149211` succeeded.
  V222 is applied successfully on both control-plane databases.
- UI follow-up `50fbe023fd571ab16f4a7f8f73c706ce8d538803` retains the selected
  kit across initial workspace creation, email return and bookmarked setup
  routes. Workflow `41526916999` succeeded; both UIs are exact-source healthy.
  Final focused UI/auth suite: 33 passed, none skipped. The native image and
  individual Coolify job identities are in the dated qualification record.
- Production authenticated generic kit create/read/update passed with an
  existing Supabase fixture, using normal public-template enrollment. Replay
  returns the same kit; changed replay and stale edits return 409. Editing through
  the normal Engage API is visible in the kit, and later kit edits preserve that
  advanced field. A second customer's reads and writes return 404.
- Real canonical rollback passed: an invalid second-channel origin returns 400;
  kit, deployment, app and consumer counts all remain unchanged. Public catalogue
  is readable anonymously; customer endpoints return 401 without authentication
  and 403 to a staff API key. Console-origin CORS passed.
- Both qualifiers have zero versions, releases, managed dependency graphs,
  managed vectors and provider allocations. No customer runtime, framework,
  business database, document storage, live assignment or paid resource changed.

### Production UI Qualification Completed

The Browser connection recovered on the owner's next retry. Actual production
customer navigation created kit `kit-4777f699-ae6c-45f9-bf54-e86d2166114a` and
draft deployment `dep-4e084db3` from the Dealership simulator, with both Engage
channels and optional Desk requested. A title/colour edit persisted after a real
reload. The canonical Engage editor showed the same saved branding and rendered
the dealership tool groups. Deployment, knowledge, connections, Desk and preview
links resolved the correct resources; no missing release was presented as ready.

My Kits and its navigation worked at 390px without horizontal document overflow;
desktop inspection used 1440px. Six screenshots are retained under the dated
evidence directory. No signup, runtime apply, domain activation, AI query or
agent assignment was performed by this draft-creation walkthrough. First-signup
selection continuity is covered by focused tests, not fresh hosted signup proof.

The new review draft is intentionally retained for customer inspection. The two
earlier Acme test qualifiers were checked to have zero releases and only exact
unused DRAFT apps. Normal APIs removed those apps, revision-checked unbound and
removed their consumers, and archived both deployments. Their final deletion
jobs failed on the pre-existing `platform_customer_offer_deployments` foreign
key to `platform_deployments`; no database deletion workaround or unrelated
backend rollout was used. These are archived metadata, not orphan services.
That initial failure is resolved by the focused deletion fix below.

### Template-Bootstrap Deletion Fix Verified

- Backend fix `7c1a5a631154ca366182704560090b7e16d7c3bf` deletes and flushes
  only the deployment-owned bootstrap association before deleting the parent,
  inside the existing transaction. Shared offers/customers and audit history are
  retained. No foreign key, ownership guard or approval was disabled; no manual
  data deletion or framework change was used.
- Thirteen focused deletion/kit tests passed on GitHub's Docker-enabled runner,
  with no skips (run `41539591010`). This includes real PostgreSQL/JPA deletion,
  rollback after a later failure, preservation of another deployment and its
  shared offer, no-association deletion, and the bound-consumer guard. Twenty-nine
  additional local kit/offer tests passed. The CI-only commits have the identical
  backend tree as the deployed image source; initial Docker-unavailable skips
  were not counted as successful PostgreSQL qualification.
- Backend image run `41538946059` succeeded. Staging deployment job
  `gr96wrq6733fpikp4i25j803` and production job `msntu1442cegilrzke781nbd`
  finished; native container labels match the exact fix source and are healthy.
- Normal retry operations `del-82e418e4` (staging) and `del-df8401c3`
  (production) SUCCEEDED. Native readback shows zero original deployment rows,
  bootstrap links and kit metadata in each environment. Historical deletion
  receipts remain for audit. The separate Northfield review kit still refreshes
  through the production UI. No UI or customer-runtime redeployment was needed.

See `verification-support/experience-kits/2026-11-02-full-stack-qualification.md`.

### Business-First Wording Pass

The public site, Console catalogue, kit selection, setup and overview now lead
with customer outcomes rather than deployment terminology. Product identities
remain Platform, Engage and Desk; the kit overview describes them as **Your AI
assistant**, **Your customer workspace** and **Human support**. Setup explains
what will be created, what customers will see and what remains to go live.

Catalogue migration V223 updates only the two platform-owned kit definitions'
summary, description and feature text. Template references, versions, customer
configuration, permissions and publishing remain unchanged. Main-screen
readiness text uses exact known codes/statuses; original codes and diagnostics
remain under **Technical details**, while unknown statuses/messages are retained.
No AI prompt, routing, action, runtime or framework behavior changes are included.

Qualification passed: 33 backend tests (including real PostgreSQL migration
checks), 36 Console/auth tests, both production builds and site content/static
checks. Source `9b2522105` is healthy on both control planes and UIs and the
production public site. Live catalogue/detail, setup review and overview checks
passed; original readiness diagnostics remain available. No new test kit was
created. Full details are in
`verification-support/experience-kits/2026-11-02-business-first-wording.md`.
The administrative/operator views remain unchanged.

### Follow-Up Boundaries

This release does not include a kit-definition authoring UI. Additional kits use
the catalogue and existing template/experience-pack contracts, not another
runtime service. Editing delivery modes independently in the advanced Engage
editor merits a later read-model check against the kit's saved channel plan;
do not infer that untested variant is already qualified. Current qualified
creation/edit cases use the kit's explicit delivery selection.

### Explicit Boundaries

Creation prepares configurations, not a working AI answer before services and
knowledge have been connected. Simulator and customer-owned connections remain
clearly distinct. Existing releases, promotion, authorization, deployment
settings and advanced editors remain the source of truth. There is no kit-only
runtime, duplicated inbox, customer inventory database or credential store.

# 010.56 Business AI Experience Kits

Date: 2 November 2026
Status: Implemented and deployed; final production UI qualification remains open

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
- [ ] K4: Integration tests, exact-source builds/deployments, actual customer UI
  create/reload/edit/resource navigation and readiness/permission failure checks.
- [ ] K5: Documentation, cleanup and accurate external/review prerequisites.
  Guide and evidence are written. Configuration-only qualifiers are retained
  for the final UI inspection; they have no services or funded allocations.

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
  channels. Final production browser qualification is tracked below.

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

### Remaining K4 UI Evidence

Final production create/edit/reload and resource-link traversal, first-signup
return, and narrow-layout inspection remain unclosed. Browser automation could
read old tabs but could not navigate or attach new ones after the interruption;
the old tab retained its old bundle even after the backend and UI rollouts.
Restoring an explicit viewport recovered screenshots, not navigation. The owner
was asked to reopen the Browser panel. Do not substitute API success for this
remaining UI evidence or mark the whole plan fully qualified yet.

See `verification-support/experience-kits/2026-11-02-full-stack-qualification.md`.

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

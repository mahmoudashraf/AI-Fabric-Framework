# 010.56 Business AI Experience Kits

Date: 2 November 2026
Status: Implementation in progress

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
- [ ] K2: Console chooser/detail/setup and My Kits/resource inspector, real API
  wiring, preview, auth return continuity, dark/light and basic narrow layout.
- [x] K3: Public site business-kit layer and concise experience pages.
- [ ] K4: Integration tests, exact-source builds/deployments, actual customer UI
  create/reload/edit/resource navigation and readiness/permission failure checks.
- [ ] K5: Documentation, cleanup and accurate external/review prerequisites.

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
- Public and authenticated kits, previews and resource links are implemented;
  hosted customer-journey qualification and exact-image rollout remain open.

### Explicit Boundaries

Creation prepares configurations, not a working AI answer before services and
knowledge have been connected. Simulator and customer-owned connections remain
clearly distinct. Existing releases, promotion, authorization, deployment
settings and advanced editors remain the source of truth. There is no kit-only
runtime, duplicated inbox, customer inventory database or credential store.

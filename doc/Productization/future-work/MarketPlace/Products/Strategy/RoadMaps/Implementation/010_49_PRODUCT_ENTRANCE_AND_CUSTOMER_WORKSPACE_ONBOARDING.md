# 010.49 Product Entrance And Customer Workspace Onboarding

Date: 2026-10-28

## Current Status

**Implemented, committed, deployed and hosted-verified on staging and production.**

- Production entrance: `https://console.loomai.pro/`; short Platform, Engage and
  Desk pages, authentication/start actions, no upper menu, light/dark mode.
- Backend source `6bd90580a8f411777b53df3e308670896a97b878`, native schema 217 on
  both control planes. UI source `6df8f2b32019ebdabd3875c2283951962cfdef52` on both
  frontends; all product routes and health returned 200, zero UI restarts after
  the route-directory correction. Existing domains/settings were preserved.
- Real Supabase signup email delivery, emailed confirmation and PKCE exchange
  passed. A fresh production identity signed in, explicitly created its workspace
  in the browser and opened the picker showing exactly the two selected starters.
- Both clean templates created owned `CONFIGURATION_ONLY` drafts through live
  customer APIs in both environments. No infrastructure, provider allowance or
  customer business database was created. Customer-owned provider/vector/storage
  and simulator/action bindings are still prerequisites for an actual deployment;
  this check does not claim an AI conversation without those bindings.
- Final public enrollment IDs: `public-dealership-simulator-v2` and
  `public-knowledge-workspace-v1`. Corrected template versions are 1.12.0 and
  1.2.0. The first failed staging-test offer grant was explicitly revoked through
  the normal audited API; no customer account or deployment data was rewritten.
- Control-plane backups were saved as private compressed PostgreSQL exports;
  archive integrity checked, restore not tested. No Coolify-wide backup, new
  database, new runtime release or framework release was required.
- Final sources are pushed to `Platform-V11`; image publication runs 40696394103
  and 40699096920 passed. Hosted receipts and screenshots are retained privately
  under `~/.codex/private/loomai-onboarding-01049/`. The local preview is stopped.
- The earlier local/publication checkpoints below are historical, not remaining
  rollout blockers. Follow-on customer configuration remains the normal existing
  deployment lifecycle, not a second onboarding implementation.

## Scope And Direction

Implement the owner-approved simple Console entrance: products and authentication,
no upper navigation menu. Each product has a short separate page, a clear purpose,
a representative visual, Back to products, and relevant demo/setup actions.
Keep the existing customer configuration and staff administration screens; this
is not another deployment lifecycle or a new authentication provider.

## Journey

1. Explore Platform, Engage or Desk without authentication. Demo links do not
   require a Console account; the dealership demonstration is explicitly simulated.
2. Get started preserves the selected product through sign-in or Supabase email
   registration and the existing PKCE verification callback.
3. A verified new identity explicitly creates a named customer workspace and owner
   membership. No infrastructure, AI usage, business database or shared provider
   credential is created or granted by registration.
4. Choose a deployment template from the existing customer setup workflow.
   Only operator-configured public bootstrap offers can be self-enrolled.
5. Existing deployments continue directly into Engage setup or human support.
   If no deployment exists, these products first explain and open that prerequisite.
6. Existing customers and staff retain their normal scopes, editing, publishing,
   verification and apply flows. Staff layout is not redesigned.

## Initial Template Selection

Owner decision, 2026-10-28: offer **Dealership simulator** and **Knowledge workspace**
to newly registered customers. Reuse the normal marketplace/template/bootstrap
mechanism, not a second deployment pipeline.

- `public-dealership-simulator-v2`: simulated dealership stock/discovery/actions,
  customer-owned approved policy documents, dealership Engage experience.
- `public-knowledge-workspace-v1`: generic document knowledge and generic Engage
  experience. No dealership vocabulary or integration is required.
- Neither starter contains Northfield's advertiser/account identity, provider
  credentials, document bucket, vector-service binding, Supabase project, existing
  customer assignment or funded canary allowance. Customers configure their own
  bindings before applying; registration does not grant provider spending.
- AI operational state may use managed PostgreSQL or customer PostgreSQL. No
  inventory, lead or other customer business database is provisioned. The existing
  external vector binding remains required for these durable starter shapes.
- Public-only Engage bootstrap uses the existing `none` identity profile. This
  does not make protected actions anonymous or skip runtime authorization.
  Customer sign-in can be configured separately; never inherit the demo project.
- Public document retrieval requires `publicationStatus=PUBLIC_APPROVED` in its
  source filter and explicit per-source approval. An install-form boolean is not
  a security boundary. The generic document UI must recognize this contract
  without dealership plugin-ID coupling. New template defaults do not pre-approve
  customer material.
- Publish new immutable marketplace versions and offers, then enable exactly
  those two offer IDs in backend configuration. This does not modify existing
  deployments, migrate data, create infrastructure or replace customer apps.

## Ownership And Security

- Reuse Supabase JWT verification and live verified-user checks. Reject anonymous,
  invalid and delegated Desk identities for workspace ownership.
- A token cannot choose a customer, role, owner ID or another identity. Identity
  binding is the immutable verified issuer/subject, not name or email matching.
- An email collision requires the existing dual-proof linking flow; never
  automatically join an existing company or grant staff privileges.
- Check active owner/customer membership on return. Revoked links cannot register
  a replacement workspace. The database uniqueness constraints bound concurrent
  registration; a collision rolls the entire transaction back and returns a
  visible conflict. A successful repeated request returns the original workspace.
- Workspace POST requires an explicitly trusted Console origin and a bearer token;
  local staff cookies or API keys alone cannot register a customer.
- GET/POST `/api/platform/auth/customer-onboarding` performs its own verified
  identity check before Console membership exists. No other API becomes public.
- POST `/api/customer-self-service/enrollment` requires the normal active customer
  scope, reads its offer list from server configuration, uses the existing grant/
  receipt/audit tables, and never reactivates or reassigns an existing entitlement.
- New public bootstrap access is not a timed trial. The existing required expiry
  field uses year 9999; explicit revocation and actual execution checks remain.
- Product selection is an explicit three-value UI routing contract, not AI text
  matching and not an arbitrary `next` URL. Desk OAuth and linking keep their own
  return paths and do not acquire Console membership merely by signing in.

## Configuration And Release Requirements

- Existing customer Supabase configuration and trusted Console origins are reused.
- `PLATFORM_CUSTOMER_ONBOARDING_ENABLED` defaults to `true` for creating only an
  isolated workspace; existing users remain usable when creation is disabled.
- `PLATFORM_CUSTOMER_ONBOARDING_TEMPLATE_OFFER_IDS` defaults to empty. Configure
  appropriate already-published public bootstrap offers explicitly. Do not use an
  existing customer's funded canary or private service bindings as public defaults.
- Empty availability is shown explicitly. There is no private-template fallback,
  fabricated successful setup, automatic deployment or new provider allowance.
- V217 adds `ENROLL` to the existing entitlement receipt command constraint;
  it creates no tables. No framework release, runtime image or customer business
  storage is required. Release the control-plane backend before the matching UI.

## Initial Local Qualification

- Implemented: public product entrance and three detail pages, dark mode, basic
  mobile layout; auth forms, product-preserving callback, explicit workspace
  creation, setup entry, and configured public-template enrollment.
- Final local qualification: TypeScript/Vite build passed; 37 focused backend
  tests passed; 8 auth unit checks passed; 19 browser auth/product/onboarding
  scenarios passed; 15 existing customer scope scenarios passed. Public pages
  remain available during a backend outage; empty template availability is
  explicit. V217 and non-empty database-backed enrollment, including concurrent
  requests, passed. This database proof uses H2 PostgreSQL mode, not native
  PostgreSQL. Existing Vite bundle-size warning remains, not a test failure.
- Independent review caught that the initial new command was absent from the
  existing database constraint. The forward V217 migration and actual-SQL-backed
  enrollment regression address it; mock-only enrollment is not sufficient proof.
  Bounded re-review found no additional blocker.
- Actual production build inspected at `http://127.0.0.1:5187/`: product links,
  return navigation, light/dark toggle, loaded vehicle bitmap and no desktop
  horizontal overflow verified. Public dealership demo URL returned HTTP 200.
  The preview is presentation only without a configured local backend/provider.
  Simultaneous Vite test servers caused stale development theme modules; a clean
  production build verified correct theme behavior. No theme workaround added.
- Hosted rollout, native signup email delivery for this change, and publication/
  enablement of public bootstrap offers are **not** proven by local fixtures.
  Do not advertise a fully live signup-to-deployment journey until those are done.

## Evidence Commands

Backend: `mvn -q -Dtest=CustomerPublicEnrollmentIntegrationTest,CustomerSupabaseAuthIntegrationTest,CustomerOfferEntitlementServiceTest,CustomerSupabaseAuthControllerTest,PlatformSessionAuthIntegrationTest test`

Frontend commands are recorded in `Platfrom/ui/README.md`. Browser tests use the
real Supabase SDK against synthetic HTTP boundaries, not a hosted provider. They
cover signup/PKCE return, workspace creation, existing setup handoff and no automatic
infrastructure writes, together with retained Desk consent and staff/linking paths.

## Public Starter Qualification, 2026-10-28

- Published both clean offers through normal staff marketplace/offer APIs on
  staging and production. Dealership template is `1.11.0`; generic knowledge
  template and its public-approved document DATA plugin are `1.2.0`.
- Existing customer offers, deployments, app assignments and provider allowances
  were not changed. Publication itself created no infrastructure. Registered the
  already-qualified runtime source `63888284e1fa36dfe9fe013578f026bd2d9e9ea0`
  in staging's artifact catalogue, without deploying a runtime or releasing the
  framework.
- The document UI now derives approved anonymous document handling from the
  selected published source contract, not a dealership plugin-ID list. Missing
  contract evidence prevents source registration, protecting the public/private
  audience boundary. It does not change semantic routing or AI validation.
- Latest checks passed: 38 backend checks including native PostgreSQL V217;
  7 starter contract checks; 7 generic/public document audience unit checks;
  10 document target-race and 9 customer document-authority browser scenarios;
  19 signup/auth/product browser scenarios; 8 auth unit checks; TypeScript/Vite
  production build. Dedicated test caches prevent parallel Vite fixtures from
  sharing development optimizer state.
- Hosted backend/UI activation and fresh-customer readback remain the next gate.
  Catalogue publication is not proof of a live signup-to-deployment journey.

### Hosted Findings And Corrections

- The native fresh-customer check exposed an inherited simulator template defect:
  mutable `MANAGED_*` external install defaults conflict with target-owned external
  bindings. The guard correctly returned `EXTERNAL_INTEGRATION_MUTABLE_INSTALL_BINDING_FORBIDDEN`.
  Publish simulator template `1.12.0` and offer `public-dealership-simulator-v2`
  without those defaults; normal binding initialization supplies the symbolic
  fields. Initial template `1.11.0`/offer v1 are not public enrollment defaults.
  This is a template correction, not a weakened guard or a framework workaround.
- The real Node production server exposed a route/asset collision: the new
  `/products` asset directory was streamed as a file when the SPA route was
  requested, causing `EISDIR`, process restarts and 502s. Static serving now checks
  `isFile`, leaves directories to the SPA, handles stream read errors and serves
  WebP with its correct MIME type. A regression starts the actual production
  server and checks all product routes plus an image and subsequent health.
  Vite preview alone was insufficient to detect this hosting-only defect.
- Native Supabase signup email was delivered to the owned test inbox and its
  actual verification link plus PKCE exchange passed. No administrator user
  confirmation or token-validation bypass was used.

### Console Brand Navigation, 2026-10-28

The public Console logo returns to `https://loomai.pro/` in the same tab.
The separate **Back to products** action stays within Console. UI revision
`c8d03af9b60064b50e57f776f147545ae4e33c80` is deployed and verified on staging
and production; all 19 signup/auth/product browser regressions and the production
build passed. Both live navigation paths were clicked in the production browser.
This UI-only change did not rebuild or redeploy the backend or customer runtimes.

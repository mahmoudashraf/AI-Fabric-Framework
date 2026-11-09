# Developer Entry Verification

Status: implemented, committed and verified live on staging and production.

## Scope

010.67 adds a dark developer public entry, reuses two existing Kits, persists a
typed staging-only environment policy, and retires the former staging-global
presentation profile. No account type, framework release, runtime AI change or
new product database is introduced.

## Local Evidence

- Backend: 303 tests across 24 focused classes passed, plus a 17-test focused
  follow-up covering valid staging apply as well as rejection. The latter
  overlaps the main suite and is not an additional independent 17-test total.
  PostgreSQL/H2 migration checks passed. The server guards cover apply,
  provisioning, dependency allocation, promotion, target mutation and consumer
  binding; clone/import/reset paths retain the canonical resource policy.
- Developer landing browser: 8 scenarios passed, including desktop/tablet/mobile
  (390/768/1440/1920), all eight dismissible capability previews, loaded bitmap
  media, canonical developer-host links, ordinary Console entry unchanged, and
  the selected Kit/developer intent surviving the unauthenticated setup route.
- Kit/auth/profile focused unit checks: 64 passed. A stale pre-existing sign-up
  link assertion inspected the public host rather than its existing KitChooser;
  the test now checks the actual shared component. No production link workaround.
- Production UI build passed. Existing nonmodule runtime-config script and
  bundle-size warnings remain. Generated build metadata is not a source change.
- Updates browser: 29 scenarios passed; staff/shared Kit setup browser: 14
  scenarios passed. Coverage includes staging updates, blocked production URLs,
  preserved ordinary production actions and exact developer creation payloads.
- Existing Brain/release unit group: 33 checks passed. The credential-free
  Support offer builder has 27 passing offline contract checks.
- Screenshots are ignored under `output/developer-entry/`.

## Published Sources And Rollout

- Backend: `6f5d59b7ac224e99c90dd510b9167c24a2c7f922`, image CI
  `42532865318` successful. Healthy exact-source containers and successful
  migration `238` verified natively on both hosts. Database dumps were taken
  before migration and retained privately on their respective servers.
- Frontend: `edfb370717d1e3eff6f4efcc1dc1666640ee2a92`, image CI
  `42533958728` successful, backend image correctly skipped. Staging job
  `m31wuljcne1nm12n4vz419ry` and production job `o5hpvsmo9injq4gndhoxjrai`
  reached healthy exact-source containers.
- Backend configuration-only restarts `laby2d79375t8n6jbvnv0cx8` and
  `s8agk6de0u70shwvlizgpexn` completed at the same backend source to enable the
  public Support starter. No framework or runtime image was rebuilt.
- `developer.loomai.pro` resolves to production `46.225.162.106` and serves valid
  HTTPS. Its obsolete staging route is removed. The unused `develop` hostname
  is not part of the implementation.

## Hosted Qualification

`hosted-public.browser.mjs` passed six checks covering:

- Developer layout at 1440, 768 and 390 pixels, decoded media, two starter
  actions, all eight open/close capability previews, no horizontal overflow and
  no captured page errors.
- Unchanged business entry on production Console and ordinary staging. The old
  `uiProfile` field is absent from both runtime configurations.
- Canonical Console sign-in redirect with the selected Kit/developer intent
  preserved. Developer links never create a second identity origin.

An existing genuinely email-verified Supabase fixture signed in through the
normal customer UI on both environments. Customer Experience and Support Kits
were created through the actual forms, including their branded Engage app
configuration. All four returned and retained:

```json
{"allowedEnvironments":["staging"],"tags":["staging-only"]}
```

The authenticated customer target list contained staging targets only. Promotion
planning and production-apply commands both returned HTTP 403 with
`DEPLOYMENT_STAGING_ONLY` using the actual consumer binding revision. No runtime
allocation was requested. A separate ordinary business control Kit retained
both allowed environments and exposed a production target. It was configuration
only and was removed; no serving business configuration was changed.

The existing test account also held an obsolete starter. The runner explicitly
selected the current public Knowledge offer rather than its invalid historical
offer. An incorrect probe URL and stale command revision were corrected in the
runner, not in application code. Setup used the existing Minimize support control
when the docked assistant overlapped its bottom controls; no forced clicks or
test-only hidden UI were used.

## Public Support Starter

`public-support-profile-20261108` reuses the published generic business Support
template `mkp-template-ai-support-workspace@1.2.0` and its existing Data/Action
plugins. Both server-controlled public enrollment lists retain current Knowledge
and Dealership entries and include this Support entry.

It has customer-scoped storage slots and optional managed/customer-provided AI
conversation state. It contains no LoomAI internal support documents, customer
credentials, funding, business database, preassigned agent or active Desk binding.
Tickets/handover remain off in the starter until the customer configures them.

## Scope And Cleanup

All configuration-only Kit/app/consumer/Brain fixtures from hosted qualification
are removed through supported APIs; deletion receipts are recorded with the
final working-context entry. Existing serving deployments, indexed sources and
app assignments were not replaced. Private auth and per-run receipts stay in
the ignored private workspace; screenshots/reports stay under `output/`.

Do not equate a configuration-only Kit save with runtime provisioning, source
indexing, AI answer quality or a full Platform release gate. Existing indexed
customer data, active app assignments and serving runtime configurations are
outside this implementation's changes.

The one-year developer offer is presentation of developer platform access, not
a new automatic billing/expiry engine or unlimited provider/infrastructure credit.
Existing credentials, usage accounting, quotas and readiness checks still apply.

Do not roll back the backend to a version lacking the policy guard while
developer resources exist without first restricting their infrastructure access.
The public landing alone may be reverted independently of that server boundary.

## Visual Explorers Follow-Up

The developer page's logos are noninteractive branding. The eight capability
cards now open named, icon-led previews. Five configuration-editor model providers
and six search engines match the existing provider editor; embedding providers
are shown separately. Provider-family names are not a promise that every model
ID is enabled for every account. Icons are self-hosted with pinned upstream
revisions and license files in `Platfrom/ui/public/developer/providers/`.

Web app, Mobile app and Backend targets and all four start steps are clickable:

- Web: embedded workspace illustration and the existing one-script installer.
- Mobile: mobile-friendly standalone/web-view delivery and the option of the
  customer's own native interface, not a new native SDK.
- Backend: copyable canonical conversation-message request with runtime token,
  conversation ID and idempotency-key explanations. It is conditional on enabled
  conversation control and does not replace published route discovery.
- Choose a template: the two real developer starters, with icons, names and
  developer-intent setup links.
- Connect your tools: S3 documents, HTTP JSON sources, action endpoints and Desk.
- Test your Brain: an explicitly illustrative test plus evidence/action/follow-up
  checks, with no actual runtime request.
- Use it in your app: the same three integration previews, not duplicate guides.

The primary action's theme variables now also scope to the dialog portal,
correcting its earlier dark-on-dark appearance. Dialog screenshots wait for
transition completion rather than capturing a partially faded panel.

Local qualification: 12 browser scenarios passed, including all 15 panels on
mobile, both existing starter paths, named/decoded provider icons, readable
portal actions, clipboard contents and zero configuration commands. TypeScript
and the production build passed, with only existing build warnings.

Current follow-up source: `ef1578444b3860e855c2f3ee7c4cd14f3bb573d5`.
Image CI `42538922362` passed with backend skipped. Staging UI job
`f9zjxvgc3mx50zp0ei7ojp5z` and production job `wqgyeqb0vpl8p9ituaqcnpbl`
finished; healthy exact-source containers were verified natively on both hosts.

The hosted runner passed six top-level checks: all eight capability, three
integration and four setup previews at 1440/768/390 pixels, loaded named model
icons, the backend example, readable portal actions, noninteractive logos and
unchanged ordinary Console/staging root pages. Canonical sign-in redirects retain
the selected developer Kit. No captured page errors or horizontal overflow.
Screenshots and the structured report are under `output/developer-entry/hosted/`.
No customer configuration, runtime, backend or AI decision behavior changed.

## Remembered Developer Console Discovery

The November 9 follow-up reuses the two developer starters on Home, My kits,
discovery and empty Brain lists. General sign-in/Open Console links and email
callbacks retain typed intent without a selected Kit. Browser preference is
scoped to customer + actor, not shared across unrelated users. The user can
switch to business kits. Existing resources remain visible; authorization and
allowed environments stay server-owned.

Local checks:

```sh
cd Platfrom/ui
node --test scripts/developer-entry.test.mjs scripts/customer-supabase-auth.test.mjs scripts/ui-profile.test.mjs
node scripts/developer-landing.browser.mjs
node scripts/developer-console.browser.mjs
npm run build
```

The focused unit group passed 31 tests, the landing runner passed 12 scenarios
and the new Console runner passed seven: general sign-in, navigation/reload,
business switching, different actors, ordinary discovery, empty Brain lists and
mobile setup links. The Console runner uses a mocked identity exchange and
read-only API fixtures, not hosted login/provisioning. It minimizes the existing
support dock through its real control; no forced clicks or hidden UI.

Ignored screenshots: `output/developer-entry/console/`. Production build passed
with existing runtime-config/large-bundle warnings. The combined focused
unit/Kit/staff/product/navigation group subsequently passed all 93 checks, and
the seven Console browser scenarios passed again before rollout.

### Hosted Continuity Rollout - 9 November 2026

Frontend source `04e5f653de3ea31aace2f9dbeeb96bbee04edf18` is committed, pushed
and deployed to both environments. Image CI `42632868209` passed with backend
correctly skipped. Staging deployment `xxbop1ne189h8ummlpvu4a85` and production
deployment `avqoq6dl01hx1nivdocytjek` finished. Native container inspection
confirmed healthy containers with that exact source label and image tag.

The new `hosted-console.browser.mjs` uses a private verified customer's real
email/password through the ordinary sign-in form; it mocks no identity/API
requests and creates no resources. Supply `DEVELOPER_LOGIN_FIXTURE` pointing to
a private JSON file with `email` and `password`; never commit that fixture.

Both environments passed five grouped hosted checks:

- General developer sign-in reaches the two developer starters without a Kit
  preselected. Production starts by clicking Sign in on the developer hostname.
- Home, My kits, discovery and reload retain the developer cards.
- Explicit business switching restores all three business cards, survives reload
  and can return to developer starters.
- At 390 pixels the starter images load, the document has no horizontal overflow
  and Support setup retains typed developer intent and staging-only wording.
- Sign-out prevents reopening Home as an authenticated customer.

Before/after Kit IDs were unchanged. This fixture currently has zero Kits; these
checks do not claim a new nonempty-portfolio qualification or runtime provisioning.
No AI query, infrastructure allocation or backend/runtime update was performed.
Reports and reviewed desktop/mobile screenshots are ignored under
`output/developer-entry/hosted-console/`.

An initial production run began before Coolify completed its route handover and
loaded the old bundle (untyped sign-in). After the deployment reported finished,
a fresh-browser run passed without changing code or weakening assertions.

The public runner also passed seven grouped checks after rollout: all 15 visual
explorers and loaded media at 1440/768/390 pixels, canonical selected-Kit and
general developer sign-in redirects, and unchanged fresh-browser business entry
on production/staging. No page errors or horizontal overflow were captured.
The mobile header deliberately hides its secondary Sign in link; the runner
inspects that link's target in the DOM and checks the visible Open Console target.
This was a runner selector correction, not an application change. Public report
and screenshots remain under `output/developer-entry/hosted/`.

## Shared Public Homepage Preference - 9 November 2026

The developer landing and explicit Console discovery choices now set a shared
Secure/SameSite=Lax presentation cookie (`DEVELOPER`/`CUSTOMER` only, one year).
The public homepage sends remembered developer visitors to the existing developer
landing before rendering; direct business subpages are unchanged. Fresh visitors
keep the business homepage. Explicit business choice resets the shared preference.
Signed-out Console discovery also reads it; an unrelated signed-in account does
not inherit it. This cookie contains no token or identity and never grants access.

This is website presentation, not the paid-production upgrade flow. No backend
business API, runtime, resource policy, billing or payment change is included.

Local checks passed: 32 focused auth/entry/profile checks, 13 landing browser
scenarios, eight Console browser scenarios, actual homepage HTTP routing tests,
TypeScript/production UI build and Astro check/build/static smoke (28 routes).
Both build paths retain only the existing Console bundle/runtime-config warnings.
Hosted delivery for this follow-up is pending; record exact sources after rollout.

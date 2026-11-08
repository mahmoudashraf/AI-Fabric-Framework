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

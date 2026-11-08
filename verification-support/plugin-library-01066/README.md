# Private Data Plugin Library - 010.66

Date: 8 November 2026.

## Scope

Existing `DATA` manifests and the existing marketplace installer/compiler are
reused. No new plugin type, runtime, framework release, customer business
database, AI decision rule or deployment lifecycle was added.

The implementation adds workspace-owned private drafts, immutable versions,
archive, usage readback, ownership enforcement, revision checks and a focused UI:

- Brain Knowledge/Capabilities -> recommended/private/all picker.
- Three-step Brain installation with one existing Brain save footer.
- `/library/plugins` -> private library.
- `/library/plugins/new/data` and `/:id/edit` -> six-step creator.
- `/:id/versions/:version` -> immutable source/form view.

Only qualified HTTP JSON and bound S3 document contracts become usable private
versions. SQL is not a customer integration today: its existing loader uses
Platform's demo JDBC connection. Private SQL, server folders, packaged resources
and unowned storage are rejected at the shared execution/manifest boundary.
Unsupported source may remain in a private editable draft. Public staff/demo
publishing remains unchanged.

## Verification

- 52 final focused UI unit/contract checks passed: creator field fidelity,
  prototype-shaped own properties, exact numbers, atomic version request,
  authentication return, capability projection and developer profiles.
- The wider affected customer API/auth regression run passed 112 checks before
  the final additional prototype-key regression was added. These counts overlap;
  they are not an aggregate unique-test count.
- `node scripts/brain-capabilities.browser.mjs`: 22 passing browser scenarios,
  including catalogue families/demo/private filtering, install saves, conflicts,
  lifecycle controls, read-only access and retained action/webhook tools.
- `node scripts/plugin-library.browser.mjs`: 10 passing browser scenarios,
  including author/check/version/use-in-Brain, invalid source retention,
  incomplete numbers across steps, failed saves, immutable versions and
  1440x900/1280x720/390x844 light/dark layout checks.
- TypeScript and the production build passed. Existing Vite chunk-size and
  non-module runtime-config warnings remain. The npm audit reports three
  pre-existing high-severity dependency advisories; no broad dependency upgrade
  was attempted in this feature.
- Backend focused tests include real PostgreSQL migration/locking/ownership,
  compiler, bundle import/export, publisher and dataset-sync regressions. Final
  migration qualification is recorded below when complete.
- Real local full-stack runner passed staff sign-in -> private bound-S3
  definition -> check -> immutable version -> persisted API readback. It sent
  only definition create/check/version commands, no release/index/provider query.

The browser fixture scripts use real UI components and the real API client with
in-memory HTTP fixtures. They do not prove hosted connectivity, AI answer quality,
document indexing or a running deployment. Backend integration tests independently
exercise real Spring services and persistence.

`verify-local-journey.mjs` is a separate loopback-only runner for the actual UI and
Spring Boot backend. It creates disposable local records and checks persisted
readback, not mock success. It cannot target external hosts.

Generated screenshots are under `output/plugin-library-implementation/` and are
ignored. Their sample names are fictional.

## Migration Qualification

Read-only hosted preflight found production at migration 235 and staging already
at the rejected implementation's original migration 236. Git rollback did not
roll back its database. Preserve that exact 236 migration; the new schema must
be a forward-only 237 migration. Do not repair checksums, rewrite history or
delete either database. A single uninstalled old private ACTION test definition
is not a reason to revive rejected private Action authoring.

The original V236 has been restored byte-for-byte; V237 contains the new schema,
retains source/revision/owner/version data and archives unsupported old private
types. No rejected application/UI implementation was restored. Final migration
and private-lifecycle rerun: 33 tests passed with no skips, including production's
235 and staging's 236 baselines. Generated build resources were cleaned to remove
the superseded V236 filename.

The earlier focused backend suite passed 90 tests, including 14 PostgreSQL
integration cases. A final bundle-focused rerun passed 19 cases. These runs
overlap with the final migration run; do not sum them as unique coverage.

Independent review identified and corrected clone destination-scope confusion,
explicit source-handle overrides and an empty draft on new private definition
import. The final bundle/seed regression rerun passed 31 tests, including those
additional cases. Existing private working drafts are not overwritten by import.

One wider marketplace regression remains: the existing public handover/template
upgrade case receives 409 instead of 200. Its prerequisite path is unchanged
from the reset baseline. The broader suite is 19/20, not green. This feature did
not change framework/runtime code to hide that result.

## Current Delivery Status

Implementation is locally qualified and ready for hosted qualification; no new
hosted image has been applied yet. Live preflight was read-only. New source indexing, answer
quality and promotion against an applied private-plugin release are not claimed.
The existing storage/indexing/release controllers are reused, not replaced.

## Operational Boundaries

Definition save/check/version and Brain draft save do not call the provider,
index data, create a release or change live app assignment. Storage binding,
content approval, source reconciliation and publish/apply/verify remain their
existing separate workflows. There is no fake Test connection command.

New and reused installation writes compare the expected timestamp under the
deployment database lock. Private definition writes compare a revision under the
plugin lock. Unknown configuration fields, nested arrays, explicit nulls and
exact numeric values are retained. Invalid source is never replaced by defaults.

The 010.65 rejected global customer redesign and unrelated dirty files remain
out of scope. Current publication/deployment status must be recorded explicitly;
local code/build success is not proof of a hosted rollout.

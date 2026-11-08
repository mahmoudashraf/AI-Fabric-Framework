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
The subsequent owner/lifecycle recheck passed 29 tests across H2 and PostgreSQL
with the final handle-binding rule enabled.

One wider marketplace regression remains: the existing public handover/template
upgrade case receives 409 instead of 200. Its prerequisite path is unchanged
from the reset baseline. The broader suite is 19/20, not green. This feature did
not change framework/runtime code to hide that result.

## Current Delivery Status

Source `ad02bbe550015b93f34d67f8701387ab720d7586` is committed and pushed on
`Platform-V11`. Image CI `42515037591` passed for backend and UI. Both services
are deployed on staging and production with exact-source healthy containers.
Both databases report `237:true`; database dumps were completed before rollout.

- Staging backend job: `kkxtjoxeh3ebyn5aw0r8o5vs`.
- Staging UI job: `n6irbcjc8b42sq2okpycfwkw`.
- Production backend job: `hur628qw6ifo3qitebtjwk65`.
- Production UI job: `y95sw8dx1dmgpycnt7btpbsi`.
- Uncached runtime configuration confirms staging/developer and
  production/standard presentation profiles.

Real hosted browser journeys passed on both environments: authorized staff
sign-in, create/check/version a private S3 definition, use the existing three-step
installer, save and read the compiled Brain draft, verify saved/applied usage,
and deny a mismatched customer workspace. No captured page errors. Customer-role
and delegated-staff authority are independently covered by the integration tests;
the hosted runner did not use the owner's personal identity.

Qualification used disposable, configuration-only Brains with an existing
durable-vector template contract. No vector service or runtime was provisioned,
no release was published/applied and no provider query or indexing command ran.
Lucene scratch configuration correctly rejected the document install; the runner
was corrected to use a compatible fixture, not to bypass that contract. Earlier
runner failures also corrected its own route/locator/wait assumptions, not the
application. All five test Brains were deleted successfully. Private test
definitions are archived, with no saved or applied installations retained.

The feature is live. New-source indexing, AI answer quality and staging-to-
production application of a private-plugin release remain unrun qualification
work, not claimed as passed. Existing storage/indexing/release controllers are
reused, not replaced. No existing customer Brain release, app assignment, prompt,
provider credential or permission was changed.

Future rollback must preserve private ownership enforcement. Do not deploy a
pre-library catalogue reader against a database containing private definitions;
use a verified forward fix, or a deliberately coordinated database/image restore.
Resetting Git alone does not roll back the schema or its private data.

## Operational Boundaries

### Required Fields And Real-Plugin Recreation - Verified Live

Creator fields are marked Required/Optional. The step rail counts missing inputs;
Review links return to and focus those fields. Incomplete drafts can still be
saved, including Save draft & check. Reusable-version creation runs the existing
server check before the atomic version command. Installer storage/text/choice/
boolean controls share requiredness and inline feedback; required booleans accept
explicit false. Separate affirmative approval contracts remain enforced. No AI
semantic validator, new plugin type or backend/schema change was introduced.

New definitions can start from an available Data plugin version. The complete
manifest is preserved except the new identity, copy name and initial version;
no connection instances, secret values or data are copied. Unsupported private
source authority remains visibly unavailable. The current production catalogue
contains bound-document and HTTP stock sources plus legacy packaged/folder/SQL
entries; do not claim every historical source is customer-authorable.

`recreate-through-ui.mjs` is the repeatable hosted verification runner. It needs
`PLUGIN_UI_ORIGIN`, `PLUGIN_API_ORIGIN`, `PLUGIN_STAFF_API_KEY`,
`PLUGIN_CUSTOMER_ID`, `PLUGIN_TEST_LABEL` and explicit
`PLUGIN_ALLOW_TEST_COPIES=true`. Use a dedicated authorized test customer scope;
never put credentials in command arguments or tracked files. It recreates
supported public Data definitions through the real form, checks saved-version
contract equality and immutable UI, verifies unsupported selections are
disabled, and archives only its own copies. Output is ignored under
`output/plugin-form-recreation/`.

UI source `51598facb9d96110975cf083134edc947d8550a3` is committed/pushed.
Image CI `42526898728` passed, with backend skipped. Staging job
`x36uwcs6ly77r9xm79ha9kte` and production job `uko8wcf3i7j5srpaodmnqghp`
finished; exact-source native UI containers are healthy. Focused checks passed:
59 unit tests, 17 library browser scenarios, 33 installer/capability browser
scenarios, TypeScript and production build. Existing runtime-config script and
bundle-size build warnings remain. Independent review found and fixed a path
that skipped Save draft & check on unfinished input; a regression covers it.

Real hosted recreation passed for all eight supported staging definitions and
all five production definitions. Each complete saved manifest matched its source
except the separate identity, copy name and initial version. The tests changed
and restored actual source/mapping/freshness/setup fields, checked immutable
version UI, and archived all 13 copies. Original plugins and installations were
unchanged. Eight legacy SQL/folder/packaged entries in each environment were
correctly unavailable, not claimed as newly authorable. Desktop/mobile checks
passed with no captured page errors. Safe receipts and screenshots are in the
ignored staging/production `output/plugin-form-recreation/` folders.

A separate from-scratch S3 definition and installer journey passed on staging
and production: create/check/version, configure the existing storage input,
review, save to a compatible configuration-only Brain, and read back the install
and compiled draft. Each had one saved and zero applied uses; cross-workspace
read returned 404. Test definitions are archived, installs removed, and fixture
deletions `del-6f5b5044` (staging) and `del-42c9374d` (production) succeeded.
No runtime, vector service, release, provider query or indexing job was created.

The first recreation attempt reloaded the page after API-key sign-in and lost
its intentionally in-memory staff session, before writing any record. The runner
now follows the real SPA navigation links; no authentication workaround or app
change was needed. Read-only field-guidance checks also pass against the final
source, preserving staging/developer and production/standard profiles. Private-
plugin indexing, answer quality and release application remain separate,
unrun qualification; these authoring results are not a full Platform gate.

### Field Guidance Follow-Up - 8 November 2026

The creator now explains setup field names versus real connection values, uses
safe example placeholders, and shows a collapsible HTTP-response/mapping
example. Mapping columns name the JSON path and indexed field explicitly.
Starter refresh, page and document limits include units and their effect.
Setup inputs explain what the eventual installer sees; new definitions include
editable installation descriptions. Storage and boolean controls also display
their declared help. Example URLs, IDs and mappings are never saved as customer
connection defaults, and existing definitions are not backfilled or overwritten.

Focused unit checks pass (53); library browser checks pass (13), including
no mutation while navigating examples and preservation of existing definitions.
Capability browser checks pass (23), including installation URL placeholders and
declared boolean help. Production build passes, with the existing runtime-config
script and bundle-size warnings. Expanded-help checks also cover bounded desktop
scrolling and mobile overflow: off-screen table labels are anchored within their
headers, rather than contributing an empty area below the editor.

Initial source `6c5781c9b7351394d8ef21a6ef3faeaad21f05fe`; final UI source
`1cdb0992556d233f6b8b804141f3f543571ba275`. Both are committed and pushed on
`Platform-V11`. Final image CI `42521529897` passed, with backend skipped.
Staging UI job `homna78rruhih6ud92rt385p` and production UI job
`cyhfdnjwmgge92mq09yxmrw5` finished; both exact-source native containers are
healthy. Backend source and database migrations were not changed.

Read-only live browser checks pass on both environments: authorized staff entry,
field hints and starter values, token/page/cursor variants, HTTP response example,
S3 limits, editable installer descriptions and desktop/mobile layout. No captured
page errors or configuration writes; no test definitions, Brains, installations,
indexing commands or releases were created. Staging remains developer and
production standard. Screenshots are ignored under
`output/plugin-library-implementation/*-guidance-*.png`.

The first staging login probe ran during replacement and timed out before the
login control appeared. Checks after the completed rollout passed; no auth code
was changed. This is UI-only work, not new indexing/runtime qualification.

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

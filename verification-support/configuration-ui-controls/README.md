# Configuration UI Controls - 22 November 2026

## Scope

Ten existing executor retrieval/read-planning properties now have typed controls
in Knowledge > Search settings > Answers & sources. No backend, framework,
authorization, template, model or runtime behavior change is part of this release.
See `Final_Documentation/Development_Guides/SMART_BRAIN_CONFIGURATION_FORM_COVERAGE.md`
for the audit, canonical owners, exact properties and expert-payload limits.

The optional Provider configuration JSON editor remains available. Both scoped
and legacy provider saves use a field-delta merge, avoiding unrelated defaults
and normalization. Unknown saved enum values remain visible; absent/null values
are inherited without an opening-time save. Mobile choices use shorter labels
and keep the selected task visible in the horizontal rail.

## Automated Verification

- `node --test scripts/brain-settings.test.mjs`: 32 PASS, zero failures/skips.
  Actual React editor, draft-save wrapper, mapper preservation, all new controls,
  no-op, absent/null/custom values, concurrent unrelated edits, invalid numeric
  input, discard, read-only/failed save and 1280/390/320px light/dark coverage.
- `npm run build`: PASS. Existing runtime-config script and bundle-size warnings
  remain; no new TypeScript/build failure.
- Backend unchanged-contract regression: 173 PASS, zero failures/skips across
  DeploymentDraftValidationServiceTest (119) and RailwayProvisioningPlanServiceTest
  (54). Existing validation bounds and environment generation are retained.
- Screenshots inspected locally and on the production page. An initial phone
  review caught long option truncation and an off-screen selected task; a separate
  UI-only polish commit corrects these and adds a visibility assertion.

## Initial Live Qualification

Feature source `1bc144fc0889e6e81b2e1869edb67089268ca03b`, CI 45026656056
SUCCESS; UI image published, backend job correctly skipped. UI deployed in order:
staging `mjoo9twkdjoseoskwnskuuaq`, production `gptaj48p14tjlft4rtzueykv`.
Both native UI containers were healthy, zero restarts; environment, mounts,
resource limits, domains and port bindings preserved.

Actual normal-session Playwright form checks passed on BOTH control planes:

- Staging Northfield `dep-f023c863` and production Harbor `dep-86ad8f93`.
- All three RAG modes and inheritance saved, reloaded and checked through the
  normal draft API. All ten controls exercised; numeric rejection and discard
  verified. Zero saves from opening the form.
- Six draft saves per Brain, including optional JSON-editor restoration. Original
  provider objects restored exactly, not approximately/default-normalized; every
  other configuration object unchanged. No release publication or application.
- Desktop 1440px and phone 390px, no document overflow or browser page errors.
- Harbor app, exact release assignment and configuration unchanged.
- Backend source `abc04c00e1aee5c9b69e0f72ed062503b557955c`, framework 0.8.62,
  schema 241 and Harbor runtime/connector images/containers unchanged.
- Both owned staff sessions revoked; their old cookies proved unauthenticated.

Private evidence: `~/.codex/private/configuration-ui-coverage-20261122/`.
No raw credentials, session cookies or environment dumps are committed.

## Final Mobile Refinement

UI source `5404944ba7f2d59cbb6af3fd584972df35ecfce2` adds shorter display labels
without changing any enum/configuration value, and automatically reveals the
selected horizontal search task. Local 32-test suite and production build PASS.
Final CI 45027515729 SUCCESS (UI published, backend skipped). Deployed in order:
staging `vo7spv97g510361u9jw3qfip`, production `kvj4uz13wmni0182t5mmv4uu`.
Both native UI images are the exact source above, digest
`sha256:304f8a7742c418625ef34041803aff8090a9f22dbe85c640b93bd4b5efc5c6c6`,
healthy with zero restarts and unchanged infrastructure settings.

The complete live form suite passed again on BOTH environments on this final
image: three RAG modes, inheritance, all ten controls, save/reload, invalid-input
blocking, discard and exact original configuration restoration. Six saves per
Brain for this qualification, no publish/apply and no changed release or Harbor
installation. Explicit 390px assertions now prove both the selected rail item
is visible and the RAG selection fits without truncation. Final phone screenshot
visually inspected. No overflow or browser page errors.

Final native checks confirm both backend containers, Harbor runtime/connector,
framework 0.8.62 and schema 241 unchanged. Both task-owned staff sessions revoked;
old cookie-only requests unauthenticated. No remaining task process or login.
Final private proof and screenshots:
`~/.codex/private/configuration-ui-coverage-20261122-polish/`.

Only these supported retrieval/read-planning form gaps are claimed closed.
Arbitrary nested schemas, resolver objects and transport/projection payloads
retain their existing expert JSON or AI-assisted paths as documented in the
coverage guide. This is not a claim of a visual editor for every JSON shape.

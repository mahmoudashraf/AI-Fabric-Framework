# Smart Brain Settings Navigation

Owner-approved correction of the orphaned Technical settings page, 8 November 2026.
This is a scoped 010.64 follow-up, not the rejected 010.65 customer-wide redesign.

## One Home Per Task

- AI models: direct Brain sidebar entry, reusing Models & fallback / Purpose overrides.
- Knowledge / Search settings: embedding/vector form and the single existing-vector
  binding control. Document files remain under Storage & uploads.
- AI capabilities: action access/requirements link, existing HTTP routing and
  Webhooks & deliveries entry. The existing active-runtime delivery scope is retained.
- Advanced: pinned sidebar entry with addressable Runtime connections, Provider
  parameters, Recovery policy, Provider checks and Configuration JSON children.
  No second technical tab navigation is mounted inside those child pages.
- Old Settings URLs redirect to those owners with exact Brain, target and release.
  Unknown old settings remain explicit errors, not guessed destinations.

Existing provider/action controllers, permission checks, focused lossless draft
merging, explicit recovery policy and release workflow are unchanged. No backend,
framework, schema, AI prompt, routing policy or customer configuration changes.
Staff fleet tools and staging developer presentation remain separate.

## Verification

From `Platfrom/ui`:

```sh
node --test scripts/brain-workspace.test.mjs scripts/brain-settings.test.mjs scripts/brain-capabilities.test.mjs scripts/brain-scoped-operations.test.mjs scripts/brain-change-preview.test.mjs
node --test scripts/brain-action-reviews.test.mjs scripts/deployment-workspace-navigation.test.mjs scripts/customer-existing-vector-binding.test.mjs scripts/customer-document-storage-scope.test.mjs scripts/ui-profile.test.mjs scripts/brain-management.test.mjs scripts/brain-release-journey.test.mjs
node --test scripts/customer-existing-vector-binding-ui.test.mjs
node scripts/brain-settings-navigation.browser.mjs
node scripts/brain-knowledge.browser.mjs
node scripts/brain-capabilities.browser.mjs
npm run build
```

First suite: 60 passing checks, including 10 provider-save/browser journeys.
Second suite: 44 passing checks. Knowledge/reviews browser: 13 journeys.
Existing-vector browser suite: 25 passing checks.
Capabilities browser: 21 journeys. Full-shell settings browser covers every
Advanced child, old redirects, retained target/release, JSON invalid/dirty guard,
save preservation, document/search separation, one vector editor, action panel
entrances, short desktop pinned navigation, light/dark, basic mobile and read-only.
Independent review caught the unsaved provider-probe workflow. Compatible
structured Advanced children now retain the same mounted editor and buffer;
the existing Probe current edits command receives that buffer without saving.
Only editor-declared sibling paths with unchanged target/release query may retain
it. Busy operations, leaving Advanced and switching into/out of dirty raw JSON
remain guarded. The full-shell browser proves probe payload, no save, returning
to preserved inputs and guarded exit/raw-JSON transitions.
Provider/action writes are memory-only fixtures, never hosted configuration.

Build passes with the existing bundle-size and runtime-config warnings.
Local screenshots are in `local/`.

## Hosted Closeout

Source `1cb4b5b899eda855786c5c4adbcdf7e9713a624d`, branch `Platform-V11`.
UI image CI `42475630404` passed; backend skipped.

- Staging job `o5s6mba2b5zef3kltupx6uvk` finished. Exact-source container
  `/u67gepsowf7oo90dwjdx018r-112704818235` is healthy.
- Production job `scxzezczxt528tzncf2ab4pj` finished. Exact-source container
  `/a6fz2ncuzzulg6lrzzwrjshu-112919465951` is healthy.
- Staging developer and production standard runtime profiles retained.
- Owned Support fixtures on both hosts passed the legacy Settings redirect,
  Models, Knowledge search and every Advanced child with the exact target retained.
  Search has one vector-binding editor, separate from document storage.
- Production Webhooks & deliveries opens the existing webhook policy editor.
  Light/dark 1280x720 desktop checked without outer page overflow. Staging 390px
  mobile drawer opens, selects a child and closes without horizontal overflow.
- No captured browser errors. No hosted configuration save, binding, source
  mutation, vendor-probe command, release/app assignment or AI query was submitted.
  Save/probe preservation and write permission tests used memory fixtures.
- Test sessions signed out, temporary tabs closed and viewport reset. The existing
  local read-only preview server was left running.

Screenshots: `live/`. UI rollback uses image
`d343ca48a9026b747d18d581a5ef5dee309db5c9`; no backend/database rollback required.

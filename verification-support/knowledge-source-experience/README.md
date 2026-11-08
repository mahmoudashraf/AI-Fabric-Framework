# Knowledge Views And Focused Search Setup

Owner-approved follow-up to 010.64, 8 November 2026. UI only; no backend,
framework, retrieval decision, provider default, draft schema or live setting change.

## Customer Experience

- All sources, Documents and Live data are filters over one source workspace,
  with distinct headings, relevant primary actions, source counts and guidance.
- Counts describe source records, not files in storage, chunks or indexed business
  records. Unknown/loading/failed/undeployed inventories never report zero.
  A failed source kind does not masquerade as an empty successful read.
  Before an applied target is resolved, inventory is labelled unavailable, not
  falsely diagnosed as an undeployed environment.
- Search filtering keeps total source counts; Clear search restores the list.
  Empty/loading lists have no misleading zero-result pagination.
- Search settings is a bounded task workspace: Search service, Embedding model,
  Service connection and Customer separation. One focused form is shown at a time;
  long connection details scroll within the task, not the whole desktop page.
- Existing provider controls, all six vector engines and all embedding choices,
  field mapping, permissions and one Save/Discard footer remain. One provider
  buffer survives section changes; the single external binding editor also stays
  mounted so a section switch cannot discard its unsent connection inputs.
- File storage/uploads stay separate. These controls do not upload, index or
  apply a runtime change. Staff global provider layout and developer profile remain.

## Local Verification

From `Platfrom/ui`:

```sh
node --test scripts/brain-settings.test.mjs scripts/brain-scoped-operations.test.mjs scripts/brain-workspace.test.mjs scripts/customer-existing-vector-binding.test.mjs scripts/ui-profile.test.mjs
node scripts/brain-knowledge.browser.mjs
BRAIN_SETTINGS_EVIDENCE_DIR=../../verification-support/knowledge-source-experience/local node scripts/brain-settings-navigation.browser.mjs
npm run build
```

46 focused checks passed, including ten provider browser workflows. Existing
storage save test now traverses all four sections before saving and verifies exact
embedding/connection changes with unknown configuration retained. All six search
engines and remote/local connection views remain reachable. Permissions, failure,
raw JSON, probes and staging developer presentation checks are retained.

Source browser covers accurate counts, three view headings/filters, distinct empty
states, failed/loading/unconfigured/undeployed inventories, search clearing, and
the retained document/sync/review/mobile flows. Full-shell browser covers the
four search tasks, retained binding inputs, one vector editor, dirty/save guards,
light/dark 1280x720 layouts with bounded desktop scrolling, and 390px layouts
without horizontal page overflow.

Independent review caught initial default values appearing as saved search
configuration before loading or on failure. The summary and local-engine endpoint
claim now require a loaded draft. A failed-read browser regression verifies that
defaults are not presented as the customer's configuration and Save is disabled.

All test writes/probes use browser-memory fixtures. Read-only local actual-data
review also checked 13 document sources / zero live sources and the different
document/live-data presentations. Screenshots are in `local/`.

Build passes with the existing bundle-size/runtime-config warnings.

## Hosted Verification

Implemented in `aa43751b92560db506a1e257ee63756513f2266f`, with the
initial inventory-wording correction in final source
`76b70713cb72fd8d6c131fa8e69df1fc5e5be25c` on `Platform-V11`.
Image CI `42478846183` passed. Only the UI image was deployed.

- Staging deployment `p13qdhycwqlgft2bl7wzteii` finished; exact-source native
  container `/u67gepsowf7oo90dwjdx018r-121055346067` is healthy.
- Production deployment `jg2x4xon8shgn6swwwsw81ec` finished; exact-source native
  container `/a6fz2ncuzzulg6lrzzwrjshu-121254795121` is healthy.
- Authorized owned Support fixtures showed 13 document sources and zero live
  sources, with distinct All sources / Documents / Live data presentations,
  preserved totals when filtering, working Clear search and no empty pager.
- All four Search tasks expose the existing selected-provider controls. One
  external-vector binding editor is visible only in Service connection. Section
  changes alone leave Save disabled and the shared draft unchanged.
- Production dark/light 1280x720 layouts fit without outer page scrolling;
  390px basic navigation has no horizontal page overflow. Staging basic mobile
  navigation also passed. No captured console errors on either hosted fixture.
- A staging reload exposed the old premature "No applied release" wording while
  environment queries were still pending. The final source uses truthful
  inventory-unavailable wording, with a focused browser regression.
- Uncached runtime configuration confirms staging/developer and
  production/standard. No backend, runtime, framework, schema, customer setting,
  source/index/binding, release or AI-query mutation was used for qualification.
- Verification sessions signed out, temporary tabs closed and viewport reset.
  Existing local preview remains available at `http://127.0.0.1:4398`.

Hosted screenshots are in `live/`. Save/probe/input-retention writes remain
memory-fixture tests; hosted checks intentionally did not save live settings.

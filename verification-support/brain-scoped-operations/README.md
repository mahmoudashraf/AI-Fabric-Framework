# Smart Brain Scoped Operations

Approved scope: the 010.64 Knowledge / Connections / Action reviews follow-up.
This does not restore the rejected 010.65 customer-wide redesign.

## Retained Contracts

- One Brain and environment header; source and task identities are addressable.
  Explicit unavailable scopes never substitute a different source or task.
- Existing document discovery/audience approval/preview/index/refresh/reconcile/
  index-removal, hosted/BYO uploads, evidence and retention controllers are reused.
- Live source sync, signed webhook diagnostics/manual replay and safe authority
  details reuse the same integration controller. Data-only views do not read or
  mount conversation-handover operations. Human support owns that separate view.
- Existing vector binding remains under Storage with its published-version,
  exact-target, credential-clearing and ownership rechecks.
- Reviews retain customer authority and the ACTIVE/APPLIED_VERIFIED/PASSED target
  eligibility. Confirmation freezes the task/version/customer/target and decision
  ID. Approval is not represented as successful action execution.
- Customer global menu contains resources, not Brain-specific tasks. Staff fleet
  pages and staging developer presentation remain available and unchanged.

## Local Verification

From `Platfrom/ui`:

```sh
node --test scripts/brain-scoped-operations.test.mjs scripts/human-review-workspace.test.mjs scripts/brain-action-reviews.test.mjs scripts/brain-workspace.test.mjs scripts/deployment-workspace-navigation.test.mjs scripts/customer-existing-vector-binding.test.mjs scripts/customer-document-storage-scope.test.mjs scripts/ui-profile.test.mjs scripts/brain-change-preview.test.mjs
node scripts/brain-knowledge.browser.mjs
node --test scripts/customer-existing-vector-binding-ui.test.mjs scripts/brain-settings.test.mjs
npm run build
```

The first suite passed 75 tests. The in-memory browser harness passed 13 journeys:
filtering, exact-source sync, no handover reads, diagnostics/events, absent source,
read-only commands, reused document detail, mobile Back navigation, task selection,
confirmation, cancellation, version-bound decision and absent task. Writes never
reach real infrastructure. All 44 vector/settings checks passed, including 25
vector browser regressions and 10 settings browser journeys. Build passes with
existing bundle-size/runtime-config warnings.

Initial fixture failures (Vite cold optimization and obsolete selector assertions)
were corrected in test setup, not by weakening application checks.

Independent review found a source/webhook identity mismatch. The scoped view now
uses the declared `reconcileDataSourceRef` relationship; URL/events/replay still
use the webhook's own ID. A distinct-ID browser fixture covers this regression.

## Hosted Verification

Source/image: `d343ca48a9026b747d18d581a5ef5dee309db5c9`, branch `Platform-V11`.
UI image CI `42471212481` succeeded; backend skipped. Document-storage CI
`42471212536` passed backend contracts, UI authority tests and mocked browser
document journeys.

- Staging job `v1qjak2mtjxpkz8zo4mdm8sc` finished. Native container
  `/u67gepsowf7oo90dwjdx018r-102518142976` healthy, exact source/image verified.
- Production job `q8t75ftdc23jwy9jwm74k8w4` finished. Native container
  `/a6fz2ncuzzulg6lrzzwrjshu-102708491041` healthy, exact source/image verified.
- Owned Support fixtures: source list/detail with retained commands and manifest,
  old Connections/review links into canonical routes, explicit Brain picker,
  separate Desk/handover view and truthful review-capability status.
- Production dark/light desktop and 390px mobile detail/Back navigation checked.
  Desktop document views had no outer vertical or horizontal overflow. Staging
  developer and production standard runtime profiles remain unchanged.
- No captured browser errors. No hosted Brain release, indexing/sync/deletion,
  review decision, permission, ticket, AI-query or app-assignment mutation.

The hosted Support fixtures do **not** enable Action reviews in their applied
configuration. Their API reports that state; it is not an empty-inbox success.
Approval/cancel/decision flows were qualified in memory, not executed against
real tasks. Staging handover diagnostics also retain an old closed-fixture
delivery failure (`PROVIDER_HTTP_409`); it was not retried or hidden by this UI
change. No runtime behavior was changed to make navigation checks pass.

Screenshots are in `live/`. Test sessions signed out, temporary tabs closed and
viewport override reset. Existing local preview server was left unchanged.

UI-only rollback: use the previous immutable UI image
`a20d1b7b272a4420bad08a792e735365aa4f71ef` on the same Coolify applications.
No database migration or runtime rollback is involved.

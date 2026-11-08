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

Pending exact-source image publication and staging/production UI-only rollout.
No real customer release, indexing deletion, sync command, review decision,
permission, AI query or app assignment is needed for hosted navigation checks.

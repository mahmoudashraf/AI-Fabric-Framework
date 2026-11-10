# 010.37 Manual / AI UI Local Verification

Recorded 2026-11-10. **Local controlled fixtures, not live AI or hosted verification.**
Actual Console React components ran against intercepted/in-memory APIs; AI output,
saves, receipts and accounting were simulated. No paid calls were made. Preparing
this note changed documentation only and reran no tests. Main owns hosted UI
qualification; later revisions are not qualified by these earlier runs.

## Verified Behavior

- Manual (`scripts/brain-settings-navigation.browser.mjs`): retained edits through
  Providers -> Recovery -> Providers -> Diagnostics; unsaved probe payload with
  zero saves; existing navigation guards, target/release scope, focused saves,
  read-only behavior and desktop/mobile fit.
- AI (`src/assistant/assistant.browser.mjs`): pause/refresh including unchanged
  draft ID/revision; requested manual-value replacement; unrelated controlled
  input retention; refresh-error pause; preview/apply/explain, cancel, same-key
  retry, restore and UNDONE UI; explicit source/operation selection, escaped
  review, paste MIME/file/URL/reuse/removal, runtime metadata and visible errors.
- Scope: explicit route-selector tests; private-plugin immutability; active
  Engage preview-only; actual Human support/Specialists/reviews and Kit entries.
- Source fix: `src/assistant/AssistantWorkspace.tsx` retains the editor parent and
  keys only the controller. Canonical `max-mode-widget/src/controlledConversation.tsx`
  and its export remain intact; harnesses dedupe React without a duplicate package.

## Commands and Counts

Working directory: `Platfrom/ui`. Completed runs:

- `npm run build`: PASS, including widget preview prebuild, TypeScript and Vite;
  non-fatal runtime-config/chunk-size warnings remained.
- `env ASSISTANT_EVIDENCE_DIR=/private/tmp/01037-assistant-qualified node src/assistant/assistant.browser.mjs`:
  **16/16 browser groups passed**.
- `env BRAIN_SETTINGS_EVIDENCE_DIR=/private/tmp/01037-settings-qualified node scripts/brain-settings-navigation.browser.mjs`:
  **one complete settings journey passed**, including retained edits and unsaved probing.
- `node scripts/customer-engage-save-ui-smoke.mjs`: **2/2 recorded checks passed**,
  including account denial, explicit save, retained input and same-key retry.
- `npm run smoke:document-storage`: **11/11 scenarios passed**, resolving the
  optional React JSX runtime/Fragment harness failure.
- `node --test src/assistant/scope.test.mjs src/components/documents/HostedDocumentStorageSetup.test.mjs scripts/customer-document-storage-scope.test.mjs`:
  **44/44 tests passed** (29 assistant scope + 15 storage/scope tests).
- Broader batch: **47/48 Node test entries passed**:

  ```sh
  node --test --test-concurrency=1 scripts/brain-updates.browser.mjs scripts/plugin-library.browser.mjs scripts/developer-console.browser.mjs scripts/developer-landing.browser.mjs src/assistant/scope.test.mjs src/components/documents/HostedDocumentStorageSetup.test.mjs scripts/customer-document-storage-scope.test.mjs
  ```

  Updates **29**, plugin library **17**, developer landing **13** scenarios passed.
  Developer Console hit a card-render timing race. After test-only readiness waits,
  `node scripts/developer-console.browser.mjs` passed **8/8 scenarios**. The original
  combined batch was not rerun as an all-green aggregate.
- Scoped `git diff --check` passed for the eight UI source/harness handover files.

## Parent Comparison

The original journey passed with only `SmartBrainShell.tsx` temporarily loaded
from `171010aed53d305ba7643ea8b7d076eb1aeba641` via Vite; page/provider/context/
navigation sources matched that parent. Pre-fix current source lost the dirty
buffer on scoped/null parent switches and failed at `Probe current edits`.
The stable-parent fix passed original and expanded journeys. Main was never
reverted; the temporary override was removed.

## Existing Local Evidence

Exact paths, retained for review:

- Saved dark desktop: `/private/tmp/01037-assistant-qualified/saved-dark-desktop.png`
- Actual Brain form / same-revision refresh: `/private/tmp/01037-assistant-qualified/real-brain-same-revision-dark.png`
- Narrow light, 320px: `/private/tmp/01037-assistant-qualified/assistant-light-320.png`
- Engage mobile: `/private/tmp/01037-assistant-qualified/engage-light-mobile.png`
- Actual Kit guide: `/private/tmp/01037-assistant-qualified/kit-guide-real-details.png`
- Retained edits / probe: `/private/tmp/01037-settings-qualified/provider-checks-retained-edits-dark-1280.png`
- Manual provider form, 390px: `/private/tmp/01037-settings-qualified/providers-light-390.png`
- Parent comparison directory: `/private/tmp/01037-settings-parent-171010/`
- Pre-fix failure: `/private/tmp/01037-settings-before-fix/failure.png`
- Engage final report: `/private/tmp/01045-engage-save-ui-1794275855068/report.actual.json`
- Storage screenshots: `/var/folders/vb/pk83dvvx58v6dpw66mcsn9sr0000gn/T/hosted-document-storage-screenshots-vu6wl46MzQKI/`

`failure.png` inside `01037-settings-qualified` predates the passing rerun and is
an older test-instrumentation failure, not the final result. No evidence was deleted.

Not covered: hosted rollout, live provider/import quality, real accounting,
backend authorization/isolation/undo correctness, or Docker/Testcontainers CI.

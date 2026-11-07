# Outcome-Led Smart Brain Setup

Date: 2026-11-07
Status: committed, deployed to staging and production, and live UI verified.

## Scope

The requested `/deployments?view=setup&deploymentId=...&step=template` page now
distinguishes continuing an existing Smart Brain from creating a new one.
The existing customer shell, Home, Kits, Engage, Support, account pages and
approved 010.64 Smart Brain workspace remain unchanged.

- One customer story: Starting point, Connect your business, Try in staging,
  Go live, Manage staging. URL stage identifiers remain unchanged.
- Existing setup: saved name and starting template, declared template outcomes,
  Continue setup, canonical Smart Brain link, separate Create another action.
  No second creation form or duplicate bottom Next button.
- New setup: selectable starting templates, name, authorized storage choice and
  one Create my setup command. A single staging destination needs no selector;
  multiple destinations retain an explicit selector and their exact references.
- Template outcomes come from declared binding kinds and workspace profiles,
  never template-name matching. They describe possible connections, not readiness.
- Operations policy, quota details and the existing advanced depth control are
  retained under Technical setup & resource limits, not ahead of the task.
  Missing setup checks and source failures remain visible, not bypassed.
- Guided connections no longer repeat resource headers and legacy editor links.
  Business knowledge/access labels replace enum names in the normal view;
  identifiers remain available at advanced depth.
- Creation now follows an explicit `offerId` change in the URL, including
  Create another; an earlier template selection cannot silently carry across.

No backend, runtime, framework, prompts, AI behavior or release contract changes.
Owner-scoped APIs, storage validation, command keys and publish/apply/activation
checks remain in place. The rejected 010.65 global redesign is not restored.

## Local Verification

Preview: `http://127.0.0.1:4398`, existing normal sign-in.
The existing preview helper blocks live resource writes by default. Sign-in and
optional selection preferences use their existing authenticated APIs.

The screenshots use an authorized internal-support account and its existing
Brain, not the owner's `dep-d173ba90` resource. The same generic setup component
renders both. No new infrastructure, configuration save, AI query, upload,
publish/apply or activation was submitted.

Browser checks:

- Existing starting point -> Continue setup retains the exact Brain ID and opens
  connections. Stage navigation retains the existing query-string contract.
- Create another uses the existing leave-confirmation dialog and removes the
  old Brain ID. Available templates, required name and both authorized storage
  choices render; the creation command enables only after required selections.
  The command was not submitted.
- Light/dark render with the existing customer theme. At 1440x900 the existing
  starting-point page has no horizontal or vertical overflow; its primary action
  is visible. The new-setup primary action is visible, with a short extra page
  scroll for lower detail at that size.
- Old internal templates with changed composition/no staging destination still
  show their real blockers. Public Dealership simulator was selectable. This is
  presentation/read qualification, not new deployment readiness certification.

Focused tests cover the presentation, storage contracts, assignment scopes,
admission commands, developer profile and customer release API contracts.
The production build passes with the existing runtime-script and chunk-size
warnings. The owner subsequently authorized commit, deployment and live verification.

## Screenshots

- [Existing setup, light](local/existing-light.png)
- [Existing setup, dark](local/existing-dark.png)
- [New dealership starting point, dark](local/new-setup-dark.png)

## Hosted Verification

- Source `e8831995ebe8da331e32cb76c23c402726d16663` pushed on `Platform-V11`.
  CI `42386483439` passed; UI image published, backend build skipped.
- Staging deployment `p94bavt90lpyea3ave7m1dfe` and production deployment
  `mcuey0z1uc90yf9mo62ydhfy` finished. Both native UI containers are healthy on
  the exact source revision above. Backend, runtime and AI configuration unchanged.
- Staging uses `https://developer.loomai.pro`, with runtime configuration
  `staging/developer`; production uses `https://console.loomai.pro`, with
  `production/standard`. Pricing/personal contact remains absent on developer
  pages and present in production's normal catalogue.
- Existing authorized staging/production verification accounts were used, not
  the owner's `dep-d173ba90` resource. The same shared setup component was tested.
  No new infrastructure, configuration save, release apply, app switch or AI query.
- Staging: sign-in, all five stages, same-Brain Continue setup, technical
  disclosure, Advanced references and return to Configuration passed. Create
  another retains the explicit leave confirmation and removes the old Brain ID.
  The chosen template follows the URL change. Public Dealership simulator,
  required name/storage and the enabled/disabled create command passed without
  submission. Old internal templates retain their existing composition/target
  blockers; this UI rollout does not repair or requalify those templates.
- Production: fresh setup page, same-Brain continuation, canonical Brain link,
  retained 010.64 workspace, light/dark and basic mobile fit passed. At 1440x900
  the existing starting-point page is 1440x900, has one heading, no second create
  form and a visible Continue action. At 390x844 document width is 375; compact
  navigation is available and there is no horizontal overflow.
- The normal support dock was minimized through its existing control for task
  screenshots. Its existing dock behavior was not changed. No final production
  browser console errors were captured.
- All 140 focused tests passed. Verification sessions were signed out and the
  temporary viewport override reset. Unrelated local work remains uncommitted.

Live evidence:

- [Source, deployment and verification report](live/qualification.json)
- [Production, dark](live/production-existing-dark.png)
- [Production, light](live/production-existing-light.png)
- [Production, basic mobile](live/production-mobile.png)
- [Staging, new setup](live/staging-new-setup.png)

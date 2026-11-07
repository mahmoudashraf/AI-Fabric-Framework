# Outcome-Led Smart Brain Setup, Local Review

Date: 2026-11-07
Status: owner approved publication and live verification after reviewing the local screenshots. Hosted results are recorded below after rollout.

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

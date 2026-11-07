# Smart Brain Management Workspace

## Scope

Owner-approved implementation of the management-page proposal. Customer
`/deployments?view=manage&deploymentId=...` now opens the existing resource-first
`/smart-brains/:id` workspace. No backend, runtime, framework, authentication,
permission, infrastructure or AI configuration change is included.

- The redirect checks the owned customer portfolio. Missing/foreign resources
  remain visible errors; no stored/default Brain is substituted.
- Explicit target and release parameters are retained. A release-only link
  resolves that owned release's target, not the default environment. The Brain
  shell retains its existing unavailable-target/release checks.
- An unscoped management link opens the resource list. Creation and Engage
  configuration retain their existing paths.
- The overview shows five outcome links, one next task and a compact environment
  summary. Configured knowledge/handover describe the saved draft, not asserted
  live indexing, agent assignment or readiness.
- Only active, ready Engage apps assigned to the exact selected release count
  as connected customer access. Missing/failed reads do not invent a next task.
- Failed updates, required verification and unapplied draft changes stay visible.
- Updates retains the filtered existing connection, assignment, publish, apply,
  verification, promotion, activation and rollback commands. Nothing is silently
  published/applied/activated by visiting this page.
- The old "All setup controls" escape becomes "Open Smart Brain workspace".
  Global staff administration and the staging-only developer profile remain.

## Qualification

**119 focused checks passed**, including the nine new management tests and
existing Brain, setup, account, collaboration, authentication, navigation and
UI-profile regressions. `npm run build` passed. Existing runtime-config script
and large-bundle warnings remain unsuppressed.

Local browser qualification uses the read-only preview at
`http://127.0.0.1:4398`, with an authorized owned support fixture, not the owner's
personal `dep-d173ba90` resource. No customer configuration or business writes,
AI queries, app assignment changes or infrastructure commands are executed.

The 1280x720 overview fits all five outcomes and its environment summary without
page or pane scrolling. Basic mobile navigation may scroll normally.

Local browser checks passed: old management URL, release-only target resolution,
explicit unavailable target, unavailable Brain, unscoped resource-list fallback,
all five outcome sections, next-action Updates link, light/dark desktop and
390x844 mobile drawer navigation. Screenshots are in `local/`.

An existing Support iframe disposal/state-listener error was observed during
full navigation between the resource shell and the global customer shell. This
was already recorded in the preceding release-journey qualification; it is not
hidden or claimed fixed by this scoped management-navigation change.

## Rollout

Pending UI-only CI image publication, staging-first deployment and hosted checks.

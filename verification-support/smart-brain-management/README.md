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
- Blocked preflight and in-progress provisioning/verification point to Updates,
  not a second apply. Embedded-only apps point to their website installation;
  only an active, ready standalone app offers the standalone Try preview.
- Updates retains the filtered existing connection, assignment, publish, apply,
  verification, promotion, activation and rollback commands. Nothing is silently
  published/applied/activated by visiting this page.
- The old "All setup controls" escape becomes "Open Smart Brain workspace".
  Global staff administration and the staging-only developer profile remain.

## Qualification

**121 focused checks passed**, including the eleven new management tests and
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

- Final UI source: `31bfde9abb61e22814aeecd0d68e9fafaf15bd51`, following
  implementation commit `869a0e8467c92c4164f98fd1acd0478f256ebbf0`.
- [UI image CI 42421539309](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42421539309)
  passed; backend build skipped. The focused independent review found three
  status/CTA cases, all corrected and rechecked without further findings.
- Staging Coolify job `u7ovg7c8dz6r2pf4v08nddyw`: finished. Native UI container
  healthy on the exact final source. Developer profile retained.
- Production Coolify job `z9gddxetu4ah0s01pp5tz6ei`: finished. Native UI container
  healthy on the exact final source. Standard profile retained.
- Both backends remain healthy on
  `baf6ea3328a1ef72d83d01552ffbc02da1582105`; no backend/runtime rollout occurred.
- Hosted staging passed the old management redirect and next-action route into
  the existing Prepare staging / Connections / Connect Engage app / History tabs.
- Hosted production passed old management and release-only redirects, canonical
  reload, all five outcome sections, retained production target, Updates,
  light/dark desktop and basic mobile drawer navigation. No horizontal overflow;
  all overview sections fit at 1280x720. Screenshots are in `live/`.
- Verification used authorized owned support fixtures, not the owner's personal
  Brain. No AI query, business write, draft save, publication, infrastructure
  apply, app assignment change or member/credential change was performed.
- Test sessions were observed signed out before closing; temporary browser tabs
  were closed and viewport override reset.

## Remaining Observations

The previously recorded Support iframe disposal/state-listener warning also
appeared in production during full shell changes. Steady-state Brain navigation
and the tested pages remained usable. This task does not claim to fix that
separate widget lifecycle issue.

The initial source's separate document-storage CI run `42421128098` failed at
**Browser installation**. Follow-up log inspection corrected the initial report:
the backend completed 302 tests with no failures (three PostgreSQL checks skipped),
and all 15 UI contract tests passed. Only the browser journeys did not run.
The runner was Ubuntu 26.04, unsupported by pinned Playwright 1.58.2. No
storage/backend code was changed by the management redesign. Follow-up fixes are
tracked in `verification-support/support-lifecycle-ci/README.md`.

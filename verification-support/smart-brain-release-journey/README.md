# Smart Brain Release Journey

## Scope

UI-only consolidation of staging preparation into Smart Brain Updates, exact
verified-release testing, and explicit practical production promotion. Reuses
the existing release and assignment APIs; no second versioning implementation.

## Local Verification

- Production build passed (existing runtime-config and bundle-size warnings).
- Combined Brain/customer/profile/release contract checks: **194 passed**, with
  zero failures or skips. Includes scoped target selection, original assignment
  and promotion contracts, inherited connection targets and guarded tab routing.
- Existing authorized internal-support customer account and Brain `dep-79af290c`
  used against the real API via the write-blocking localhost preview.
- Legacy staging URL resolved to its authorized bootstrap target in Updates.
- Connections inherited the selected environment. No resource picker or
  duplicate connection-target selector remained in the focused panel.
- Engage assignment remained explicit. The production assignment was not
  changed; staging Try it correctly reported no active exact-release standalone
  app instead of opening production.
- Production setup offered an explicit choice of its two authorized targets,
  then opened scoped Updates / Promote staging with original readiness,
  verification, activation and rollback controls.
- Existing internal-support missing connection profile and stale readiness
  evidence were shown unchanged, not bypassed or relabelled as passing.
- No publishing, apply, assignment change, app activation, credentials update,
  AI query or business action was performed for this UI qualification.
- Normal desktop and basic 390px mobile navigation checked in light/dark views.
  Corrected a moved Grid's overflow and narrow-screen footer wrapping; final
  document and Updates pane had no horizontal overflow.

## Screenshots

- `local/production-promotion.png`
- `local/staging-prepare.png`
- `local/staging-mobile-dark.png`

## Hosted Rollout

- Implementation commits: `42f1cdc66` and `e65525d4d`.
- Deployed source: `e65525d4d3f28c425526961aca53e6c2b664d2a3`.
- [CI 42390937952](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42390937952)
  passed; UI image published, backend build skipped.
- Staging job `o52vymi4c1oedu0qa9o50s2z`: finished; exact-source native UI
  container healthy. Production job `cbh762vpbt5vjd3wgrdd32s2`: finished;
  exact-source native UI container healthy.
- Hosted customer sign-in, legacy staging redirect, scoped connection forms,
  explicit Engage assignment and production destination choice passed on both
  environments. Staging Try it retained its exact-release app requirement.
- Production promotion retained the existing reviewed-candidate, readiness,
  verification, activation and rollback commands. Readiness findings were
  expandable; activation stayed disabled for the unqualified candidate.
- Final production page and Updates pane had no horizontal overflow. Developer
  presentation remained on staging; ordinary customer presentation remained on
  production. Test identities signed out, temporary tabs closed and viewport
  overrides reset.
- No customer configuration/release/app binding was changed, no infrastructure
  was created and no AI query/business action was sent. This is a UI rollout
  qualification, not proof of a new customer runtime promotion.

Hosted screenshots:

- `live/staging-updates.png`
- `live/production-staging-updates.png`
- `live/production-promotion.png`

### Observed Support Console Warning

Navigation between the customer shell and Brain shell produced transient
disposed-Support-frame/state-listener console errors in the verification tabs.
The Support dock subsequently opened with its real composer on both hosts.
No Support message was submitted. This cleanup warning is recorded, not treated
as a passing chat/transport test or silently suppressed; its frame lifecycle
needs a separate focused investigation. No Support transport rewrite was made
as part of this release-navigation change.

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

Hosted rollout is not yet qualified in this checkpoint.

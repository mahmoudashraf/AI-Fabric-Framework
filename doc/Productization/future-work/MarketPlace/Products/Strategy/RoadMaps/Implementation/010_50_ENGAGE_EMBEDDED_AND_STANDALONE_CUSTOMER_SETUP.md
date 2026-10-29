# 010.50 Engage Embedded And Standalone Customer Setup

## Scope

Make website embedding a first-class customer Engage delivery option. A customer
can use embedded and standalone installations simultaneously, including against
the same consumer/deployment. Reuse the existing installation API, shared editor,
assignment resolution, exact-origin validation and release/readiness mechanics.
No new runtime, framework change, delivery enum `BOTH`, duplicated editor or
per-installation backend service.

Templates are bootstrap defaults. Published starter definitions remain verified
against their bootstrap hashes; owned saved configurations are not rejected
merely because they evolved from those defaults. The backend still enforces
ownership, current operation authorization, public-configuration safety,
pack/profile compatibility, exact origins and activation readiness.

## Execution

- Implemented: independent customer delivery selector; both-channel shortcuts in
  the resource inspector and shared editor; complete configuration copying;
  website origin input, installation script/copy/setup/readiness details.
- Implemented: owned configurations remain editable after changing delivery;
  channel-specific slug/domain/origin payloads; no active-app mutation on copy.
- Local verification: 18 profile/payload tests and 19 command/selection/product
  model tests PASS; TypeScript/Vite build PASS (existing bundle-size warning).
- Pending: hosted staging/production UI verification, same-deployment
  coexistence, copy/script behavior, existing embed and standalone smoke,
  small-screen layout and exact source/image rollout record.

## Completion Evidence

To be appended after hosted qualification. Do not infer live status from the
local test results above. Preserve existing app assignments and deployment
versions during setup tests; new origins require normal deployment publication,
not a CORS/runtime hot patch. Remove only newly created test drafts through the
normal installation API after verification.

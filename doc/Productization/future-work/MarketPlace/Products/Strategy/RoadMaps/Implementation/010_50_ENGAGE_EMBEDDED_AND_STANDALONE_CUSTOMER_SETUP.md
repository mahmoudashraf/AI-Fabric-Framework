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
- Implemented: existing app commands use current ownership/assignment rather
  than bootstrap offer entitlements or target lists. The backend's actual
  authorization remains unchanged. Missing starter-catalogue data does not
  prevent using an owned saved configuration.
- Local verification: 39 profile/payload, command, selection and product-model
  tests PASS; TypeScript/Vite build PASS (existing bundle-size warning).
- Hosted verification: staging and production PASS for the scoped delivery
  journey below. No remaining implementation item in this scope.

## Completion Evidence

Qualified 2026-10-29:

- Implementation commits `b13f63b6f` and `bb95dface`. Final live UI source:
  `bb95dface54c73b8e8362681b4ae0ec7075f66aa`.
- Final image workflow `40799597897` PASS; backend build skipped. Same UI OCI
  `sha256:652ba5b4c66ec2bd2b41cf3f8d45178994d7b28b9260bde17a63731712c54c7a`
  on staging job `x11wcgymkkewufxh5zdybabg` and production job
  `za1icmidz7yr1u3kkppihr6o`. Native source, routes, health and preserved
  configuration PASS; zero container restarts.
- Staging customer sign-in and delivery selector verified. A customer without
  an assignment cannot save. Embedded origins and standalone address controls
  switch correctly. 390px layout has no page overflow and readable controls.
- Production Supabase customer used **Add website embed** from the existing
  standalone app. The exact consumer and full owned configuration were selected
  automatically. Created a ready embedded draft with the already approved
  `https://loomai.pro` origin. Server readback matched every copied experience,
  connection and authentication field, including custom tools.
- Reopened and saved that embedded draft successfully despite its delivery
  differing from the bootstrap defaults. **Add standalone app** then selected
  its configuration and same consumer, required a new slug, and enabled Save
  only after entry. The unsaved standalone counterpart was cancelled.
- Installation script clipboard contents matched the Platform-hosted installer
  and exact installation ID. The existing live website uses that script and
  showed **Workspace ready** with the docked input. The existing standalone app
  loaded its workspace, tools, Sign in and Talk to a person controls.
- Existing embedded and standalone installations both remain ACTIVE/ready on
  consumer `northfield-console-live-20261024`, deployment `dep-3c4dbe4f`, release
  `rel-73d5288d`. Configuration, origins, row versions and assignment matched
  their pre-test baseline. No runtime replacement or AI query was required.
- Public allowed-origin manifests 200 for both channels; foreign origin 403;
  missing origin 404; installer 200. Draft creation does not publicly activate
  an installation. This qualifies installation/bootstrap behavior, not a new
  protected-action, provider-quality or private-adapter end-to-end regression.
- Exact temporary draft `awi_pub_0b76c848a2dd4d71c05cb6514a1cdef0` deleted through
  the normal installation API, then absence and unchanged live apps verified.
  No customer business data, vectors, database or existing app was removed.

Private API/native receipts:
`~/.codex/private/loomai-onboarding-01049/production/engage-dual-delivery/` and
`{staging,production}/engage-dual-delivery-ownership/`. Browser screenshots:
`output/engage-dual-delivery-20261029/`. Other deployment historical-release
warnings in the customer inspector were not changed by this scoped task.

New origins still require normal deployment publication/application if absent
from the assigned runtime; there is no runtime/CORS hot patch or automatic
activation. Backend, runtime, framework and public-site images were unchanged.

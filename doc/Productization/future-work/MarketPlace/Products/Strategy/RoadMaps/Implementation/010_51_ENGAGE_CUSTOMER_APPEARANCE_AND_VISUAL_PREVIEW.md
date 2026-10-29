# 010.51 Engage Appearance And Customer Preview

## Contract

Customer journey: select deployment/experience, choose either delivery channel,
customize customer-facing appearance with an immediate preview, explicitly save,
verify and activate. Existing apps expose the same editor through Appearance &
preview. Viewing an active installation never disables or changes it.

Reuse shared widget presentation components inside an isolated, no-network
sample preview. No AI execution, runtime credentials, visitor conversations,
business actions, storage writes or automatic activation. Sample messages are
not real AI results. Preview configuration is allowlisted appearance data only.

Branding: customer-facing assistant name (separate from installation/app name),
HTTPS logo URL, primary colour, light/dark/system appearance, welcome text and
input placeholder. Configuration merge preserves tools, integrations, auth and
pack-specific fields. Both generic and dealership packs consume the same theme
logo contract. No framework release or customer-runtime replacement.

Follow-up scope is tracked in
[010.52 Connection, Complete Preview And Custom Domains](010_52_ENGAGE_CONNECTION_COMPLETE_PREVIEW_AND_CUSTOM_DOMAINS_PLAN.md).
The original 010.51 branding preview was not configuration-complete: its reader
and fixture used an array rather than the real object-shaped tool-group
contract. **010.52 now corrects this on staging and production**, including
shared pack defaults, contextual groups, rail, registered renderers and
conditional auth/attachment/handover presentation. Saved/live tools were never
removed by the old preview reader. Customer-owned-domain positive acceptance
and the missing production CNAME target remain pending DNS in 010.52, separately
from verified Platform-default addresses.

Active installation editing follows existing lifecycle: preview locally while
active; an explicit, confirmed disable is required before persisting changes.
No silent disable/re-enable or hidden recovery workflow. Saving affects only
the chosen installation, not its other delivery-channel counterpart.

## Implementation

- Shared Console editor: branding first, split settings/preview on desktop,
  Settings/Preview tabs on small screens. Existing resource inspectors link to
  this editor; no duplicate configuration page.
- Isolated preview IIFE uses real widget header, message list and composer.
  Sandboxed iframe has scripts only, no same-origin privilege. CSP denies
  connections/forms/storage bootstrap; only public HTTPS images may load.
  Parent messages contain allowlisted appearance and display labels, never
  connection/auth configuration or tool queries.
- Generic and dealership packs support `theme.logoUrl`. Shared widget mark
  renders logos in the launcher, dock and MAX header with a safe fallback.
  Widget-local light/dark/system behaviour and configured MAX placeholder
  match the preview without changing the host website theme.
- Preview is representational, not a connected live test. Real chat and
  integration qualification still happen after readiness/activation.
- Core image scope includes widget changes for both backend-served assets and
  Console preview assets. No framework/runtime image or schema change.

## Qualification Progress

- PASS: 42 focused UI tests (appearance, preserved profiles/command guards,
  selection and iframe isolation), 43 backend configuration-validator tests,
  widget and both experience-pack typechecks, Console production build.
- PASS: isolated browser customer journey; active preview issued no mutation;
  sample send produced only a visible preview notice; explicit disable then
  save retained unsaved branding, tools and knowledge context. Desktop and
  390px navigation, embedded/MAX/standalone and dark/human samples checked.
  Fixture recorded exactly the requested disable and save, no runtime calls.
- PASS: both experience-pack builds/typechecks, two delivery regression tests
  and four branding/copy/auth-preservation mount cases covering both packs and
  embedded/standalone delivery.
- PASS: hosted staging preview under deployed CSP. An unassigned customer can
  inspect sample appearance but cannot save; an invalid logo scheme is rejected.
- PASS: production owner opened active-app preview, changed branding without
  saving or disabling it, then created a separate branded embedded draft using
  the normal customer UI. Native readback confirmed all six appearance fields,
  preserved auth/connection/tools/knowledge configuration, and unchanged
  original ACTIVE/ready installations. Reopened draft restored saved fields.
- PASS: exact temporary draft `awi_pub_d55087baea35415a562816d60b11918d`
  deleted through the normal API; absence reconciled and both original apps'
  configuration/rowVersion/status unchanged. No customer runtime release or AI
  query. Existing standalone workspace still bootstraps with tools, sign-in and
  handover controls. Allowed manifests 200, foreign origin 403, no origin 404;
  new widget asset SRI digest verified and logo support present.
- PASS: final production source, 1440px desktop and 390px customer-preview tab
  checked without dialog horizontal overflow. Visual changes cancelled after
  screenshots. Local fixture server stopped and temporary browser tabs closed.

## Deployed Evidence (2026-10-29)

- Feature/backend source: `389f956ca698c3fd1a699bd53aea7005114b500b`;
  workflow `40913171364` passed. Both backend containers source-verified,
  healthy, schema 217 unchanged, zero restarts and all settings preserved.
  Backend OCI `sha256:f062697fd8d099bbad5636576ae51237c856eb45f205a825228bd6d67b8b3f31`.
  Staging job `rlozkyfwj9jp7js48pcf28qm`; production `vmlapt1fl0tcvv3shu6ylwp0`.
- Final Console source: `fa3e4d6d11a8eabdad7600eef8bbba7b490f9b12`;
  workflow `40914930127` passed, backend skipped. Narrow follow-up bypasses
  immutable HTTP caching for the preview asset across upgrades; the asset is
  still fetched only once per loaded Console page.
  Console OCI `sha256:db5fcc5326134af329c209d5cd86aff1f9e1404eecb60754d4f457d114a9cc2c`.
  Staging job `ya25aj8wwmrl0jbj28xineeg`; production `ixq6bhu9fc0l026wdj5dqpuq`.
  Both source/routes/health/settings checks passed, zero restarts.
- Customer location: Engage apps -> app details -> Appearance & preview;
  also present during Create Engage app. Customer guide:
  `Final_Documentation/External_User_Guides/LOOMAI_ENGAGE_WEBSITE_AND_STANDALONE_SETUP_GUIDE.md`.
- Browser screenshots: `output/engage-appearance-20261029/`; private native
  rollout and cleanup receipts:
  `~/.codex/private/loomai-onboarding-01049/{staging,production}/engage-appearance*`.
- Scope is appearance/setup/bootstrap qualification, not a new AI-answer,
  private-adapter or protected-action regression. No framework change, new
  runtime, broad release matrix, managed image storage or global cleanup.

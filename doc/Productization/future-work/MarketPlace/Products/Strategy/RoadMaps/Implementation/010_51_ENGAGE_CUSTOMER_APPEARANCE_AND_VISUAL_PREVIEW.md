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
- Pending: immutable source commit, staging and production rollout, hosted
  customer preview and saved-branding qualification, temporary fixture cleanup.

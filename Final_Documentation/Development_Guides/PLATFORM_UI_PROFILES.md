# Platform UI Profiles

The customer-wide 010.65 design expansion was rejected and rolled back on
2026-11-07. Keep the earlier approved customer pages and the 010.64 Smart Brain
workspace. Do not restore the removed customer shell/task redesign or its design
guide without a new owner request.

Brain-scoped operations have one canonical home under `/smart-brains/:brainId`:
Knowledge (documents/live data/storage), Human support and Action reviews. Do not
re-add their customer global-menu selectors or Manage wrappers. Legacy links are
navigation adapters, not parallel editors. Staff-wide administration stays
separate; sharing the workspace never grants customer review authority. See the
010.64 scoped Knowledge/Connections/Reviews proposal and its verification record.

Brain configuration also has one canonical navigation home: AI models in the
Brain sidebar, embedding/vector settings in Knowledge -> Search settings, and
runtime/provider/recovery/diagnostic/JSON tools under pinned Advanced children.
Action access and webhooks stay with AI capabilities, not empty Settings pages.
Old `/settings/...` URLs are redirects only. Reuse the existing provider/action
editors and draft-save contracts; never mount a second copy to add an entry point.
See `verification-support/brain-settings-navigation/README.md`.

Knowledge's All sources / Documents / Live data tabs are filtered views, not
parallel editors. Keep their source counts and empty-state wording specific;
unknown or failed reads are not zero. Search settings uses four focused tasks
over the same provider buffer: service, embeddings, connection and customer
separation. Keep file uploads separate, retain connection inputs across these
tasks and do not display provider defaults as a loaded customer configuration.
See `verification-support/knowledge-source-experience/README.md`.

The separately approved account follow-up uses `/account/profile`,
`/account/team`, `/account/loomai-access` and `/account/security` in a single
resource workspace. Customer `/users` redirects there; staff `/users` remains
unchanged. Account permissions are workspace-wide, never scoped by a Brain
navigation query. See `verification-support/customer-account-workspace/README.md`
for the preserved authentication/team contracts and verification evidence.

The 010.66 private Data plugin library is a separate deliberate authoring task
under `/library/plugins`, entered from a Brain's existing Knowledge/Capabilities
picker. It is workspace-owned and has no environment selector. Keep definition
authoring separate from Brain installation; reuse the existing installer,
compiler and release flow. There is no new plugin type. Unsupported customer SQL
or filesystem adapters must not be presented as connected. Preserve source/form
fidelity and server-enforced ownership/revisions; advanced presentation is not
permission. See `verification-support/plugin-library-01066/README.md` for delivery
status, including the distinction between local tests and hosted qualification.

The staging-only developer presentation is retained independently of that
redesign. It uses the same existing UI and staging Platform backend, not a
separate product, backend, authentication role or authorization bypass.

## Configuration

Set these runtime variables on the staging UI service:

```text
PLATFORM_UI_ENVIRONMENT=staging
PLATFORM_UI_PROFILE=developer
```

The existing uncached `/runtime-config.js` supplies the profile before React
renders. Production and an unset environment always resolve to `standard`, even
if the requested profile is `developer`. Query strings/local storage cannot
enable it. To restore the standard staging presentation, set
`PLATFORM_UI_PROFILE=standard` and redeploy the same UI image.

`https://developer.loomai.pro` and the existing staging UI aliases use the same
staging service and profile. This is real staging data/infrastructure, not a
resource-free simulation. Production keeps the ordinary commercial view.

## Shared Presentation Wrappers

Use `Platfrom/ui/src/uiProfile.tsx` for these concerns:

- `CommercialContent` wraps LoomAI offers, sales pricing, commercial terms and
  marketplace/service-plan price displays.
- `PersonalContactContent` wraps personal integration/contact-card triggers,
  dialogs and associated personal sales copy. The shared `IntegrationCallButton`
  already wraps its entire trigger/dialog, so callers inherit the rule.

Hidden components do not mount. Do not hide them with CSS or remove matching
text. Product previews, actual business data/prices, configuration commands,
Support requests and real Desk handover retain their existing behavior.
Budget/accounting controls, quotas, permissions and entitlement checks are not
sales chrome and remain enforced. The profile is not a confidentiality feature:
compiled assets and authorized API data can still contain commercial metadata.

## Verification

Run `node --test scripts/ui-profile.test.mjs` and the production UI build.
Check the developer hostname's HTTPS and runtime configuration, normal staging
sign-in, absence of offers/contact actions, retained setup/Support and clean
desktop/mobile layout. Confirm production still has its normal presentation.
Never infer authentication or resource isolation from hidden UI alone.

Evidence and rollback status:
`verification-support/developer-ui-profile/README.md` and the latest
`LLM-guides/CODEX_WORKING_CONTEXT.md` entry. Earlier hosted screenshots/revisions
are historical evidence, not proof of the current deployed layout.

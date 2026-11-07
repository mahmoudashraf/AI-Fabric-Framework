# Platform UI Profiles

The customer-wide 010.65 design expansion was rejected and rolled back on
2026-11-07. Keep the earlier approved customer pages and the 010.64 Smart Brain
workspace. Do not restore the removed customer shell/task redesign or its design
guide without a new owner request.

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

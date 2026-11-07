# Staging Developer UI Profile

Owner request: use the staging Platform through `https://developer.loomai.pro`, without LoomAI sales pricing/offers or entry points to Mo's personal integration card. This is a UI-only presentation profile, not a new product, backend, authentication role or customer-permission bypass.

**Rollback update:** the owner rejected the customer-wide 010.65 UI redesign.
Its commits were removed from the active branch; the developer profile is
reapplied on baseline `0aa83b968` independently. The earlier source revisions and
screenshots below are historical until the post-rollback qualification is
recorded. They do not authorize restoring the rejected customer-page design.

## Configuration

On the existing staging UI service:

```text
PLATFORM_UI_ENVIRONMENT=staging
PLATFORM_UI_PROFILE=developer
```

The existing uncached `/runtime-config.js` supplies these values before React renders. An unset environment or `production` always resolves to the standard profile. No query parameter, local-storage switch or separate UI build enables this mode. Use `PLATFORM_UI_PROFILE=standard` and redeploy the same UI image to restore the standard staging appearance.

`CommercialContent` wraps offer banners, prices, commercial terms and marketplace/service-plan price displays. `PersonalContactContent` wraps the shared personal-contact trigger and dialog, plus associated personal sales copy. The product previews, installation/setup commands, knowledge, actual business data/prices, support requests, human handover, budgets and entitlement checks remain available under their existing permissions. The compiled assets can still contain commercial content; this is not a confidentiality mechanism.

The same staging UI service retains its existing sslip addresses and receives an additional HTTPS route. The staging backend needs only its exact CORS-origin configuration updated; backend source and customer runtimes remain unchanged. Production remains on the standard presentation.

## Verification

- Seven profile/component/server checks pass, including production/missing-environment rejection and hidden children not mounting.
- The earlier pre-rollback design/profile run passed 38 checks. Those removed
  customer-design suites are not the post-rollback release gate.
- TypeScript and the production UI/preview build pass; existing bundle-size and runtime-script warnings remain.
- Publication/deployment and actual developer-domain checks are recorded below. These checks must not be confused with a newly provisioned customer deployment or an AI answer-quality run.

## Hosted Closeout, 2026-11-07

- Final source `a126e2f27009ad1a56fcc242d5903e8f6f7c379c`, CI `42381210896` success. Staging UI job `zhf96n4oy76b1t1oxs0t670z` finished; the exact-source native container is healthy. The earlier profile source `0b50bc991` is superseded by the clean offer-free grid reflow.
- `https://developer.loomai.pro` resolves to staging and serves valid HTTPS. The uncached runtime configuration reports the staging API and `staging/developer` presentation. Existing staging aliases share this same profile.
- Only the staging backend's exact CORS origin configuration changed, with a same-image redeploy (`quof4ss1vjuk6rd3oqqe7h9f`). Source remains `baf6ea3328a1ef72d83d01552ffbc02da1582105`. Authenticated CORS for the developer origin passes without wildcard origins.
- Three exact developer callbacks were added to Supabase's existing allowlist: `/auth/callback`, `/auth/recovery`, `/auth/account-recovery`. Existing redirects and every other auth setting were retained. No email was sent and new-domain email delivery was not requalified.
- Existing staging customer password sign-in and sign-out passed through the developer hostname. The real customer Home and Support page loaded; Chat, Request help and My requests remain available. No personal contact trigger appeared. No customer data/configuration was changed.
- Public catalogue retains three Kit cards with zero LoomAI offer banners, zero commercial terms and zero personal contact triggers. Detail pages retain Install kit and previews but omit personal project/contact actions and commercial offer sections. Desktop 1280x900 and mobile 390x844 pass horizontal fit; light/dark render correctly. Desktop cards have a single grid track with no reserved offer band. No captured final-browser console errors.
- Production stays on UI `bb672a85748bcc61039deb259630f3868a8f72b0`, standard view: three offers and two personal contact triggers observed on its catalogue. No production setting or service was redeployed by this follow-up.

Evidence:

- [Deployment, HTTPS, runtime profile and CORS](live/deployment-report.json)
- [Browser observations and boundaries](live/browser-report.json)
- [Developer catalogue, dark desktop](live/catalogue-dark-desktop.jpg)
- [Developer catalogue, light desktop](live/catalogue-light-desktop.jpg)
- [Developer catalogue, mobile](live/catalogue-dark-mobile.jpg)
- [Customer Home without personal contact action](live/developer-customer-home.jpg)

This is a presentation qualification using existing accounts/resources, not an infrastructure launch, email-delivery test, new custom-domain Engage app or AI-quality gate. Developer and standard modes retain the same backend authorization.

## Future UI Work

Use the wrappers for new commercial/contact surfaces and follow the
[UI profile guide](../../Final_Documentation/Development_Guides/PLATFORM_UI_PROFILES.md).
Root `AGENTS.md` and the operating context link it. Do not reintroduce the
rejected 010.65 layout or its deleted guide.

# 010.67 Developer Entry And Staging-Only Brains

## Owner Decision

Provide two public entry pages over the same Platform: the existing business
entry at `console.loomai.pro` and a dark developer entry at `developer.loomai.pro`.
The developer page follows the approved central Brain visualization and two
starting-point cards. Do not create another Console, customer role, runtime or
database. The rejected 010.65 global customer redesign remains out of scope.

The existing staging-wide developer presentation is retired. Staging returns
to ordinary customer/staff presentation; developer wording belongs to the new
entry page, not a global commercial/contact-hiding profile.

## Product Journey

1. Explore the Brain's data, integration, retrieval, action, model, behaviour,
   specialist and human-support capabilities.
2. Choose Customer Experience Brain or Support Team Brain. These reuse the
   existing `ai-customer-experience` and `ai-support-experience` Kits.
3. Sign in or register through the canonical production Console. Retain the
   selected Kit and typed developer entry across password/email/onboarding.
4. Configure the same existing Kit, Brain, plugins, knowledge, Engage and Desk
   resources, using customer staging only.
5. Test and connect the staged Brain to the developer's app or side project.

There is no new developer identity or authorization role. The same customer
may separately create ordinary business Kits under existing permissions.

## Visual Contract

- Dark charcoal, sharp white text, mint neural connections and primary action.
- A generated transparent silver/teal Brain asset, not a screenshot of the page.
- Eight compact capability nodes around the Brain; each opens a dismissible
  description. Specialist readiness is not implied by selecting a starter Kit.
- A clear connection to web, mobile and backend apps.
- Four-step start sequence and two real Kit setup actions, not fake forms.
- Developer offer: Free for developers, staging only, training by LoomAI included.
  Owner update, 9 November 2026: remove the one-year offer limit; existing model
  usage and infrastructure allowances remain unchanged.
  This describes developer platform access; existing model-usage, credentials,
  funding and infrastructure allowances still apply. It is not unlimited AI
  credit, an automatic quota waiver or a new billing/expiry engine.
- Existing customer commercial pages and ordinary Kit functionality stay intact.

## Backend Boundary

Creation accepts a typed `entryPoint` of `CUSTOMER` or `DEVELOPER`. The server
allows the developer entry only for the two supported existing Kits and stamps
a persisted staging-only environment policy on the canonical deployment.
Existing records and ordinary creations remain unrestricted between authorized
staging and production targets. The shared business templates themselves are
not globally marked staging-only.

Public policy projection includes `allowedEnvironments` and derived tags,
including `staging-only`. UI metadata is explanatory, not authority. Enforce the
policy at infrastructure/apply, provisioning, promotion and assignment boundaries.
Customer draft editing, template reset, cloning/import and another hostname must
not erase or bypass it. This is an infrastructure allocation boundary, not an
LLM semantic validator. Do not reintroduce original offer/model/runtime pins.

## Routing And Operations

`developer.loomai.pro` is a hostname on the existing production frontend. Its root
opens the developer page; `/developers` also provides a direct preview route.
Developer account/setup links use `console.loomai.pro`, preserving the existing
authentication origin and redirects. Hostname and query strings grant no rights.

Remove the obsolete `PLATFORM_UI_PROFILE` behavior and staging setting while
retaining the actual API URL and environment configuration. No staging backend
or customer infrastructure moves to production because a landing page moves.

The owner selected the existing `developer` hostname rather than `develop` and
updated its A record to `46.225.162.106`. Remove its old staging route, configure
the existing production frontend route, and qualify HTTPS.

## Execution Status

### Console Discovery Continuity Follow-Up

Owner request, 2026-11-09: retain developer entry through sign-in and general
Console navigation, showing developer rather than business starter cards.

- Reuse the approved two developer cards on Home, My kits, discovery and empty
  Brain lists, retaining all existing resources and their settings.
- Carry typed intent on general account links/email callbacks without requiring
  a selected Kit.
- Remember display choice per actor/customer in this browser, with short-lived
  pending intent. No new account type or retired staging-global profile.
- Allow explicit business switching. Existing resource policy stays authoritative;
  new developer Kit setup retains its typed staging-only creation intent.
- Verify account isolation, ordinary business cards, navigation/reload,
  desktop/mobile and no infrastructure mutations. Record hosted delivery
  separately from local verification.

Delivered on 2026-11-09: frontend source
`04e5f653de3ea31aace2f9dbeeb96bbee04edf18`, image CI `42632868209` passed,
staging/production containers healthy at that exact revision. All 93 focused
regression checks and seven local Console browser scenarios passed. Real hosted
customer sign-in, Home/My kits/discovery/reload, explicit business switching,
phone fit, typed staging-only setup and sign-out passed on both environments.
No backend/runtime changes or new resources. See the verification README for
exact deployment receipts, test scope and screenshots.

### Public Homepage Continuity Follow-Up

Owner clarification: developer referrals keep developer presentation across
`loomai.pro` and Console unless intentionally browsing business content. Use one
non-sensitive shared presentation cookie, not a new session/account role.
The public homepage redirects to the existing developer landing for this choice;
deep product/demo/proposal links remain reachable. Fresh browsers keep the business
homepage; explicit business choice resets the shared preference. Signed-out Console
discovery may read it, but another signed-in account does not inherit it.

"Upgrade" means paid publishing to production, not browsing business kits. Do not
enable production, change resource policy or imply a payment flow is implemented
as part of this minimal presentation change. Hosted verification is recorded in
the verification README and working context after rollout.

Delivered and qualified on 2026-11-09 at
`864239acf0651df371e3e83faafc2f57e18486ed`: public-site and Console UI images
passed CI, both Console environments and the production public site are healthy.
Anonymous and authenticated cross-domain developer continuity, deliberate business
browsing/reset, ordinary visitors and mobile/desktop checks passed. No business
backend, runtime, payment or environment-policy changes.

### Previously Hosted Foundation

- Complete: typed backend policy, migration 238 and focused enforcement tests.
  Backend source `6f5d59b7ac224e99c90dd510b9167c24a2c7f922` is healthy on
  staging and production; migration 238 succeeded on both databases.
- Complete: dark landing, generated Brain asset, eight interactive previews,
  existing Kit links and authentication intent propagation. Frontend source
  `edfb370717d1e3eff6f4efcc1dc1666640ee2a92` is healthy on both environments.
- Complete: staging-global presentation wrappers and their environment setting
  are removed. Both environments retain ordinary business content.
- Complete: `developer.loomai.pro` DNS, production routing and HTTPS. Hosted
  desktop/tablet/phone checks passed, including all capability dialogs, image
  loading, canonical Console authentication and unchanged ordinary root pages.
- Complete: enable a credential-free public Support starter using the existing
  generic Support template 1.2.0, its published plugins and the current reviewed
  runtime. It declares customer storage slots, not LoomAI's internal support
  documents, credentials, funding or business data. No new template/account type.
- Complete: both Kit types were created through the actual customer UI on
  staging and production. Their persisted policies and staging-only target lists
  were read back; promotion planning and production apply returned the explicit
  staging-only denial. Disposable configuration-only fixtures were removed.
- Complete: an ordinary business Kit retained both environments and exposed a
  production target. No serving deployment or existing app assignment changed.

The public starter is enrolled through the existing operator-controlled
`PLATFORM_CUSTOMER_ONBOARDING_TEMPLATE_OFFER_IDS` setting on both environments.
Current Knowledge and Dealership entries are preserved, and
`public-support-profile-20261108` is appended. This is bootstrap access only;
ordinary business resources retain the existing unrestricted upgrade contract.

Record actual test/deployment results in the verification record and working
context. Neither this plan nor an image is evidence of a hosted feature.

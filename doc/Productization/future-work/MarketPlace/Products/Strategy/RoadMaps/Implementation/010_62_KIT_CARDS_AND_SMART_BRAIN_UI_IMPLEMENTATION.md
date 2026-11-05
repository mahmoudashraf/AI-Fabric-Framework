# 010.62 Kit Cards And Smart Brain UI

## Scope

Approved UI-only implementation, 2026-11-05. Backend, framework, billing,
deployment contracts, runtime images, customer configuration and data are unchanged.

- Replace the three catalogue cards with the approved dark design, also supporting light mode.
- Show adaptive Customer Experience, industry-specific Dealership AI and AI Support.
- Use real local product/vehicle/agent assets in clearly illustrative workspace scenes.
- Show Platform + Engage + Human Desk, with optional human-support wording.
- Place small WhatsApp/mobile companion icons above the Desk headset, without a coming-soon label.
- Preserve real previews, three display formats, comparison/handover samples,
  existing demo links, detail navigation and authenticated kit setup routes.

The channel icons are commercial presentation only. This change does not
implement a mobile app or enable/configure a WhatsApp integration.

## Dealership Offer

Only the dealership card and its detail page show the approved offer:

- GBP 500 per month, with modest price emphasis.
- Only pay when value starts, directly beside the price.
- First three months free.
- Free integration.

`KitCommercialPresentation.tsx` owns the shared offer copy. No checkout,
subscription, entitlement, provider allowance or billing behavior was added.
Other kits receive no invented price or free-integration offer.

## Detail Pages

Existing descriptions, features, previews and setup/demo links are retained.
New kit-specific integration sections explain approved external systems,
knowledge and optional people-in-the-loop support. The owner explicitly
confirmed that LoomAI's technical team handles integration.

The journey is requirements/access -> LoomAI integration -> team testing ->
go-live. Dealership inventory/customer systems remain external integrations;
the fictional simulator disclosure remains. Existing `/support` and kit setup
routes are reused, with no new request or backend contract.

## Reversible Terminology

`Platfrom/ui/src/deploymentTerminology.ts` is the single source of UI terminology:

```typescript
export const DEPLOYMENT_TERMINOLOGY: DeploymentTerminologyMode = 'smart-brain'
```

Change this one value to `deployment` and rebuild the Console to restore
Deployment/Deployments labels. Singular, plural and lowercase copy derives from
that setting. Customer/admin/operator navigation and configuration use it.

Routes such as `/deployments`, `deploymentId` payload/query keys, enum values,
IDs, selectors, customer-supplied names, server messages and operation inputs
are not renamed. Azure provider-native deployment fields retain their meaning.
The existing `SMART_BRAIN` behavior is displayed as Event-driven to distinguish
it from the new resource label; its backend code and execution remain unchanged.

## Verification

- TypeScript and production Vite build passed.
- Full Console test run: 448 passed; one production-server browser test timed
  out during concurrent execution, then passed independently without code changes.
- Six explicit terminology tests cover rollback, navigation, names, API paths,
  request keys, authorization enums and server-message preservation.
- Catalogue browser checks cover 20 light/dark and desktop/tablet/mobile states,
  all preview formats, comparison/human samples, detail links and source images.
- Detail-page browser coverage checks all three kits, both themes and four widths.
- The existing Support kit browser smoke passes all nine setup/edit/tracking/
  human-support scenario groups. Its storage fixture was updated to the existing
  hosted-storage response shape; no production storage implementation changed.
- No real AI query, business action, customer resource mutation or storage change
  is needed to qualify this presentation-only change.

## Initial Rollout

Initial release completed on staging and production. Native UI image revision:
`e4d4fc2d5c568da459a95699006cc684cfbc133c`. Both Coolify jobs finished and both
applications are running healthy. The production `/health` endpoint returns UP.

- Core image CI `42066513599` passed; the backend image job was explicitly skipped.
- Document storage CI `42066513781` and environment quota/Desk CI `42066513671`
  also passed. No framework or backend release was required.
- Hosted public checks on both hosts verified three cards, dealership-only offer,
  three Desk icon groups, three preview formats, all three detail pages, dark/
  light rendering, local images and basic mobile bounds.
- Real Supabase customer sessions on both hosts verified Smart Brains navigation
  at the unchanged `/deployments` route and all three entitled kit setup links.
  Normal authenticated reads before/after proved app configurations unchanged.
- No browser errors or customer writes were observed. Existing backend, Desk and
  customer runtime image names/tags were read back unchanged from the baseline.
- No customer resources or persistent test fixtures were created.

Public-safe qualification summaries and public catalogue screenshots are under
`verification-support/kit-cards-01062/`. Authenticated screenshots, credentials
and operational receipts remain private. Unrelated pre-existing worktree
changes are not part of this release.

## Approved-Image Fidelity Follow-Up

The next UI-only refinement, 2026-11-05, brings the catalogue closer to the
approved mockup rather than treating the first implementation as visually final:

- Compact single-row desktop heading and aligned, shorter card composition.
- Locally served, licensed Manrope typography with readable titles and examples.
- Reduced whitespace, consistent separators, photo-header strips and aligned
  scenes, product rows and primary commands.
- A fictional headset-wearing support-agent portrait beside Human Desk, retaining
  the small WhatsApp/mobile companion icons above it.
- A full-vehicle fictional preview image instead of the tightly cropped bumper.
- Removed the duplicate catalogue support band; optional Desk composition remains
  visible on every card. Detail content and setup destinations are preserved.

Generated illustrative assets and font licensing are documented with the UI
assets. No live inventory photograph, indexed evidence or customer branding was
replaced. Companion icons still do not implement the corresponding channels.

Focused qualification passed: production build, 21 catalogue/preview/integration
tests, 22 responsive/theme/browser states and all nine Support kit UI scenario
groups. Geometry checks cover compact desktop card height, aligned scenes and
commands, the local font, and companion icons above each portrait. No customer
write or browser error was observed.

### Follow-Up Hosted Qualification

Completed on staging, then production. Current native UI revision on both hosts:
`637de00163f96690da236274370d19e282539ef4`. Core image CI `42078045833`
passed with the backend image job skipped. Both Coolify jobs finished and both
Console services are running healthy.

Live browser checks verified all three cards, headset portraits, companion-icon
positioning, the locally loaded font, aligned scenes/commands, light/dark and
390px mobile bounds. At 1536px, cards measure about 715px high with 28px titles,
matching the approved mockup's compact composition. All three preview formats,
detail pages and native Supabase customer setup links passed. Customer app
configurations and protected backend/Desk/runtime image tags remain unchanged.

The first staging browser attempt started during image replacement and timed
out. It passed after native revision and service health were confirmed; no
additional code change or repeated deployment was needed.

Public-safe screenshots and qualification summaries are in
`verification-support/kit-cards-01062/refinement/`. This is a presentation-only
release; existing kit installation and AI execution behavior are unchanged.

## Reference Background Colour Correction

The owner identified that the adaptive/dealership preview backgrounds were too
grey. Pixel inspection of the approved image confirmed deeper teal for adaptive
and blue-charcoal for dealership. Explicit dark-mode card/scene/header tokens
now reproduce those tones, rather than relying on a four-percent accent mix.
AI Support and all light-mode backgrounds are unchanged. Layout, content and
behavior are unchanged. The production build and 24 browser/theme states passed,
including explicit computed-colour regressions. Current native UI revision on
both hosts is `18ca81042b5efa0089326fca7960015853815f46`. CI `42090027303`
passed with the backend build skipped. Both Console deployments finished healthy.
Live browser checks on staging and production confirmed the intended card,
scene and header colours, unchanged geometry, local assets, preview/detail/setup
links, customer authentication, light mode and mobile bounds. No browser errors,
customer writes, app configuration changes or protected image-tag changes occurred.

Safe evidence: `verification-support/kit-cards-01062/background-colours/`.

The separate request for an improved kit detail page is a visual proposal only;
no detail-page layout or commercial terms have been changed in this correction.

## Three Kit Detail Pages

The owner approved full implementation of the detail-page proposal for all three
kits. This follow-up changes presentation only, not kit definitions, runtime
features, customer configuration, storage, billing or backend contracts.

- A full-width photographic hero for each kit, with a real HTML workspace sample.
- Business-first value copy: AI-enabled buying for dealerships, an adaptable
  customer experience for general businesses, and connected guidance/requests
  for support. No invented performance metrics or partnership assertions.
- The dealership alone shows three free months, free integration handled by
  LoomAI's team, GBP500/month and Only pay when value starts. Other kits gain
  no unapproved commercial terms.
- Three rich, clickable format examples open their matching existing previews:
  website chat, Max workspace and standalone space. Preview interactions remain
  illustrative and cannot submit requests or execute business actions.
- Kit-specific three-step outcome journeys, optional human support, linked
  Platform/Engage/Desk product composition and the existing integration process.
- Original catalogue descriptions/features remain visible. Detailed integration
  scope and customer choices are retained in an expandable section, not removed.
- Existing demo URLs, authenticated setup routes, public signup routes, return
  navigation, theme switching and human/ticket sample controls are preserved.

Three generated fictional photographs are optimized and bundled locally; their
provenance/prompts are in the product asset README. No customer inventory,
knowledge, branding or agent profile was changed. The approved image is archived
with the visual evidence for comparison.

Local production build and focused tests pass, including all three detail pages,
both themes, four viewport widths, exact-format dialog opening, setup/demo links,
offer isolation, image/font loading, equal preview geometry and uncropped input
areas. The 22 focused tests passed, including 24 detail/theme/width states;
catalogue checks passed 24 states and Support kit setup passed nine scenario groups.

### Detail-Page Rollout Completed

Current native UI revision on staging and production:
`9c8d4a7fde369cf88a701f9044c449dd27f117b2`. Core CI `42098044609` passed;
the backend build was skipped. Both Console deployment jobs finished healthy.

Live qualification on both environments passed all three detail pages, all nine
format-preview entry points, the expandable integration scope, product links,
anonymous signup links and authenticated customer setup links. Each kit was
captured at desktop/mobile widths in light/dark mode. Images/fonts loaded,
preview columns remained aligned, inputs were not clipped, and no horizontal
page overflow or browser error was observed. The original catalogue's geometry,
backgrounds, offer and portraits were also checked unchanged.

Authenticated readback confirmed customer app configurations unchanged. Protected
backend, Desk and customer runtime image tags were unchanged. No customer resource
or persistent fixture was created and no real AI query or business action ran.

Public screenshots and summaries are in
`verification-support/kit-cards-01062/detail-pages/`. The implementation remains
UI-only; there is no framework, deployment-template or commercial-contract change.

## Public Homepage Kit Cards

The owner requested the approved kit cards on `https://loomai.pro/`. The public
homepage and its shared `/experiences` catalogue now use three coordinated
cards: AI Customer Experience, Dealership AI and AI Support. Explore links lead
to the three new Console detail pages, rather than duplicating those details.

The presentation retains the approved adaptive teal, dealership blue and Support
amber identities, fictional photographic workspace scenes, capability rows,
Platform/Engage/Human Desk composition and headset portrait with small channel
symbols above it. The dealership alone has the existing approved offer: GBP500
per month, Only pay when value starts, first three months free and free
integration. The existing dealership live-demo link remains available.

Each card opens a native, dismissible illustrative preview with website-chat,
Max-workspace and standalone formats. Existing adaptive/dealership preview
interactions are retained; Support gains a request/guidance/human example that
cannot submit a ticket or contact an agent. Assets and Manrope are served
locally with provenance/licences. The public site's light-mode default remains
unchanged. A narrow-phone header sizing correction keeps theme/navigation
controls within 320px bounds.

This is a static public-site change only. No backend, runtime, template,
customer assignment, AI prompt, billing or customer-data changes are required.
Local production build, Astro checks (zero errors/warnings/hints), content graph
and all 28 static route checks passed. The focused browser runner passed both
catalogue routes, all nine preview formats, close/Escape/focus behavior, local
images/font, offer isolation and ten width/theme layouts (320-1536px). The shared
theme/navigation regression also passed. Evidence is in
`verification-support/kit-cards-01062/homepage/local/`.

### Homepage Rollout Completed

Serving-image CI `42106691452` passed, publishing only `loomai-site`. The
production image-only deployment finished healthy. Native container image and
revision confirm `852bc5614ed0ee55919acf2fa4b0025fa271a47f`; all other application
image tags are unchanged. A separate clean-source build/check/smoke and browser
pass confirmed no pre-existing dirty work was included in the release.

Hosted browser verification passed on both `https://loomai.pro/` and
`https://loomai.pro/experiences`: three cards, exact Console detail links,
dealership-only offer, portrait/channel positioning, all nine preview formats,
close/Escape/focus, five widths in both themes, local images/fonts, aligned
desktop commands and mobile preview bounds. All three Console detail targets
returned HTTP200. The public site's light default and explicit dark preference
work correctly. No AI query, enquiry or ticket was submitted.

Live safe screenshots and reports are in
`verification-support/kit-cards-01062/homepage/live/`. Existing health JSON has
stale build-commit metadata; qualification therefore also records the correct
native image/revision rather than treating that old field as deployment proof.
This metadata discrepancy does not affect the served cards or workspace setup.

## Website, Console And Integration-Team Navigation

The owner found that the kit detail hero mostly led to another preview, and
the website-to-Console boundary was unclear. This follow-up keeps one rich kit
detail implementation in Console and makes the journey explicit:

1. `loomai.pro` is the public website and experience catalogue. Card actions say
   Explore kit in Console; they open the corresponding Console detail page.
2. Console kit pages show the Console identity and a visible LoomAI website
   return link. Install kit is prominent in the hero and closing section and
   opens the existing `/kits/new/{code}` setup route. Anonymous visitors use
   the existing sign-in/registration gate with the selected kit preserved.
3. Start a project opens the approved integration/customer-success Calendly
   event. It starts with a consultation, not a silent kit/infrastructure create
   or a claim that Calendly creates a Platform project/ticket.
4. Book integration call is also available on the public site header, mobile
   navigation, footer, homepage, catalogue and Connect page; Console public
   product/kit pages; customer navigation; and customer Support. Existing
   signed-in integration requests remain accessible independently.

Approved scheduling event:
`https://calendly.com/mahmoud-elgammal-loomai/loomai-integration-customer-success`.
The real calendar loaded successfully and displayed the 30-minute LoomAI
Integration - Customer Success event. No appointment was booked during testing.
Booking uses ordinary external links in a new tab with noopener/noreferrer:
no Calendly script/iframe, automatic account prefill or resource disclosure.
The fictional dealership customer-facing demo is not given owner-only LoomAI
integration booking controls.

Changes are UI-only: no API, authentication contract, template entitlement,
billing, AI decision or runtime behavior changes. Booking URLs are centralized
within each independently deployed frontend. Existing previews, kit offers,
signup/recovery behavior, customer support requests and live demo links remain.

Local build/check and 28 static route checks passed. Focused checks passed 11
kit/booking/detail tests, 16 authentication-return/continuity tests, 42 catalogue
and product responsive/theme states, nine Support kit setup groups, and the
homepage preview/booking checks.

### Navigation Rollout And Hosted Installation Entry Checks

Current native frontend source on staging Console, production Console and the
public site: `73b49a469c211e28cc92039902809525f91f0975`. Core-image CI
`42114424597` passed with the backend image skipped. Serving-image CI
`42114423331` passed for the public site only. The site auto-deployed, observed
and qualified without submitting a duplicate deployment.

Live verification exposed a pre-existing HTTP409 REVIEWED_COMPOSITION_CHANGED
at public-template enrollment. Both public defaults pinned old target-profile
fingerprints; their runtime artifact fingerprints were valid. Through the
normal audited staff publication API, copied definitions changed only the
current target-profile fingerprints, using new immutable IDs. The old records
were not mutated. Public onboarding configuration now selects those current
definitions. This required a configuration-only restart of the same backend
image on each host, not a backend/framework release or a safety-gate bypass.
Runtime images, plugins, AI defaults, allowed operations, target choices, limits,
customer deployments, assignments and credentials were not changed.

After repair, staging passed 21 hosted observations and production passed 22.
These cover all three detail pages, anonymous install-to-sign-in/registration
intent, native customer setup entry forms, desktop/mobile/light/dark, product
booking links, customer navigation and Support. Production additionally clicked
the real website-to-Console-to-Calendly path and the return-to-website link.
No appointment, kit, infrastructure or AI query was submitted/created. Only the
ordinary existing customer self-enrollment calls occurred in browser testing.
Customer app configurations and protected application image tags are unchanged.

Existing public defaults remain dealership simulator and generic knowledge, as
approved for new registrations. Support installation still requires that
account's approved Support template; the project consultation requires none.
This is not new public access to private LoomAI diagnostic/support credentials.

Safe evidence is in `verification-support/kit-cards-01062/navigation/`. Future
target-profile changes must revalidate/republish affected public defaults through
the same normal workflow. Do not hide composition drift with hash edits, SQL
updates to immutable records or relaxed validation.

## In-Page Integration Booking Panel

Follow-up request: Book integration call and Start a project should open a
floating, dismissible scheduler rather than take the visitor away from the
current page. This supersedes the external-tab-only booking behavior above.

- Console uses the shared IntegrationCallButton to open a theme-aware dialog.
  The public website uses one native modal dialog in its existing base layout.
- Both load the approved Calendly event in an iframe only after a normal click.
  There is no scheduling script on the host page, automatic account prefill,
  token forwarding, customer-resource context or new scheduling backend.
- The visitor can choose a date/time and continue Calendly's booking flow inside
  the panel. Close, Escape and backdrop dismissal retain the current route and
  page state; the scheduler is removed on close. Existing link destinations
  remain usable for modified clicks and an explicit Open in new tab option.
- CSP frame access adds only https://calendly.com, preserving the existing
  self/YouTube permissions and script-source restrictions. The site's fictional
  dealership visitor pages remain outside LoomAI integration-team booking.
- Focused Console tests cover lazy loading, iframe-only URL parameters, retained
  form draft/route, close/Escape/backdrop/focus restoration, and 320/390/1440px
  layouts in both themes. The site checks include mobile-navigation modal
  stacking, ten card/theme layouts and the existing nine preview formats.

Reference: Calendly's official iframe embedding guide,
https://calendly.com/help/how-to-embed-calendly-with-an-iframe.
No appointment is submitted during verification. Hosted rollout and real
calendar slot-selection results are recorded separately after qualification.

### Booking Panel Rollout Completed

Frontend source `ab4a8778f20027a78c5579f193b17f1b718646c4` is live and
healthy on staging Console, production Console and the production public site.
Native container revisions confirm that source. Core-image CI `42125363664`
passed with the backend skipped; serving-image CI `42125363589` passed for the
public site only. The site auto-deployed without a duplicate deployment request.

Real Calendly verification passed on the website (desktop, mobile and mobile
menu) and both Consoles (public detail desktop/mobile, authenticated Support,
sidebar and mobile customer navigation). Date/time selection works inside the
panel; closing returns to the same page and restores focus after the closing
transition. No appointment, AI query or customer-resource mutation was made.
Non-frontend application image tags remain unchanged. Safe reports and public
screenshots are in `verification-support/kit-cards-01062/booking-panel/`.

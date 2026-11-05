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
including explicit computed-colour regressions. Hosted rollout is pending.

The separate request for an improved kit detail page is a visual proposal only;
no detail-page layout or commercial terms have been changed in this correction.

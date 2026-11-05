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
- No real AI query, business action, customer resource mutation or storage change
  is needed to qualify this presentation-only change.

## Rollout

UI-only hosted rollout and final native-source checks are pending. Backend,
customer runtimes, Engage assignments and Desk service must remain unchanged.
Unrelated pre-existing worktree changes are not part of this release.

# 010.61 Engage Workspace Design System And Theme Proposals

Date: 5 November 2026
Status: approved for opt-in implementation. See the [execution record](010_61_ENGAGE_STYLE_IMPLEMENTATION_RECORD.md) for verified implementation and rollout status.
Scope: customer-facing Engage/widget surfaces and their Console configuration preview

## Current Review Direction: Twelve Visual Styles

The owner clarified that functionality must not change and rejected the first
three concepts as insufficiently expressive. Focus/Studio/Concierge below remain
historical layout studies, not the proposed limit on visual variety.

The current proposal is the [twelve-style analysis and atlas](010_61_ENGAGE_TWELVE_STYLE_ATLAS_ANALYSIS.md).
It separates style (typography, colour, material, shape, density) from experience
pack, layout, delivery and AI mode. All twelve use identical sample content and
controls for comparison, with light/dark and dock/Max/standalone specimens.
No live widget, backend contract, AI configuration or deployment changed.

## Verdict

Yes: multiple reusable designs would improve Engage, especially when a buying
workspace and a support workspace should feel different. Do not achieve this by
copying the widget for every industry or creating separate AI products/backends.
Keep one conversation engine, common trust boundaries and reviewed experience
packs. Add a small presentation catalogue above those existing capabilities.

Start with three structural design directions, two complete colour schemes and
customer branding. This is not eighteen independently maintained widgets.
Docked, Max and standalone must be presentations of the same conversation.

## Current Source Inventory

The counts below describe inspected source contracts, not a fresh audit of every
live installation's configuration.

- **Two installation delivery modes:** `embedded` and `standalone`.
- **Three main experiences asked about here:** bottom companion dock, expanded
  Max workspace, and standalone app. The dock has collapsed and expanded states;
  standalone currently reuses Max with a dedicated URL/auth shell and no close
  button. It is not a third independent chat engine.
- **Two floating launcher placements:** `bottom-left` and `bottom-right`.
  The generic dock itself is bottom-centred and capped at 78rem; that placement
  does not follow the launcher placement. Icon and pill launcher variants exist.
- **One shared generic visual family**, plus a separate Shopify-specific Max
  component and the Support pack's surrounding shell. There is no generic named
  `design` or `themePreset` catalogue today. Pack-specific views are not a
  configurable set of interchangeable designs.
- **Two colour schemes and three selection policies:** light, dark and system.
  System is automatic selection, not a third colour palette. Dark-mode tokens
  exist, but fixed component and renderer colours prevent a uniform guarantee.
- **Five theme fields:** primary colour, logo URL, font family, border radius
  and dark-mode policy. Console's basic appearance form exposes logo, colour and
  light/dark/system, plus assistant name, welcome text and input placeholder;
  font/radius are configuration-level fields rather than basic form controls.
- **Three reusable installed experience packs inspected:** generic workspace,
  dealership and Support. Shopify additionally selects a specialized widget
  experience. These specify capabilities, content and renderers, not just skin.
- **Support-specific extra presentations:** compact widget/popup, inline page
  and standalone. Do not claim that the generic/dealership pack already supports
  an arbitrary inline page just because the installer parses `page`.
- **Four request-context positions:** `landing`, `catalog`, `search`, `cart`.
  These are orchestration context, not physical screen positions or designs.
  Six conversation-mode identifiers also exist; these are not visual themes.
- The synthetic Console preview enumerates launcher, embedded/dock, Max and
  standalone. A preview option alone is not proof of a separately installable
  layout or a complete runtime implementation.

### Inspected Sources

- [Widget configuration](../../../../../../../../max-mode-widget/src/config.ts)
- [Theme engine](../../../../../../../../max-mode-widget/src/theme.ts)
- [Root style tokens](../../../../../../../../max-mode-widget/src/styles/index.css)
- [Launcher/dock/Max selection](../../../../../../../../max-mode-widget/src/entries/WidgetShell.tsx)
- [Companion dock](../../../../../../../../max-mode-widget/src/components/CompanionDock.tsx)
- [Max composition](../../../../../../../../max-mode-widget/src/components/MaxModeView.tsx)
- [Request positions and modes](../../../../../../../../max-mode-widget/src/constants.ts)
- [Installer presentation parser](../../../../../../../../max-mode-widget/src/installer/presentation.ts)
- [Generic pack](../../../../../../../../experience-packs/generic-workspace-experience/src/index.ts)
- [Dealership presentation](../../../../../../../../experience-packs/dealership-experience/src/presentation.ts)
- [Support shell](../../../../../../../../experience-packs/support-experience/src/shell.ts)
- [Appearance form](../../../../../../../../Platfrom/ui/src/components/customer/EngageAppearanceFields.tsx)
- [Existing live-configuration preview](../../../../../../../../Platfrom/ui/src/components/customer/EngageAppearancePreview.tsx)
- [Backend theme contract](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceConfigurationValidator.java)

## Customer-Experience Findings

1. Theme configuration exists, but many widget classes still fix blue/purple
   colours, large radii, shadows and typography. Primary-colour/radius changes
   therefore do not consistently style every control or state. Dealership
   action renderers and Support shell have their own fixed style systems.
2. The dock has a large composer and expanded chrome. Secondary mode badges and
   technical page-position labels consume space without helping most customers.
   Keep tools in Max, not a permanent tool shelf above the docked input.
3. Generic fallback copy still contains shopping language, for example Shopping
   context, Account & Order Assistant and product-policy guidance. Packs override
   much of this; a reusable design system needs neutral defaults everywhere.
4. Max is conversation-first with panels added around it. Rich discovery needs
   an intentional results surface rather than oversized results inside a narrow
   chat bubble. Ordinary knowledge answers should not inherit that complexity.
5. Human identity, handover state and confirmed-action receipts deserve a clear,
   consistent treatment. A design must never imply a human is present, a source
   was retrieved, or a write succeeded merely because its mockup shows one.
6. Console already has useful appearance preview plumbing. Extend the existing
   configuration/preview path; do not build an unrelated design editor whose
   output differs from the installed widget.

## Separate Four Customer Choices

1. **Experience:** knowledge, dealership, support, and later Shopify. The pack
   supplies tools, copy, typed results and supported business integration.
2. **Design:** Focus, Studio or Concierge. Defines content arrangement, density,
   evidence placement and contextual panels. Any compatible pack may use it.
3. **Appearance:** Porcelain/light or Graphite/dark, system preference, brand
   colour and logo. These affect all surfaces and capability renderers.
4. **Delivery:** on the website, expanded Max, or its own app address. Launcher
   placement and optional inline mounting are separate presentation settings.

The UI should recommend a design from the selected Kit, not constrain it forever.
Template defaults remain editable. Do not expose runtime `position`/mode strings
as a visual-design picker or alter retrieval/actions when switching designs.

## Three Proposed Directions

### A. Focus

Conversation-first, restrained spacing, readable answer hierarchy and compact
source rows. Context opens on demand, not as permanently empty sidebars. A small
history/navigation column is available in standalone; Max can remain borderless.
The dock is a quiet, dismissible full-width composer with no tool group shelf.

Recommended default for generic knowledge/customer questions. It can also show
catalogue results, forms and humans using the same capability contracts.

### B. Studio

Conversation and an adjacent result workspace. Search results, comparison and
selected-item details receive meaningful space and images. Browse and current
context remain reachable independently of attachments. Selected items are clearly
labelled. Mobile becomes stacked content with a reachable composer, not two tiny
columns. Dock remains compact; expansion reveals the richer arrangement.

Recommended for dealership discovery and, later, shopping. No vehicle/cart logic
belongs to the generic design: result presenters come from the selected pack.

### C. Concierge

An AI conversation with a human team row and optional request context. Distinct
human messages, clear waiting/active/return-to-AI states, request navigation and
readable confirmation/receipt treatments. A request pane appears only when the
pack actually supplies requests. With no Desk integration there are no invented
agent photos, availability indicators or handover buttons.

Recommended for the Support Kit, service-led businesses and assisted buying.
It is a layout, not a requirement to purchase or enable Desk.

## Review Prototypes

[Open interactive review](../../../../../../../../output/engage-design-review-20261105/index.html).
It opens as local static HTML; no development server, external network, login,
AI spend or production resource is required. Bundle rebuild:
`node output/engage-design-review-20261105/build.mjs`.

The review offers three designs, all three requested surfaces, light/dark,
three sample accent colours, sample confirmation/receipt and human states,
attachment add/remove, drawers, sample typing and dock expansion/minimization.
All text, vehicle facts, agent presence and receipts are illustrative. Switching
designs uses purpose-specific sample content to explain intent; it is not proof
that a real installation's pack changes when its design changes.

Screenshots under `output/engage-design-review-20261105/screenshots/` contain each
design's light, dark, docked, Max, receipt, human and 390px mobile variants.
`verification.json` records prototype interactions and zero HTTP requests.
This validates the review artifact only, not live product feature readiness.

## Proposed Implementation Shape After Approval

Use a bounded versioned generic contract, for example `appearance.design`,
`appearance.colourScheme`, `appearance.density`, and existing branding values.
Names are provisional, not implemented API fields. Keep existing auth, tools,
attachments, handover and result-presentation contracts; layouts consume them.
Do not fork conversation state/controllers or add a new backend service.

- Central semantic tokens: surfaces, text hierarchy, borders, brand contrast,
  status colours, typography, spacing, density, elevation and component shape.
- Common primitives: composer, identity/header, source row, message, attachment,
  tool scope, action form, confirmation, receipt and handover status.
- Structural shells arrange those same primitives for Focus/Studio/Concierge.
- Pack renderers inherit documented tokens in their own rendering roots; token
  propagation must cover Shadow DOM/iframe boundaries explicitly.
- Platform persists the design choice in the existing configuration/theme path
  and validates supported identifiers. Safe CSS/image validation stays intact.
- Existing installation preparation, manifests and Console preview transport
  carry the reviewed presentation configuration. No separate preview schema that
  silently loses tools or capability state.
- Auth, deployment assignments, trusted IDs, approvals, retrieval rules and
  runtime releases are unaffected by visual selection. UI visibility does not
  grant a capability or override server authorization.
- Theme/layout switches retain messages, attachments, draft inputs and handover
  ownership. They must not remount/reset a conversation to change its appearance.
- Screen reader labels, keyboard focus and readable contrast remain baseline
  hygiene. Desktop is primary, with basic narrow/mobile navigation; no large
  accessibility certification or new mobile app programme is implied.

### Customer Configuration Journey

Kit selection recommends an experience/design. Engage setup shows a side-by-side
editor and the existing isolated preview: design thumbnails, light/dark/system,
brand colour/logo, delivery selection and focused capability toggles. Preview
supports welcome/answer/results, clarification, confirmation/cancel, receipt,
source/context, sign-in, handover and error/disconnected states. Save a draft;
activate deliberately using the current product mechanism. No AI deployment
rebuild should be needed solely to change presentation.

### Bounded Delivery Order

1. Owner reviews these three prototypes and identifies the preferred defaults.
2. Implement shared tokens/primitives with Focus; prove generic/Support/dock/Max
   and standalone parity first. Preserve an explicit existing-style option until
   the visual migration is approved, not a second runtime/framework version.
3. Add Studio arrangement and token-aware existing rich result renderers.
4. Add Concierge arrangement through existing Support/Desk contracts.
5. Wire backend/Console design selection and the actual installed preview to the
   same configuration. Verify existing installation draft/reset/apply behaviour.
6. Qualify representative existing dealership, generic and Support installations
   with real interactions, then publish reviewed assets. Shopify remains deferred
   until its own Kit readiness work; do not imply Shopify qualification here.

Avoid a drag-and-drop UI builder, arbitrary customer CSS/JS, a new theme service,
many near-identical templates or a comprehensive twenty-layout catalogue.
Three designs by two schemes by three surfaces is a test matrix, not eighteen
products. First review decisions are visual direction, default per Kit, and how
much compactness is desirable in the dock.

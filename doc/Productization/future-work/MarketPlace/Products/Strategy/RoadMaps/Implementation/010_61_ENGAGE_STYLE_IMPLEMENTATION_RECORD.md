# 010.61 Engage Styles Implementation Record

## Scope

Presentation-only implementation of the reviewed twelve-style atlas. Keep the
existing appearance as **Current**; omitted `theme.stylePreset` means Current.
No automatic restyling of existing installations. No framework release, model,
prompt, retrieval, action, authentication or runtime deployment changes.

Styles: Current, Clarity, Editorial, Obsidian, Botanical, Atelier, Kinetic,
Frost, Workshop, Terminal, Pop, Gallery and Human. All enhanced styles support
light/dark appearance, website dock, Max workspace and standalone delivery.

## Progress

- Complete: canonical style contract, palettes, bundled licensed fonts and
  opt-in rendering tokens. Current remains outside enhanced CSS selectors.
- Complete: backend validation and persistence/manifest round trips; 190 focused
  tests passed. No database migration.
- Complete: customer appearance picker and unsaved isolated preview; the UI
  agent's 72 focused tests passed before final integration.
- Complete: generic, dealership and Support pack propagation. The pack agent's
  63 focused tests passed before final integration.
- Complete: actual widget passed 52 desktop/mobile and light/dark combinations,
  plus 13 website dock checks. 39 screenshots captured. Selected images render
  through the existing explicit image-host allowlist. Same draft node,
  attachment and conversation retained; Current computed appearance restored.
- Complete: 40 integrated Console tests, 34 Support tests and 17 shared-pack /
  dealership tests passed. Theme contract verifies all palettes and contrast.
  Existing host/history, confirmation replay, semantic boundary, request context,
  action presentation and compound projection smokes passed.
- Complete: ESM/CJS, embedded, installer and preview builds; pack builds,
  TypeScript checks, CI backend/Console images and native healthy rollout on
  staging and production. Main implementation `8bf95331c`; final Console
  typography-default refinement `2bba164cf`. The latter rebuilt only Console.
- Complete: native Supabase customer sign-in opens all 13 styles and unsaved
  preview on both hosted Consoles. Selecting Current restores the old design;
  no active app configuration was modified by this preview check.
- Complete: both hosted backends persisted all 13 styles using normal app
  create/update/read APIs. Exact temporary draft deletion passed; existing app
  configurations remained unchanged.
- Complete: real production Northfield inventory and reference-bound detail
  queries, provider images, five live style switches, same input node/draft and
  conversation, basic mobile rendering, and human-support dialog passed.
  No business write action was executed; no browser errors were observed.

## Presentation Boundaries

New styles start with the desktop source panel closed. The Sources control
opens it; underlying evidence is unchanged. Source counts say sources, not
business-result counts. Technical metadata remains expandable. Long informational
answers with a structured result may be collapsed into **Full answer**, retaining
the entire canonical text. Confirmations, required fields, errors, uncertain
outcomes and human messages are never collapsed by that rule. No semantic text
matching or rewritten model decisions are introduced.

Appearance switches must preserve the existing controller, draft text,
attachments, conversation identity, auth and confirmation state. Pack-specific
result components inherit generic tokens; no vehicle logic enters the widget.

## Evidence

Evidence is stored under `verification-support/engage-styles-01061/`.
Local preview assertions are not a claim of live AI/provider qualification.

Hosted results and selected actual screenshots are in
`verification-support/engage-styles-01061/hosted/`. The installed-workspace style
switches were temporary browser-host API changes, not saved app changes.
Existing installations remain Current. Customers deliberately opt in through
Engage app -> Appearance & preview -> Workspace style.

Selecting a new style from Current replaces the inherited Inter / 0.5rem
defaults so the preset's typography and shape are actually visible. Non-default
font/shape customizations, brand colour and other configuration remain intact.
Returning to Current in the unsaved editor restores its original settings.

## Limits

This is not a framework/AI quality release. No models, prompts, routing,
retrieval, action policies, authentication or runtime images changed. Arbitrary
third-party renderers must adopt the documented tokens themselves. No claim of
complete cross-browser, WCAG or every-language/RTL certification is made; the
existing supported controls and basic mobile flow were qualified here.

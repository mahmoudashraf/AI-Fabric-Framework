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
- Complete: 39 integrated Console tests, 34 Support tests and 17 shared-pack /
  dealership tests passed. Theme contract verifies all palettes and contrast.
  Existing host/history, confirmation replay, semantic boundary, request context,
  action presentation and compound projection smokes passed.
- In progress: final production builds and hosted qualification.
- Pending: scoped commit, CI image publication, staged/production rollout and
  live Console/installed-workspace verification.

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

Evidence is being collected under `verification-support/engage-styles-01061/`.
Local preview assertions are not a claim of live AI/provider qualification.

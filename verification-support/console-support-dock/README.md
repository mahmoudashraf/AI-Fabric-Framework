# Native Console Support Dock

Implementation: `8569c565787b541f7f023ea03c4d09050e866f3e`.
Final support empty-state copy: `615c27e7bb7929773cf09e203fd1f991ebb9d50b`.
Both core-image CI runs passed: `42130548197` and `42131529333`.

## Presentation

- Ordinary customer Console pages use the existing CompanionDock input,
  expandable conversation and Max view, not a support page squeezed into a
  floating 440px card. The host uses Clarity, a teal accent and the selected
  Console light/dark mode. Other customers' pack themes are not overridden.
- One Support pack controller and isolated chat frame survive navigation,
  minimize/reopen, Max and theme changes. The dock's transparent frame area is
  clipped for hit testing so the Console remains clickable behind it.
- The dedicated Support page keeps the assigned roster, request form, My Requests,
  diagnostics and ticket lifecycle. Global support controls link back to it.
- The pack's optional trusted-host `docked` presentation is provider-neutral.
  Default widget/page/standalone installations keep their previous presentation.
- Shared dock sizing now shrinks history on short screens instead of pushing
  header controls outside the viewport. The Support empty state uses support
  copy rather than the widget's legacy shopping-specific fallback.

## Scope And Verification

No AI prompt, orchestration, action, retrieval, framework, database schema,
customer runtime image or deployment configuration changes. Platform backend
images are refreshed only because they serve integrity-checked widget assets.
The private Console adapter, original identity, ownership boundaries, runtime
assertions and generic ticket provider contract are unchanged.

Local checks: frontend build/types; widget types/installer/IIFE build and viewport
contract; 35 Support pack unit/browser/installer/theme tests; 11 Console Support
scenario groups; nine booking-panel/CSP regressions. The last empty-state-copy
change additionally passed the focused real-engine dock browser test.

Hosted reports verify the real signed-in customer shell, background click-through,
dock/expanded/Max/close, minimize/reopen, request form cancel, My Requests, retained
draft/frame across navigation and both themes, and 390/320px mobile bounds.
They do not submit a live ticket, transfer a real conversation or send an AI query.
Synthetic tests cover those transport/lifecycle and handover-modal interactions.
Private account screenshots, sessions and full deployment receipts stay private.

## Hosted Result

Final source `615c27e7bb7929773cf09e203fd1f991ebb9d50b` is healthy on both
Consoles and their Platform asset-serving backends. All four image-only
deployments finished; native container labels confirm the revision. Both backend
aggregate health endpoints returned HTTP200/UP. The final staging and production
browser reports passed all four observation groups with no errors or mutations.
Other application image tags, including Desk, public site and customer runtimes,
are unchanged. No infrastructure, template, assignment or resource configuration
was modified. The older always-open floating support-page UI is superseded.

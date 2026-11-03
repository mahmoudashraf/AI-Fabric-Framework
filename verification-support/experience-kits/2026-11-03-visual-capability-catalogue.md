# Visual Capability Catalogue

## Scope

Source `c6abd77f7db3dc1b69c7d6a394e1249cff64a04b` implements the approved
experience-card concept in Console and the public-site catalogue. The cards show
adaptive versus dealership scope, sample products/actions/human handover, three
customer-facing formats, and the Platform + Engage + optional Desk composition.
Generic and dealership detail pages in Console reuse the richer preview.

Miniatures and Preview experience open a dismissible branded view. Console's
illustrative interactions include selecting a product, comparing examples,
delivery/enquiry context and an example agent joining/returning to the AI view.
Public-site preview actions/transcripts are static, while its format and
dismissal controls are interactive. This difference is intentional: neither is
a live AI demo or an assigned support agent. The existing dealership demo link
remains the path to the actual working demonstration.

Max is a presentation layout, not a new installation delivery mode. Existing
embed/standalone/both setup, normal assignment and publishing remain unchanged.
The backend, customer runtimes, framework, prompts, retrieval, actions,
permissions and customer configurations are untouched. No preview creates a
resource, sends a message/enquiry or changes a customer session.

Furniture images are generated fictional product photos, WebP encoded at
174,896 and 117,990 bytes; provenance is in each product-media README. They
are only marketing examples, not template defaults or indexed AI evidence.

## Qualification

- 45 Console/auth/kit tests passed, zero failures/skips. Nine new preview tests
  cover formats, scope, photo rendering/assets, optional Desk, no API/auth client
  dependency, closed initial state, local interactions and cross-site fixtures.
- Console production build passed. Existing runtime-config/bundle-size warnings
  remain; they are not new errors.
- Astro check passed across 78 files with zero errors/warnings. Site production
  build, content graph and 28-route static smoke passed.
- Local browser verified the Console card and all three formats; product
  comparison, example human join, mode switching, Escape and X dismissal passed.
  Focus returns to the correct miniature. Both themes were inspected.
- Console phone check: actual 390 x 844 viewport, no horizontal overflow;
  full-screen dialog width 390 and all format/close controls available.
- Public-site local browser checked Max/website/standalone selection, Escape/X
  dismissal and restored focus. At 390px, dialog width is 374px with no document
  overflow. Static illustration controls are not dead interactive form inputs.
- No message, live AI invocation, paid resource creation or real agent assignment
  was performed by the marketing preview checks.

## Hosted Rollout

Core workflow `41561636691` succeeded: UI image published, backend image correctly
skipped. Production site job `p2dfjyyd7avyarksts5y7cav` finished at the exact source.
Staging UI job `qio6ikh6ok78iycvatbp9j7h` and production UI job
`jvr0w00sh9vsklrm6ewi9mes` finished. Both native UI containers are healthy with
exact source OCI labels. The public-site native container is healthy; its
provenance is the exact Coolify source commit, not an OCI revision label (that
image does not provide one). No backend or customer-runtime rollout occurred.

## Production Browser Evidence

Console cards render both scopes, the three surfaces, product photography and
the included/optional products. All image checks passed with no broken assets.
At actual 1440px desktop and 390px phone sizes, the Console page/dialog had no
horizontal overflow. The phone dialog is 390px wide with visible format and
close controls. Live selection, comparison, delivery context, example human
join, format switching and X/Escape/backdrop dismissal passed. Focus returns
to the triggering miniature. The authenticated detail page retains the selected
generic-kit setup link; no new kit was submitted.

Production public catalogue and homepage show the new cards. Its website and
standalone preview controls work after the real static deployment; X/Escape
restore focus. Public-site phone qualification is local source-matched evidence
above, not a claimed production phone rerun: the later browser viewport override
targeted the selected Console tab rather than the background public-site tab.
That public-site screenshot is correctly labelled desktop.

Screenshots are in `capability-cards-live-2026-11-03/`. The old document/consultation
hero montage is unchanged; the owner has now asked whether to replace it. That
is a separate design discussion, not an unfinished capability-card rollout.

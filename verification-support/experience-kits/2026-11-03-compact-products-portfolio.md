# Compact Products Portfolio

Date: 2026-11-03

## Scope

Public `/products` only: compact experience-kit, customer-facing product,
Platform and open-source foundation bands, plus a dealership simulator link.
Canonical experience labels and images reuse the existing catalogue data.
No new backend model, API, runtime setting or AI decision behavior. Desk remains
optional; Max remains a presentation layout, not another delivery-mode contract.
The full experience cards and dismissible previews remain on `/experiences`.
The old homepage montage is unchanged pending review of the generated proposal.

## Checks

- Astro check: 79 files, zero errors, warnings or hints.
- Production build: passed, 33 generated pages.
- Content graph and 28-route static smoke: passed. Added assertions check layer
  order, product and kit destinations, editable-kit wording, and absence of the
  full catalogue dialogs and old responsibility matrix on this overview.
- Local browser: light and dark themes, actual 1280px desktop, 768px tablet and
  390px phone. No main-content horizontal overflow; all images loaded.
- Generic kit navigation reaches the existing detail page; browser Back returns
  to the overview. Links use existing product and simulator destinations.

## Hosted Verification

Source `ac689635058bce9d2aefa426dcb79d264a8f65aa` is committed and pushed on
`Platform-V11`. Production site job `f1v4e0pxsprmdgk0qdhyyt67` finished at
that exact source. Native container
`/t3r7unm08sh3tfatpadz7qky-022450904828` is healthy. No Console, backend or
customer deployment rollout was performed.

Production browser checks confirmed all four ordered layers, loaded images,
light/dark themes at actual 1280px desktop and 390px phone, and no main-content
horizontal overflow. Dealership-kit, Engage and Desk links open their existing
public detail pages, and browser Back returns to the overview. Generic-kit
navigation was checked locally. This was navigation/presentation qualification,
not a new paid-kit creation or AI/provider quality run.

Screenshots: `compact-products-live-2026-11-03/`. The homepage proposal was
generated with the built-in image tool as a review-only image; it is not a new
runtime, live workspace, or promise of a preconnected customer catalogue.

## Follow-Up: Homepage Removal And Product Visuals

Owner requested removal of the old homepage montage, not installation of the
proposed replacement. Homepage headline/actions and both rich experience cards
remain. Shared montage usage on experience detail pages remains unchanged.

Products adds compact, non-interactive illustrative conversation scenes for
Engage/Desk, a fictional support-agent portrait, document examples, and reused
Google Drive/Slack source icons with provenance/license. Optional human support
is labelled explicitly. No new backend, configured integration or AI behavior.

Local qualification: Astro check (79 files), production build and 28-route/content
smoke passed. Both themes at desktop/390px phone fit without main-content
horizontal overflow. Homepage has zero montage figures and two experience cards.
Hosted follow-up source `04445eefc6e23c7f6993ad8447dc7501298a4703` is pushed.
Production public-site job `t4fqjarne9wxjiuasve99zf3` finished at exact source;
native `/t3r7unm08sh3tfatpadz7qky-112846319367` is healthy.

Live desktop and actual 390px phone checks confirm zero homepage montages and
two unchanged experience cards. Products has two illustrative conversation
scenes, two agent portraits and two document examples; source logos and visible
assets load. Both themes fit without main-content horizontal overflow.
Screenshots: `home-product-visuals-live-2026-11-03/`. No backend, Console,
customer deployment, credentials or AI behavior changed. Temporary local server
stopped; browser viewport reset.

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

Pending source publication and production public-site rollout. No Console,
backend or customer deployment rollout is required.

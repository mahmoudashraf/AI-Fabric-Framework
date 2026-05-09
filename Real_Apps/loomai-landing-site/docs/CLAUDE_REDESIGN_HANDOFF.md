# Claude Redesign Handoff - LoomAI Landing Site

Status: design handoff for creative redesign while preserving working interfaces (2026-05-09)

Use this document as the brief for a creative redesign of the LoomAI public product landing page and partner landing page. The goal is to let Claude redesign the UI, layout, visual system, and storytelling while keeping the current app functional.

---

## Pasteable Claude Prompt

You are redesigning the LoomAI landing site in `Real_Apps/loomai-landing-site`.

The current implementation works and must keep working. Redesign the visual interface creatively, but preserve the routes, forms, data attributes, runtime config bindings, health endpoint, lead endpoint, and claim-safe product positioning described below.

Files you may primarily edit:

- `Real_Apps/loomai-landing-site/public/index.html`
- `Real_Apps/loomai-landing-site/public/partners.html`
- `Real_Apps/loomai-landing-site/public/styles.css`

Avoid editing server or form JavaScript unless absolutely required:

- `Real_Apps/loomai-landing-site/server.mjs`
- `Real_Apps/loomai-landing-site/public/app.js`

If you add assets, put them under `Real_Apps/loomai-landing-site/public/assets/` and keep them local. The current CSP allows self-hosted images and data URLs, not remote images.

After redesign, the following commands must pass:

```bash
bash scripts/verify-loomai-landing-site.sh
docker build -f Real_Apps/loomai-landing-site/Dockerfile -t loomai-landing-site:local .
git diff --check -- Real_Apps/loomai-landing-site
```

Do not add public claims about Shopify App Store availability, commissions, checkout automation, refunds, returns automation, protected-customer-data automation, or arbitrary write automation unless the copy keeps the readiness-gated wording below.

---

## Product Vision

This site should feel like a product company, not an infrastructure vendor.

The homepage should focus on LoomAI products:

- `Companion` - shopper-facing commerce intelligence for Shopify.
- `Thinker` - evidence-backed diagnosis for support and operational issues.
- `Resolver` - governed follow-up and support resolution after policy, dry-run, and confirmation.
- `Product Platform` - the engine behind the products, but not the main marketing headline.

The partner page should focus on helping clients launch LoomAI products, not only Companion.

Avoid leading with:

- MCP
- Coolify
- Hetzner
- Marketplace internals
- deployment IDs
- secrets
- provider internals
- generic AI platform language

Those are part of the engine, not the public product story.

---

## Current Routes

The service is one static Node app.

| Route / Host | Page |
| --- | --- |
| `/` on normal host | Product-suite merchant/company landing page from `index.html` |
| `/partners` or `/partners/` | Public partner landing page from `partners.html` |
| `/` when host or `X-Forwarded-Host` starts with `partners.` | Public partner landing page |
| `/health` | JSON health endpoint |
| `/runtime-config.js` | Browser runtime config for public CTA URLs |
| `/api/leads` | Lead form POST endpoint |

Current staging URLs:

- Product landing: `https://loomai-landing.46.224.145.148.sslip.io/`
- Partner landing: `https://partners.loomai-landing.46.224.145.148.sslip.io/`

---

## Functional Contracts That Must Stay Intact

### Shared Runtime Config

Links with `data-config-href` are rewritten by `public/app.js` from `/runtime-config.js`.

Supported keys:

- `demoUrl`
- `privateInstallUrl`
- `partnerSignInUrl`
- `docsUrl`
- `statusUrl`

Keep this pattern:

```html
<a data-config-href="partnerSignInUrl" href="fallback-url">Partner Sign In</a>
```

You can redesign the button, but do not remove `data-config-href` where runtime-config behavior is required.

### Lead Forms

All lead forms must keep:

```html
data-lead-form
data-kind="..."
data-form-status
```

The JavaScript collects form fields by `name`. If you rename fields, update `public/app.js` and tests. Prefer keeping the field names below.

#### Product / Merchant Access Form

Current kind:

```html
data-kind="merchant-private-install"
```

Required by backend:

- `name`
- `email`
- `shopDomain`
- `consent`

Optional but useful:

- `goal`

Current copy intent:

```text
Request product access
```

The form can visually become a card, panel, drawer, wizard, split form, or inline conversion block, but it must still POST to `/api/leads` through `app.js`.

#### Partner Application Form

Current kind:

```html
data-kind="partner-application"
```

Required by backend:

- `name`
- `email`
- `company`
- `consent`

Optional but useful:

- `storeCount`
- `goal`

Current copy intent:

```text
Apply to the partner program
```

This can be redesigned as an application panel, short qualification flow, or product-support intent form, but keep the same field names unless you also update the JavaScript and verifier.

### Form Status

Each form should contain:

```html
<p class="form-status" data-form-status aria-live="polite"></p>
```

This is used for success/error messages.

### Required Headings For Smoke Tests

The smoke tests currently look for these headings:

- Product page: `LoomAI products for commerce and support work.`
- Partner page: `Help clients launch LoomAI products.`

You can improve the surrounding layout, but keep these exact headings unless you update:

- `Real_Apps/loomai-landing-site/scripts/smoke.mjs`
- `Real_Apps/loomai-landing-site/scripts/browser-smoke.mjs`

### Required Product Names

The public pages must include:

- `Companion`
- `Thinker`
- `Resolver`

The homepage should also include:

- `Loom Companion`
- `Loom Thinker`
- `Loom Resolver`
- `Loom Product Platform`

### Security And CSP

Current server CSP:

```text
default-src 'self'
script-src 'self'
style-src 'self'
img-src 'self' data:
font-src 'self' data:
connect-src 'self'
frame-ancestors 'none'
base-uri 'self'
form-action 'self' mailto:
object-src 'none'
upgrade-insecure-requests
```

Implications:

- Do not use remote images unless you also update the CSP intentionally.
- Do not add remote fonts unless you update the CSP intentionally.
- Do not add inline scripts.
- Keep form submissions same-origin through `/api/leads`.
- Do not expose webhook tokens or secrets in browser config.

---

## Homepage UI And Functionality

### Page Role

Audience:

- merchants
- operators evaluating the product suite
- potential design partners
- early product-platform prospects

Primary job:

Show the product suite clearly and route the visitor to product access.

Secondary job:

Show that there is a serious operating platform behind the products without making infrastructure the headline.

### Required Sections

You can rename section labels, reorder some sub-blocks, and redesign layout, but the page should preserve these functional areas:

1. Product-suite hero
2. Product grid
3. Companion detail
4. Thinker detail
5. Resolver detail
6. Product platform / readiness detail
7. Request access form
8. Footer

### Product-Suite Hero

Current intent:

```text
LoomAI products for commerce and support work.
Companion helps shoppers. Thinker diagnoses issues with evidence. Resolver turns approved follow-up into governed action.
```

Must communicate:

- LoomAI has multiple products.
- Companion is the first live commerce product.
- Thinker and Resolver are controlled rollout products.
- The visitor should be able to explore products or request access.

Creative directions that fit:

- command-center style product suite
- product constellation
- operating console
- commerce storefront + diagnosis + governed action timeline
- three-product split scene
- product cards with readiness states

Avoid:

- single Shopify-only hero
- generic AI chatbot hero
- abstract bokeh/orb backgrounds
- "AI agents do everything" claims

### Product Grid

Products:

```text
Loom Companion
Loom Thinker
Loom Resolver
Loom Product Platform
```

Each should have:

- short category label
- product name
- plain-language outcome
- link/anchor to detail section

### Companion Detail

Positioning:

```text
Shopper-facing intelligence for Shopify stores.
```

Allowed feature language:

- natural language product search
- product FAQ
- product comparison
- policy answers
- contextual storefront guidance
- store catalog and policy grounding
- package posture: Free, Starter, Elite
- private/design-partner installs now
- public App Store launch after approval

Avoid unsupported claims:

- broad order tracking
- returns execution
- refunds automation
- checkout completion
- protected-data automation by default

### Thinker Detail

Positioning:

```text
Diagnosis before action.
```

Allowed feature language:

- read-first diagnosis
- persisted sessions
- evidence items
- resolution plans
- audit trail
- support exports
- partner-safe redacted views

Avoid:

- autonomous operator wording
- arbitrary issue repair claims
- broad write capability

### Resolver Detail

Positioning:

```text
Governed resolution when follow-up is approved.
```

Allowed feature language:

- proposal ledger
- policy decision
- non-mutating dry-run
- exact confirmation
- idempotency controls
- execution evidence
- current governed execution scope: support escalation records

Avoid:

- broad catalog/pricing/inventory/order/refund/theme mutation claims
- "fully autonomous resolution"
- "acts without approval"

### Product Platform / Readiness

Positioning:

```text
The engine is the advantage, not the headline.
```

Should explain in product-safe language:

- managed runtime
- Marketplace capability packaging
- MCP execution
- RAG grounding
- deployment profiles
- partner/merchant approvals
- verification gates
- evidence bundles

Do not expose:

- Coolify internals
- provider app UUIDs
- deployment IDs
- API tokens
- secret names

### Request Access Form

Form intent:

```text
Request access to the right product.
```

The redesign can make the form more guided. Example product focus options:

- Companion for Shopify
- Thinker diagnosis
- Resolver governed follow-up
- Product platform discussion
- Partner-led rollout

If converting the `textarea` into buttons, chips, or a segmented control, ensure the final selected value still maps into the `goal` field or update `app.js` and tests.

---

## Partner Landing UI And Functionality

### Page Role

Audience:

- Shopify implementers
- agencies
- consultants
- technical partners
- support partners

Primary job:

Explain what partners can help launch and capture partner applications.

Secondary job:

Route approved partners to the Partner Portal login.

### Required Sections

1. Partner product hero
2. Product/workflow cards
3. Operating path
4. What partners get now
5. Partner application form
6. Footer

### Partner Hero

Current required heading:

```text
Help clients launch LoomAI products.
```

Must communicate:

- partners help clients launch products, not infrastructure
- products include Companion, Thinker, Resolver
- rollout includes scoped access, staging verification, and evidence
- approved partners sign in through the real Partner Portal

The `Partner Sign In` button must keep:

```html
data-config-href="partnerSignInUrl"
```

### Product / Workflow Cards

Keep these concepts:

- Companion
- Thinker
- Resolver
- Launch Portal

Partners should understand the actual work:

- activate storefront intelligence
- inspect diagnosis/evidence
- support governed escalation workflows
- manage scoped approvals and evidence packs

### Operating Path

Keep these workflow steps in some form:

1. Apply.
2. Select product.
3. Request merchant/client access.
4. Merchant approves and can revoke.
5. Use launch checks.
6. Produce evidence before go-live.

Do not imply partners can bypass merchant approval.

### What Partners Get Now

Keep these claims:

- partner workspace
- scoped store access
- merchant approval links
- product-specific client launch workspace
- verification packs
- evidence bundles
- support escalation workflow
- store notes and implementation history
- templates and launch guidance

Commercial copy:

```text
Commercial terms are handled during partner approval.
```

Do not publish:

- fixed commission %
- payout calculator
- public revenue share
- white-label promise
- partner tiers

### Partner Application Form

Current fields:

- `name`
- `email`
- `company`
- `storeCount`
- `goal`
- `consent`

The page should ask what products the partner wants to support:

```text
Companion, Thinker, Resolver, product launches, conversion, support, or app setup.
```

---

## Design Constraints

The redesign should be creative, but keep the page product-grade:

- Make product names visible in the first viewport.
- Make pages scannable for busy merchants and partners.
- Use real product/workflow metaphors, not abstract AI visuals.
- Cards should be 8px radius or less unless there is a strong reason.
- Text must not overflow buttons/cards on mobile.
- No nested cards inside cards.
- No provider internals.
- No secrets or operational diagnostics.
- No fake testimonials, press logos, customer logos, or metrics.
- No hidden dependency on external scripts.

Frontend-specific constraints:

- Keep layout stable on mobile and desktop.
- Use responsive constraints for fixed-format UI scenes.
- Avoid viewport-width font sizing in CSS.
- Avoid negative letter spacing.
- Avoid a one-note palette dominated by one hue.
- Do not rely on hover-only interactions for essential content.
- Keep all CTAs keyboard focusable.

---

## Suggested Creative Directions

These are optional directions Claude can choose from:

### Direction A: Product Operating Console

Hero looks like a clean product command center with three large panes:

- Companion storefront pane
- Thinker evidence pane
- Resolver approval/action pane

Strong for showing product suite and platform maturity.

### Direction B: Commerce Support Workflow

Hero shows a left-to-right product workflow:

```text
Shopper question -> Companion answer -> Thinker diagnosis -> Resolver follow-up
```

Strong for explaining why the products connect.

### Direction C: Product Cards With Live State

Hero presents product cards with status tags:

- Companion: Design partner
- Thinker: Controlled rollout
- Resolver: Governed

Strong for launch safety and clarity.

### Direction D: Split Audience Landing

Homepage distinguishes:

- Merchants: launch Companion
- Operators/support teams: use Thinker/Resolver
- Partners: manage product rollout

Strong if the page needs to route different visitor types quickly.

---

## Verification Requirements

Run this after redesign:

```bash
bash scripts/verify-loomai-landing-site.sh
docker build -f Real_Apps/loomai-landing-site/Dockerfile -t loomai-landing-site:local .
git diff --check -- Real_Apps/loomai-landing-site
```

The verifier checks:

- product-suite copy exists
- partner product-suite copy exists
- forbidden unsupported public claims are absent
- app routes work
- partner host routing works
- `/runtime-config.js` works
- `/api/leads` accepts valid lead JSON
- desktop/mobile browser rendering has no horizontal overflow
- core buttons are not collapsed

If headings or required copy intentionally change, update:

- `Real_Apps/loomai-landing-site/scripts/smoke.mjs`
- `Real_Apps/loomai-landing-site/scripts/browser-smoke.mjs`

Do not weaken forbidden-claim checks to pass a design.

---

## Current File Map

```text
Real_Apps/loomai-landing-site/
  Dockerfile
  README.md
  package.json
  server.mjs
  public/
    index.html
    partners.html
    styles.css
    app.js
  scripts/
    smoke.mjs
    browser-smoke.mjs
  docs/
    CLAUDE_REDESIGN_HANDOFF.md
```

---

## Final Instruction To Claude

Redesign the interface so it feels original, modern, and product-led, but keep the functional contracts intact. If a visual idea conflicts with lead capture, runtime config, host routing, security posture, or claim safety, preserve the working contract and choose a different visual treatment.

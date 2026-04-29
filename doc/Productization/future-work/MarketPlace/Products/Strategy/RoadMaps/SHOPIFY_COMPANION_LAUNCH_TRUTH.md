# Shopify Companion Launch Truth

Status: canonical launch truth (2026-04-24)

This document is the short source of truth for Shopify Companion launch positioning, tier scope, and surface gating.

Use this before updating:

- pricing copy
- App Store copy
- merchant admin UI
- partner enablement material
- launch packets
- support docs
- roadmap claims

If another document conflicts with this one, this document wins until superseded by a newer launch-truth decision.

---

## 1) Canonical Product Claim

Launch claim:

- **Loom Companion is embedded store intelligence for Shopify.**

Allowed language:

- AI product intelligence for Shopify
- embedded store intelligence
- make every product page smarter
- AI shopping assistant for Shopify stores
- read-first product discovery and policy assistant

Do not lead with:

- AI chatbot
- autonomous sales agent
- full AI enablement layer
- AI employee
- AI that runs your store

Interpretation:

- embedded surfaces are the product identity
- chat is the depth layer
- shopper actions stay user-controlled
- write-capable behavior must be governed, audited, and explicitly tiered

---

## 2) Canonical Tiers

Current launch tiers:

1. **Free**
2. **Starter**
3. **Elite**

Historical names:

- `Growth`
- `Pro`

Rule:

- `Growth / Pro` must not appear in current launch, App Store, billing, partner, or merchant-facing copy unless clearly marked as historical.

---

## 3) Tier Truth

### 3.1 Free

Purpose:

- distribution wedge

Canonical scope:

- **AI search only**

Includes:

- AI search surface
- 50-product catalog cap
- daily knowledge sync posture
- mandatory `Powered by Loom Companion` badge
- documentation/community support

Does not include:

- order lookup
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- Companion chat
- write actions
- email support
- custom appearance

Decision:

- Free is **AI search only**, not AI search plus order lookup.

Required alignment:

- billing entitlement
- storefront gating
- merchant UI
- App Store listing
- partner intelligence catalog
- docs and support material

### 3.2 Starter

Purpose:

- first serious paid product

Canonical scope:

- full read-only embedded store intelligence

Includes:

- product insight block
- AI search
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only Companion chat/depth layer
- unlimited products
- products, pages, policies, collections, and approved content sources
- basic analytics
- optional `Powered by Loom Companion` badge
- custom accent color
- email support posture

Does not include:

- order lookup
- cart actions
- return/exchange initiation
- support ticket creation
- discount application
- governed write actions
- Deep Resolver claims

### 3.3 Elite

Purpose:

- read + governed action tier

Canonical scope:

- Starter plus verified governed actions

Allowed only when implemented and verified:

- governed add-to-cart
- cart update
- variant guidance if implemented as a real governed or bounded surface
- customer-safe order lookup if explicitly moved into Elite
- support handoff only when support behavior is real
- action audit history
- confirmation governance
- merchant controls for action classes

Do not advertise until verified:

- return/exchange initiation
- support ticket creation
- discount/coupon application
- abandoned cart recovery
- counter-offer governance
- Deep Resolver
- broad autonomous support claims

Rule:

- Elite must not launch as a promise bundle.

---

## 4) Surface Matrix

| Surface / Capability | Free | Starter | Elite | Notes |
|---|---:|---:|---:|---|
| AI search | yes | yes | yes | Free entry point and distribution wedge |
| Product insight block | no | yes | yes | Read-only embedded surface |
| Product FAQ | no | yes | yes | Grounded product Q&A |
| Comparison | no | yes | yes | Read-first, evidence-backed comparison |
| Policy strip | no | yes | yes | Contextual policy surface |
| Contextual pill | no | yes | yes | Embedded context/depth entry |
| Companion chat/depth layer | no | read-only | read + governed actions | Chat is not product identity |
| Order lookup | no | no | gated | Only if scoped, verified, and intentionally assigned to Elite |
| Add-to-cart | no | no | gated | Governed action only |
| Cart update | no | no | gated | Governed action only |
| Variant guidance | no | no | gated | Must be real bounded behavior before claim |
| Support handoff | no | no | gated | Must match live implementation |
| Return/exchange initiation | no | no | future | Do not claim at launch |
| Discount/coupon application | no | no | future | Do not claim at launch |
| Deep Resolver | no | no | future | Later orchestration enhancement |
| Basic analytics | no | yes | yes | Usage, top questions, surfaces |
| Advanced analytics/export | no | no | future/gated | Only after real reporting exists |
| Powered-by badge | required | optional | none | Free badge is mandatory |

---

## 5) Launch-Ready Story

The launch story should be:

> Make every product page smarter with AI search, product insights, FAQs, comparisons, policy context, and a deeper companion when shoppers need it.

Feature order:

1. Smart product pages
2. AI search
3. Product FAQ
4. Product comparison
5. Contextual policies
6. Contextual pill
7. Companion chat/depth layer

Do not put chat first.

---

## 6) Partner Enablement Truth

Partner positioning:

- developers, integrators, and agencies can use LoomAI to add intelligence pieces to client stores without building RAG, sync, governance, and observability from scratch

Partner catalog should reflect the same tier truth:

- Free: AI search only
- Starter: full read-only embedded intelligence
- Elite: governed actions only where verified

Partner enablement should include:

- sandbox/demo access
- intelligence-piece catalog
- setup templates
- verification packs
- support escalation
- scoped store access
- implementation playbooks

Do not lead partner material with:

- passive acquisition
- white-label
- partner API
- custom product assembly

Those are later modules.

---

## 7) Roadmap Consequences

Immediate consequences:

- Update any active launch or partner copy that says `Growth / Pro`.
- Update any active Free-tier copy that includes order lookup.
- Update any active App Store copy that leads with chatbot.
- Keep Elite claims bounded to verified governed actions.
- Keep full AI enablement layer language out of launch copy.

Active roadmap sequence remains:

1. Canonical launch truth
2. Storefront product shell
3. Starter launch package
4. Partner enablement foundation
5. Design-partner proof
6. Public launch push
7. Elite activation
8. Second-product gate

---

## 8) Open Checks

The following should be audited against this launch truth:

- `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md`
- `LOOM_COMPANION_SHOPIFY_LAUNCH_PLAN.md`
- `LOOM_COMPANION_GO_TO_MARKET_PLAYBOOK.md`
- `LOOM_COMPANION_OUTREACH_AND_CONTENT_STRATEGY.md`
- `LOOM_COMPANION_PARTNER_PROGRAM_STRATEGY.md`
- `PARTNER_DASHBOARD_STRATEGY_PLAN.md`
- `RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md`
- merchant admin billing copy
- storefront entitlement gates
- App Store listing package
- partner intelligence catalog

---

## 9) Decision Log

- 2026-04-24: Free tier is AI search only. Order lookup is not Free.
- 2026-04-24: Current tier names are `Free / Starter / Elite`.
- 2026-04-24: Starter is full read-only embedded store intelligence.
- 2026-04-24: Elite claims are limited to verified governed actions.
- 2026-04-24: Launch story leads with embedded store intelligence, not chatbot.

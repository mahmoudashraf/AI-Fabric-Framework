# Loom Companion for Shopify — Launch Plan

Status: historical launch plan (2026-04-19; superseded for launch tier truth on 2026-04-25)

Note:

- one-app packaging and launch sequencing in this document are still useful
- pricing, plan naming, and order-lookup placement here are historical in places
- current launch tiers are `Free / Starter / Elite`
- current Free scope is AI search only; order lookup is not Free or Starter
- use [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md) for current tiering
- use [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md) for the current builder-mode roadmap

---

## 1) What This Document Is

This document defines the product catalog, tier structure, and launch sequence for Loom Companion on the Shopify App Store. It answers:

- What capabilities the platform can produce for Shopify merchants
- Whether to ship as one app or multiple apps
- How capabilities tier into pricing plans
- What ships when, and in what order

This is a product launch plan, not an architecture document. The architecture already exists in Platform-V5 and the Shopify Bridge Service.

---

## 2) Product Identity

**Company:** LoomAI Labs

**Product:** Loom Companion

**App Store listing:** Loom Companion — AI Shopping Companion

**One-line pitch:** Helps your customers find the right product, compare options, read reviews, and understand your policies — without waiting for support.

**Category positioning:** Shopping companion, not sales agent. Helps shoppers decide, does not push them to convert. Trust-building, not conversion-optimising.

**Why this positioning:**
- Amazon Rufus validated the shopping companion category at scale
- Nobody builds it for the 4.6 million non-Amazon merchants
- Read-only launch avoids write OAuth scopes and accelerates App Store review
- Platform-V5 strengths (multi-source RAG, attribution, rich cards) align perfectly with read-first value

---

## 3) One App, Not Multiple

Loom Companion ships as a single Shopify app with tiered pricing plans. Not multiple apps.

### Why one app

1. **One install, one review.** Every separate app starts at zero reviews. One app accumulates all social proof in one place. Reviews compound — splitting them is self-sabotage.

2. **Merchant fatigue.** Merchants optimise around 5-15 apps. They will not install three apps from the same company. They will upgrade one app they already trust.

3. **Upgrade friction is nearly zero.** Shopify's billing API supports plan changes in-app. The Shopify Bridge Service billing module already handles this.

4. **App Store review is once.** Each separate app goes through full review. One app with capability tiers reviews once, then plan changes are billing-only.

5. **The platform already supports it.** The action framework has read/write access modes. Tier gating is deployment configuration, not new code.

### When a separate app IS justified

Only when all three conditions are true:

- Different runtime model (not conversation-based)
- Different buyer persona (not CX/marketing)
- Different UX surface (not chat widget)

Only Loom Insights (Smart Brain batch processing) would qualify. Layers 1-3 are all chat, all shopper-facing, all the same widget.

---

## 4) Capability Layers

### Layer 1: Shopping Companion (Launch)

The core product. Read-only assistance for shoppers.

**Capabilities:**
- Product search and discovery ("show me running shoes under $100")
- Product comparison ("compare these two jackets side by side")
- Review summaries ("what do customers say about this product?")
- Policy Q&A ("what's your return policy?" "how long does shipping take?")
- Collection browsing ("show me your summer collection")
- Size and fit guidance from product data

**Runtime requirements:**
- Read-only actions
- Multi-source RAG (products + policies + reviews)
- Source attribution in answers

**Shopify OAuth scopes:**
- `read_products`
- `read_content`

**What the merchant gets:** Smarter shoppers who find what they need, fewer repetitive support questions, better informed purchase decisions.

### Layer 2: Support Agent (historical Growth upgrade)

Everything in Layer 1, plus write-enabled customer support.

**Additional capabilities:**
- Order status lookup ("where's my order?")
- Shipping tracking with carrier data
- Return and exchange initiation (with confirmation flow)
- FAQ resolution from merchant's help centre
- Support ticket creation (escalation to human)

**Runtime requirements:**
- Read + write actions
- Confirmation interception for write operations
- Order and customer data access

**Additional Shopify OAuth scopes:**
- `read_orders`
- `read_customers`
- `write_draft_orders`

**What the merchant gets:** Fewer support tickets, faster WISMO (where is my order) resolution, 24/7 basic support coverage.

### Layer 3: Sales Assistant (historical Pro upgrade)

Everything in Layer 2, plus conversion and retention tools.

**Additional capabilities:**
- Abandoned cart recovery conversations
- Discount and coupon application (within merchant-defined rules)
- Cross-sell and upsell suggestions from product graph
- Retention offers ("10% off if you keep this order instead of cancelling")
- Post-purchase follow-up prompts

**Runtime requirements:**
- Deep Resolver pattern (investigate-then-act loop)
- Counter-offer governance flows
- Discount and order write permissions

**Additional Shopify OAuth scopes:**
- `write_discounts`
- `write_orders`

**What the merchant gets:** Higher average order value, reduced cancellation churn, cart recovery without email fatigue.

---

## 5) Pricing Structure

| | Free | Historical Growth ($29/mo) | Historical Pro ($79/mo) |
|---|---|---|---|
| Layer 1 — Companion | 50 conversations/mo | Unlimited | Unlimited |
| Layer 2 — Support | — | Included | Included |
| Layer 3 — Sales | — | — | Included |
| Analytics | Basic | Full | Full + export |
| Branding | Loom badge | Custom colours + logo | Full white-label |
| Knowledge sources | Products only | + Policies + reviews | + Custom sources |
| Merchant playground | — | Included | Included |
| Support | Community | Email | Priority |

### Pricing rationale

- **Free tier** exists to drive installs and reviews. 50 conversations is enough for small stores to experience value and leave a review.
- **Historical Growth at $29** was the older paid-tier placeholder; current launch truth uses Starter at $29 for full read-only embedded intelligence.
- **Historical Pro at $79** was the older action-tier placeholder; current launch truth uses Elite at $179 and only for verified governed actions.

---

## 6) Launch Sequence

### Phase 1: Companion (Month 1-2)

Ship Layer 1 only. Free and Starter plans.

**What ships:**
- Shopping companion with read-only actions
- Product search, comparison, reviews, policy Q&A
- Merchant admin dashboard (sync status, playground, analytics)
- Theme app extension (storefront widget)
- Shopify billing integration (Free + Starter tiers)

**Why Layer 1 only:**
- Read-only OAuth scopes = fastest App Store approval path
- Smallest attack surface for first review
- Proves the companion category before adding complexity
- The core RAG + attribution value stands alone

**Success criteria for Phase 1:**
- App Store approval within 2 weeks of submission
- 20 installs within first month
- 4.0+ average rating from first 10 reviews
- Merchants report shoppers using companion for product questions

### Phase 2: Support Agent (Month 3-4)

Historically, this added Layer 2 to Growth. Current launch truth keeps order lookup and support actions out of Starter; move only verified governed support/action surfaces into Elite when ready.

**What ships:**
- Write actions with confirmation governance
- Order status lookup, return initiation, ticket creation
- Additional OAuth scopes (triggers App Store scope review)

**Gate:** Only ship Phase 2 if Phase 1 achieves 20+ active installs. Do not add write complexity to an unvalidated product.

**Success criteria:**
- Scope review approved without issues
- Starter conversion rate increases because merchants want the full read-only embedded intelligence package
- Measurable reduction in merchant support ticket volume

### Phase 3: Sales Assistant (Month 5-6)

Historically, this added Layer 3 as Pro. Current launch truth uses Elite for verified governed actions only.

**What ships:**
- Deep Resolver pattern for multi-step resolution
- Abandoned cart recovery, discount application, retention offers
- Counter-offer governance flows
- Elite billing tier

**Gate:** Only ship Phase 3 if Phase 2 shows merchants actively using write actions and asking for conversion features. Do not build sales tools on assumption.

**Success criteria:**
- Elite adoption from Starter subscribers
- Measurable AOV or retention improvement for Elite merchants
- Deep Resolver pattern stable in production

### Phase 4: Loom Insights — separate app (Month 8+)

Only if Smart Brain runtime is built.

**What it is:**
- Background batch analysis (not conversation)
- Review sentiment analysis, product performance scoring, customer pattern detection
- Dashboard UX, not chat UX
- Different buyer (ops/analytics, not CX)

**Gate:** Only build if Smart Brain runtime exists and Loom Companion has 100+ installs proving the Shopify channel works.

---

## 7) How Layers Map to the Platform

The platform already supports capability gating per deployment. No new runtime code is needed for tier differentiation.

**Action framework:**
- Actions have read/write access modes
- Layer 1 = read actions enabled
- Layer 2 = read + write actions enabled, confirmation interception active
- Layer 3 = read + write + Deep Resolver orchestration mode

**Shopify Bridge Service:**
- Billing service manages plan tiers via Shopify billing API
- Install flow is identical for all tiers
- Webhook sync scope varies: products always, orders at Layer 2+
- Theme extension is the same widget — capabilities differ by backend config
- Admin UI shows plan-appropriate screens

**Deployment configuration:**
- Each merchant's deployment is configured with an action set matching their plan
- Upgrading a plan = updating the deployment's action configuration
- No rebuild, no redeploy — configuration change applied at runtime

---

## 8) Competitive Positioning

| | Loom Companion | Rep AI | Manifest AI | Tidio |
|---|---|---|---|---|
| Core positioning | Shopping companion | Sales agent | GPT shopping | Live chat + AI |
| Primary value | Trust + informed decisions | Conversion rate | AOV increase | Support deflection |
| Buyer motivation | "Help my customers" | "Increase my revenue" | "Sell more" | "Reduce support cost" |
| Starting price | Free | $29/mo | $99/mo | $29/mo |
| RAG quality | Multi-source + attribution | Basic product search | GPT + product feed | Template-based |
| Read-only option | Yes (core product) | No | No | No |
| Write governance | Confirmation flows | Direct execution | Direct execution | N/A |

### Differentiation that matters

1. **Multi-source RAG with attribution.** Competitors cannot answer "what's your return policy for electronics specifically?" by grounding across product data, policy documents, and reviews with source attribution.

2. **Read-only by design.** Competitors went write-first and fight App Store reviews, broken carts, and write failures. Read-only is a feature, not a limitation.

3. **Trust positioning.** "Help your customers decide" is a different value proposition from "increase your conversion rate." Different buyer psychology, lower resistance, higher retention.

---

## 9) Distribution Plan

### Month 1: Active outreach (zero organic expected)

- Shopify community forums: 2-3 posts showing companion in action on demo store
- r/shopify, r/ecommerce: authentic posts about the companion approach
- Twitter/X: demo videos, before/after of shopper experience
- Direct outreach to 20 Shopify merchants in target verticals (fashion, electronics, home goods)
- Goal: 5-10 installs from direct effort

### Month 2-3: First reviews

- Follow up with every install for feedback
- Ask satisfied merchants for App Store reviews
- Publish 2-3 blog posts: "How AI shopping companions reduce support load," "Why shoppers leave without buying (and how to fix it)"
- Goal: 10-30 installs, 5+ reviews at 4.5+ stars

### Month 4-6: Organic traction

- If reviews hold, App Store algorithm starts surfacing the app in category searches
- "AI shopping assistant" and "product comparison" become searchable categories
- Publish case study from strongest merchant result
- Goal: 30-100 installs, organic > outbound

### Month 6+: Compound growth

- Reviews + ranking + category presence compound
- Content SEO drives blog traffic to App Store listing
- Merchant referrals begin (if product delivers)
- Goal: 100+ installs, Starter plan conversion rate stable

---

## 10) Success Metrics

### Product metrics

- **Conversations per store per day:** target 5+ (proves shoppers use it)
- **Completion rate:** percentage of conversations where shopper gets a useful answer
- **Source attribution rate:** percentage of answers grounded in merchant's actual data
- **Escalation rate:** percentage of conversations that need human support fallback

### Business metrics

- **Installs:** 50 in 3 months, 100 in 6 months
- **Review rating:** 4.5+ average
- **Starter plan conversion:** 20% of free installs upgrade within 30 days
- **Monthly recurring revenue:** $1,000 MRR by month 6 from Starter subscribers
- **Churn:** under 5% monthly for Starter plan

### Visa-relevant metrics

- Active merchant count (demonstrates scalable product)
- Revenue (demonstrates viable business)
- UK entity with product on international marketplace (demonstrates innovation and scalability)

---

## 11) Factory Discipline Rules

These rules keep the product-factory model working. Break any of them and the factory degrades into a custom shop.

1. **Every product need either goes into the platform or gets cut from the product.** No one-off code in the bridge service that should be a platform capability.

2. **Configuration, not code, differentiates tiers.** Plan upgrades change deployment config, not deployment code.

3. **The widget is the same for all tiers.** Capabilities differ by backend response, not by frontend build.

4. **Ship Layer 1 before building Layer 2.** Do not add complexity to an unvalidated product.

5. **The first 50 installs come from outreach, not organic.** Budget the effort. "Let it sell itself" is Month 6, not Month 1.

6. **Hire or contract ops support at install 20-50.** Support load will exceed solo capacity before revenue justifies it. Budget for this.

---

## 12) What This Document Does Not Cover

- Shopify Bridge Service implementation details (already built in Platform-V5)
- Platform architecture and runtime design (covered in existing architecture documents)
- Smart Brain runtime design for Loom Insights (separate track, covered in NEW_DEPLOYMENT_TYPE_CONCEPTS_EVALUATION.md)
- Legal entity setup and UK incorporation specifics
- Shopify App Store listing copy and screenshots
- Detailed marketing content calendar
- Customer support playbook and escalation procedures

---

## 13) Summary

Loom Companion ships as one Shopify app with three plan tiers. Each tier unlocks a capability layer: shopping companion (read-only), support agent (read-write), and sales assistant (Deep Resolver). The platform already supports this through action access modes and deployment configuration — no new runtime code for tier gating.

Ship Layer 1 alone. Prove it works. Add layers as plan upgrades. The only separate app comes if Smart Brain ships and justifies a different product for a different buyer.

The factory built the product. The product is in PR #153. What remains is: merge, deploy, submit, and grind through the first 50 installs.

# LoomAI Labs Pricing and Licensing Strategy

Status: planning document (2026-04-26)

This document defines pricing and licensing across every layer of LoomAI Labs. It clarifies who pays for what, what is sold today, and what pricing decisions are deferred until demand is real.

The existing [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md) covers end-merchant pricing for the Loom Companion product. This document covers the platform-wide picture and how all the pieces fit together.

---

## 1) The Core Mental Model

LoomAI Labs has seven sellable layers. Each layer has a different customer.

```
LAYER                          CUSTOMER                  WHAT THEY GET
──────────────────────────────────────────────────────────────────────────
1. Framework core              developer community       open-source foundation
2. Bridge SDK                  developer community       open-source build kit
3. Hosted platform             operator / agency         multi-product runtime
4. Products (Loom Companion)   end merchants             ready-to-use AI app
5. Partner program             integrators / agencies    distribution channel
6. White-label / enterprise    enterprise customers      branded deployment
7. LLM pass-through            built into subscription   underlying AI cost
```

The confusion most founders have is trying to price all seven layers at once. Most of these layers have no customer yet. Today, you sell three things — everything else becomes real when demand for it appears.

---

## 2) What Is Sold Today

These are the only revenue streams that matter in the next 12 months.

### 2.1 Loom Companion Subscription (to merchants)

```
Free:     $0/month         AI search only, 50 products
Starter:  $29/month         All read-only intelligence, unlimited products
Elite:    $179/month        Read + write + governance + Thinker
```

Source of truth: [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)

### 2.2 Partner Commission Program (to integrators)

```
Cost to join:                $0
Commission rate:             20% recurring (Starter and Elite plans)
Tier bumps:                  25% at 25+ active merchants
                             30% at 100+ active merchants
Attribution window:          90 days
Payment cadence:             monthly, 30 days after merchant payment clears
```

Source of truth: [LOOM_COMPANION_PARTNER_PROGRAM_STRATEGY.md](LOOM_COMPANION_PARTNER_PROGRAM_STRATEGY.md)

### 2.3 Optional White-Label and Custom Engagements

```
Frequency:                   rare, deal-by-deal
Pricing:                     starts $5,000/month + revenue share
                             OR $50,000+ project fee + ongoing platform fee
Terms:                       custom, contract-based
Volume expected in Year 1:   0-3 deals
```

Not advertised publicly. Inbound only. No standardized package.

---

## 3) What Is Deferred

These layers exist architecturally but have no customer yet. Pricing them now is premature.

### 3.1 Hosted Platform Tiers

**For:** developers, agencies, or operators who want to run their own product on the platform.

**Why deferred:** no demand yet. The platform serves only Loom Companion today. When a second product or a third-party operator wants to run a deployment, this becomes real.

**Tentative shape (do not advertise):**

```
Indie:        $99/month       1 deployment, 5K conversations/mo
Studio:       $499/month      5 deployments, 50K conversations/mo
Agency:       $1,999/month    25 deployments, 250K conversations/mo
Enterprise:   custom          Unlimited, dedicated, SLA
```

**Decision trigger:** ship the second product (WooCommerce Companion or Document Q&A) and have at least one external operator request a hosted deployment.

### 3.2 Self-Hosted Enterprise License

**For:** companies with compliance requirements that block cloud deployment (healthcare, finance, government).

**Why deferred:** no inbound interest yet. Self-hosting requires support obligations the founder cannot deliver alone.

**Tentative shape (do not advertise):**

```
Annual license:   $50,000 - $200,000 per year
Includes:         platform binary, install support, security updates, 12-month support
Excludes:         custom development, on-site support, SLA guarantees
```

**Decision trigger:** at least 3 inbound enterprise inquiries with budget confirmed.

### 3.3 Public API Access for Developers

**For:** developers building their own applications on top of the LoomAI runtime.

**Why deferred:** no public API exposed yet. The platform is consumed only by internal product services and the Shopify Bridge Service.

**Tentative shape:**

```
Free tier:    1,000 calls/month, rate-limited
Developer:    $49/month, 50,000 calls/month
Production:   $299/month, 500,000 calls/month
Scale:        usage-based after $299
```

**Decision trigger:** publish a public API surface and have at least 10 developers building against it.

### 3.4 Open-Source Framework Core

**For:** the developer community.

**Why deferred:** open-sourcing the framework is a brand and ecosystem decision, not a revenue decision. It only makes sense once the platform has paying customers proving the commercial layer works.

**Recommended timing:** Year 2, after Loom Companion proves the platform and at least one second product ships.

**Recommended license:** MIT or Apache 2.0 for framework core, commercial license for hosted platform.

**Decision trigger:** Year 2 review of brand strategy and developer acquisition needs.

---

## 4) Pricing Principles

### 4.1 Bundle LLM Costs by Default

Merchants do not want to think about token costs. Every paid tier should include LLM usage in the subscription price.

**Margin protection:**

- Starter ($29/month) typical LLM cost: $3-5/month → 83-90% gross margin
- Elite ($179/month) typical LLM cost: $8-15/month → 92-96% gross margin

Margins remain healthy even with partner commission and LLM cost bundled.

### 4.2 Optional BYO LLM Key for Elite and Above

Some Elite merchants and most enterprise customers already have OpenAI/Anthropic contracts. Allow them to bring their own LLM key.

**When BYO is offered:**

- Elite tier: optional, 15% discount when activated
- White-label / enterprise: standard, customer pays providers directly

**Why:** removes margin from your side but increases willingness to pay for the platform layer because the customer feels they have leverage on the AI cost.

**Margin impact:** lower LLM cost absorbed by you, slightly lower subscription price, similar net margin.

### 4.3 Predictable Base + Usage Cap

Hybrid pricing is how Twilio, Stripe, and OpenAI all work. It is the right model for any usage-sensitive product.

**Pattern:**

```
Subscription:     fixed monthly fee
Includes:         a generous baseline allowance
Overage:          per-unit pricing past the allowance
Soft caps:        merchant gets warning at 80% of allowance
Hard caps:        merchant chooses whether overage is allowed or blocked
```

For Loom Companion specifically:

- Starter: includes 5,000 conversations/month, $0.02 per conversation over (when implemented)
- Elite: includes 25,000 conversations/month, $0.015 per conversation over (when implemented)

**Decision:** overage pricing should not ship in Year 1. Most stores will not exceed the baseline. Add overage when a customer hits the cap and asks for more.

### 4.4 Partners Pay Nothing, Earn Recurring

The partner program is a distribution channel, not a revenue source. Partners pay nothing to join. Their incentive is recurring commission, not status or certification.

**Why this matters:**

- removes friction from partner recruitment
- aligns partner success with platform success
- prevents the partner program from becoming a services trap (charging partners for "training" or "certification" is how programs lose credibility)

### 4.5 No Price Discrimination by Geography

Same prices in every country. $29/month is $29/month whether the merchant is in London, Cairo, Lagos, or Mumbai.

**Why:**

- localized pricing is operationally complex for a solo founder
- Shopify already adjusts displayed currency, not actual price
- the partner program creates the local affordability path (lower-cost local partners can serve smaller markets)

**Decision trigger to revisit:** Year 3+ if data shows specific markets are under-converting due to price.

---

## 5) Customer-Pays-Once Map

Each customer pays for exactly one layer, sometimes consumes lower layers for free.

| Customer type | What they buy | Pricing model |
|---|---|---|
| Open-source developer | Framework + SDK (after Year 2) | Free |
| Indie developer | Hosted platform Indie tier | $99/month |
| Agency / studio | Hosted platform Studio tier | $499-$1,999/month |
| Shopify merchant (free) | Loom Companion Free | $0/month |
| Shopify merchant (growth) | Loom Companion Starter | $29/month |
| Shopify merchant (premium) | Loom Companion Elite | $179/month |
| Partner / integrator | Nothing — earns commission | $0 / 20%+ revenue |
| White-label client | Branded LoomAI deployment | $5K+/month + share |
| Enterprise / restricted | Self-hosted license | $50K-$200K/year |

No customer pays for two layers at once. A merchant pays for Loom Companion. They do not also pay for the platform underneath. An agency that runs its own deployment on the hosted platform tier does not also pay per-merchant — they charge their own customers however they want.

---

## 6) The Stripe / Vercel / Shopify Comparison

This is how mature platforms structure pricing. LoomAI's structure mirrors them.

```
                   Open core            Platform tier            End product           Channel program
──────────────────────────────────────────────────────────────────────────────────────────────────────
Vercel             Next.js (free)       Pro/Enterprise           N/A                   Vercel Partners
Stripe             Stripe.js libs       Stripe API per usage      N/A                   Stripe Partners
Shopify            Liquid templates     Subscription per store    N/A                   Shopify Partners
LoomAI Labs        Framework (Year 2)   Hosted platform tiers     Loom Companion        LoomAI Partners
```

The pattern:

- give away the developer layer (drives adoption)
- charge for the operational layer (platform fees, per-usage, per-deployment)
- charge end customers for products built on the platform (subscriptions)
- pay channel partners commission for distribution (not a revenue source)

LoomAI is following the same pattern. The only difference is sequence — Vercel built Next.js first then the platform; LoomAI built the platform first and will open the framework second.

---

## 7) Pricing Decisions To Make Now

In priority order:

### 7.1 Decide: bundled LLM cost or BYO?

**Default:** bundled in all tiers below Elite. BYO optional at Elite. BYO standard at white-label/enterprise.

**Reason:** merchants do not want to manage token costs. Margin remains strong.

### 7.2 Decide: usage caps in Year 1?

**Default:** no overage pricing in Year 1. Generous baseline. Add overage only when a real customer needs more.

**Reason:** simplicity wins for first 100-500 merchants. Overage adds billing complexity that is not worth it yet.

### 7.3 Decide: partner commission tier bumps?

**Default:** keep the bump structure (20% / 25% / 30%) defined in the partner program doc.

**Reason:** rewards top partners, signals that partners can grow within the program, costs little because tier bumps only kick in once partners deliver real volume.

### 7.4 Decide: white-label availability?

**Default:** quietly available, inbound only, no public pricing page. Decided per deal.

**Reason:** white-label deals consume founder time. Limit to deals with strategic value. Do not put up a "white-label" page that attracts low-quality inbound.

---

## 8) Pricing Decisions To Defer

Do not spend time on these until demand is real.

| Decision | Defer until | Why |
|---|---|---|
| Hosted platform tier prices | Second product ships | No customers yet |
| Self-hosted enterprise license price | 3+ inbound inquiries | No ICP confirmed |
| Public API pricing | Public API exposed | API does not exist publicly |
| Open-source framework license | Year 2 brand review | Strategic, not financial |
| Per-conversation overage rates | First merchant hits cap | Premature optimization |
| Geographic pricing tiers | Year 3 conversion data | No volume to analyze yet |
| Annual prepay discounts | Month 6 churn data | Need data on willingness to commit |
| Free trial for Elite | First 50 Elite merchants | Need conversion data first |

---

## 9) Margin Reality

### 9.1 Per-tier margins (current pricing)

```
Tier        Price/mo    LLM cost    Partner cut   Net margin    %
Free        $0          ~$0.50      $0            -$0.50        N/A
Starter     $29         ~$3-5       $5.80         ~$18-20       62-69%
Elite       $179        ~$8-15      $35.80        ~$128-135     72-76%
```

Net margins after LLM and partner commission are healthy. Free tier is a planned loss leader for distribution.

### 9.2 What erodes margin over time

- LLM cost increases (mitigated by multi-provider routing and BYO option)
- Support cost as customer base grows (mitigated by Egypt-based support hires)
- Infrastructure cost (mitigated by efficient platform architecture, already done)
- Partner commission tier bumps (offset by volume growth)

### 9.3 What protects margin

- No sales team (partner program replaces it)
- No account managers below Enterprise tier
- Self-serve onboarding (no white-glove deployment in Starter)
- Bundled LLM with provider price flexibility
- Multi-tenant architecture (one infrastructure serves all customers)

---

## 10) When to Revisit Pricing

Revisit pricing when one of these is true:

- 100+ paying merchants on Starter (validate $29 is correct)
- 25+ paying merchants on Elite (validate $179 is correct)
- 25+ active partners (validate 20% commission is correct)
- First merchant exceeds usage baseline (validate overage model)
- First 3 white-label inquiries (validate white-label price)
- First 3 enterprise inquiries (validate self-hosted price)
- LLM provider price drops 50%+ (consider tier price reduction or margin capture)
- A competitor lowers prices significantly (evaluate response, do not race to bottom)

Do not revisit pricing on a calendar schedule. Revisit it when data demands it.

---

## 11) Pricing Communication Rules

These rules keep the pricing story coherent across the website, App Store listing, partner materials, and sales conversations.

### 11.1 What every public-facing surface must say

- Free / Starter / Elite tier names and prices for Loom Companion
- 20% recurring partner commission for the partner program
- "Bundled LLM costs included" for paid tiers

### 11.2 What public-facing surfaces must not say

- specific LLM provider details (OpenAI, Anthropic) — keep it abstract as "best-in-class AI"
- specific token costs or LLM markup
- self-hosted enterprise pricing (deal-by-deal only)
- white-label pricing (deal-by-deal only)
- hosted platform tier prices (until second product ships)
- public API pricing (until API exists)

### 11.3 What to say when asked

- "Can I bring my own LLM key?" → "Available at Elite tier with a 15% discount, standard at enterprise"
- "Is there an annual discount?" → "Not yet. We will announce annual plans once we have data on customer commitment patterns"
- "Can you white-label this?" → "Yes, on a deal-by-deal basis. Reach out and we will discuss"
- "Is it open source?" → "The framework will be open source in Year 2. The platform stays commercial"
- "Can I self-host?" → "For enterprise customers with compliance requirements, yes. Reach out and we will discuss"

---

## 12) Summary

**Today, sell three things:**

1. Loom Companion subscription (to merchants)
2. Partner commission program (to integrators)
3. Optional white-label / custom (rare, by deal)

**Defer everything else** until demand is real.

**Keep margins healthy** by bundling LLM costs, avoiding sales team overhead, and using the partner program as the distribution layer.

**Revisit pricing on data, not calendar.** 100 paying merchants is the trigger to validate Starter pricing. 25 Elite merchants validates Elite. First overage request triggers usage pricing design. First enterprise inquiry triggers self-hosted pricing.

The platform-wide pricing question is answered once you accept that you do not need to price what you do not sell yet.

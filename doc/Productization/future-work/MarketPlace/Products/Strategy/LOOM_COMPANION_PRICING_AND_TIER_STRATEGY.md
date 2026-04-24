# Loom Companion Pricing and Tier Strategy

Status: planning document (2026-04-22)

This document defines the pricing model for Loom Companion on the Shopify App Store. It replaces the three-layer pricing from the original launch plan with a cleaner two-tier paid model built around a single dividing line: read vs. read+write.

---

## 1) The Dividing Line

The entire tier structure is built on one distinction:

- **Starter** answers questions
- **Elite** takes action

This is the clearest possible line for merchants. They understand it immediately. "Starter helps customers find products. Elite helps customers resolve issues and buy."

Every feature falls on one side of this line. No ambiguity, no feature-by-feature comparison charts, no confusion about which plan does what.

---

## 2) Tier Definitions

### 2.1 Free ($0/month)

**Purpose:** distribution engine. Free stores show "Powered by Loom Companion" badge that links to the App Store listing. Every free store is a billboard.

**What it includes:**

- AI search surface only (one intelligence surface)
- 50 products maximum
- Daily knowledge sync
- "Powered by Loom Companion" badge (mandatory, cannot be removed)
- Documentation and community support only

**What it does NOT include:**

- Product insights, FAQ, comparison, policy strip, or contextual pill
- Any write actions
- Email support
- Custom appearance

**Why this scope:**

- AI search is the most visible and impressive surface — it hooks merchants who try it
- 50-product limit means small stores get full value, larger stores need to upgrade
- mandatory badge drives organic installs through storefront visitors
- low enough value that merchants upgrade quickly, high enough to leave a review

### 2.2 Starter ($29/month)

**Purpose:** the workhorse tier. Most merchants live here. Full read-only intelligence across every surface.

**What it includes:**

- All read-only intelligence surfaces:
  - Product insights block
  - AI-powered search
  - Product FAQ block
  - Product comparison tool
  - Policy strip
  - Contextual pill
  - Companion chat (read-only mode)
- Unlimited products
- Knowledge sync every 2 hours
- Knowledge sources: products, pages, policies, collections
- Basic analytics (queries per day, popular questions, surface usage)
- "Powered by Loom Companion" badge (optional — can be removed)
- Custom accent color
- Email support (48-hour response)

**What it does NOT include:**

- Write actions of any kind
- Order status lookup
- Return or exchange initiation
- Cart assistance
- Discount or coupon application
- Confirmation governance
- Deep Resolver
- Full appearance customization
- Priority support

**What the merchant gets:**

- Every product page becomes smarter
- Shoppers find products faster through AI search
- Common questions answered without support tickets
- Policy questions resolved instantly
- Product comparisons help undecided shoppers commit

### 2.3 Elite ($179/month)

**Purpose:** the premium tier for merchants who want verified governed actions, not just read-only answers.

**What it includes:**

Everything in Starter, plus only verified governed action surfaces:

- governed add-to-cart when enabled and verified
- governed cart update when enabled and verified
- customer-safe order lookup only when explicitly assigned to Elite and verified
- confirmation governance for action classes
- action audit history
- merchant controls for enabled action classes
- no mandatory "Powered by" badge
- advanced analytics or exports only when actually implemented

**What the merchant gets:**

Everything Starter provides for product discovery, plus:

- bounded shopper actions with explicit confirmation
- auditability for action attempts and completions
- a launch-safe path to action handling without broad autonomous support claims

---

## 3) Pricing Rationale

### 3.1 Why $29 for Starter

- Undercuts Rep AI ($29-$599) and Manifest AI ($99-$799) at entry
- Matches the low end of Shopify app pricing norms
- Low enough for impulse install after seeing a free store demo
- High enough to signal real value (not a toy)
- Covers per-store LLM and infrastructure costs with healthy margin

### 3.2 Why $179 for Elite

- Positioned as premium but not enterprise
- Verified governed actions with confirmation and audit are meaningfully harder than read-only intelligence
- Merchants evaluating Elite are already seeing action-intent demand in Starter analytics
- High enough margin to fund partner commissions (20% = $35.80/month per Elite store)
- Room for future Enterprise tier above at $500+ without repricing existing tiers

### 3.3 Why not $79 for Elite

- $79 undervalues write actions and governance — these are the hardest features to build and the most valuable to merchants
- $79 leaves no room for a mid-tier in the future if needed
- The gap between $29 and $79 is small enough that merchants compare features instead of categories — at $29 vs. $179, they think in terms of "do I need actions or not?" which is the right question

### 3.4 Why not $299+ for Elite

- Unproven product with zero reviews cannot command $299
- $179 is the "try it and see" price point — merchants will test it for a month
- $299 is the "I need to get approval" price point — adds friction
- Can raise to $249 or $299 after proving ROI with data from early Elite merchants

---

## 4) The Upgrade Path

### 4.1 Free to Starter

Trigger: merchant hits 50-product limit, or wants more intelligence surfaces beyond AI search.

The Free tier gives enough value to experience the product but not enough to stop wanting more. A merchant with 200 products on the Free tier sees AI search working on 50 of them — upgrading is obvious.

### 4.2 Starter to Elite

Trigger: merchant's customers ask questions that require write actions.

This is the key insight. Starter merchants will see conversations where shoppers ask "where's my order?" or "can I return this?" and the AI responds with "I can help you find products and answer questions — ask your store about order management." The merchant sees real demand from their own customers. The upgrade sells itself.

### 4.3 Built-in upsell mechanics

- Starter analytics dashboard shows "questions the AI couldn't answer" — most of these will be order/action questions
- Monthly email digest to Starter merchants: "Your customers asked 47 action questions this month — Elite could have resolved them automatically"
- In-app banner (non-intrusive): "Customers asked about orders 23 times this week"

---

## 5) Revenue Projections

### 5.1 Month 6

```
200 Free stores      ×  $0/month    =  $0
80 Starter stores    ×  $29/month   =  $2,320
15 Elite stores      ×  $179/month  =  $2,685
                                       ─────────
Total MRR                           =  $5,005
Partner commission (20%)            =  -$1,001
Net MRR                             =  $4,004
```

### 5.2 Month 12

```
500 Free stores      ×  $0/month    =  $0
200 Starter stores   ×  $29/month   =  $5,800
50 Elite stores      ×  $179/month  =  $8,950
                                       ─────────
Total MRR                           =  $14,750
Partner commission (20%)            =  -$2,950
Net MRR                             =  $11,800
```

### 5.3 Month 18

```
1,200 Free stores    ×  $0/month    =  $0
400 Starter stores   ×  $29/month   =  $11,600
100 Elite stores     ×  $179/month  =  $17,900
                                       ─────────
Total MRR                           =  $29,500
Partner commission (20%)            =  -$5,900
Net MRR                             =  $23,600
ARR                                 =  $283,200
```

### 5.4 Conversion assumptions

- Free to Starter: 25-30% convert within 60 days
- Starter to Elite: 15-20% convert within 90 days
- Monthly churn: 4% Free, 3% Starter, 2% Elite

Elite churn is lowest because merchants who pay $179 have measured ROI and depend on the write actions.

---

## 6) Competitive Pricing Position

```
                Free    Entry     Mid       High
─────────────────────────────────────────────────
Loom Companion  $0      $29       $179      —
Rep AI          —       $29       $99       $599
Manifest AI     —       $99       $199      $799
Tidio           $0      $29       $59       $289
Gorgias         —       $60       $300      $750
Siena AI        —       custom    custom    custom
```

Loom Companion positioning:

- matches competitors at entry ($29)
- beats Manifest AI on entry by 3.4x
- the $29 to $179 jump is steep enough to signal premium
- no merchant is priced out at entry
- Elite at $179 is cheaper than Gorgias mid-tier ($300) while offering governance that Gorgias does not have

---

## 7) Partner Commission by Tier

```
Free:     $0    (no commission — but partner gets attribution for future upgrades)
Starter:  $5.80/month per store  (20% of $29)
Elite:    $35.80/month per store (20% of $179)
```

Partner incentive naturally pushes toward Elite deployments:

- a partner with 20 Elite merchants earns $716/month passive income
- a partner with 20 Starter merchants earns $116/month
- partners will recommend Elite to merchants who need write actions

This aligns partner incentives with product value — partners push Elite when it genuinely helps the merchant, not as an upsell for commission.

---

## 8) Future Tier Considerations

### 8.1 Enterprise ($499-999/month)

Not at launch. Consider when:

- Shopify Plus merchants request it
- 50+ Elite merchants prove the product at scale
- merchants ask for features like: dedicated infrastructure, SSO, custom SLA, API access, multiple store management under one account

### 8.2 Possible Future Mid-Tier ($79-99/month)

Possible mid-tier if the gap between $29 and $179 proves too wide. Would include:

- limited write actions (order status only, no returns/exchanges)
- 6-hour sync instead of real-time
- basic analytics export

Only introduce this if data shows merchants wanting write actions but refusing $179. Do not add a tier on speculation.

### 8.3 Price Increases

After 12 months with proven ROI data:

- Starter could move to $39
- Elite could move to $199 or $249
- Grandfather existing merchants at their original price for 12 months

---

## 9) LLM Cost Per Tier

### 9.1 Estimated per-store monthly LLM costs

```
Free:     ~$0.50-1.00   (limited queries, 50 products)
Starter:  ~$3-5         (full read-only, all surfaces)
Elite:    ~$8-15        (read+write, Deep Resolver, more complex queries)
```

### 9.2 Margin per tier

```
Free:     negative ($0 revenue, ~$1 cost) — acceptable for distribution
Starter:  ~$24-26 margin ($29 - $3-5 cost) — 83-90% gross margin
Elite:    ~$164-171 margin ($179 - $8-15 cost) — 92-96% gross margin
```

LLM costs are the primary variable cost. Infrastructure (hosting, vector DB, sync) is shared across all stores and scales sub-linearly.

The margins are strong. Even after partner commission (20%), net margins remain:

```
Starter net margin:  ~$18-20/store/month (after commission + LLM)
Elite net margin:    ~$128-135/store/month (after commission + LLM)
```

---

## 10) Tier Feature Matrix

| Feature | Free | Starter | Elite |
|---|---|---|---|
| **Price** | $0/mo | $29/mo | $179/mo |
| | | | |
| **Intelligence Surfaces** | | | |
| AI Search | yes | yes | yes |
| Product Insights | — | yes | yes |
| Product FAQ | — | yes | yes |
| Product Comparison | — | yes | yes |
| Policy Strip | — | yes | yes |
| Contextual Pill | — | yes | yes |
| Companion Chat | — | read-only | read + verified governed actions |
| | | | |
| **Knowledge** | | | |
| Products | 50 max | unlimited | unlimited |
| Pages | — | yes | yes |
| Policies | — | yes | yes |
| Collections | — | yes | yes |
| Custom Sources | — | — | future/gated |
| Sync Frequency | daily | every 2 hours | faster/gated |
| | | | |
| **Actions** | | | |
| Read-only actions | search only | all read actions | all read actions |
| Order status | — | — | gated order lookup only if verified |
| Return/exchange | — | — | future |
| Cart assistance | — | — | governed add-to-cart/cart update when verified |
| Ticket creation | — | — | future |
| Deep Resolver | — | — | future |
| Counter-offers | — | — | future |
| Discount application | — | — | future |
| | | | |
| **Appearance** | | | |
| Powered by badge | mandatory | optional | no badge |
| Custom accent color | — | yes | yes |
| Full customization | — | — | future/gated |
| | | | |
| **Analytics** | | | |
| Basic dashboard | — | yes | yes |
| Advanced + export | — | — | future/gated |
| Unanswered questions | — | yes | yes |
| | | | |
| **Support** | | | |
| Documentation | yes | yes | yes |
| Email support | — | 48-hour | 24-hour priority |

---

## 11) How This Changes the Launch Sequence

### Phase 1: Free + Starter (Month 1-2)

Ship read-only intelligence surfaces with Free and Starter tiers.

- Free: AI search only, 50 products, mandatory badge
- Starter: all read-only surfaces, unlimited products

This is the same as the original Layer 1 launch but with the updated naming and pricing.

### Phase 2: Elite (Month 3-5)

Add verified governed actions, confirmation governance, and the Elite tier.

- governed add-to-cart and cart update first
- customer-safe order lookup only if deliberately assigned to Elite and verified
- confirmation interception for enabled action classes
- action audit history and merchant controls

Gate: only ship Elite if Starter has 30+ active merchants proving the read-only value.

### Phase 3: Elite Advanced (Month 6-8)

Add advanced Elite features only after the basic governed-action tier is real:

- Abandoned cart recovery
- Discount and coupon application
- Cross-sell and upsell
- Retention offers

Gate: only ship if 10+ Elite merchants are actively using write actions.

---

## 12) Naming Decision

**Why "Elite" not "Pro":**

- "Pro" is overused in SaaS — every product has a Pro tier
- "Elite" signals genuine premium, not just "more features"
- "Elite" justifies the price gap ($29 to $179) — it is not just "a bit more," it is a different category
- "Elite" gives room for a future mid-tier if needed without launch naming conflicts

**Why "Starter" not "Growth":**

- "Starter" is honest — this is where merchants start their AI journey
- "Growth" implies revenue growth which is a promise the read-only tier does not directly deliver
- "Starter" pairs well with "Elite" — clear hierarchy without forcing a middle tier name

---

## 13) Summary

Three tiers, one clean line:

- **Free** — AI search only, 50 products, distribution engine
- **Starter** ($29/month) — all read-only intelligence, full product catalog
- **Elite** ($179/month) — Starter plus verified governed actions only

The line between Starter and Elite is read vs. read+write. Merchants understand it in one sentence. Partners can explain it in one breath. The upgrade path is built into the product — shoppers asking action questions that Starter cannot answer is the most honest upsell possible.

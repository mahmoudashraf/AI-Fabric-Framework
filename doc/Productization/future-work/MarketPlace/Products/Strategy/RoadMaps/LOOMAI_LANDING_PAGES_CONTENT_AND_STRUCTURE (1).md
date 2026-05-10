# LoomAI Landing Pages - Product-Suite Content and Structure

Status: product-suite correction (2026-05-09)

This revision corrects the earlier single-product landing direction. Loom Companion is still the first live commerce product, but the public LoomAI landing page must present the product line: Companion, Thinker, Resolver, and the product platform behind them.

The site should sell products, not infrastructure. MCP, Coolify, Marketplace, RAG, deployment profiles, and release gates stay behind the scenes as the operating engine.

---

# loomai.pro

## The Whole Page in One Sentence

Show LoomAI as a product company with clear product entries:

- Companion: shopper-facing commerce intelligence
- Thinker: evidence-backed diagnosis
- Resolver: governed follow-up and support resolution
- Product Platform: the managed engine behind repeatable product rollout

## Structure

```text
NAV
PRODUCT-SUITE HERO
PRODUCT GRID
COMPANION DETAIL
THINKER DETAIL
RESOLVER DETAIL
PLATFORM ENGINE / READINESS
REQUEST ACCESS
FOOTER
```

## Nav

```text
LoomAI    Products    Companion    Thinker    Resolver    Partners    [Request Access]
```

## Section 1: Product-Suite Hero

Primary headline:

```text
LoomAI products for commerce and support work.
```

Supporting copy:

```text
Companion helps shoppers. Thinker diagnoses issues with evidence. Resolver turns approved follow-up into governed action.
```

First viewport must show product cards for Companion, Thinker, and Resolver. Do not make Companion the only first-viewport signal.

Claim posture:

```text
Companion is the first live commerce product. Thinker and Resolver are controlled rollout products for evidence-backed diagnosis and governed resolution.
```

## Section 2: Product Grid

Cards:

- `Loom Companion` - Shopify storefront intelligence for product discovery, product answers, comparison, policy context, and shopper guidance.
- `Loom Thinker` - evidence-backed diagnosis with sessions, evidence, plans, audit trail, and exports.
- `Loom Resolver` - governed follow-up using proposals, policy checks, dry-run, confirmation, and execution evidence.
- `Loom Product Platform` - the managed runtime behind the suite: Marketplace plugins, MCP execution, RAG pipelines, deployment profiles, and verification gates.

## Section 3: Companion Detail

Companion is the most concrete commercial product today.

Allowed claims:

- natural language product search
- product FAQ
- product comparison
- policy answers
- contextual storefront guidance
- private/design-partner install posture
- public App Store launch only after approval

Do not claim broad order, checkout, returns, refunds, or protected-data automation unless the target store and package have the required external gates and live verification.

## Section 4: Thinker Detail

Thinker should be described as:

```text
Diagnosis before action.
```

Allowed claims:

- read-first diagnosis
- persisted sessions
- evidence items
- resolution plans
- audit trail
- support exports
- partner-safe redacted views for assigned stores

Do not describe Thinker as an unrestricted autonomous operator.

## Section 5: Resolver Detail

Resolver should be described as:

```text
Governed resolution when follow-up is approved.
```

Allowed claims:

- proposal and policy ledger
- non-mutating dry-run
- exact confirmation
- idempotency controls
- execution evidence
- current governed execution scope: support escalation records

Do not claim broad write automation across catalog, pricing, inventory, refunds, orders, billing, or themes.

## Section 6: Platform Engine / Readiness

The platform should be present as the product engine, not the primary offer.

Language:

```text
The engine is the advantage, not the headline.
```

Readiness labels:

- Companion: live staging, design-partner launch posture
- Thinker: controlled rollout, evidence-backed diagnosis
- Resolver: governed support escalation scope, no broad write automation claim
- Platform: managed runtime, Marketplace, MCP, deployment, verification, evidence

## Section 7: Request Access

Form should collect:

- name
- work email
- Shopify store or company domain
- product focus: Companion, Thinker, Resolver, or product platform
- consent

The CTA is `Request Access`, not only `Request Private Install`.

---

# partners.loomai.pro

## The Whole Page in One Sentence

Let qualified partners apply to help clients launch LoomAI products, not only Loom Companion.

## Structure

```text
PRODUCT-SUITE HERO
PARTNER PRODUCT CARDS
OPERATING PATH
WHAT PARTNERS GET NOW
APPLY
```

## Section 1: Partner Product Hero

Headline:

```text
Help clients launch LoomAI products.
```

Supporting copy:

```text
Companion for storefront intelligence. Thinker for evidence-backed diagnosis. Resolver for governed follow-up when action is approved.
```

## Section 2: Partner Product Cards

Cards:

- Companion: activate shopper-facing search, FAQ, comparison, policy context, and storefront guidance.
- Thinker: inspect evidence-backed sessions, redacted support handoffs, plans, and launch evidence.
- Resolver: operate governed support escalation workflows with policy, dry-run, confirmation, and audit.
- Launch Portal: scoped merchant approval, staging checks, evidence bundles, support notes, and readiness proof.

## Section 3: Partner Operating Path

```text
1. Apply and get approved.
2. Select the product focus.
3. Request merchant scoped access.
4. Merchant approves and can revoke.
5. Use launch checks and templates.
6. Produce evidence before go-live.
```

## Section 4: What Partners Get Now

- Partner workspace and scoped store access
- Merchant approval links
- Product-specific client launch workspace
- Verification packs and evidence bundles
- Support escalation workflow
- Store notes and implementation history
- Templates and launch guidance

Commercial terms stay private during partner approval. Do not publish commission percentages, payout claims, or white-label promises until contracts, attribution, billing, and payout workflows are implemented and release-gated.

## Section 5: Apply

Form should ask what products the partner wants to support:

```text
Companion, Thinker, Resolver, product launches, conversion, support, or app setup.
```

---

# Claim Rules

- Lead with product names and customer-facing outcomes.
- Keep readiness labels visible.
- Do not lead with MCP, Coolify, Hetzner, or internal deployment architecture.
- Do not show public App Store install CTA until listing approval and install URL are live.
- Do not claim unsupported order, checkout, returns, refunds, or broad protected-data automation.
- Do not promise public partner commissions or revenue share until the commercial workflow exists.
- Do not imply Thinker/Resolver execute arbitrary writes.
- Use real product/workflow visuals, not abstract AI artwork.

---

# Implementation Record

Implemented package: `Real_Apps/loomai-landing-site`

Routes:

- `loomai.pro` / `/` serves the product-suite page.
- `partners.loomai.pro` / `/` serves the partner application page when the host or forwarded host starts with `partners.`
- `/partners` also serves the partner page.
- `/health` exposes service health.
- `/runtime-config.js` exposes public CTA targets only.
- `/api/leads` records product access requests and partner applications.

Verification:

```bash
bash scripts/verify-loomai-landing-site.sh
docker build -f Real_Apps/loomai-landing-site/Dockerfile -t loomai-landing-site:local .
```

Current staging deployment:

- Merchant/product suite: `https://loomai-landing.46.224.145.148.sslip.io/`
- Partner application: `https://partners.loomai-landing.46.224.145.148.sslip.io/`

The implementation keeps lead webhook tokens server-side, does not expose provider/deployment internals, and keeps public copy in a readiness-labeled product posture.

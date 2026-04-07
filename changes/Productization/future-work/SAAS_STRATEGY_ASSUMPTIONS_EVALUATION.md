# SaaS Strategy Assumptions Evaluation

Status: evaluation document (2026-04-07)
Evaluates: SAAS_AI_ENABLEMENT_AND_INTEGRATIONS_STRATEGY.md
Baseline: rebased on Platform-V4 (797 files, 137K+ additions over main)

---

## Executive Verdict

The SaaS strategy document is **directionally correct but operationally overscoped**. After rebasing on Platform-V4, the codebase is closer to SaaS-ready than the strategy document assumes in some areas, but further away in others. The document treats 70+ integrations as a roadmap when only 5 are needed for launch. The pricing model doesn't account for LLM costs. The timeline is optimistic by roughly 2x.

**Score: 7/10 as a vision document, 4/10 as an execution plan.**

---

## 1) SaaS Readiness Assessment — UPDATED AFTER PLATFORM-V4

### What the strategy document claims

The document says multi-tenant architecture is "Implemented" and lists it as existing capability.

### What Platform-V4 actually shows

Platform-V4 significantly advances the multi-tenancy story beyond what the strategy document knew about:

| Capability | Strategy Doc Assumption | Platform-V4 Reality |
|---|---|---|
| Customer entity | "Implemented" | **Confirmed.** `PlatformCustomerEntity` with id, slug, status, platformManaged flag |
| Tenant entity | "Implemented" | **Confirmed.** `PlatformTenantEntity` with customerId FK, slug, status |
| Customer → Tenant hierarchy | Implied | **Confirmed.** `customerId` field on tenant entity creates the boundary |
| Shared vector handles | Not mentioned | **Exists.** `PlatformTenantSharedVectorSummary`, `PurgePlatformTenantSharedVectorHandlesRequest` — tenant-scoped shared storage is real code, not just a plan |
| Vectorization control plane | Listed as existing | **Confirmed and extensive.** 8+ vectorization services including VectorizationRunnerService, VectorizationVerificationService, checkpoint tracking, deployment cleanup |
| Widget with Shopify | Listed as existing | **Confirmed.** `max-mode-widget/src/integrations/shopify.ts` with multi-auth mode support |
| Runtime tenant isolation | Implied as done | **Still incomplete.** The AI Fabric runtime (`AISearchRequest`, `RAGOrchestrator`) still lacks tenantId propagation. Platform-level tenant identity exists, but runtime query isolation is not wired through |

**Updated verdict:** Multi-tenancy is ~70% implemented (was ~40% before Platform-V4). The identity model and shared vector infrastructure exist. The remaining gap is runtime query-path isolation.

---

## 2) Shopify-First Strategy — CORRECT, NOW STRONGER

### What changed with Platform-V4

The auth planning documents in `changes/Productization/future-work/Auth/` now provide a concrete implementation sequence:

1. Shared auth foundation first (stop trusting caller identity)
2. Private runtime mode as default
3. Public runtime browser mode as opt-in
4. Shopify and assistant as consumers of the shared auth, not separate stacks

This is the right sequencing. The `AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md` explicitly says auth must land before Shopify app backend — which corrects the strategy document's implicit assumption that these could be parallelized.

The `SHOPIFY_APP_ARCHITECTURE_PLAN.md` covers shop-to-deployment binding, merchant billing via Shopify Billing API, and the security bridge pattern. This is concrete enough to execute.

**Verdict: Keep. Shopify-first is now backed by detailed auth and architecture plans.**

---

## 3) 70+ Integrations — STILL SCOPE CREEP

Platform-V4 does not change this assessment. The strategy document lists:

- 10 communication channels (Slack, Teams, WhatsApp, Messenger, Instagram, Email, SMS, Intercom, Zendesk, LiveChat)
- 5 CRM integrations (Salesforce, HubSpot, Zoho, Pipedrive, Freshdesk)
- 8 knowledge integrations (Notion, Confluence, Google Drive, SharePoint, Airtable, PDF, Website crawler, YouTube)
- 4 payment integrations
- 4 logistics integrations
- 6+ analytics tools
- 4 auth providers
- 4 automation platforms

**What you actually need for SaaS launch (Wave 1-2):**

1. Shopify connector (partially exists — widget + planned app backend)
2. Stripe (only if expanding beyond Shopify billing)
3. PDF/document upload for knowledge ingestion
4. Website crawler for automated RAG
5. Outbound webhooks (covers Zapier/Make indirectly via existing REST connector)

**What can wait until you have paying customers (Wave 3+):**
- WhatsApp Business API (first communication channel expansion)
- WooCommerce (second vertical)
- Everything else

The existing REST connector abstraction handles most integration patterns. Building dedicated connectors for Salesforce, HubSpot, etc. is the old iPaaS playbook. Let Zapier handle the long tail.

**Verdict: Reduce from 70+ to 5 for launch. Build more only when customers request them.**

---

## 4) Pricing Tiers — NEEDS REWORK

### Strategy document proposes

```
Starter:    $49/mo  — 1 deployment, 5K conversations
Growth:     $149/mo — 3 deployments, 25K conversations
Business:   $399/mo — 10 deployments, 100K conversations
Enterprise: Custom
```

### Problems

1. **LLM cost math doesn't work.** At $0.03-0.10 per GPT-4 conversation (including context), 5K conversations = $150-500/mo in LLM costs alone. A $49/mo tier loses money from day one.

2. **ONNX local embeddings help with embedding costs, not generation costs.** The strategy document correctly identifies ONNX as a cost advantage for embeddings ($0 vs cloud APIs), but the per-conversation LLM generation cost is the real margin killer.

3. **Shopify app pricing conventions.** Shopify merchants expect $29, $49, $79, $99, $199 tiers. The $149 and $399 tiers feel misaligned with the ecosystem.

4. **Overage at $0.008/conversation** is below cost. Should be $0.02-0.05 minimum.

### Recommended revision

```
Starter:    $49/mo  — 1 deployment, 1K conversations (use GPT-4o-mini)
Growth:     $99/mo  — 2 deployments, 5K conversations (GPT-4o-mini default, GPT-4 optional)
Business:   $199/mo — 5 deployments, 20K conversations
Enterprise: Custom  — dedicated infra, SLA, any model
Overage:    $0.03/conversation
```

Use model routing: simple questions → GPT-4o-mini ($0.003/conversation), complex questions → GPT-4 ($0.05/conversation). This is achievable with the existing multi-provider support.

**Verdict: Fix pricing. Account for LLM generation costs, not just embedding costs.**

---

## 5) Timeline — STILL OPTIMISTIC, BUT LESS SO

### Strategy document says: 14 months across 5 waves

### Platform-V4 changes the math

Platform-V4 has already delivered significant infrastructure:
- Customer/Tenant entities and services ✓
- Shared vector handle management ✓
- Vectorization control plane with 8 services ✓
- Widget with Shopify integration ✓
- Deployment verification automation ✓
- Detailed auth implementation sequence plan ✓

This means **Wave 1 (auth + billing) is partially de-risked** — the auth plan is concrete and the tenant foundation exists.

### Revised timeline estimate

| Wave | Strategy Doc | Revised Estimate | Rationale |
|---|---|---|---|
| Wave 1: Auth + Billing | 3 months | 3-4 months | Auth plan is detailed but "stop trusting caller identity" touches many code paths |
| Wave 2: Shopify App Store | 2 months | 2-3 months | Widget exists, but Shopify app review + embedded admin UI is new work |
| Wave 3: Channels | 2 months | Defer | Don't build until you have Shopify revenue |
| Wave 4: Ecosystem | 3 months | Defer | Build on customer demand only |
| Wave 5: Enterprise | 4 months | Defer | Only with enterprise pipeline |

**Realistic time to Shopify App Store launch: 5-7 months** (was 5 in strategy doc, was my previous estimate of 6-8 before seeing Platform-V4 progress).

Platform-V4 shaved ~1 month off by having the tenant model and vectorization already built.

**Verdict: 5-7 months to first sellable product. Waves 3-5 should not be scheduled — build on demand.**

---

## 6) Competitive Positioning — NEEDS ONE ADDITION

### What the strategy document gets right

- Comparison to Tidio, Gorgias, Rebuy, Certainly, Siena AI is fair
- Differentiation claims (action-grounded, confirmation safety, multi-provider, ONNX cost advantage) are all verified in code
- Open-core trust model is a real differentiator

### What's missing: Shopify's own AI

The document does not mention **Shopify Sidekick** (Shopify's built-in AI assistant for merchants) or **Shopify Magic** (AI features embedded in the admin). Shopify is aggressively building AI into the platform itself.

**Why this matters:** If your Shopify-first strategy succeeds, Shopify itself is your biggest competitive threat. They could add customer-facing AI assistants to every store natively.

**Mitigation (add to strategy):**
- Loom AI targets the **customer-facing storefront experience** (helping shoppers), not the **merchant admin experience** (helping store owners). Shopify Sidekick targets the latter.
- Loom AI offers **cross-platform** deployment. A merchant who also has WooCommerce, a custom site, or WhatsApp needs one AI assistant everywhere — Shopify's AI only works inside Shopify.
- Loom AI offers **action-grounded responses** with confirmation safety for write operations (returns, exchanges, order modifications). Shopify's AI features are currently read-only.

**Verdict: Add Shopify Sidekick/Magic as a competitive threat with the above mitigations.**

---

## 7) Connector Architecture — USE WHAT YOU HAVE

### Strategy document proposes

A `ConnectorRegistry` with dedicated connectors per platform, each implementing `authenticate()`, `syncEntities()`, `executeAction()`, `handleWebhook()`.

### What Platform-V4 already provides

- REST connector abstraction (generic, configurable)
- Vectorization services with source connections, transformation rules, execution runners
- Deployment-scoped provider secret bindings for secure credential management

### Recommendation

Do NOT build ShopifyConnector, SalesforceConnector, HubSpotConnector as separate implementations. Instead:

1. **Shopify:** Build as a dedicated app backend (it's special — needs OAuth install flow, Billing API, theme extension). This is correctly planned in `SHOPIFY_APP_ARCHITECTURE_PLAN.md`.
2. **Everything else:** Use the existing REST connector + vectorization pipeline + outbound webhooks. Configure, don't code.
3. **Zapier/Make:** Add outbound webhook delivery + inbound webhook receiver. This gives you 5000+ app connections for the cost of one integration.

**Verdict: Build Shopify-specific. Use REST connector + webhooks for everything else. Don't build an iPaaS.**

---

## 8) North Star Alignment Check

The `AI_ASSISTANT_PRODUCT_NORTH_STAR_AND_SCOPE.md` (updated in Platform-V4) defines the product as:

> "grounded, configurable, integration-ready AI assistants"

The SaaS strategy document is broadly aligned with this, but introduces scope that drifts from it:
- Building 70+ connectors drifts toward "we are an integration platform"
- Building analytics dashboards, Segment/Mixpanel integrations drifts toward "we are an analytics platform"

The North Star says: connect assistant to data, connect to APIs/actions, keep grounded, configure without code, deploy safely.

**The SaaS strategy should be filtered through this lens.** If an integration doesn't directly make the assistant more grounded, more configurable, or more deployable — it's out of scope for launch.

---

## Summary: Assumptions Scorecard

| # | Assumption | Verdict | Action |
|---|---|---|---|
| 1 | Multi-tenant is implemented | **Partially correct** (70% after V4) | Update: runtime isolation still needed |
| 2 | Shopify is the right first vertical | **Correct** | Keep, now backed by auth plan |
| 3 | 70+ integrations needed for SaaS | **Wrong** | Reduce to 5 for launch |
| 4 | $49-399/mo pricing works | **Wrong** | Rework for LLM cost margins |
| 5 | 14-month timeline | **Optimistic** | 5-7 months to first product, rest on demand |
| 6 | Competitive positioning is complete | **Mostly correct** | Add Shopify Sidekick as threat |
| 7 | Need dedicated connector per platform | **Wrong** | REST connector + webhooks + Zapier |
| 8 | Auth is P0 blocker | **Correct** | Platform-V4 has detailed execution plan |
| 9 | ONNX gives cost advantage | **Correct for embeddings** | Clarify: doesn't help with LLM generation costs |
| 10 | Open-core builds trust | **Correct** | Keep — AI Fabric Framework public, Loom AI private |

---

## Critical Path After Platform-V4

```
1. Auth foundation (stop trusting caller identity)     — 6-8 weeks
2. Private runtime mode (production-safe)              — 3-4 weeks
3. Shopify app backend (OAuth, billing, shop mapping)  — 4-6 weeks
4. Self-service onboarding wizard                      — 3-4 weeks
5. Shopify App Store submission + review               — 2-4 weeks
                                                Total: 5-7 months
```

Everything else (WooCommerce, WhatsApp, CRM, analytics) comes after you have merchants paying through the Shopify App Store.

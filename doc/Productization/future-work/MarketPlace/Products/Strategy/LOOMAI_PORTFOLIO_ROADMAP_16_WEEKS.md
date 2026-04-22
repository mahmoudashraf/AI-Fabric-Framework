# LoomAI Portfolio Roadmap — 16 Weeks to Full Ladder

Status: unified execution roadmap for the LoomAI product portfolio (2026-04-21)

Purpose:

- lay out a concrete 16-week plan to ship the full five-product ladder
- define weekly exit gates so progress is measurable and slippage is visible early
- make the reliability gate between product #2 and product #3 explicit and binding
- parallelize vertical spin-up wherever safe, serialize only where signal requires it

This document should be read with:

- [PRODUCT_DIRECTION_DECISION_RECORD.md](PRODUCT_DIRECTION_DECISION_RECORD.md)
- [LOOMAI_LABS_PRODUCT_CATEGORIES_PLAN.md](LOOMAI_LABS_PRODUCT_CATEGORIES_PLAN.md)
- [SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md](SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md)
- [SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md](SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md)
- [LOOM_COMPANION_SHOPIFY_LAUNCH_PLAN.md](LOOM_COMPANION_SHOPIFY_LAUNCH_PLAN.md)
- [LOOM_COMPANION_GO_TO_MARKET_PLAYBOOK.md](LOOM_COMPANION_GO_TO_MARKET_PLAYBOOK.md)
- [PLATFORM_UI_PERSONA_SEPARATION_PLAN.md](PLATFORM_UI_PERSONA_SEPARATION_PLAN.md)
- [OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md](../../../../Operations/observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)

## 1) Portfolio Shape

Five products, one shared platform:

1. **Loom Companion for Shopify** — read-first shopping companion, anchor vertical
2. **Loom Companion for WooCommerce** — same product, different bridge, 6M-store TAM
3. **Loom Docs** — documentation assistant for SaaS ($49–$199/mo)
4. **Loom Comply** — regulated-vertical Q&A with attribution ($200–$2K/mo)
5. **Loom Knowledge for Slack** — enterprise internal assistant

Shopify Companion is the anchor. Every other product inherits platform capability, observability, design system, and operational playbooks from the Shopify track.

## 2) The Reliability Gate

Velocity never overrides reliability. Product #3 does not ship to production until the Phase D checklist in [OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md](../../../../Operations/observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md) is green:

1. Two products running on the shared observability stack for ≥2 weeks
2. MTTD <5 min verified via synthetic-failure drill
3. MTTA <15 min verified in the on-call rotation
4. Every alert fired in the prior two weeks has a resolution note linked to its runbook
5. At least one post-incident retrospective completed and acted on
6. Cardinality budgets held across all live products
7. Support escalation playbook documented and exercised once

If this gate fails at Week 6, product #3 slips one week and the failing check becomes the only priority. No exceptions.

## 3) Weekly Execution

### Week 1 — Foundation Sprint

Objective: all Shopify V1 prerequisites live on staging; observability foundation live from day one.

Shipping:

- Shopify Bridge: consumer binding, health / drift / activity / mapping APIs
- Theme app extension + widget injection verified end-to-end
- Merchant dashboard at [app.loomai.pro](app.loomai.pro) — dark theme, six pages, persona-clean
- Partner portal scaffolding at [partners.loomai.pro](partners.loomai.pro) — built, not yet released
- Demo store populated, screencast recorded
- Micrometer + Prometheus remote write on every JVM service
- Logback JSON encoder + tenant MDC filter
- OpenTelemetry Java agent on Shopify Bridge, Platform core, Runtime
- Grafana Cloud workspaces provisioned (Loki, Prometheus, Tempo)
- Sentry integrated across all services
- BetterStack probes on Shopify Bridge and manifest endpoints

Exit gate: every staging surface green; every service emits structured logs, metrics, and traces visible in Grafana Cloud within 60 seconds.

### Week 2 — Shopify V1 Submission and Launch

Objective: App Store submission in review; cold outreach already in motion.

Shipping:

- Six embedded intelligence surfaces live: insight blocks, AI search, comparison, policy strips, contextual pill, chat fallback
- Read-only scopes only (fastest review path)
- Shopify Bridge runbook in the standard template
- "Shopify Companion health" dashboard live
- Sentry routing to `#incidents-shopify`
- Five error-budget SLOs with burn-rate alerts active

Human-cycle work, in parallel:

- Cold outreach to first 100 target merchants (Shopify Community, Reddit, Twitter, direct email)
- Partner program soft-launch to five design partners

Exit gate: App Store submission in review; synthetic-failure drill produces page + Sentry issue + red dashboard + runbook link within 2 minutes.

### Weeks 3–4 — Parallel Factory Spin-Up

Objective: three parallel tracks while Shopify V1 waits on App Store review.

Shipping:

- **Loom Companion for WooCommerce** code-complete (1-week build — same product, new bridge)
- **Shopify Wave 1**: live indexing, metaobjects, Judge.me review provider, storefront search augmentation, freshness dashboards — staged, not yet released
- **Loom Docs v1** scaffolding: domain model, ingestion pipeline, assistant surface
- `loomai-observability-starter` extracted — WooCommerce and Docs inherit the full stack on day one
- Cardinality-budget recording rules and alerts live
- Platform control-plane runbook + storefront embed runbook written

Human-cycle work:

- Shopify App Store review cycle (assume one revision round)
- First 50-install push once approved
- First incident drill — tabletop for "merchant reports assistant returning wrong product data"

Exit gate: three products code-complete, one in App Store review, one staged, one in development; WooCommerce consumes the observability starter on day one.

### Weeks 5–6 — Launch Wave, Wave 2 Actions, Reliability Gate

Objective: two products live in production; reliability gate evaluated.

Shipping:

- Shopify **Wave 2**: bounded write actions (add-to-cart, variant guidance, cart updates) shipped behind per-merchant feature flags
- WooCommerce Companion shipped on WordPress.org
- WooCommerce runbook finalized

Human-cycle work:

- Install target: 40–80 across Shopify + WooCommerce
- Partner portal opens publicly; 5–10 active partners
- **Reliability Gate evaluated** per Section 2

Exit gate: Phase D checklist green; two products in production with stable signal for ≥2 weeks.

### Weeks 7–8 — Loom Docs and Shopify Deepening

Objective: first non-e-commerce vertical live; Shopify support-stack integrations ready.

Shipping:

- **Loom Docs v1** ships — $49–$199/mo tier
- Shopify **Wave 3** integrations (Gorgias / Klaviyo / Recharge) code-complete
- **Loom Comply** build starts in parallel

Human-cycle work:

- Loom Docs: 10 signed sites in 4 weeks
- Partner portal: 15–25 active partners
- First multi-product incident drill (Shopify + Docs simultaneous scenario)

Exit gate: three products live, Loom Comply in development, observability healthy across all live products.

### Weeks 9–12 — Four Products Live, Platformization Begins

Objective: regulated vertical live; shared design system extracted.

Shipping:

- **Loom Comply** ships — $200–$2K/mo tier
- Shopify **Wave 4**: governance, audit trails, ROI / conversion influence reporting
- **Loom Knowledge for Slack** build starts
- `@loomai/design-system` extracted (patterns stable across four products)

Human-cycle work:

- Loom Comply: 3–5 regulated-vertical pilots (procurement cycles gate close rate)
- Partner program: 25–50 active partners
- 90-day production retrospective across all live products

Exit gate: four products live, one in development, combined installs 150–300, observability monthly spend <$1k.

### Weeks 13–16 — Full Ladder

Objective: five products live; second wave of products begins.

Shipping:

- **Loom Knowledge for Slack** ships
- **Loom Insights** (Smart Brain batch runtime) — new product track begins
- Shopify **Wave 5**: reusable ecosystem integration framework; platformized Shopify layer

Human-cycle work:

- Loom Knowledge: first enterprise pilots
- Loom Comply: first closed deals
- Partner program drives >30% of new installs

Exit gate: five products live, portfolio matches the LoomAI Labs product categories plan, Loom Insights in development.

## 4) Targets

| Metric | Target |
|---|---|
| Shopify V1 in App Store review | Week 2 |
| First product live in production | Week 3–4 |
| Two products live | Week 5–6 |
| Four products live | Week 12 |
| Five products live | Week 16 |
| Combined installs across products | 150–300 by Week 12 |
| Active partners | 25–50 by Week 8 |
| Loom Docs signed pilot sites | 10 by Week 12 |
| Loom Comply pilots | 3–5 by Week 12 |
| Loom Comply closed deals | First by Week 16 |
| Observability monthly spend at 4 products | $400–800 |
| MTTD | <5 minutes |
| MTTA | <15 minutes business hours |
| Runbook coverage | 100% of services that can page |

These are targets, not commitments. The weekly exit gates and the reliability gate in Section 2 override the numbers when signal warrants.

## 5) Calendar-Gated Constraints

Items that do not respond to engineering velocity:

- **Shopify App Store review** — 3–10 business days per cycle; first submission often triggers a revision round, budget two weeks worst case
- **Merchant install decisions** — evaluation is slow; a 4.5-star rating takes real usage to accrue
- **Partner trust** — Shopify agencies commit only after seeing 3–5 live references
- **Regulated-vertical sales (Comply)** — 30–90 day procurement cycles regardless of product readiness
- **Incident retrospectives** — experience-gated; the only way to shorten them is to have had incidents already
- **Internal QA and review bandwidth** — high shipping throughput requires serious automated tests, error monitoring, and rollout gates, not optional

If a weekly milestone slips, the first diagnostic question is whether the slipping item is code-bound or one of the above.

## 6) Risks and Mitigations

- **Quality and support debt compounds.** Mitigation: the reliability gate at Week 6 is the structural defense. Product #3 does not launch until it clears.
- **Generated code introduces subtle regressions.** Mitigation: observability starter's compile-time lint rules; mandatory integration tests per product; weekly review of new Sentry issue groups.
- **Parallel spin-up dilutes go-to-market focus.** Mitigation: one named GTM lead per product; Shopify stays the anchor; non-Shopify products use lower-touch distribution (WordPress.org, direct sales).
- **Partner and support programs lag the portfolio.** Mitigation: partner portal built in Week 1; support runbook coverage gated at Week 6, not after the fact.
- **Cost blow-ups at scale.** Mitigation: cardinality governance policy enforced in the starter; monthly cost review.

## 7) Non-Goals

- This plan does not rewrite the existing Shopify launch, GTM, expansion, or execution plans — it sets the timing in which their phases land
- This plan does not add new products beyond the LoomAI Labs product categories plan
- This plan does not remove Shopify's anchor position — Shopify receives the most iteration depth; other verticals validate breadth
- This plan does not attempt internationalization, localization, or regulatory expansion beyond Comply's initial verticals in the 16-week window

## 8) Review Cadence

- End of every week: go/no-go review against that week's exit gate
- End of Week 6: Phase D reliability gate evaluation
- End of Week 12: portfolio review — evaluate whether to accelerate, hold, or cut Weeks 13–16
- End of Week 16: retrospective on the plan itself — where did adoption lag most, where did code-complete land ahead of human-cycle readiness, recalibrate the next 16 weeks

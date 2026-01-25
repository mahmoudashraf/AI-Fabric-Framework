# Smart Brain Offers Pack (Promotions, Coupons, Loyalty) — Change/Analysis Plan

## Status
Draft

## Summary (what this is)
This document describes one possible Smart Brain “action pack” focused on offers/promotions.
For the generic Smart Brain plan, see `changes/SMART_BRAIN_MODULE_CHANGE_PLAN.md`.

“Smart Brain” is a **domain module + action pack** that turns the existing Orchestrator “brain” into an **AI-driven offer decisioning copilot**:
- read system signals (behavior insights + product/subscription context)
- recommend **who** to target and **what** to offer (retention, loyalty, upsell)
- optionally create **draft** coupons/promotions (human-in-the-loop)
- optionally execute and track outcomes (behind strong gates)

This is not a new orchestrator; it’s a **specialized capability built on top of** the existing orchestrator + action handler framework.

---

## 1) Is this a good or bad idea?
### Why it’s a good idea
- **High ROI use case**: churn prevention + upsell/upgrade offers are among the most measurable AI applications.
- **Strong fit for AI Fabric**: we already have the critical primitives:
  - Behavior Insights (sentiment/churn/segment/recommendations)
  - Orchestrator safety rails (security/access/PII/compliance)
  - Action system (explicit, tool-based execution with confirmation support)
- **Clear path to “safe autonomy”**: you can start in “advisor mode” (suggestions only) and graduate to “draft creation” and later to “autopilot” under strict constraints.

### Why it can be a bad idea (if scoped wrong)
- **Market is crowded** if the goal is to be a full marketing automation platform (Braze/Iterable/Salesforce/Adobe/Klaviyo class).
- **High-stakes**: offers are money + fairness + trust. Without hard guardrails and auditing, an LLM-driven offer engine can create legal/compliance and margin risks.
- **Integration heavy**: “create coupon / send offer / track redemption” depends on your commerce/billing/CRM stack; without connectors it becomes “just a demo”.

**Conclusion:** Good idea if positioned as **“Offer Decisioning Copilot + Guardrailed Actions”**, not as a replacement for marketing automation suites.

---

## 2) Market fit (positioning)
### Target users
- Subscription businesses (SaaS, apps): churn + upgrade paths are native.
- E-commerce / marketplaces: personalized promos with strict budget/eligibility rules.
- Teams that want **privacy-first / on-prem** decisioning or don’t want vendor lock-in to a single marketing platform.

### Differentiators (where we can win)
- **Plug-in signals + plug-in actions**: bring your own data sources, keep the “brain” generic.
- **Governed autonomy**: confirmation, policy checks, audit logs, and bounded “facts-only” generation.
- **End-to-end story with Real Apps**: ship a reference implementation that proves ROI without pretending we built Braze.

---

## 3) What is already supported in this repo (today)?
### 3.1 Orchestrator + Action framework (core)
- Action routing via `ActionHandler` + prompt-visible action metadata.
- `requiresConfirmation()` support to keep mutating actions human-approved.
- Post-action grounded generation (bounded facts payload) for action results.
- Security / access control / PII detection / compliance gating around orchestration.

### 3.2 Behavior Insights (specialized module)
- Behavior module generates sentiment + churn risk + segment + recommendations.
- Orchestrator can be enriched via SPI (`BehaviorContextProvider`) without tight coupling.

### 3.3 Data access patterns
- Relationship Query module can translate NL → JPQL/SQL over your JPA model (useful for “find churn-risk users”, “users without purchase in 30d”, etc.).
- Real Apps already demonstrate churn signals and subscription plan upgrades (useful baselines for Smart Brain demos).

---

## 4) What’s missing (gaps to close)
### Product gaps
- No first-class **promotion/coupon domain** (schema, lifecycle, eligibility, budget, channels).
- No tracking loop (offer impressions → redemption → churn outcome) that feeds evaluation.

### Technical gaps (to build Smart Brain as a reusable module)
- Standardized **Offer Decision** contract (LLM output shape + validation).
- A small set of **promotion actions** (read + draft + publish) with safe defaults.
- An SPI for “promotion system” integration (so this isn’t tied to one app’s schema).
- Policy layer (limits, allowlists, budgets, fairness constraints).

---

## 5) Proposed design (how Smart Brain fits)
### 5.1 Operating modes (recommended)
1) **Advisor mode (MVP)**: Smart Brain only recommends targets + offers; no writes.
2) **Draft mode**: Smart Brain can create *draft* promotions/coupons; publishing requires explicit approval.
3) **Autopilot mode (later)**: Smart Brain can execute within hard constraints (budget caps, max discount, eligible segments) + full audit logging.

### 5.2 Architecture (3 layers)
1) **Signals layer (inputs)**
   - Behavior context (segment, churn risk, recommendations)
   - Subscription/product context (plan tier, tenure, LTV proxy, feature usage)
   - Offer history (previous offers sent, redemption, cooldown windows)

2) **Decision layer (AI)**
   - Produces structured `OfferDecision` objects from bounded facts:
     - target criteria (segment/churn threshold/eligibility rules)
     - offer type (retention credit, percent discount, upgrade incentive, loyalty perk)
     - justification (facts-based), confidence, expected cost band

3) **Execution layer (actions)**
   - Explicit `ActionHandler`s that implement:
     - read-only lookups (promotions, user cohorts)
     - draft creation (coupon/promo draft)
     - optional publish/assign/send (guarded + confirmed)

### 5.3 Proposed action catalog (v1)
**Read-only (safe by default)**
- `smart_brain.get_behavior_insights(userId?)`
- `smart_brain.list_active_promotions(filters?)`
- `smart_brain.preview_target_users(criteria)` → counts + sample IDs (bounded)
- `smart_brain.recommend_offers(criteria, constraints)` → list of `OfferDecision`

**Mutating (confirmation required + restricted)**
- `smart_brain.create_promotion_draft(decision)` → returns `promotionId` + draft summary
- `smart_brain.publish_promotion(promotionId)` (optional, behind stricter gates)
- `smart_brain.assign_promotion(promotionId, userIds|segmentId)` (optional)

### 5.4 Integration model (keep it generic)
Add a small SPI (similar spirit to `BehaviorContextProvider`) so apps wire their own systems:
- `PromotionCatalogProvider` (read/list)
- `PromotionDraftWriter` (create draft)
- `PromotionPublisher` (publish/apply)
- `OfferOutcomeTracker` (record sent/redeemed/churn outcome)

Smart Brain module depends only on these interfaces; Real Apps provide concrete implementations.

### 5.5 Safety / governance requirements (non-negotiable)
- **Hard limits** enforced in code (not “prompt only”):
  - max discount / max credit
  - budget caps per day/week
  - eligibility allowlist (plans/regions/tenure)
  - cooldown windows to avoid spamming users
- **Confirmation**: any write action defaults to `requiresConfirmation() = true`.
- **Access control**: only privileged roles/users can run mutating promotion actions.
- **PII + fairness**:
  - do not use protected attributes for targeting (explicit denylist)
  - audit what signals were used to make decisions
- **Audit logging**:
  - store decisions + facts summary + action inputs/outputs + approver identity
- **Bounded payloads**:
  - decisions must be derived from explicit facts maps (no raw object dumps)

---

## 6) Development path (recommended phases)
### Phase 0 — Requirements + constraints (1–2 weeks)
- Define offer types + constraints (discount caps, budgets, approval workflow).
- Decide integration target: internal promotions DB vs external tool (Braze/Iterable/etc.).
- Define “success metrics” and what telemetry is available.

### Phase 1 — MVP: Advisor mode (2–4 weeks)
- Implement `recommend_offers` as a read-only action (returns `OfferDecision[]`).
- Implement `preview_target_users` using existing data sources (behavior insights DB / relationship-query).
- Add a Real App demo flow:
  - “show churn-risk users → suggest retention offers”
  - “show most engaged → suggest upgrade offers”

### Phase 2 — Draft mode: create promotions safely (2–4 weeks)
- Implement `create_promotion_draft` (draft-only) + strict validation.
- Add an approval UI endpoint (or admin screen in a Real App) to publish drafts manually.
- Add audit storage for decisions + drafts.

### Phase 3 — Execution integrations (4–8 weeks)
- Integrate with one “delivery” channel (email/push/in-app) OR export to an external platform.
- Implement outcome tracking (sent → redeemed → churn/upgrade deltas).
- Add A/B testing hooks (control vs treatment assignment).

### Phase 4 — Autopilot (only after metrics + controls exist)
- Enable bounded, policy-checked auto-publishing for limited segments.
- Add budget circuit breakers + anomaly detection.
- Add periodic evaluation + rollback plan.

---

## 7) Test plan
### Unit tests
- Offer policy validator (caps, allowlists, cooldowns).
- Action handler parameter validation (schema + type safety).
- “Facts-only” generation payload bounds.

### Integration tests
- In-memory DB or testcontainers:
  - seed behavior insights + subscriptions
  - run `preview_target_users` and `recommend_offers`
  - ensure deterministic outputs with stubbed LLM provider

### Real API tests (optional, keys-only)
- One deterministic prompt path that validates the provider wiring for `recommend_offers`.

---

## 8) Acceptance criteria (v1)
- Smart Brain can recommend offers for:
  - churn-risk users
  - highly engaged users (upgrade/loyalty)
- Recommendations are returned in a strict, validated structure (`OfferDecision`) with:
  - targeting criteria, offer definition, justification, confidence
- No write path is enabled by default; draft creation (if implemented) requires confirmation + policy validation.
- Decisions and actions are auditable (who requested, what facts used, what was suggested/executed).

---

## 9) Open questions
- Where do promotions live initially (internal DB vs external provider)?
- What is the minimum “engagement” signal set (events vs derived metrics)?
- Who approves drafts, and what is the desired UX (chat confirmation vs admin UI)?
- What constraints are mandatory for v1 (discount cap, budget cap, cooldown)?
- How do we define a “successful” offer (retained after N days? upgrade within N days?) and where is that data?

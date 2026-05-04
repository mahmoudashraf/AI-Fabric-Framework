# Implementation Prioritization Roadmap

Status: planning document (2026-03-31)

This document prioritizes the current future-work plans for implementation.

The prioritization is based on:

- market positioning
- product differentiation
- dependency order
- customer adoption value
- engineering leverage

It assumes the target product position is:

- **enterprise AI deployment control plane**

not:

- generic prompt IDE
- generic eval tool
- generic chatbot builder

---

## 1) Executive Summary

Recommended implementation order:

1. enterprise deployment administration and unified deployment workspace
2. prompt management with hot apply for dev/test
3. deployment test data migration and embedded POC chatbot
4. runtime action-grounded answering and deep knowledge navigation
5. confirmation interception productization
6. data migration platform, full version
7. platform AI assistant deployment
8. remote confirmation policy service
9. multi-cloud provisioning expansion

The key principle is:

- first strengthen the control plane
- then shorten onboarding and iteration loops
- then deepen runtime intelligence
- then expand enterprise extensibility and cloud reach

---

## 2) Priority Model

### P0: strategic anchor

Not an implementation feature, but should guide all work:

- [GO_TO_MARKET_POSITIONING_AND_GAP_ANALYSIS.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/GO_TO_MARKET_POSITIONING_AND_GAP_ANALYSIS.md)

This defines the target identity and should be treated as the filter for implementation decisions.

### P1: must build next

These are the highest-value implementation priorities:

1. [ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md)
2. [PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md)
3. [DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md)

### P2: core product depth after that

These materially improve product quality and differentiation:

4. [RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md)
5. [CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md)
6. [DATA_MIGRATION_PLATFORM_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/DATA_MIGRATION_PLATFORM_PLAN.md)

### P3: enterprise expansion and advanced extensibility

These are valuable, but should follow a stronger core platform:

7. [PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md)
8. [REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md)
9. [MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md)

---

## 3) Why This Order

### 3.1 Build the control plane first

The strongest market position is the control plane.

That means the platform must first feel like:

- a serious deployment administration surface
- a governed operational workspace
- a coherent operator product

This is why enterprise administration comes first.

Without that, later features risk feeling like useful capabilities attached to an immature shell.

### 3.2 Shorten time-to-value next

After control plane foundations, the next most valuable moves are:

- prompt iteration
- POC setup
- test data and embedded validation

These reduce the time between:

- creating a deployment
- proving it works
- tuning behavior

That is critical for adoption, demos, onboarding, and implementation velocity.

### 3.3 Then improve answer quality and business behavior

Once setup and iteration are better, the next differentiation comes from runtime quality:

- read-only action grounding
- deep knowledge navigation
- confirmation interception

These improve the substance of the deployed assistants, not only the admin shell.

### 3.4 Then expand into advanced enterprise territory

Full migration platform, platform assistant, remote policy services, and multi-cloud are all valuable.

But they depend on:

- a cleaner control plane
- better operator UX
- stronger deployment and access primitives

Without that foundation, they add complexity faster than they add product clarity.

---

## 4) Detailed Ranking

### Rank 1: Enterprise deployment administration and unified workspace

Document:

- [ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md)

Why first:

- directly supports target market position
- closes the biggest current UX and governance gap
- creates the shell that later features should live inside
- gives the product enterprise credibility

What to build first inside it:

- deployments grid
- persistent deployment header/context
- unified deployment workspace
- archive/delete with guardrails
- user roles and deployment assignments

### Rank 2: Prompt management with hot apply

Document:

- [PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md)

Why second:

- highest visible value for behavior tuning
- shortens iteration cycle dramatically
- easy for customers to understand
- complements, rather than replaces, the release model

Why not first:

- prompt tools alone are not your market wedge
- better inside a stronger unified deployment workspace

### Rank 3: POC deployment mode with test data and embedded chatbot

Document:

- [DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md)

Why third:

- accelerates demos and onboarding
- creates a strong customer-facing “it works now” moment
- reduces dependency on external apps for proof-of-concept validation

This is a strong commercial feature because it improves:

- pre-sales
- workshop flow
- implementation speed

### Rank 4: Runtime action-grounded answering and deep knowledge navigation

Document:

- [RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md)

Why fourth:

- materially improves quality of deployed assistants
- supports operational usefulness, not just chat
- increases trustworthiness of answers

Why not earlier:

- runtime quality matters most after iteration and POC loops are easier to use

### Rank 5: Confirmation interception productization

Document:

- [CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md)

Why fifth:

- strong differentiator for domain workflows
- especially useful in commerce, support, and operations use cases
- restores an important capability that already exists conceptually in the framework

Why after action-grounded answering:

- broader value first
- interception is powerful but more scenario-specific

### Rank 6: Full migration platform

Document:

- [DATA_MIGRATION_PLATFORM_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/DATA_MIGRATION_PLATFORM_PLAN.md)

Why sixth:

- very valuable
- strategically important
- but larger in scope than POC/test-data migration

Recommended sequencing:

- first ship the lighter POC/test-data version
- then expand into the full migration platform

### Rank 7: Platform AI assistant deployment

Document:

- [PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md)

Why seventh:

- strong dogfooding value
- strong UX value
- depends on better admin APIs, deployment context, and read-only action support

This becomes much better once:

- deployment workspace is unified
- runtime grounding is stronger
- access/assignment model is richer

### Rank 8: Remote confirmation policy service

Document:

- [REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md)

Why eighth:

- powerful enterprise extensibility
- useful for consulting and highly customized customers
- more advanced than most customers need initially

Recommended dependency:

- first ship config-driven confirmation interception
- then add remote service extensibility

### Rank 9: Multi-cloud provisioning expansion

Document:

- [MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md)

Why ninth:

- strategically important
- often commercially attractive
- but not the strongest immediate differentiator compared with the control-plane and onboarding gaps

Important caveat:

- if a near-term enterprise deal explicitly requires AWS or Azure, this priority can be pulled forward

So this is:

- default priority: later
- deal-driven priority: potentially much earlier

---

## 5) Recommended Implementation Waves

### Wave 1: control plane foundation

- enterprise deployment administration
- unified deployment workspace
- roles, assignments, archive/delete operations

### Wave 2: adoption and iteration

- prompt management with hot apply
- POC deployment mode
- embedded chatbot and test data flows

### Wave 3: runtime quality

- read-only action-grounded answering
- deep knowledge navigation
- confirmation interception

### Wave 4: enterprise expansion

- full migration platform
- platform AI assistant
- remote policy service
- multi-cloud expansion

---

## 6) What Not to Do First

Do not start with:

- full multi-cloud expansion
- remote policy service
- advanced platform assistant

These are good future capabilities, but they do not close the biggest present gap between current position and target market position.

The biggest current gap is:

- a strong, unified, enterprise-grade control plane with fast iteration and onboarding

---

## 7) Conditional Reordering Rules

The default order above should change only if one of these becomes true:

### Rule 1: deal pressure

If a customer or design partner requires AWS/Azure:

- move multi-cloud into P1 or early P2

### Rule 2: adoption pressure

If customers struggle most with behavior tuning:

- keep prompt management at the top

### Rule 3: domain workflow pressure

If the main customer use cases depend on offers, escalations, or interception logic:

- move confirmation interception ahead of deep knowledge navigation

### Rule 4: internal support pressure

If platform complexity is becoming a major usability issue:

- move platform assistant earlier, but only after read-only action grounding exists

---

## 8) Recommendation

The best implementation sequence is:

1. fix the control plane experience
2. make iteration and POC setup fast
3. improve runtime answer quality
4. expand into advanced enterprise and multi-cloud features

That sequence best supports the market position we want and gives the product the strongest path from current state to a credible enterprise AI deployment platform.

---

## 9) Commercial and Go-to-Market Plans (PLAN-006 to PLAN-008)

The plans above are engineering-focused. Three additional sequenced plans address pricing, licensing, positioning, and partner channel — the commercial surface around the engineering work:

- [PLAN-006-PRICING_LICENSING_AND_POSITIONING_RECONCILIATION.md](./PLAN-006-PRICING_LICENSING_AND_POSITIONING_RECONCILIATION.md)
- [PLAN-007-INTEGRATION_PARTNER_CHANNEL_LAUNCH.md](./PLAN-007-INTEGRATION_PARTNER_CHANNEL_LAUNCH.md)
- [PLAN-008-VERTICAL_PRODUCT_FACTORY_OPERATING_MODEL.md](./PLAN-008-VERTICAL_PRODUCT_FACTORY_OPERATING_MODEL.md)

Recommended sequencing relative to engineering work:

- PLAN-006 runs immediately and in parallel with engineering work (it is documentation, not code)
- PLAN-007 begins once Loom Companion is GA on Shopify and the install flow polish in PLAN-007 section 6 is engineered
- PLAN-008 is the operating model; it is referenced continuously and reviewed quarterly

PLAN-008 also re-tags certain items in this roadmap as "deferred":

- multi-cloud provisioning expansion → defer until Phase 5 of PLAN-008
- plugin marketplace monetization → defer until Phase 4 of PLAN-008
- "Pro Developer License" framework pricing → cut entirely per PLAN-006

The combined effect: engineering continues on the priority order in sections 1–8, but the **commercial center of gravity** shifts to channel and product polish per PLAN-007 and PLAN-008. Stop adding platform; start adding channel.

---

## 10) Ecosystem Integration Plan (PLAN-010)

A separate engineering plan addresses Model Context Protocol (MCP) citizenship — both consuming external MCP servers as Actions and exposing deployments as MCP servers:

- [PLAN-010-MCP_CLIENT_AND_SERVER_INTEGRATION.md](./PLAN-010-MCP_CLIENT_AND_SERVER_INTEGRATION.md)

Recommended sequencing:

- Waves A + B (MCP Client v1 — 5 weeks) can run in parallel with PLAN-007 Wave A foundations; the unlock from "AI Fabric supports MCP" justifies the effort independently
- Wave D (MCP Server adapter) ships before any partner-channel marketing emphasizes third-party tool integration
- Waves E + F (Marketplace plugin type and positioning) coordinate with marketplace v1 work and PLAN-006 positioning lock-in

The strategic frame: MCP support is a translation layer that lets AI Fabric absorb the entire MCP ecosystem while keeping the governance, audit, and tenant isolation that vanilla MCP servers do not standardize. Position externally as the *governed* MCP platform, not as catch-up.

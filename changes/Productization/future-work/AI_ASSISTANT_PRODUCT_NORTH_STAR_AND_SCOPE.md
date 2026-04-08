# AI Assistant Product North Star and Scope

Status: planning document (2026-03-31)

This document defines the core product north star for AI Fabric and the scope rules that should guide future roadmap decisions.

It exists to keep the product focused as the number of platform, runtime, migration, and vertical plans grows.

---

## 1) North Star

The core product goal is:

- **provide configurable AI assistants that understand customer data, use customer APIs/actions, see live and updated information, and integrate easily into any application or system**

This is the product center of gravity.

Everything else should support that goal.

---

## 2) Expanded Product Statement

AI Fabric should make it easy for a team to:

- connect an assistant to customer data
- connect an assistant to customer APIs and business actions
- keep the assistant grounded in current/live information
- configure the behavior without rebuilding custom code each time
- deploy and operate the assistant safely across environments

In short:

- **grounded, configurable, integration-ready AI assistants**

---

## 3) What the Product Is

The product is:

- an AI assistant integration and deployment platform

More specifically:

- a control plane for configuring, testing, deploying, and operating grounded assistants

Important product-boundary clarification:

- the assistant experience should be designable as a separate customer product or customer-facing integration that uses AI Fabric as its control plane and execution platform
- first-party assistant surfaces inside the platform are still valuable, but they should behave as reference consumers of the product rather than as one-off privileged exceptions
- this means assistant architecture should stay compatible with the shared authentication and authorization modes instead of assuming only platform-internal session behavior

The assistant should be able to:

- answer with knowledge
- answer with live action/API data
- act safely through configured business actions
- stay current through data sync and migration

---

## 4) What the Product Is Not

The product is not primarily:

- a generic prompt playground
- a generic chatbot UI builder
- a model-switching dashboard
- an eval-only tool
- a cloud infrastructure abstraction for its own sake

Those may exist as supporting capabilities, but they are not the north star.

---

## 5) Core Capability Pillars

Everything should map to one or more of these pillars.

### 5.1 Customer data grounding

The assistant must understand customer data and knowledge.

This includes:

- vector spaces
- knowledge bases
- retrieval
- indexing
- data migration

### 5.2 Customer API and action grounding

The assistant must use customer APIs/actions safely and meaningfully.

This includes:

- read-only action grounding
- confirmation-based write actions
- action contracts
- connector patterns

### 5.3 Live and updated state

The assistant must see current information, not just stale snapshots.

This includes:

- data sync
- migration
- webhook/event-based refresh
- live read-only API calls

### 5.4 Configurable behavior

The system should be configured, not re-implemented, for every customer.

This includes:

- actions config
- entities / vector spaces config
- routing config
- prompt management
- confirmation policies

### 5.5 Easy integration

The assistant should be easy to integrate into external systems and applications.

This includes:

- connectors
- embedded chatbot surfaces
- widget/admin integrations
- shared widget-based chat shells where first-party and external integrations can reuse the same assistant UI contract appropriately
- deployable runtime boundaries
- separate product packaging that can consume the platform through supported auth and deployment contracts

### 5.6 Safe operation

The system should be safe to deploy and run in real organizations.

This includes:

- versioning
- release/apply lifecycle
- diagnostics
- verification
- governance
- roles and assignments

---

## 6) How the Existing Plans Align

### Strong direct alignment

These plans are directly aligned to the north star:

- [RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md)
- [PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md)
- [DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md)
- [DATA_MIGRATION_PLATFORM_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/DATA_MIGRATION_PLATFORM_PLAN.md)
- [CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md)
- [SHOPIFY_VERTICAL_STRATEGY_AND_PRIORITY_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/SHOPIFY_VERTICAL_STRATEGY_AND_PRIORITY_PLAN.md)

Why:

- they improve grounding, behavior, onboarding, or integration directly

### Strong supporting alignment

These plans support the goal operationally:

- [ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md)
- [PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md)

Why:

- they improve deployment, operation, and supportability
- they provide a first-party reference path, but they should still stay compatible with the broader assistant product boundary

### Conditional alignment

These are aligned only when they support real assistant deployment needs:

- [MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md)
- [REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md)

Why:

- they are valuable, but they are not the core assistant value on their own

---

## 7) What Counts as “Aligned”

A roadmap item is aligned if it improves one of these:

- assistant understanding of customer knowledge
- assistant access to live customer data
- assistant use of safe business actions
- speed of configuring and deploying assistants
- ease of integrating assistants into customer systems
- trust, safety, and correctness in production

If a feature does not improve one of those, it is likely a distraction or a lower-priority enabler.

---

## 8) What Counts as “Distraction”

Potential distraction patterns:

- infrastructure features with no clear assistant value
- generic AI tooling features with weak differentiation
- admin complexity that does not improve deployment or assistant quality
- vertical features that are not reusable and do not strengthen the platform

Examples:

- cloud expansion without customer pressure
- broad widget theming before grounding quality is strong
- advanced policy-service extensibility before config-driven behavior is solid
- complex analytics before the assistant works reliably on live data

---

## 9) Scope Rules for Decision Making

Use these rules when deciding whether to start or continue work.

### Rule 1

Ask:

- does this make the assistant more grounded in customer data?

### Rule 2

Ask:

- does this make the assistant better at using live customer APIs/actions safely?

### Rule 3

Ask:

- does this reduce time-to-value for getting an assistant working in a customer environment?

### Rule 4

Ask:

- does this make deployment and operation more repeatable and configurable?

### Rule 5

Ask:

- does this help prove the Shopify or similar vertical wedge?

If the answer is weak across all five, it should not be near-term priority.

---

## 10) Recommended Short Product Statement

Recommended internal product statement:

- **AI Fabric helps teams configure, deploy, and operate grounded AI assistants that understand customer data and use live customer APIs safely.**

Recommended shorter external form:

- **Grounded AI assistants for real customer systems.**

Recommended category form:

- **AI assistant deployment control plane**

---

## 11) What Success Looks Like

The product is succeeding when a team can:

1. connect customer knowledge and live APIs
2. configure the assistant behavior quickly
3. test it in the platform
4. deploy it into the target system
5. trust that it stays current, safe, and explainable

If the product does that well, the market position becomes much stronger.

---

## 12) Recommendation

The north star should remain:

- configurable assistants
- customer data grounding
- customer API/action grounding
- live/updated information
- easy integration

This should be the lens for all future work.

When roadmap choices become unclear, the correct question is:

- **does this help us build a better grounded assistant for real customer systems?**

If yes, it belongs.

If not, it is secondary.

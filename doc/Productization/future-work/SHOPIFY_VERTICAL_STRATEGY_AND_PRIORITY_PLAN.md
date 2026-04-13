# Shopify Vertical Strategy and Priority Plan

Status: planning document (2026-03-31)

This document defines where Shopify fits in the product strategy, how to prioritize it against broader platform work, and how to keep the vertical focused without losing the platform direction.

It is intended to prevent two opposite mistakes:

- building too horizontally and losing a concrete market wedge
- building Shopify too specifically and weakening the platform

---

## 1) Executive Summary

Shopify should be treated as:

- the **first reference vertical**

not:

- a side project
- the whole company/product identity
- a fully separate custom product stack

The right relationship is:

- the platform is the enterprise AI deployment control plane
- Shopify is the first strong vertical proof that the control plane solves a real market problem

Recommended timing:

- do not postpone Shopify until every platform feature is complete
- do not start with full Shopify breadth before the control-plane foundation is strong

Recommended order:

1. core platform control-plane improvements first
2. then Shopify as the first bounded vertical implementation

---

## 2) Why Shopify Matters

Shopify is strategically useful because it gives the platform:

- a real business domain
- real actions
- real knowledge sources
- real customer-facing UI surfaces
- clear safety and confirmation requirements
- a strong demo and sales narrative

It is a good first vertical because it exercises almost every important part of the platform:

- deployment setup
- runtime + connector architecture
- action routing
- confirmations
- retrieval
- migration / sync
- admin UI
- storefront UI

---

## 3) Why Shopify Should Not Become “Everything”

The platform should not become:

- “a Shopify AI app company with some internal tooling”

That would make it harder to:

- support other verticals later
- keep the runtime generic
- preserve the platform’s enterprise control-plane value

So the rule should be:

- Shopify drives prioritization
- but platform abstractions stay generic

Meaning:

- build reusable action / knowledge / deployment / governance capabilities
- validate them in Shopify first
- do not hardcode the product around Shopify-specific assumptions

---

## 4) Recommended Product Positioning Relationship

Recommended hierarchy:

### 4.1 Company / platform category

- enterprise AI deployment control plane

### 4.2 First vertical solution

- Shopify commerce assistant platform

### 4.3 How to talk about it

External message:

- “We provide the control plane for deploying and operating grounded AI assistants and workflows.”

Proof point:

- “Our first strong vertical is Shopify commerce.”

This is a better market story than:

- “We are a Shopify chatbot company”

or:

- “We are a general AI platform with no concrete wedge”

---

## 5) Where Shopify Fits in the Roadmap

### 5.1 Recommended timing

Shopify should come:

- after the first control-plane foundation wave
- before broader enterprise expansion work like full multi-cloud and remote policy services

### 5.2 Recommended placement

Adjusted roadmap placement:

1. enterprise deployment administration and unified workspace
2. prompt management with hot apply
3. POC deployment mode with embedded chatbot
4. Shopify vertical reference implementation
5. runtime action-grounded answering and deep knowledge navigation
6. confirmation interception productization
7. full migration platform
8. platform AI assistant
9. remote confirmation policy service
10. multi-cloud expansion

### 5.3 Why this timing is right

Because Shopify needs:

- a better platform shell
- prompt iteration
- fast demo/POC loop

But Shopify should then arrive early enough to:

- keep the product grounded
- provide a strong vertical wedge
- validate the generic abstractions

---

## 6) What Shopify Should Drive

Shopify should actively drive these platform features:

- action-grounded answering
- confirmation interception
- prompt management
- POC deployment mode
- migration/test data flow
- embedded assistant/chat testing
- deployment diagnostics

Why:

- commerce is rich in read-only questions
- commerce has safe-but-important write actions
- commerce has clear value from policies + product knowledge
- commerce needs strong UX and trust

This makes Shopify a strong forcing function for product quality.

---

## 7) What Shopify Should Not Block

Do not let Shopify force premature work on:

- full multi-cloud support
- large partner connector ecosystem
- advanced enterprise remote policy services
- broad multi-vertical templating

Those can come later.

The first Shopify goal is:

- prove one strong vertical path on the generic platform

---

## 8) Recommended Shopify V1 Scope

Keep Shopify V1 narrow and strong.

### 8.1 Core surfaces

- embedded admin playground
- storefront chat widget
- sync and health UI
- environment view (dev/prod)

### 8.2 Core data sources

- products
- collections
- pages
- policies

Avoid broad PII-heavy sources initially.

### 8.3 Core action types

Start with:

- read-only actions
- a few safe confirmation-based flows

Examples:

- product discovery
- product comparison
- policy Q&A
- cart-assist flows
- safe write flows only with confirmation

### 8.4 Runtime behavior focus

Prioritize:

- read-only action grounding
- policy + product knowledge grounding
- confirmation-aware writes

This gives the most trust and value fastest.

---

## 9) Shopify V2 Scope

After V1 proves the wedge, expand into:

- better migration/sync
- production provisioning
- richer widget customization
- merchant-facing analytics
- more advanced action catalogs
- merchant connector forwarding

Only after that should you consider:

- broader ecosystem or app-marketplace style expansion

---

## 10) Risk of Losing Focus

The product loses focus if it does either of these:

### 10.1 Too horizontal

Symptoms:

- too many future platform plans
- no clear buyer
- no clear reference vertical
- good infrastructure, weak product wedge

### 10.2 Too vertical too early

Symptoms:

- hardcoded Shopify assumptions everywhere
- platform abstractions weakened
- difficult future reuse
- roadmap becomes “merchant features only”

### 10.3 Recommended focus rule

Use this rule:

- every platform feature should be justified by a concrete Shopify or enterprise operator use case
- every Shopify feature should be implemented in a reusable way unless there is a strong reason not to

---

## 11) Success Criteria

Shopify is the right first vertical if it proves:

- the platform can configure and deploy a real assistant experience
- the runtime can ground answers in actions and knowledge
- confirmations work in real business flows
- operators can manage the deployment from one control plane
- merchants / stakeholders can see value quickly through POC and embedded testing

If Shopify cannot prove those, the broader platform story is weaker.

---

## 12) Recommendation

The right move is:

- keep Shopify as the first vertical wedge
- keep the platform as the main product category
- start Shopify immediately after the first control-plane and iteration foundations

That preserves both:

- strategic focus
- and real market grounding

In short:

- **platform first**
- **Shopify second, but early**
- **Shopify as proof, not as distraction**

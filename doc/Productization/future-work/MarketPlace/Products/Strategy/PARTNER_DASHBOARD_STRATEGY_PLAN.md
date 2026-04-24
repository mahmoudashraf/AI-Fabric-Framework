# Partner Enablement Dashboard Strategy Plan

Status: revised strategy plan (updated 2026-04-25)

This plan defines the partner surface needed to support developers, integrators, and agencies who use LoomAI as an AI enablement layer for client stores and existing apps.

This is not a passive acquisition dashboard.

The partner surface should help capable implementation partners deploy, verify, and support LoomAI-powered intelligence pieces without exposing the full operator control plane.

---

## 1) Decision

Partner support should be treated as an early platform capability.

The reason is not only merchant acquisition. The platform may be positioned as:

- an AI enablement layer for Shopify and commerce apps
- a way for agencies to add intelligence pieces to client stores
- a way for developers and integrators to ship AI surfaces without building RAG, sync, governance, and observability from scratch

That requires a mature partner support surface earlier than a passive acquisition program would.

However, broad partner recruitment, public signup, commercial attribution surfaces, white-label packaging, and public partner APIs are out of current scope until founding implementation partners prove the flow.

---

## 2) Product Goal

Build a partner enablement dashboard that lets agencies, developers, and integrators:

- understand the available intelligence pieces
- deploy LoomAI surfaces to client stores
- verify that each surface works
- monitor client-store health
- handle first-line setup/support
- escalate technical issues with useful evidence

The dashboard should make partners effective without making them platform operators.

---

## 3) Partner Product Positioning

The partner offer is:

> Add LoomAI-powered intelligence surfaces to your client stores and apps without building the AI infrastructure yourself.

The partner does not only sell Loom Companion.

The partner can implement:

- AI search
- product insight blocks
- product FAQ
- comparison
- policy strips
- contextual pills
- chat/depth layer
- read-only store intelligence
- governed action surfaces later
- analytics and ROI surfaces later

This makes partners implementation multipliers, not passive acquisition channels.

---

## 4) Primary Users

### 4.1 Partner Admin

Owns:

- partner account
- team members
- client/store portfolio
- scoped store access
- implementation quality
- support and escalation flow

### 4.2 Partner Implementer

Owns:

- store setup
- intelligence surface placement
- source and sync readiness
- launch verification
- first-line client support
- escalation packet creation

### 4.3 Partner Developer

Owns:

- integration understanding
- API or embed usage when available
- custom implementation questions
- future client-app intelligence pieces

This role should get docs, contracts, sandbox examples, and verification tools, not raw platform internals.

### 4.4 Platform Operator

Owns:

- partner approval
- partner-store access control
- escalations
- operational evidence
- product/support feedback loop

---

## 5) Non-Goals

- Do not turn the partner dashboard into the full platform admin.
- Do not expose secrets, raw tokens, provider credentials, or low-level infrastructure controls.
- Do not make merchant Shopify admin carry partner/operator packets.
- Do not make passive acquisition or commercial attribution the partner product.
- Do not offer broad white-label until powered-by, support, brand, and escalation tradeoffs are proven.
- Do not expose partner-led custom product assembly until internal product packaging is repeatable.
- Do not make partners responsible for unresolved platform reliability gaps.

---

## 6) Information Architecture

### 6.1 Partner Home

Purpose: answer "what needs attention across my client stores?"

Shows:

- active client stores
- setup blockers
- launch blockers
- sync/freshness blockers
- support escalations
- verification status
- recent installs
- usage/value signal

### 6.2 Intelligence Catalog

Purpose: show what partners can implement.

Shows each intelligence piece:

- name
- buyer/user value
- storefront placement
- required data sources
- supported tiers
- setup steps
- verification steps
- known limitations
- demo screenshot or sandbox link

Initial catalog:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- chat/depth layer

Later catalog:

- governed add-to-cart
- cart update
- order lookup
- support handoff
- advanced value reporting

### 6.3 Sandbox And Demo Center

Purpose: let partners learn and sell before touching a client store.

Includes:

- demo store links
- sample storefront surfaces
- sample merchant admin view
- sample launch packet
- sample support escalation
- before/after product-page examples
- short clips or screenshots for partner sales calls

### 6.4 Client Store Portfolio

Purpose: manage all partner-linked stores.

Shows per store:

- shop domain and merchant name
- install status
- current plan
- enabled intelligence pieces
- storefront readiness
- knowledge sync status
- webhook/live update health
- top blocker
- last activity
- owner/contact
- escalation state

### 6.5 Client Store Workspace

Purpose: let a partner complete normal setup without using the operator dashboard.

Includes:

- setup checklist
- source readiness
- surface placement status
- widget/settings summary
- support handoff profile
- sync/retry actions
- launch readiness
- bounded billing visibility
- verification run history

Partner actions must be scoped, logged, and explainable.

### 6.6 Verification And Launch Center

Purpose: make partner rollout repeatable.

Includes:

- launch checklist
- surface verification pack
- sync verification pack
- App Review support material when relevant
- screenshot/evidence checklist
- generated launch dossier
- design-partner rollout packet

Default UI should show short summaries and copy/download actions, not long inline text.

### 6.7 Support And Escalation Center

Purpose: make first-line support repeatable and escalation useful.

Includes:

- support runbook
- support bundle export
- lifecycle/subscription packet
- scope and handoff posture
- recent usage and top questions
- escalation notes
- owner
- next action
- due date
- resolution status

### 6.8 Templates And Playbooks

Purpose: let partners deploy faster across common merchant types.

Includes:

- vertical presets
- surface presets
- source presets
- support handoff templates
- launch checklist templates
- troubleshooting playbooks

Initial verticals:

- fashion/apparel
- electronics
- health/beauty
- home/furniture
- general merchandise

### 6.9 Deferred Commercial Attribution

Purpose: explicitly keep commercial attribution out of the current partner workspace.

Not in current scope:

- public partner signup
- acquisition tracking
- partner directory
- commercial attribution ledgers
- commercial accounting workflows

Reconsider only after founding implementation partners repeatedly deploy, verify, and support client stores without platform-operator intervention.

---

## 7) Required Platform Capabilities

### 7.1 Identity And Access

- partner account
- partner member
- partner roles
- partner invitation flow
- scoped partner sessions
- partner-store assignment
- merchant or operator revocation
- platform-operator override

### 7.2 Intelligence Piece Registry

Track for each surface:

- id
- name
- description
- tier availability
- required sources
- required scopes
- setup instructions
- verification checks
- launch-safe claim text
- known limitations

### 7.3 Merchant Linking

- merchant can be linked to zero or one primary partner at first
- partner can request access to a merchant
- merchant or operator can approve/revoke access
- partner actions are audited

### 7.4 Verification Pack APIs

Expose bounded APIs for:

- setup checklist state
- source readiness
- surface activation status
- sync/freshness status
- storefront verification status
- launch readiness
- support readiness

### 7.5 Evidence Generation

Move generated packet logic behind shared APIs so partner and operator surfaces can request:

- launch dossier
- App Review guide
- screencast script
- design-partner rollout packet
- support runbook
- lifecycle/subscription packet
- support bundle

### 7.6 Support Notes And Escalations

Add partner/operator notes per merchant:

- internal note
- escalation status
- owner
- next action
- due date
- resolved date
- linked evidence bundle

### 7.7 Commercial Attribution

Current posture:

- do not build commercial attribution into the first partner workspace
- do not expose it in merchant admin
- do not make it the reason partners use the platform

Reconsider only after implementation partner workflows are proven and a separate business decision requires it.

---

## 8) Build Roadmap

### Phase 0: Clean Merchant Boundary

Goal: stop merchant admin from carrying partner/operator content.

Build:

- keep merchant `Support tools` short
- remove long packet text from merchant admin
- expose copy/download actions while partner dashboard does not exist
- define partner-only packet ownership
- keep merchant admin focused on setup, surfaces, knowledge, billing, support handoff, usage/value, and blockers

Gate:

- merchant admin can be understood by a store owner without platform explanation

### Phase 1: Founding Partner Enablement Kit

Goal: support 3-5 founding implementation partners before broad recruitment.

Build:

- demo/sandbox store
- intelligence catalog as docs or lightweight UI
- deployment checklist
- verification checklist
- escalation template
- implementation playbooks for 2-3 verticals
- partner agreement draft
- private partner communication channel

Gate:

- a founding partner can understand what can be implemented and how to verify it without a live walkthrough every time

### Phase 2: Partner Identity And Store Links

Goal: partners can log in and see assigned stores with scoped access.

Build:

- partner account model
- partner member model
- partner roles
- partner-store assignment
- scoped partner dashboard shell
- platform-operator override/revocation

Gate:

- one partner can manage multiple test stores without full operator access

### Phase 3: Intelligence Catalog And Verification Packs

Goal: partners can implement intelligence pieces repeatably.

Build:

- catalog entries for core surfaces
- per-surface setup requirements
- per-surface verification checks
- source readiness requirements
- launch-safe claim text
- packet summary cards
- copy/download actions

Gate:

- partner can choose a surface, install it, verify it, and explain it to a client from the dashboard

### Phase 4: Multi-Store Health And Client Workspace

Goal: partners can manage setup and support across stores.

Build:

- portfolio table
- health rollups
- blockers
- plan status
- enabled surfaces
- usage/value signals
- sync/live update status
- bounded sync/retry controls
- client store workspace

Gate:

- partner can prioritize stores and onboard a client store without platform-operator help

### Phase 5: Support And Escalation Center

Goal: partners can handle first-line support and escalate with evidence.

Build:

- support runbook surface
- support bundle export
- lifecycle/subscription packet
- escalation notes
- owner/status/next action
- evidence links

Gate:

- support escalations arrive with enough context for the builder/operator to act without reconstructing the issue from chat history

### Phase 6: Advanced Implementation Enablement

Goal: add implementation leverage once the foundation is proven.

Build:

- deeper multi-store health rollups
- reusable implementation templates
- richer verification automation
- client-app intelligence integration examples
- advanced support evidence exports

Gate:

- founding implementation partners can repeatedly deploy and support stores without platform-operator intervention

### Phase 7: Public Partner Scale Gate

Goal: decide whether public partner scale is justified.

Possible later work:

- partner API
- bulk setup templates
- certification
- partner directory
- white-label options
- client-app intelligence integrations beyond Shopify
- public signup

Gate:

- founding partners repeatedly deploy stores successfully, support load is understood, and platform reliability is strong enough for broader partner promises

---

## 9) Launch Gates

Founding implementation partners can start when:

- canonical launch truth is stable
- demo/sandbox store exists
- core intelligence pieces have clear setup and verification instructions
- support escalation path exists
- partner agreement and scope boundaries are clear

Broad partner recruitment should wait until:

- Shopify Companion live verification is green
- Free/Starter/Elite tier truth is stable
- merchant admin is clean and merchant-safe
- at least 3-5 design-partner stores complete setup
- at least 1-3 founding partners complete real or test deployments
- support runbook has been used in real support situations
- partner-store access is scoped and revocable

White-label and partner APIs should wait until:

- partner operations are stable
- support load is measurable
- brand/support ownership is explicit
- implementation templates are repeatable

---

## 10) Success Metrics

### Enablement Metrics

- founding partners onboarded
- partner time to first successful deployment
- partner setup completion without operator help
- verification-pack pass rate
- repeated setup questions converted into docs/playbooks

### Portfolio Metrics

- stores per partner
- stores blocked by support/sync/billing
- enabled surfaces per store
- time to resolve launch blockers
- partner-managed activation rate

### Support Metrics

- partner escalations per store
- escalations with complete evidence
- time to triage partner escalation
- support runbook coverage
- merchant support escalations per partner-managed store

### Partner Enablement Metrics

- client stores implemented
- time to first verified setup
- verification pass rate
- support escalations per partner-managed store
- stores launched without operator intervention
- repeated deployments per partner

Commercial metrics are not part of the first enablement milestone.

---

## 11) Product Boundary

The merchant admin is for merchant confidence and action.

The partner dashboard is for implementation enablement, multi-store support, verification, evidence, templates, and escalation.

The operator dashboard is for platform internals.

Do not collapse these surfaces again.

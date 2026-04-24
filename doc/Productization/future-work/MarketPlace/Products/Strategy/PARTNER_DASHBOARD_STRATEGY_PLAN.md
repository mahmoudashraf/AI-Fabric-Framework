# Partner Dashboard Strategy Plan

Status: strategy plan (2026-04-24)

This plan defines what must be built to support partners without overloading the merchant Shopify admin UI.

## 1) Decision

Partner and operator material should move out of the merchant admin surface.

Merchant Shopify admin should stay focused on:

- setup
- storefront surfaces
- knowledge sync
- billing
- support handoff
- usage and value
- clear launch blockers

Partner dashboard should own:

- multi-store management
- launch and App Review packets
- support runbooks
- design-partner rollout evidence
- raw support bundles
- webhook/vectorization diagnostics
- partner referrals, commissions, and payouts

## 2) Product Goal

Build a partner dashboard that lets agencies, consultants, and integrators deploy, monitor, support, and grow Loom Companion across multiple merchant stores.

The partner dashboard should make partners effective without exposing the full operator control plane.

## 3) Primary Users

### Partner Admin

Owns the partner account, invited team members, merchant portfolio, billing/revenue view, and partner agreement.

### Partner Operator

Deploys stores, configures surfaces, monitors health, exports support evidence, and handles first-line merchant support.

### Platform Operator

Sees partner activity, escalations, health across partner-managed stores, and operational evidence.

## 4) Non-Goals

- Do not turn the partner dashboard into the full platform admin.
- Do not expose secrets, raw access tokens, provider credentials, or low-level infrastructure controls.
- Do not make merchants read launch dossiers, App Review packets, or raw support bundles.
- Do not start broad white-label work until partner onboarding and support workflows are proven.

## 5) Information Architecture

### 5.1 Partner Home

Purpose: answer "which stores need attention?"

Shows:

- active merchants
- stores blocked by launch/support/billing/sync
- recent installs
- plan mix
- usage/value signal
- partner revenue summary

### 5.2 Merchant Portfolio

Purpose: manage all partner-linked stores.

Shows per store:

- shop domain and merchant name
- install status
- current plan
- storefront readiness
- knowledge sync status
- webhook/live update health
- top blocker
- last activity
- owner/contact

### 5.3 Merchant Workspace

Purpose: let a partner complete setup without using the operator dashboard.

Includes:

- setup checklist
- source categories
- storefront surface placement
- widget settings summary
- support handoff profile
- sync/retry actions
- safe billing visibility
- launch readiness

Partner actions must stay bounded and explainable.

### 5.4 Launch And Review Center

Purpose: move long generated launch material out of merchant admin.

Includes:

- launch dossier
- App Review guide
- review screencast script
- App Store listing package
- design-partner rollout packet
- screenshot/evidence checklist

Default UI should show short summaries and copy/download actions, not long inline text.

### 5.5 Support Center

Purpose: make first-line support repeatable.

Includes:

- support runbook
- raw support bundle export
- lifecycle/subscription packet
- scope and handoff posture
- recent usage and top questions
- escalation notes
- support status per merchant

### 5.6 Revenue And Referrals

Purpose: make partner economics visible.

Includes:

- referral links/codes
- referred installs
- active paid merchants
- commission events
- payout history
- churned stores
- conversion funnel

### 5.7 Templates And Playbooks

Purpose: let partners deploy faster across common merchant types.

Includes:

- surface presets
- source presets
- support handoff templates
- launch checklist templates
- vertical playbooks

## 6) Required Platform Capabilities

### Identity And Access

- partner account
- partner member
- partner roles
- merchant-to-partner assignment
- partner invitation flow
- scoped partner access tokens/session

### Merchant Linking

- merchant can be linked to zero or one primary partner at first
- platform operator can override partner assignment
- partner can request access to a merchant
- merchant or operator can revoke partner access

### Evidence Generation

Move generated packet logic behind shared APIs so both partner and operator surfaces can request:

- launch dossier
- App Review guide
- screencast script
- design-partner rollout packet
- support runbook
- lifecycle/subscription packet
- raw support bundle

### Support Notes

Add partner/operator notes per merchant:

- internal note
- escalation status
- owner
- next action
- due date
- resolved date

### Referral And Commission

Track:

- partner referral code
- install attribution
- plan activation attribution
- commission event
- payout batch
- clawback/cancellation state

## 7) Build Roadmap

### Phase 0: Clean Merchant Boundary

Goal: stop merchant admin from carrying partner/operator content.

Build:

- keep merchant `Support tools` short
- remove long packet text from merchant admin
- add clear copy/download only while partner dashboard does not exist
- define partner-only packet ownership

Gate:

- merchant admin can be understood by a store owner without platform explanation

### Phase 1: Partner Identity And Store Links

Goal: partners can log in and see assigned stores.

Build:

- partner account model
- partner member model
- partner roles
- partner-store assignment
- basic partner dashboard shell

Gate:

- one partner can manage multiple test stores with scoped access

### Phase 2: Multi-Store Health Dashboard

Goal: partners can see what needs work across all stores.

Build:

- portfolio table
- health rollups
- blockers
- plan status
- usage/value signals
- last sync/live update status

Gate:

- partner can prioritize stores without opening each merchant admin page

### Phase 3: Merchant Workspace

Goal: partners can complete normal setup/support actions for one store.

Build:

- setup checklist
- source and surface summary
- support handoff editor
- launch readiness
- bounded sync/retry controls
- link to merchant-facing Shopify admin when needed

Gate:

- partner can onboard a design-partner store without platform operator help

### Phase 4: Launch And Support Packet Center

Goal: long runbooks and generated documents live in the partner dashboard.

Build:

- packet summary cards
- copy/download actions
- generated-at/store posture metadata
- evidence checklist
- support runbook export
- raw support bundle export

Gate:

- merchant admin no longer needs any launch dossier, App Review guide, design-partner packet, or raw support bundle

### Phase 5: Partner Revenue And Referral Tracking

Goal: partners can see business value.

Build:

- referral links/codes
- install attribution
- active paid merchants
- commission ledger
- payout history
- churn visibility

Gate:

- commission reporting can be trusted for first partner payouts

### Phase 6: Templates And Repeatability

Goal: partners can deploy faster and more consistently.

Build:

- vertical presets
- setup templates
- support handoff templates
- reusable launch checklist
- optional bulk apply after safety review

Gate:

- repeat partner setup time is under 30-45 minutes per store

## 8) Launch Gates

Do not recruit broad partners until:

- Shopify Companion live verification is green
- Free/Starter/Elite tier truth is stable
- merchant admin is clean and merchant-safe
- at least 3-5 design-partner stores complete setup
- support runbook has been used in real incidents
- partner-store access model is scoped and revocable

## 9) Success Metrics

- partner setup time per store
- stores per partner
- stores blocked by support/sync/billing
- time to resolve launch blockers
- active paid merchants by partner
- partner-attributed MRR
- commission payout accuracy
- merchant support escalations per store
- churned partner-managed stores

## 10) Product Boundary

The merchant admin is for merchant confidence and action.

The partner dashboard is for deployment, evidence, support, and portfolio management.

The operator dashboard is for platform internals.

Do not collapse these surfaces again.

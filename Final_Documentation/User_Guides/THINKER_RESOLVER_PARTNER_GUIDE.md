# Thinker Resolver Partner Guide

Status: partner-facing guide for assigned-store Thinker/Resolver support (2026-04-29)

This guide is for implementation partners supporting merchants through the Partner UI.

Related guides:

- [Shopify Companion Customer Capabilities Guide](./SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md)
- [Thinker Resolver Operator Guide](./THINKER_RESOLVER_OPERATOR_GUIDE.md)
- [Partner Enablement Deployment Guide](../Development_Guides/PARTNER_ENABLEMENT_DEPLOYMENT_GUIDE.md)

---

## 1) What Partners Can See

Partners can access Thinker sessions only for stores assigned to their partner account.

Required assignment permissions:

- `STORE_READ` for the store workspace
- `PRODUCT_CONFIG_READ` for Thinker session visibility
- `SUPPORT_MANAGE` to create partner support handoffs

Partner-visible data is redacted. The partner view does not expose:

- tenant IDs
- customer IDs
- user subject identifiers
- operator raw evidence references
- operator proposal parameters
- secrets or runtime credentials

---

## 2) Partner UI

Open Partner UI and use:

- main Thinker route: `/thinker`
- store workspace Thinker tab: `/stores/{storeId}` then `Thinker`

Use the Thinker page to:

- choose an assigned store
- review recent Thinker sessions
- inspect redacted evidence and resolution plans
- see write-required escalation status
- create a partner support handoff when permitted

---

## 3) Partner API

Partner routes require a valid Supabase partner bearer token and an active Platform partner member.

Core routes:

- `GET /api/partners/stores/{storeId}/thinker/sessions`
- `GET /api/partners/thinker/sessions/{sessionId}`
- `POST /api/partners/thinker/sessions/{sessionId}/support-escalations`

The backend checks the assignment, store ownership, permissions, and assignment status on each call. A revoked assignment loses access immediately.

---

## 4) Support Handoff Rules

Create a support handoff only when:

- the session belongs to an assigned store
- the evidence is enough to explain the issue
- the merchant-facing next action is clear
- the partner assignment includes `SUPPORT_MANAGE`

The handoff writes real Platform Partner Enablement records:

- partner evidence bundle
- partner support escalation

It is not a placeholder or separate duplicate data store.

---

## 5) What Partners Cannot Do

Partners cannot:

- enable Thinker for a deployment
- enable governed execution
- bypass policy decisions
- execute arbitrary writes
- view unassigned stores
- view raw operator evidence
- change merchant tier or product truth

Merchant and Platform controls remain the authority for store installation, tier, deployment, and execution posture.

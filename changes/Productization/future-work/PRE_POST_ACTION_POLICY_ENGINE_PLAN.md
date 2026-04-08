# Pre/Post Action Policy Engine Plan

Status: planning document (2026-04-07)

This document defines a generalised policy framework around action execution that extends the existing confirmation interception into a full pre/post action lifecycle.

---

## 1) Problem Statement

The platform currently has a confirmation interception mechanism for write actions. This is a strong differentiator (competitors auto-execute without confirmation). However, the current implementation is binary: confirm or not.

Real-world deployments need richer policies:

- **Pre-action:** Should we confirm? Should we check auth? Should we rate-limit? Should we validate input against business rules?
- **Post-action:** Should we send a webhook? Should we log to audit trail? Should we trigger a Zapier workflow? Should we notify the merchant via Slack/email?

These are not one-off features — they are a **policy engine** that makes every action configurable without code.

---

## 2) Architecture

### Current state

```
User request → Orchestrator → Action selected → Confirmation? → Execute → Response
```

### Target state

```
User request → Orchestrator → Action selected
    │
    ▼
┌──────────────────────────────────────┐
│         PRE-ACTION POLICIES          │
│                                      │
│  1. Auth policy    → Is caller       │
│                      authorised?     │
│  2. Rate limit     → Within quota?   │
│  3. Confirmation   → Requires user   │
│                      confirmation?   │
│  4. Input guard    → Validate params │
│                      against rules   │
│  5. Business rule  → Custom webhook  │
│                      to merchant     │
│                      for approval    │
│  6. Cost gate      → Will this       │
│                      exceed budget?  │
│                                      │
│  Any policy can BLOCK or MODIFY      │
│  the action request                  │
└──────────────┬───────────────────────┘
               │
               ▼
         Execute Action
               │
               ▼
┌──────────────────────────────────────┐
│         POST-ACTION POLICIES         │
│                                      │
│  1. Audit log      → Record what     │
│                      happened        │
│  2. Webhook out    → POST to         │
│                      merchant URL    │
│  3. Zapier trigger → Fire Zapier     │
│                      webhook         │
│  4. Notification   → Email/Slack     │
│                      merchant        │
│  5. Analytics      → Track action    │
│                      metrics         │
│  6. Chain action   → Trigger         │
│                      follow-up       │
│                                      │
│  Policies execute async (do not      │
│  block response to user)             │
└──────────────────────────────────────┘
```

### Policy definition (per deployment, per action)

```yaml
# Example: deployment action policy configuration
actions:
  create-return:
    pre-policies:
      - type: confirmation
        message: "Create return for order {{orderId}}?"
      - type: rate-limit
        max: 5
        window: 1h
        per: session
      - type: input-guard
        rules:
          - field: refundAmount
            max: 500
            exceed-action: escalate
    post-policies:
      - type: webhook
        url: "https://merchant.com/webhooks/returns"
        method: POST
      - type: zapier
        webhook-url: "https://hooks.zapier.com/hooks/catch/xxx"
      - type: audit
        level: full

  check-order-status:
    pre-policies:
      - type: rate-limit
        max: 20
        window: 1m
        per: user
    post-policies:
      - type: analytics
        event: order-status-checked
```

### Policy engine interface

```java
public interface ActionPolicy {
    String getType();
    PolicyPhase getPhase();          // PRE or POST
    PolicyResult evaluate(ActionContext context);
}

public enum PolicyPhase { PRE, POST }

public record PolicyResult(
    PolicyDecision decision,         // ALLOW, BLOCK, MODIFY, ESCALATE
    String reason,
    Map<String, Object> modifications
) {}
```

---

## 3) Pre-Action Policy Types

| Policy Type | Purpose | Blocks Execution? | Configuration |
|---|---|---|---|
| **confirmation** | Require user to confirm before executing | Yes — waits for user | Message template, skip conditions |
| **rate-limit** | Throttle action frequency | Yes — if exceeded | Max count, time window, per user/session/tenant |
| **auth-check** | Verify caller has permission for this action | Yes — if unauthorised | Required roles, scopes |
| **input-guard** | Validate action parameters against rules | Yes — if invalid | Field rules (min, max, pattern, allowed values) |
| **cost-gate** | Check if action would exceed budget/quota | Yes — if over budget | Budget limit, current usage lookup |
| **business-rule-webhook** | Call merchant's endpoint for custom approval | Yes — waits for response | Merchant webhook URL, timeout |

---

## 4) Post-Action Policy Types

| Policy Type | Purpose | Blocking? | Configuration |
|---|---|---|---|
| **audit** | Log action execution details to audit trail | No (async) | Log level (summary/full), retention |
| **webhook** | POST action result to merchant endpoint | No (async) | URL, method, headers, retry policy |
| **zapier** | Trigger Zapier catch hook | No (async) | Zapier webhook URL |
| **notification** | Notify merchant via email/Slack | No (async) | Channel, recipients, template |
| **analytics** | Track action event for dashboard | No (async) | Event name, dimensions |
| **chain-action** | Trigger a follow-up action | No (async) | Next action ID, parameter mapping |

---

## 5) Why This Matters for Market Position

1. **Extends confirmation safety** — the platform's sharpest differentiator — into a full governance layer
2. **Zapier integration for free** — post-action webhooks give merchants 5000+ app connections without building dedicated connectors
3. **Business rule hooks** — merchants can inject their own approval logic (e.g. "refunds over £100 need manager approval") without touching AI configuration
4. **Audit trail** — enterprise requirement for compliance; no competitor offers action-level audit logging
5. **Cost gates** — prevents runaway AI agents from executing expensive operations without budget checks

---

## 6) Implementation Scope

| Component | Effort | Dependencies |
|---|---|---|
| PolicyEngine interface and registry | Small | None |
| Confirmation policy (refactor existing) | Small | Existing confirmation interception |
| Rate-limit policy | Small | None — in-memory or Redis |
| Webhook post-action policy | Medium | Outbound HTTP with retry |
| Audit log policy | Medium | Audit storage (DB table or log service) |
| Input-guard policy | Small | JSON Schema validation |
| Platform UI for policy configuration | Medium | Platform frontend |
| Policy configuration in deployment YAML | Small | Deployment config model |

**Total estimate:** 3-4 weeks for core engine + first 4 policies. Additional policies added incrementally.

---

## 7) Relation to Existing Plans

- Extends `CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md` — confirmation becomes one policy type among many
- Enables the Zapier/webhook integration from `SAAS_STRATEGY_ASSUMPTIONS_EVALUATION.md` without dedicated connectors
- Feeds into the analytics dashboard requirement (post-action analytics policy provides the data)
- Supports the `REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md` — remote confirmation becomes a pre-action business-rule-webhook policy

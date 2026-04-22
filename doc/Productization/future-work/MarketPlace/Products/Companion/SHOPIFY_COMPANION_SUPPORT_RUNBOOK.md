# Shopify Companion Support Runbook

Status: operational baseline for Wave 8 launch hardening

This runbook is for:

- platform operators
- Shopify Bridge operators
- implementation/support engineers assisting a merchant rollout

It is not the merchant-facing help center.

## 1. Core Principle

Treat Shopify Companion as three layers that must be checked separately:

1. platform control plane
2. Shopify Bridge service
3. storefront widget / theme app extension

A store is healthy only when all three agree:

- the store mapping is `INSTALLED`
- source readiness is `READY`
- the latest release is applied and verified
- sync status is `SYNCED`
- storefront bootstrap is available

## 2. Primary Verification Commands

Normal product verification:

```bash
scripts/verify-shopify-companion.sh
```

Uninstall and cleanup verification:

```bash
scripts/verify-shopify-companion-uninstall.sh
```

These scripts are the canonical fast check before deeper debugging.

GitHub Actions operator entrypoint:

```text
.github/workflows/shopify-companion-verification.yml
```

Use workflow mode `verify` for the normal live check, `rollout` for platform-side bootstrap/go-live progression, and `uninstall_verify` only for a disposable shop mapping with explicit destructive confirmation.

## 3. Triage Order

Always inspect in this order:

1. product service health and dependents
2. store summary and readiness blockers
3. vectorization summary, policy, and live-update backlog
4. bridge shell and merchant session
5. storefront bootstrap
6. sync details and webhook detail
7. latest deployment release / verification state

Do not start with the storefront widget if the platform store summary is already blocked.

## 4. Primary Operator Reads

Use the platform APIs or UI pages for:

- managed product service summary
- managed product service health
- managed product service overview / dependents
- Shopify store summary
- Shopify store mapping, deployment, and consumer drill-through
- Shopify vectorization summary, policy, indexed fields, automation summary, and recent events

Use the Shopify Bridge service for:

- merchant session state
- merchant UI shell
- storefront bootstrap/query/suggestions/events
- vectorization source-page reachability when bridge admin access is available

## 5. Common Failure Modes

### 5.1 Install/auth failure

Symptoms:

- merchant session cannot resolve
- install record missing
- merchant UI shows reinstall guidance immediately

Checks:

- confirm Partner App install completed for the shop
- confirm Shopify session token path is working
- confirm the shop appears in bridge install records

Recovery:

- repeat the Shopify install/auth flow
- if marked `UNINSTALLED`, reinstall before attempting connect/bootstrap again

### 5.2 Platform bootstrap incomplete

Symptoms:

- store summary missing customer, deployment, or consumer
- readiness says platform bootstrap is incomplete

Checks:

- store summary fields:
  - `customerId`
  - `deploymentId`
  - `consumerId`
- deployment exists and is owned by the mapped customer

Recovery:

- rerun bootstrap from the merchant UI or platform operator flow
- confirm consumer binding points to the intended deployment

### 5.3 Source readiness blocked

Symptoms:

- source readiness is not `READY`
- go-live is blocked before apply

Checks:

- source preflight category counts
- enabled source-category toggles
- bridge credential status

Recovery:

- reconnect to persist fresh Shopify credentials
- rerun source preflight
- disable only the bounded source categories that are known-bad

### 5.4 Apply-time sync failure

Symptoms:

- release is verified or applied but store sync status is `FAILED`
- storefront bootstrap remains unavailable

Checks:

- store `syncDetail`
- deployment release verification status
- runtime dataset sync path

Recovery:

- rerun sync from merchant UI after fixing the root issue
- if runtime dataset state is suspect, re-apply the deployment and resync

### 5.5 Storefront bootstrap unavailable

Symptoms:

- `/api/storefront/shops/{shop}/bootstrap` returns `available=false`

Checks:

- readiness blockers from the platform store summary
- widget status
- latest release verification result

Recovery:

- resolve the first listed storefront blocker
- do not debug theme embed behavior before bootstrap becomes available

### 5.6 Billing blocks go-live

Symptoms:

- merchant can connect and sync but go-live returns a conflict

Checks:

- bridge billing summary
- merchant UI billing message

Recovery:

- if running free launch mode, ensure billing mode is configured that way
- if paid launch, complete the Shopify billing setup before go-live

### 5.7 Uninstall / cleanup issues

Symptoms:

- install status is `UNINSTALLED`
- storefront bootstrap unavailable
- merchant session requires reinstall

Checks:

- platform store summary:
  - `installStatus`
  - `credentials.status`
  - `syncDetail.status`
- uninstall details in the store record

Recovery:

- for reinstall: complete Shopify install flow again, reconnect, rerun source preflight, then go live
- for cleanup failure: inspect sync cleanup status and rerun uninstall verification after correcting runtime cleanup issues

## 6. Escalation Rules

Escalate to platform engineering when:

- product service health/drift is broken
- bootstrap produces inconsistent customer/deployment/consumer bindings
- apply-time sync fails repeatedly for known-good source payloads
- storefront bootstrap is blocked even when readiness appears healthy

Escalate to Shopify app/integration engineering when:

- Partner App install/auth is failing
- webhook delivery is not arriving
- theme app extension activation is inconsistent across stores/themes

## 7. Live Verification Before Merchant Rollout

Before onboarding a new design partner or reviewer, confirm:

1. `scripts/verify-shopify-companion.sh` passes
2. vectorization summary shows policy, indexed fields, and a healthy or explainable live-update backlog
3. the merchant UI resolves session, store summary, preview, and support bundle
4. the storefront widget loads from the theme app extension
5. uninstall verification can be run on a disposable store mapping

## 8. Non-Goals For Support

Do not support through ad hoc changes:

- arbitrary plugin edits inside the merchant app
- arbitrary dataset schema changes
- arbitrary model/vectorization tuning
- write-action expansion during incident handling

Support should stay inside the bounded Shopify Companion product posture.

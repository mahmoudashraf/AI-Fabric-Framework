# Customer Operator User Guide

Status: customer-facing product guide for the current platform direction (2026-03-29)

This guide is for the **Customer Operator** user type.

Customer Operator is the day-to-day observer on the customer side. This user should be able to see whether the deployment is healthy and provide operational context back to the implementation/support team, without owning the underlying platform configuration.

Important status note:

- the **customer-operator persona is defined**
- a **separate backend customer-operator role is not yet fully enforced**

Companion guide:

- `Final_Documentation/User_Guides/PLATFORM_USER_TYPES_GUIDE.md`

---

## 1) What Customer Operator Is For

Customer Operator should be able to:

- see if the deployment is healthy
- know whether a release is still in progress
- confirm whether verification passed
- report useful context when something looks wrong

Customer Operator should **not** be the person making platform configuration changes.

---

## 2) Recommended Scope

Customer Operator should primarily use:

- `Deployments`
- `Diagnostics`

Optional:

- `Revisions` for high-level release visibility

Customer Operator should not be responsible for:

- editing drafts
- publishing versions
- rotating secrets
- changing routing/providers/security

---

## 3) Suggested Day-To-Day Workflow

### 3.1 Check Deployment Health

In `Deployments`, review:

- deployment status
- health status
- health summary

If health is positive, no further action may be required.

### 3.2 Check Release Progress

If health is unclear or a rollout is in progress:

- open `Diagnostics`
- inspect the latest release state
- note the current step and any visible error

### 3.3 Check Verification Result

In `Diagnostics`, confirm whether:

- verification passed
- warnings exist
- failures need escalation

### 3.4 Report Useful Context

When escalating an issue, report:

- deployment name
- environment
- current status
- latest release id/status
- latest verification summary

This is much more useful than reporting only that “it is broken.”

---

## 4) What Customer Operator Should Not Do

Avoid:

- changing advanced platform config
- using internal backend routes directly
- rotating secrets
- using the public provisioning API

Customer Operator is an observer and operational reporter, not the deployment configurator.

---

## 5) Escalation Path

Escalate to Customer Admin or the enablement team when:

- deployment health is degraded
- a release is stuck
- verification failed
- runtime/connector URLs are missing or unexpected

Escalate to Platform Operator if the issue appears deployment-specific.

Escalate to Platform Admin only if the issue appears platform-global or secret-related.

---

## 6) Related Docs

- `Final_Documentation/User_Guides/CUSTOMER_ADMIN_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`


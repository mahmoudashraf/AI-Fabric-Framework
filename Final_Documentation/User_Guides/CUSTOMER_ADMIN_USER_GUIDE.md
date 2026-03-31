# Customer Admin User Guide

Status: customer-facing product guide for the current platform direction (2026-03-29)

This guide is for the **Customer Admin** user type.

Customer Admin is the customer-side owner of a deployment. This user is responsible for understanding whether the customer deployment is healthy, coordinating change requests, and approving high-level deployment lifecycle operations.

Important status note:

- the **customer-admin persona is defined**
- a **separate backend customer-admin role is not yet fully enforced**
- in the current branch, this guide describes the intended customer-facing workflow and safe operating model

Companion guide:

- `Final_Documentation/User_Guides/PLATFORM_USER_TYPES_GUIDE.md`

---

## 1) What Customer Admin Is Meant To Own

Customer Admin should be able to:

- understand what deployment exists for the customer
- see whether the deployment is healthy
- understand what version is active
- coordinate rollout timing with the enablement team
- review safe lifecycle state without diving into low-level internals

Customer Admin should generally **not** be the person editing low-level routing, provider, or secret configuration directly.

---

## 2) Current Product Reality

In the current branch:

- customer-facing lifecycle UX exists
- deployment health summaries exist
- archive flow exists
- revisions and diagnostics navigation exist

But:

- customer-admin is not yet a separately enforced backend role
- org/project ownership isolation is not complete yet
- customer-safe hidden/advanced screen separation is still evolving

So today, customer-admin access should be treated as:

- a **scoped operational workflow**
- not yet a fully isolated product role

---

## 3) What Customer Admin Should Focus On

Recommended screens:

- `Deployments`
- `Revisions`
- `Diagnostics`

Primary questions Customer Admin should answer:

- do we have an active deployment?
- what template/environment is it using?
- is it healthy?
- what is the latest release doing?
- has verification passed?

---

## 4) Suggested Customer Admin Workflow

### 4.1 Confirm Deployment Exists

In `Deployments`, confirm:

- deployment name
- environment
- status
- health summary

### 4.2 Confirm Active Version

In `Revisions`, confirm:

- the latest published version
- whether a release is in progress
- whether the deployment has been applied successfully

### 4.3 Review Diagnostics When Needed

Open `Diagnostics` when:

- a rollout is still running
- verification failed
- the health summary is unclear

Look for:

- latest release status
- current step
- latest verification summary

### 4.4 Coordinate With Your Enablement Team

If customer-specific changes are needed:

- request them through your platform operator / enablement consultant
- confirm when the new version is published and applied
- use deployment health and verification to confirm the rollout result

---

## 5) What Customer Admin Should Not Change Directly

Avoid direct ownership of:

- platform secrets
- provider internals
- action routing internals
- raw knowledge/entity schema changes

Those changes affect deployment correctness and should stay with the enablement team until the customer-safe product role model is complete.

---

## 6) Recommended Permissions Model

When the product role is fully enforced, Customer Admin should ideally be allowed to:

- view deployments
- create customer deployments from allowed templates
- request or trigger safe apply/re-apply
- archive only with confirmation and policy
- view customer-safe diagnostics

Customer Admin should not automatically get:

- platform secret mutation
- unrestricted advanced configuration access
- unrestricted audit visibility across other customers

---

## 7) Escalation Rules

Escalate to your enablement team if:

- verification fails
- a deployment appears unhealthy
- runtime or connector URLs are missing after rollout
- changes are needed to actions, providers, or security behavior

Escalate to Platform Admin if the issue is clearly platform-global rather than deployment-specific.

---

## 8) Related Docs

- `Final_Documentation/User_Guides/CUSTOMER_OPERATOR_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`
- `changes/Productization/PLATFORM_PHASE_18_PLUS_EXECUTION_PLAN.md`


# Platform Cleanup And Live Customer Journey Audit

Started: 4 November 2026. Status: IN PROGRESS.

## Owner Request

Remove unused drafts and non-running Platform resources, then exercise the
customer journeys through the live UI and resolve reproducible defects.

## Safety And Scope

- Inventory current references before deletion. Preserve productized templates,
  live customer assignments, shared services, approved source storage, ProdUS,
  Shopify and framework demos. A stopped resource is not automatically unused.
- Use the normal archive/deletion lifecycle for confirmed disposable fixtures.
  Record exact IDs and terminal results. Never delete shared dependencies by name.
- Keep the unfinished 010.59 document-storage qualification separate from
  disposable drafts. No framework release or recovery redesign is implied.
- UI actions are live-browser evidence. Read-only API/native checks supplement
  them but do not substitute for a successful customer UI journey.
- Record failed attempts and incomplete coverage honestly; no blanket zero-error
  claim based on page loading or a single happy-path run.

## Checklist

- [x] Current staging/production inventory and reference map.
- [ ] Confirmed disposable drafts/test resources removed and terminally verified.
- [ ] Public products, Kit catalogue and authentication entry/recovery navigation.
- [ ] Kit creation, address validation/preview, setup and edit/reset navigation.
- [ ] Deployment configuration, integrations and document upload/indexing.
- [ ] Staging publication, launch/readiness and Engage connection.
- [ ] Engage branding/capability preview, embed/standalone and domain controls.
- [ ] Production promotion, activation and assignment continuity.
- [ ] End-user inventory/knowledge, follow-up, attachment and action journeys.
- [ ] Desk handover/reply/return and support request/My Requests journeys.
- [ ] Account/theme/sign-out, basic phone navigation and empty/error states.
- [ ] Fixes retested, final inventory/evidence recorded, task-only changes committed.

## Results

Historical acceptance is context, not a fresh pass for this audit.

### Cleanup Cycle 1

Before cleanup: staging 40 deployment records, production 51. Removed 19 empty
configuration-only fixtures through the existing archive/delete operation and
observed every receipt SUCCEEDED. Removed three never-active production app
drafts and the two associated abandoned Kits. No direct SQL deletion or native
service/database removal. Exact private snapshots and receipts are retained.

Staging deployment IDs removed: dep-db9976e3, dep-2bcc8c44, dep-a9fbb2e4,
dep-fc33edc4, dep-21baf7fd, dep-34ca281c, dep-49b45875, dep-cb2fe53c,
dep-6640aa2d, dep-08ac088a, dep-abb47160.

Production IDs removed: dep-30ea5174, dep-f3b5b977, dep-4e084db3, dep-4466d153,
dep-628c5848, dep-61abaf50, dep-59b4d5ef, dep-fa0df093.

Two app-cleanup defects reproduced: disabled apps were forbidden by the normal
deletion API, and unused unbound drafts required a deployment binding. Bounded
fixes retain customer ownership, normal authorization/locks/audit, active-app
protection and owned domain/route cleanup. Backend focused tests: 114 passed,
zero failures/errors/skips. Console deletion guard now allows cleanly unbound
cleanup only for explicit deletion; creation/activation still need connections.
Its confirmation explains permanent app/address removal, not document or
conversation deletion. Source is pending commit/rollout at this checkpoint.

### Live UI Observations

- Public catalogue and dismissible dealership MAX preview passed.
- Engage: connected a verified staging assignment without reassigning it;
  branding/contextual tools/phone preview and draft save/reopen passed. Temporary
  app `awi_pub_ae82edd559d464657ffdd49fb14d616b` is tracked for cleanup.
- Fresh Kit: starting experience, optional managed AI-state choice, branding,
  invalid address feedback, valid full app-link preview, review and creation
  passed. Kit kit-46bbdf7c-fff2-49fd-9394-40aff5aa8682 owns dep-a0a6ac0a and
  app awi_pub_8d628c084b4caf7c847b57fa0c588caa. UI publication created
  ver-45fedc31. No runtime applied or paid infrastructure created for this Kit.
- Production Northfield: anonymous inventory search and a compound follow-up
  comparing the same cars with approved delivery policy passed. No knowledge-
  domain question. Protected action opened sign-in; cancelling did not execute.
  Normal fixture-owner sign-in passed. Trusted item attachment selected This
  vehicle, and Browse remained usable while the item stayed attached.
- Console Support: real request form submission passed without ACCESS_DENIED;
  its opening details appeared in chat and AI answered from approved guides.
  Domain/readiness guidance was accurate, but the answer focused mainly on
  custom domains and omitted part of the requested website-embed setup. Treat
  this as PARTIAL semantic coverage, not a complete answer-quality pass.

### Fresh Simulator Setup Blocker

The new Kit has no simulator account/receiver approvals or bindings. Existing
Northfield has instance-only credentials and a receiver fixed to its own
deployment/tenant. Neither v2 nor the new v3 bootstrap creates a simulator
account. Current documented integration is attended: operator prepares the
account/receiver, staff approves exact scope, customer binds credentials.

This is a missing self-service simulator supply workflow, not an approval-lookup
failure. Do not copy Northfield credentials, repoint its receiver, bypass account
scope or add a per-deployment business database. Completing that fresh launch
requires isolated account/scoped receiver provisioning in shared external
services, then the existing generic integration contracts. The UI now offers
integration help instead of an unexplained administrator dead end. Full fresh
staging/promotion acceptance remains NOT PASSED.

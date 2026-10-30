# 010.53 Request Integration Support Customer Journey Plan

**Status: DRAFT FOR OWNER REVIEW**

**Created:** 2026-10-30

**Scope:** Product proposal only. No implementation, schema, release or operational change is approved by this document.

## 1. Verdict

Add a lightweight **Request integration support** journey to the customer Console: one global Help entry, contextual shortcuts from deployments, Engage apps and Connections, a short form, optional consented diagnostic context, customer request tracking and a small integration-team triage view.

Keep the customer experience shallow and the persistence/authorization contract complete. A request must be durably recorded and trackable, not just open an email composer. This is assistance from LoomAI's integration team to the customer configuring their product. It is not an Engage visitor asking a dealership employee to take over a conversation.

Do not build a ticketing platform, require Desk enablement, add a new customer portal, or make submitting a request grant anyone access to customer systems. Existing configuration editors and deployment lifecycles remain authoritative.

## 2. Recovered Intent And New Proposals

The supplied earlier chat request was:

> We need to include a feature so customer request integration team support. How can we do it and where exactly and how light/deep should be. Give verdict/summary.

The recovered intent is a customer-facing way to request integration-team help, with a recommendation on placement and depth. It does not establish an approved design, API, delivery date, commercial entitlement or permission to implement. No earlier assistant verdict is available as an approved decision here.

All entry-point labels, routes, fields, states and delivery choices below are **new proposals for review**. The current authorization is to create this review plan only.

A targeted repository Markdown search found related Help/contact-support mentions and existing partner support and Desk plans, but no dedicated customer integration-support journey plan. Generated/output directories and private credential/handoff material were excluded. The relevant source contracts are distinguished below; absence is a scoped search finding, not a claim that no support process exists outside this repository.

## 3. Existing Capabilities And Gaps

These are source observations, not fresh hosted verification. Current execution status belongs in [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md); historical plan entries alone do not establish the deployed version.

- **Console navigation exists.** Customer routes include `/home`, `/deployments`, `/ai-workspaces`, `/integrations`, `/human-support`, `/notifications` and `/users`. Their visible labels include **Engage apps**, **Connections**, **Human support**, **Activity & alerts** and **Account & team**. There is no integration-support route or global Help control in the inspected customer shell. See [App.tsx](../../../../../../../../Platfrom/ui/src/App.tsx) and [CustomerConsoleShell.tsx](../../../../../../../../Platfrom/ui/src/layout/CustomerConsoleShell.tsx).
- **Resource context and shared editors exist.** Deployment and app inspectors use `resourceKind`/`resourceId`, carry selected release context, and compose the existing deployment/Engage editors through **Configure**. They already expose **Connect Engage app**, **Change deployment** and **Appearance & preview**. Reuse that context and composition, not another settings implementation. See [CustomerResourcesPage.tsx](../../../../../../../../Platfrom/ui/src/pages/CustomerResourcesPage.tsx) and [CustomerResourceSurface.tsx](../../../../../../../../Platfrom/ui/src/components/customer/CustomerResourceSurface.tsx).
- **Connection troubleshooting context exists.** Connections reads the selected deployment/release and `connectionId`, shows source synchronization state, and opens the existing integrations editor. Engage has a **Connect deployment** dialog with **Change connection** options. Neither is a request intake contract. See [CustomerKnowledgeConnectionsPage.tsx](../../../../../../../../Platfrom/ui/src/pages/CustomerKnowledgeConnectionsPage.tsx) and [CustomerEngageConnectionPanel.tsx](../../../../../../../../Platfrom/ui/src/components/customer/CustomerEngageConnectionPanel.tsx).
- **Customer identity and ownership exist.** Supabase issuer/subject links resolve an active `CUSTOMER_ADMIN`; active customer ownership is checked server-side. `/api/customer-self-service/deployments` supplies the owned portfolio. Reuse those boundaries rather than accepting a customer identity from form fields. See [CustomerSupabaseIdentityService.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/service/CustomerSupabaseIdentityService.java), [PlatformCustomerAccessService.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/service/PlatformCustomerAccessService.java) and [CustomerSelfServiceController.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/web/CustomerSelfServiceController.java).
- **Partner escalations are real but differently scoped.** `GET /api/partners/support/escalations`, `POST /api/partners/stores/{storeId}/escalations`, and escalation thread/reply APIs require partner roles and partner-account/store-assignment scope. They provide useful patterns, not a customer-wide endpoint. Do not create dummy store assignments or grant partner roles to submit a Console request. See [PartnerEnablementController.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/web/PartnerEnablementController.java) and [PartnerEnablementService.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/service/PartnerEnablementService.java).
- **Desk handles a different kind of support.** Its API queues deployment-target-bound conversations and separately authorized action reviews. Console's `/human-support` enables/opens that service through `/api/deployments/{deploymentId}/desk`. No general integration-support case intake is defined in the inspected Desk API. Do not synthesize a handover or action-review task for this feature. See [Desk API contract](../../../../../../../../product-services/loomai-desk-service/API_CONTRACT.md), [DeskActivationController.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/desk/DeskActivationController.java) and [010.48](010_48_LOOMAI_DESK_MANAGED_HUMAN_SUPPORT_AND_REVIEW_SERVICE_IMPLEMENTATION_PLAN.md).
- **Notifications are not a generic case-update contract.** The customer `/notifications` page reads customer-staging admission notices; the Platform notification controller handles admin-only deployment deletion operations. Partner SMTP methods cover access invitations/decisions, not integration requests. Do not describe these as a ready-made request notification bus. See [NotificationsPage.tsx](../../../../../../../../Platfrom/ui/src/pages/NotificationsPage.tsx), [PlatformNotificationController.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/PlatformNotificationController.java) and [SmtpPartnerNotificationGateway.java](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/gateway/SmtpPartnerNotificationGateway.java).

The missing product contract is a customer-owned integration-support request with intake, authorized list/detail, customer-visible updates, triage ownership and status changes. A Help link in the older [persona separation plan](../../PLATFORM_UI_PERSONA_SEPARATION_PLAN.md) is related intent, not evidence that this journey exists.

## 4. Proposed Placement

Use **Help** for the global entry and **Request integration support** for the action. Avoid the ambiguous label **Human support**, which already means conversation handover.

1. **Global Console entry:** add an accessible Help icon/menu in the customer header, including the empty-account/onboarding state. Offer **Request integration support** and **My requests**. Do not require a selected deployment, installed connection, verified release or active Desk service.
2. **Deployments:** add a secondary request action to the existing resource inspector at `/deployments?resourceKind=deployment&resourceId=<deploymentId>`. Carry the explicitly selected target/release when present; allow pre-release setup help.
3. **Engage apps:** add the same action to the app inspector at `/ai-workspaces?resourceKind=app&resourceId=<installationId>` and a small help action in **Connect deployment**. The existing **Appearance & preview**, installation and domain setup surfaces can link to the same form with a topic hint. Do not add help to the public Engage visitor tool rail.
4. **Connections:** add the action to selected connection details under `/integrations`, using its selected `deploymentId`, `releaseId` and `connectionId`. Preserve the existing **Manage connection** path for self-service fixes. Do not replace errors with a generic contact-support screen.
5. **Request tracking:** propose `/integration-support` as one compact list/detail destination, reached through Help and submission confirmation. This route is new, not currently implemented. Keep it separate from `/human-support` and preserve that route's current behavior.

All shortcuts open the same form and request store. Context is visible and removable before submission; opaque URL parameters are navigation hints, never authorization. Returning from help preserves the customer's editor and unsaved work.

## 5. Proposed MVP Journey

1. A signed-in customer administrator chooses the global or contextual action.
2. A compact dialog/panel asks **What do you need help with?** Optional topic and details expand only when useful. Show the selected resource, if any; allow an account-level request without one.
3. Offer a separate, unchecked **Share a diagnostic summary with LoomAI's integration team** option. Show the exact allowlisted summary before consent. Declining it still permits submission.
4. Submission revalidates customer and referenced-resource ownership. A successfully persisted request returns a reference, **Submitted** status and a link to **My requests**. It does not imply that a person has read it or that an email was delivered.
5. The integration team sees a small authorized queue, assigns an owner, reviews the submitted material and posts a customer-visible next step. Internal triage notes remain separate from customer updates.
6. The customer can read status/history and add a short reply in Console, including clarification requested by the team. A request is visible only inside its owning customer account; proposed MVP audience is that account's active customer administrators, subject to owner approval.
7. Resolution records what was advised or completed and links back to the relevant existing editor. Customer cancellation is explicit. Team work never silently publishes a draft, changes a binding or activates an app.

Proposed states: **Submitted**, **In review**, **Waiting for customer**, **Resolved**, **Cancelled**. The team owns review/waiting/resolution transitions; the customer can cancel an open request. A resolved request may be explicitly reopened into review with a reason, retaining the same history. No custom workflows or state designer.

If diagnostic reads fail, show the missing context and allow a text-only request. If request persistence fails, preserve the form and show failure without a fabricated reference. Safe retries use the same submission key to prevent duplicate requests. If a notification later fails, retain the submitted request and show delivery separately.

## 6. Proposed Fields And Safe Context

### Minimum request

- **Summary:** required short statement of the help needed; proposed limit 240 characters.
- **Topic:** optional choice such as setup, external system connection, Engage/embed/domains, or something not working. It is a customer-selected hint, not an inferred routing decision.
- **Details:** optional explanation, desired outcome and business impact in one field; proposed limit 4,000 characters. No mandatory technical questionnaire or reproduction package.
- **Resource context:** optional deployment, target/release, Engage installation and connection/source references relevant to the entry point. Show them before submit; only actual owned resources can be attached. Do not require a future integration to already have a connection ID.
- **Requester/customer:** derive from the authenticated active identity and customer scope. Display the account/requester for confirmation, not editable identity or tenant fields. In-Console replies are sufficient for MVP; do not require a phone number or a second registration.

The service would generate the request reference, timestamps, status, assigned team member, submission key and update history. These are not customer-editable authority fields. Length limits protect bounded storage/display; authorization and reference checks protect customer isolation. Optional details, topic or diagnostics must not become submission gates.

### Optional diagnostic summary

Build a small server-side allowlist projection from already authorized reads, not a browser state dump or an export of existing error objects. Proposed contents:

- Relevant opaque resource identifiers and safe resource type labels; include display names only when reviewed as appropriate for sharing.
- The selected environment/target/release and observed deployment/app/connection status enums, with observation time. Do not substitute a different release or assume that a source configuration status is live health.
- Safe verification outcome, classified error code and a known-safe correlation reference, where available. Mark unavailable/stale information explicitly; never collect additional provider data just to complete the summary.

Record the sharing choice, consent text/schema version, actor, time and exact shared snapshot. Newly collected diagnostics or a later expanded snapshot require fresh customer review/consent. Permission to share a summary is not permission for staff to inspect other data or execute changes.

Exclude credentials, tokens, cookies, private keys, authorization headers, connection strings, raw configuration, raw logs/stack traces, conversation transcripts, uploaded documents, business records, analytics/visit histories and arbitrary URLs/query strings. No attachments, screenshots or automatic session replay in MVP. A relevant public hostname may be supplied deliberately in the customer's details; do not automatically harvest it or private endpoints from configuration.

Free text is customer-supplied content, not guaranteed sanitized diagnostics. Warn against pasting secrets, render it safely, restrict access and provide a correction/removal path. A diagnostic allowlist must not be presented as proof that arbitrary text is secret-free. Requests are not an AI-training or analytics dataset by default.

## 7. Reuse And Minimum New Contract

**Reuse now in the design:** the current customer login and immutable identity mapping, active customer ownership checks, owned deployment/app/target reads, existing Console components and configuration editors. Link to the existing draft -> publish -> apply -> verify workflow when work needs a configuration change. Opening or submitting a request has no deployment side effect.

**Requires an approved implementation contract:** durable request creation; customer-scoped list/detail/replies/cancellation/reopen; staff triage assignment/status/customer updates; separate internal notes; bounded consented snapshots; retry identity and minimal access/update audit. Prefer a small module in the existing Platform application and persistence rather than a separate service/database per customer. Do not publish guessed endpoint names as existing APIs.

Partner escalation persistence/UI patterns can inform implementation, but their partner/store authorization cannot simply be reused for customer intake. Desk adoption is a later option only if an explicit case-intake, LoomAI-team membership and customer-visible update contract is designed and approved. Sharing Supabase identity does not supply that contract or grant Desk agents integration-team access.

For MVP, the in-Console request record and staff queue are authoritative; the named team must agree who checks it. Email or Activity & alerts integration is conditional on a real compatible customer-scoped event/delivery contract. If none exists, leave that channel out rather than repurposing admission/deletion notices or claiming an SMTP send method already accepts support updates. Any later email should contain only a request reference and authenticated Console link, not diagnostics or replies.

## 8. MVP And Later Depth

**MVP:** global Help, the three contextual entry types, one short form, optional previewed diagnostics, durable reference, customer list/detail with a simple reply history, small staff queue/assignment/status view, and explicit resolution/cancellation/reopen. Keep the existing Console styling and editors. No new AI dependency or automatic diagnostic agent is needed.

**Later, after usage evidence:** compatible email notifications; approved external helpdesk or Desk case adapter; curated setup-guidance links; scheduled integration sessions; richer explicitly consented evidence; team metrics based on request lifecycle data. Commercial packages and response-time commitments require their own product decision, not assumptions in the UI.

**Out of scope:** live visitor/dealership handover, action approval/review, a new ticketing/CRM/workforce system, omnichannel messaging, automatic remote access or production fixes, provider provisioning, a support-only runtime/framework capability, arbitrary attachment intake, broad governance redesign, or coupling to the separate visits-analytics work.

## 9. Ownership And Launch Decisions

- **Product owner:** approves naming, placement, MVP depth, account-wide administrator visibility, eligibility and customer expectations. No response-time promise until a team has accepted it.
- **Integration-team owner:** names queue coverage and triage responsibility, assigns requests, distinguishes guidance from separately agreed implementation work, and owns customer-visible resolution. This role is not automatically a platform administrator or a customer's Desk agent.
- **Platform backend owner:** owns the request contract, persistence, customer isolation, staff authorization, safe context projection and retention/removal behavior.
- **Console owner:** owns the shared intake/tracking UI, contextual shortcuts and preserving existing editor state.
- **Verification owner:** proves the customer/team journey and isolation before release. Runtime/framework changes are not expected for this scope.

The owner must approve the following before implementation:

1. Console-first MVP and the proposed Help/request labels and tracking destination.
2. Account-wide administrator access versus requester-only visibility, plus the exact existing staff permission or narrowly scoped permission needed for triage. Request access must not imply deployment administration or business-data access.
3. The proposed fields, consent copy, diagnostic allowlist, request retention period and deletion/redaction responsibility. No retention duration is silently inherited from Desk or analytics.
4. A named receiving team, queue coverage, response expectations and whether help is included or may require a separately accepted scope/fee. No automatic charge or paid-work commitment on submit.
5. The initial request system of record and any notification adapter. Recommended default is the small Platform module with Console tracking, not automatic Desk coupling.

Review approval of this document is distinct from authorization to implement, deploy or contact customers. No code changes are requested by the current task.

## 10. Acceptance For A Future Implementation

- [ ] An empty customer account can request integration help without a deployment, release, connection or Desk subscription/enablement.
- [ ] Deployment, Engage and Connections shortcuts open the same form with correct visible context; unavailable context is marked missing and can be removed, never replaced with another customer's/resource's context.
- [ ] Submitting, opening tracking and returning to configuration preserve unsaved editor work and do not publish/apply/activate, rebind apps, install Desk or alter human-support queues.
- [ ] One persisted request returns one durable reference across double-clicks/retries. Reopening Console shows its real current state/history; persistence and notification failures are distinguished.
- [ ] Two-customer tests deny cross-account create/list/detail/reply/snapshot access, including tampered resource references. Revoked/inactive customer membership cannot continue accessing requests.
- [ ] Only explicitly authorized team members can triage. Assignment to a request grants no runtime, credential, transcript, business-system or deployment-write authority. Internal notes cannot appear in customer responses.
- [ ] Diagnostic sharing defaults off. The displayed summary matches the persisted shared snapshot; declining or failing diagnostics still permits a text-only request. Tests verify prohibited fields and raw provider/error payloads are absent.
- [ ] The customer can see ownership/status/next steps, reply, cancel and explicitly reopen a resolved request; the team can review, request clarification and resolve with a visible outcome.
- [ ] The receiving team, retention/removal behavior and honest response expectations are agreed before launch. Any notification channel has an independently tested compatible contract and accurate delivery status.
- [ ] Desktop/mobile and keyboard journeys are verified, and existing Connections, Engage editors, `/human-support` and Desk conversation/review journeys remain unchanged.

## 11. Review Summary

**Recommended decision:** approve the product direction for a small Console help-request journey, with real tracking and team ownership. Keep contextual assistance close to the existing deployment, Engage and connection work. Approve the missing request/permission/data contract before building it; defer helpdesk/Desk integrations and richer diagnostics until they solve an observed need.

**Current outcome:** documentation only, ready for owner review. The review document is committed separately; no feature implementation, ticket submission, service activation, notification or support-feature release has been performed.

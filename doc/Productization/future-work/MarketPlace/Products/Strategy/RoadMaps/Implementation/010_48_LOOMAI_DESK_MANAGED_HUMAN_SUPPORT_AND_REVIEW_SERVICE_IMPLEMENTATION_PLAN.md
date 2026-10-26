# 010.48 LoomAI Desk Managed Human Support And Review Service Implementation Plan

- **Status:** Hosted dealership core qualified (2026-10-26, 12:55 UTC). Shared Desk, customer linking, owner SSO, agent assignments, full AI/human conversation journeys and genuine Action Review approve/reject/replay pass on staging and production. The public dealership demo uses the verified production release; all three legacy Inbox application/database pairs and active provisioning paths are retired. Full-plan closure remains open for native public email, branded DNS, domain-neutral/second-customer hosted proof and the active-human outage case; see the current execution record.
- **Created:** 2026-10-26
- **Proposed product name:** **LoomAI Desk**, alongside **LoomAI Engage** and the **LoomAI Platform**.
- **Primary outcome:** A customer enables human support on an existing deployment, opens its Desk workspace without another login, and assigns registered human agents to handle conversations or action reviews.
- **First integration:** The existing dealership deployment and Engage workspace, followed by a domain-neutral deployment.

## 1. Agreed Scope And Constraints

LoomAI Desk becomes our formal, LoomAI-managed human-support service. It serves multiple customers through one shared service and one Desk-owned PostgreSQL store per hosting environment. It has a separate operator portal, but uses the same Supabase identity system as the customer Console.

The first release includes:

- Customer Console authentication moved to Supabase, preserving existing customer ownership and permissions.
- A separate Desk portal with Supabase-backed sign-in and one-click access from an authenticated customer Console.
- Multiple isolated customers, deployments and registered human agents.
- **Deployment-scoped agent assignments**, independently scoped to staging or production targets.
- Live conversation handover through the existing conversation-channel integration contract.
- A separate action-review queue using the existing Human Review APIs and framework decision gateway.
- One-click enablement on compatible deployments, implemented through the existing configuration, publish, apply and verification lifecycle.
- Integration into the current dealership deployment without changing its public Engage address.
- Removal of the per-deployment Basic Inbox service and its dedicated databases after the replacement passes live verification.

The first release does **not** include:

- Application-specific agent authorization. An Engage app opens its deployment's Desk workspace and inherits that deployment's support team.
- AI Fabric framework changes/releases or a new handover/review engine. The original no-runtime-edit scope has the separately owner-authorized, narrowly bounded application-proposal adapter exception recorded in section 15; no other runtime redesign is included.
- A CRM, lead database, workforce-management platform, skills routing, omnichannel messaging or an enterprise notification system.
- A new database, service deployment or Supabase project for every customer, agent or deployment.
- Capacity/admission redesign, generalized draining, disaster-recovery orchestration or an exhaustive unrelated release matrix.
- Moving ordinary AI chat traffic through Desk or the Platform control plane.

The name is a product proposal, not a trademark or domain-availability claim. Proposed public addresses are `https://desk.loomai.pro` and a separate staging address; neither is claimed to exist yet.

## 2. Current Implementation And Reuse

### 2.1 Pre-implementation baseline

This subsection records the starting point, not the current supported product.
The standalone Basic Inbox module/provisioner and its live per-deployment
resources were removed during the qualified Desk cutover below.

Basic Inbox implements queue, claim, participant replies, explicit return to AI, close, callback retries and operator delivery status. Its README explicitly requires one trusted tenant/deployment pair per instance. Tenant/deployment configuration, provider credentials, callback destination and operator credentials are currently process-global.

Its current PostgreSQL-backed managed dependency allocates a separate database service. Sharing that binary across customers without changing its scope resolution and persistence is not safe.

The generic connector already sends:

```text
POST {providerBaseUrl}/api/conversation-channel/v1/dispatches
X-LoomAI-Inbox-Key: <binding-specific provider credential>
```

It already accepts signed provider events at:

```text
POST {connectorBaseUrl}/integrations/conversation-channel/events
X-LoomAI-Provider-Timestamp
X-LoomAI-Provider-Nonce
X-LoomAI-Provider-Signature
```

Desk must implement this existing wire contract. Existing protocol/header names do not need to change just because the product has a new name. The Marketplace contract remains `INTEGRATION / CONVERSATION_CHANNEL`, using `LOOMAI_CONVERSATION_CHANNEL_V1`; there is no new plugin type.

Human Review already exposes runtime APIs under `/api/reviews/v1` for listing tasks, reading a task and submitting `APPROVE` or `REJECT`. The current Platform proxy authenticates a customer administrator and signs private-runtime reviewer requests. Its authorization is not a ready-made agent-access contract; adapt the product-side authorization without impersonating that administrator.

### 2.2 Authentication is not already unified

The customer Console currently uses Platform password/session authentication. Its frontend calls the Platform login API, and multiple customer views explicitly require authentication mode `SESSION`.

There is a separate Supabase integration for Partner authentication. That does not mean customer Console users or Desk operators already use Supabase. Do not copy its email-fallback account lookup into the new customer identity mapping.

This plan deliberately moves **customer** Console authentication to Supabase. It does not require moving internal Platform administrator/operator login at the same time.

### 2.3 Source anchors

Relevant current files, relative to the repository root:

- `product-services/loomai-desk-service/README.md`
- `product-services/loomai-desk-service/src/main/java/com/loomai/desk/service/DeskDirectory.java`
- `product-services/loomai-desk-service/src/main/java/com/loomai/desk/service/DeskChannelService.java`
- `product-services/loomai-desk-service/src/main/java/com/loomai/desk/service/DeskReviewService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/desk/DeskActivationService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/desk/DeskBindingProvisioner.java`
- `Platfrom/backend/src/test/resources/dependency/retired-basic-inbox-spec.json` (historical decoding/test fixture, not an active provisioning path)
- `Platfrom/ui/src/auth/PlatformAuthProvider.tsx`
- `Platfrom/ui/src/pages/CustomerKnowledgeConnectionsPage.tsx`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/service/PlatformIdentityService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/security/PartnerSupabaseAuthenticationFilter.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHumanReviewService.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/HumanReviewController.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/review/HumanReviewService.java`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/ConversationChannelDispatchWorker.java`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/controller/ConversationChannelWebhookController.java`

The runtime files are contract references, **not planned edit targets**.

## 3. Customer And Agent Journeys

### 3.1 Enable Desk

1. A signed-in customer opens an owned deployment's **Human support** panel and selects its staging or production target.
2. The customer clicks **Enable LoomAI Desk**. The panel explains that this installs/configures the existing support integration and may apply an updated configuration.
3. Platform creates the customer's Desk workspace and a target-scoped channel binding, generates its credentials server-side, and installs the reviewed Desk integration.
4. Platform uses the existing draft -> publish -> apply -> verify services. It does not create another versioning mechanism, require a new Engage app, or request separate manual approval.
5. The panel shows progress, then **Enabled** only after the actual target passes verification. Repeating the request returns the same activation operation rather than another workspace or binding.
6. **Open LoomAI Desk** opens the deployment's authorized support workspace. No pasted operator key, separate Desk registration or second password is required.

One click means one authorized user operation with visible progress, not an instantaneous configuration flag. If the deployment has an unrelated unsaved draft, show that conflict and require the customer to review it; do not silently publish unrelated edits.

For a deployment that already has compatible handover enabled, reuse that configuration and replace only the provider binding through the normal release lifecycle. A missing runtime capability is a visible incompatibility, not permission to change runtime/framework code under this plan.

### 3.2 Customer account continuity

After the customer Console's Supabase cutover, the same authenticated person is recognized in Console and Desk. Desk creates its local profile/workspace membership automatically from trusted Platform ownership. This is a service profile, not a second set of credentials.

Console links identify the selected deployment/target as navigation context only. Desk independently authorizes access; changing a URL cannot reveal another customer's queue.

An Engage app's **Human support** link opens its bound deployment/target. Multiple apps using the same deployment share its support assignments in this release. An agent assigned there can handle that deployment's conversations regardless of which of its apps originated them. The UI must say this clearly.

### 3.3 Register and assign human agents

1. A human agent creates a Desk account using Supabase and verifies their email. Registration alone grants no customer or conversation access.
2. The customer selects **Assign agent** and identifies an already registered agent using an exact verified email or a shareable agent reference. Do not expose a searchable global directory.
3. Desk resolves the registered account to an immutable identity and the customer selects a deployment, target and role.
4. The assignment becomes active and the agent sees that workspace on their next authorized refresh.
5. Removing the assignment immediately stops new reads, claims, replies and review decisions for that scope. It does not delete conversation history or silently return an active human conversation to AI.

Do not create placeholder agent accounts, copy an owner's credentials, or grant customer Console administration just to let a person reply. One agent may have separately authorized assignments for several customers.

Keep roles small:

- **Customer owner:** manages their Desk workspace and assignments; may explicitly assign themselves support/review access.
- **Support agent:** views, claims, replies, returns a conversation to AI and closes conversations within assigned targets.
- **Action reviewer:** views and approves/rejects assigned deployment action-review tasks.

Support-agent and action-reviewer permissions are separate and may both be assigned to one person. Internal LoomAI support access is explicit and audited, not automatic access to all customer transcripts.

### 3.4 Handle a conversation

The visitor searches or asks a normal AI question in Engage, then requests a human through the existing AI action or UI button. A registered, assigned Desk agent sees the queue item, claims it, reads the approved transcript projection, replies, and explicitly returns it to AI or closes it.

The visitor receives human messages through the current deployment-owned chat/event path. Desk does not add a second browser-to-runtime contract. Any agent contact details requested from the visitor remain controlled by the existing handover intake configuration.

### 3.5 Review an action

A separately authorized reviewer opens **Action reviews** in the same Desk portal. They see the runtime's safe action proposal, inspect it, and approve or reject using the existing task version and decision ID contract.

This is not a conversation handover. Approving an action does not claim the chat or switch its response authority. Claiming/replying to a chat does not approve an action.

## 4. Architecture And Ownership

```mermaid
flowchart LR
    C[Customer Console] --> S[Supabase Auth]
    D[LoomAI Desk portal and backend] --> S
    A[Registered human agents] --> D
    C --> P[Platform configuration and ownership]
    P -->|Workspace, assignments and integration binding| D
    E[Engage visitor] --> R[Deployment runtime]
    R --> X[Deployment connector]
    X -->|Existing channel dispatch contract| D
    D -->|Existing signed callback contract| X
    X --> R
    D --> Q[Desk-owned shared PostgreSQL]
    R --> T[Deployment AI-state storage]
```

**Platform** owns customer/deployment ownership, configuration, plugin installation, secrets, versioning and integration enablement. It stores binding metadata and operational summaries, not a duplicate support transcript store.

**Supabase Auth** owns user credentials, authentication, verification and identity sessions. It does not decide which dealership/deployment a person may operate.

**Desk** owns operator profiles, registered-agent assignments, queues, claims, bounded transcript copies, delivery tracking and its portal. It checks its resource permissions on every request.

**The deployment runtime** remains authoritative for AI/human conversation control, canonical safe messages, review tasks, review policy and action continuation. Its AI-state database remains deployment-owned or customer-provided.

**The connector** retains dispatch/callback validation, retries and delivery to the runtime using the current integration contract.

**Customer systems** retain inventory, leads, bookings and other business effects. Desk must not become a dealership inventory or CRM database.

### 4.1 Hosting and database count

Deploy one Desk application service, including its portal and backend, per hosting environment. Start with a single instance; horizontal replicas are a later hosting choice, not a customer provisioning requirement.

Use one Desk-owned PostgreSQL store per environment with shared, scope-keyed tables. Do not create per-customer databases or schemas. Do not store Desk data inside each runtime database or inside Platform control-plane tables.

Supabase Auth can use the existing appropriate Supabase project. That choice does not require storing Desk application data in the Auth project's database. Confirm environment/client separation before reusing an existing project; never grant an Engage visitor operator rights merely because that visitor authenticates through the same project.

The resulting topology has deployment AI-state storage per deployment, plus a shared Desk store per environment. The extra Inbox PostgreSQL service per deployment disappears after cutover.

## 5. Multi Customer Persistence And Channel Isolation

Evolve the Basic Inbox implementation into `product-services/loomai-desk-service`, reusing its tested channel lifecycle, repository/outbox behavior and UI operations. Move/refactor rather than maintaining two permanent production implementations.

The minimum persistent model is:

- Desk workspace linked to a Platform customer.
- Operator profile with unique authentication issuer/subject links.
- Deployment/target channel binding with its trusted customer, tenant, deployment, connector callback destination and credential references.
- Agent assignment linking a registered person to a deployment/target and explicit support/review permissions.
- Conversation, message, dispatch receipt and callback outbox scoped to the trusted binding.
- Review references/decision receipts and audit records; runtime review tasks remain canonical.

Every queue query, detail lookup, mutation, stream/poll subscription, receipt lookup and callback retry must enforce the binding and customer scope. Include that scope in database keys, foreign keys and idempotency uniqueness where identifiers are currently assumed process-global. No tenant-per-process assumption may survive in a shared worker.

Resolve a dispatch's binding from its authenticated, binding-specific provider credential. Check its payload scope against the registered binding. Never select a customer from an untrusted `customerId`, an operator's query parameter or a browser attachment.

Callback destinations and signing credentials come from the registered binding, never from incoming transcript fields. Each binding has its own provider/signing credentials. The current one-process callback URL, operator list and encryption settings must become appropriately scoped service configuration/data. Do not substitute a shared all-customer operator key.

Retain the existing durable callback outbox and per-conversation ordering. Callback workers select each row's binding configuration. Reuse existing bounded retries and show delivery pending/failed honestly; do not build another recovery framework.

Staging and production have distinct bindings and queue labels, even for the same logical deployment. A production app link resolves its actual verified production assignment rather than the latest-created staging release.

Keep reasonable transcript retention and protected intake/secret storage. Deleting one workspace or disabling one binding must never drop the shared database or another customer's records.

## 6. Supabase Authentication And One Click Desk Access

### 6.1 Customer Console cutover

Replace customer Console password login with Supabase authentication. Preserve the Platform user/customer identifiers and existing resource authorization services. Add a server-owned link from the verified Supabase issuer/subject to that Platform customer identity.

For an existing local customer account, require a one-time verified linking/enrolment process: prove the existing account and the new Supabase identity, or use an explicitly authorized administrative enrolment. Do not match accounts solely by email and do not copy or share local password hashes. The initial migration is not a requirement to register again every time Desk is opened.

Update customer-only `SESSION` guards to accept the verified customer Supabase principal through one shared authorization contract. Do not simply remove the guards. Internal administrator/operator session login remains unchanged. Retire local password authentication for migrated customer accounts at cutover rather than shipping indefinite dual customer login paths.

All registered Supabase users are not customer administrators. Platform membership and active customer ownership still determine Console permissions. Desk-only agents can authenticate without acquiring any customer Console permissions.

### 6.2 Use the identity provider for cross app sign in

Use Supabase's supported OAuth/OIDC authorization-code flow with PKCE for Desk as a registered first-party application, with the Console providing the authorization UI against the same identity project. Prefer standard OAuth client/server libraries; do not build a custom identity provider or token-exchange protocol.

```text
Authenticated customer clicks Open LoomAI Desk
  -> Desk starts an authorization request with state and PKCE
  -> Supabase routes authorization to the Console authorization page
  -> Existing Console Supabase session identifies the same person
  -> Approved first-party authorization returns a one-use code to Desk
  -> Desk backend exchanges the code and creates its own protected session
  -> Desk authorizes membership and opens the requested deployment queue
```

The reviewed first-party Desk client should not require a second credential prompt or manual Desk signup. Configure the supported first-party authorization experience explicitly and prove it live, including a first-ever launch, rather than assuming same-project authentication shares browser storage. Any provider-required consent must be identified during qualification; repeated consent/login prompts do not satisfy the intended normal one-click journey.

Use exact client IDs and registered redirect URLs, PKCE/state validation, verified issuer/signature/expiry and supported audience/client checks. Store Desk's refresh credentials server-side and expose a secure, host-only HttpOnly session cookie to its portal. Do not put access/refresh tokens, runtime keys or permanent launch secrets in URLs; do not share a wildcard `.loomai.pro` authentication cookie across customer app subdomains.

Use `(issuer, subject)` as identity, not email or editable user metadata. Customer ownership and agent permissions are server-owned records. A Supabase email-confirmed agent can have zero assignments.

Document and test session refresh, local logout, expiry and membership revocation. Supabase sign-out does not instantly invalidate an already issued access token, so do not promise that signature verification alone provides immediate global logout. Check current Desk assignments before commands and stop a revoked agent's live subscriptions. Define a bounded identity-session expiry/revalidation policy using the supported Supabase mechanisms.

### 6.3 Authentication material and setup check

The private handover contains a Supabase project URL and public authentication configuration. This planning session has **not** verified live key validity, administrative access, OAuth Server enablement or registered OAuth clients. No private values belong in this plan, Marketplace manifests, audit bodies or Git.

Before authentication implementation is called ready, confirm:

- The intended active Supabase project and verified issuer/signing-key configuration.
- Existing Console/Engage authentication consumers and effects of any project-wide setting change.
- OAuth Server availability/enablement and administrative permission to register Console/Desk client configuration.
- Exact staging and production callback/authorization URLs and allowed redirect configuration.
- Public client configuration, server-only client credentials where applicable, and server-only administrative credentials only if enrolment requires them.
- Working verification/recovery email delivery for customer and agent registration.

Ask the owner only for a specific missing permission or configuration after checking the existing private material. Missing auth setup must not silently turn into a custom SSO system or an insecure session-sharing workaround.

Official references: [Supabase OAuth Server](https://supabase.com/docs/guides/auth/oauth-server), [OAuth configuration](https://supabase.com/docs/guides/auth/oauth-server/getting-started), [authorization-code and PKCE flow](https://supabase.com/docs/guides/auth/oauth-server/oauth-flows), and [session lifecycle](https://supabase.com/docs/guides/auth/sessions).

## 7. Platform And Marketplace Integration

Publish a reviewed **LoomAI Desk** Marketplace integration using the current conversation-channel contribution. Reuse the current HTTP adapter contract and existing behavior compatibility checks. Do not invent a new runtime adapter just to replace Basic Inbox branding.

Platform supplies safe defaults and server-generated binding credentials. The customer should not enter provider base URLs, callback signing secrets, operator API keys or a managed Inbox database profile. Existing advanced configuration remains available through shared editors where appropriate.

One-click enablement should coordinate these existing capabilities:

1. Authorize the actual customer, deployment and selected target.
2. Upsert the workspace and target binding through an authenticated service-to-service Desk administration API.
3. Install/configure the integration and the existing handover behavior, retaining the customer's current intake and AI/UI transition choices.
4. Publish/apply the resulting version through the normal deployment services and verify it.
5. Expose a safe Desk portal link and current status to the customer UI and Engage app inspector.

Use an idempotent activation record with progress and retry of that operation, not a new general workflow engine. An unsuccessful apply must not be shown as enabled or leave a second operator inbox advertised as active.

Update the dealership template's bootstrap defaults to Desk. Existing deployments are changed through a new configuration version, not by rewriting immutable template/release records or reimposing template defaults continuously.

The integration is generic. Dealership labels and branding may be configured in its workspace; inventory, Auto Trader logic and dealership business actions do not enter Desk's generic components. The same activation path must work on a compatible non-dealership deployment.

Disabling Desk stops new handovers after the corresponding configuration update. Existing active conversations require an explicit authorized return/close or deliberate retention of the binding until they finish. Do not silently transfer authority because a provider was disabled. Do not expand this into runtime draining or infrastructure retirement machinery.

## 8. Action Review In The Same Service

### 8.1 Reuse existing review authority

Add **Action reviews** beside **Conversations** in Desk. Continue to use the `HUMAN_REVIEW` execution extension and current action-proposal review policy. Do not send approvals as conversation-channel messages or treat a chat reply saying "approved" as a decision.

The supported current runtime contract is:

```text
GET  /api/reviews/v1/tasks
GET  /api/reviews/v1/tasks/{taskId}
POST /api/reviews/v1/tasks/{taskId}/decisions

{
  "decisionId": "<unique decision identifier>",
  "decision": "APPROVE",
  "expectedVersion": 3
}
```

`REJECT` is the other supported decision for the current policy. The existing task-creation API is `POST /api/reviews/v1/action-proposals/{receiptId}` with its existing trusted identity and idempotency requirements. Task creation remains part of the originating action workflow. Enabling Desk does not automatically turn every action into a review or create fake review tasks inside Desk.

For initial delivery, Desk refreshes the existing review inbox through a small Platform-side integration adapter that reuses private-runtime request signing and the existing review service transport. The adapter authorizes the actual registered reviewer and selected target; it must not call the current administrator-only endpoint as a shared administrator.

```text
Desk reviewer
  -> Desk verifies active deployment/target review assignment
  -> Authorized Platform integration adapter preserves that reviewer's identity
  -> Existing deployment-local /api/reviews/v1 contract
  -> Framework ReviewDecisionGateway
  -> Existing action continuation and customer-system authorization
```

This deliberately reuses the current low-volume review bridge. It does not put normal AI chat or human conversation messages through the control plane, and does not require distributing a Platform signing private key to Desk. Direct delegated review access can be considered later only if demonstrated traffic warrants it.

### 8.2 Permissions and outcomes

Keep `review:tasks:view` and `review:tasks:decide` distinct from channel operations. A support-only agent cannot approve. Carry the real reviewer subject to the runtime so its separation-of-duty, expiry, version and replay checks remain effective. Changing roles must not allow somebody to approve their own action proposal when the configured policy forbids it.

Show the exact returned outcome. `APPROVE` may advance execution through the framework's existing action gateway; it is not necessarily a harmless annotation or an unconditional guarantee that the business action succeeded. Rejection must not execute that action. Desk records the receipt and displays the actual continuation status rather than executing the business write itself.

Use the existing review semantics and target APIs only. If a proposed capability is not supported by those APIs, record it as a separate limitation, not an implicit runtime/framework work item. Correction, arbitrary document approval, answer moderation and multi-step approval design are deferred.

Enabling Desk conversation support does not automatically enable Human Review or grant reviewers. Review can be enabled separately using the existing configuration lifecycle. The first-release dealership qualification must demonstrate both capabilities, using existing compatible APIs and a bounded demo-system action proposal.

## 9. Portal And Console UI

Desk is a separate service UI, not an embedded operator screen in the customer Console. A person may be a customer owner, an agent, a reviewer, or more than one of these.

Keep the first portal compact:

- Header: current customer/workspace, deployment and clearly labeled environment.
- Navigation: **Conversations**, **Action reviews**, and **Agents & access** for authorized owners.
- Conversations: waiting/active/closed queue, transcript, current assignee, reply composer, explicit return-to-AI and close controls, delivery status.
- Action reviews: pending tasks, safe proposal details, separate approve/reject controls and the actual decision/execution receipt.
- Agents: registered-agent assignment, deployment/target selection, support/reviewer permissions and revoke.

The conversation queue contains transferred human-support conversations and their permitted transcript projection. It is not a promise to mirror every AI-only conversation into a second database. A complete AI conversation-history product is outside this plan.

Console keeps only product configuration/status, **Enable LoomAI Desk**, **Open LoomAI Desk**, and the safe assignment summary. Engage app inspectors link to the corresponding deployment workspace; they must not suggest independent app-level agent permissions.

Use existing product styling, light/dark themes, a desktop-first queue/detail layout and basic mobile navigation. Reuse tested UI patterns and safe content rendering. Do not create a design-system extraction or a new dashboard framework as a dependency.

Use bounded polling for Desk queue updates initially, reusing the Basic Inbox approach. The visitor continues using existing deployment event delivery. Advanced push notifications and workforce features remain in the enterprise integration roadmap rather than blocking this service.

## 10. Availability And Minimal Operational Boundaries

Desk is an optional integration. Its outage must not make an otherwise healthy deployment's ordinary AI chat, indexing or read actions unavailable. Handover can report provider unavailability/pending delivery through the existing contract.

An active human conversation must not silently revert to AI during a Desk outage. Use the existing configured, authorized transition rules and visible delivery states. Do not add a lexical or automatic semantic fallback to hide provider failure.

Use the existing PostgreSQL outbox, scoped idempotency, current callback retries, service health probes, standard backups, bounded retention and practical logs. Measure resource usage during the real two-customer journey. Do not build capacity admission, billing tiers, multi-region failover or a new recovery subsystem for this release.

The shared service introduces a shared human-support dependency. Accept and document that hosting trade-off; ordinary deployment AI remains self-contained. Review operations using the existing Platform adapter also depend on that adapter's availability and must show an honest failure if it is unavailable.

## 11. Phased Delivery And Verification

Each phase should be a focused implementation commit with its own verification evidence. Checked source/offline items are distinguished below from hosted exit gates; passing an offline test does not close a live journey.

### P1 Shared service foundation

- [x] Refactor Basic Inbox into the Desk service and include it in the appropriate build/image path.
- [x] Replace single-process tenant/binding/operator assumptions with shared, scoped persistence and registered bindings.
- [x] Reuse the current dispatch/callback contract, callback outbox, claim/reply/return/close behavior and secret handling.
- [x] Run focused PostgreSQL and HTTP integration tests with two customers and multiple targets, including colliding local identifiers and wrong-binding credentials.

**Exit:** One shared instance safely serves two independent deployment bindings without a runtime/framework edit.

### P2 Shared customer authentication and registered agents

- [x] Confirm private Supabase configuration and configure the required first-party OAuth clients/redirects.
- [x] Move customer Console sign-in to Supabase, preserving stable customer/resource ownership and internal staff login (deployed; actual existing-owner dual-proof UI linking passed).
- [x] Implement immutable subject linking, automatic owner Desk membership and standard first-party Console-to-Desk authentication (actual first owner launch passed on staging).
- [x] Add agent registration/verification, deployment/target assignment and independent support/review permissions (source and offline verification; real email delivery remains blocked below).
- [ ] Verify first-ever Desk launch, returning launch, expired/cancelled login, logout, revoked assignment, unassigned registered user and unchanged staff administration.

**Exit:** A real customer signs in once, opens Desk through one link, and a separately registered agent can access only assigned deployment targets.

### P3 One click activation and conversation portal

- [x] Publish/configure the reviewed Desk conversation-channel integration and its server-side binding administration.
- [x] Add Console enable/open/status controls and app links using existing editors and release services (live staging enable/apply/recheck/open passed).
- [x] Implement the separate Desk queue/detail/agents UI using the existing channel operations.
- [x] Switch the current dealership target to Desk using a normal new configuration version, keeping its deployment identity and Engage URL (both environments; existing app activated on production).
- [x] Verify AI search -> AI-requested handover -> agent claim/reply -> explicit return to AI -> UI-requested handover -> close, through actual public routes (staging and production; public embedded demo rechecked after legacy removal).

**Exit:** Current dealership visitors and assigned registered agents complete the real conversation journey on the shared service. No per-deployment Inbox is used for that qualified target.

### P4 Action review portal

- [x] Add the bounded Platform review integration adapter using actual Desk reviewer identity, existing request signing and target authorization (focused offline tests passed).
- [x] Configure Human Review through existing deployment settings; consume real runtime tasks rather than Desk-only fixtures (real staging and production tasks and decisions qualified).
- [x] Add pending-task detail, approve/reject and receipt views in Desk.
- [x] Prove a reviewer can approve one real demo proposal and reject another, a support-only agent cannot decide, and stale/replayed decisions do not duplicate execution (actual UI/API decisions and read-only business counts on both environments; permission checks through actual APIs).
- [x] Check configured separation of duty and that the returned business-action outcome is shown honestly (actual distinct application source and assigned reviewer; actual completed outcome, not a fabricated receipt).

**Exit:** Conversation support and action review both work in Desk with separate permissions. The framework remains published 0.8.41. The initial no-runtime-change constraint has the explicit owner-authorized, generic opt-in application-adapter exception recorded below; ordinary chat behavior is unchanged.

### P5 Hosted customer journey and removal of old Inbox

- [x] Run the full dealership journey with a customer owner and a different registered agent/reviewer on staging, then the current production deployment.
- [ ] Enable Desk on one compatible domain-neutral deployment through the same customer control, without dealership branches.
- [ ] Verify cross-customer, staging/production target and support/reviewer isolation through the actual APIs, not only hidden UI elements.
- [ ] Verify Desk interruption leaves ordinary AI chat healthy and does not silently transfer human authority.
- [x] Record actual service/database inventory and basic resource usage; show that adding a customer creates a binding, not another Inbox service/database (shared-directory PostgreSQL tests and actual two-target provisioning; a second hosted customer journey is not claimed).
- [x] Remove all remaining per-deployment Basic Inbox services/databases and active provisioning paths after the retirement checks below (three scoped pairs, containers and owned volumes verified absent; archived demo history retained privately).
- [ ] Update operator/customer guides, Marketplace/template defaults, cleanup ownership and a real customer/agent demonstration recording.

Guides, Desk-ready template defaults and cleanup ownership are updated. Actual
customer/agent browser recordings exist privately with the passing journey
reports; a redacted, shareable demonstration has not been delivered. The unchecked
isolation gate still needs a known second hosted customer, and the outage gate
still needs an active-human conversation case; existing offline and ordinary-AI
outage proofs are not substituted for those cases.

**Exit:** The formal support service is Desk, old per-deployment Inbox resources are gone, and no supported template/provisioner recreates them.

## 12. Cutover And Complete Retirement

After the shared service passes dealership conversation **and** review verification:

1. Inventory the actual old Inbox services/databases and their deployment bindings. Do not infer ownership from an application name alone.
2. Stop new handovers to old bindings through the normal configuration path. Complete or explicitly return/close active demo conversations using current APIs; record any retained support data before deletion.
3. Activate verified Desk configuration on the existing deployment/app. Refresh the Console link and remove browser-visible operator keys and old Inbox URLs.
4. Confirm no current runtime/connector release or active customer binding still uses an old Inbox endpoint. Verify Desk callbacks and review access again.
5. Remove the old per-deployment Inbox application and its **owned** dedicated database resources. Do not remove runtime AI-state storage, Platform PostgreSQL, the shared Desk store, customer systems or Supabase Auth.
6. Remove the Basic Inbox managed-dependency provisioning defaults/spec builders from active product paths and remove its obsolete standalone production build/deployment entry. Reuse/move useful tests rather than maintaining a second supported service.
7. Preserve immutable historical release/audit records as history, but do not preserve a functioning old product path as a compatibility requirement. Mark historical rollback targets that require retired resources as unavailable rather than claiming they remain operational.

The user has previously allowed fresh fictional dealership data. No automatic transcript/data migration is required for this demo cutover. Do not silently generalize that permission to unrelated customer support history: obtain a retention/export/deletion decision for any non-demo data discovered.

Before the retirement step, a failed canary may revert to the still-existing old binding using the normal configuration path. After retirement, use a previously verified Desk release or temporarily disable new handovers; do not automatically recreate a per-deployment Inbox/database.

## 13. Definition Of Done

- A customer can enable support on an owned compatible deployment through one Console operation, with actual publish/apply/verify progress.
- Console customer authentication and Desk use Supabase-backed identity; an authenticated owner opens Desk without a separate account/password.
- A human agent registers first, then receives deployment/target support or review access without becoming a Console administrator.
- Engage apps link to deployment-scoped support; no application-specific security is advertised.
- One shared Desk service/store serves multiple isolated customers in each hosting environment.
- The dealership's existing public Engage URL completes AI -> human -> AI journeys with real operator replies.
- The same portal handles real action-review tasks through the existing runtime decision APIs, with distinct reviewer permissions and real actor attribution.
- Existing visitor authentication, attachments, conversation continuity and ordinary AI queries still work.
- The framework and existing conversation-channel contract are unchanged. The narrowly owner-authorized opt-in runtime application-proposal adapter is separately versioned, tested and qualified; it does not silently change ordinary chat confirmation.
- Per-deployment Basic Inbox services, dedicated databases, active provisioners, operator-key UI and obsolete deployment instructions are removed.
- No business inventory/lead database, hidden LLM fallback, duplicate review engine or unrelated operational redesign was added.

## 14. Related Plans

- [010.34 Conversation handover and the original Basic Inbox](010_34_TEMPORARY_HUMAN_CONVERSATION_HANDOVER_AND_LIVE_AGENT_EXTENSION_PLAN.md)
- [010.42 Enterprise conversation-channel integrations](010_42_ENTERPRISE_CONVERSATION_CHANNEL_AND_OPERATOR_INTEGRATION_PRODUCTIZATION_PLAN.md)
- [010.44 Customer system ownership boundaries](010_44_CUSTOMER_SYSTEM_INTEGRATION_BOUNDARY_CORRECTION_PLAN.md)
- [010.45 Customer-focused resource console](010_45_CUSTOMER_RESOURCE_CONSOLE_PROGRESSIVE_UI_PLAN.md)
- [010.47 Template bootstrap and normal deployment upgrades](010_47_TEMPLATE_BOOTSTRAP_AND_NORMAL_DEPLOYMENT_UPGRADES_EXECUTION.md)

This plan replaces the single-deployment Basic Inbox hosting decision for the managed product. It retains the provider-neutral integration boundary from 010.34/010.42 and does not make enterprise workforce features prerequisites for the first Desk release.

## 15. Execution Record

### 2026-10-26 Shared Service And Portal

- Commit `ec20cb3fe3c572c35fb98ca8ee00cd08dc397514` publishes the shared service,
  generic portal, contract and image workflow. GitHub run `40260746672` passed.
  The pipeline requires real PostgreSQL and runs portal browser tests.
- Service verification: 11 tests passed, including five real PostgreSQL cases,
  protocol-level dispatch/callback tests, two-customer collision isolation,
  authenticated role checks, encrypted OAuth credentials and CSRF/PKCE behavior.
  Portal verification: 23 browser scenarios passed, with inspected light/dark
  and basic mobile screenshots. Fixtures are test-only, not production data.
- Staging Desk is HTTPS healthy at
  `https://loomai-desk.46.224.145.148.sslip.io/`; app
  `zyivu69wblt4alx2zevffghj`, shared PostgreSQL `e4fqe4817eaeoam1ys7ev5rv`.
  The native authorization redirect carries the exact callback, state, nonce and
  PKCE S256. A logged-in authorization-code exchange is not yet qualified.
- Production service container and shared database are healthy: app
  `i13lxkspuql9qkjzbf2eha4q`, database `gwp0no7b84h6egbh9hq6hr4y`.
  `desk.loomai.pro` DNS/certificate qualification remains pending; internal
  container health is not public HTTPS readiness.
- Platform's initial 13 Desk tests passed, including real PostgreSQL migration
  through V215. Customer auth passed 30 HTTP tests, 28 signature/authorization
  tests, 87 frontend checks and 11 offline browser scenarios. Integration review
  found a target-local binding gap: shared published versions must not contain
  staging Desk addresses or credentials. That correction is in progress before
  the control-plane rollout; no runtime/framework changes are involved.
- Two confidential Supabase OAuth clients, the central authorization page and
  exact callbacks/signup/recovery redirects were configured, preserving existing
  redirects and email-verification policy. Credentials stay in ignored private
  material; no service credential is published in this plan.
- A single coordinated native agent signup at 02:54 UTC failed with HTTP 500.
  The matching Supabase auth log identifies SMTP `525 5.7.1 Unauthorized IP
  address`. No identity was created, no automatic retry or admin-confirmation
  shortcut was used, and new signup-email delivery is not qualified. The owner
  action is tracked in **Required for production** alongside the DNS action.
  Earlier testing of an opaque masked password was not proof of an invalid SMTP
  password. No IP-security disabling or signing-key rotation was performed.
- All existing customer runtimes, Engage assignments and old Inbox services are
  retained. No shared Desk conversation/review cutover or retirement is claimed.
- Customer and agent usage guide:
  [LoomAI Desk guide](../../../../../../../../Final_Documentation/External_User_Guides/LOOMAI_DESK_CUSTOMER_AND_AGENT_GUIDE.md).

### 2026-10-26 Hosted Control Plane, Identity And Staging Cutover

- Platform backend commit `34fb995e5d7691877a86687af1242c24807e7364`
  is deployed on both hosts, with native schema V215 successful. GitHub core
  image run `40265720257` passed. Focused Platform tests: 170 affected tests,
  another 31 metadata/access regressions and the separately run auth slice passed.
- Desk commit `5b55f9e4ce1b6fdc6988332b6e9493d6737b838e` is healthy on both
  hosts; GitHub Desk run `40265781695` passed. Its review contract follows the
  published 0.8.41 `WAITING_FOR_REVIEW`, typed decision-failure and action-outcome
  records. Portal production-bundle browser checks: 36 passed. No invented
  `PENDING` review status or fabricated proposal fields remain.
- Production Desk is publicly HTTPS healthy at the temporary address
  `https://loomai-desk.46.225.162.106.sslip.io/`, using the SAME application and
  shared database listed above. The branded `desk.loomai.pro` DNS action remains
  open. Both exact production OAuth callbacks are registered; no TLS exception,
  wildcard authentication cookie or second service was introduced.
- The owner explicitly approved two synthetic admin-confirmed native Supabase
  accounts for testing. They use distinct credentials, ordinary native roles,
  and no application-permission metadata. This is not public-signup/email proof.
  Actual customer Console UI linking preserved `usr-dc2c2ed0`, customer
  `cus-9fd67f55`, deployment `dep-3c4dbe4f` and existing resource ownership.
  A distinct agent completed native OAuth and registration with zero bindings.
- Staging activation created `ver-28c18fd1` / `rel-c5e8d054` and target binding
  `desk-e389ad83fd75b19d9f697936086d1639` through the normal publish/apply path.
  Runtime and connector source binaries remain unchanged: framework 0.8.41,
  source `c2e886f804b473d5efc20d52beb8f15df7423228`.
- Initial verification honestly failed only on missing active document-source
  registrations. The retained runtime scopes those registrations to the release.
  The customer UI discovered, publicly approved, registered, indexed and reconciled
  the same three fictional S3 policies. Recheck `vrf-91efa90b` passed:
  **29 passed, 0 failed, 4 not-applicable skips**. No SQL registration rewrite,
  forced verification result or runtime/framework fix was used.
- A real first owner Desk launch exposed a duplicate native OAuth-details read
  during Console identity refresh. Commit `2d14f299ab65e13c407d1415cd067d61564a94db`
  deduplicates the complete one-use authorization exchange, not just consent.
  Seven auth unit tests, 12 browser scenarios and the UI production build passed;
  UI-only image run `40269725285` published and the two Console deployments
  completed. Actual Console -> Desk owner launch then passed without a second
  password. Owner initially had no Support/Reviewer permission, as intended.
- Through actual Desk UI, the owner assigned the separately registered agent
  Support and Reviewer access to staging only. Through the existing customer
  **Roll back assignment** control, the SAME Engage app was temporarily switched
  to the verified staging target for qualification. Its public URL remains
  `https://northfield-console-live.apps.loomai.pro/`.
- Private test evidence is under
  `/Users/mahmoudashraf/.codex/private/loomai-desk-01048/ui-proof/`.
  Do not publish browser storage, tokens, passwords or raw authorization URLs.

### Action Review Qualification: Discovered Application Gap

The original expectation that the unchanged conversational runtime could
produce the review source was incorrect. Published AI Fabric 0.8.41 already
supports governed write receipts and their review; no missing framework
primitive or framework release has been identified.

However, the deployed LoomAI runtime's ordinary chat calls `RAGOrchestrator`
and uses pending conversation-action confirmation, not durable
`ActionProposalReceipt` creation. The fixed deployment-knowledge specialist is
read-only. There is no exposed registered write-specialist workflow that
produces the required governed receipt in this dealership deployment.

`POST /api/reviews/v1/action-proposals/{receiptId}` consumes an existing,
unexpired, correctly owned `PROPOSED` receipt. It is not a proposal factory.
Enabling `HUMAN_REVIEW` or opening an empty review inbox cannot prove an actual
approve/reject lifecycle. Desk must not insert receipts/tasks into runtime SQL,
fabricate tasks, impersonate a source principal, or label ordinary chat
confirmation as human review.

Main has requested an owner scope decision through **Required for production**:
defer live Action Review qualification explicitly, or separately authorize the
small application integration connecting an existing registered write
specialist -> governed receipt -> existing review creation API. Conversation
and identity qualification continue. Runtime/framework source changes remain
excluded until that separate scope is agreed. Full P4 and the combined
retirement gate remain OPEN, not passed by the implemented UI/bridge alone.

### 2026-10-26 Live Conversation And Permission Evidence

- The actual visitor and separately assigned agent completed the full staging
  conversation flow. Two live inventory results were returned before the AI
  handover; Desk received the real dispatch and transcript. Agent claim,
  asynchronous reply visible to the visitor, visitor reply visible in Desk,
  explicit AI return, resumed stock/policy question, a second UI-button handover
  and explicit close all passed. No operator key was used in either browser.
- Evidence directory `conversation-staging-1792989794167` contains videos,
  actual response receipts and light/dark/basic-mobile screenshots. That runner
  deliberately ends FAILED at its separate review-inbox transport check, not
  because any of those conversation checks failed. The earlier runner
  `conversation-staging-1792989570102` looked for the wrong provider identifier;
  its synthetic waiting episode was explicitly claimed and closed in the next
  run, not hidden or blindly replayed.
- Actual API checks passed for owner-without-Support denial, support-without-
  owner denial, unknown binding/nonexistent staging copy of a production binding,
  Support-only review denial, Reviewer-only conversation denial, and immediate
  conversation/review denial using the SAME cookie after UI revocation. Explicit
  UI reassignment restored scope. Native revocation was not inferred from a
  hidden button or a refreshed token.
- That permission runner found an independent real logout race: clearing cached
  state while the old binding deep link is still present can restart SSO.
  The bounded UI correction retains a signing-out screen until navigation and
  returns to an explicit signed-out landing. An expired deep link also waits
  for an explicit sign-in. Portal checks now pass 38 scenarios and the build;
  hosted logout requalification is still pending the corrected service image.
- The review-inbox transport check found that Desk's Boot 4/Jackson 3 converter
  treated its Jackson 2 tree response as Java bean flags, rather than the actual
  JSON array. This is a Desk HTTP serialization defect, separate from the missing
  receipt producer. The bounded fix serializes validated, size-bounded JSON with
  an explicit JSON content type and preserves typed decision failures. Native
  MVC/HTTP converter tests and hosted recheck are required before claiming it
  fixed; an empty valid inbox still does not qualify an approval lifecycle.
- Existing customer UI production promotion submitted `rel-29f1fa74`, using
  the SAME `ver-28c18fd1`, unchanged runtime image, target-local Desk credentials
  and callback configuration. As of this record, normal cold replacement is
  provisioning; the Engage app remains on the verified staging release.

### 2026-10-26 Production Activation And Focused Corrections

- After normal customer UI registration/indexing of the three production S3
  policies, `rel-29f1fa74` reached `APPLIED_VERIFIED`. Recheck `vrf-4496e8a0`:
  **29 passed, 0 failed, 4 not-applicable skips**.
- Production owner Console -> Desk SSO passed. The SAME native Supabase agent
  registered a profile in the separate production Desk store, initially with
  no assignments, then received Support and Reviewer through the real owner UI.
- At 05:11 UTC the customer selected **Activate production**. The SAME consumer
  and installation now resolve `rel-29f1fa74` / `ver-28c18fd1`, binding revision 6.
  Public app URL remains `https://northfield-console-live.apps.loomai.pro/`.
  Activation did not copy vector records, alter the customer business backend,
  create another Engage app, or change runtime/framework binaries.
- Focused Desk correction commit `3b9909cd10989344b86f344b8dcd455a5ec15731`
  passed two real Boot HTTP/converter regression tests, 38 browser scenarios and
  the portal build. GitHub run `40272106154` passed its required PostgreSQL,
  backend, browser and image steps. Both existing Desk applications now run that
  immutable image; no control-plane or runtime rebuild was needed for this fix.
- The native tests check real HTTP JSON arrays, task/detail objects, typed 409
  failures, returned outcomes, sanitization, real reviewer forwarding and role
  denial. They are not a hosted business-action execution proof. Hosted final
  production conversations, corrected review transport and logout checks follow
  this activation; the separate receipt-producing integration remains gated.

### Legacy Inbox Inventory Before Retirement

Read-only native snapshot, 26 October 04:33 UTC:

- Customer staging old Inbox app `uf8e89g08fokzp5kk2fmeq27`, dedicated PG
  `q7gc5jrr1mzh5tlmpcppo0qw`. Both healthy; connector has switched to Desk.
  One returned synthetic conversation and three completed callbacks remain.
- Customer production old Inbox app `sscwmjlivwe6ag7buffll8vq`, dedicated PG
  `tyobr77as149sarjjn649r0s`. Still required by production `rel-ccf9fb9d`
  until its verified Desk cutover. Two returned conversations, six callbacks.
- Original Northfield demo Inbox app `i9mwzvct2ttrfabo6t2u0ebx` is stopped;
  PG `kmxefnvl9rajltyvxkmxc483` is healthy. Two WAITING conversations require
  explicit disposition before deletion. The public site still points to that
  stopped deployment `dep-f023c863` and its old staff inbox.
- Current entitled offers still reference historical Basic Inbox templates and
  `INBOX_OPERATOR`; 9 staging and 12 production dependency specs containing
  `BASIC_INBOX_KEYS_V1` remain active. New Desk-ready template revisions alone
  do not retire these executable paths. Publish new offers/deactivate old specs
  after qualification; preserve immutable history and ordinary deployment upgrades.
- Delete none of these three app/PG pairs yet. Runtime AI-state databases,
  customer business systems, Platform databases and the shared Desk stores are
  not retirement targets. Desk's reused internal channel classes and
  `X-LoomAI-Inbox-Key` are still the supported protocol, not obsolete resources.

### Final Hosted Checkpoint: 26 October 05:28 UTC

- Production visitor/agent runner `conversation-production-1792991540201`
  PASSED: grounded inventory search, AI-requested handover, agent claim,
  asynchronous human reply, visitor human reply, explicit AI return, a second
  UI-requested handover and close. The real review queue also returned HTTP 200
  with JSON `[]`, confirming the serialization correction, not an approval flow.
- Follow-up read-only inspection `visual-production-1792991882424` confirmed the
  actual committed provider states `RETURNED_TO_AI` and `CLOSED`; no synthetic
  episode remained WAITING/ACTIVE/pending. Desktop light/dark and 390px mobile
  screenshots were inspected. Dark-mode text/background computed colours pass
  after transitions settle; an earlier screenshot captured the transition and
  was not evidence of a persistent theme defect.
- Native access runner `access-staging-1792992099315` PASSED 11 checks: owner
  without Support, Support without owner, unknown binding, wrong environment,
  Support-only review read AND decision denial, Reviewer-only conversation
  denial, immediate same-cookie read denial after revocation, explicit
  reassignment, and actual CSRF-protected local logout ending unauthenticated.
  No protected task was created by the denied synthetic decision request.
- Returning owner SSO passed again on the corrected service image, without a
  second password (`owner-desk-staging-1792992003990`). One earlier post-rollout
  reauthorization attempt reached the honest cancelled-login page; its cause
  was not established. The fresh launch passed. Do not erase that observation
  or claim provider-wide authentication reliability from these bounded tests.
- Outage runner `outage-production-1792992392126` PASSED: after verifying no
  active human conversations, stopped ONLY the shared production Desk app,
  confirmed it out of service, opened the unchanged Engage app and obtained a
  grounded stock answer (HTTP 200). Restored the SAME Desk app/image, health UP.
  This proves ordinary AI availability during a Desk outage. Active human
  authority during an outage was not separately exercised live; existing
  protocol/durable-state tests cover that contract, not this hosted experiment.
- Point-in-time idle snapshot: staging Desk 309.6 MiB / 768 MiB, PG 42.61 MiB /
  512 MiB; production Desk 310.4 MiB / 768 MiB, PG 42.03 MiB / 512 MiB. Both app
  and shared database pairs healthy. These are observed samples, not capacity
  admission guarantees or a load benchmark.
- **Answer-quality follow-up, not a Desk lifecycle failure:** after the human
  returned the chat to AI, the request "We are back with AI. Summarize the
  delivery policy and the electric vehicles you found." ran successfully but
  answered that no relevant indexed context was available. Initial live stock
  search and release document-retrieval proof passed. This compound follow-up
  answer is not qualified as correct; no keyword workaround, forced search-all,
  hidden fallback, deployment-prompt patch or framework/runtime edit was added.

### Remaining Closure Gates

1. Owner scope decision for the missing originating governed-receipt workflow,
   then real distinct-reviewer approval/rejection, separation of duty and
   no-duplicate execution proof. The question is in **Required for production**.
2. Native signup/verification/recovery email delivery after the Brevo IP action.
   Approved admin-confirmed fixtures do not close public registration readiness.
3. Branded `desk.loomai.pro` DNS/certificate qualification. Temporary production
   HTTPS works; it is not a claim that the branded name resolves.
4. Compatible domain-neutral hosted activation and a known second-customer
   hosted isolation proof. Generic source and real PostgreSQL multi-scope tests
   passed; these additional hosted journeys have not been run.
5. Original public dealership site's stopped assignment/staff inbox cutover,
   explicit disposition of its two waiting conversations, new Desk-ready offer
   defaults, and full legacy provisioner/UI/module/app/database retirement AFTER
   the agreed combined qualification gate. No deletion has been performed.

The implementation is committed and working for the qualified conversation
journey. The overall plan is deliberately **not marked complete**.

### 2026-10-26 Owner-Authorized Narrow Review Integration

At 08:56 UTC the owner selected **Authorize the narrow dealership integration**
in Required for production (human message
`01a1776b-885e-7cb2-8ccb-b2280300bfa6`). This is a dated exception to the
original no-runtime-edit scope, not a claim that its missing producer existed.

- Add only the originating application's governed-proposal wiring needed for
  genuine Action Review. Reuse published AI Fabric **0.8.41** receipt, policy,
  reviewer authorization and execution APIs; no framework change or release.
- Keep dealership business execution in its external integration. Shared
  runtime/Desk code must remain domain-neutral, with explicit opt-in capability
  configuration and backend-owned authority, not client-selected identity.
- Prove actual approve/reject, source/reviewer separation, stale decision and
  replay behavior against the external action system before Inbox retirement.
- Do not fabricate SQL receipts, impersonate an administrator, replace normal
  chat confirmation silently, or expand into capacity/drain/recovery work.
- Branded DNS and Brevo email delivery remain separate external gates. This
  approval does not authorize an email retry or make those checks pass.

Execution: contract inspection in progress; no new runtime implementation or
rollout claimed yet. The first remaining gate above now has owner authorization;
its actual review qualification is still pending.

09:28 UTC source progress: the generic opt-in application-proposal endpoint,
real registered write specialist, same-source review submission, preserved
verified caller identity and safe outcome projector are implemented against
published 0.8.41. Versioned `humanReview.actionProposals` config supplies an
explicit allowed-action list. Ordinary chat confirmation is unchanged.
Eighteen focused runtime and 45 Platform tests pass. Independent contract review
caught provider-accounting scope and anonymous-classification issues; both were
corrected before rollout. Existing native confirmation templates supply actual
candidate facts in Desk's summary; no receipt decryption/private binding-format
dependency, duplicate proposal store, raw handler projection or receiver-auth
change remains. Image publication, live approve/reject and retirement are still
pending; these source checks do not close them.

10:00 UTC hosted progress: source `befdf1b78521c0ec72d037facd48214a4fbec0cc`
is published. Runtime CI `40299794581` and backend CI `40299794571` passed;
both control planes run the new backend, with unchanged Console and Desk images.
New runtime artifact `dsa-332b1a2c`, version `ver-8d1df30d`, enables only the
registered test-drive proposal action. No framework release was made.

The first staging release `rel-1a7ceaab` was healthy but failed its final
document-source gate. Its new image also includes the pre-existing committed
document ownership change `cde62f78c`, absent from the retained `c2e886f804`
image. That change rejects historical registrations without a physical storage
identity; opening an empty source list does not make them safe to adopt.

Using the owner's existing fresh-canary-data authorization, the operator used
normal binding removal/configuration APIs to create a fresh **staging-only**
owned binding for the SAME S3 bucket, prefix and read-only credentials. No S3
files, legacy source ownership, SQL registrations or vector records were
rewritten. The SAME deployment/version/image is being reapplied as
`rel-ca818c8e`, after which the normal customer registration/indexing flow must
qualify fresh sources. This is not a general migration claim or a storage
ownership-check bypass. Production runtime/app assignment is unchanged.

Real approval/rejection and legacy retirement remain pending. The private
runner now checks actual Desk UI decisions and read-only external business
counts, without extracting contact payloads or requiring them in Desk's safe
outcome projection. Native email delivery and branded DNS remain independent
open gates.

10:52 UTC continuation: staging `rel-ca818c8e` is now **APPLIED_VERIFIED**,
`vrf-28daa1db` has 29 PASS, 0 FAIL and 4 not-applicable checks. The actual
customer UI registered/indexed/reconciled the three policy files through the
fresh owned binding; no source objects or historical ownership were rewritten.

Real proposal checks found two distinct gates. Published framework 0.8.41
incorrectly treats substrings such as `example` as missing required values.
The owner explicitly directed **keep the framework, provide real values**;
this defect remains documented and unfixed, not hidden by input rewriting.
Qualification will use the already authorized owner-controlled contact mailbox.
Separately, the action correctly requires a trusted vehicle rather than a
name supplied in text. The narrow runtime integration now supports an optional,
deployment-configured authorized READ lookup. Its single canonical safe target
projection becomes internal specialist context, with caller authority preserved.
No domain terms, raw handler payloads, automatic broad search or hidden-target
bypass enter the generic adapter. Independent audit required checking the actual
governed invocation's EXECUTED status, not merely its handler result; fixed before
rollout with a focused rejection regression. The new lookup source is not yet
deployed. Genuine approve/reject and business-write counts remain the next gate.

11:13 UTC: source `0d4095a7c` passed both CI image workflows and both backends
are native-healthy. The v5 staging attempt `rel-ebdbddc4` exposed a runtime
startup mistake: the proposal specialist requested `dealer-vehicle` vector
retrieval although this workflow only invokes an authorized READ and proposes
a write. Framework startup validation correctly rejected its unregistered
specialist retrieval declaration. Corrected the runtime capability profile to
request no vector retrieval. The optional target projection label is still
checked, and action/resource authorization is unchanged. A focused regression
now validates that exact profile against a distinct registered vector catalogue;
all 24 focused runtime tests pass. This is a LoomAI integration correction, not
a framework change or a validation bypass. Production assignment is unchanged.

### 2026-10-26 Genuine Staging Review And Production Upgrade

Runtime-only correction `c0265e8d89c8d7ab0d6075bd2b6cd2db7886e6e6` passed
CI `40313425336`. Staging version `ver-6b73cd40`, artifact `dsa-32658297`,
release `rel-416f6ea9` is **APPLIED_VERIFIED**. Verification `vrf-af0c072b`
records 29 PASS, 0 FAIL and 4 not-applicable checks. Native runtime source and
health match that immutable artifact; the three registered S3 policies remain
indexed under the fresh owned staging binding.

Real staging tasks `review-task-234f9760-c6ec-4459-9bce-92097b1cbe32` and
`review-task-ea5ee443-38b5-4629-8eef-7d2df662a757` were created through the
private application endpoint, then approved/rejected in actual Desk UI by the
separately assigned reviewer. Read-only external receiver counts prove approval
created exactly one request and rejection none. Exact decision replay returns
the original result without another write; a new decision with the stale version
returns 409. The actual current READ authorized the candidate target; no SQL
receipt fixture or untrusted vehicle identifier bypass was used.

The published framework's substring validator remains unfixed under the owner's
explicit keep-framework decision. Owner-authorized real contact values are test
inputs, not a framework correction. The first harness count read failed before
any decision because it assumed a shared route rather than the actual compiled
per-action URL/header; that failed run is retained, not relabelled as a pass.

After staging proof, the artifact was promoted to the production channel.
The same customer-owned production S3 binding was renewed through normal APIs
using the same bucket, prefix and read-only keys; no source objects were written.
The first-time promotion screen correctly rejected an already production-bound
consumer, before an apply. The existing-deployment direct upgrade path was then
used: production `rel-c24e897e` is cold-applying the same v5/artifact. The app URL
and consumer identity remain unchanged. Production approval/rejection, fresh
policy registration and explicit verified assignment activation are still due.

Source cleanup has passed 378 focused backend tests, 12 customer UI scope
scenarios, 14 API contract checks and the staff-page browser regression. The
obsolete standalone Inbox module/build entry is removed in source; its historical
specification fixture moved into backend test resources. Old native Inbox apps
and databases are not yet removed. Their scoped configuration/SQL exports exist;
restore testing is not claimed.

The public embedded site has no sign-in host for protected actions. Its anonymous
journey may be qualified independently; protected workflows remain on the
existing standalone Engage app. Native public verification email delivery and
branded Desk DNS remain external open gates.

### 2026-10-26 Production Qualification And Legacy Retirement Complete

Current observation: **12:58 UTC**. This entry supersedes earlier in-progress
entries; those failures and checkpoints remain historical evidence.

- Both control-plane backends and Consoles, the public site and the same
  external dealership API run source
  `7bf91b1f419103bd7b474b0b986f4fe93b948ff0`. Native source, immutable image,
  platform and health readbacks passed. Core CI `40319293535` and dependency CI
  `40319293608` passed. Both Platform schemas are 216; no obsolete Inbox
  dependency specification remains active, and four Desk-ready template versions
  are available. The shared Desk remains `3b9909cd1`; both runtimes remain
  `c0265e8d8`, using published AI Fabric **0.8.41**.
- The existing deployment `dep-3c4dbe4f`, version `ver-6b73cd40` and runtime
  artifact `dsa-32658297` are verified on staging `rel-416f6ea9` and production
  `rel-c24e897e`. Checks `vrf-af0c072b` and `vrf-e038063f` each record 29 PASS,
  0 FAIL and 4 not applicable. The customer UI discovered, registered, publicly
  approved and indexed the three existing customer-owned S3 policies in each
  fresh binding; no source-object writes or new storage service were needed.
- Production review tasks were genuinely created by the authorized application
  endpoint and decided by a different registered reviewer in Desk. Actual
  external receipt counts prove approval wrote exactly once, rejection did not
  write, and identical decision replay did not repeat a write. A different
  decision against the stale version returns 409. Both staging and production
  review journey reports pass; this is not an empty-queue or SQL-fixture proof.
- Normal **Versions & updates -> Use verified release** assigned the same
  consumer/app to production release `rel-c24e897e`, revision 7. The existing
  `northfield-console-live.apps.loomai.pro` address did not change. Rejected
  first-time promotion/activation attempts occurred before assignment and remain
  failed harness-path evidence; no extra publishing mechanism or safety bypass
  was added to force them through.
- The public dealership site uses new active installation
  `awi_pub_53199a05a3e82c788c2dc84f8009925f` for that same consumer. The actual
  browser journey **passed again after all old Inbox resources were deleted**:
  AI inventory search, AI-requested handover, agent claim/reply, visitor reply,
  explicit return to AI, another AI query, button-requested handover and explicit
  close. Latest proof: `conversation-production-embedded-1793018725024`.
- The staff page exposes only the shared Desk link before independent dealership
  staff login. Final read-only desktop/native OAuth/basic-mobile proof
  `final-staff-readback-1793019285711` passed with zero old Inbox requests and zero
  page errors. The signed-out link correctly redirects automatically to Console
  authorization. Earlier harness assumptions about an intermediate link and a
  unique Sign in button failed; those reports remain failed, and only the
  read-only harness was corrected.
- All three old Inbox application/database pairs are verified absent through
  native API/container/owned-volume checks: staging
  `uf8e89g08fokzp5kk2fmeq27` / `q7gc5jrr1mzh5tlmpcppo0qw`, production
  `sscwmjlivwe6ag7buffll8vq` / `tyobr77as149sarjjn649r0s`, and original demo
  `i9mwzvct2ttrfabo6t2u0ebx` / `kmxefnvl9rajltyvxkmxc483`. Scoped SQL and
  configuration archives with hashes were retained privately before deletion;
  restore testing is not claimed. The original demo's two historical waiting
  episodes were archived, not falsely closed or migrated. The old demo's four
  installations and two consumers are disabled through normal APIs.
- Source retirement commits `50bc2861e` and `7bf91b1f4` remove the obsolete
  module/build path, provisioner, old operator UI/API and external demo Inbox
  client. Historical payload/hash/graph/audit records remain intact. V216 marks
  obsolete specifications inactive and old template versions deprecated; it does
  not rewrite immutable history or claim native deletion through historical
  graph status. The external business database and shared Desk, Platform,
  runtime AI-state and vector services were not deleted.
- Both connectors' desired unused `MANAGED_INBOX_*` environment records are
  removed. Existing processes retain dead variable strings until the next normal
  restart. The actual loaded, signed v5 routing matches immutable SHA-256
  `bd8d484fe287f3bf4449a5c9abf9a4e081ff1360a2efb51201ec974bda0c8f8c` and
  references none of them; no connector restart was performed for this cleanup.
- The customer guide now explicitly separates deployment/target-to-Desk binding
  from agent-role assignment, explains verified app assignment and target-local
  promotion, and records that upgrades reuse bindings without allocating another
  Inbox service/database.

**Remaining, not represented as passed:** native public signup/recovery email
delivery, branded `desk.loomai.pro` DNS/certificate, domain-neutral activation,
known second-customer hosted isolation and active-human authority during a Desk
outage. A redacted shareable recording has not been delivered; actual customer
and agent browser recordings are retained with the private passing reports.
The public embedded demo's protected sign-in host is also not implemented;
its qualified anonymous journey is distinct from standalone Engage sign-in.
The earlier compound inventory/policy answer-quality observation remains a
separate product-quality issue, not a failed handover or a silently fixed one.

Structured, secret-free evidence is recorded in
`verification-support/loomai-desk-01048/hosted-qualification-20261026.json`.
Raw proof, credentials, SQL exports and browser state remain outside Git.

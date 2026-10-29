# LoomAI Engage Standalone AI Workspace Delivery Guide

**Audience:** Invited customer administrators, Platform operators, implementation teams, and security reviewers
**Product:** LoomAI Engage
**Technical contract:** AI Workspace installation manifest v2

## What This Delivers

A standalone AI Workspace is a full-page LoomAI Engage application on an exact
HTTPS hostname. One shared frontend artifact can serve many customer apps, but
each hostname resolves to one active Platform installation and that
installation's currently assigned deployment. Conversation, retrieval, action,
attachment, and human-handover traffic goes directly to the assigned deployment
or its reviewed private adapter after bootstrap; it does not pass through a
shared Platform chat gateway.

Standalone delivery and embedded delivery use the same reviewed workspace,
experience-pack, connection, assignment, and runtime contracts. The delivery
choice does not create a second AI execution engine.

Customers can use **both** delivery channels against the same deployment. The
customer Console offers **Embed on my website** and **Standalone app**, with
**Add website embed** / **Add standalone app** shortcuts that preserve the
existing installation. Follow the [website and standalone setup guide](LOOMAI_ENGAGE_WEBSITE_AND_STANDALONE_SETUP_GUIDE.md)
for current customer setup and script installation.

## Choose The Supported Path

| Delivery | Interaction policy | Connection | Intended use |
| --- | --- | --- | --- |
| Standalone | Public | Anonymous direct runtime | Public-safe knowledge and bounded capabilities |
| Standalone | Login required | Authenticated direct runtime | Private knowledge or user-owned operations after entry sign-in |
| Standalone | Public with protected capabilities | Anonymous plus authenticated broker | Public discovery with sign-in only when a protected capability is selected |
| Embedded | Any supported policy | Same direct or private-adapter profiles | Existing customer website, including dealership installations |
| Embedded | Customer/private | Backend-mediated private runtime | Shopify and other integrations that must retain server-side assertions |

Shopify's private adapter remains supported. Do not replace it with anonymous
runtime access merely because the standalone shell also exists.

## Customer Self-Service Portal

Customer administrators sign in to `https://console.loomai.pro` with their own
Supabase-backed customer account. Console administration is separate from
visitor identity and permissions inside an Engage app, even if both use the
same identity provider. Shared staff API keys are not a customer sign-in mechanism.

1. In **My Deployments**, accept the reviewed offer, select its customer-staging
   target, verify placement and create the deployment configuration. Optional
   managed PostgreSQL holds AI conversation/execution state; it does not host
   dealership inventory, leads or another customer business system.
2. Supply the offer's approved external integrations and document storage.
   **Knowledge** uses your read-only S3-compatible storage and target-specific
   prefix. Use **Verify storage target placement** if placement evidence expires,
   then explicitly save the binding. Verification never automatically retries a
   rejected credential save. Mounted folders are for approved small/demo data,
   not a LoomAI-managed customer object-storage product.
3. Publish the reviewed configuration and deploy to customer staging. Open
   **Engage apps** to create the app from a starter or existing configuration,
   verify its address and connect it to the verified staging assignment. App
   branding and experience configuration use the existing installation editor.
4. Use **Manage deployment** to select the resource before opening Knowledge,
   **Inbox & Integrations**, or another deployment-scoped view. Navigation keeps
   the selected deployment in its URL; do not substitute another deployment or
   target release when investigating a failure.
5. Test the app's indexed knowledge, sign-in/protected capabilities, confirmed
   actions and human handover as applicable. The Basic Inbox is reached through
   **Inbox & Integrations** with separately authorized operator credentials.
   **Action Reviews** concerns governed action approval, not human-chat replies.
   It is available only when the deployment enables the corresponding review
   extension. An unavailable/error response must not be read as an empty inbox;
   conversation handover can be enabled independently.
6. Review and deploy the production candidate from **My Deployments**. Production
   uses its own integration/storage bindings and independently builds its index.
   Staging vector records and visitor conversations are not transferred.
7. Read the go-live checklist. Use **Refresh production readiness** to renew
   time-limited connection evidence before **Activate production**. Refresh does
   not deploy anything, switch an app, or extend its capacity allocation. Only
   explicit activation changes the app's assignment to the verified production
   release; the installation and app URL stay the same.

**Notifications** shows customer-staging expiry/retention/cleanup notices.
**Account & Team** uses the existing customer-scoped account administration.
Staff infrastructure routes are not part of this customer workspace. On mobile,
open the navigation menu; Escape closes it and restores keyboard focus.

Customer staging has a bounded lifetime. After promotion, explicitly choose an
allowed retention window or retirement and inspect its actual result. A blocked
drain or cleanup is not evidence that resources were reclaimed; request operator
assistance for a documented unsupported retirement case. UI layout rollback does
not roll back deployments, source bindings, indexes or app assignments.

The dealership pilot may use the explicitly labelled external provider
simulator. This does not claim production AutoTrader access or authorize the AI
to call an unrestricted public vehicle search. The shared-frontend operation
instructions below are for Platform operators, not required customer tasks.

## 1. Operate The Shared Frontend

In **Product Services**, register one service per Platform environment with:

- service kind: `STANDALONE_AI_WORKSPACE_SERVICE`;
- product family: `LOOMAI_ENGAGE`;
- deployment mode: `SHARED_PLATFORM_SERVICE`;
- tenant mode: `MULTI_TENANT_SHARED_FRONTEND`;
- health path: `/health`;
- source root: `workspace-apps/standalone-ai-workspace`;
- Dockerfile: `workspace-apps/standalone-ai-workspace/Dockerfile`; and
- the explicit staging or production Coolify target profile.

Reconcile the service and verify its health independently from every customer
installation. The service needs no business credential. Its only required
runtime setting is:

```text
PLATFORM_API_BASE_URL=https://api.loomai.pro
```

Use the environment's actual Platform API URL in staging. Configure the
Platform backend with:

```text
PLATFORM_AI_WORKSPACE_STANDALONE_BASE_DOMAIN=apps.loomai.pro
PLATFORM_AI_WORKSPACE_STANDALONE_SHELL_BASE_URL=https://engage.loomai.pro
```

The base domain creates Platform-provided app addresses. The shell URL is the
stable CNAME target shown for customer-owned domains. Neither value grants an
installation access to a deployment.

## 2. Create An AI App

In **AI Workspaces**, choose **Create AI App** and supply:

1. the customer and active consumer assignment;
2. a reviewed experience pack and immutable version;
3. `Standalone AI app` delivery and a unique DNS-safe app slug;
4. the connection profile;
5. the interaction policy and authentication profile;
6. optional exact customer hostnames; and
7. pack configuration, branding, tools, and feature flags.

Save the draft, resolve every readiness check, verify at least one app domain,
then activate. Unknown, unverified, disabled, or cross-installation hostnames
return the same unavailable response and do not load the installer.

## 3. Domain And TLS Activation

The Platform-provided address is:

```text
https://<app-slug>.<standalone-base-domain>
```

For a customer-owned hostname, create both records shown by Platform:

```text
TXT    _loomai-workspace.<customer-host> = loomai-workspace-verification=<token>
CNAME  <customer-host>                   = <standalone-shell-host>
```

Then select **Verify domain**. The Platform performs this ordered workflow:

1. prove the exact TXT ownership token;
2. add only that owned hostname to the shared Coolify frontend route set;
3. perform a direct, pinned HTTPS `/health` check without following redirects;
4. mark the domain `VERIFIED` only after TLS and health pass; and
5. include only verified domains in public host bootstrap.

Removing or rebinding a domain updates the Platform-owned route set while
preserving unrelated service domains. A route or certificate that is still
settling remains `ROUTING_PENDING`; it is not treated as active.

## 4. Authentication

`none@1.0.0` is valid only for the public interaction policy. The first
supported customer identity provider is `supabase@1.0.0`, configured with its
exact project origin, publishable key, and approved email/password or magic-link
methods. The publishable key is browser metadata; service-role keys and runtime
signing material must never enter installation configuration.

Selecting a reviewed sign-in profile projects its identity requirements, together
with exact app origins, into the editable deployment draft. Publication refreshes
that projection against the current behavior configuration. Publish and apply
the new version before activating the app; changing the app profile does not
hot-patch a running service or mutate a published version. Public-only apps do
not enable an identity broker.

The resulting Supabase runtime configuration is equivalent to:

```text
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_BROKER_ENABLED=true
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_PROVIDER=SUPABASE
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_ISSUER=https://<project>.supabase.co/auth/v1
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_JWK_SET_URI=https://<project>.supabase.co/auth/v1/.well-known/jwks.json
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_AUDIENCE=authenticated
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_ALLOWED_ORIGINS=https://<exact-app-host>
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_GRANTED_SCOPES=chat:query,chat:suggestions,chat:conversations,chat:messages,chat:handover:request,chat:handover:control,chat:handover:read
AI_FABRIC_RUNTIME_DEPLOYMENT_ID=<assigned-deployment-id>
AI_FABRIC_RUNTIME_CUSTOMER_ID=<owning-customer-id>
AI_FABRIC_RUNTIME_TENANT_ID=<owning-tenant-id>
```

The runtime validates issuer, JWK signature, audience, expiry, origin, and
identity before issuing a short-lived runtime token. The browser cannot choose
customer, tenant, deployment, or scopes; those remain deployment-owned. The
runtime fails startup when an enabled external identity broker lacks any of the
three ownership defaults. The example includes the four additional owner scopes
required when conversation handover and canonical message routes are enabled.
These are customer conversation permissions, not operator or administrator
permissions. Installation readiness rejects an incomplete explicit grant;
it never silently adds scopes to the broker configuration.

Managed projection adds only the supported conversation scopes required by the
enabled behavior. Existing manually enabled broker settings are preserved and
must already match the selected provider and required scopes. Conflicting
identity providers across apps sharing one deployment, manual configuration
drift, or malformed projection metadata block publication. Removing the last
sign-in app restores only the settings owned by projection; independent private
authentication and email-verification policy are retained.

A successful anonymous-to-authenticated sign-in starts a fresh,
identity-scoped conversation. Anonymous messages, attachments, confirmations,
handover ownership, and blocked requests are not transferred or replayed.

## 5. Runtime And Browser Boundaries

- Platform bootstrap is exact-origin and carries no provider secret.
- Workspace and experience-pack assets are immutable and integrity checked.
- The standalone shell denies framing, suppresses referrer disclosure, blocks
  MIME sniffing, and disables unrelated browser permissions by default.
- The installation manifest selects only registered profiles and reviewed packs.
- The browser talks directly to the assigned runtime for public modes.
- Private modes talk only to their reviewed adapter.
- Assignment revision invalidates stale client configuration but never migrates
  an active conversation into another deployment.
- Login, sign-out, and account switching use separate identity storage
  namespaces.

### Explicit Recovery Before Native Provisioning

When a release fails after local preparation but before creating any native
workload, do not cancel its reservation as unused or approve another provider
allowance. Local secret/graph preparation and lifetime allowance allocation
are real history even if no application has started.

The owner-only recovery contract uses the normal customer session:

```text
POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations/{reservationId}/prepared-failure-recovery-plan
{ "releaseId": "<failed-release>", "operationKey": "<unique-key>", "reason": "<reason>" }

POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations/{reservationId}/recover-prepared-failure
<the exact command returned by the plan>
```

Planning makes no configuration change. Execution requires current ownership,
entitlement, reservation revision and an unchanged server-derived proof. The
initial supported boundary is a terminal Coolify source-resolution failure
before native reconciliation, with no dependency create intent or initialized
provider allowance. Native-effect or ambiguous failures remain blocked for
their owning reconciliation/retirement path; an error message alone is never
recovery evidence.

Successful recovery retains the same reservation, graph, secrets, allowance
and failed-release history. It restores admission to `RESERVED` and fences the
failed release without retrying a provider request, detaching the app, deleting
resources, refunding credits or resetting counters. A new apply is a separate
explicit operation and must pass normal fresh authorization and prerequisites.
Reuse the same operation key only to recover its immutable receipt, not to
repeat an uncertain provider operation.

### Explicit Retry After Initial Connector Startup Failure

This is a different operation from pre-native recovery. When the initial
Coolify runtime and connector already exist but connector startup failed before
activation, an owner can review a retry of those existing owned applications:

```text
POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations/{reservationId}/failed-apply-retry-plan
{
  "releaseId": "<failed-release>",
  "operationKey": "<unique-key>",
  "reason": "<reason>",
  "acceptOwnedServiceRedeploy": true
}

POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations/{reservationId}/retry-failed-apply
<the exact command returned by the plan>
```

Use the normal `CUSTOMER_ADMIN` session. Planning verifies ownership and complete
terminal provider-job history without starting services. The plan identifies
`runtime` and `connector` as the affected roles. Review the plan and explicitly
accept their redeployment before submitting its exact fenced command. Existing
application and database identities remain; new jobs and container replacements
are authorized, so downtime is possible. This is not a connector-only restart.

The same release, reservation, version, dependency preparation and provider
allowance are retained. Failed-attempt progress is archived in the release and
an immutable receipt; credits and counters are not refunded, reset or replaced.
Normal preflight, prerequisites, provisioning and verification still run. A
queued receipt is not a verified or active deployment.

Changed ownership, a stale revision/proof, pending or unproven provider jobs,
initialized funding, or a different failure phase blocks this initial-boundary
retry. Repeated or ambiguous failures require their owning reconciliation path.
Do not automatically fall back between recovery types, cancel the reservation
as unused, or repeat an uncertain native operation.

### Retry A Reapply Before Runtime Replacement

The same owner-only retry-plan and execute endpoints also distinguish a failed
update of an existing deployment from an initial installation. This boundary
requires the old verified runtime generation to remain ACTIVE, its existing
runtime ownership to be intact, the connector to be stopped, and no replacement
runtime job or generation transition to have begun. Existing managed Inbox
resources and operator identities remain owned by the deployment.
The first supported update boundary uses a new candidate version, managed
PostgreSQL AI state and one PostgreSQL-backed managed Basic Inbox; it does not
claim recovery for every deployment topology or failure phase.

Review the returned boundary and exact fenced command before executing it.
Retrying retains the same candidate release, reservation, resource identities,
prior verified release, consumer assignment and provider allowance. The failed
attempt remains in immutable history. Connector configuration or stopping may
already have occurred, so this is not a no-effect recovery. The ordinary drain,
authority fence, single-writer runtime replacement, dependency initialization
and verification still run before the candidate can be activated.

This bounded retry does not authorize recovery after a runtime stop, adoption,
an ownership change, a pending provider job or an ambiguous generation. Do not
manually change admission state or create another allocation to bypass it.

### Resume Unfinished Setup After Provisioning

When runtime and connector provisioning, credential removal and allowance
activation have completed, a later dependency-admission failure is not a reason
to redeploy those services or initialize funding again. The normal owner can
review the supported continuation:

```text
POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations/{reservationId}/post-provision-resume-plan
{
  "releaseId": "<failed-release>",
  "operationKey": "<unique-key>",
  "reason": "<reason>",
  "acceptRemainingDependencyProvisioning": true
}

POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations/{reservationId}/resume-post-provisioning
<the exact command returned by the plan>
```

The v1 boundary requires a failure at dependency admission with no dependency
create intent or native identity, exact owned healthy runtime/connector/database
resources, complete terminal job history and the same active allowance. The
plan explicitly reports `runtimeRedeployRequired: false` and the remaining
phases: managed dependencies, source sync and post-apply verification. It may
create the reviewed AI-owned Inbox and its operational database. It does not
provision customer inventory, CRM or other business systems.

Every execution rechecks current placement, ownership, published configuration
and the existing runtime wallet. No timestamps are copied forward to disguise
stale evidence. Cached metadata reads remain separate from provider probes so
an enclosing transaction never holds its locks during those probes. Failure
history and financial counters remain intact; the receipt means queued, not
finished. Other phases, ambiguous effects, stale plans or changed authority are
rejected. Never automatically select this path after another recovery fails.

## 6. Release Verification

Before activating a customer app, prove:

1. shared shell `/health` is green and reports the intended environment;
2. two exact hostnames resolve to different installation IDs and assignments;
3. an unknown hostname fails closed;
4. another origin cannot fetch either installation manifest;
5. a disabled installation remains unavailable while shell health stays green;
6. anonymous, login-required, and mixed policies behave as configured;
7. successful login starts a fresh authenticated conversation with no replay;
8. protected runtime APIs reject modified-browser anonymous requests;
9. dealership tools, actions, sources, attachments, and handover regress cleanly;
10. the Shopify private-adapter path still works; and
11. shared frontend restart/recovery does not change runtime assignment or
    customer conversation ownership.

## 7. Recovery

- **App unavailable:** inspect installation status, exact host binding, domain
  status, assignment, pack assets, and connection readiness in that order.
- **Domain pending:** confirm both TXT and CNAME records, then retry verification
  after DNS/TLS propagation.
- **Sign-in succeeds but runtime access fails:** compare Supabase project origin,
  runtime issuer/JWK/audience, exact origin, and granted scopes.
- **Assignment changed:** start a fresh conversation after the installer reload;
  do not copy browser state to the new deployment.
- **Shared shell unavailable:** recover the managed frontend service. Do not
  redeploy customer runtimes merely to repair static frontend delivery.

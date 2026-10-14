# LoomAI Engage Standalone AI Workspace Delivery Guide

**Audience:** Platform operators, customer implementation teams, and security reviewers
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

For Supabase, the assigned deployment must enable its deployment-local broker:

```text
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_BROKER_ENABLED=true
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_PROVIDER=SUPABASE
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_ISSUER=https://<project>.supabase.co/auth/v1
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_JWK_SET_URI=https://<project>.supabase.co/auth/v1/.well-known/jwks.json
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_AUDIENCE=authenticated
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_ALLOWED_ORIGINS=https://<exact-app-host>
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_GRANTED_SCOPES=chat:query,chat:suggestions,chat:conversations
AI_FABRIC_RUNTIME_DEPLOYMENT_ID=<assigned-deployment-id>
AI_FABRIC_RUNTIME_CUSTOMER_ID=<owning-customer-id>
AI_FABRIC_RUNTIME_TENANT_ID=<owning-tenant-id>
```

The runtime validates issuer, JWK signature, audience, expiry, origin, and
identity before issuing a short-lived runtime token. The browser cannot choose
customer, tenant, deployment, or scopes; those remain deployment-owned. The
runtime fails startup when an enabled external identity broker lacks any of the
three ownership defaults.

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

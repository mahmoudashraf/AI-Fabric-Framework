# 010.31 Platform-Hosted AI Workspace Installation And Assigned Runtime Discovery Plan

- **Status:** Implemented, deployed, and live release-gated. Anonymous direct
  and private Bridge modes are live-proven. Authenticated broker activation is
  intentionally fail-closed until a real host identity broker is registered.
- **Created:** 2026-10-09
- **Scope:** Generic Platform-hosted AI Workspace installation, browser-safe
  connection discovery, all supported runtime auth/transport modes, and
  dealership first adoption
- **First adopter:** Northfield dealership demonstration using
  `public-runtime-anonymous`
- **Hosting decision:** Platform backend is the direct source; no CDN in the
  first release
- **Compatibility decision:** Greenfield replacement of the old
  dealership-backend descriptor flow. Preserve the generic workspace's three
  supported connection modes and the current Shopify private-bridge path.
- **Owner decision:** Implementation authorized on 2026-10-09. The earlier
  review hold in this plan is superseded by the owner's instruction to
  implement the plan fully.

### Implementation snapshot - 2026-10-09

- Platform persistence, customer-scoped lifecycle APIs, readiness, consumer
  binding guards, safe public manifests, exact-origin enforcement, bounded
  rate limiting, immutable asset delivery, health, metrics, and audit are
  implemented.
- The Platform image deterministically builds and packages the generic
  installer, generic workspace, dealership experience pack, SHA-256 content
  identities, and SHA-384 integrity values from the same source tree.
- The generic installer maps the manifest onto the existing anonymous direct,
  authenticated brokered-direct, and private backend-mediated widget modes. It
  does not introduce a fourth transport or domain branches.
- The dealership site now uses the one-script contract. Its obsolete runtime
  descriptor and fixed runtime-discovery configuration were removed.
- The Shopify Bridge implements the reviewed private-adapter bootstrap while
  retaining its existing server-side assignment and assertion boundary.
- The Platform Console includes a customer-scoped **AI Workspaces** lifecycle
  surface with typed configuration, readiness, activation, disablement, draft
  deletion, and installation snippet controls.
- Local release evidence is green: Platform backend `910/910`, focused
  AI Workspace tests, Platform UI build and browser smoke, generic widget and
  experience-pack package gates, dealership backend tests, Shopify Bridge
  tests, complete public-site static/browser verification, and a
  production-shaped Platform container started against PostgreSQL with
  readiness `UP`.
- A real authenticated host-identity broker is not currently registered. The
  generic broker contract and browser transport are implemented and tested,
  while activation remains fail-closed until a reviewed broker exists.

### Hosted release evidence - 2026-10-09

- The implementation landed in `cddee1e22055923136edf10fc732bee94fe19141`.
  Cross-origin public delivery corrections landed in `024bd6d`, `73ac2f3`, and
  `944f650`. Provider-record-to-page routing and scoped dealership detail reads
  landed in final release identity
  `bbe647d2706884cb100618048e4358bf9cd08370`.
- Platform image workflow `37881681357` completed successfully. Staging
  backend deployment `dq8gvgxlt4a17uq4bhxgb7xg` finished from `bbe647d...`.
  Production backend deployment `zmpt30drvhqsxmgjm2i8cr5p` finished with the
  immutable backend image pinned to the same `bbe647d...` identity. Production
  Platform UI and public site also run `bbe647d...`.
- The temporary production control-plane window was closed after verification.
  Firewall `10915120` rules hash is
  `71ee78c836904fbaffa01c6ff87ead74f6186cda554a9bbd5629e9ef7eca90bc`;
  firewall `10918233` rules hash is
  `8fd900686a4b064cbe647e8fcab437a79a44b6399109d6fa4fe91660e17df931`.
  Operator `38.126.94.35/32` has zero matches, direct local Coolify access
  returns HTTP `000`, and public Platform readiness remains HTTP `200`.
- Northfield customer `cus-28e3b6f9`, consumer
  `northfield-dealership-demo`, deployment `dep-f023c863`, version
  `ver-ea759582`, and release `rel-2d292866` are bound. Installation
  `awi_pub_456ec9f67744f4d7e231a939e835ac05` is `ACTIVE`; all ten readiness
  checks pass. Assignment revision is
  `sha256:cf185c16e4cad7ca0a595b22856538813f1460b19a78d3a0cc13f8c3ca27d48b`.
- The production site loads the Platform installer and manifest, then sends
  normal browser traffic directly to `dep-f023c863`. Browser evidence found no
  Platform chat proxy or retired runtime-descriptor request. Exact allowed
  origin succeeds, a foreign origin returns `403`, and an unchanged manifest
  returns `304`. Northfield remains a staging customer/runtime by design, so
  its production-site-safe manifest is served by the staging control plane;
  `api.loomai.pro` independently serves the same released installer/assets and
  is not falsely populated with a duplicate production customer assignment.
- Live workspace asset SHA-256 is
  `ae5a935872994f31569b2f373a96724d883f9a2dbe71505462603adf5a0fd732`;
  dealership pack SHA-256 is
  `befb528aa9347d01ab0bf9c6fc0d2dbce94adf18e290959937cf89a407e52429`.
  Their content-addressed filenames and manifest SHA-384 integrity values match
  the downloaded bytes. Production installer SHA-256 is
  `0988e864e82eb826d0a659baa549b8a7e50575b99583dd969db8f95cfa999b53`.
- The production-site dealership live gate passed one-session bootstrap,
  renewal, inventory, detail, comparison, grounded follow-up, page attachment,
  confirmed test-drive/callback receipts, inbox exact-once readback and cleanup,
  desktop/mobile presentation, and forbidden-proxy checks. Its strict quality
  gate passed all `12/12` scenarios in conversation
  `chat-e7aba388-df78-4b5a-9d62-7f278847f6bc` without an unintended write.
- The existing production Shopify Bridge is healthy. Its private adapter
  bootstrap returned `loomai-workspace-private-adapter-v1` with only
  browser-safe Bridge routes and ephemeral session state. A live query through
  deployment `dep-8c3e7259` returned five grounded sources/documents, and a
  separate safe canary executed read-only action `shopify_search_catalog`.
  No assignment credential, private assertion, or internal runtime contract
  was exposed to the browser.
- The authenticated broker handler is implemented and deterministically
  tested, but no real customer host-identity broker is registered. The profile
  remains disabled and cannot be advertised as live-proven until that external
  identity integration exists and passes section 19.5.

Related plans:

- [010.14 Consumer-Bound Runtime Assignment And Direct Private Auth Plan](010_14_CONSUMER_BOUND_RUNTIME_ASSIGNMENT_AND_DIRECT_PRIVATE_AUTH_PLAN.md)
- [010.26 Auto Trader Dealership First Release And Meeting Demo Plan](010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md)
- [010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan](010_27_AUTOTRADER_INTEGRATION_PLATFORM_READINESS_CHANGE_AND_EVIDENCE_PLAN.md)
- [010.29 Generic Max Mode Injectable Action UI And Dealership Experience Plan](010_29_GENERIC_MAX_MODE_INJECTABLE_ACTION_UI_AND_DEALERSHIP_EXPERIENCE_PLAN.md)

## 1. Purpose

Allow a customer website to install the LoomAI Workspace with one public script
and no customer-written workspace bootstrap or routing code. Anonymous public
experiences require no customer backend. Authenticated and private experiences
may use a reviewed token broker or trusted backend adapter because browser
identity and private runtime credentials must remain server-controlled.

The Platform must resolve a stable browser installation to a reviewed
connection contract associated with the consumer's currently assigned,
verified deployment. The generic workspace then uses exactly one of these
connection modes:

- `public-runtime-anonymous`: deployment-local anonymous bootstrap followed by
  direct browser-to-runtime traffic;
- `public-runtime-authenticated`: browser-safe identity exchange through a
  trusted token broker followed by direct browser-to-runtime traffic; or
- `backend-mediated-private-runtime`: browser traffic to a trusted adapter,
  which resolves the assigned runtime and signs private runtime requests.

The Platform remains the control plane and asset host. It must not become a
per-message proxy for chat, retrieval, or action traffic in any mode.

The intended customer installation is:

```html
<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="awi_pub_7d1f4c9a2e6b48d0">
</script>
```

This plan replaces the current dealership pattern in which site JavaScript
obtains `/api/public/runtime-descriptor` from the dealership backend and passes
the result to the experience pack. It does not remove the widget's explicit
programmatic initialization API or the authenticated/private connection modes
used by other products.

## 2. Product And Terminology Decision

The public product is **LoomAI Workspace**, not merely a chat widget.

Use these terms consistently:

| Term | Meaning |
| --- | --- |
| AI Workspace | The installed customer experience containing conversation, context, evidence, tools, rich action results, and Max Mode |
| AI Workspace installation | A Platform-owned, browser-visible installation configuration linked to a consumer |
| Consumer | The stable Platform routing identity bound to one current deployment/release |
| Deployment | The isolated LoomAI data plane that owns runtime behavior, knowledge, connectors, and governed actions |
| Experience pack | A reviewed domain presentation/configuration package loaded by the generic workspace |
| Connection mode | The generic auth/transport strategy used by an installation |
| Connection profile | A reviewed, versioned Platform registration that supplies the safe browser contract for one connection mode |
| Token broker | A trusted service that exchanges existing browser identity for a short-lived runtime token |
| Backend adapter | A trusted service, such as the Shopify Bridge, that keeps private runtime assignment and assertion material server-side |
| Chat/session | The conversational protocol inside the wider AI Workspace |
| Max Mode | The expanded workspace presentation mode |

Existing runtime route names such as `/api/chat/me/query` and
`/api/public/chat/session` remain correct because they describe the runtime
conversation protocol. Do not perform a cosmetic runtime endpoint migration as
part of this work.

## 3. Fixed Architectural Decisions

1. The Platform backend directly serves `install.js`, versioned workspace
   assets, experience-pack assets, and installation manifests.
2. No CDN is introduced in this release.
3. The customer page contains only a public opaque installation ID. It contains
   no Platform key, runtime key, provider credential, or signing secret.
4. A browser installation is a separate Platform primitive from a consumer.
   The installation references a consumer; it does not replace consumer
   assignment.
5. The existing authenticated consumer `runtime-assignment` endpoint remains a
   server-to-server contract and must not be made anonymous or embedded in a
   browser manifest.
6. A new browser-safe manifest endpoint exposes one typed `connection`
   contract: `public-runtime-anonymous`, `public-runtime-authenticated`, or
   `backend-mediated-private-runtime`.
7. Anonymous and authenticated public modes send normal runtime traffic
   directly to the assigned deployment. Private mode sends browser traffic to
   its trusted adapter, which calls the assigned deployment. Platform is not a
   per-message chat, retrieval, action, document, or provider proxy.
8. Anonymous bootstrap remains deployment-local, short-lived,
   origin-restricted, rate-limited, and low privilege. Authenticated public
   tokens are short-lived and issued through a trusted broker. Private runtime
   assertions never reach the browser.
9. Connection profiles are reviewed, typed, and versioned. Installation input
   cannot introduce arbitrary broker, adapter, runtime, or asset URLs.
10. Experience-pack code is generic infrastructure plus reviewed domain
   packages. Dealership names, actions, fields, and UI must not enter the
   generic loader or widget.
11. The dealership backend may continue to own ordinary dealership website
    data or dealer-owned business effects. It must not be required to configure
    or route the LoomAI Workspace.
12. Auto Trader credentials remain deployment-side. The browser and Platform
    installation manifest never receive them.
13. A consumer binding points to the explicitly assigned deployment/release,
    not whichever deployment happened to be created most recently.
14. The existing Shopify Bridge remains a supported
    `backend-mediated-private-runtime` adapter. This plan must not move Shopify
    private assertions or runtime assignment into the browser.

## 4. Target Runtime Flows

Every mode begins with the same installation and asset flow:

```text
Customer webpage
  -> GET Platform /api/public/ai-workspace/install.js
  -> GET Platform /api/public/ai-workspace/installations/{installationId}/manifest
       -> resolve installation
       -> resolve bound consumer
       -> resolve assigned verified deployment/release
       -> validate selected connection profile and website origin
       -> return safe connection + experience manifest
  -> GET Platform immutable generic workspace asset
  -> GET Platform immutable selected experience-pack asset
```

### 4.1 Public anonymous runtime

```text
Browser
  -> POST assigned deployment /api/public/chat/session
  -> deployment returns short-lived anonymous token + sessionId
  -> browser calls assigned deployment /api/chat/me/** directly
  -> deployment performs RAG and governed action orchestration
  -> deployment-local connector calls approved provider/customer service
```

### 4.2 Public authenticated runtime

```text
Browser with an existing application identity
  -> POST reviewed credential-broker endpoint
  -> broker validates the host identity and current assignment
  -> broker returns a short-lived, deployment-scoped runtime token
  -> browser calls assigned deployment /api/chat/me/** directly
  -> deployment validates the token and performs allowed orchestration
```

The workspace understands only the standard token-broker response contract.
OIDC, cookies, host sessions, Shopify customer identity, or another identity
mechanism stays behind the broker.

### 4.3 Backend-mediated private runtime

```text
Browser
  -> reviewed adapter endpoint, for example Shopify Bridge storefront API
  -> adapter resolves/caches consumer runtime assignment server-to-server
  -> adapter signs a short-lived private runtime assertion
  -> adapter calls assigned deployment private runtime routes
  -> adapter returns the safe response to the browser
```

The Platform participates in installation and connection discovery. It may be
called server-to-server by a broker or adapter to resolve assignment, but it is
not the data-plane proxy for every chat turn. A browser network trace must show
either direct runtime traffic for public modes or the selected trusted adapter
for private mode.

## 5. Ownership Boundaries

### 5.1 Platform owns

- installation identity and lifecycle;
- customer and consumer linkage;
- exact allowed website origins;
- current consumer binding resolution;
- experience-pack selection and reviewed configuration;
- connection-mode selection and reviewed connection-profile registration;
- Platform-hosted loader and immutable browser assets;
- safe manifest projection and revisioning;
- activation/readiness checks;
- audit of configuration, activation, disablement, and binding changes; and
- installation management UI and copyable script snippet.

### 5.2 Generic AI Workspace owns

- reading the installation ID from the script element;
- resolving and validating the manifest;
- loading the generic widget and selected experience pack;
- selecting the typed connection adapter from the manifest;
- anonymous runtime bootstrap and renewal;
- authenticated token-broker exchange and renewal;
- backend-mediated request transport without receiving private assertions;
- conversation/session state;
- messages, attachments, page context, tool groups, action presentations,
  clarification, confirmation, and error handling;
- keeping an active conversation pinned to its runtime; and
- refreshing assignment before a new session when the previous assignment is
  stale or unavailable.

### 5.3 Experience pack owns

- domain labels, starter prompts, tools, and action-result presentations;
- safe page-context discovery rules;
- domain-specific formatting and navigation;
- pack configuration validation; and
- optional host-facing methods such as `attachItem` and `sendMessage`.

The dealership pack is the first implementation. The loader must know only a
pack code, version, asset contract, and registration contract.

### 5.4 Deployment owns

- anonymous and authenticated public-token validation and scope;
- private assertion validation for trusted adapters;
- tenant/customer/deployment identity;
- retrieval and vector-space policy;
- conversation persistence;
- AI orchestration;
- action allowlists, validation, confirmation, and execution;
- indexed provider and dealership knowledge; and
- provider/customer connector credentials.

### 5.5 Customer application owns

- the containing webpage and its normal business functionality;
- any explicitly integrated authenticated customer identity and the trusted
  broker/adapter that projects it safely to LoomAI;
- customer-owned system-of-record effects not assigned to a provider; and
- optional richer browser context supplied through the public workspace API.

The anonymous baseline must operate when the customer does nothing except add
the installation script. Authenticated or private modes still use the same
one-script page integration, but activation requires a registered and ready
broker or adapter; removing that trusted server boundary would expose identity
or private runtime credentials.

## 6. Platform Data Model

Add an `ai_workspace_installations` table and matching entity/repository.

Required fields:

| Field | Rule |
| --- | --- |
| `id` | Internal immutable Platform ID, for example `awi-<uuid>` |
| `installation_id` | Public opaque high-entropy locator, globally unique and non-sequential |
| `customer_id` | Owning Platform customer |
| `consumer_entity_id` | Foreign key to the stable Platform consumer entity |
| `display_name` | Operator-facing name |
| `status` | `DRAFT`, `ACTIVE`, or `DISABLED` |
| `experience_pack_code` | Reviewed pack code, initially `dealership` |
| `experience_pack_version` | Exact packaged version; never `latest` |
| `connection_mode` | `PUBLIC_RUNTIME_ANONYMOUS`, `PUBLIC_RUNTIME_AUTHENTICATED`, or `BACKEND_MEDIATED_PRIVATE_RUNTIME` |
| `connection_profile_code` | Reviewed profile code compatible with the selected mode |
| `connection_profile_version` | Exact immutable profile version; never `latest` |
| `connection_configuration_json` | Schema-validated public settings for the selected profile; never credentials |
| `allowed_origins_json` | Canonical exact HTTPS origins |
| `configuration_json` | Schema-validated public experience configuration |
| `created_at`, `updated_at`, `activated_at`, `disabled_at` | Lifecycle evidence |
| `row_version` | Optimistic-lock version |

Rules:

- An installation belongs to exactly one customer and one consumer.
- The selected consumer must belong to the same customer.
- The public installation ID is a locator, not a credential.
- Exact HTTPS origins are required in staging and production. Loopback HTTP may
  be accepted only in local development/test profiles.
- Wildcard origins are not supported in the first release.
- Configuration must contain public presentation values only.
- Connection mode and profile must be compatible and must pass their
  mode-specific readiness contract before activation.
- Broker and adapter endpoints come from the reviewed profile registry, not
  from arbitrary installation input.
- Arbitrary JavaScript URLs, HTML, secrets, headers, provider endpoints, and
  runtime credentials are forbidden in configuration.
- Active installations are disabled, not physically deleted. Drafts with no
  history may be deleted by an authorized customer administrator.
- Every mutation records a Platform audit event.

Add a migration after the current latest Flyway migration. Include unique and
lookup indexes for `installation_id`, `customer_id`, `consumer_entity_id`, and
`status`.

## 7. Reviewed Asset And Connection Registries

### 7.1 Experience-pack registry

The first release needs a small, reviewed Platform asset registry. It must not
accept arbitrary uploaded browser code.

Each packaged entry declares:

```json
{
  "code": "dealership",
  "version": "1.0.0",
  "assetPath": "/api/public/ai-workspace/assets/dealership/1.0.0/dealership-experience.iife.js",
  "integrity": "sha384-...",
  "configurationSchemaVersion": "loomai-dealership-experience-config-v1",
  "workspaceCompatibility": "1",
  "enabled": true
}
```

The registry is generated from the exact packaged asset bytes during the
Platform build. Asset paths and integrity values must not come from installation
input.

This is a reviewed built-in-pack registry, not the deferred Marketplace
`UI_EXTENSION` lifecycle. Do not claim customer-uploaded UI packages or a
Marketplace experience-pack product until that separate lifecycle, review,
signing, entitlement, and rollback work exists.

### 7.2 Connection-profile registry

Add a reviewed built-in registry for browser connection behavior. A profile
selects an existing generic workspace transport adapter and supplies only
Platform-owned endpoint templates and schema metadata.

Conceptual entries:

```json
[
  {
    "code": "runtime-anonymous-direct",
    "version": "1.0.0",
    "mode": "public-runtime-anonymous",
    "handler": "direct-public-runtime",
    "endpointSource": "assigned-runtime",
    "configurationSchemaVersion": "loomai-runtime-anonymous-v1",
    "enabled": true
  },
  {
    "code": "runtime-authenticated-broker",
    "version": "1.0.0",
    "mode": "public-runtime-authenticated",
    "handler": "brokered-public-runtime",
    "endpointSource": "registered-broker",
    "configurationSchemaVersion": "loomai-runtime-token-broker-v1",
    "enabled": true
  },
  {
    "code": "shopify-storefront-bridge",
    "version": "1.0.0",
    "mode": "backend-mediated-private-runtime",
    "handler": "private-backend-adapter",
    "endpointSource": "registered-adapter",
    "configurationSchemaVersion": "loomai-shopify-bridge-adapter-v1",
    "enabled": true
  }
]
```

The registry must not execute profile-provided JavaScript or accept arbitrary
URLs from a customer form. Adding a new broker or adapter profile is a reviewed
Platform code/configuration change with contract, origin, security, and
rollback tests. Secrets remain in the broker, adapter, deployment, or Platform
secret store and never enter the installation row or manifest.

## 8. Public Browser Contract

### 8.1 Loader

```http
GET /api/public/ai-workspace/install.js
Content-Type: application/javascript
```

The stable loader must be small and domain-neutral. It derives the manifest URL
from its own script origin, reads `data-installation-id`, and never embeds an
environment-specific Platform URL.

### 8.2 Installation manifest

```http
GET /api/public/ai-workspace/installations/{installationId}/manifest
Origin: https://dealer.example
```

Proposed anonymous response:

```json
{
  "schemaVersion": "loomai-ai-workspace-installation-v1",
  "installationId": "awi_pub_7d1f4c9a2e6b48d0",
  "manifestRevision": "sha256:...",
  "assignmentRevision": "sha256:...",
  "generatedAt": "2026-10-09T12:00:00Z",
  "cacheTtlSeconds": 60,
  "connection": {
    "mode": "public-runtime-anonymous",
    "profileCode": "runtime-anonymous-direct",
    "profileVersion": "1.0.0",
    "runtimeBaseUrl": "https://dep-example.loomai.pro",
    "routes": {
      "queryUrl": "/api/chat/me/query",
      "suggestionsUrl": "/api/chat/me/suggestions",
      "authContextUrl": "/api/chat/me/auth-context",
      "shellConfigUrl": "/api/chat/me/shell-config",
      "conversationsUrl": "/api/chat/me/conversations",
      "conversationItemUrlTemplate": "/api/chat/me/conversations/{conversationId}"
    },
    "anonymousBootstrap": {
      "url": "/api/public/chat/session",
      "renewUrl": "/api/public/chat/session/renew",
      "authorizationHeader": "Authorization",
      "tokenScheme": "Bearer"
    }
  },
  "workspace": {
    "asset": {
      "version": "1.0.0",
      "url": "/api/public/ai-workspace/assets/workspace/1.0.0/max-mode-widget.iife.js",
      "integrity": "sha384-..."
    },
    "experiencePack": {
      "code": "dealership",
      "version": "1.0.0",
      "url": "/api/public/ai-workspace/assets/dealership/1.0.0/dealership-experience.iife.js",
      "integrity": "sha384-..."
    },
    "configuration": {
      "assistantLabel": "Northfield AI",
      "theme": {
        "primaryColor": "#123b35"
      },
      "capabilities": {
        "comparison": true,
        "testDrive": true,
        "callback": true
      }
    }
  }
}
```

The `connection` value is a discriminated union. An authenticated public
installation keeps the same runtime routes but replaces
`anonymousBootstrap` with a reviewed credential broker:

```json
{
  "connection": {
    "mode": "public-runtime-authenticated",
    "profileCode": "runtime-authenticated-broker",
    "profileVersion": "1.0.0",
    "runtimeBaseUrl": "https://dep-example.loomai.pro",
    "routes": {
      "queryUrl": "/api/chat/me/query",
      "suggestionsUrl": "/api/chat/me/suggestions",
      "authContextUrl": "/api/chat/me/auth-context",
      "shellConfigUrl": "/api/chat/me/shell-config",
      "conversationsUrl": "/api/chat/me/conversations",
      "conversationItemUrlTemplate": "/api/chat/me/conversations/{conversationId}"
    },
    "credentialBroker": {
      "url": "https://customer.example/loomai/runtime-token",
      "method": "POST",
      "credentials": "include",
      "responseSchemaVersion": "loomai-workspace-runtime-token-v1"
    }
  }
}
```

The broker validates the application's existing browser identity and returns a
short-lived token for the manifest's assigned deployment. The manifest never
contains the host session token, refresh token, long-lived runtime token, or a
way for the browser to select a different deployment.

A private installation exposes only a reviewed browser adapter contract:

```json
{
  "connection": {
    "mode": "backend-mediated-private-runtime",
    "profileCode": "shopify-storefront-bridge",
    "profileVersion": "1.0.0",
    "adapter": {
      "bootstrapUrl": "https://shopify-bridge.loomai.pro/api/storefront/example/workspace/bootstrap",
      "method": "POST",
      "credentials": "include",
      "responseSchemaVersion": "loomai-workspace-private-adapter-v1"
    }
  }
}
```

The adapter bootstrap returns only its safe browser routes and ephemeral
browser state. Runtime URL, assignment lookup credentials, private assertion
headers, issuer material, and signing secrets stay server-side. The adapter
resolves the current consumer assignment through the existing authenticated
Platform contract and calls that runtime on behalf of the browser.

The authenticated broker response is standardized so the widget can implement
`runtimeAuth.getBearerToken` without knowing the host identity system:

```json
{
  "schemaVersion": "loomai-workspace-runtime-token-v1",
  "accessToken": "<short-lived-runtime-token>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-09T12:05:00Z",
  "assignmentRevision": "sha256:..."
}
```

The private adapter bootstrap response is also standardized. It may return an
adapter base URL, profile-approved route paths, expiry, and an ephemeral browser
session identifier/header defined by that reviewed profile. It must not return
the assigned runtime URL or assertion material. The loader maps this response
to the existing backend-mediated widget configuration rather than teaching the
widget about Shopify or another domain.

The actual response must not contain:

- customer, tenant, or private consumer management records unless required by
  the runtime public contract;
- private assertion issuer/audience/header hints;
- long-lived authenticated tokens, host identity tokens, or cookies;
- trusted backend keys;
- provider, Auto Trader, connector, or embedding credentials;
- Coolify identifiers or internal URLs;
- arbitrary asset, broker, adapter, or runtime URLs; or
- the installation origin allowlist.

### 8.3 Connection invariants

- Exactly one connection union member is present.
- `mode`, profile code/version, and handler must agree.
- Direct public modes expose only the assigned verified runtime URL.
- Authenticated mode exposes only a registered broker URL and standard token
  response contract.
- Private mode exposes only a registered adapter URL and never exposes the
  private runtime contract.
- Route names remain generic chat/session protocol names; experience packs do
  not override them.
- The generic loader rejects unknown modes, profiles, handlers, routes, and
  response schema versions before mounting.

### 8.4 Revision and caching

Compute `manifestRevision` from canonical installation configuration, the
consumer assignment revision, connection profile/version and safe projection,
runtime or adapter routes, and packaged asset digests.

- `install.js`: short browser cache with revalidation.
- Manifest: `Cache-Control: public, max-age=60, must-revalidate`, strong ETag,
  and `Vary: Origin`.
- Versioned assets: one-year immutable browser cache.
- New page loads perform a conditional manifest request.
- No service worker and no CDN are introduced in the first release.

## 9. Installation And Connection Security Contract

Add a dedicated public security boundary for `/api/public/ai-workspace/**`.

### 9.1 Static assets

- Public and unauthenticated.
- Correct JavaScript content type and `X-Content-Type-Options: nosniff`.
- Cross-origin loading allowed because the bytes are public and immutable.
- Asset names are generated by the Platform build, not request-controlled file
  paths.
- Dynamic loader must reject an unknown manifest schema, pack registration,
  asset version, connection profile, handler, or integration mode.

### 9.2 Manifest

- Public but installation- and origin-scoped.
- Require an `Origin` header for browser manifest resolution.
- Match the normalized origin exactly against the active installation.
- Return no `Access-Control-Allow-Origin` header for rejected origins.
- Return `Access-Control-Allow-Origin` with the exact accepted origin and
  `Vary: Origin` for accepted requests.
- Allow no browser credentials or cookies on the Platform manifest request.
- Use generic not-found/unavailable responses that do not expose customer or
  assignment internals.
- Apply bounded per-installation/origin/IP request limiting without writing an
  audit row for every page load.

Origin controls reduce unauthorized embedding but are not user authentication.
Anonymous deployments must contain only public-safe knowledge and actions.
Authenticated and private modes must establish identity through their broker or
adapter, not from manifest origin checks. Non-browser callers can imitate
headers, so runtime scopes, action policy, provider scoping, quotas, and abuse
controls remain mandatory in every mode.

### 9.3 Common activation requirements

An installation cannot become active unless:

- its connection profile is enabled, compatible with its selected mode, and
  has an exact immutable version;
- its bound consumer resolves to an explicitly assigned verified release;
- the assigned release passes the deployment verification expected by the
  consumer;
- all browser-facing endpoints use HTTPS;
- every endpoint and response schema comes from a reviewed profile;
- all required generic workspace routes are present;
- every installation origin is exact and approved at each browser-facing
  boundary; and
- no connection projection exposes credentials or arbitrary endpoints.

### 9.4 Anonymous activation requirements

`public-runtime-anonymous` additionally requires that the assigned release:

- has signed public-token validation configured;
- has anonymous bootstrap and renewal enabled;
- includes every installation origin in runtime CORS and public-bootstrap
  origin allowlists;
- uses `allow-missing-origin=false`;
- has explicit accepted issuer and audience configuration; and
- has bounded anonymous scopes and bootstrap rate limits.

### 9.5 Authenticated public activation requirements

`public-runtime-authenticated` additionally requires:

- a registered token broker that validates the host application's existing
  user identity server-side;
- a short-lived token response containing deployment, consumer/customer,
  subject, scope, issuer, audience, and expiry claims expected by the runtime;
- no caller-selected runtime, tenant, customer, or scopes;
- broker CORS/credential/CSRF behavior appropriate to its host identity model;
- runtime CORS for every installation origin;
- signed public-token validation with the registered broker issuer/audience;
  and
- bounded token lifetime, scope, renewal, revocation, and abuse controls.

### 9.6 Backend-mediated private activation requirements

`backend-mediated-private-runtime` additionally requires:

- a registered trusted adapter with exact installation-origin controls;
- browser authentication, session, CSRF, rate-limit, and abuse behavior defined
  by that adapter profile;
- server-to-server authorization for consumer assignment lookup;
- private assertion issuer, audience, expiry, scope, and signing material
  configured only in the adapter and runtime;
- deterministic adapter routes for query, conversations, attachments, and
  other enabled workspace capabilities;
- a readiness check proving the adapter can resolve the current assignment and
  reach its verified runtime; and
- a manifest projection containing no private runtime URL or assertion
  material.

## 10. Platform Backend Changes

Implement a new `aiworkspace` package under the Platform backend containing:

- `AIWorkspaceInstallationEntity` and repository;
- create, update, activate, disable, delete-draft, and list models;
- `AIWorkspaceInstallationService` for customer-scoped lifecycle operations;
- `AIWorkspaceExperiencePackRegistry` for reviewed packaged assets;
- `AIWorkspaceConnectionProfileRegistry` for reviewed connection handlers and
  safe endpoint projections;
- `AIWorkspaceReadinessService` for deterministic activation/binding gates;
- `PublicAIWorkspaceManifestService` for the safe public projection;
- customer/admin controller; and
- permit-all public loader/asset/manifest controller or static resource
  configuration with a dedicated origin filter.

Admin API shape:

```text
GET    /api/platform/customers/{customerId}/ai-workspace-installations
POST   /api/platform/customers/{customerId}/ai-workspace-installations
GET    /api/platform/customers/{customerId}/ai-workspace-installations/{installationId}
PUT    /api/platform/customers/{customerId}/ai-workspace-installations/{installationId}
POST   /api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/activate
POST   /api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/disable
DELETE /api/platform/customers/{customerId}/ai-workspace-installations/{installationId}
GET    /api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/readiness
```

Public API shape:

```text
GET /api/public/ai-workspace/install.js
GET /api/public/ai-workspace/installations/{installationId}/manifest
GET /api/public/ai-workspace/assets/{packOrWorkspace}/{version}/{assetName}
```

Reuse `PlatformCustomerConsumerService.resolvePublicConsumer` internally so
active status, deployment binding, and verified release rules stay canonical.
Direct public projections may expose only the verified public runtime URL and
generic routes. Private projections expose only their reviewed adapter. Do not
return `PublicConsumerRuntimeAssignmentResponse` to the browser because that
DTO contains server-side integration hints not needed by the workspace.

### 10.1 Consumer binding guard

Add an AI Workspace readiness guard to consumer binding and trusted promotion.
When a consumer has active installations, a rebind must fail before mutation if
the target deployment/release does not satisfy every installation's selected
connection-mode, profile, runtime, adapter/broker, and origin contract.

Rules:

- Do not allow an active installation's consumer to become unbound.
- Disable the installation first for a deliberate shutdown/unbind.
- Validate production rollback targets by the same rules.
- For direct modes, validate the target public runtime and token contract.
- For private mode, validate the adapter's assignment/assertion readiness
  without exposing that information to the browser.
- Rebinding changes the manifest revision automatically; no installation row
  rewrite is needed.
- Do not silently retain the old runtime when the Platform binding points to a
  new invalid target.

## 11. Platform UI Changes

Add an **AI Workspaces** surface to the customer view.

Required capabilities:

- list installations with status, consumer, assigned deployment/release,
  experience pack, origins, and readiness;
- create an installation by selecting an existing customer consumer;
- choose an exact reviewed experience-pack version;
- choose one of the three connection modes and a compatible reviewed profile;
- configure exact website origins;
- configure safe pack-owned fields through typed controls rather than raw JSON;
- configure mode-specific public fields through typed controls without exposing
  secrets or accepting arbitrary endpoint URLs;
- show deterministic common and mode-specific activation blockers;
- activate, disable, and delete an unused draft;
- show assignment and manifest revisions;
- display and copy the exact one-script installation snippet;
- show that the installation ID is public and must not be treated as a secret;
- explain when anonymous needs no backend and when authenticated/private modes
  require a token broker or trusted adapter;
- show that provider and private runtime credentials stay server-side; and
- link to the assigned deployment and consumer binding history.

The UI must not expose private assignment credentials or permit arbitrary
script URLs, raw JavaScript, arbitrary HTML, request headers, or secrets.

## 12. Asset Build And Platform Hosting

The Platform image must contain assets built from the same source commit as the
backend image.

### 12.1 Generic installer

Add a small installer entry to `max-mode-widget`, built separately from the
React widget IIFE. It must:

1. find its owning script element;
2. read and validate `data-installation-id`;
3. derive the Platform base URL from `script.src`;
4. fetch the manifest with a bounded timeout and normal browser `Origin`;
5. validate schema, connection union, profile, and HTTPS endpoint requirements;
6. load exact integrity-addressed workspace and pack assets once;
7. require the selected pack to register the exact code/version;
8. mount the pack with the manifest and current page context;
9. emit generic lifecycle/error events without breaking the host page; and
10. reject duplicate/conflicting initialization deterministically.

Conceptual browser registry:

```ts
window.LoomAIWorkspace.registerExperiencePack({
  code: "dealership",
  version: "1.0.0",
  mount(context) { /* pack-owned setup */ }
})
```

The loader must never contain a dealership, Shopify, vehicle, product, or
provider branch. It may select only the generic connection handlers named by
the reviewed manifest contract. Shopify-specific behavior belongs to its
registered adapter profile and experience pack, not loader conditionals.

### 12.2 Reproducible image packaging

Add one deterministic asset assembly script that:

- runs `npm ci`, typecheck, and production builds for the generic widget and
  selected built-in packs;
- computes SHA-384 Subresource Integrity values and content hashes;
- writes versioned assets and a generated pack catalog under the Platform
  backend build output;
- fails on dirty/missing/unexpected output; and
- proves that every catalog entry points to a packaged file with the recorded
  digest.

Update both Platform backend Dockerfiles and the Platform image publication
workflow so changes under `max-mode-widget/**`, the installer, or built-in
experience packs rebuild the backend image. Do not depend on GitHub Pages or a
separately deployed widget artifact.

## 13. Generic Workspace And Pack Changes

### 13.1 Generic widget

The widget already defines these generic connection modes:

- `public-runtime-anonymous`;
- `public-runtime-authenticated`; and
- `backend-mediated-private-runtime`.

Preserve all three, including anonymous bootstrap/renewal, direct authenticated
runtime queries, private backend mediation, runtime-change invalidation, tools,
attachments, and action presentations. The Platform installer becomes another
generic initialization source; it must not replace or weaken explicit
programmatic initialization used by existing integrations.

Add only generic installation concerns:

- installation manifest types and validation;
- a discriminated connection union mapped to the existing generic widget
  transport/auth adapters;
- standard credential-broker and private-adapter bootstrap response contracts;
- installer lifecycle events;
- immutable asset loading support;
- installation/assignment revision diagnostics; and
- a safe new-session refresh hook.

The mapping must reuse the existing widget configuration boundary:

| Manifest mode | Existing generic widget configuration |
| --- | --- |
| `public-runtime-anonymous` | Assigned runtime `chatBaseUrl`, runtime routes, and `runtimeAuth.bootstrapAnonymous` or the existing direct bootstrap URLs |
| `public-runtime-authenticated` | Assigned runtime `chatBaseUrl`, runtime routes, and `runtimeAuth.getBearerToken` backed by the registered credential broker |
| `backend-mediated-private-runtime` | Adapter `chatBaseUrl`, adapter routes, browser-safe adapter session headers/cookies, and no private runtime token in the widget |

Do not add a fourth transport just for Platform installation. The loader adapts
the manifest to these existing generic contracts.

Do not put Platform assignment calls inside every query and do not let generic
widget components interpret dealership fields, Shopify storefront fields, or a
host application's identity mechanism.

### 13.2 Dealership experience pack

Refactor the pack to mount from the generic installation context rather than
fetching a dealership-backend descriptor.

Remove from the production pack contract:

- `backendBaseUrl` as the runtime-discovery authority;
- `runtimeDescriptorPath`;
- one-script `data-bootstrap-url`; and
- assumptions that a dealership backend publishes workspace configuration.

Keep or improve:

- dealership tool groups and tool rail;
- vehicle/search/comparison/action renderers;
- current-page attachment;
- anonymous conversation support;
- exact runtime routes supplied by the manifest;
- generic optional host methods; and
- safe image-host and detail-link configuration.

Page behavior for a script-only installation must use pack configuration and
safe browser evidence such as path rules, document title, selected visible
content, headings, and approved selectors. Browser-derived IDs are untrusted
hints. Any protected stock/deal/action target must be resolved and revalidated
by the deployment/connector before execution.

## 14. Northfield Dealership Cutover

Use the dealership demo as the first hosted canary.

1. Create an active customer consumer for the Northfield deployment and bind it
   to the verified dealership release.
2. Ensure the deployment's runtime CORS and anonymous-bootstrap origin lists
   include the exact LoomAI dealership demo website origin.
3. Create a dealership AI Workspace installation linked to that consumer.
4. Configure the dealership pack, branding, tools, presentations, page rules,
   image hosts, and public capabilities in the installation.
5. Add only the Platform-hosted `install.js` script to the demo site.
6. Remove site-side calls that manually mount the pack with a backend URL.
7. Remove the dealership backend `/api/public/runtime-descriptor` endpoint and
   its now-unused fixed runtime URL/path configuration.
8. Keep dealership website inventory APIs and any explicitly required
   dealer-owned business endpoints independent from workspace discovery.
9. Verify the workspace loads, searches, retrieves, compares, attaches pages,
   renders actions, clarifies, confirms, and executes permitted demo actions.
10. Prove from browser traffic that Platform is used for script/assets/manifest
    only and the assigned deployment receives all runtime chat traffic.

No temporary dual production path is required. Coordinate the Platform,
installation, and demo-site deployment as one cutover and accept bounded demo
downtime if necessary.

## 15. Shopify And Authenticated-Path Compatibility

### 15.1 Shopify private path

Shopify remains `backend-mediated-private-runtime`:

```text
Shopify storefront
  -> Platform-hosted workspace assets/manifest or existing theme asset
  -> LoomAI Shopify Bridge storefront endpoint
  -> authenticated Platform consumer runtime-assignment lookup
  -> private assertion signed by the Bridge
  -> currently assigned runtime
```

The existing Bridge remains the authority for store mapping, storefront abuse
controls, assignment caching, private assertion signing, and runtime calls. A
future Shopify installation may select the reviewed
`shopify-storefront-bridge` connection profile and Shopify experience pack, but
the browser must still see only the Bridge adapter contract.

Northfield cutover does not require an immediate Shopify asset migration. It
does require regression evidence that the current Shopify private path still
works and that the new generic installer/manifest can represent the same path
without changing its security boundary.

### 15.2 Authenticated public path

Authenticated applications may use the one-script installer when they have a
registered token broker. The host keeps its own login/session design. The
workspace calls the broker's standard endpoint, receives a short-lived token,
and uses the assigned public runtime directly. No host-specific identity logic
belongs in the loader, widget, or experience pack.

An installation cannot claim authenticated readiness until an approved staging
broker has proved subject, tenant/customer, deployment, scope, expiry,
revocation, origin, and runtime-token validation end to end.

## 16. Assignment Refresh And Conversation Semantics

An assignment change must not silently move an existing conversation to a
different deployment because conversation state belongs to the runtime that
created it.

- A new page load conditionally resolves the current manifest.
- A new conversation may revalidate the manifest before anonymous bootstrap,
  authenticated token exchange, or private adapter bootstrap.
- An active conversation stays pinned to its original runtime/session.
- Anonymous/authenticated token renewal uses the same runtime. Private adapter
  renewal stays pinned to the adapter's resolved assignment for that session.
- A bootstrap or broker/adapter failure before conversation creation may
  refresh the manifest once and retry against the newly assigned connection.
- A runtime `401`, `403`, `404`, or transport failure during an active
  conversation invalidates that session; the UI offers to start a new session
  after resolving the latest assignment.
- Do not replay private conversation history automatically into another
  deployment.
- Persisted browser session keys must include installation ID, connection mode,
  connection/runtime identity, and session subject/identity so two
  installations, users, adapters, or deployments cannot collide.

For private mode, the adapter owns assignment-cache invalidation and must not
move an active conversation when its Platform consumer is rebound. New sessions
resolve the new assignment. This is the same user-visible rule as direct modes,
enforced at the trusted adapter boundary.

## 17. Connection Mode And Identity Boundaries

| Mode | Browser calls | Identity source | Suitable use |
| --- | --- | --- | --- |
| `public-runtime-anonymous` | Assigned runtime directly | Short-lived anonymous deployment token | Public discovery, approved public knowledge, and bounded low-risk actions |
| `public-runtime-authenticated` | Broker, then assigned runtime directly | Existing app identity exchanged for a short-lived runtime token | Customer accounts, user-scoped knowledge/actions, and direct deployment traffic |
| `backend-mediated-private-runtime` | Trusted adapter/bridge | Adapter-owned user/store identity plus server-signed private assertion | Shopify, protected server integrations, and flows where runtime details must remain private |

### 17.1 Anonymous boundary

Anonymous Workspace is suitable for:

- public product/vehicle discovery;
- public approved document knowledge;
- comparisons and grounded explanations;
- low-risk public requests with explicit confirmation and abuse controls; and
- customer-supplied contact details when the action contract, consent, privacy
  notice, retention, and destination are explicitly approved.

It is not sufficient for:

- private customer accounts;
- existing order/deal/account history;
- user-specific entitlements;
- protected staff operations;
- actions requiring durable identity proof; or
- treating browser page values as trusted resource ownership.

Those flows require a separate authenticated public-runtime token exchange or
trusted backend integration. Do not weaken anonymous scopes to simulate user
authentication.

### 17.2 Authenticated and private boundaries

- Public authenticated mode does not make browser identity trustworthy by
  itself; only the broker's signed, bounded runtime token is authoritative.
- Private mode does not expose private assertions, assignment credentials, or
  internal runtime endpoints to the browser.
- A trusted broker/adapter is mandatory where the flow depends on durable user
  identity, protected resources, or private runtime credentials.
- "One script" means no customer-authored workspace bootstrap/routing code. It
  does not mean removing the trusted server boundary needed for secure private
  or authenticated access.
- Experience packs may request capabilities but cannot elevate connection-mode
  scopes or select a different consumer/deployment.

## 18. Observability And Operations

Expose bounded metrics and structured logs for:

- loader and asset availability;
- manifest requests by outcome, without high-cardinality raw installation IDs;
- origin rejection;
- disabled/missing installation;
- consumer/deployment/release resolution failure;
- connection mode/profile and mode-specific readiness blockers;
- broker/adapter bootstrap outcome using bounded labels and no user tokens;
- manifest generation latency;
- manifest revision and ETag behavior; and
- packaged asset/catalog digest mismatch.

Audit durable control-plane events only:

- installation created/updated/activated/disabled/deleted;
- origin, pack, connection mode, or profile configuration changed;
- consumer linkage changed; and
- assignment change affecting an active installation.

Do not persist one database audit row per public page load.

The Platform health/readiness surface must fail or warn when the configured
asset catalog is missing or inconsistent. It must not require every customer
runtime to be reachable for the Platform process itself to remain live.

## 19. Verification Plan

### 19.1 Platform unit and integration tests

- Flyway migration passes on PostgreSQL and test database.
- Customer scoping prevents cross-customer installation access.
- Consumer and installation customer ownership must match.
- Installation IDs are opaque and unique.
- Origin normalization rejects paths, queries, credentials, wildcards, and
  insecure production origins.
- Pack configuration schema rejects unknown or unsafe fields.
- Connection-profile schema rejects unknown modes, mismatched handlers,
  arbitrary URLs, unsafe fields, and incompatible versions.
- Activation fails for every common and mode-specific readiness blocker
  independently.
- Public manifest denies missing, disabled, unknown, and wrong-origin calls.
- Anonymous manifest exposes only the assigned public runtime and bounded
  bootstrap/routes.
- Authenticated manifest exposes only the assigned public runtime and reviewed
  broker contract.
- Private manifest exposes only the reviewed adapter and contains no runtime,
  private assignment, assertion, or secret fields.
- ETag returns `304` for an unchanged manifest.
- Consumer rebind changes the manifest and ETag.
- Rebind to an incompatible runtime is rejected before mutation.
- Static assets are present in the packaged JAR/image with correct MIME,
  caching, and digest metadata.
- Existing private `runtime-assignment` authorization remains unchanged.

### 19.2 Installer and widget tests

- One script initializes one workspace without customer JavaScript.
- Manifest URL is derived from the script source.
- Unknown schema/connection/profile/handler/pack/version fails closed.
- Assets load once in deterministic order.
- Integrity and registration mismatch prevents mounting.
- Duplicate script execution is idempotent.
- Anonymous bootstrap, renewal, auth-context probe, conversation, and query
  continue to work.
- Authenticated broker exchange, direct query, expiry/renewal, subject
  isolation, and broker failure handling work without host-specific widget
  code.
- Private adapter bootstrap, mediated query/conversation transport, assignment
  refresh, and adapter failure handling work without private material in the
  browser.
- Existing explicit initialization for all three widget modes remains
  supported independently of Platform installation discovery.
- Current-page attachments and navigation persistence continue to work.
- An assignment change applies to a new session without moving an active one.
- No domain text or action name exists in generic installer/widget logic.

### 19.3 Dealership pack tests

- Inventory and detail pages select the correct tool group without backend
  runtime discovery.
- Browse tools remain available when contextual attachments exist.
- Search, vehicle details, comparison, location, callback, and test-drive
  presentations remain functional.
- Protected IDs are re-resolved server-side.
- Mobile and desktop Max Mode remain usable.
- Generic fallback still handles an unmapped action result.

### 19.4 Northfield anonymous live staging canary

Capture evidence for:

1. Platform-hosted script response and immutable asset digests;
2. accepted-origin manifest and rejected-origin manifest;
3. active consumer/release assignment readback;
4. anonymous bootstrap identity matching the assigned deployment;
5. direct browser-to-runtime query traffic;
6. grounded inventory and dealership-document retrieval;
7. read action and confirmed write-action receipts;
8. page attachment and multi-page navigation;
9. desktop and mobile screenshots;
10. disabled-installation failure behavior;
11. consumer rebind followed by new-session runtime resolution; and
12. absence of `/api/public/runtime-descriptor` and Platform chat-proxy calls.

### 19.5 Authenticated and private compatibility canaries

Before declaring the generic installation contract complete, capture:

1. an approved staging authenticated application exchanging its existing user
   identity through a registered broker for a short-lived runtime token;
2. direct authenticated query and conversation traffic to the manifest's
   assigned deployment;
3. rejection of expired, wrong-origin, wrong-audience, wrong-deployment, and
   caller-expanded-scope tokens;
4. a Shopify test store or equivalent Bridge staging client completing
   bootstrap, query, retrieval, and one non-destructive action through
   `backend-mediated-private-runtime`;
5. Bridge assignment lookup resolving the currently bound verified runtime;
6. browser evidence containing no private runtime assertion, assignment
   credential, or internal runtime URL;
7. a private-mode manifest selecting the reviewed Bridge adapter without any
   Shopify branch in the generic loader/widget; and
8. consumer rebind behavior in which an active session stays pinned and a new
   broker/adapter session resolves the replacement deployment.

The existing production Shopify path must remain operational throughout this
work. Migrating its asset bootstrap to Platform-hosted `install.js` may be a
coordinated follow-up, but the private connection contract and Bridge behavior
are release-blocking regressions for this plan.

Run focused widget, experience-pack, Platform backend, Platform UI, and
dealership browser gates first. Because this changes Platform public security,
consumer promotion guards, shared browser assets, and the customer UI, run the
normal Platform code-regression and release-readiness gates before production.
No AI Fabric framework release or framework-wide test matrix is required unless
implementation uncovers a genuine missing framework contract.

## 20. Delivery Sequence And Status

### Phase A: Contracts and persistence

- [x] Add the versioned installation-manifest JSON contract and fixtures.
- [x] Add the Flyway migration, entity, repository, models, and lifecycle
  service.
- [x] Add reviewed experience-pack registry and configuration validation.
- [x] Add reviewed connection-profile registry and all three versioned
  connection union fixtures.
- [x] Add readiness service and consumer-binding guard.

### Phase B: Platform public delivery

- [x] Implement the safe manifest service and endpoint.
- [x] Implement exact-origin CORS and public request rate limiting.
- [x] Implement loader and immutable asset serving with cache headers/ETags.
- [x] Add packaged-asset catalog verification and health diagnostics.

### Phase C: Generic installer and pack migration

- [x] Build the generic `install.js` entry.
- [x] Add pack registration, connection-handler mapping, broker/adapter
  bootstrap, and manifest-mount contracts.
- [x] Preserve and regression-test explicit initialization for all three
  existing generic widget modes.
- [x] Refactor dealership pack away from backend descriptor discovery.
- [x] Update generic/dealership external developer guidance to the one-script
  AI Workspace contract.

### Phase D: Platform UI

- [x] Add AI Workspaces customer surface and typed create/edit flow.
- [x] Add typed connection mode/profile selection plus readiness, activation,
  disablement, assignment, and script-snippet controls.
- [x] Add UI tests for lifecycle and customer scope.

### Phase E: Northfield cutover

- [x] Create/bind the Northfield consumer.
- [x] Publish a workspace-ready verified deployment release.
- [x] Create and activate the Northfield installation.
- [x] Replace manual site initialization with the Platform script.
- [x] Delete obsolete dealership runtime-descriptor code/config.
- [x] Run focused and live staging/production-site evidence gates.

### Phase F: Authenticated and private compatibility

- [x] Implement and browser-test the authenticated broker contract against an
  assigned runtime using a deterministic broker fixture.
- [ ] Prove an approved real authenticated staging broker against an assigned
  runtime.
- [x] Implement and regression-test the existing Shopify Bridge private path
  and private manifest
  projection against a test store.
- [x] Verify generic new-session rebind behavior, browser storage isolation,
  and credential non-disclosure in all three modes.
- [x] Record that the existing Shopify production integration remains healthy.

### Phase G: Production release

- [x] Build immutable Platform backend/UI images from one source commit.
- [x] Verify packaged asset versions/digests from the live Platform.
- [x] Deploy staging, pass gates, then deploy production.
- [x] Verify `https://api.loomai.pro/api/public/ai-workspace/install.js` and a
  production-safe installation manifest.
- [x] Update operating context, strategy, external guide, and release evidence.

## 21. Definition Of Done

This change is complete only when all of the following are true:

1. A customer can install LoomAI Workspace using one Platform-hosted script and
   one public installation ID.
2. Anonymous mode requires no customer backend. Authenticated/private modes use
   a reviewed broker/adapter while still requiring no customer-authored
   workspace bootstrap or routing JavaScript.
3. Platform resolves the installation through the stable consumer binding to
   an explicitly assigned verified deployment/release.
4. All three generic connection modes are represented by one typed manifest
   union and mapped to generic widget handlers.
5. The browser receives no long-lived secret, assignment credential, private
   assertion, or private runtime contract.
6. Anonymous/authenticated tokens are short-lived and bounded; private
   assertions remain server-side; every browser boundary enforces its exact
   origin and abuse controls.
7. Chat, retrieval, actions, documents, and provider calls bypass Platform
   after initialization: directly to the runtime in public modes or through the
   trusted adapter in private mode.
8. The generic loader and widget contain no dealership, Shopify, provider, or
   host-identity coupling.
9. The dealership experience retains its rich tools, attachments, renderers,
   confirmation, and action receipts through the new installation contract.
10. A consumer rebind moves new sessions to the new deployment without silently
   transplanting an active conversation.
11. Invalid origin, disabled installation, unverified release, unavailable
    broker/adapter, invalid token/assertion, stale profile/pack, and unsafe
    configuration all fail closed.
12. The old dealership-backend runtime descriptor and `data-bootstrap-url`
    production path are removed.
13. Existing explicit widget initialization and the Shopify Bridge private path
    remain functional.
14. Northfield anonymous, authenticated public, and private Bridge evidence is
    recorded before the installation contract is declared complete.
15. Staging and production Platform readback, live browser evidence, and release
    gates are recorded against immutable source and image identities.

## 22. Rollback

The database migration is additive and may remain after rollback. Operational
rollback consists of:

1. disable the affected AI Workspace installation;
2. restore the last known-good Platform backend/UI image pair;
3. restore the last known-good customer site asset if required;
4. rebind the consumer only to a verified workspace-compatible deployment; and
5. rerun manifest and the selected mode's bootstrap/broker/adapter, query,
   identity, credential-non-disclosure, and origin-isolation checks.

There is no requirement to operate old and new installation contracts
simultaneously. Rollback is an explicit coordinated release action, not a
per-request compatibility fallback.

## 23. Implementation Decision

The owner approved the following implementation boundary and instructed the
repository implementation to proceed:

> Introduce a generic Platform-owned AI Workspace installation primitive and
> Platform-hosted one-script loader. Resolve its consumer's current verified
> deployment through a browser-safe, typed connection manifest. Preserve
> anonymous direct, authenticated direct through a token broker, and private
> backend-mediated runtime modes. Adopt the anonymous path first in the
> dealership experience, retain the Shopify Bridge private boundary, and delete
> only the obsolete dealership-backend descriptor path.

The remaining unchecked items are hosted rollout/evidence work or require a
real third-party host-identity broker. They are not permission holds on the
implemented contract.

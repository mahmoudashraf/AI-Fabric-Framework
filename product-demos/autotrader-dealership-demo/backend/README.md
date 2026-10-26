# LoomAI Dealership Demo Backend

This service is the customer-owned application boundary for the public
dealership demo. It is intentionally independent from both the provider and
the LoomAI deployment:

- the dealership database owns its website display catalogue, exact filters,
  and buyer lead records;
- the deployment-local LoomAI connector authenticates to the configured stock
  provider, indexes its approved projection, receives signed notifications,
  and exposes governed provider read actions;
- the LoomAI runtime owns chat, retrieval, and action orchestration;
- the browser talks to the runtime through anonymous session bootstrap; and
- the deployment connector calls this backend only for dealer-owned reads,
  authorization, and confirmed lead actions that the provider does not own.

The website catalogue and provider fixture share stable `stockId` values for
the demo, but neither is populated from the other. Replacing the provider does
not require replacing the dealership website. New confirmed lead requests
validate the canonical stock ID and current availability against the external
provider, not the website's independent display rows. Provider-only stock can
therefore be used without adding a substitute website inventory row.

This backend and its business database are independent demonstration customer
systems. They are not provisioned, seeded, retired or deleted with a customer AI
deployment. The AI-only customer templates bind external DATA/ACTION services;
see [the current integration contract](../../../verification-support/productization-01036/EXTERNAL_DEALERSHIP_INTEGRATION.md).

All included vehicles are fictional. The provider canary uses a synthetic
public-document contract fixture; it is not an official Auto Trader sandbox,
certification, endorsement, or proof of production access.

## Local run

```bash
export APP_INTERNAL_API_KEY="$(openssl rand -hex 32)"
export APP_PII_ENCRYPTION_KEY_BASE64="$(openssl rand -base64 32)"
export APP_STAFF_USERNAME=staff
export APP_STAFF_PASSWORD_HASH_BASE64="$(htpasswd -bnBC 12 '' 'replace-this-password' | tr -d ':\n' | base64)"
mvn spring-boot:run
```

The default local database is persistent H2 under `./data`. Hosted deployments
must use PostgreSQL and set `DEMO_PRODUCTION_GUARDS_ENABLED=true`.

## Production environment

Required application values:

```text
PORT=8108
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<secret>
DEMO_PRODUCTION_GUARDS_ENABLED=true
CORS_ALLOWED_ORIGINS=https://loomai.pro
DEALERSHIP_PUBLIC_MEDIA_BASE_URL=https://loomai.pro
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
APP_STAFF_USERNAME=<staff-user>
APP_STAFF_PASSWORD_HASH_BASE64=<base64-encoded-bcrypt-hash>
APP_INTERNAL_API_KEY=<random-secret>
APP_PII_ENCRYPTION_KEY_BASE64=<base64-encoded-32-byte-key>
APP_VERSION=<release-version>
APP_BUILD_COMMIT=<source-commit>
APP_BUILD_TIME=<ISO-8601-build-time>
```

Required for the external confirmed lead-action capability:

```text
DEALERSHIP_RECEIVER_ENABLED=true
DEALERSHIP_RECEIVER_BINDING_ID=<new-explicit-fixture-binding-id>
DEALERSHIP_RECEIVER_CUSTOMER_ID=<authorized-customer-id>
DEALERSHIP_RECEIVER_TENANT_ID=<authorized-tenant-id>
DEALERSHIP_RECEIVER_DEPLOYMENT_ID=<authorized-deployment-id>
DEALERSHIP_RECEIVER_ADVERTISER_ID=<authorized-advertiser-id>
DEALERSHIP_RECEIVER_API_KEY=<dedicated-random-receiver-key-at-least-32-characters>
AUTOTRADER_PROVIDER_BASE_URL=https://<explicit-stock-provider-or-simulator>
AUTOTRADER_PROVIDER_KEY=<server-only-scoped-read-key>
AUTOTRADER_PROVIDER_SECRET=<server-only-scoped-read-secret>
AUTOTRADER_ADVERTISER_ID=<authorized-advertiser-id>
```

The receiver is disabled by default. Both `/api/internal/authz/check` and
`/api/internal/actions/execute` require the dedicated receiver key in
`X-DEALERSHIP-INTERNAL-KEY` and resolve the same exact customer/tenant/deployment
binding. The advertiser must match the configured provider-read account.
Neither `APP_INTERNAL_API_KEY` nor any `LOOMAI_RUNTIME_*` credential is
a receiver fallback; reusing one of those credentials is rejected. Keep those
old integration settings unchanged until a separate deliberate cutover.

This is one active external business fixture, not a managed AI dependency or a
multi-customer database service. For sequential cutover, disable the receiver,
stop/drain old callers, replace the complete receiver binding and its dedicated
credential, restart, then explicitly approve/bind that credential through the
new deployment's external ACTION slot. Revoke the old external binding and
credential separately; a Platform-local approval is not proof of remote access.
Use a new binding ID for the new owner. Scope fingerprints include all five
binding fields, so even reusing an ID label cannot transfer receipt ownership.
Do not copy the old runtime signing keys or staff login authority to the
new customer. There is no need to clone this instance or its business database.

V5 adds nullable receiver-scope metadata without rewriting or assigning owners
to historical rows. New receipts persist the full binding, its fingerprint and
the caller's idempotency key. Their storage key is a structured digest of the
binding fingerprint and caller key; the existing global unique constraint and
historical keys remain intact. Replays look up the exact binding and still
require the original confirmed payload/session. Unscoped rows never replay to
a receiver, including after a legacy-key collision. Operator projections label
them `LEGACY_OPERATOR_ONLY`; their status and content remain read-only. Staff
views remain global fixture-operator surfaces, not customer receipt APIs.

Lead retention is explicitly off by default (`APP_LEAD_RETENTION_ENABLED=false`).
An operator may deliberately enable `APP_LEAD_RETENTION_ENABLED=true` with a
positive `APP_LEAD_RETENTION` (default `P30D`) for scoped receipts only. Legacy
unscoped receipts are always excluded from purging. Preserve the existing DB
and PII encryption key; changing a receiver binding never migrates those rows.

These credentials must read the same advertiser as the DATA plugin. The
simulator must be selected explicitly; simulator-control credentials are not a
stock API credential fallback. Missing credentials or an unavailable provider
return a capability error and create no lead. The stock client never accepts
an endpoint or advertiser from the browser/model and does not follow redirects.
The API uses `/authenticate` form exchange and an advertiser/stock-scoped
`/stock` read before accepting a new request.

Required when the assigned runtime is enabled:

```text
LOOMAI_RUNTIME_ENABLED=true
LOOMAI_RUNTIME_BASE_URL=https://<assigned-runtime>
LOOMAI_RUNTIME_TRUSTED_API_KEY=<deployment-trusted-backend-key>
LOOMAI_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=<deployment-private-assertion-key>
LOOMAI_RUNTIME_ASSERTION_ISSUER=dealership-demo-backend
LOOMAI_RUNTIME_ASSERTION_AUDIENCE=<deployment-audience>
LOOMAI_RUNTIME_DEPLOYMENT_ID=<deployment-id>
LOOMAI_RUNTIME_CUSTOMER_ID=<customer-id>
LOOMAI_RUNTIME_TENANT_ID=<tenant-id>
LOOMAI_RUNTIME_INVENTORY_VECTOR_SPACE=dealer-vehicle
LOOMAI_RUNTIME_INTEGRATION_SOURCE_ID=autotrader-dealership-stock-source
LOOMAI_RUNTIME_INTEGRATION_WEBHOOK_SOURCE_ID=autotrader-stock-events
```

Those source IDs describe the original independent demo configuration. For the
010.44 external customer template, set the exact compiled IDs instead:
`dealership-external-stock-source` and `external-dealership-stock-events`.
Do not infer a source ID from a provider name or silently use another source.

The runtime credentials stay backend-only. They authorize only the exact
private scopes needed for source status, source reconciliation, and webhook
event evidence. Browser workspace installation, assigned-runtime discovery,
and public conversation routes are owned by the LoomAI Platform installation;
this dealership service does not disclose or route them.

Human conversations and action reviews use the shared LoomAI Desk service.
The staff page opens Desk directly, independently of this backend's staff
login. Desk uses native SSO and assigned support/reviewer roles; no dealership
credentials are forwarded. This backend no longer proxies human conversations
or stores Inbox operator credentials. The customer workspace continues to call
its assigned runtime directly with its anonymous owner session.

The protected meeting-demo simulator controls additionally require:

```text
PROVIDER_SIMULATOR_ENABLED=true
PROVIDER_SIMULATOR_BASE_URL=https://<provider-simulator>
PROVIDER_SIMULATOR_CONTROL_API_KEY=<operator-only simulator key>
PROVIDER_SIMULATOR_ADVERTISER_ID=<fixed synthetic advertiser ID>
PROVIDER_SIMULATOR_WEBHOOK_TARGET_URL=https://<deployment-connector>/integrations/webhooks/autotrader-stock-events
```

These values are fixed by the backend. The browser may choose only one of the
allowlisted scenario codes; it cannot supply an advertiser, stock payload,
webhook destination, or control credential.

The public-site container receives only:

```text
DEALERSHIP_DEMO_API_BASE_URL=https://<dealership-backend>
AI_WORKSPACE_PLATFORM_BASE_URL=https://api.loomai.pro
AI_WORKSPACE_INSTALLATION_ID=awi_pub_<opaque-installation-id>
AI_WORKSPACE_CONNECT_ORIGINS=https://api.loomai.pro,https://<assigned-runtime>
```

## Deployment contracts

The checked-in `deployment/runtime`, `deployment/connector` and original
`staging-profile.json` below preserve the independently operated demo's original
configuration. They are not the new customer self-service provisioning graph.
The current customer template uses reviewed external plugin profiles and the
Platform compiler, not a bundled instance of this backend or its database.

- `deployment/runtime/ai-entity-config.yml` registers `dealer-vehicle`.
- `deployment/document-sources` contains public-safe fictional policy and
  operations documents for the mounted-folder staging proof. Production
  dealerships supply their approved replacements from customer-owned storage.
- `deployment/runtime/ai-actions.yml` defines provider inventory reads, the
  dealer-owned comparison read, and two confirmed dealer-owned lead writes.
- `deployment/connector/actions-routing.yml` routes stock search/detail through
  the deployment's provider connection profile and immutable advertiser
  binding. Comparison, callback, and test-drive routes remain on this backend.
- `deployment/platform/staging-profile.json` composes the provider profile,
  protected resource, complete baseline, targeted current-record fetch,
  signed notification ingress, indexing mapping, runtime source identity, and
  prompts that distinguish live inventory facts from approved document terms.

The provider baseline requests only `lifecycleState=FORECOURT`, uses
`advertiserId`, `page`, and `pageSize`, and stores stable stock/search IDs.
Signed `STOCK_UPDATE` events are treated only as change signals. After raw-body
HMAC and advertiser checks, the connector extracts the bounded `stockId`,
fetches that one current provider record, and then upserts or deletes it through
runtime Data Sync. A periodic full baseline remains the missed-event repair
path. Event bodies are never indexed directly.

Provider search/detail actions and indexing share the same deployment-local
connection profile and advertiser authority. The model, browser, and action
parameters cannot choose an advertiser or provider host.

## Staff verification surfaces

Authenticated staff can inspect and exercise the deployment without receiving
provider secrets:

```text
GET  /api/staff/integration/status
POST /api/staff/integration/reconcile
GET  /api/staff/provider-simulator/status
POST /api/staff/provider-simulator/scenarios/{scenario}
```

The bounded simulator scenarios are `add-stock`, `update-price`, `mark-sold`,
and `delete-stock`. A scenario response includes notification delivery status
and the deployment connector's terminal `COMPLETED` or `DEAD_LETTER` evidence.

## Verification

```bash
mvn test
mvn -o -Dtest='*Test,LeadProviderIdentityPostgresIT' test
docker build \
  -f product-demos/autotrader-dealership-demo/backend/Dockerfile \
  -t loomai/dealership-demo-backend:local \
  .
```

Useful public surfaces:

```text
GET /actuator/health/readiness
GET /actuator/info
GET /api/public/status
GET /api/public/vehicles
GET /api/public/vehicles/by-reference?dealershipId=<id>&reference=<buyer-facing-reference>
GET /api/public/vehicles/resolve?dealershipId=<id>&reference=<buyer-facing-reference>
GET /api/public/security/csrf
```

The lower-level website vehicle resolver is not model-selectable or authoritative
for provider stock validation. Lead actions require a canonical provider stock ID
and perform trusted provider validation. Exact retries reuse the original
encrypted snapshot and receipt; racing duplicate keys revalidate the full
confirmed payload/session/advertiser before replay. A changed request conflicts
instead of being reported as the earlier successful request.

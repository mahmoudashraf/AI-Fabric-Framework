# LoomAI Dealership Demo Backend

This service is the customer-owned application boundary for the public
dealership demo. It is intentionally independent from both the provider and
the LoomAI deployment:

- the dealership database owns the website catalogue, exact filters, current
  dealership availability, and buyer leads;
- the deployment-local LoomAI connector authenticates to the configured stock
  provider, indexes its approved projection, receives signed notifications,
  and exposes governed provider read actions;
- the LoomAI runtime owns chat, retrieval, and action orchestration;
- the browser talks to the runtime through anonymous session bootstrap; and
- the deployment connector calls this backend only for dealer-owned reads,
  authorization, and confirmed lead actions that the provider does not own.

The website catalogue and provider fixture share stable `stockId` values for
the demo, but neither is populated from the other. Replacing the provider does
not require replacing the dealership website.

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
LOOMAI_RUNTIME_INTEGRATION_SOURCE_ID=autotrader-dealership-stock-source
LOOMAI_RUNTIME_INTEGRATION_WEBHOOK_SOURCE_ID=autotrader-stock-events
```

The runtime credentials stay backend-only. They authorize only the exact
private scopes needed for source status, source reconciliation, and webhook
event evidence. The browser receives only the safe runtime descriptor.

Optional route overrides remain available for deployments that publish
different public chat paths:

```text
LOOMAI_RUNTIME_PUBLIC_BOOTSTRAP_PATH=/api/public/chat/session
LOOMAI_RUNTIME_QUERY_PATH=/api/chat/me/query
LOOMAI_RUNTIME_SUGGESTIONS_PATH=/api/chat/me/suggestions
LOOMAI_RUNTIME_AUTH_CONTEXT_PATH=/api/chat/me/auth-context
LOOMAI_RUNTIME_SHELL_CONFIG_PATH=/api/chat/me/shell-config
LOOMAI_RUNTIME_CONVERSATIONS_PATH=/api/chat/me/conversations
LOOMAI_RUNTIME_CONVERSATION_ITEM_PATH_TEMPLATE=/api/chat/me/conversations/{conversationId}
```

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
DEALERSHIP_DEMO_RUNTIME_BASE_URL=https://<assigned-runtime>
```

## Deployment contracts

- `deployment/runtime/ai-entity-config.yml` registers `dealer-vehicle`.
- `deployment/runtime/ai-actions.yml` defines provider inventory reads, the
  dealer-owned comparison read, and two confirmed dealer-owned lead writes.
- `deployment/connector/actions-routing.yml` routes stock search/detail through
  the deployment's provider connection profile and immutable advertiser
  binding. Comparison, callback, and test-drive routes remain on this backend.
- `deployment/platform/staging-profile.json` composes the provider profile,
  protected resource, complete baseline, targeted current-record fetch,
  signed notification ingress, indexing mapping, and runtime source identity.

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
GET /api/public/runtime-descriptor
GET /api/public/security/csrf
```

The lower-level vehicle resolver is not model-selectable. Buyer-facing stock
references are resolved again inside trusted dealer-owned action paths before a
lead can be written.

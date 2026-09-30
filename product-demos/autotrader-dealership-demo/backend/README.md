# LoomAI Dealership Demo Backend

This service is the customer-owned application boundary for the public dealership
demo. It is intentionally separate from the LoomAI runtime:

- the dealership database owns stock, exact filters, current availability and leads;
- a LoomAI deployment owns chat, retrieval and governed action orchestration;
- the browser talks directly to the deployment through anonymous runtime bootstrap;
- the backend pushes approved inventory projections to the deployment-local index;
- the deployment connector calls the backend's protected authorization and action APIs.

The included inventory is fictional and is labelled `Demonstration inventory`.
It is not an Auto Trader emulator and proves no Auto Trader sandbox or production
access.

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
```

Optional route overrides are available when a deployment publishes different
public paths:

```text
LOOMAI_RUNTIME_PUBLIC_BOOTSTRAP_PATH=/api/public/chat/session
LOOMAI_RUNTIME_QUERY_PATH=/api/chat/me/query
LOOMAI_RUNTIME_SUGGESTIONS_PATH=/api/chat/me/suggestions
LOOMAI_RUNTIME_AUTH_CONTEXT_PATH=/api/chat/me/auth-context
LOOMAI_RUNTIME_SHELL_CONFIG_PATH=/api/chat/me/shell-config
LOOMAI_RUNTIME_CONVERSATIONS_PATH=/api/chat/me/conversations
LOOMAI_RUNTIME_CONVERSATION_ITEM_PATH_TEMPLATE=/api/chat/me/conversations/{conversationId}
```

Keep every value in the second block backend-only. The public runtime descriptor
returns only the public runtime base URL and route paths.

The public-site container receives only:

```text
DEALERSHIP_DEMO_API_BASE_URL=https://<dealership-backend>
DEALERSHIP_DEMO_RUNTIME_BASE_URL=https://<assigned-runtime>
```

The runtime URL is used to extend the site Content Security Policy. The browser
obtains its exact public routes from the dealership backend's safe descriptor.

## Deployment contracts

- `deployment/runtime/ai-entity-config.yml` registers `dealer-vehicle`.
- `deployment/runtime/ai-actions.yml` defines three reads and two confirmed writes.
- `deployment/connector/actions-routing.yml` routes those actions and authz checks
  to this service.

The connector must receive `DEALERSHIP_BACKEND_BASE_URL` and the same internal
key configured here. Runtime customer ingestion must allow upsert, delete, and
work-status queries for `dealer-vehicle`:

```text
AI_FABRIC_RUNTIME_AUTH_INGRESS_CUSTOMER_INGESTION_ALLOWED_UPSERT_ENTITY_TYPES=dealer-vehicle
AI_FABRIC_RUNTIME_AUTH_INGRESS_CUSTOMER_INGESTION_ALLOWED_DELETE_ENTITY_TYPES=dealer-vehicle
AI_FABRIC_RUNTIME_AUTH_INGRESS_CUSTOMER_INGESTION_ALLOWED_WORK_STATUS_ENTITY_TYPES=dealer-vehicle
```

Active records produce `UPSERT`; sold, unpublished, or otherwise inactive
records produce `DELETE` using the same stable record/chunk identity. Every
upsert carries the server-owned tenant and deployment IDs required by the
runtime entity projection. Those values come only from the backend's private
runtime configuration, never from the browser or source record.

## Verification

```bash
mvn test
docker build \
  -f product-demos/autotrader-dealership-demo/backend/Dockerfile \
  -t loomai/dealership-demo-backend:local \
  .
```

An accepted ingestion response is not considered indexed proof. The service
persists each `metadata.indexingWorkId` and reconciles it through the runtime
work-status endpoint.

Useful local surfaces:

```text
GET /actuator/health/readiness
GET /actuator/info
GET /api/public/status
GET /api/public/vehicles
GET /api/public/runtime-descriptor
GET /api/public/security/csrf
```

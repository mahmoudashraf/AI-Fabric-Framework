# LoomAI Deployment Operations Service

Optional server-to-server deployment capacity policy and explicit lifecycle jobs. This service owns its own
JDBC/Flyway/PostgreSQL data, with no dependency on Platform backend, runtime,
AI Fabric, provider SDKs, browser authentication, or the Platform database.
Java 21 and Spring Boot 3.2.12 are used as requested for this service.

## Responsibility

- Assess a trusted Platform command against server-owned target limits.
- Persist the original decision and exact command/fingerprint correlation.
- Track short-lived admission holds and trusted `ACTIVE`, `FAILED`, `STOPPED`
  observations. Stopped compute does not imply reclaimed storage.
- Persist and advance explicitly authorized STOP, RETIRE, RECOVER and RECONCILE
  jobs through core's authenticated lifecycle gateway. No provider operation is executed here.
- Return `UNAVAILABLE` for an unconfigured target. A storage failure produces
  HTTP 503, never a fabricated `ALLOW` or a lost financial record.

**Platform owns the bypass.** When disabled, unreachable, invalid, timed out or
`UNAVAILABLE`, Platform may visibly continue under its integration-team/manual
policy. A healthy `ENFORCE` `DENY` must be honored. `OBSERVE` returns the raw
would-decision, including `DENY`, but does not authorize Platform to block.
The mode is selected by trusted core configuration, never by browser input.
Authorization, tenant ownership, credentials, provider verification and existing
funded usage ledgers remain core responsibilities and are not bypassed here.

Lifecycle scheduling is disabled by default and never runs from capacity hold
expiry. The bounded lifecycle workflows below use core-issued grants and core
ownership/effect receipts, not admission decisions. There is no generic workflow
language, historical recovery adoption, provider SDK, financial refund, or direct
access to the Platform database.

## Run

Set a unique service-only bearer secret (32-512 printable non-space ASCII
characters), dedicated database password and server-owned policy. Supply secrets
through the environment or an ignored local `.env`; do not commit them.

```sh
export DEPLOYMENT_OPERATIONS_API_KEY="$(openssl rand -hex 32)"
export DEPLOYMENT_OPERATIONS_JDBC_PASSWORD="$(openssl rand -hex 32)"
export DEPLOYMENT_OPERATIONS_CAPACITY_POLICY_JSON='{"revision":"team-staging-v1","targets":{"dtp-coolify-staging":{"cpuMillis":4000,"memoryMiB":8192,"diskMiB":20000,"databases":4,"indexes":8,"dependencies":4,"replicas":4,"concurrentApplies":1}}}'
docker compose -f product-services/deployment-operations-service/compose.yml up --build -d
curl --fail http://localhost:8087/actuator/health/readiness
```

The example limits are illustrative, not measured capacity or a production
recommendation. Database storage is a dedicated Docker volume; PostgreSQL is not
published to the host. The service is bound to localhost by default. In hosted
use, put it on a private network and use HTTPS if traffic crosses hosts. Keep
the bearer secret on the Platform server; it must never reach the widget/browser.
The service has no CORS configuration. TLS termination is outside this image.

For an existing dedicated PostgreSQL database:

```sh
export DEPLOYMENT_OPERATIONS_JDBC_URL='jdbc:postgresql://db.internal:5432/deployment_operations'
export DEPLOYMENT_OPERATIONS_JDBC_USERNAME='deployment_operations'
export DEPLOYMENT_OPERATIONS_JDBC_PASSWORD='use-your-secret-store'
mvn -f product-services/deployment-operations-service/pom.xml clean verify
java -jar product-services/deployment-operations-service/target/deployment-operations-service-0.1.0.jar
```

Flyway creates only this service's tables in its database. Do not point this
service at the Platform database. The database account needs migration rights for
this database only. Back up the volume/database: losing observations means the
service no longer knows already-running resources. Do not enable enforcement on
an empty database as if it were proof of an empty fleet; reconcile trusted core
observations first or continue in manual/observe mode.

## Configuration

- `PORT`: `8087`.
- `DEPLOYMENT_OPERATIONS_API_KEY`: required; no built-in/test default.
- `DEPLOYMENT_OPERATIONS_JDBC_URL`: defaults to local dedicated PostgreSQL.
- `DEPLOYMENT_OPERATIONS_JDBC_USERNAME`: `deployment_operations`.
- `DEPLOYMENT_OPERATIONS_JDBC_PASSWORD`: supplied externally.
- `DEPLOYMENT_OPERATIONS_CAPACITY_POLICY_JSON`: schema below; without it the
  application policy is `{"revision":"unconfigured","targets":{}}`, so every
  target receives `UNAVAILABLE`. Compose requires an explicit value.
- `DEPLOYMENT_OPERATIONS_HOLD_TTL`: `5m`, configurable from 5 seconds to 30 minutes.
- `DEPLOYMENT_OPERATIONS_MAX_REQUEST_AGE`: `5m`, from 5 seconds to 30 minutes.
- `DEPLOYMENT_OPERATIONS_MAX_CLOCK_SKEW`: `30s`, from zero to 2 minutes.
- `DEPLOYMENT_OPERATIONS_MAX_BODY_BYTES`: `16384`, from 4096 to 65536 bytes.
- `JAVA_OPTS`: optional JVM sizing; compose uses `-Xms64m -Xmx256m`.
- `DEPLOYMENT_OPERATIONS_HOST_PORT`: compose-only host port, `8087`.

Policy JSON contains a bounded revision and up to 256 target IDs. Each target has
all eight capacity fields shown above. Limits are non-negative integer counts;
zero deliberately admits no demand in that dimension. Policy is immutable for a
running process: change the environment and restart **all** replicas with the
same revision/policy. There is no browser policy editor or policy mutation API.
Malformed configuration or a missing/weak API key stops startup. Secrets are not
included in responses or application logs; do not enable request-detail logging.

## API v1

All internal endpoints require `Authorization: Bearer <secret>`. The only public
routes are `GET /actuator/health`, `/actuator/health/liveness`, and
`/actuator/health/readiness`; health details/components are hidden, and readiness
includes the service database. No metrics, environment or configuration endpoint
is exposed.

### Assess

`POST /internal/v1/deployment-operations/assess`, JSON:

```json
{
  "schemaVersion": "1",
  "commandId": "command-unique-123",
  "customerId": "cus-example",
  "tenantId": "ten-example",
  "deploymentId": "dep-example",
  "targetProfileId": "dtp-coolify-staging",
  "releaseId": "rel-example",
  "versionId": "ver-example",
  "versionConfigHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "operation": "APPLY",
  "mode": "ENFORCE",
  "requestFingerprint": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "resourceDemand": {
    "cpuMillis": 1000,
    "memoryMiB": 2048,
    "diskMiB": 4096,
    "databases": 1,
    "indexes": 2,
    "dependencies": 1,
    "replicas": 1,
    "concurrentApplies": 1
  },
  "requestedAt": "2026-10-24T14:00:00Z"
}
```

The timestamp is illustrative: new commands must be within the age/skew limits.
All fields are required. IDs are 1-128 safe identifier characters. Operation is
a bounded uppercase identifier; it is audit context, never an execution request.
The config hash permits 64 hex digits with optional `sha256:` prefix; the core's
request fingerprint must be exactly 64 hex digits and is echoed unchanged.
CPU, memory, replicas and concurrent applies must be positive. Disk, database,
index and dependency counts may be zero. Counts are integer-only, at most
1,000,000,000,000 each. Unknown/duplicate fields, scalar coercion, fractional or
non-finite counts, excessive nesting and oversized bodies are rejected.

HTTP 200:

```json
{
  "schemaVersion": "1",
  "commandId": "command-unique-123",
  "requestFingerprint": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "assessmentId": "assess-server-generated-uuid",
  "decision": "ALLOW",
  "reason": "WITHIN_TARGET_CAPACITY",
  "policyRevision": "team-staging-v1",
  "observedAt": "2026-10-24T14:00:00Z",
  "expiresAt": "2026-10-24T14:05:00Z"
}
```

Other decisions/reasons: `DENY` / `TARGET_CAPACITY_EXCEEDED`, `UNAVAILABLE` /
`TARGET_POLICY_UNAVAILABLE`. The response has exactly the nine fields above.
Core verifies schema, command, fingerprint, allowed decision and expiry before
using an `ENFORCE` result. Service decisions are not provider-state proofs.

The same command and identical request returns the persisted response, even
after expiry/restart: **no TTL renewal or second hold**. The same command with
a changed fingerprint **or any changed payload field** returns HTTP 409
`COMMAND_PAYLOAD_CONFLICT`. The service additionally hashes its own structured
request; it does not assume a caller-supplied fingerprint authorizes payload drift.
Use a new command for a new attempt/policy re-assessment; do not automatically
replay provider effects on a decision retry.

### Outcomes

`POST /internal/v1/deployment-operations/outcomes`, JSON:

```json
{
  "schemaVersion": "1",
  "commandId": "command-unique-123",
  "requestFingerprint": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "status": "ACTIVE",
  "observedAt": "2026-10-24T14:00:10Z"
}
```

HTTP 204 for acceptance and exact duplicate. The command locates the persisted
customer/tenant/deployment/target/release scope; the fingerprint must match.
The caller does not submit replacement scope fields. Observations cannot precede
the assessment or exceed the clock-skew allowance. Exact prior duplicates remain
safe after later state changes. A new observation must be newer than the last;
same-time conflicts, stale observations and outcomes for superseded commands
return 409. A `STOPPED` command cannot reactivate; a new assessment is required.
Unknown commands return 404. An enforced denial has no allocation to activate.

- `ACTIVE`: trusted core has verified activation and completed the cold
  replacement (no old deployment resources left). Project this assessment's
  exact desired resource demand and release its concurrent-apply slot. This
  allows verified downsizing; it is not inferred from a hold timeout.
- `FAILED`: record uncertainty, **retain capacity demand**. Failure does not
  establish no provider side effects, release a financial cap, refund usage,
  delete infrastructure or perform recovery.
- `STOPPED`: trusted core has verified stopped compute. CPU, memory, replicas and
  concurrent-apply demand become zero; disk and provisioned database/index/dependency
  counts remain counted. This is an observation, **not a request to stop or delete**.
  Existing STOPPED records are conservatively projected this way without rewriting
  their history. The v1 outcome contract has no reclamation certificate, so it
  cannot release retained storage. Separately, exact verified lifecycle RETIRE
  completion can settle its pinned service allocation to zero, as described below.

Outcomes after hold expiry are accepted and remain auditable. A failed operation
may later be observed active or stopped; the service never infers that transition.

## Capacity accounting

One allocation exists per deployment/target, with immutable customer and tenant
ownership. A replacement uses the component-wise maximum of previous and new
demand, rather than adding a duplicate whole deployment. This models the agreed
cold-replacement path, **not simultaneous old/new blue-green capacity**. For
overlapping runtimes, core must report combined demand or a separate identity.
Different targets count independently. During `HOLD`/`UNCONFIRMED`, shrinks do
not prove old resources gone: the maximum remains counted. A verified `ACTIVE`
cold-replacement observation projects exact desired demand, including downsizing.
Stopped allocations still contribute retained disk and provisioned resource counts.
A superseded outcome cannot clear a newer operation's hold.

An expired hold becomes `UNCONFIRMED` when another assessment is processed and
still counts; expiry is only the authorization deadline, never proof of absence.
In `OBSERVE`, even a would-denial is tracked because core may proceed. Missing
target policy also tracks the demand for later trusted observation. There is no
silent reset, automatic lease-based refund or automatic deletion of audit rows.

A short database row lock serializes assessments/outcomes across service replicas,
preventing concurrent oversubscription. Transactions have a five-second timeout;
there are no network/provider calls inside them. This is intentionally a
low-volume control-plane implementation. Policy
changes/replicas must be coordinated by the operator.

## Tests and build

```sh
mvn -B -ntp -f product-services/deployment-operations-service/pom.xml clean verify
docker build -f product-services/deployment-operations-service/deploy/container/Dockerfile \
  -t loomai-deployment-operations:local product-services/deployment-operations-service
```

Tests cover the wire contract, service-key boundary, strict/bounded JSON,
configuration failure, every capacity dimension, observe/unavailable semantics,
deduplication, replacement, tenant isolation, TTL uncertainty, outcome ordering,
concurrent admission and duplicate commands. The same repository/service contract
suite runs on H2 and a real PostgreSQL 16 Testcontainer. PostgreSQL tests are
explicitly skipped when Docker is absent; do not call that a PostgreSQL pass.
No tests use or modify live infrastructure, Platform data, or provider balances.

## Platform integration

Platform uses a bounded HTTP client, not this service's Java artifact or database.
Apply Platform migration `V210` before using the new backend. It adds a core-owned
release authorization with immutable owner, target, version and provider-use
profile; it does not backfill or replace historical admission/financial rows.
Required integration setup, source access, promotion and verification use that
core authorization rather than a capacity reservation.

Trusted Platform environment settings:

```properties
PLATFORM_DEPLOYMENT_OPERATIONS_MODE=DISABLED
PLATFORM_DEPLOYMENT_OPERATIONS_SERVICE_URL=http://deployment-operations:8087
PLATFORM_DEPLOYMENT_OPERATIONS_API_KEY=<service-only-secret>
PLATFORM_DEPLOYMENT_OPERATIONS_TIMEOUT=PT1S
PLATFORM_DEPLOYMENT_OPERATIONS_ALLOW_INSECURE_HTTP=true
PLATFORM_DEPLOYMENT_PROVIDER_USAGE_MODE=FUNDED
```

`ALLOW_INSECURE_HTTP=true` is only appropriate on the same host's private Docker
network; keep it false and use HTTPS across hosts. Do not publish the service key
or this URL through browser configuration. Core readiness has no service-health
dependency. `DISABLED` makes no policy call. `OBSERVE` and `ENFORCE` make one
assessment with a total deadline of at most one second, no inline retry, and a
64 KiB response bound. An outcome call is best-effort and cannot change release
success. The authenticated `/api/deployment-operations/status` and exact-release
decision endpoint expose safe status, never the service credential or URL.

Provider funding is a separate choice, not changed by capacity-service outages.
The default `FUNDED` preserves actual approved allowances. For genuinely new,
integration-team-funded deployments only, an administrator can explicitly set
`PLATFORM_DEPLOYMENT_PROVIDER_USAGE_MODE=TEAM_AUTHORIZED` and
`PLATFORM_DEPLOYMENT_TEAM_AUTHORIZED_TARGETS=<approved-target-id,...>` in the
trusted server environment. This permits shared-key provider charges without a
Platform hard allowance on those targets. Existing deployment/target allocations
remain funded, and the mode is pinned to each release. It is not a customer or
chat setting. `CUSTOMER_KEY` is deliberately rejected until a complete owned-key
contract is implemented; it is not an implicit unbounded shared-key fallback.

Replacement stops and confirms the owned connector, runner and runtime before
restarting the new version. It preserves state and accepts downtime; it does not
claim a graceful drain or require worker-fencing certificates. Automatic legacy
expiry, reclamation and retirement workers are not registered in this delivery.
Old diagnostic/history APIs remain available but do not govern new applies.

To qualify the actual Platform client against this independently launched JAR
and a separate PostgreSQL Testcontainer:

```sh
mvn -B -ntp -f product-services/deployment-operations-service/pom.xml verify
mvn -B -ntp -f Platfrom/backend/pom.xml \
  -Dtest=DeploymentOperationsServiceProcessTest \
  -DdeploymentOperationsJar="$PWD/product-services/deployment-operations-service/target/deployment-operations-service-0.1.0.jar" test
```

That explicit cross-artifact test is skipped unless the JAR property is supplied,
so the standalone service does not become a prerequisite for a normal Platform
build. It exercises real allow/deny/observe responses, an outcome and service
shutdown followed by visible core bypass. PostgreSQL/Docker is required.

### Implementation qualification: 24 October 2026

`mvn -B -ntp ... verify` passed **137 tests, zero failures/errors/skips**:
41 service tests on H2, 43 on PostgreSQL 16 (including actual-socket HTTP),
41 wire/auth tests, and 12 configuration/default-policy/body-limit tests.
This includes zero-disk demand and verified cold-replacement downsizing.
The executable JAR built successfully. Compose configuration validated.

The initial local Docker build failed because of the local builder's `overlay2`
mount state. The independent image workflow subsequently passed, and immutable
image `ghcr.io/mahmoudashraf/loomai-deployment-operations-service@sha256:167ae0b1ce9b59bf238e45d50c0bf08327d4c86652903dd305f6a82e76fd5f44`
is deployed and verified on both staging and production. Each instance has its
own database/user, private network alias, service-only credential and no public
port. The service role cannot select Platform deployment tables. Both instances
start with unconfigured capacity policy; they do not claim fleet enforcement.

Both services were explicitly stopped for the fresh customer-journey proof;
both control planes remained healthy. The canonical 010.46 plan records the
exact journey results and cleanup, separately from the local enforce/outage
wire tests. Existing financial terms and customer assignments were preserved.
Legacy operational histories are not imported or replayed. Actual ownership,
provider verification and native effects remain Platform responsibilities.

## Lifecycle gateway contract

The frozen wire source is `com.loomai.operations.lifecycle.LifecycleContracts`.
Core mirrors these records, not the executable service artifact:

- `JobRequest(schemaVersion, operationId, grantId, customerId, tenantId,
  deploymentId, targetProfileId, releaseId, scopeHash, goal, mode, policyRevision,
  dueAt, deadlineAt, requestedAt)`.
- `Command(schemaVersion, commandId, operationId, grantId, scopeHash, phase)`.
- `Receipt(schemaVersion, commandId, operationId, grantId, phase, status, code,
  observedAt, blockers)`.

Schema version is the string `1`. Timestamps are ISO-8601 Instants. All scope
fields are required; identifiers use the existing 1-128 character ID pattern,
and scopeHash is exactly 64 hex characters. Receipt codes are bounded uppercase
identifiers; blockers have at most 32 nonblank strings of at most 256 characters.
Goals are `STOP`, `RETIRE`, `RECOVER`, `RECONCILE`; modes are `COLD`, `GRACEFUL`.
Receipt statuses are `PENDING`, `SUCCEEDED`, `BLOCKED`, `UNKNOWN`, `FAILED`.
The service checks every receipt's schema, command, operation, grant and phase.
The core grant must independently bind exact scope, goal, mode, policy, dates and
retention/reclamation authority. Request fields and a capacity ALLOW do not grant
permission. Core must reject phases outside the grant or a stale target claim.

New requests use the existing max-request-age/clock-skew window. dueAt may be at
most 366 days after requestedAt, and no earlier than requestedAt minus max-request-age.
deadlineAt must be strictly after dueAt and at most seven days later. Exact replay
is checked before freshness, so retries never renew dates or broaden authority.

The gateway uses only server-configured base URL/key, never a request URI:

- POST `<gateway-base>/internal/v1/deployment-lifecycle/commands` sends Command.
- GET the same path plus `/{commandId}` polls Receipt.

Core must persist its exact command before effects, deduplicate by grant/phase,
serialize target claims with cold apply, and treat UNKNOWN readback as observation,
not a fresh provider dispatch. Explicit replay of a BLOCKED command may re-observe
after a configuration fix; core must still enforce grant dates and identity.

## Lifecycle service API

All routes use the existing service-only bearer authentication and body limits.
Platform proxies these only after owner authorization and core grant creation.
The service returns its own JobView JSON; it does not claim to have revalidated
core grants at registration. Only a matching successful CLAIM receipt advances work.

Base: `/internal/v1/deployment-operations/lifecycle`:

- POST `/jobs`: JobRequest; returns HTTP 202 and the durable job. Same operation
  plus identical payload returns that job; any changed field returns 409.
- GET `/jobs?offset=0&limit=50`: jobs plus nextOffset; limit capped at 100 and
  offset bounded to 10000. GET `/jobs/{operationId}` returns status, current stable
  command, last core receipt, immutable request and cancellation/irreversibility state.
- POST `/jobs/{operationId}/advance`: advances or observes one phase. Future dueAt
  jobs stay scheduled. Empty action requests use JSON `{}` with Content-Type application/json.
- POST `/jobs/{operationId}/retry`: explicit retry of BLOCKED uses POST of the
  **same command ID and payload**, allowing core re-observation. UNKNOWN, PENDING,
  crashed dispatches and failed/expired command observations use GET only.
- POST `/jobs/{operationId}/cancel`: requests cancellation, allowed only before
  FENCE/STOP/DELETE/RECOVER dispatch. Unresolved CLAIM/OBSERVE_DRAIN/OBSERVE
  commands do not delay CANCEL; uncertain native mutations are polled first.
  Core must atomically close the grant against late CLAIM and acknowledge any Runtime drain cancellation.
  Only a successful core CANCEL receipt marks CANCELLED and frees the service slot.
- GET `/jobs/{operationId}/events?after=0&limit=50`: immutable events and nextCursor;
  limit capped at 100. POST `/notices/dispatch` explicitly dispatches bounded outbox work.

One unfinished job occupies a deployment/target slot. Different targets advance
independently, and recorded target customer/tenant ownership cannot be reassigned.
Renewal is deliberately cancel/resubmit: acknowledge core CANCEL, then submit a
new operation ID and newly authorized grant. A grant cannot be recycled into a
different job, even after cancellation/completion. No in-place deadline or grant edits.

Fixed phase order:

```text
STOP/RETIRE: CLAIM -> [BEGIN_DRAIN -> OBSERVE_DRAIN -> FENCE if GRACEFUL]
             -> STOP -> [DELETE if RETIRE] -> VERIFY -> RELEASE
RECOVER:     CLAIM -> RECOVER -> VERIFY -> RELEASE
RECONCILE:   CLAIM -> OBSERVE -> RELEASE
```

Each command is committed READY with a deterministic ID before IO, then its SENT
intent commits before POST. All network IO is outside database transactions.
Worker leases bound ownership, not effects. After restart/lease takeover, SENT
always resumes with GET, including a 404 or loss between SENT commit and POST.
Such a command may require explicit core reconciliation; it is never discarded,
renamed, automatically resent, or interpreted as no effect. Stale worker receipts
cannot overwrite a takeover. BLOCKED/FAILED do not auto-retry. A lost explicit
BLOCKED replay response also returns to GET-only observation.

No new phase is posted after deadline except CANCEL. Existing accepted commands
can still be polled. A partially irreversible job may therefore remain blocked
with its core claim retained; expiry is not cancellation, RELEASE, or absence.
SUCCEEDED is recorded only after successful RELEASE. Core must provide truthful
unsupported/blocker receipts for missing graceful/provider capabilities and must
not turn opaque strings into drain, deletion or reclamation proof.

On registration, V3 pins the current capacity assessment command only when its
stored release/customer/tenant/deployment/target exactly match the job. Successful
STOP + VERIFY + RELEASE settles only that still-current allocation to STOPPED
with disk and provisioned counts retained. RETIRE additionally requires successful
DELETE and settles it to STOPPED with zero current demand. The allocation update
and durable CAPACITY_SETTLED event share the RELEASE transaction. Assessments,
original outcome history, command identity and financial records are not rewritten;
late outcomes cannot reactivate a settled command without a new assessment.
Superseding assessments (even for the same release), foreign or uncorrelated
allocations, pending/unknown phases, cancellation, RECOVER and RECONCILE never
reduce capacity. Jobs registered before V3 have no pinned command and are not
backfilled or used to settle capacity retroactively.

## Lifecycle scheduling and notices

- `DEPLOYMENT_LIFECYCLE_SCHEDULER_ENABLED`: false by default; explicit advance,
  retry and cancellation still work. When enabled, only registered due jobs are
  advanced, at most ten per tick. No fleet discovery or legacy job adoption.
- `DEPLOYMENT_LIFECYCLE_SCHEDULER_INTERVAL`: PT5S by default.
- `DEPLOYMENT_LIFECYCLE_GATEWAY_BASE_URL` / `DEPLOYMENT_LIFECYCLE_GATEWAY_KEY`:
  required to advance work; scheduler startup fails without them.
- `DEPLOYMENT_LIFECYCLE_ALLOW_INSECURE_HTTP`: false; only explicit trusted local
  test/private-network use should enable it. Redirects and URI credentials/query/
  fragment are rejected. Keys are 32-512 printable non-space ASCII characters.
- `DEPLOYMENT_LIFECYCLE_HTTP_TIMEOUT`: 2s; 50ms-10s total response deadline and
  64 KiB response cap. There are no inline retries.
- `DEPLOYMENT_LIFECYCLE_WORKER_LEASE`: 30s; at least five seconds and twice HTTP timeout, and at
  most five minutes. Lease expiry never clears a job or a core claim.
- `DEPLOYMENT_LIFECYCLE_NOTICE_LEAD`: 24h; bounded from zero to 30 days.
- `DEPLOYMENT_LIFECYCLE_WEBHOOK_URL` / `DEPLOYMENT_LIFECYCLE_WEBHOOK_KEY`: optional
  trusted destination and separate bearer key, never job fields.

Notice/due events are durable and emitted once per operation. Event insertion and
outbox creation share the job transaction. Delivery is at-least-once with stable
eventId and Idempotency-Key, bounded backoff and eight attempts, including abandoned
delivery leases. Receivers must deduplicate that ID. No webhook is called unless
configured; no callback API accepts lifecycle authority. Automatic delivery requires
scheduling enabled, or use the explicit authenticated notices/dispatch endpoint.
Events created without a webhook stay auditable and are not retroactively delivered.

Lifecycle tests share H2/PostgreSQL scenarios for workflows, frozen requests,
per-target concurrency, crash/lease takeover, unknown effect polling, deadlines,
acknowledged cancellation and notice replay. Local HTTP tests verify actual wire
authentication, no redirects/retries, total deadlines, response caps and trusted
webhook IDs. These tests use fake core receipts, not real provider effects.

# LoomAI Deployment Operations Service

Optional server-to-server deployment capacity policy. This service owns its own
JDBC/Flyway/PostgreSQL data, with no dependency on Platform backend, runtime,
AI Fabric, provider SDKs, browser authentication, or the Platform database.
Java 21 and Spring Boot 3.2.12 are used as requested for this service.

## Responsibility

- Assess a trusted Platform command against server-owned target limits.
- Persist the original decision and exact command/fingerprint correlation.
- Track short-lived admission holds and trusted `ACTIVE`, `FAILED`, `STOPPED`
  observations. No provider operation is executed here.
- Return `UNAVAILABLE` for an unconfigured target. A storage failure produces
  HTTP 503, never a fabricated `ALLOW` or a lost financial record.

**Platform owns the bypass.** When disabled, unreachable, invalid, timed out or
`UNAVAILABLE`, Platform may visibly continue under its integration-team/manual
policy. A healthy `ENFORCE` `DENY` must be honored. `OBSERVE` returns the raw
would-decision, including `DENY`, but does not authorize Platform to block.
The mode is selected by trusted core configuration, never by browser input.
Authorization, tenant ownership, credentials, provider verification and existing
funded usage ledgers remain core responsibilities and are not bypassed here.

This is an admission/limits service, **not parity with every legacy operations
feature**. Drain fencing, recovery execution, retirement orchestration, provider
budgeting, automatic stop/delete, billing and autonomous remediation are absent.
Future drain/recovery proposals can be optional policies, not a workflow engine.

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

Flyway creates only this service's four tables in its database. Do not point this
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
- `STOPPED`: trusted core has verified the entire tracked deployment/target
  allocation is absent. Only then remove its demand from capacity accounting.
  This is an observation, **not a request for this service to stop anything**.

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
A superseded outcome cannot clear a newer operation's hold.

An expired hold becomes `UNCONFIRMED` when another assessment is processed and
still counts; expiry is only the authorization deadline, never proof of absence.
In `OBSERVE`, even a would-denial is tracked because core may proceed. Missing
target policy also tracks the demand for later trusted observation. There is no
silent reset, automatic lease-based refund or automatic deletion of audit rows.

A short database row lock serializes assessments/outcomes across service replicas,
preventing concurrent oversubscription. Transactions have a five-second timeout;
there are no network/provider calls inside them. This is intentionally a
low-volume control-plane implementation, not a distributed scheduler. Policy
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

The Docker image build was attempted but is **not qualified** on this machine:
BuildKit stalled while resolving base-image metadata; an isolated legacy build
then failed creating an `overlay2` mount with `invalid argument`. No Docker
daemon reset, global cleanup or live deployment was performed. Re-run the image
build/Compose startup in a healthy builder before publishing this service image.
This limitation does not invalidate the real PostgreSQL/HTTP tests, and it is
not a claim that the image has been deployed or that all legacy operations
features have moved.

# AI Operational PostgreSQL Binding Contract

The deployment creator selects `MANAGED_POSTGRES` or `CUSTOMER_POSTGRES` through
the reviewed bootstrap request's `aiStateStorageMode`. The published version
retains this explicit choice. Neither mode authorizes a dealership inventory,
booking, lead or CRM database: customer business systems remain integrations.

## Administrator API

`POST /api/deployments/{deploymentId}/targets/{targetId}/ai-state-storage-binding`

```json
{
  "versionId": "new-published-never-applied-version-id",
  "host": "postgres.customer.example",
  "port": 5432,
  "databaseName": "customer_database",
  "runtimeSchema": "loomai_runtime_stage",
  "connectorSchema": "loomai_connector_stage",
  "username": "loomai_ai_state",
  "password": "write-only-secret",
  "migrationAuthorized": true,
  "approvalReference": "customer-approved-ai-schema-migrations"
}
```

`GET` on the same route requires `?versionId=...`. Both return the unchanged
`DeploymentAiStateStorageContracts.Summary`: `bindingId`, `customerId`, `tenantId`,
`deploymentId`, `targetProfileId`, `versionId`, `configHash`, `mode`, `lifecycle`,
`migrationAuthorized`, `fingerprint`, `checkedAt`. The fingerprint identifies the
version-approved credential generation, not a mutable latest credential. No
host, database, schema, username, password or JDBC URL is projected. The timestamp
is the original approval time and is not refreshed by replay or readback.

This route remains `PLATFORM_ADMIN`, not a customer permission bypass.

## Customer Wrapper

The parent-owned, owner-scoped route is:

`POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/ai-state-storage-binding`

```json
{
  "expectedEntitlementRevision": 3,
  "operationKey": "bind-new-version-ai-state-generation",
  "targetProfileId": "approved-staging-profile",
  "bindingSlotId": "reviewed-ai-state-slot",
  "connection": {
    "versionId": "new-published-never-applied-version-id",
    "host": "postgres.customer.example",
    "port": 5432,
    "databaseName": "customer_database",
    "runtimeSchema": "loomai_runtime_stage",
    "connectorSchema": "loomai_connector_stage",
    "username": "loomai_ai_state_next",
    "password": "write-only-secret",
    "migrationAuthorized": true
  }
}
```

The wrapper derives `approvalReference`; customers cannot supply it. The offer
must authorize `BINDING_REPLACE` and a `CUSTOMER_AI_STATE` slot supporting
`CUSTOMER_POSTGRES`. `GET` takes `targetProfileId`, `bindingSlotId` and `versionId`.
Use a new operation key for a new version. Parent-owned authorization, HTTP,
customer UI and hosted acceptance are separate from the SQL delegate tests.

## Connection And Ownership

- The reviewed target `networkPolicy.aiStateStorageAllowedHosts` must contain
  the exact lowercase host. Wildcards, raw JDBC overrides and TLS-disable options
  are rejected.
- PostgreSQL uses `sslmode=verify-full` and the JVM trust store, including hostname
  validation. Private server certificates need an explicitly trusted JVM CA;
  this API never accepts trust-all TLS.
- Customers pre-create two distinct dedicated AI schemas and a restricted role
  with the necessary schema usage/create and existing AI-table privileges.
  Database/schema/role creation authority, business-table access, privileged
  reachable memberships and executable application security-definer functions
  are rejected. Migration consent authorizes AI-schema migrations only.
- The physical endpoint/database and the individual runtime/connector schema
  roles remain immutable. The schema pair cannot be swapped or reassigned while
  changing credentials. Production uses its own approved target and schema pair.
- A schema cannot belong to another customer, tenant, deployment or target.
  Historical non-secret claims survive deployment-metadata removal because
  customer-retained schemas may still contain AI data. Reuse requires an
  explicitly reviewed reset/migration protocol.

## Credential Replacement: New Version, Normal Apply

The supported workflow is **immutable credential staging for a new published,
never-applied candidate**, followed by ordinary sequential release replacement.
It does not mutate or reload a live connection. No maintenance coordinator,
`/rotate` API or caller-provided quiescence flag is needed for staging.

1. The customer prepares working replacement credentials with the same restricted
   AI-schema authority. Prefer a second restricted login so the old runtime can
   still drain using its original credentials. Platform never issues `ALTER ROLE`
   or changes customer passwords.
2. Publish a new deployment version. Call the binding API for that version using
   the same physical binding and schema pair, new credentials and fresh consent.
   The deployment row lock serializes this with apply acceptance. Any previous
   release for that target/version, including queued, failed or unknown outcomes,
   blocks a new approval; legacy target-unknown release history also blocks it.
3. The delegate verifies TLS, target allowlisting, ownership and SQL privileges,
   then atomically appends the version approval, credential-generation record and
   two newly named managed secret entries. It never updates old secret entries,
   binding metadata, schema claims or approvals. A failed transaction rolls back
   the new generation and its vault entries together.
4. Apply normally. The release snapshot freezes the selected generation ID,
   fingerprint, physical binding and target-profile fingerprint. Worker apply,
   provisioning and recovery resolve that exact version's generation, never a
   global latest-secret pointer. Identity failures block continuation before
   provider/dependency effects.
5. The existing generation protocol stops/readbacks connector and indexing
   consumers, drains and fences the old runtime, authoritatively stops it, then
   starts/adopts the replacement and resumes its dependencies. External SQL
   replacement requires frozen runtime authority and both the drain coordinator
   and durable stop-checkpoint service. A missing prerequisite is typed
   `AI_STATE_STORAGE_SEQUENTIAL_REPLACEMENT_REQUIRED`, not an overlapping rollout.
6. Verify the new release before the customer revokes the old login. Old secrets
   are retained by this workflow; there is no rotation-time garbage collection.
   Purging historical secrets requires separately proved lack of references and
   the relevant retention policy. No unconditional deletion is introduced.

Exact replay of an approved request returns its original summary and writes
nothing, including after apply is queued. A changed credential for an already
approved version returns `AI_STATE_STORAGE_VERSION_CREDENTIALS_IMMUTABLE`; a
changed approval reference returns `AI_STATE_STORAGE_APPROVAL_IMMUTABLE`.
`AI_STATE_STORAGE_NEVER_APPLIED_VERSION_REQUIRED` blocks initial generation
creation behind any target/version release history. A failed candidate requires
a new published version for different credentials, not rewriting its approval.

V200 adds `deployment_ai_state_storage_credential_generations` with one generation
per `(binding_id, version_id)` and a foreign key to the V198 approval. Generation
identity binds customer, tenant, deployment, target, physical binding, version,
config hash, approved target profile, consent and original approval. Its private
HMAC includes all credential material and scope. Public fingerprints contain no
raw password hash; write-only DTO serialization is not used as HMAC input.
The H2 test-profile mirror is `V120_25`, and `application-test.yml` targets
`120.25`; the real PostgreSQL migration chain reaches V200.

## Boundaries And Failure Recovery

Staging a candidate does not require a downtime fence because no serving secret
is modified. Activating it still requires the real sequential generation fence;
local row locks alone are not claims that remote consumers are quiescent.
The existing durable `FENCED`, `STOP_CONFIRMED` and `ADOPTED` replacement checkpoints
handle the stop/start gap. Restart refreshes authoritative provider-stop evidence;
the provisional runtime handle preserves the previous release identity.

This is **not emergency hot rotation**. If the customer already revoked the old
credential and the old runtime cannot complete its durable drain, the normal
replacement may block. Do not bypass that check. Recover old connectivity or use
a separately authorized, proved recovery procedure. Full target retirement is
not a hidden rotation operation: it may revoke authority and destructively
reclaim derived AI indexes. Its final `RETIRED` gate is not a normal resume permit.
No retirement reactivation or emergency maintenance protocol is added here.

External mode never provisions managed PostgreSQL. Runtime Flyway/Hibernate use
only the runtime schema; connector persistence uses its separate schema without
bootstrap/role-creation authority. `EXTERNAL_AI_STATE_STORAGE_BINDING` remains
`RETAIN_EXTERNAL`: admission does not count it as a managed database and provider
lifecycle/retirement must not delete its server, database or schemas. Any later
AI-row purge policy remains inside the approved AI-schema boundary.

## Evidence

- [Version-bound generation and frozen identity](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentAiStateStorageBindingService.java)
- [Generation storage migration](../../Platfrom/backend/src/main/resources/db/migration/V200__ai_state_storage_credential_generations.sql)
- [Sequential provider replacement and SQL environment generation](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/CoolifyDeploymentProvider.java)
- [Durable replacement checkpoints](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/generation/RuntimeGenerationCheckpointService.java)
- [Verification scope and outstanding gates](AI_STATE_STORAGE_VERIFICATION.md)

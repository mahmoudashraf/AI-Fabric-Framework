# External Integration Binding Contract

Source-only generic DATA/ACTION binding. No provider API calls or business
infrastructure lifecycle are implemented here. This is not an Auto Trader
bridge, CRM, inventory database, account-creation service or health certificate.

## Reviewed Profile

`Definition.externalIntegrationProfiles` is a list of
`ExternalIntegrationContracts.Profile`. Schema:
`loomai-external-integration-profile/v1`.

Fields: `id`, `bindingSlotId`, `pluginId`, `pluginVersionId`, `manifestSha256`,
`targetProfileIds`, `configFields`, `secretFields`, `endpointFields`,
`accountScopeFields`, `allowedHosts`. The last seven are sets of strings.
Profiles use deterministic ordering for integrity evidence. Each selected
composition has one profile per slot; ambiguous alternatives fail closed.

The offer slot has kind `EXTERNAL_SERVICE`, matching `pluginId`, the exact
profile ID in `supportedProfileIds`, and null `installConfigField`. Only exact
published DATA/ACTION manifest pins are supported. Every install field must be
reviewed as text/URL config or `secretRef`; other field types fail closed. URL
fields and endpoint fields must match exactly. DATA protected-resource fields
must appear in `accountScopeFields`. Existing source/action execution contracts
remain in the manifest; no plugin type or hardcoded product endpoint is added.

## APIs

Staff admin review:

`POST /api/customer-self-service/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals`

Request: `versionId`, `targetProfileId`, `bindingSlotId`, `selectedProfileId`,
`config` object, `accountScope` object, `reviewReference`, `operationKey`.
Config is the exact reviewed field map. Account scope must be nonempty; every
mapped account field must equal its config value. This is an explicit staff
review of endpoint/resource authorization, not an assertion that a credential
has passed a provider probe. Staff review creates no binding or credential.

Customer binding:

`POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/bind`

Request: `versionId`, `targetProfileId`, `bindingSlotId`, `selectedProfileId`,
`approvalId`, `expectedEntitlementRevision`, `operationKey`, `credentials` object.
Only literal write-only credential values are accepted; clients cannot supply
URLs, account values, existing secret names, ownership flags or runtime refs.
Credentials must exactly match the reviewed manifest secret fields. Template
syntax, control characters and nontextual values are rejected. Request JSON is
strict, including explicit integral nonnegative revisions; unknown fields fail.

Customer revoke:

`POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/revoke`

Request: same version/target/slot/profile scope, `bindingId`,
`expectedBindingRevision`, `expectedEntitlementRevision`, `operationKey`.
Revocation blocks future resolution and retains credentials, receipts and
external data. It does not revoke an external provider key, delete a resource
or claim that a running deployment has already been deactivated.

Revocation returns `EXTERNAL_INTEGRATION_RELEASE_IN_PROGRESS` rather than a
success receipt while a nonterminal/uncertain release uses that exact
deployment/version/target. Finish or explicitly fail/reconcile that apply first;
no cancellation is inferred. Apply and revoke serialize on the deployment lock.
The persisted release protects the effect interval after the short database
transaction ends, without holding a transaction open for provider provisioning.
Unknown release statuses and unknown target scope block conservatively. Offer
revocation/re-invitation also returns `RELEASE_IN_PROGRESS` while its external
snapshot has an unfinished release; snapshot creation and those commands share
the entitlement write lock. A terminal release does not prevent local revocation,
but any later recovery must revalidate the current grant and frozen binding.

Customer local summary:

`GET /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations?versionId=...&targetProfileId=...&bindingSlotId=...&selectedProfileId=...`

Responses contain IDs, scope labels, status and binding revision, never raw
credentials, approved account values or endpoint config. `CONFIGURED_LOCAL_CREDENTIALS`
is local credential/configuration evidence, not provider connectivity or health.
Bind/revoke use the existing durable customer-command ledger and keyed request
fingerprints. Changed replay conflicts. A binding is immutable per published
version/target/slot; rotate through a new reviewed immutable version/binding.

## Compiler And Execution Hooks

1. `ExternalIntegrationPolicy.validateDefinition(definition)` validates profile,
   slot and immutable manifest compatibility at publication and resolution.
2. `ExternalIntegrationCompilerResolver.installInputs(profile, config, secretRefs)`
   overlays deterministic `MANAGED_EXTERNAL_INTEGRATION_*` aliases on copies.
   It refuses conflicting mutable values. Never write the overlay to a global
   install or mutate a published draft/version.
3. Append `compiler.compilerEvidence(profile)` to immutable
   `compositionProvenance.externalIntegrationProfiles`. Resolution checks exact
   structured evidence, not name/domain/content heuristics.
   `policy.requireCompilerEvidence(definition, provenance)` is the owning
   immutable-version validation hook: missing, duplicate, extra, changed or
   malformed entries fail; empty definitions permit only absent/empty markers.
4. `resolver.requireForEffects(deploymentId, versionId, targetId)` runs before
   admission effects/credit allocation. It requires every exact scoped binding,
   target fingerprint, approval, credential ownership and persisted revision,
   plus public DNS preflight. No business/provider request is made.
5. After runtime authority has been frozen by the owning release flow, call
   `freezeRelease(deploymentId, versionId, targetId, releaseId, authorityFingerprint)`
   in that transaction. The separate snapshot is immutable and authority-bound.
   Do not inject the resolver into the authority service itself: the resolver
   consumes that existing service's release-snapshot validation.
6. Worker/generation calls `releaseEnvironment(...)` with the same exact IDs
   and authority fingerprint before effects. It returns typed `ReleaseEnvironment`:
   `configEnvironment` contains nonsecret values; `secretEnvironment` maps aliases
   to exact owned vault reference names. Resolve these only in the execution
   secret boundary and mark all entries from that map secret by map membership,
   not by key-name heuristics. Never persist resolved credential bytes in plans,
   Coolify details, logs, audit, receipts or API output. Connector-only injection
   is sufficient for these DATA/ACTION contracts.

`requireForEffects` and `freezeRelease` require the owning transaction.
`releaseEnvironment` uses REQUIRED propagation with a 15-second timeout: it can
open a transaction for provisioning callers or join the worker's transaction.
This does not remove its NOWAIT fence or frozen-evidence equality checks.
Recovery and queued redispatch call `requireForRecovery(deployment, release)`
before resuming work or marking a late successful verification active. Revoked
bindings/grants and changed compiler/snapshot evidence cannot become successful
recovery. These checks do not deactivate an already running external service.

`resolveLocal(deploymentId, versionId, targetId)` is network-free and can supply
scoped credential readiness. Do not probe DNS/provider health on ordinary page
reads or copy these aliases into a global secret catalogue fallback. Missing,
foreign, revoked or stale binding evidence must block, not fall back to managed
simulator defaults. Programming/database failures propagate; only expected
precondition denials are suitable for an explicit not-ready summary.

Ordinary deployments with no compiler profile declaration and no reviewed-offer
association return empty without an offer/vault/DNS/authority lookup. A declared
external profile without its exact association fails closed. Production must
have a separately reviewed/bound production target; staging refs are never
reused by promotion. Worker fences use deployment NOWAIT and translate only
known lock contention; unexpected database failures are not hidden.

## Persistence And Remaining Integration

V199 adds approval, binding and release-snapshot tables and command types
`BIND_EXTERNAL_SERVICE`, `REVOKE_EXTERNAL_SERVICE`, `BIND_AI_STATE`. Vault writes
reuse `PlatformSecretService`; JPA flush precedes JDBC timestamp evidence so
PostgreSQL precision, not a transient nanosecond timestamp, is pinned. The
credential bytes are never stored in binding/approval/command evidence.

Owning cleanup/cancellation must include these tables in exact no-effect/history
proofs. Foreign keys deliberately do not silently cascade away external binding
history. This API does not release physical capacity or weaken INDEX retirement.

Static review forbids arbitrary hosts, IP literals, non-HTTPS/userinfo/query/
fragment URLs and URL template injection. Pre-effect DNS rejects mixed/private
answers. Request-time DNS rebinding and redirect enforcement must also be
verified at the deployed HTTP execution boundary; a control-plane preflight is
not a substitute. No hosted execution, provider authorization/ACL or full
customer-apply/promotion race is certified by the focused local tests.

Tests live in the matching backend `externalintegration` test package. The
isolated sidecar command is:

```sh
mvn -o -f /private/tmp/01044-external-integration-sidecar/pom.xml '-Dtest=ExternalIntegration*Test' test
```

PostgreSQL tests separately exercise the full Flyway chain through V199 and a
minimal predecessor schema with actual `JpaTransactionManager`, JPA vault/
command writes and JDBC binding writes. Authorization/owner lookups are local
stubs there; concurrency is the binding flow, not full customer apply. Docker
absence fails rather than silently skipping these tests.

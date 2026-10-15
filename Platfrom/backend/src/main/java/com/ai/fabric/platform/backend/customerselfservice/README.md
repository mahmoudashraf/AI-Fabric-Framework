# Customer Self-Service Authorization (R6)

This module is a closed-offer authorization boundary, not another provisioning
engine. Configuration bootstrap, practical promotion and the generic owning
apply, document-storage, document-operations, workspace and retirement services
invoke this boundary. Admission/provider lifecycle integration and hosted
acceptance must still be verified separately. Existing non-offer staff workflows remain available. No framework
dependencies or live resources are changed by this module.
No P0-P7 acceptance gate is closed by these source changes or unit tests.

## Durable Contracts

- `Definition.schemaVersion` is exactly `1`. Publish a new immutable offer ID
  for every changed permission, plugin manifest, source artifact, target profile,
  policy, or measured limit. There is no update/compatibility/latest-version path.
- Offers pin a published Marketplace template, its exact required plugin versions
  and manifest hashes, reviewed digest-based promoted artifacts, and complete
  profile configuration fingerprints. `profileFingerprint`/`artifactFingerprint`
  are server-side tooling helpers; never give raw provider configuration to a customer.
- An administrator invites a named active persisted `CUSTOMER_ADMIN` belonging
  to an active customer. Invitation reference, expiry, expected revision,
  operation key, and reason are mandatory. Only that owner's real Platform
  session can accept. Invite renewal requires explicit new acceptance.
- Invite/accept/revoke use row locks, revisions, persisted command fingerprints,
  and audit. Repeating an identical key returns its original result; changing
  payload, command or actor conflicts. Replaying an old acceptance does not
  reactivate a revoked grant. No public signup or global owner grant exists.
- `V160` and H2 `V120.8` create offers, entitlements, command receipts, durable
  placement snapshots and immutable offer/deployment associations. They seed no offers, quota, credentials
  or trust configuration. Foreign keys preserve evidence, with no cascading deletion.

## API

All routes are under `/api/customer-self-service`. Bodies reject unknown fields.

- Customer session: `GET /catalogue`, `GET /catalogue/{offerId}`,
  `GET /invitations`, `POST /invitations/{entitlementId}/accept`,
  `POST /catalogue/{offerId}/targets/{targetProfileId}/placement-evidence`,
  `POST /catalogue/{offerId}/deployments`.
- Platform administrator: `POST /staff/offers`,
  `POST /staff/offers/{offerId}/invitations`,
  `POST /staff/entitlements/{entitlementId}/revoke`,
  `GET /staff/customers/{customerId}/entitlements`,
  `POST /staff/targets/{targetProfileId}/placement-evidence` (`{intent}`).
- Catalogue responses include reviewed identifiers, intent, limits, allowed
  operations/settings/slots and blockers. They omit images/repositories,
  provider configuration, provider credentials, vault references and raw secrets.
  Missing/stale/invalid durable placement evidence excludes the target.
  `PLACEMENT_VERIFIED_ADMISSION_REQUIRED` is
  deliberately not apply readiness: catalogue reads perform no remote calls or capacity reservation.
- `400 INVALID_CONTRACT`; `403` for scope, ownership, entitlement or permission
  denials; `409` for stale revisions/operation conflicts and missing or stale
  policy/artifact/placement/prerequisite evidence. Responses contain stable typed
  `errorCode` values, not provider error messages or credentials.

## Owning-Service Integration

Use `CustomerSelfServiceAuthorizationService.authorize(customerId, request)`
inside the owning command's transaction (`MANDATORY`). The customer ID comes
from persisted ownership, or is checked against the active session for bootstrap.
Derive `changedSettings` from the actual parsed command, not a browser claim.
Call `validateSettings` on actual values, and `requirePinnedComposition` on
the exact compiler/artifact inputs. Raw draft YAML, repository/image overrides,
action routes and vault references are not settings. Do not translate unsupported
fields into a permissive generic setting or silently ignore them.

Before that transaction, obtain placement evidence through `CustomerPlacementProbeService`
or its scoped endpoint. `probeAndRecord` is `NEVER`, not `NOT_SUPPORTED`: it
rejects an ambient transaction rather than suspending one whose locks remain held.
All Coolify preflight/server readbacks complete before the short snapshot insert.
Offer publication and locked authorization use `requireFreshEvidence`, which
performs only database/config checks, bound to exact profile hash, server UUID/IP,
destination and reviewed freshness/expiry. Missing/stale/mismatched snapshots
deny without an automatic probe. `AdmissionContext.placementEvidenceId` is part
of R7's exact evidence context; `registerBootstrap` revalidates that snapshot ID.

`BOOTSTRAP_CONFIGURATION` is the explicit prerequisite-setup permission: create
only the owned deployment/draft/tenant and binding slots, with active entitlement,
reviewed composition and verified staging placement. It does not require ready
customer storage/identity/capacity and must not provision a provider resource.
`BINDING_REPLACE`, `BINDING_REVOKE` and `AUTH_CONFIGURE` likewise allow scoped
prerequisite setup before admission. No provider credential/vault administration
is authorized by those operations.

The configuration command accepts only `targetProfileId`, `name`,
`expectedEntitlementRevision`, and `operationKey`. It locks the active accepted
entitlement, validates the reviewed name rule and exact template/plugin pins,
reuses the Marketplace factory, and atomically saves the offer association and
command receipt. Its response is a `CONFIGURATION_ONLY` creation receipt, not
runtime readiness. An identical retry returns that receipt; changed payload or
key reuse across command types conflicts. Revoked/expired grants cannot replay.
Configuration-only creation does not seed an automatic vectorization plan,
publish a version, or queue an apply. The reviewed target selects customer
staging; callers cannot supply an environment, owner, repository or secret ref.

`BOOTSTRAP` (actual resource provisioning), `APPLY` and promotion still require
all admission gates, including R7's reservation. After creating the owned
deployment/tenant, call `registerBootstrap(Authorization, deploymentId)` in the
same transaction. Later operations require this durable association. There is
no automatic grant/backfill of legacy deployments.

`CustomerSelfServiceOperationBoundary` derives the accepted offer from the
persisted deployment association; generic HTTP routes cannot substitute customer
ownership for an offer. An unassociated customer call denies. An associated
staff call still requires the offer as well as existing staff access thresholds.
Legacy non-offer staff paths retain their existing access contract.

The generic version-apply, storage-binding and AI Workspace mutation routes
accept the owning operation key in `Idempotency-Key`. Offer-scoped calls must
supply it; legacy non-offer staff calls do not gain a new requirement. Storage
binding writes also take `bindingSlotId` and `selectedProfileId` query parameters;
revoke takes `bindingSlotId`. The dedicated customer binding endpoint remains
the replay-receipted customer command. Passing a key alone does not add replay
receipts to an owning route that has no receipt store.

Apply validates the explicit target, exact reviewed artifact and published
version provenance (template/plugin version IDs and manifest hashes) before
release recovery, authority snapshots or queuing. Trusted promotion keeps its
existing `PROMOTION_APPLY` authorization rather than requiring a second `APPLY`
reservation. Its caller remains responsible for exact candidate/version pins.

Workspace mutations authorize the persisted consumer's exact deployment,
target and release. Actual normalized values are checked: title directly,
origins individually, and canonical structured profile digests for interaction,
theme and authentication. Publish those digests as `CHOICE` values in the
existing `INTERACTION_PROFILE_ID`, `THEME_PROFILE_ID` and `AUTH_PROFILE_ID`
setting rules. `profileId(kind, payload)` computes `kind:SHA256` using the
existing canonical offer codec. The workspace interaction payload contains
pack/version, connection mode/profile/version, delivery mode, connection config,
and the validated experience configuration excluding theme. The theme payload
is the removed `theme` value (including `null` for no theme). The auth payload
contains profile code/version, interaction policy and validated auth config.
Changing profile contents without a new reviewed digest denies; no arbitrary
JSON is treated as a harmless profile label. Removed domains and clearing a
theme are checked too. Existing creation/configuration validation still runs.

For apply/promotion/activation use explicit target and release identities, not
the deployment's latest runtime URL. `requireOwnedConsumer` checks app ownership;
the parent must still enforce candidate/version/consumer-binding revision and
provider-handle generation through R1/R2. Keep framework selection at `0.8.33`.

Binding writes must resolve the slot under the authorized customer/deployment/
target/release and replace/revoke only that owned binding. Accept secret values
write-only in the owning secret service; never accept a global vault reference.
Never return values or raw references. Inbox operator onboarding does not grant
human-reply authority to the customer owner. Storage must remain customer-owned
S3-compatible text/JSON; no upload, source-object deletion, managed-storage fallback
or mounted production source is introduced here.

The mapped Marketplace install retains logical `slot:document-storage`. Physical
storage handles, credential references, revisions and fingerprints are frozen
per target/release by `DeploymentDocumentBindingResolver`. Binding a second target
does not rewrite or recompile the shared configuration, so the same immutable
version can promote. Revoke removes only that target's handle/platform secrets;
it does not disable the shared plugin or delete source objects. Existing deployed
copies of credentials still require the owning runtime reconcile/IAM revocation
procedure; deleting a Platform binding is not instantaneous provider revocation.

Document operations authorize before contacting the runtime. Their audience is
derived from the published external-document dataset's knowledge-source auth
modes, never from query wording or the caller's supplied visibility label. Every
installed document audience must be permitted; missing audience evidence denies
an offer-scoped operation. Index/refresh validate `DOCUMENT_AUDIENCE` values.
Retirement plan/execute/retry/cancel authorize the exact target and release IDs;
effect-time revocation checks belong to the worker interlock.

The operation/field/staff matrix is `CustomerPermissionMatrix`. Staff calls reuse
`DeploymentAccessService` assignment thresholds. Session operators need an
assignment; operator keys retain that helper's editor ceiling and cannot grant
offers or manage admin-only binding/retention/retirement. Existing non-offer staff
workflows remain on their existing helpers, not a synthesized customer grant.

## Required Trusted Inputs

Configure `platform.customer-self-service.trusted-servers` by `CUSTOMER_STAGING`
and `CUSTOMER_PRODUCTION`, with verified `uuid` and `address`. Staging must be
`46.224.145.148`; production must be `46.225.162.106`. Obtain actual UUIDs from
approved readback, not labels. Also set an explicitly reviewed positive
`evidence-max-age-seconds`. None have defaults. The two trust identities/addresses
must differ. The separate placement probe checks current configured UUID,
passed provider preflight, destination-server match and actual server UUID/IP/readiness. The misleading
`dtp-coolify-prod-staging` production-server record is not an approved staging
target. Do not change placement or spill onto production when staging is full.

Supply exactly one trusted `CustomerPrerequisiteEvidenceProvider` bean. It must
return durable evidence bound to the entire `AdmissionContext` (including placement snapshot ID), reviewed limits,
offer hash, entitlement revision, operation and operation key, with bounded
freshness/expiry. Admission requires approved policy, immutable artifacts,
customer storage, supported identity, measured atomically reserved capacity,
notification transport and operating policy. A nonblank capacity reservation ID
is mandatory; this module never creates capacity or certifies a supplied boolean.
The adapter must verify persisted reservation ownership/state in R7, include all
dedicated dependencies, and retain uncertain outcomes until reconciliation.
The adapter runs under the owning command's locks: read previously recorded
evidence/reservations only; never run S3/identity/provider canaries or other remote
I/O there. Populate those records before the command transaction.
Source operations require scoped storage (and identity for private audiences).
Missing/ambiguous adapters, wrong-context evidence, or any absent gate deny.

Pending inputs: a passing owner S3 canary after both scoped identities receive
read-only `s3:GetBucketVersioning` on the exact bucket ARN, approved complete
template/artifact/dependency policies, verified physical server UUIDs, identity
evidence, measured capacity/lifecycle policy, notification/recovery evidence
and hosted owning-service wiring. The private S3 credentials exist and must not
be copied into source or deployment-readable manifests. Do not substitute demo
fixtures for these prerequisites.

## Verification

Run the new `customerselfservice` unit and migration tests from an independent
backend checkout/output tree, never the shared parent's `target`. Parent final
verification must include real customer sessions/direct HTTP denials, two-customer
isolation, admin/assigned operator/operator-key behavior, real mislabelled-server
denial, stale/missing/mismatched placement evidence, rejection of probes under
entitlement row locks, Postgres concurrency and durable restart/replay, plus all bootstrap/apply/
storage/promotion bypass checks after wiring. Record hosted evidence in
`01036-customer-authorization`; tests alone are not lifecycle certification.

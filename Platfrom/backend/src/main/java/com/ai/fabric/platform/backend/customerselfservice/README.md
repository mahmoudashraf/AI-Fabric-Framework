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
  `GET /deployments`,
  `GET /invitations`, `POST /invitations/{entitlementId}/accept`,
  `POST /catalogue/{offerId}/targets/{targetProfileId}/placement-evidence`,
  `POST /catalogue/{offerId}/deployments`, and
  `POST /catalogue/{offerId}/deployments/{deploymentId}/admission-reservations`.
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
- The deployment portfolio is reconstructed from immutable offer/deployment
  associations for the authenticated customer. It exposes deployment lifecycle
  identity and the reviewed offer fingerprint/revision, but never bootstrap
  operation keys, provider configuration, or credentials. Ownership divergence
  between the association and deployment fails closed.
- Customer retirement planning uses
  `POST /catalogue/{offerId}/deployments/{deploymentId}/retirements/plan`.
  Its body contains `targetProfileId`, `releaseIds`, `drainDeadline`, `retention`
  (`decision`, `expiresAt`, `entitlementReference`, `backupDecisionReference`),
  `operationKey`, and `reason`. It rejects caller-supplied `consumers`, internal
  consumer IDs, binding revisions and ownership fields. The server verifies the
  owned offer/deployment/releases, locks and refreshes the bound consumers in
  deterministic order, and derives their exact binding revisions. The complete
  deployment assignment set is frozen because retirement also protects consumers
  already assigned to other targets. The generic retirement service rechecks the
  snapshot after the short preparation transaction commits; no participant or
  provider work runs while these locks are held. Identical retries reuse the
  persisted expectations, including after assignment detachment; changed requests
  or actors cannot reuse the key. The response contains progress, blockers and the
  planned `requestFingerprint`, without consumer IDs or provider inventory.
- The same customer retirement base path also exposes
  `GET ?targetProfileId={targetProfileId}`, `GET /{operationId}`, and
  `POST /{operationId}/execute`, `/retry`, and `/cancel`. Each command body
  contains only the planned `requestFingerprint`; target and consumer authority
  come from the persisted operation. Every access revalidates the active customer
  session, owned deployment/offer association, entitled target, and selected
  release ownership. Commands require current `TARGET_RETIRE` permission and the
  exact saved fingerprint, including on replay. Read access requires `READ` and
  can show customer-owned scheduled retirements regardless of their initiating
  staff/system actor. Operation locks are released before the existing engine
  runs; its own fingerprint/generation/ownership checks remain authoritative for
  intervening changes. All responses use the customer-safe projection. The generic
  `/api/deployments/{deploymentId}/targets/{targetProfileId}/retirements` controller
  is staff-only; customers cannot read its inventory or submit internal consumer
  expectations through it.
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

The customer admission request contains only `targetProfileId`, `versionId`,
optional `releaseId`, `operation`, `operationKey`, and optional `allocationId`.
It never accepts a manifest ID, capacity amount, prerequisite reference, runtime
origin, provider handle, or secret. After authorization, the server derives an
immutable admission manifest from the published offer topology, exact published
version, target-scoped document binding, frozen runtime authority, active
staff-reviewed fleet policy, staff-reviewed customer quota, and the one active
managed-dependency spec bound to the offer's exact template version. The server
validates the dependency spec bytes/fingerprint and proves its resource demand
fits inside the complete admission envelope; production readback resolves the
same offer association and fingerprint before effects. Repeating the
same materialization is deterministic. Any changed reviewed input produces a
different manifest and cannot mutate the old record.

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

The entitled catalogue also returns the offer's reviewed `workspaceProfiles`:
stable ID/label, normalized interaction payload, theme and authentication
payload. The publisher validates those public payloads through the existing
workspace registries/configuration validator and checks all three hashes against
the offer's setting choices. Credentials and private adapter endpoints are not
published. A fresh customer selects one of these payloads directly; an existing
customer may also reuse an owned configuration that matches the same pins.
The browser does not synthesize configuration from a hash.

Configure the staging app assignment before publishing its first runtime
version. `POST .../deployments/{deploymentId}/staging-consumer-assignments`
accepts an omitted `releaseId` to create an offer-authorized, configuration-only
binding to the deployment's reviewed staging target. It grants no runtime
access and does not activate an Engage installation. Creating the Engage app
then projects its exact origin into the draft. Publish and admit that version,
wait for verification, authorize `ACTIVATE` against its existing allocation,
and activate the staging assignment. Providing `releaseId` on creation retains
the atomic verified-release path. Both creation paths persist an exact-request
receipt and roll back creation/binding if that receipt cannot be saved.

Every admission reservation and its owning command use the same operation key.
Promotion planning reserves one production allocation; apply and activation
reuse its allocation ID with separate exact operation receipts. A lost response
must retry the original reservation payload, including its original allocation
choice. Go-live readiness resolves capacity through the exact release's apply
receipt, regardless of whether its allocation was first created during planning.

For apply/promotion/activation use explicit target and release identities, not
the deployment's latest runtime URL. `requireOwnedConsumer` checks app ownership;
the parent must still enforce candidate/version/consumer-binding revision and
provider-handle generation through R1/R2. Keep framework selection at `0.8.35`.

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
and `CUSTOMER_PRODUCTION`, with verified `uuid`, physical `address`, and exact
Coolify `reported-address`. The reported address is the value returned by that
Coolify control plane's server record (for a local Coolify server this may be
`host.docker.internal`); it is not substituted for the physical address stored
in placement evidence. Staging must be
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

The owner S3 canary passes with separate prefix-scoped identities and read-only
`s3:GetBucketVersioning` on the exact bucket ARN. Remaining hosted inputs are
approved complete template/artifact/dependency policies, verified physical
server UUIDs, identity evidence, measured capacity/lifecycle policy, working
notification/recovery evidence, and owning-service wiring. Private S3
credentials must not be copied into source or deployment-readable manifests.
Do not substitute demo fixtures for these prerequisites.

## Retry And Replacement Contracts

AI Workspace creation persists a whole-command receipt in the same transaction
as the installation, domains and draft origin projection. The browser must reuse
its `Idempotency-Key` after an unknown outcome. The receipt pins the customer,
actor and exact canonical request; replay rechecks current owner, consumer and
offer authorization before returning the original response. A changed request
or actor cannot reuse the key. Deleted or reassigned installations are not
silently recreated. Slug availability is checked only when changing resources,
not when acknowledging a completed command whose former slug was reused.

A reviewed new version can reuse an idle owned target allocation without
expanding its held capacity or topology. Promotion planning stores a separate
candidate receipt and prerequisite proof, leaving the old active release's
readiness and rollback authority intact. Only the exact unconsumed apply receipt
may commit the candidate manifest and bind a new release in the owning apply
transaction. Busy, expired, retiring, foreign or expanded allocations fail
closed. These source contracts still require hosted repeated-release evidence.

## Verification

Run the new `customerselfservice` unit and migration tests from an independent
backend checkout/output tree, never the shared parent's `target`. Parent final
verification must include real customer sessions/direct HTTP denials, two-customer
isolation, admin/assigned operator/operator-key behavior, real mislabelled-server
denial, stale/missing/mismatched placement evidence, rejection of probes under
entitlement row locks, Postgres concurrency and durable restart/replay, plus all bootstrap/apply/
storage/promotion bypass checks after wiring. Record hosted evidence in
`01036-customer-authorization`; tests alone are not lifecycle certification.

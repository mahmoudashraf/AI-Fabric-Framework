# Customer Self-Service Authorization (R6)

This module is a closed-offer authorization boundary, not another provisioning
engine. Configuration bootstrap, practical promotion and the generic owning
apply, document-storage, document-operations and workspace services invoke this
boundary. Core authorization, actual resource ownership and release verification
are mandatory; capacity, expiry and advanced retirement policy are not. The old
capacity/retirement routes retain read-only history and explicit retired mutation
responses, not operational engines. Existing non-offer staff workflows remain
available. This documentation update changes no framework dependency or live
resource. Local source changes and tests do not by themselves close P0-P7 hosted
acceptance gates.

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

Customer routes are under `/api/customer-self-service`. Typed request contracts
reject unknown top-level fields; retained legacy JSON is never execution authority.

- Customer session: `GET /catalogue`, `GET /catalogue/{offerId}`,
  `GET /deployments`,
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
  Reviewed configured targets use `REVIEWED_TARGET_READY`; this is not proof of
  provisioning or verification. Catalogue reads perform no remote calls or
  capacity reservation, and historical placement snapshots do not authorize apply.
- The deployment portfolio is reconstructed from immutable offer/deployment
  associations for the authenticated customer. It exposes deployment lifecycle
  identity and the reviewed offer fingerprint/revision, but never bootstrap
  operation keys, provider configuration, or credentials. Ownership divergence
  between the association and deployment fails closed.
- Capacity archive, under
  `/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations`:
  `GET ?targetProfileId={targetProfileId}` and `GET /{reservationId}` read
  customer-safe historical projections through JDBC. Lists return at most 200
  records. Saved states, expiry and slot flags describe historical records, not
  current capacity, runtime health or authorization. `chargedDemand` is null;
  this archive does not reconstruct active accounting from the old engine.
- Retirement archive, under
  `/catalogue/{offerId}/deployments/{deploymentId}/retirements`:
  `GET ?targetProfileId={targetProfileId}` and `GET /{operationId}` read saved
  status, blockers and retention information, with at most 200 list entries.
  Every access requires the active customer session, owned deployment/offer
  association and current target/entitlement authorization. Retirement records
  also revalidate each frozen release through the core read authorizer. No
  consumer inventory, provider credentials or worker authority is exposed.
  `customerCommandsAllowed` is always false, regardless of the initiating actor.
- Legacy mutations are retired: capacity `POST` and
  `POST /{reservationId}/refresh-evidence`; retirement `POST /plan` and
  `POST /{operationId}/execute`, `/retry`, `/cancel`. After contract and current
  scope checks, they return `410 Gone` with `LEGACY_CAPACITY_OPERATIONS_RETIRED`
  or `LEGACY_TARGET_RETIREMENT_RETIRED` in the reason. They create no reservation,
  renewal, plan, drain, provider effect, assignment change or financial adjustment.
  They do not report successful completion of the requested operation. Invalid
  or unauthorized requests can still fail before the retired response.
- The generic
  `/api/deployments/{deploymentId}/targets/{targetProfileId}/retirements` engine
  controller has been removed. This does not remove the separately authorized
  core resource start/stop/restart/delete APIs.
- `400 INVALID_CONTRACT`; `403` for scope, ownership, entitlement or permission
  denials; `409` for stale revisions/operation conflicts and missing or stale
  core artifact/binding/verification evidence. Customer authorization denials use
  stable typed `errorCode` values, not provider credentials. A retired mutation's
  `410` reason is not a successful operation receipt.

## Owning-Service Integration

Use `CustomerSelfServiceAuthorizationService.authorize(customerId, request)`
inside the owning command's transaction (`MANDATORY`). The customer ID comes
from persisted ownership, or is checked against the active session for bootstrap.
Derive `changedSettings` from the actual parsed command, not a browser claim.
Call `validateSettings` on actual values, and `requirePinnedComposition` on
the exact compiler/artifact inputs. Raw draft YAML, repository/image overrides,
action routes and vault references are not settings. Do not translate unsupported
fields into a permissive generic setting or silently ignore them.

Locked command authorization and `registerBootstrap` use
`requireConfiguredPlacement`: the target must match the reviewed profile and
approved server/destination configuration. A capacity sample, historical
placement lease or optional-service presence is not a substitute for this core
identity check. The compatibility-named `AdmissionContext` carries the exact
core command scope; its `placementEvidenceId` is null on this path and it does
not grant reservation authority.

Explicit placement diagnostics remain available through
`CustomerPlacementProbeService` and its scoped endpoints. `probeAndRecord` is
`NEVER`, not `NOT_SUPPORTED`: provider readback must not run while the caller
holds entitlement locks. Historical snapshots and any publication-time review
checks remain inspectable; new source commands do not need to renew them.

`BOOTSTRAP_CONFIGURATION` is the explicit prerequisite-setup permission: create
only the owned deployment/draft/tenant and binding slots, with active entitlement,
reviewed composition and an approved configured staging target. It does not
require ready customer storage/identity/capacity and must not provision a provider resource.
`BINDING_REPLACE`, `BINDING_REVOKE` and `AUTH_CONFIGURE` likewise allow scoped
prerequisite setup before apply. No provider credential/vault administration
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

`BOOTSTRAP` (actual resource provisioning), `APPLY` and promotion require core
customer/tenant/target authorization, exact published composition and artifact
provenance, owned credentials and required integrations/dependencies, followed
by actual provider execution and verification. They do not require an R7
reservation. After creating the owned deployment/tenant, call
`registerBootstrap(Authorization, deploymentId)` in the same transaction. Later
operations require this durable association. There is no automatic grant or
backfill of legacy deployments.

`CoreReleaseAuthorizationStore` records the exact customer, tenant, deployment,
target, published version/config hash, pinned artifact and provider-use mode.
`require(deployment, version, release)` rejects missing or changed authority;
an old capacity allocation cannot satisfy it. Required dependency specifications
retain exact template/spec fingerprints, artifact ownership and real production
persistence checks. No fabricated admission manifest, funding allowance or
dependency-health pass is created to replace the removed engine.

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
queuing/provisioning. Trusted promotion uses `PROMOTION_APPLY` authorization;
its caller remains responsible for exact candidate/version pins. Historical
recovery or repair records do not select or authorize a new provider effect.

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
then projects its exact origin into the draft. Publish and apply that version,
wait for actual verification, authorize `ACTIVATE` against that exact release,
and activate the staging assignment. Providing `releaseId` on creation retains
the atomic verified-release path. Both creation paths persist an exact-request
receipt and roll back creation/binding if that receipt cannot be saved.

Owning commands retain bounded operation keys, exact request fingerprints and
their existing replay receipts; none is replaced by a capacity reservation.
Go-live readiness requires exact core release authority, unchanged production
bindings, reviewed composition/dependency persistence, the exact fresh successful
post-apply verification run, knowledge evidence and required workspace readiness.
Optional operations status is informational, never a substitute for these checks.
Staging disposition is integration-team cleanup, not a mandatory retirement
certificate or retention entitlement before production activation.

For apply/promotion/activation use explicit target and release identities, not
the deployment's latest runtime URL. `requireOwnedConsumer` checks app ownership;
the parent must still enforce candidate/version/consumer-binding revision and
provider-handle generation through R1/R2. Use the immutable framework/runtime
artifact pinned by the current reviewed offer.

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
`CoreSourceAccessService.require(target, operation, key, audiences)` resolves the
explicit provisioned release through `DeploymentTargetContextService`, retaining
target/provider/resource-handle ownership and unchanged frozen document binding
checks. Runtime access still uses exact frozen authority, credential revisions
and issuer/audience scopes. Capacity expiry cannot make a healthy owned source
unusable, and an operations outage cannot make a foreign source usable.

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

`CustomerPrerequisiteEvidenceProvider` has been deleted. Do not register a
replacement adapter, supply a reservation ID or restore a legacy evidence
fallback. `CustomerSelfServiceAuthorizationService` always evaluates relevant
core prerequisites, independent of `DeploymentOperationsService` presence. Its
preparation entry points cannot bypass those checks. `CAPACITY`, expiry notices
and advanced retirement are optional operational policy, not core authority.

Core and the owning services require the exact active customer/session/tenant,
accepted offer and entitlement revision, permitted operation/settings/audiences,
reviewed target identity, published version and pinned artifact provenance.
Existing-release storage checks resolve that release's owned target binding,
credential revisions and frozen fingerprint. Supported authentication and
identity configuration, scoped runtime credentials, private-audience access,
required dependency initialization and actual provider verification remain
mandatory where selected. New connection setup is verified by its owning
provisioning/integration path, not certified by a supplied boolean or a historical
capacity row. No S3/identity/provider canary runs under entitlement row locks.

Use separate prefix-scoped S3 identities and the reviewed read-only
`s3:GetBucketVersioning` permission on the exact bucket ARN where required by the
storage integration. Validate real target/integration inputs; do not substitute
demo fixtures or optional policy results. Private S3 credentials must not be
copied into source or deployment-readable manifests.

## Optional Operations HTTP

`DeploymentOperationsService` calls the independent deployment-operations service
through a bounded authenticated HTTP client. Configure trusted server-side
`platform.deployment-operations` settings, not browser-supplied approvals. The
client permits a total deadline of at most one second, a 64 KiB response and no
inline retry. Inputs are server-derived command/release scope and demand, not
provider administrator credentials. The service owns its operations database;
it does not query Platform's history or authorize access to customer resources.

- `DISABLED`: manual-team path; no assessment or reservation is required. Core
  records the bypass and still performs authorization, provisioning and verification.
- `OBSERVE`: record the assessment without blocking the authorized command.
- `ENFORCE`: honor a timely authenticated, scope-matching explicit denial.
  Missing/unavailable service or an invalid response is recorded as a policy
  bypass, never as trusted authorization. Core failures still fail normally.

Effective policy mode/decision is recorded with core release authority. A late
assessment or outcome-report failure cannot revoke the committed command,
reclaim resources or turn a failed provider operation into success. Readiness
shows optional status separately; runtime chat/indexing do not depend on this
HTTP service. A service outage does not change a deployment's selected provider
payer, credentials or funded enforcement contract. Financial history stays with
its existing owner; no placeholder allowance or automatic refund is issued.

## Preserved Migrations And History

Retain existing Flyway migrations/checksums and recorded rows, including legacy
retirement operations (`V161`), capacity/expiry (`V165`), admission authorizations
and release bindings (`V178`), and source evidence (`V180`). Their historical
foreign keys are not new-release prerequisites. Core release authorization
(`V210`, `platform_release_authorizations`) is independent of those reservations.
Removing the engine does not drop its tables, rewrite old outcomes, settle an
uncertain attempt or migrate financial/runtime data into the operations service.

The customer archive services use scoped JDBC reads rather than reinstating old
entities, schedulers, drain workers or repair journals. An old `ACTIVE`,
`OUTCOME_UNKNOWN` or `RETRYABLE_FAILURE` row remains that recorded state; reading
it neither resumes work nor proves a current provider effect absent. Historical
records cannot grant authority for a new command. Customer-owned source objects,
runtime AI state and financial/audit history retain their ownership boundaries.

## Retry And Replacement Contracts

Retry uses the owning core command/release contract, current authorization and
exact immutable version/artifact identity. Do not reserve capacity or refresh
legacy evidence first: that mutation route now returns `410`. A lost response
must be reconciled through the original command's normal receipt/readback;
changing the key is not permission to duplicate a provider create or business
action. Keep the original failure, uncertain effects and known resource handles
visible. No failure implies a refund, fresh credit or successful cleanup.

AI Workspace creation persists a whole-command receipt in the same transaction
as the installation, domains and draft origin projection. The browser must reuse
its `Idempotency-Key` after an unknown outcome. The receipt pins the customer,
actor and exact canonical request; replay rechecks current owner, consumer and
offer authorization before returning the original response. A changed request
or actor cannot reuse the key. Deleted or reassigned installations are not
silently recreated. Slug availability is checked only when changing resources,
not when acknowledging a completed command whose former slug was reused.

`DeploymentTargetMutationService` uses the core deployment-row lock and active
target-release guard to exclude conflicting mutations; it needs no retirement
gate table or capacity epoch. Consumer assignments retain customer ownership,
exact target/release matching, verified release status, binding revisions and
existing workspace guards. Historical staging expiry is not an assignment
authorization check.

Cold replacement is an explicit core lifecycle with accepted downtime: stop
owned connectors/index workers and runtime, confirm stop, retain state, then
start and verify the replacement. A failed or uncertain stop remains a real
failure for that target. No graceful-drain certificate, admission reservation,
artifact-repair journal or fabricated completion receipt selects that path.
The normal authorized resource start/stop/restart/delete APIs remain available;
the retired customer archive mutations are not their cleanup replacement.
Stopping/restarting does not authorize data disposal or deletion of a
customer-owned system.

## Verification

Use the coordinated backend test runner or an independent output tree; do not
run conflicting builds against the shared `target`. Focused verification covers
customer/tenant/role isolation, current entitlement/target authorization, exact
release/version/artifact identity, frozen storage/credential/audience boundaries,
required dependency persistence, failed/stale/foreign verification, assignment
mutation exclusion, archive redaction/read-only behavior and `410` with no effects.
Also cover disabled/unavailable operations without bypassing core failures,
rejection of remote probes under entitlement locks, PostgreSQL concurrency,
preserved migrations/history and durable command replay.

The canonical `010_46_OPTIONAL_DEPLOYMENT_OPERATIONS_MODULE_AND_SERVICE_SEPARATION_PLAN.md`
tracks the full-decoupling follow-up and its hosted evidence separately from the
earlier direct-service delivery. Hosted proof for this follow-up is pending CI;
local tests are not proof of staging deployment, cold replacement, production
promotion or Engage behavior. This documentation update performs no live change.

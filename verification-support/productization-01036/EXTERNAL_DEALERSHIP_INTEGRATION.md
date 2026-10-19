# External Dealership AI Integration

## Source Checkpoint

This is the 010.44 workstream-A source contract, not a live rollout receipt.
V196 publishes new immutable catalogue entries, not customer offers, bindings,
managed specs, entitlements, physical policies or provider resources. Old rows,
secrets, data, prefixes, reservations and financial allowances remain intact.
The parent-owned ownership guard must reject historical managed specs whose
artifacts lack an allowed reviewed ownership contract. Existing customer access
can be withdrawn through the supported entitlement-revocation command; offers
have no supported withdrawal status. Do not rewrite an immutable old offer or
mark a charged reservation free to make the replacement workflow succeed.

## Compositions

- `mkp-template-dealership-external-systems-v1@1.0.0`: externally operated stock,
  business actions and Basic-Inbox-compatible human channel. No managed spec.
- `mkp-template-dealership-external-systems-managed-inbox-v1@1.0.0`: the same
  business boundaries, with an explicitly selected AI conversation Inbox. Its
  separate spec is `mds-dealership-ai-managed-inbox-v1-0`; no simulator, business
  receiver or business PostgreSQL is allowed in that spec.

Both retain inventory search/detail/compare, callback confirmation, protected
test-drive confirmation, read-only customer documents and human handover.
External Inbox operator administration belongs to its operator, not the
Platform's managed-Inbox enrollment API. The external-channel binding still
needs reviewed target identity and callback authority; omission is not consent
to silently disable handover. Inbox conversation storage is AI operational
state, not a CRM or inventory authority.

## Required External Contracts

DATA: `mkp-data-dealership-external-stock-v1@1.0.0` requires reviewed config
`providerBaseUrl`, `providerHost`, `advertiserId`, and the manifest's secret-typed
fields `providerKey`, `providerSecret`, `notificationSecret`. These field names
are not permission to select existing secret references. It retains the reviewed
form-token-exchange, stock pagination, typed mapping, resource scope and signed
notification protocol. This is not an arbitrary API adapter or proof of real
Auto Trader access. Real-provider packages remain drafts until rights and
sandbox/production access are verified. An external simulator is only an
independently operated fixture, never deployment-owned customer infrastructure.

ACTION: `mkp-action-dealership-external-engagement-v1@1.0.0` requires reviewed
config `receiverActionUrl` and secret-typed field `receiverApiKey`. The retained
protocol is POST with the reviewed key header, action/params/trace body,
idempotency key and business receipt. Callback remains confirmed; test-drive
requires a verified identity as well as confirmation. The external business
authority must validate canonical stock IDs, consent, customer scope and
idempotency. No local lead, inventory, booking or order database is provisioned.
An arbitrary CRM URL is not automatically compatible with this protocol.

The three discovery actions use `dealership-external-stock-source`, successful
sync and a 1,800-second freshness bound. Their source projection and vector
index are derived evidence of the external authority, not a second business
system of record. No demo seeder or business-database initializer runs in the
new graph. A successful Inbox callback initializer proves that narrow callback
contract, not stock synchronization, action authorization or whole-app readiness.

Customer document storage still uses `CUSTOMER_S3`, the existing read-only
profile, explicit public-content approval and a separately confirmed initial
index. Both V196 variants seed only the knowledge install's required boolean
`config.publicContentApprovalConfirmed=false`; there is no binding ref or
credential default. False is a valid boolean, not approval. Missing S3 inputs
keep the install disabled at bootstrap; the normal customer storage-binding
command can add its scoped binding ref without losing the explicit false.
Per-source public approval and initial-index confirmation remain separate,
and trusted auto-indexing stays forbidden. New templates do not pre-accept
document consent. Workspace profiles
require reviewed `dealerId` and `imageHostAllowlist`; they have no fixed demo
identity, advertiser, endpoint, media host or credential defaults.

## Typed Profiles And Scoped Binding

`buildOfferProposal(input)` now requires `externalIntegrationAllowedHosts` with
exactly two keys: `vehicle-stock` and `business-actions`. Each value is a
nonempty reviewed list of exact lower-case DNS hosts, not URLs, IP literals,
wildcards or inferred demo/provider defaults. This is review input, not a
connectivity result. The builder is local and performs no DNS or HTTP requests.

The candidate contains real `Definition.externalIntegrationProfiles`, schema
`loomai-external-integration-profile/v1`, and matching `EXTERNAL_SERVICE` slots:

- `vehicle-stock` / profile `dealership-external-stock-v1` pins
  `mkv-data-dealership-external-stock-v1-0`, its canonical manifest hash, the
  three config fields, three secret fields, URL field `providerBaseUrl` and
  account field `advertiserId`.
- `business-actions` / profile `dealership-external-engagement-v1` pins
  `mkv-action-dealership-external-engagement-v1-0`, its canonical manifest hash,
  config/URL field `receiverActionUrl` and secret field `receiverApiKey`.

Both profiles explicitly include the reviewed staging and production target
IDs. Each slot has its exact plugin ID, one `supportedProfileIds` value and
null `installConfigField`. Profile host permission does not grant an account
binding on either target. The helper checks exact immutable version IDs for
all six V196 composition plugin pins, including the three unchanged plugins;
it never chooses a latest version. The server remains the publication and
binding authority and revalidates actual immutable manifests.

Use the implemented APIs, not marketplace-install edits:

1. Bootstrap the chosen composition and AI-state mode, then use the exact
   published `versionId`. During configuration-only setup, an ACTION plugin may
   be deferred only for a declared `route.sourceProjection.sourceRef` belonging
   to an unavailable, explicitly required DATA install. The deferred action's
   plugin reference remains visible in
   `behaviorConfig.marketplaceRequirements.unresolvedRequiredPluginRefs`.
   It is a setup blocker, not a removed feature or a successful source sync;
   ordinary compilation does not gain this deferral. The parent initializer
   then enables the reviewed required
   DATA/ACTION installs using target-independent symbolic config/secret aliases
   only, followed by compiler resync. It does not bind an account or store real
   provider credentials. Pure compilation remains non-mutating.
2. Staff administrator reviews one exact customer deployment/version/target/
   slot/profile with
   `POST /api/customer-self-service/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals`.
   Body fields are `versionId`, `targetProfileId`, `bindingSlotId`,
   `selectedProfileId`, `config`, `accountScope`, `reviewReference`,
   `operationKey`. Config keys must exactly match the profile. `accountScope`
   is nonempty; stock `advertiserId` must equal the approved config value.
   ACTION has no account field in its manifest, so its explicit staff account
   scope is retained review evidence, not a claim of automatic remote-account
   isolation or credential access verification.
3. The customer binds that approval with
   `POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/bind`.
   Body fields are the same four scope IDs plus `approvalId`, explicit
   `expectedEntitlementRevision`, `operationKey`, `credentials`. Only the
   manifest's exact credential field names and write-only literal values are
   accepted. Never submit URLs/account changes or existing vault reference
   names through credentials; never store real credentials in global install
   config, helper inputs/output, proposal files, logs or receipts.
4. Read local evidence using
   `GET /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations`
   with all four scope IDs as query parameters. `CONFIGURED_LOCAL_CREDENTIALS`
   proves local scope/integrity/refs only, not provider health or ACLs.
5. Replays require the identical command and operation key; changed credentials
   conflict. Bindings are immutable per version/target/slot. Rotate through a
   new immutable version and a fresh scoped review/binding. Production needs
   its own approval and credentials, including after promotion. Revocation is
   the separate `/external-integrations/revoke` command with exact binding and
   entitlement revisions; it retains evidence/data and does not revoke a remote
   provider key or certify deactivation of a running release.

Immutable compiler evidence is checked for exact profiles, fingerprints and
aliases, including missing/duplicate/extra markers. Removing a marker cannot
disable the binding requirement. Apply and workers recheck scoped evidence;
release generation uses the frozen authority-bound snapshot and typed secret
environment map, never a name heuristic or global fallback. See the
[generic API contract](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/externalintegration/README.md).

## AI State Storage Choice

Optional LoomAI-hosted AI-state PostgreSQL is owner-approved. Creation must
explicitly select `AI_STATE_STORAGE_MODE` / `aiStateStorageMode`, persisted in
the immutable draft/version provider configuration. The reviewed choices are
`MANAGED_POSTGRES` and `CUSTOMER_POSTGRES`; the latter requires the parent-owned
real typed PostgreSQL binding, not a JDBC environment overlay. Durable AI
orchestration, budget, receipt and other required operational state must not be
dropped or replaced by ephemeral storage.

Because both modes are offered, the candidate always declares exactly one
unmapped slot `{id: "ai-state-database", kind: "CUSTOMER_AI_STATE",
supportedProfileIds: ["CUSTOMER_POSTGRES"]}`. For customer SQL use
`POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/ai-state-storage-binding`
with `expectedEntitlementRevision`, `operationKey`, `targetProfileId`,
`bindingSlotId: "ai-state-database"` and nested `connection` matching the typed
SQL request. Caller `approvalReference` must be absent/null: the server derives
it. Exact published mode/version/target, host/TLS and ownership validation are
mandatory. Do not select an admin-only API or a mutable JDBC overlay instead.
Target-local SQL credential rotation and readback remain publication gates;
declaring the slot is not evidence those gates have passed.

`Definition.aiStateStorageTopologies` has exactly the two setting choices as
keys. Each value is a complete `ReviewedTopology`. `Definition.topology` is
their component-wise maximum; `limits` remains the reviewed cap. Admission must
use the immutable version's actual mode, never charge the maximum as if it were
an actually provisioned hosted database for BYO.

Explicit capacity components:

- Runtime: 1,000m CPU / 1,024 MiB memory.
- Connector: 250m / 768 MiB; vectorization runner: 250m / 768 MiB.
- Only managed AI PostgreSQL: 250m / 512 MiB / 2,048 MiB disk, one database.
- Only selected managed Inbox: 250m / 512 MiB / 4,096 MiB disk, one dependency.
- Both modes retain two derived vector indexes; an external database is zero
  hosted databases, not absence of AI durability.

Without managed Inbox, managed SQL totals 1,750m / 3,072 MiB / 2,048 MiB and
BYO totals 1,500m / 2,560 MiB / zero hosted disk. Selected managed Inbox adds its
explicit component to both modes. These are source-reviewed demand proposals,
not measured headroom, policy approval or an authority to release earlier holds.

## Publication Gates

`buildOfferProposal()` returns `publishable=false`, a typed definition candidate,
the scoped binding protocol and unsatisfied verification gates.
`buildOfferDefinition()` and the offer publisher still fail before any request;
there is no caller success flag to bypass the gates. Implemented API/enum/slot
support is not missing anymore, but it is not sufficient publication evidence.
The remaining generic gates are
`REAL_V196_BOOTSTRAP_BINDING_COMPILER_INTEGRATION_VERIFICATION_REQUIRED` and
`AI_STATE_STORAGE_TARGET_ROTATION_VERIFICATION_REQUIRED`.

Parent's later actual Spring/PostgreSQL V200 run passes19 cases without skips:
three template/customer workflows and16 SQL wrapper persistence cases. It uses
normal staff offer publication/invitation, customer acceptance/bootstrap for both
explicit storage modes, customer S3 binding, immutable version publication,
compiler provenance and scoped external approval/binding/replay. Provider clients
are mocked effect boundaries; synthetic reviewed hosts/targets are not hosted
access proof. Separate245-case SQL verification covers immutable credential
generations and low-level privilege/TLS checks. Counts overlap earlier runs.

The source publication helper remains deliberately checkpointed pending the
separate hosted target-local credential-generation and attended rollout proof.
Local merged ownership/lifecycle verification now passes3,343 tests across350
classes with zero failures/errors/skips. Independent review found a worker
revocation race and missing external checks in recovery; the source fixes and
their40-case actual PostgreSQL concurrency/revocation qualification also pass
without failures/errors/skips. Coolify and
optional managed-Inbox resource creation/deletion must use exact durable ownership
proof, not names, descriptions or historical handles. Do not treat the older
blocker names as evidence that the already tested customer commands are absent,
or remove them solely because APIs/enums exist. The local customer-command gate
is proven; its old blocker name is not a claim that those APIs remain absent.
Hosted apply, indexing, provider
auth, capacity, production promotion and final UI remain separate gates.

The ordinary external-Inbox variant has the additional
`EXTERNAL_INBOX_TARGET_BINDING_REQUIRED` blocker. Generic B supports DATA/ACTION,
not the INTEGRATION plugin's target callback/operator authority. Its handover
features remain declared rather than silently dropped. Do not invent a third
external profile or reuse managed-only Inbox enrollment to make it pass.
The explicitly selected managed-Inbox variant instead retains its real
`INBOX_OPERATOR` slot and separately reviewed AI-only dependency spec. That
spec's supported publication helper does not publish or approve the offer.

Bindings must pin customer, tenant, deployment, immutable version, exact target,
reviewed endpoint/resource and target-owned secret references. No global secret
fallback, first/latest target inference, shared customer credential selection
or cross-tenant vector reuse is authorized. Static identifiers are not evidence
of provider access or owner approval. Live provider checks remain separate from
local credential/configuration readiness.

The optional Inbox artifact uses only the dedicated capability schema:
`loomai-managed-dependency-artifact/v1`, with nested
`loomai-managed-dependency-contract/v1`, purpose `AI_OPERATIONAL_SERVICE`,
ownership `DEPLOYMENT_OWNED`, persistence `AI_OPERATIONAL_STATE`, service contract
`BASIC_INBOX_KEYS_V1`, initializer `inbox-callback-canary/v1`. Digest, commit,
normalized manifest hash and staff promotion must all match. It has no invented
`aiFabricVersion`. Its six connector bindings retain separate target callback
signing and Inbox provider-webhook signing authorities.

## Local Verification

```sh
node --test verification-support/productization-01036/test/simulated-dealership-managed-spec.test.mjs verification-support/productization-01036/test/simulated-dealership-offer.test.mjs
mvn -o -f /private/tmp/01044-catalog-sidecar/pom.xml -Dtest=DealershipExternalCatalogPostgresTest test
```

The isolated local Maven harness points to
`test/java/com/ai/fabric/platform/backend/deployment/DealershipExternalCatalogPostgresTest.java`.
It exercises the real Flyway chain through V196, historical-row preservation,
all five new manifests with the production parser, action/security semantics,
the real Inbox spec/ownership-manifest normalizer, and JS-generated external
profiles from actual V196 manifests against the production Java policy and
exact compiler evidence. This narrow profile test uses synthetic targets/hosts
and stubbed repositories backed by those real manifests, not a hosted bootstrap.
Docker absence fails the
test rather than silently skipping it. Output stays in the sidecar directory;
no shared Maven build output or hosted/provider/model service is used.

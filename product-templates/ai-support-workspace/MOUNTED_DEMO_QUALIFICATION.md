# Existing Mounted-Document Demo Contract

For the internal/small Acme **non-production hosted proof only**. This reuses an
existing connector and reviewed DATA plugin. It adds no storage implementation,
customer bucket, production service or managed customer-storage claim. The
generic `mkp-template-ai-support-workspace@1.0.0` S3 dependency stays unchanged.

## Existing Plugin

Source-reviewed catalog entry: `mkp-data-document-knowledge-mounted-demo@1.0.0`,
plugin type `DATA`, release channel `DEMO`. Confirm the actual hosted catalog
contains that exact published version before installing:

```http
GET /api/marketplace/plugins/mkp-data-document-knowledge-mounted-demo/versions/1.0.0
```

The existing source contract is seeded in migration V148; inspect it as evidence,
do not execute SQL to create installs or change its manifest. It defines:

- Deployment target `custom-start-from-scratch`.
- Auth modes `PRIVATE_RUNTIME_BACKEND_MEDIATED` and `PUBLIC_RUNTIME_AUTHENTICATED`,
  **not anonymous knowledge access**.
- Install field `documentStorageBindingRef` (required text; an owned binding ID).
- Dataset `document-knowledge`, entity `document`, `EXTERNAL_DOCUMENT_STORAGE`,
  `CUSTOMER_MANAGED`, `DEPLOYMENT_ONLY`, `VERSIONED_REPLACE`.
- Handle template `documents/{deploymentId}/document-knowledge`.
- `sourceConnector.connectorType=MOUNTED_FOLDER`, operations `LIST,READ,HEAD`,
  `deleteSourceOnRemoval=false`.
- `.txt`/`.json`, `text/plain`/`application/json`, at most 1 MiB per source,
  100 sources and 100 MiB indexed total. Initial indexing requires confirmation;
  trusted automatic indexing is disabled.
- Allowed source metadata: `originalFilename`, `locale`, `sourceCategory`.
  Do not send this package's S3-only `publicationStatus` metadata to that older
  plugin. Its existing knowledge source filters by `datasetId=document-knowledge`.

The legacy `CUSTOMER_MANAGED` enum is its existing manifest classification, not
evidence that this fixture proves customer S3 ownership or a managed storage SLA.
Label the actual source as an **operator-mounted demo guide set** in evidence.

## Non-Production Prerequisites

Both the deployment's logical `environment` **and** the selected target profile
must be non-production. The target must use Coolify. A production deployment
pointed at a staging target is still rejected by the existing binding/provider
checks. Do not weaken those checks or relabel a production deployment to pass.

The generic documents-only canary may still use normal `POST /api/deployments`
with `custom-start-from-scratch` and behavior `CONVERSATIONAL`. Do not recreate
or change a currently applying generic canary to add external checks.

The connected internal/Acme canaries must instead use the existing **normal
offer bootstrap** below. A manual deployment has no offer/profile association,
so installing a managed URL placeholder alone cannot project its endpoint.

Do **not** bootstrap the generic S3 support template and silently replace its
required DATA dependency. This canary proves runtime specialist/knowledge/Desk
mechanics, not the unchanged S3 template's bootstrap or production readiness.
Customer-storage qualification remains outstanding until owner storage is ready.

## Connected Mechanics Offer Bootstrap

Publish the corrected ACTION `1.0.1`, then its separate TEMPLATE candidate:

- Internal: `mkp-template-loomai-support-mounted-mechanics@1.0.0` requires
  `mkp-action-loomai-support-diagnostics@1.0.1`.
- Acme: `mkp-template-acme-support-mounted-mechanics@1.0.0` requires
  `mkp-action-acme-cloud-support-reads@1.0.1`.
- Both also require the existing mounted DATA `@1.0.0`, use channel `DEMO`,
  empty ACTION install defaults and no S3 or managed business-service dependency.
- Assistance starts disabled until DATA is bound/enabled; use the existing
  variant overlay afterward. This avoids compiling document/tool authority
  before its owned installs are configured. Neither template grants caller scopes.

Create a staging-only reviewed offer through `POST /api/customer-self-service/staff/offers`.
Its `definition.plugins` must exactly match the TEMPLATE's required plugins.
Use exact published version IDs/hashes, promoted existing artifact pins, the
current staging target fingerprint and reviewed `limits`/`topology`/policy refs.
Include a `DEPLOYMENT_NAME` TEXT setting and an `EXTERNAL_SERVICE` binding slot
whose `pluginId` and `supportedProfileIds` match `externalIntegrationProfiles`.
For this non-S3 canary, `documentAudiences` is empty: the current offer validator
requires a CUSTOMER_S3 slot for nonempty audience choices. Normal document
knowledge APIs still provide the mounted indexing workflow.

The helper declares the connected journey, not just its bootstrap HTTP calls:
`TEMPLATE_SELECT`, `BOOTSTRAP_CONFIGURATION`, `READ`, `DRAFT_EDIT`,
`VERSION_PUBLISH`, `BINDING_REPLACE`, `APPLY`, `VERIFY`, `SOURCE_DISCOVER`,
`SOURCE_PREVIEW`, `SOURCE_INDEX`, `SOURCE_REFRESH`, `APP_CONFIGURE`,
`AUTH_CONFIGURE`, `DOMAIN_CONFIGURE`, `ACTIVATE`. No legacy BOOTSTRAP,
per-deployment Inbox, recovery, promotion, revoke or source-removal operation is
needed for this happy-path staging proof. Those are not inferred permissions.

Used setting names are `DEPLOYMENT_NAME`, `DOCUMENT_AUDIENCE`, `APP_TITLE`,
`INTERACTION_PROFILE_ID`, `THEME_PROFILE_ID`, `AUTH_PROFILE_ID`, `APP_DOMAIN`.
Only deployment-name bootstrap needs an offer setting rule. Current
`authorizeExisting` and `validateSettings` do not enforce historical offer
operation/settings pins for an existing owned deployment. Later source audience,
workspace/theme/auth payloads and origins are validated by their actual owning
contracts. Do not copy stale profile hashes, model/image restrictions or S3
audience choices from another offer just to allow this journey. Exact reviewed
external profile/version/endpoint/credential checks remain separately enforced.
For mounted `SOURCE_INDEX`, the actual source's authenticated/private auth modes
provide the explicit `AUTHENTICATED` audience; current authorization still checks
the owned target/release and unchanged storage binding. An empty bootstrap
audience list is not itself an authorization grant or hosted indexing proof.

Enable Desk has no `DESK_ENABLE` offer enum: it requires an actual Supabase-linked
customer owner, an owned applied target with a connector, a clean draft and a
configured shared Desk service. Its existing service installs handover/Desk,
then uses normal publish/apply; there is no extra Inbox or recovery permission.
Use `enableReviews:false` for this read-only mechanics proof. Desk staffing still
requires current Desk permissions, not an offer operation or LLM-supplied role.

Each profile uses schema `loomai-external-integration-profile/v1`, its exact
ACTION version/hash and staging target. Config and endpoint fields are all of
that ACTION's URL fields; secret fields are its one `secretRef` field.
`accountScopeFields=[]`; `allowedHosts` contains only its approved existing host.
Do not mix internal and Acme actions, customers or upstream credentials.

Invite the existing owner through `/api/customer-self-service/staff/offers/{offerId}/invitations`,
accept through `/api/customer-self-service/invitations/{id}/accept`, then use:

```http
POST /api/customer-self-service/catalogue/{offerId}/deployments

{"targetProfileId":"OWNED_STAGING_TARGET","name":"Reviewed staging mechanics","expectedEntitlementRevision":2,"operationKey":"STABLE_OPERATION_KEY"}
```

Use the actual accepted entitlement revision, not the example number. This
creates owned **configuration only**, records the offer association and runs
the normal external install initializer. It does not apply infrastructure.
Do not reuse the old offer-less Acme deployment by writing an association row.

Main's private operational helper is
`~/.codex/private/loomai-support-01054/prepare-external-canaries.mjs`.
It performs no live call on import or CLI dry-run. Its imported execution
function accepts main-owned authenticated `staff(path, options)` and
`customer(variant, path, options)` clients plus a private journal/save hook.
Only metadata GETs and offer/invite/accept/configuration-bootstrap POSTs exist;
there is no credential, storage, release, SQL or provider operation.

Required helper configuration: `environment:staging`, `targetProfileId`, existing
`additionalArtifactIds` (existing non-runtime artifacts), `reviewReference`, fixed `invitationExpiresAt`, `policy` containing
`limits`, `topology`, `approvedPolicyRef`, `operatingPolicyRef`, `identityPolicyRef`,
`storagePolicyRef`, and `variants` with `variant:loomai|acme`, fresh `offerId`,
`name`, existing `customerId`, `ownerUserId`, and exact `allowedHosts`.
Limits/topology must come from actual reviewed values; the helper invents none.
The runtime is selected at invocation from main's current
`runtime-artifact-staging.private.json` and its immutable image/source/manifest
identity must match API readback. There is no fixed source commit or runtime
artifact constant. Main owns runtime qualification and updates this record only
when ready; `candidateOnly` is not interpreted as a hosted qualification claim.
Offline `--plan config.json metadata.json` accepts saved actual API summaries:
`metadata.target`, `metadata.artifacts`, `metadata.versions[pluginId@version]`.
The live execution function obtains those same summaries through existing APIs.
An uncertain immutable offer creation requires manual outcome inspection; it is
not blindly retried. Later idempotent commands retain stable operation keys.

After bootstrap, bind/enable the existing DATA install as below, apply the
reviewed specialist and identity overlay, and publish the version normally.
Then staff calls
`/api/customer-self-service/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals`
with version/target/slot/profile, exact URL `config`, nonempty reviewed
`accountScope`, review reference and operation key. Customer calls the matching
`/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/bind`
with approval ID, current entitlement revision, operation key and literal
write-only credential. Use the existing dedicated credential, never model input
or a browser-held key. Normal apply freezes and projects those values.

These source/helper changes alone do not publish the new candidates, set up the
fixed internal support binding, prove endpoint reachability, populate a mount,
index documents, activate Desk or qualify storage. Record those separately.

## Bind And Install Through Existing APIs

```http
POST /api/deployments/{deploymentId}/document-storage-bindings
Content-Type: application/json

{"targetProfileId":"OWNED_COOLIFY_STAGING_TARGET_ID","connectorType":"MOUNTED_FOLDER","mountedRoot":"/app/document-sources"}
```

Record the returned binding ID. It must belong to this deployment and exact
target. No S3 endpoint, access key or bucket is supplied.

```http
PUT /api/deployments/{deploymentId}/marketplace-installs/{existingMountedDataInstallId}
Content-Type: application/json

{"pluginVersion":"1.0.0","status":"ENABLED","config":{"documentStorageBindingRef":"RETURNED_OWNED_BINDING_REF"},"secretRefs":{}}
```

Get the existing install ID from `GET /api/deployments/{deploymentId}/marketplace-installs`.
For a manual documents-only canary with no install yet, normal POST installation
remains available. The connected TEMPLATE already installed DATA: do not create
a duplicate. Use normal owned authorization and operation/idempotency handling, not a
customer S3-offer bootstrap bypass. Validate, publish and apply the resulting
configuration through the existing release mechanism. There is one document
source binding per runtime; do not combine mounted and S3 DATA installs in the
same canary as a hidden storage fallback.

## Existing Mount And Indexing Behavior

Coolify provisioning reconciles a host directory derived from the deployment
and target IDs:

```text
/srv/loomai/document-sources/{deploymentId}/{targetProfileId}
  -> runtime container /app/document-sources
```

An authorized operator must place only the selected variant's approved `.txt`
files there and make them readable by runtime UID `10001`. Provisioning creates
the mapping, **not the document contents**. Do not bake the guides into each
runtime image or put them in the Acme fixture service's filesystem and assume
the runtime sees them. Verify the actual mapping and OS permissions. The source
connector performs read-only operations; the Coolify mapping itself is not a
claim that the container mount has an OS `read_only` flag.

The existing plan projects `LOOMAI_DOCUMENTS_CONNECTOR_TYPE=MOUNTED_FOLDER`,
binding reference, dataset IDs/handle refs and `LOOMAI_DOCUMENTS_MOUNTED_ROOT`
from the target binding. Let normal provisioning generate these values; do not
patch a deployed runtime environment by hand.

Then use the existing document-knowledge operations on the **exact target and
release**: connector status, discover, register source, preview, explicit index,
reconcile completion and retrieval proof. The normal source revision/hash rules
and tenant/deployment filters still apply. This is not raw vector import.

Keep anonymous grants chat-only. Sign in through the documented generic/Acme
Supabase broker before attempting the mounted plugin's knowledge/specialist
proof, or use the internal authorized case adapter. Record actual source IDs,
work IDs, release/hash readback and observed citations. No hosted success is
claimed by this note; no new mount or deployment was created by this task.

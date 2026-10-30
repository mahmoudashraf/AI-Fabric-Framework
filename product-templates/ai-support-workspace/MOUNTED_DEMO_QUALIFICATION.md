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

Use a separate operator-owned staging canary with `custom-start-from-scratch`
and behavior `CONVERSATIONAL`. The normal `POST /api/deployments` accepts these
fields alongside the actual owned `customerId` and `tenantId`. Install this
existing mounted DATA plugin and, for Acme, its reviewed ACTION plugin, then
apply the same generic/Acme specialist overlay through the normal draft API.

Do **not** bootstrap the generic S3 support template and silently replace its
required DATA dependency. This canary proves runtime specialist/knowledge/Desk
mechanics, not the unchanged S3 template's bootstrap or production readiness.
Customer-storage qualification remains outstanding until owner storage is ready.

## Bind And Install Through Existing APIs

```http
POST /api/deployments/{deploymentId}/document-storage-bindings
Content-Type: application/json

{"targetProfileId":"OWNED_COOLIFY_STAGING_TARGET_ID","connectorType":"MOUNTED_FOLDER","mountedRoot":"/app/document-sources"}
```

Record the returned binding ID. It must belong to this deployment and exact
target. No S3 endpoint, access key or bucket is supplied.

```http
POST /api/deployments/{deploymentId}/marketplace-installs
Content-Type: application/json

{"pluginId":"mkp-data-document-knowledge-mounted-demo","pluginVersion":"1.0.0","config":{"documentStorageBindingRef":"RETURNED_OWNED_BINDING_REF"},"secretRefs":{}}
```

Use the normal owned authorization and operation/idempotency handling, not a
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

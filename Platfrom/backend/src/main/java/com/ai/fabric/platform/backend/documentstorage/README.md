# Customer Document Source Storage

010.59 adds optional hosted source storage above existing target document
bindings and runtime document operations. It does not implement another document
engine, vector database, application database or storage microservice.

## Resource Ownership

- `DocumentStorageConnectionEntity`: customer/tenant-owned shared bucket and root.
- `DocumentStorageUploadEntity`: confirmed immutable object reference/checksum,
  operation state and safe metadata. No document content is stored in Platform.
- `DocumentStorageSelectionEntity`: exact deployment/target/release registration
  and indexing receipt. It is not a duplicate runtime ingestion manifest.
- Normal provider handles and target-managed secret references project the
  connection into each runtime's existing `slot:document-storage`.

Connection/upload rows survive deployment removal. Target selection receipts are
removed with the deployment. No removal API in this package empties S3, deletes
a source object or changes a shared bucket's lifecycle.

## Backend-Only Provider Configuration

Use `LOOMAI_DOCUMENT_STORAGE_ENABLED`, `LOOMAI_DOCUMENT_STORAGE_REGION`,
`LOOMAI_DOCUMENT_STORAGE_ACCOUNT_ID`, `LOOMAI_DOCUMENT_STORAGE_CONTROL_PLANE_ID`,
`LOOMAI_DOCUMENT_STORAGE_BUCKET_NAMESPACE`,
`LOOMAI_DOCUMENT_STORAGE_AWS_ACCESS_KEY_ID`,
`LOOMAI_DOCUMENT_STORAGE_AWS_SECRET_ACCESS_KEY` and
`LOOMAI_DOCUMENT_STORAGE_READER_PERMISSIONS_BOUNDARY_ARN`.

Credentials belong only on the trusted control-plane backend. Never register
the provisioner key as a customer deployment secret or put it in a manifest,
public bootstrap response, browser asset, runtime or connector. Disabled hosting
does not initialize AWS clients or disable existing BYO connections.

Generated readers use the configured read-only S3 permissions boundary and a
separate exact bucket/prefix inline policy. The provider must have only the
reviewed namespaced IAM reader-management permissions. Missing permissions are
an external prerequisite, not permission to broaden IAM automatically.

## API

Base: `/api/deployments/{deploymentId}/document-storage/connections`.
The controller allows authenticated customer administrators and Platform
operators/administrators; service methods independently resolve the deployment
and exact customer/tenant ownership. No anonymous or public-API upload route.

- `GET /`: availability and owned connections.
- `POST /`: configuration-only create with name/operationKey; no AWS transaction.
- `POST /{id}/prepare`: private versioned bucket preparation and readback.
- `POST /{id}/bind`: target and relative purpose folder; scoped read-only reader.
- `GET /{id}/uploads`: source receipts, not an indexing-success claim.
- `POST /{id}/uploads`: bounded raw UTF-8 TXT/JSON body, with filename/prefix/
  operationKey query parameters. Controller reads at most 1 MiB plus one byte.
- `POST /{id}/prepare-selection`: explicit target/release/dataset, exact upload
  IDs, public approval and operationKey. Uses existing register/preview/index/
  reconcile APIs, preserving normal runtime authority and source scope checks.
- `GET /{id}/selections`: last recorded target/release receipts. A fresh
  prepare/reconcile checks actual runtime state; the database is not a health probe.

Idempotency prevents changing a payload behind an operation key. Source keys are
generated server-side and conditional object writes verify checksum/size/version.
No model chooses a recipient, file key, storage authority or publication status.

## Promotion

The existing promotion candidate freezes reviewed upload IDs and checksums,
not vectors. Production must bind the same connection with a readable purpose
scope and index the selected originals in its own runtime. Activation checks
the exact production source receipts and current runtime Active state, in
addition to existing release verification and assignment checks.

Uploading does not publish, index, activate, invalidate the connection's unrelated
targets or silently consume staging-only files. Replaying an unchanged hosted
binding preserves the frozen handle/secret timestamps.

## Verification And Bounded Operational Limits

Run provider, storage/selection/promotion, binding/resolver and document-contract
tests, including PostgreSQL migration/isolation checks. Frontend tests cover
Kit draft setup, raw uploads, readback and stale-scope protection. Actual AWS
IAM/S3, hosted runtime retrieval and production activation require separate live
evidence in the 010.59 execution record.

An ambiguous IAM key issuance or crash between issuance and secret persistence
requires operator reconciliation; the provider never silently issues/deletes
another key. An abandoned PREPARING/UPLOADING operation also requires inspecting
the provider receipt before resetting its recorded state. These bounded recovery
limits are explicit, not another drain/recovery subsystem.

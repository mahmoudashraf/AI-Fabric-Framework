# Fresh Qdrant Staging Planning Helpers

Pure, offline preparation only. No HTTP/provider calls, auth reads, key generation,
artifact registration/promotion, execution transport, retries, build or deployment.
Main owns fresh reads, policy/accounting, publication and the sequential hosted journey.

## Frozen Scope

- Derive the original published managed-Inbox template `1.0.0` into a new immutable
  **`1.2.0` version of the existing publisher-owned family**, not an update of `1.0.0`/`1.1.0`.
- Family `mkp-template-dealership-external-systems-managed-inbox-v1`, base `dev-openai-qdrant`.
- Preserve required plugin refs/defaults, behavior, public-document policy and managed PG/Inbox contract.
- Qdrant is `EXTERNAL_EXISTING` / `SHARED`, `qdrantManagedCollectionsEnabled=false`, REST only.
  Host and ports are reviewed public template configuration; no raw key or prefix is published.
  The existing resolver purpose is exactly `QDRANT_API_KEY`; it is not a new manifest field.
- Runtime `dsa-6776d0bf`, source `d8743ee3b66307b4bb26645023b11f0dec64127f`,
  digest `sha256:a32574d477a1330550d3bb8da3fb4a1a5edddcf13117538736bcbc9c0dace677`,
  framework `0.8.36`, native artifact promotion channel **staging** only.
- Inbox `dsa-1dc83268`, additionally checked by the existing exact Inbox image/ownership validator.
- Only `dtp-coolify-staging-behavior` / `CUSTOMER_STAGING`; no production permission or
  `PROMOTION_PLAN`/`PROMOTION_APPLY`. Managed AI-state PG plus Inbox PG: 2250m CPU,
  4096 MiB RAM, 6144 MiB disk, 2 DBs, 2 indexes, 2 dependencies, 1 concurrent apply.

## Exports And Inputs

Import from `./fresh-qdrant-staging-package.mjs`. All functions return values and never execute requests.
All metadata below must be actual normal API readbacks for publication; test fixtures are synthetic.

```js
reviewedVector = {host, port, grpcPort, preferGrpc: false, apiKeySecretName: "QDRANT_API_KEY"}
// host is a complete HTTPS origin. For REST 6333 it MUST include :6333.
// Example observed origin supplied by Main:
// https://8ad5ab4f-a514-49ec-b599-742922b34a3a.eu-west-1-0.aws.cloud.qdrant.io:6333

buildFreshQdrantStagingTemplateManifest(sourceTemplate, reviewedVector)

planFreshQdrantTemplatePublication({sourceTemplate, reviewedVector, publisher,
  submission, reviewReference}, phase)
// phase: template-submit | template-validate | template-publish
// publisher: {id, status: "ACTIVE", verificationStatus: "VERIFIED", ...nativeMetadata}
// submit requires submission:null; later phases require actual native submission with
// pluginVersionId, publisherId, pluginId, pluginSlug, version, releaseChannel,
// status (SUBMITTED then VALIDATED), manifest, bundleSha256. No native ID is invented.

planFreshQdrantStagingSpec({sourceTemplate, reviewedVector, template,
  inboxArtifact, specId, reviewReference})
// template: actual PUBLISHED 1.2.0 version with native mkv-ID and publishedAt.
// specId: new reviewed identifier, not the old immutable spec ID.

buildFreshQdrantStagingOffer(offerInput)
planFreshQdrantStagingOffer({offerId, ...offerInput})
```

`sourceTemplate` is the exact PUBLISHED original `1.0.0` native version
`mkv-template-dealership-external-systems-managed-inbox-v1-0`, including its manifest.

`offerInput` has exactly these fields:

```js
{sourceTemplate, reviewedVector, template, pluginVersions, artifact, target,
 inboxArtifact, inboxSpec, specId, configuration, review,
 behaviorCatalog, executionExtensionCatalog}

configuration = {aiStateStorageMode: "MANAGED_POSTGRES",
 auth: {projectUrl, publishableKey, dealerId, imageHostAllowlist},
 externalIntegrationAllowedHosts: {"vehicle-stock": [...hosts], "business-actions": [...hosts]}}
review = {name, reviewReference, approvedPolicyRef, measuredPolicyRef,
 operatingPolicyRef, identityPolicyRef, storagePolicyRef,
 stagingExpiryHours, maxExtensionHours, recoveryWindowHours} // integers 1..24
inboxSpec = {publication: {templateVersionId, specId, fingerprint, reviewReference, reviewedAt}, spec}
```

`artifact` is the latest native runtime artifact above, with capability manifest/hash,
promotion/build metadata. `target` is the full staging profile, including resource defaults,
credential reference, provider/network config. `pluginVersions` contains the six exact
existing published plugin pins. Catalog inputs are actual `/api/deployment-behaviors` and
`/api/deployment-execution-extensions` readbacks. `inboxSpec` must be read back after normal
publication against the **actual new template ID**; no backend fingerprint is fabricated.

Descriptors contain the exact normal POST route/body and hashes, plus
`liveReadinessProven:false`, `freeQuotaVerified:false` and remaining main-owned gates.
They are not an execution authorization or a journal/transport implementation.

## Native Compatibility And Remaining Gates

`CustomerOfferContracts.Setting` and customer bootstrap have no Qdrant provider-edit setting.
The revised owner-approved solution is the immutable template endpoint pin, not an API bypass:
`MarketplaceManifestService` allows all emitted provider fields; the ordinary runtime plan
uses `QDRANT_API_KEY` without serializing a secret reference/value into the manifest.
Customer bootstrap uses `bootstrapConfiguration`, then `createDeploymentConfiguration(...false)`
which does not seed a managed vectorization plan. The template baseline overrides the base
Qdrant managed defaults before publication/apply. EXTERNAL_EXISTING/false makes the managed
provisioner inert, avoiding the separately identified targetless managed-provisioning blocker.

Target prefix remains server-derived from customer/tenant/deployment/target by
`TenantScopedVectorHandleResolver`; the template cannot choose it. Old Milvus/dual-target
helpers are untouched. No old release, reservation, allocation, history or provider data is changed.

Still unproven by these pure helpers: current free quota/write permissions, exact runtime
prefix/readback, actual hosted stock/document indexing and retrieval, PG/Inbox and owner-UI
acceptance, fresh capacity/admission and financial authority. No migration, deletion, new
account/paid service, production successor or production-cluster approval is implied.

## Focused Qualification

```sh
node --test verification-support/productization-01036/test/fresh-qdrant-staging-package.test.mjs
```

8 tests PASS, 0 failures/errors/skips, exit 0. Covers immutable derivation, endpoint/port/key
purpose constraints, exact publisher phases, actual-ID spec linkage, stage-only latest-artifact
offer bodies, scope/artifact/spec/configuration denials, and static native-schema/bootstrap wiring.
Static source assertions and synthetic metadata are not a hosted integration qualification.

The same command with `test/managed-postgres-inbox-spec.test.mjs` and
`test/managed-postgres-inbox-offer.test.mjs` also passed: **26 tests total, 0 failures/skips,
exit 0**. The original spec and Milvus/dual-target helper contracts were not edited.

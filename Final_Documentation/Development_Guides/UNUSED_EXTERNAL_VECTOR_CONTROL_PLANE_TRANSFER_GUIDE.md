# Unused External Vector Control-Plane Transfer

This is a narrowly scoped Platform operational procedure for releasing an entirely unused external Zilliz Free/Serverless cluster reference in one control plane (CP) and claiming it in another. It is not data migration, customer data sharing, provider ownership transfer, provider credential revocation, or an exception to the ordinary local binding rules. Source qualification does not prove hosted rollout readiness.

Only explicit `PLATFORM_ADMIN` principals authenticated by a staff session or staff API key may use these routes. Customer, operator, public API, product-service, and consumer-runtime authentication do not authorize this procedure.

## Trust And Server Identity

Each CP must have server-owned deployment configuration:

```properties
platform.external-vector-transfer.control-plane-id=source-cp
platform.external-vector-transfer.control-plane-origin=https://source.example.test
platform.external-vector-transfer.peer-origins={"destination-cp":"https://destination.example.test"}
```

Configure the destination with its own distinct ID/origin and exact source peer. Origins are canonical lowercase HTTPS origins without a path, trailing slash, port, query, credentials, or fragment. IDs are stable deployment identities, not request-selected labels. Missing or mismatched configuration fails closed. V205 does not seed identities, peers, approvals, or historical ownership.

The trust anchor is the authorized staff coordinator who authenticates independently to both configured CPs. The coordinator retrieves the source receipt over the configured source CP's authenticated TLS API, independently reviews its exact scope and terminal state, and enrolls the exact response bytes at the destination. A SHA-256 digest identifies evidence; it does not authenticate a source or replace this staff review. No unauthenticated customer attestation, arbitrary remote URL fetch, or synthetic cancellation receipt is accepted as a substitute. This protocol does not implement cryptographic federation.

The destination persists the exact source receipt JSON, exact authenticated response JSON and digest, retrieval provenance reference, source fingerprint, review time, reviewer actor, and audit. The provenance reference must identify the coordinator's retained authenticated retrieval evidence, not merely repeat a digest. Protect that evidence according to staff operational policy; never include authentication headers or secrets in the reference.

## Initial Eligibility

- The complete local historical binding set for the cluster must be supplied with exact metadata fingerprints. Historical `RETAINED_UNUSED` sources require authentic committed `CANCELLED_UNUSED_NO_EFFECT` receipts, matching events and released reservation fences. An `ACTIVE` binding with no reservation history uses genuine binding-only release; the protocol never invents a reservation or cancellation.
- All historical sources must preserve their actual published version/configuration, customer, tenant, target, provider identity and original metadata. The destination CP/customer/tenant/deployment/target/version/configuration and derived collection prefix are pinned before source release commits. Every old prefix must differ from the destination prefix.
- Releases, managed deployment/resource effects, budget allocations, new reservations, retirement/deletion operations, or bound consumers block unused release. Consumers are never implicitly detached. The explicit configuration-only exception below requires exact approved consumer/draft expectations. Used bindings are not reclassified as unused.
- Required retirement gates remain available and unchanged. No source ownership row or historical metadata is rewritten.
- The destination must have its exact published version, active target, compatible external-existing provider mode and independently derived prefix. Existing destination-local binding history is not bypassed.

## Explicit Unused Draft Detachment

A reviewed deployment may have a configured consumer and DRAFT workspace before
its first admission or runtime exists. Its generic assignment API still refuses
an unbind: do not disable that guard, fabricate a cancellation, or delete the
workspace to release the unused vector reference.

For that exact case, the source-release request may include:

```json
{
  "consumerDetachments": [
    {
      "consumerEntityId": "<exact-internal-consumer-id>",
      "expectedBindingRevision": 1,
      "draftWorkspaces": [
        {
          "installationEntityId": "<exact-internal-draft-workspace-id>",
          "expectedRowVersion": 0
        }
      ]
    }
  ]
}
```

Supply this field alongside the ordinary complete release request, not as a
separate endpoint. Omitted or empty approval never detaches a bound consumer.
Missing revision fields, duplicate keys, unknown fields and coercion reject.

The primary binding must be ACTIVE, have no release or reservation history and
have one locked AVAILABLE target gate. All other existing no-effects checks
remain mandatory. The server derives customer/tenant/deployment/target scope
from that binding, verifies the original reviewed consumer-creation command and
sole initial assignment history, and requires the complete unchanged consumer
and never-activated DRAFT workspace set. Previously active, disabled, foreign,
missing or newly added workspaces reject; this cannot drain a used deployment.

The owning consumer service appends revision/history evidence in the SAME
transaction as the unused vector release, under the admission mutex and target
locks. Workspace/draft/version rows remain unchanged. The sealed release receipt
includes `consumerDetachments` with the actual history IDs and preserved draft
expectations. A changed command cannot reuse its operation key. Any validation,
audit or ledger failure rolls back the detachment, history and release together.
Other historical source bindings still must be unbound independently; this
approval applies only to the primary binding's configuration-only target.

## Empty Inventory And Credentials

The full-cluster proof for Zilliz Free/Serverless is an exact DescribeCluster identity plus an empty list of collections in `default`. These offerings have only the default database. Do not require the Dedicated-only ListDatabases operation. Dedicated transfer is outside this narrow path.

The coordinator must explicitly review that the inventory credential has full visibility for this exact cluster, retain the approved credential's actual fingerprint and review provenance, and establish an attended no-writer window covering release and claim. Current code reads the local `ZILLIZ_CLOUD_API_KEY` for this inventory. Management metadata access alone does not establish collection visibility; the review is an explicit staff responsibility. An already owner-approved broad key is acceptable when the recorded review actually covers this cluster. No paid role/cluster change is implied.

`inventoryApproval` supplies:

```json
{
  "expectedCredentialFingerprint": "<sha256-of-the-exact-reviewed-inventory-credential>",
  "fullVisibilityReviewReference": "<retained-staff-full-visibility-review>",
  "noWriterWindowReference": "<attended-operator-window>"
}
```

The proof records the actual credential fingerprint, review/window references, scope digest, endpoint, empty `default` inventory, and observation time, never the credential itself. Existing binding serving credentials are separately verified through read-only serving calls. Source release clears only locally scoped serving token or username/password copies and the local credential-fingerprint key. It does not clear the global inventory key or claim provider-side credential revocation.

The read sequence is not a provider-atomic snapshot and does not prove ACL least privilege. Keep the attended no-writer window in effect. Initial source finalization and initial destination enrollment enforce fresh evidence; first claim performs another fresh full inventory read and independent serving verification. Accepted review and committed exact replays are durable; a legitimate new destination collection does not invalidate exact claim replay. Changed credentials or commands still reject.

Provider references: [API comparison](https://docs.zilliz.com/docs/api-comparison), [List Collections V2](https://docs.zilliz.com/reference/restful/list-collections-v2), [API key management](https://docs.zilliz.com/docs/manage-api-keys).

## API Sequence

All routes are under `/api/platform/external-vector-transfers`.

1. On the source, `POST /source-releases` with `operationKey`, `approvalReference`, primary `bindingId`, exact destination `Scope`, complete `sources`, and `inventoryApproval`. Add `consumerDetachments` only for the explicitly approved configuration-only case above. A `Source` has `bindingId`, `expectedBindingFingerprint`, and nullable `cancellationReceiptId`. The destination `Scope` includes its configured `controlPlane` ID/origin, customer/tenant/deployment/target/version/config hash and derived collection prefix.
2. The source transaction checks the entire history and no-effect evidence, clears scoped local credential copies, marks all source vector handles `RELEASED_UNUSED_TRANSFER`, and writes one immutable release receipt, binding-proof set and audit. A permanent cluster tombstone prevents every source fresh bind, local handoff, resolution, or activation replay. Historical metadata and cancellations remain unchanged. There is no provider resource mutation.
3. On the source, authenticated `GET /source-releases/{id}` returns `ReleaseReadback`. Retain the exact HTTP response body. Its `releaseJson` is the exact persisted source receipt string, with a byte digest; the outer readback includes the authenticated reader actor/mode and observation time. Normal later archival does not prevent terminal readback or source exact replay. Immutable owner/version pins and terminal closure still must match.
4. On the destination, `POST /source-reviews` with a new `operationKey`, staff `approvalReference`, the exact response body encoded as `authenticatedReadbackJson`, its `expectedSourceReceiptFingerprint`, and retained `retrievalReference`. The server verifies configured source/destination identity, fingerprints, scope and byte digests before immutable staff enrollment. Review is single-source-proof enrollment, not authority to repoint the destination. `GET /source-reviews/{id}` reads that enrollment.
5. On the destination, `POST /claims` with `operationKey`, `reviewId`, `inventoryApproval`, and the ordinary existing-vector `connection` request. The connection must match the pinned version/cluster/project/region/option/database and the enrollment approval reference. Supply separately authorized serving credentials, never raw credentials inside source or review evidence. The server freshly proves emptiness, verifies serving access, performs guarded normal binding without bypassing local history, consumes the review once, and atomically writes one claim receipt/audit. `GET /claims/{id}` reads it.

Use exactly the same command and operation key for retries after uncertain responses. A changed command, credential, scope, review, or operation key cannot consume the original claim again. Receipt/readback APIs expose no raw credentials. The transfer parser rejects duplicate/unknown/case-folded fields and scalar coercion without changing global application JSON behavior.

## Evidence And Failure Handling

Source release and destination claim are separate local transactions, not a cross-CP distributed transaction. An uncertain or failed destination claim leaves the source permanently closed. Recover by reading the source/review/claim receipts and replaying the exact approved command; never reactivate the old source binding, edit SQL, or create a replacement reservation to manufacture evidence. A pinned destination that later becomes ineligible remains blocked; this path does not support repointing or undoing a terminal release.

Source and destination journals are append-only. Released vector-handle rows cannot be updated or deleted. PostgreSQL cluster serialization is shared with ordinary binds and local handoffs, including a cluster with no handle rows yet; deployment locks follow the established sorted order. Audit or ledger failure rolls back the corresponding local handle/secret changes. Model grants, financial history, provider resources, collections and source ownership remain unchanged.

Retain source release/readback, authenticated retrieval evidence, destination enrollment and claim, configured CP/peer identity evidence, full-visibility review, no-writer-window record, and post-claim readback together. Prove zero source active bindings and one destination active binding before leaving the window. Follow normal destination admission/apply afterwards; this operation creates no managed cluster, model grant, reservation, runtime, or claim of external readiness.

V202 local `handoff-unused` remains same-CP/same-customer only. V203 used external-index preservation and residual-capacity accounting are a different lifecycle, not a route for transferring used data. Never delete/drop provider data or rotate keys as part of this protocol.

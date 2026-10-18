# Private Runtime Target Retirement

App-owned R3 drain service; framework 0.8.35 retirement state remains read-only. V12
owns target retirement and V13 owns explicit same-target generation transition.

## Configuration
Provider injects exact `LOOMAI_RUNTIME_TARGET_PROFILE_ID`,
`LOOMAI_RUNTIME_RELEASE_ID`, and existing `AI_FABRIC_RUNTIME_DEPLOYMENT_ID` /
`AI_FABRIC_RUNTIME_TENANT_ID` (or configured auth-default properties). A supplied
target/release enables retirement; partial identity fails startup.
`LOOMAI_RUNTIME_TARGET_RETIREMENT_ENABLED` is an optional legacy override, never
a restore mechanism. `LOOMAI_RUNTIME_TARGET_RETIREMENT_DEDICATED_STORE=true`
attests this datasource/schema is exclusive to the configured generation;
otherwise observations remain visible but target totals are incomplete/null.
The durable singleton rejects changed target/release/tenant or topology.

The packaged manifest proves support, not enabled-capability absence. Provider
may supply a release-evidence-verified immutable certificate through
`LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_JSON` and its exact UTF-8 SHA-256 hex digest
`LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_SHA256`:

```json
{
  "contractVersion": "LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V1",
  "target": {"deploymentId": "dep-123", "targetProfileId": "staging", "releaseId": "release-1"},
  "tenantId": "tenant-1",
  "features": {
    "CHAT_SESSIONS": "PRESENT", "CONVERSATION_HANDOVER": "ABSENT",
    "HUMAN_REVIEW": "ABSENT", "SPECIALIST_EXECUTION": "ABSENT",
    "SPECIALIST_CHAINS": "ABSENT", "SMART_BRAIN": "ABSENT",
    "ACTION_WEBHOOK": "ABSENT", "INDEXING": "ABSENT",
    "DOCUMENTS": "ABSENT", "SCHEDULER": "ABSENT"
  },
  "remoteDependencies": ["CONNECTOR", "HUMAN_INBOX"]
}
```

All features and remote obligations must be explicitly certified upstream, not
caller-authored. Target/tenant/digest are verified and the digest pinned. ABSENT
must agree with explicit false enabled properties: `ai.chat.enabled`,
`loomai.conversation-handover.enabled`, `ai.execution.reviews.enabled`,
`ai.execution.enabled`, `ai.execution.specialist-chains.enabled`,
`loomai.smart-brain.enabled` (also SCHEDULER), `ai.indexing.enabled`,
`loomai.documents.enabled`. ACTION_WEBHOOK has no enable switch: upstream must
prove absence from the release's action/post-policy contract. Existing stores
are still inspected; residual rows or malformed schemas cannot become fake zero.

## Private API
All routes are POST under `/api/private/runtime-target-retirement`:
- `/status`: body `{deploymentId,targetProfileId,releaseId}`; current durable state/epoch.
- `/commands`: command below; persisted `applied`, actual `current`, and `replay`.
- `/snapshots`: body `binding`; fresh persisted counts/evidence reference.
- `/snapshots/{snapshotId}/readback`: same binding; saved evidence only while current fence matches.
- `/generation-transitions`: adopt an exact replacement release after the old
  generation is locally drained, authority-fenced, and provider stop/readback
  evidence has been recorded by the control plane.

```json
{
  "binding": {
    "target": {"deploymentId": "dep-123", "targetProfileId": "staging", "releaseId": "release-1"},
    "fenceEpoch": 1,
    "operationId": "platform-retirement-operation-id",
    "requestFingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  },
  "expectedFenceEpoch": 0,
  "type": "BEGIN_DRAIN",
  "drainDeadline": "2030-01-01T00:00:00Z",
  "externalEvidenceReference": null
}
```

Types: BEGIN_DRAIN, CANCEL_DRAIN, FENCE_AUTHORITY,
RECORD_AUTHORITY_REVOCATION, RECORD_RECLAIM_STARTED. Epoch must advance with
expected-epoch CAS. Retry requires the entire same payload digest; operation and
fingerprint cannot change mid-drain. Repeat drain cannot change deadline.
Cancellation requires DRAINING and no durable revocation/reclaim flags.
RECORD_* requires external audit reference and records an already-observed event,
not permission to perform it. FENCE_AUTHORITY requires fresh local drain proof
and parent R5 readback reference when remote obligations exist; it fences runtime
ingress/workers, not provider secrets. Deadline expiry never resolves work.

Use existing trusted API key plus signed private assertion:
`X-AIFABRIC-RUNTIME-API-KEY` and
`X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ...`. Required mode/caller are private
backend-mediated/TRUSTED_BACKEND; subject is TRUSTED_BACKEND or SYSTEM_PROCESS.
Require matching deployment/tenant and `runtime:target-retirement:read` or
`runtime:target-retirement:write`; existing issuer/audience/expiry checks apply.
Public tokens, bare keys, platform/end-user/contact identities and cross-scope
commands fail. Responses are no-store.

Generation transition is a separate write command. It binds the exact previous
and next release on the same deployment/target/tenant, previous and next
topology fingerprints, expected retirement fence, an idempotent operation and
request fingerprint, and a bounded provider stop/readback reference. The old
generation must already be `AUTHORITY_REVOKED`, with complete persisted local
drain evidence and zero unsettled permits. The new runtime may expose only this
private transition route while its environment identifies a not-yet-adopted
release; every ordinary protected route continues to fail with
`PERSISTED_RUNTIME_GENERATION_MISMATCH`. Successful adoption advances the
fence, activates the new generation, retains runtime data and audit evidence,
and causes old workers to fail on their next gate check. Exact retries replay
the receipt; changed payloads, topology, target, tenant, operation or provider
evidence fail closed. Startup variables alone never authorize adoption.

## Counts And Fencing
Metrics expose real source observations; aggregate is null unless every local
source/provider/isolation is verified. Open references union sessions/control;
all stored confirmationStack entries count, including closed/expired-but-present
sessions. Human observations cover handover/review/orphan control. Background
covers interaction/outbox/review dispatch, specialist/chain, Smart Brain,
webhook, indexing, document work/source/manifest and Quartz stores. Partial/dead
work remains outstanding. Missing beans/tables alone never prove zero.

Drain defaults to blocking new API mutations: documents/indexing/data-sync,
connector replay/reconcile, new agentic work and bootstrap. Exact continuation
routes in RuntimeRetirementIngressConfiguration retain existing messages/control,
anonymous renewal, review decisions, channel callbacks/dispatch completion and
cancellation; existing resource/owner auth remains mandatory. Persisted ownership,
not a caller-selected conversation ID, permits chat continuation. Store/control
SPI writes serialize with drain. Durable request permits survive crashes without
automatic expiry; unsettled requests block completion.

Interactive turns use actual 0.8.35 OrchestrationTurnGuard action/result checkpoints.
App-owned handover/document/Smart Brain/webhook worker effects hold the durable
gate lock; scheduled registration uses fenced proxies. New scheduled retention
cleanup stops during drain. Verified installed guards and certified absence
compute workerFencingComplete. Enabled framework execution/chain recovery,
review recovery, indexing processors and Quartz trigger creation lack the needed
app target-epoch fencing in inspected 0.8.35 and retain specific named blockers.
Custom/in-memory providers without complete enumeration also block.

## Parent R5 And Data
`localDrainComplete` is independent of `remoteObligations` CONNECTOR/HUMAN_INBOX.
Parent must merge target-generation/fence-bound authoritative connector unresolved
sync/indexing scopes and inbox work/reference/worker-fence readbacks. Local proof
alone is not reclaim permission. `reclaimable` also requires no remote obligations.
The signed FENCE_AUTHORITY reference records the parent's R5 proof; runtime does
not pretend it fetched/validated remote stores. Refresh counts after uncertain
responses and explicitly read back status/snapshot.

V12 stores identity/topology/fence, command receipts, snapshots and unsettled
permits. V13 stores immutable transition receipts and before/after fences. No
deployment/resource/session/source deletion, replay/movement, contact auth
change or TTL shortening. Production/shared resources are outside this slice.

Tests: RuntimeTargetRetirementServiceTest, RuntimeTargetDrainInventoryTest,
RuntimeRetirementSecurityAndIngressTest; shared RuntimeRetirementTestFixture.
Isolated javac/JUnit verification writes only to /tmp. Maven remains reserved for
the parent's sequential build; no commit/push, public framework edit or live action.

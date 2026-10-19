# Durable AI Workspace Domain Routing

Workspace create, update, disable, delete and domain synchronization commit local
configuration, domain desired state, security projection and normal audit only.
Create also commits its original idempotency receipt when a key is supplied. These commands do not call
Coolify or probe DNS/TLS. Standalone mutation responses do not assert delivery
readiness. Readiness/activation and explicit domain verification remain separate.
The original create receipt is not rewritten after reconciliation.

## Worker Configuration

- `platform.ai-workspace.route-worker-enabled`: defaults to `true`.
- `platform.ai-workspace.route-poll-delay-ms`: defaults to `5000`.
- Each JVM uses one dedicated daemon thread, no queued ticks, and a one-in-flight
  dispatch guard. This does not replace Spring's default scheduler or executors.
- The database lease is 90 seconds. Reads retry after bounded delays; native
  deployments are polled across ticks, never in a sleep loop.
- Shutdown interrupts the dedicated worker and waits at most two seconds. An
  expired effect lease becomes `UNKNOWN`; it never authorizes redispatch.

`ai_workspace_route_reconciliations` is one durable record per shared frontend
application. It pins the managed service, target profile identity and native app
UUID. Target identity reuses the canonical profile fingerprint plus active and
platform-services permission flags, without resolving or hashing secret values.
Identity drift fails closed; the worker never silently adopts a rebind.

The worker scans committed domain/installation rows, including an empty desired
set after deletion or disable. Custom domains enter desired routing only after
explicit TXT ownership verification. Managed/in-flight sets record cleanup
ownership before a provider patch. Other provider domains are preserved; a
hostname suffix is not ownership evidence. Existing managed-domain metadata is
imported only as ownership, not as deployed/verified proof. The additive V206
migration rewrites no existing rows.

Provider GET/PATCH/start runs outside database transactions. Patch and start
intents are committed first; each completion is fenced by its lease token.
A successfully patched snapshot still receives its known pending native start
even if a newer desired generation exists. Its completion acknowledges only its
own generation; subsequent ticks reconcile the latest desired state. The worker
does not activate installations or mark domains `VERIFIED`.

One controller must own a shared frontend application's routing. These leases
coordinate local database workers, not unrelated control planes or external
operators writing the same provider application's domains. Coordinate such
changes operationally; no remote federation or cross-database lock is implied.

## Status and Reviewed Recovery

Both routes require `PLATFORM_ADMIN`:

- `GET /api/platform/ai-workspaces/routes`: local persisted phase, identity pins,
  generations/fingerprints, native deployment UUID, retry time and fixed error
  code. This route makes no provider request. Status is reconciliation state,
  not DNS/TLS, application activation or customer ownership proof.
- `POST /api/platform/ai-workspaces/routes/{routeKey}/observe-deployment`: attach
  an exact staff-reviewed native deployment receipt to a blocked snapshot using
  provider GETs only. The command JSON requires exactly these fields:

```json
{
  "operationKey": "staff-chosen-stable-operation-key",
  "expectedGeneration": 1,
  "expectedFingerprint": "<exact desiredFingerprint from current status>",
  "expectedInflightFingerprint": "<exact inflightFingerprint from current status>",
  "deploymentUuid": "<exact reviewed native deployment UUID>",
  "operatorEvidenceRef": "<durable operator review evidence reference>"
}
```

This example is a shape, not a submittable command. Unknown/duplicate fields,
missing fields and number/string coercions are rejected by a scoped JSON reader.
Read the exact current status and review the native receipt before submission.
The server checks the current binding/pin, desired generation, in-flight
fingerprint, exact native job and application identities, job creation after
the persisted patch intent, recognized native job state, and exact expected
provider domains. It then stores an actor-bound audited attachment receipt and
resumes observation. Exact replay performs no provider IO; changed command or
actor is rejected. This does not mark a domain verified: positive explicit
ownership and DNS/TLS route verification are still required.

An unreceipted start, expired effect lease, or failed deployment is not silently
reset. Without a matching positive native receipt, the record stays blocked.
Any separately approved provider-native recovery remains an explicit staff
operation and needs its own exact receipt before observation can resume. This
endpoint cannot initiate that operation, assert that no start occurred, force a
build, retry a deployment, erase history, or accept a direct SQL state reset.

Customer removal, origin allowlists, disabled installations/domains and public
access guards take effect locally even while physical routing cleanup is pending.

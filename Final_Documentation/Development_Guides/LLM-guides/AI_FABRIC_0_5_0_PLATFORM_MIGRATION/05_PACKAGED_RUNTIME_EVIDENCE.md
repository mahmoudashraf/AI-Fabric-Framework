# Gate A Packaged Runtime Evidence

Status: **PASSED**

Evidence date: 2026-07-30
Runtime framework: AI Fabric `0.4.0`
Entity contract: `AI_ENTITY_CONFIG_V0_4`

## 1. Artifact

```text
Image: loomai/ai-fabric-runtime:0.4.0-candidate
Digest: sha256:aec3c8daeb50312299283ce8e38b4612d0224cfac434997938255123029b4391
Canary deployment ID: dep-gate-a-040
Canary version ID: ver-gate-a-040
Vector provider: persistent local Lucene, 512 dimensions
LLM/embedding provider: real OpenAI configuration
```

The image was built from published Maven artifacts. Its Docker build does not
clone framework source.

## 2. Package Defects Found And Fixed

### Boot 4 queue mapper

The runtime fallback `ObjectMapper` could serialize an indexing work item but
could not deserialize its `Instant` during durable queue processing. The
fallback now calls `findAndRegisterModules()`. A regression test round-trips a
real `AIIndexDocument`.

### Lucene search metadata

Lucene returned stored result metadata as JSON text. Platform's source adapter
accepted only `Map`, so its safety post-filter dropped a provider candidate
that had already passed native metadata filtering. The adapter now parses only
object-shaped JSON and keeps rejecting malformed, non-object, or
wrong-tenant metadata.

## 3. Lifecycle Canary

| Step | Result |
| --- | --- |
| Liveness and readiness | `UP` |
| Upsert `gate-a-aurora` | HTTP 200, `COMPLETED`, vector count 1 |
| Initial retrieval | Source score 0.83635 and grounded answer |
| Update same logical ID | HTTP 200, `COMPLETED`, vector count remained 1 |
| Updated retrieval | Returned revision beta only |
| Restart on same volume | Queue and updated vector persisted |
| Delete | HTTP 200, `COMPLETED`, vector count 0 |
| Retrieval after delete | Zero sources/documents |
| Required projection missing | HTTP 400, `PROJECTION_REJECTED` |

The final tenant fixture state contains two documents and five successful
lifecycle work items:

```text
document vectors: 2
queue completed: 5
queue pending/processing/dead-letter: 0/0/0
```

Five completed operations with two current vectors is expected: updates and
deletes are lifecycle history, not additional live vectors.

## 4. Provider Failure Canary

An isolated container used an intentionally invalid embedding credential and a
separate volume.

```text
Initial API result: HTTP 503
errorCode: INDEXING_RETRYABLE
indexingStatus: FAILED_RETRYABLE
workId: 1
vector count: 0
```

The durable worker retried five times, then terminated:

```text
status: DEAD_LETTER
successfulTerminal: false
requiresOperatorReview: true
errorCode: EMBEDDING_PROVIDER_FAILED
deadLetterReason: EMBEDDING_PROVIDER_FAILED
```

The test container and its volume were removed after evidence capture.

## 5. Known Package Note

Read-only fixture mounts produce harmless entrypoint `chown` warnings because
the entrypoint attempts to normalize ownership under `/config`. Startup and
health remain successful. This is not a runtime correctness blocker, but the
entrypoint can later avoid ownership changes for read-only configuration
mounts.

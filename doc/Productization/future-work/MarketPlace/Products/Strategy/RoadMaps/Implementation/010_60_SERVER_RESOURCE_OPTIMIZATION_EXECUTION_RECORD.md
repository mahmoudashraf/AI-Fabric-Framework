# 010.60 Server Resource Optimization Execution Record

- Status: implemented and hosted-qualified on staging and production, 5 November 2026.
- Scope: existing hosts, customer runtime scheduling/startup heaps, independent telemetry, admin monitoring, and CI build separation.
- Evidence: [sanitized report](../../../../../../../../verification-support/server-resource-optimization-01060/2026-11-05/report.json), [desktop](../../../../../../../../verification-support/server-resource-optimization-01060/2026-11-05/admin-desktop.png), [basic mobile](../../../../../../../../verification-support/server-resource-optimization-01060/2026-11-05/admin-mobile.png).

## Delivered

1. All four staging and two production runtime/connector pairs have their shared
   role-level CPU pin cleared. Runtime CPU quota remains one core, connector
   quota remains 0.25 core. Hard memory ceilings remain 1 GiB and 768 MiB.
2. Runtime startup heap is 64 MiB; connector startup heap is 32 MiB. Maximum
   heaps remain 768/512 MiB and `ActiveProcessorCount=1` remains unchanged.
   V234 persists these defaults in both control planes, preserving explicit
   administrator overrides and unrelated target settings.
3. A root-owned, fixed read-only collector runs approximately once a minute on
   each host. Host-scoped ingest and separate read credentials are private.
   Only the telemetry namespace is exposed on its HTTPS route; lifecycle routes
   remain private. Collection does not depend on admission mode.
4. Existing Operations services/databases now hold raw 24-hour samples and
   seven-day aggregates. No additional database or customer service was created.
5. [Servers & Resources](https://console.loomai.pro/infrastructure/monitoring)
   provides admin-only host trends, freshness, top consumers, ownership mapping,
   filtering and read-only drawers. Both control planes can read both hosts.
6. All 21 previously source-built serving apps were switched in place to CI
   images: 16 staging and five production. UUIDs, public addresses, environment
   values, volumes, ports and limits were preserved. Fourteen are existing
   framework demo services, including the MCP companion; seven are supporting
   app instances. No extra demo was deployed merely because its image exists.

## Source Identities

- Operations on both hosts: `8bdfd0fcb5988d65baad65bb200b7586b10d9075`.
- Platform backend on both: `3d9a986f55c1c2936ca08629e57cd292d2628e6b`.
- Console UI on both: `9aa6a47653962116e3fd1c1136995041f785233a`.
- Framework demo source: `c586e0733aacc96cdb24d6c23ae6fd23fc97b6b1`.
- Per-app image/source identities are recorded in the evidence report. The
  Partner UI additionally adopted `5b5bf2190915c6e94aa77bff89d4d66562958789`
  to qualify the CI-to-updater path with a source-only Dockerfile comment.
- Customer runtime artifacts, AI Fabric dependencies, prompts, retrieval policy,
  model settings, Engage assignments and Desk service were not upgraded here.

## Qualification

- Operations: 429 tests passed, including PostgreSQL/Flyway, credential isolation
  and existing policy/lifecycle regressions.
- Platform: 94 focused API/provider tests passed across the final test runs;
  the V234 PostgreSQL migration test also passed without Docker skips.
- Admin UI: 17 model/browser tests and the production build passed. Actual
  staging and production desktop/basic-mobile browser checks passed against
  real APIs, including resource/history reads, inspectors and no page overflow.
- Collector/updater: 46 Python tests passed. Publisher: 17 tests passed with
  upstream Dockerfile inspection enabled during implementation; the routine
  offline run passes 16 and explicitly skips that network inspection.
- Anonymous monitoring access is denied; an authenticated customer receives
  `403`. Reader/ingest credentials cannot cross their capabilities or hosts.
  Native router rules preserve the telemetry prefix. Outside that prefix the
  existing proxy returns its unrouted response, not Operations application data.
- Sixteen unchanged/tuned canonical AI queries ran across staging/production,
  with dealership and Support queried concurrently. All succeeded within the
  predeclared 90-second bound. Reviewed answers covered stock, follow-up
  comparisons, compound delivery-policy retrieval, Engage delivery forms and
  document replacement. No prompt or semantic fallback was introduced.
- Both Support environments passed pre-restart conversation readback,
  cancellation without a write, final confirmation with one real ticket receipt,
  actual Desk claim/reply delivery, AI pause while human-owned and explicit AI
  return. Qualification tickets were resolved.
- A 251-byte staging-only document passed upload, real indexing and retrieval
  while the optimized runtimes served chat. Its derived index was removed and
  reached `DELETED`. The private upload remains a small Acme qualification
  fixture; it was not selected for production or promoted as knowledge.
- Pausing staging collection produced `STALE` while production remained `FRESH`.
  Four additional product queries still passed. The timer was restored and both
  sources were verified fresh again.
- All 21 app cutovers passed native image/health and public HTTPS health checks.
  The existing customer's allowance remained two of two, not a new quota.

## Measurements

Short before/after snapshots of runtime/connector working sets:

- Staging, four pairs: approximately 3,566 MiB before and 3,151 MiB after.
- Production, two pairs: approximately 1,899 MiB before and 1,625 MiB after.

These are observed warm snapshots, not a controlled long-duration memory study
or a guaranteed customer-count increase. External provider latency dominates
some requests; no general latency improvement is claimed. Hard ceilings were
not reduced and no service pool/concurrency setting was changed without evidence.

At the overhead observation, the collector parent peaked around 24 MiB RSS and
its largest Docker child around 32 MiB. Their CPU time was approximately
0.91 seconds per staging collection and 0.34 seconds per production collection.
Separate process peaks are not a simultaneous cgroup peak; Docker-daemon work is
not included. The unit remains capped at 96 MiB and 10% of one CPU.

The existing Operations databases held about 1.4 MiB staging / 1.1 MiB production
of telemetry tables/indexes after the initial observation period. This is not a
seven-day storage forecast. Retention and range bounds are enforced in code.

## Build Separation

GitHub-hosted CI publishes immutable source tags and validated current channels,
with at most two serving-app builds. Scheduled checks cover both source families
so GitHub's replacement of a pending workflow cannot permanently lose an app
update. Existing images are reused; channel repair is not a forced rebuild.

Each host's five-minute updater is explicitly bound to its existing apps. It
can only read provider state, pull approved images, update image fields and
request pull-only deployments. It cannot edit customer configuration, provision
resources, delete data or execute builds. Old source-push deployment triggers
were replaced by this updater to avoid deploying the previous image before CI
finishes. The scheduler workflow is installed on the repository's default branch.

Staging's real timer adopted the Partner UI source change once. Production also
adopted it, but its initial two-second local API budget caused lost responses.
The accepted job and exact native image/time were read back; an operator
acknowledged that receipt without replaying the deployment. The local API budget
is now ten seconds, within the existing ten-minute worker deadline, with a real
greater-than-two-second HTTP regression test. Uncertain outcomes still stop for
readback/review, not blind retry.

## Operational Corrections

- Installed Coolify does not permit `dockerimage` in its PATCH conversion enum.
  The initial conversion used its own Application model under operator access,
  with private backups. Ongoing updates use the ordinary image API.
- File-backed simulator/demo H2 databases cannot have two concurrent writers.
  Consistent container names and cold replacement preserve their existing data.
  The first switch also stopped the old timestamp-named instance explicitly.
  File locking was not disabled and databases/volumes were not deleted.
- Stopped predecessor containers caused stale degraded provider status. Only
  those exact owned stopped containers were removed, without volumes; images
  and private rollback snapshots were retained.
- Coolify's application API omits numeric IDs. The updater resolves the queue
  identity through authenticated own-app one-record history, not guessed labels
  or a deployment authorization bypass.

## Boundaries And Rollback

No framework release, AI text matching, search-all fallback, autoscaler,
capacity/draining redesign, managed database migration, additional server
purchase or customer quota increase was introduced.

Scheduling and startup heaps can be restored independently through reviewed
target defaults and normal application of those settings. Telemetry can be
disabled independently; history expires normally. Disable the image updater
before deliberately pinning an older image. Restore a source-build configuration
from its private before snapshot only when an on-host build is intentionally
required. Do not rewrite applied migrations or copy vector records for rollback.

Remaining follow-up is ordinary longer-duration workload/capacity observation,
not an unfinished product implementation. Maximum customer counts and smaller
hard envelopes require separate evidence. No paid infrastructure expansion is
needed to close this bounded implementation.

# Hosted Worker Fencing Recovery

This is a bounded execution track for the concrete M5 blocker found on
21 October 2026. It is not a new productization phase or a replacement for
010.36/010.44. Hosted customer acceptance remains the objective; UI extraction
stays last. Main owns sequential integration and all live mutations.

## Priority Correction: First Usable Journey

23:07UTC latest boundary: both CPs verify ee7622a12/image78ef36d9/schema207/UP,
25 protected tables and configuration unchanged. Closure SHA256
2d179718c50cb2c5a58b23f7724dca109ae9a8e2d2ea35c6239967fc0c43aca6.
Normal owner continuation PLAN200/EXECUTE200 (R16) then failed before dependency
work: the PROVISIONING phase introduced by Main's race correction violates the
existing runtime-authority contract. R is now OUTCOME_UNKNOWN18, graph unchanged,
no runtime redeploy/financial mutation/public AI query/business write.
Correct minimal follow-up: preserve VERIFYING for an actually provisioned runtime,
PENDING verification and distinct finalize/resume steps until real verification.
Keep the live-worker/manual-check exclusion. Do NOT add an authority exception
or weaken runtime-access guards; a local budget-token alternative was abandoned
without publication. Three production lines change, with real authority-contract
tests.127 focused cases/2 real PostgreSQL cases and package pass; exact current
42-row scoped snapshot accepts R18 offline through the existing receipt chain.
Root `/private/tmp/01044-provisioned-budget-authority-20261022`; publish/rollout
then use its NEW resume helper/intents, not the consumed first continuation.
Source/runtime/framework/schema/UI and financial boundaries otherwise unchanged.

Checkpoint22:17UTC: cold maintenance VERIFIED and ordinary retry consumed;
runtime/connector healthy. A status read recovered `run_verification` while the
real apply worker was still initializing Inbox. Early verification failed the
current-release document-source check (27 other checks passed), admission became
OUTCOME_UNKNOWNrev15, and the still-running worker then correctly failed its
admission guard. Release rel-efc001ac is FAILED, graph BLOCKED/INITIALIZE:inbox.
This is a product lifecycle race, not a framework defect or missing user material.
Fix in progress: truthful finalization phase before verification, exclude live
apply workers from recovery/duplicate execution, reject competing manual checks.
No SQL repair, capacity release, funding reset, app activation or browser query.
Document0 index202 consumed; document1 registered but index409; document2 unrun.
Preserve receipts and resume only through reviewed normal workflow after rollout.

22:38UTC supporting checks: production61x5s baseline passed (peak443mCPU,
12.43GB minimum available RAM, no swap). Fresh staging61x5s also passes with
all10 owned containers stable; full8GiB/4500m reservations and2GiB/2000m
headroom retained. Normal policy `01036-pilot-post-provision-r2-fleet-20261021`
is approved through22October00:24:41UTC with unchanged reservations/funding.
The first refresh attempt412 is preserved: unchanged older staging observation
cannot satisfy the policy's newer-per-target measurement rule.
Read-only real owner browser bootstrap succeeds as CUSTOMER_ADMIN, zero mutation
attempts. It captures the current self-service page and honest failed-release
states. Two GET `/api/platform/preferences` calls return403: the current shell
still asks for staff-only preferences. Record this for the final customer-UI
functional/extraction pass; do not weaken staff authorization or claim clean UI
acceptance. Screenshot/report: new private race root. No AI ingress has run.

22:42UTC qualification:151 focused tests PASS, including35 real PostgreSQL
cases, zero failures/errors/skips, package PASS. A disposable local PostgreSQL
copy of41 actual scoped rows also accepts the exact live failed checkpoint;
no hosted mutation was used for that probe. Prior red test/fixture reports are
retained under the sidecar's private handoff root. The continuation remains the
existing explicit owner plan/queue operation: healthy native runtime is reused,
no provider allowance initialization/debit is replayed, and prior operator,
bindings, usage, financial terms and failed history stay intact. First-time
dependency guards are unchanged. Scope is one retained Basic Inbox after the
verified cold reapply; broader dependency topologies remain deferred. The live
apply exclusion is local to the current single-process control plane; startup
continues to fail interrupted applies honestly, not infer distributed ownership.
Publication/rollout is next; this is not yet hosted customer acceptance.

At18:55UTC Main answered the user's question candidly: zero-loss live upgrade
and broad lifecycle recovery have become premature blockers for this disposable
pilot. The owner permits downtime and fresh AI data. Do not make a new framework
release/complete background fencing a prerequisite for its first usable journey.

- Park the framework-only hook work at a tested, resumable checkpoint; do not
  publish it or alter immutable0.8.36 artifacts. The broader fencing work below
  remains necessary for a future zero-loss upgrade claim, not this first demo.
- Close ONE explicit cold-maintenance gap in the EXISTING repair/generation
  flow, not a second recovery engine. Pin the prior physical runtime and failed
  candidate separately, require explicit downtime/in-memory-loss acknowledgement,
  stop/read back every owned writer, and use a reviewed same-framework product
  runtime repair. Current0.8.36 candidate already contains the correct identity
  configuration; a framework upgrade is not intrinsically required for chat.
- A physical-stop generation adoption must have its own typed evidence and
  monotonic epoch transition. It must not invent completed drain/worker-fencing
  evidence. Retain source/failed-attempt history and preserve wallet/allocation
  limits and actual usage. No SQL state repair or silent artifact/config rewrite.
- Then normal continuation, app activation and hosted stock/policy, attachment,
  sign-in and handover checks take priority. Wider worker topologies, zero-loss
  reapply, generic offer upgrades and elaborate recovery stay deferred.

This is the next implementation target, NOT an existing working endpoint or a
completed hosted recovery. No cold replacement/deletion has been executed yet.

Checkpoint19:40UTC, local integration (NOT hosted acceptance):
- The existing repair flow now has an explicit COLD_MAINTENANCE_REAPPLY mode,
  prior/candidate identities, read-only prior authority, a separate SYSTEM_PROCESS
  maintenance scope, ordered stop/image/provenance/deploy/adopt/resume effects,
  and typed physical-stop evidence. Ordinary drain modes remain unchanged.
- Actual read-only hosted SQL confirms APPLICATION still belongs to
  rel-1fe0f5b8; CONNECTOR_APPLICATION and RUNTIME_POSTGRES_DATABASE belong to
  rel-efc001ac. The new optional scope field preserves that split, rather than
  rewriting native ownership. Old serialized scopes omit the new null field.
- COLD_ADOPTED is distinct from completed drain, retains the actual false
  inventory/fencing flags, and is recorded atomically only with final repair
  verification. It does not use a fabricated DrainReceipt or reset the wallet.
- The customer continuation uses the same release/allocation and exact verified
  cold receipt plus current ACTIVE candidate readback. It does not reuse the
  old pre-runtime-stop proof. Normal continuation still stops the process before
  restarting the exact repaired release and initializes the existing Inbox.
- Retry history now appends instead of replacing earlier failures. Existing
  funding/limits/usage, consumers, source bindings and source version remain
  unchanged; no new database/vector copy/framework release is introduced.
- Ten focused backend report classes pass202 unique cases, including22 actual
  PostgreSQL cases. An authorization test fixture's bean factory and a provider
  fixture's mismatched release ID were corrected; no production guard was
  weakened to pass them. Two final prior-binding checks are undergoing their
  narrow PostgreSQL rerun/package, so the previously packaged jar is superseded.
- Independent integration review reports no concrete P1/P2 blocker. The runtime
  cold leaf passed its H2/security checks; a tiny real PostgreSQL transition
  follow-up is in progress. No claim of hosted readiness follows from mocks.
- No cold plan/effect, app activation, public AI ingress or business write has
  run. Both hosted control planes remain on e8/schema207. Next: finish final
  qualification, publish ONE same-framework product runtime/backend pair, perform
  the bounded recovery, and return directly to hosted customer browser checks.

Qualification19:44UTC, ready for publication (not deployed):
- Backend commit f8054c743:202 baseline unique focused checks across ten report
  classes, including22 actual PostgreSQL cases; final two prior-binding guards
  additionally pass9 selected PostgreSQL cases and package. These9 are a rerun,
  not9 extra unique scenarios. Original22-case XML and the final9-case XML are
  preserved separately under
  `/private/tmp/01044-cold-maintenance-runtime-20261021`.
- Runtime cold leaf:40 focused checks, including3 actual PostgreSQL cases,
  security/H2 tests, existing retirement contract and manifest assertion; zero
  failures/errors/skips. Exact final source hashes/reports:
  `/private/tmp/01044-runtime-cold-maintenance-generation-20261021/postgres-followup/HANDOFF.md`.
  The inventory fix's18 earlier checks remain separate, not a new framework pass.
- Private PostgreSQL tests verify actual migrations and locking, restart/replay,
  live-work rejection and preserved diagnostic/history/wallet-fixture rows. They
  do not claim full hosted financial/native preservation; Main verifies that on
  the actual repair. Framework stays the released0.8.36 artifact set.
- Independent read-only integration review found no concrete P1/P2 blocker.
  No Maven process is left running from either sidecar. UI and unrelated files
  are not part of these commits. Next is ONE publication/rollout, not more edge
  coverage or a framework release.

Deferred rather than generalized: lost transition response requires read-only
reconciliation/operator investigation; it is not retried automatically. Live
upgrades with customer conversations, arbitrary worker topologies and complete
background fencing remain parked. UI extraction remains LAST.

Checkpoint19:05UTC:
- Framework hook prototype is frozen in the separate framework worktree:
  84 focused tests pass, zero skips,22 changed/untracked paths, no version,
  commit, push, release or shared Maven install. Exact handoff:
  `/private/tmp/01044-framework-background-work-fencing-20261021/HANDOFF.md`.
- Runtime inventory corrections are local commit `033301a78`,18 focused tests;
  actual copied XML/qualification is under
  `/private/tmp/01044-worker-fencing-runtime-20261021`.
- Main's typed backend cold-transition client/DTO test passes7 cases against the
  dedicated URI, retaining false inventory/fencing/drain fields in the response.
  It is not yet wired into repair, published or deployed.
- One bounded parallel leaf implements only the matching runtime DTO/service/
  controller/tests. Its cold endpoint requires a separate
  `runtime:target-retirement:cold-maintenance:write` SYSTEM_PROCESS scope;
  ordinary retirement-write authority is insufficient. Main owns the existing
  repair journal/eligibility/native stop sequence and final integration.
- Recovery will reuse the SAME PG, wallet, deployment, tenant and failed
  candidate0.8.36 version. Current candidate already has the corrected broker.
  No new customer database, allocation reset, vector copy or framework release
  is needed merely to make this conversational pilot usable.

## Actual Failure

### Hosted Prepared-Inbox Correction, 20:05 UTC

Both CPs verify a2df03d48 / backend image41fbb459, schema207, healthy services,
unchanged configuration and25 protected authority/financial tables. Stage job
nvwenyhwkw365nu8ektc0nei; production jobl8tmwe6r1khpz8q1sm2w1cej. Closure
SHA256c86cd3bc1958195d12850dc2ae004741b7cac812d7a77221569ff4f51bc9e38f.
Both image workflows passed; UI skipped. The runtime image e31d3b95 was inspected:
27 qualified class hashes match and20 released0.8.36 framework jars are byte
identical. dsa-6678c16b is registered/STAGING-approved, not deployed.

The first actual cold PLAN returned409
RUNTIME_ARTIFACT_REPAIR_DEPENDENCY_CHECKPOINT_UNPROVEN before a plan/claim/native
effect was recorded. The original local intent/rejection are retained under
`/private/tmp/01044-cold-maintenance-runtime-20261021/pilot`; do not replay them.
Read-only authoritative SQL exposed an incorrectly modeled fixture: the graph
is PROVISIONED/RUNTIME_INITIALIZATION_REQUIRED, configurationRevision2, but the
retained, uninitialized Inbox node is deliberately PLANNED with provisioned
revision1. This is the preparation producer's state before continuation, not a
fully provisioned revision2 node. Neither the row nor the source state was edited.

Main is correcting only cold-prepared eligibility and its continuation check to
require that retained PLANNED/prior-revision state, keeping ordinary repair modes
unchanged. Tests now retain the actual producer shape and reject already-current
or missing prior revision. This is a BACKEND-ONLY follow-up; the qualified runtime
image/artifact stays unchanged. No claim, writer stop, public AI query, app
activation, financial change or business write occurred. Reservation remains
OUTCOME_UNKNOWN revision10, same generation, no worker. Final qualification and
one backend-only rollout precede a fresh r2 plan intent.

The correction qualifies56 focused cases, including11 actual PostgreSQL cases,
with zero failures/errors/skips; packaging passes. Both cold planning and owner
continuation now use the retained PLANNED/prior-revision producer state. Already
current/provisioned and missing-prior-revision cases reject. No state row or
runtime/framework image change is required. This is a bounded integration fix,
not a relaxation allowing arbitrary failed dependency phases.

- Both control planes verify `e8f0771dd`, schema207 and health UP. Customer retry
  plan/execution200 retained the same release, allocation and full financial
  history. The apply subsequently failed `RUNTIME_GENERATION_DRAIN_INCOMPLETE`.
- Actual runtime PostgreSQL evidence: old `rel-1fe0f5b8`, DRAINING, epoch1,
  authority not revoked and reclamation not begun. Connector and Inbox are
  stopped. No conversations, confirmations, human work or in-flight turns.
- Queue inventory is57 COMPLETED and6 DEAD_LETTER, with no COMMIT_PENDING,
  PENDING or PROCESSING entries. Dead letters are terminal retained diagnostics,
  not active work. They have not been deleted or marked successful.
- Runtime incorrectly requires exactly one draft-store bean name, although the
  framework supplies a default plus an effective `@Primary` canonical store.
- Durable execution defaults to IN_MEMORY on this conversational runtime. A
  JDBC-only inventory cannot certify that store by reading empty PostgreSQL
  tables. This is a real configuration/inventory limit, not an empty-work proof.
- Runtime deliberately reports unsupported epoch fencing for enabled indexing
  and specialist execution. Independent review confirms0.8.36 has no generic
  guard spanning background claim, actual effect and completion/failure writes.
  Public-method interception alone cannot guard the private asynchronous run.

Actual private evidence is under
`/private/tmp/01044-fresh-qdrant-pilot-v2-20261021`, notably
`initial-fence-indexing-drain-readback.actual.private.json` and the retained
`initial-fence-live-progress-*`/snapshot receipts. The app is still DRAFT. No
public browser AI query or business-action write has been issued.

## 2026-10-21 20:24 UTC Hosted Follow-Up

- The prepared-Inbox correction `fa047963d` is deployed and verified on both
  control planes. Staging job `su32vvyoto7xmbwhn965dags`; production job
  `kjiu6drkc2nq7whvdlcun2ld`. Both report health UP and schema207. Configuration
  and all25 protected authority/financial tables are unchanged.
- Backend image `sha256:17c34deb268f0f52f0799a274728163ee8630a61fd4048da296c4525a953b16d`;
  closure SHA256 `0c0365b45b84e443b93799a5e88bf865799ed80bee1443fc36af7c769daa61dd`.
  Private evidence: `/private/tmp/01044-cold-maintenance-prepared-inbox-20261021`.
- Runtime artifact `dsa-6678c16b` remains the already-qualified `a2df03d48`
  product image with unchanged released AI Fabric0.8.36 jars. No additional
  runtime rebuild, framework release, schema migration or UI deployment.
- A fresh attended cold-maintenance plan is being requested with the explicit
  downtime/volatile-state-loss acknowledgement. This is not yet proof of runtime
  recovery, consumer/app activation or browser acceptance. The original rejected
  plan and all failed attempts remain retained; no SQL state repair is used.
- Next: recover the same candidate/allocation through reviewed effects, refresh
  capacity only for its consuming owner retry, then run hosted browser journeys.
  Production promotion and final UI extraction remain outstanding; UI stays last.

## 2026-10-21 21:41 UTC Attended Recovery Verified

The same cold-maintenance plan is VERIFIED, receipt fingerprint
`3412d807d408522f4ae82632c6c25bf08d5091bc3b1639ed9d739328cf140dd0`.
All9 effects are CONFIRMED, with no pending or repeated native effect. Runtime,
Inbox and connector are healthy. Actual reservation remains the same
`25dea632-66b6-48c5-897c-bfcabb112894`, generation
`8bf1bb40-5100-464b-a67b-11d366f3a380`, OUTCOME_UNKNOWN revision12, worker cleared,
full demand unchanged. Candidate `rel-efc001ac` now has the typed COLD_ADOPTED
checkpoint; its original FAILED release status/error remain intact until the
normal owner retry. The previous consumer binding and DRAFT app are unchanged.

Both control planes verify `d19b8ded2`, schema207/UP and unchanged configuration
and25 protected authority/financial tables. Stage job
`cgzk1eqlwr9agxcbceryi48n`; production job `df7gfwwinqd41cgf6uysw9q3`.
Image `sha256:04e640fb8e4303d79bc9fcc63ac893d3f4c78ad4b58491cb522740e9ed628d1a`;
closure SHA256 `e17790e5536f98b50b6e15e7cf0a6301a2757d4ccdfe9a891e839e787b98f020`.
The qualified runtime remains sourcea2df03d48 / artifactdsa-6678c16b / released
AI Fabric0.8.36. No framework release or UI deployment was needed.

The second normally reviewed maintenance window extends to22:32:43.821Z. No
allowance, reservation expiry or capacity limit was extended. All failed local
intents and read-only reconciliations remain retained. Final proof includes the
exact resumed usage delta, not an unchanged-usage claim. No SQL state repair,
wallet reset, customer inventory database, vector copy or business write.

One fresh61-by5-second staging capacity sample is now running under
`/private/tmp/01044-cold-maintenance-capacity-refresh-20261021/staging`.
Production's20:34 sample is still within its22:34 validity window. Next is the
normal owner retry of the same candidate, then consumer/app activation and the
prepared browser journeys. This is recovery closure, NOT customer acceptance or
production promotion. UI extraction remains last.

## Execution

### 20:34 UTC Provider Representation Correction

The fresh cold plan was accepted and claimed. Connector, Inbox and runtime stop,
image patch and provenance patch all confirmed. The runtime deployment was
actually dispatched as native job `e1o551qremhlni5ck7ebmnt5`; its response guard
reported `NATIVE_CONFIGURATION_OR_STORAGE_CHANGED`. The durable result remains
UNKNOWN and must be reconciled read-only, never dispatched again.

Private native diagnosis proves the complete environment hash is unchanged.
Coolify encoded `custom_labels` as Base64 during startup. Replacing that encoded
field with its exact decoded bytes reproduces the entire original configuration
hash (`d4c0e78d...48a5c8d`), without excluding a configuration field or changing a
label. The runtime is running/healthy; generation adoption has NOT occurred.

The projection now canonicalizes this provider representation only. Label
content, order, whitespace and all other settings remain guarded.63 focused
projection/provenance/ordinary-repair/cold-repair checks pass, zero failures,
errors or skips; packaging passes. This is a protocol-format correction, not
semantic text matching or an LLM fallback. Backend-only publication/rollout is
next, then reconcile the exact recorded native job and continue the same plan.
No runtime rebuild, framework release, state-row repair or business write.

Fresh capacity preparation is under
`/private/tmp/01044-cold-maintenance-capacity-refresh-20261021`; production's
read-only sample is running. Staging sampling starts only after the runtime,
connector and Inbox are stable. Keep both full reservations and headroom.

The retained Inbox's older sealed configuration hash used the encoded form,
while runtime/connector receipts used plain labels. The bounded follow-up records
both exact whole-configuration representation hashes for new observations and
requires an old sealed hash to match one of those exact forms. New receipts
require both hashes. Every nonrepresentation field remains strictly equal;
missing primary configuration, changed labels/environment, extra fields and
changed secondary evidence reject. No sealed receipt is rewritten. The first
local missing-primary negative test caught an implementation omission before
publication; its failed XML remains retained. Final107 focused checks pass,
including41 actual provider-class tests, with zero failures/errors/skips and a
successful package. No additional PostgreSQL test claim is made for this pure
projection change. Source `6b8ab54d4` was published but never deployed; the full
follow-up supersedes it before the next control-plane rollout. New private root:
`/private/tmp/01044-cold-maintenance-label-equivalence-20261021`.

- [x] Preserve the failed attempt and actual native/runtime observations. Do not
  replay retry intents, reset counters, repair SQL state or weaken drain gates.
- [x] Resolve the effective primary store; missing/ambiguous/unsupported stores
  remain unverified. Exclude terminal DEAD_LETTER from active-work counts while
  preserving its rows and the normal indexing diagnostics.18 focused tests pass,
  zero failures/errors/skips. The first build stopped on a stale private local
  provider-budget dependency, before these tests; installing the unchanged local
  source dependency corrected the build. This is not a framework test pass.
- [ ] Deferred: add narrowly scoped framework background work/effect fencing hooks with
  explicit rejection, unchanged behavior when unconfigured, and actual installed
  boundary reporting. Include synchronous indexing and the full asynchronous
  claim/effect/ack or result-persistence boundary. No LoomAI/tenant/database
  assumptions, LLM text matching or semantic fallback in the framework.
- [ ] Deferred: integrate the runtime epoch adapter and advertise coverage only after
  actual instrumentation is installed. Test active work versus concurrent drain
  and authority fencing against real PostgreSQL, including late worker denial.
- [ ] Deferred for zero-loss recovery: make the enabled execution-store composition inspectable and
  durable, rather than certifying IN_MEMORY state from unrelated SQL tables.
  Preserve real behavior; do not disable required indexing/specialists merely
  to make the gate green.
- [ ] Deferred: verify the framework change proportionately and publish one immutable
  release only after Main review. Build the consumer from Central artifacts,
  verify exact runtime jars/image and publish the new qualified artifact. Do not
  install altered artifacts under the immutable0.8.36 identity in shared Maven.
- [x] Resolve the existing pilot's recovery through explicit attended physical-stop
  maintenance, typed COLD_ADOPTED evidence and a verified journal receipt. The
  changed drain epoch was not treated as a pre-runtime-stop retry. The exception
  is limited to acknowledged, never-activated conversational customer staging;
  broader zero-loss recovery cases remain deferred.
- [ ] Verify the recovered or explicitly replaced composition and return to
  normal consumer/app activation and the prepared hosted browser gates. Reuse
  valid evidence; refresh capacity only immediately before a consuming operation.

## Boundaries

### 21:08 UTC All Recovery Effects Confirmed; Resumed Accounting Guard

- Both control planes verify `e47df94dd`, image
  `sha256:2f51aac217dabeea90f6082aa235af28b191a6f112314e6b440984aeadcc4efa`,
  schema207/UP, unchanged configuration and25 protected authority/financial
  tables. Stage job `gru9a1vpicen0xcjy320e69t`, production
  `seyvpm6x1o20xnz6h5yk7aac`; closure SHA256
  `9f4e893500d035a25b9005fa2b6634fbf0e631cb1a89224e55efc150ffe90057`.
- Exact native runtime job `e1o551qremhlni5ck7ebmnt5` finished; running digest,
  source and health match the qualified0.8.36 image. Aggregate health and
  readiness both returned HTTP200/UP after adoption.
- Dedicated cold adoption confirmed old DRAINING epoch1 -> candidate ACTIVE
  epoch2, with typed physical-stop evidence and volatile-state-loss acceptance.
  Unsupported background fencing flags were not changed into positive proof.
- Inbox job `c9z51x8lf03rp7jrontmrcr3` and connector job
  `niyskvmjumm302noj52y0btm` finished/healthy. Existing two-second expensive
  inventory polling exhausted Coolify's rate limit. Both UNKNOWN responses were
  retained, then their exact jobs reconciled read-only after cooldown. Neither
  native start was dispatched again. All9 effects are now CONFIRMED.
- Final verification rejected `WALLET_CHANGED`. Actual read-only accounting
  proves12 ->18 settled provider attempts, input727 ->1156, cost14540 ->23120
  nanoUSD, output0 and chat attempts0. Thus six resumed non-chat calls consumed
  429 input tokens/$0.00000858 under the existing allowance. Store UUID,
  allocation/approval identity, limits and expiry are unchanged. No wallet reset,
  SQL repair, top-up, allowance mutation or business lead/write occurred.
- The bounded correction retains strict unchanged accounting through adoption.
  Only final verification of this explicitly acknowledged, unactivated cold mode
  records monotonic non-chat usage under identical funding terms. Counter
  decreases, new chats/output, changed terms and cap violations reject. Both
  before/after and the exact delta are included in the verified receipt.
- Native observation polling is reduced from2s to10s; provider errors still
  produce UNKNOWN and require observation, never automatic effect replay.
 51 focused checks/package pass with no failures/errors/skips. A direct compiled
  check against the actual private accounting snapshot also verifies all18
  settled rows sum exactly to the counters/usage and accepts the typed delta.
- This backend-only follow-up is qualified locally, NOT live yet. Evidence root
  `/private/tmp/01044-cold-maintenance-resumed-usage-20261021`. The original failed
  final-verification intent stays retained. After rollout, re-run final proof
  only with a fresh local verification intent; do not replay any9 native effects.
  The existing window was normally extended to21:41:26.545Z. The same R/worker,
  generation, release, stores and consumer binding remain intact; app is DRAFT.
- Next: source/image qualification and one backend-only rollout, final cold
  verification, one fresh staging capacity sample, normal owner continuation and
  activation, then the prepared hosted browser journeys. No UI extraction yet.

Deferred rate-limit hardening: honour provider Retry-After with a bounded shared
observation budget and test concurrent maintenance operations. The10s correction
addresses the observed basic single-operation burst; it does not claim a global
Coolify quota/concurrency scheduler or automatic recovery of unknown mutations.

### 21:24 UTC Final Journal Integration Correction

Both control planes verified `965343734`, schema207/UP and unchanged protected
configuration/history (stage job `m67l6orge3lbhbraryv1d6us`, production
`a2lnumuco3lssep6zkb7x5wu`; closure SHA256
`4d12814dc152d777024847de3de746df5986cbe11e2c108dbdd66f460a1288b9`).
The fresh final-verification attempt still returned `WALLET_CHANGED`: Main missed
the second strict comparison inside `RuntimeArtifactRepairJournal.finish`.
Runtime-side proof accepted the actual accounting, but atomic finalization still
used the old rule. This is an integration miss, not another runtime/provider or
framework defect. Both failed final-verification intents remain retained.

The journal now applies the same cold-only accounting policy and requires the
recorded typed continuation to equal its own recomputation. Sealed cold receipt
readback also validates that proof. Ordinary repair finalization still requires
exactly unchanged counters.66 focused checks pass, including23 actual PostgreSQL
cases, with zero failures/errors/skips; package succeeds. Tests cover final
receipt persistence with worker release and unchanged reservation/grants,
missing/changed usage-proof rejection without releasing the worker, ordinary
mode rejection and transaction rollback. Existing actual native accounting
remains18 settled calls,1156 input/0 output tokens,23120nanoUSD and0 chat attempts.
No wallet row was edited or reset. New private evidence/helper root:
`/private/tmp/01044-cold-maintenance-final-accounting-20261021`.

Next is a backend-only rollout from965, then a fresh local `verify-r3` intent for
the SAME plan and command. All9 native effects remain confirmed and must not be
repeated. App activation, browser acceptance, production promotion and UI-LAST
extraction remain unproven. No missing user material is blocking this work.

No vector copy, customer inventory database, framework-wide LLM matrix for a
mechanical hook change, new recovery engine, silent offer/version rewrite or
inferred readiness. Old vector data is owner-declared disposable, but that does
not make an unsupported worker fence a valid proof. Production promotion uses
separate target bindings and independent source indexing. Harder unsupported
topologies/recovery cases remain recorded rather than silently generalized.

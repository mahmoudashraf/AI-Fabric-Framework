# 010.68 Managed Tasks Implementation Record

Date: 2026-11-10

Status: synchronous slice implemented, deployed and live-qualified on staging
and production under the owner's "deploy and verify heavy live" request.
Qualification limits and semantic findings remain explicit below. This is not
an assertion of perfect model reasoning or qualification of every integration.

Owner authorized starting Managed Tasks after the assessment and clarification
that structured output is not a promise of deterministic reasoning.

## Initial Scope

Deliver named, read-only Tasks alongside existing Brain behavior. Reuse the
shared draft, published release, installed capability authority, framework
specialist/schema/prompt resources and selected deployment model.

This first slice is **synchronous**, with JSON or text output. No asynchronous
Task jobs, external-agent continuation, arbitrary code hosting, per-task model
override, new database or framework release is included. The assessment's
asynchronous `/runs` example remains a proposed later contract, not a live API.

## Progress

- [x] Task definition validation and compilation into official framework resources.
- [x] Existing draft validation, publication and published-manifest integrity wiring.
- [x] Runtime provisioning bundle injection and runtime capability requirement.
- [x] Exact-target Console applied-catalogue and authenticated execution bridge.
- [x] Release verification compares actual registered task catalogue and bundle hash.
- [x] Final Platform regression selection: 256 tests passed, no failures/errors/skips.
- [x] Runtime implementation/integration qualification: 158 tests passed.
- [x] Console editing, typed fields/Advanced schemas, applied testing and API examples verified locally.
- [x] Combined runtime/Platform/UI verification and browser screenshots reviewed.
- [x] Initial local-only deployment checkpoint recorded; superseded by the hosted evidence below.
- [x] Corrected backend and Console rollout verified on both hosts.
- [x] Hosted rollout, private-caller setup and real-model task qualification.
- [x] Final applied-task browser execution on staging and production.
- [x] Internal indexed-source proof and normal Task-disable restoration.
- [x] Test-only infrastructure, volumes and five temporary Brain records removed.

## Local Verification Evidence

Passed selection on 10 November:

- 14 task compiler tests, including schema contracts, local-reference relocation,
  pure-input tasks without vectors, READ authority and immutable compiled hashes.
- 15 task bridge tests: exact environment, granted scope, unsupported request
  overrides, disabled/unapplied tasks, no silent retry and mismatched response identity.
- 25 target-context tests, including authorization before target discovery.
- 53 provisioning, 21 general compiler, 6 behavior catalogue, 80 draft validation
  and 42 release verification tests.
- 158 runtime tests across 18 classes, including actual released 0.8.46 resource
  registration, private assertion authentication, strict request conversion,
  task budget scope and existing chat/authoring/assistance regressions. Execution
  output tests mock the specialist result; this is not real-model quality proof.
- 61 focused Console checks, 17 browser scenarios and the production UI build.
  Browser coverage includes customer-admin and staff roles, save/discard, schema
  preservation, resource authority, changed applied releases, stale replies,
  explicit execution, canonical errors and basic 390px light/dark navigation.

An initial compile found a new Map/ObjectNode mismatch, corrected before tests.
A duplicate Maven invocation interfered with the shared compilation output and
produced missing-symbol errors; a subsequent single invocation passed. One new
disabled-task regression initially omitted required release metadata in its
fixture; the fixture was corrected and the final full selection passed. These
failed attempts are not reported as successful original runs.

Platform command, from `Platfrom/backend`:

```sh
mvn -q -Dtest=ManagedTaskCompilerTest,ManagedTaskOperationsServiceTest,DeploymentTargetContextServiceTest,RailwayProvisioningPlanServiceTest,DeploymentConfigCompilerTest,DeploymentBehaviorCatalogServiceTest,DeploymentDraftValidationServiceTest,DeploymentReleaseVerificationServiceTest -DfailIfNoTests=false test
```

Runtime command, from `ai-infrastructure-module/ai-fabric-runtime`:

```sh
mvn -q -Dtest=ManagedTasksBundleTest,ManagedTaskServiceTest,ManagedTaskWebTest,ManagedTasksConfigurationTest,ManagedTasksStartupTest,RuntimeManagedTaskBudgetTest,RuntimeBudgetTurnScopesTest,RuntimeBudgetBindingServiceTest,RuntimeAdminOverviewControllerTest,RuntimeSpecialistAssistanceStartupTest,ChatRuntimeControllerAuthContextTest,ChatRuntimeControllerCanonicalPrincipalTest,ChatRuntimeControllerCompoundResultTest,ChatRuntimeControllerPromptPreviewTest,RuntimeCapabilityManifestServiceTest,RuntimeAuthStartupValidatorTest,ConfigurationAuthoringServiceTest,ConfigurationAuthoringControllerTest test
```

Console commands, from `Platfrom/ui`:

```sh
node --test scripts/managed-tasks.test.mjs scripts/brain-workspace.test.mjs scripts/brain-updates.test.mjs scripts/brain-change-preview.test.mjs scripts/brain-release-presentation.test.mjs
node scripts/managed-tasks.browser.mjs --serve
npm run build
```

The browser command uses in-memory fixture APIs and serves the real editor and
Brain shell locally. It does not authenticate to hosted services or run paid AI.
Local review URL while the preview process is running:
`http://127.0.0.1:5173/smart-brains/brain/tasks/assess-request?targetProfileId=target&customer=1`.
Screenshots from the reviewed run are under
`/var/folders/vb/pk83dvvx58v6dpw66mcsn9sr0000gn/T/managed-tasks-evidence-ZrPEzbmHSuNO/`;
the script prints a fresh evidence directory on each run. Generated captures
and build metadata are not product source or deployment receipts.

No paid AI call or production deployment was performed during local qualification.
The unrelated pre-existing `.gitignore` changes are preserved and excluded.

## Hosted Rollout Checkpoints

The following initial checkpoints are historical; final identities and results
in the later sections supersede their pending statuses.

- Source `4f5d459d174d8840eea797eadb19f200794614da` is pushed. Core image CI
  `42860717388`, complete runtime verification/image CI `42860717126` and
  storage regression `42860717444` passed.
- Initial backend image was healthy on staging and production:
  `sha256:2d176fcd5e9f0f786b0f50edc3fad195a8e9e9f47c9a9369451bb94705d58c4f`.
  Initial Console image was healthy on staging:
  `sha256:98a9eb83fb04bfdd09c1ece6c7046be9bcce1b238741be08c8794a5179765c96`.
  These initial image identities are superseded by the corrected rollout below.
- Registered exact runtime `dsa-54bddc22`, digest
  `sha256:995deaf4d9e854d8cda3363b4dffcd955b5452bc6a947c65b1ab539565f7c4ea`.
  AI Fabric remains 0.8.46. Existing dealership/support runtimes are unchanged.
- A dedicated fixture customer owns canary `dep-695b9c7a` on the production
  control plane. Staging configuration-only canary `dep-caed3ec7` is separate.
  Test definitions are generic, synthetic and read-only. Raw receipts/captures
  are private under `~/.codex/private/loomai-managed-tasks-01068/`.
- The first publication succeeded with HTTP 201; the initial helper expected
  200. The actual existing version was read back, not published again. The
  ordinary Git-source target correctly rejected the capability-attested image;
  the explicit image-source staging destination was then used.
- First live private call returned `TASK_EXECUTION_FAILED`, with an actual
  orchestration access-policy denial. The neutral starter selected REMOTE_HTTP
  without a connected external authorization service. This is not an LLM result
  or permission to bypass that service. Canary v2 explicitly selects the same
  existing ALLOW_VERIFIED mode used by managed product templates, while keeping
  private task grants, tenant checks and resource/side-effect limits.
- v1 `ver-1f79d4eb` / `rel-b77a7b3d` finished infrastructure but failed the
  post-apply `release_target_context` check. Read-only DB evidence confirmed the
  cause: both initial releases have null authority snapshot/fingerprint and no
  authority rows. Apply froze authority only for public/productized/drain-enabled
  resources, not private Tasks-only Brains. The successful context GET did not
  check that separate frozen-authority contract and was not proof of a transient
  failure. Apply and Coolify materialization now also require authority for
  enabled Tasks, retaining empty origins for private-only resources and normal
  public-origin requirements. A fresh normally frozen release is required;
  failed records are not patched or overridden.
- v2 `ver-bdc24a09` / `rel-8e9ad478` applies the existing ALLOW_VERIFIED policy.
  A direct private-runtime call returned HTTP 200/SUCCEEDED in 4.9 seconds,
  correctly reporting an unknown subscription with missing information. This
  real-model smoke does not qualify the still-unfrozen Console bridge or a
  production release. The first denied smoke remains separate evidence.
- Authority correction: 174 tests across runtime-authority freeze/materialization,
  authority service, task compiler/bridge and release verification passed. The
  first two new test fixtures incorrectly omitted behavior schema metadata;
  the focused predicate tests were corrected to isolate that boundary, not
  weaken production validation, and the complete selection passed.
- Updates now renders the existing Task names/count/status summary only for
  behavior configuration containing Tasks. 45 focused UI checks, 31 rendered
  browser groups and production build passed. Hosted staging editing passed ten
  groups with zero inference/page errors; its configuration-only Brain was
  archived and fixture customer signed out. Production UI/CRUD and applied
  execution await the corrected core rollout.
- No production task-runtime promotion or complete paid matrix is claimed yet.
  The live runner retains failed, denied and semantically incorrect outcomes
  separately; no automatic query retry or answer replacement is used.

### Corrected Core Rollout

Source `eddd61eec9bdc78cd4a316f26e12ac84d77bfc45` is committed/pushed;
core image CI `42865694202` passed. Both staging and production now have native
OCI source/digest and healthy-container evidence for:

- Backend: `sha256:a9e4b99e9da355502a052b731357c3a468640cf80ba7a2cf59e412d7e2a76428`.
- Console: `sha256:4331fdbf8ead9ae8ff952da7406c33cc621e63a0174792f7f948557f740b8f3a`.

Production core rollout completed at 17:39:48 UTC. This is image-only deployment;
no customer-serving host build was performed. Runtime remains the exact `4f5d459d`
image and AI Fabric 0.8.46. Existing Northfield and production Support runtimes
were not upgraded for these tests.

Production editor qualification passed ten hosted groups with zero inference,
unexpected writes or page errors. This includes the actual rendered Task summary
in Updates. Its temporary task was removed, configuration-only Brain
`dep-2cd61ea3` archived and fixture session signed out.

The separate quota/Desk CI `42865694198` failed at Testcontainers initialization:
the runner's Docker minimum API 1.40 rejected the client's API 1.32. This was not
a product assertion failure. The exact full selection passed locally against
real Docker/PostgreSQL: **232 tests, zero failures/errors/skips**, including all
13 quota PostgreSQL tests and six deletion PostgreSQL tests. The failed hosted
CI remains recorded as failed; no runner/dependency change is included here.

Fresh normal staging release `rel-2996057a` retains v2 `ver-bdc24a09` and the
same runtime artifact. Read-only database inspection confirms both frozen
authority fields are present, unlike the earlier failed release. Deployment
verification and the paid API/UI matrix remain pending at this checkpoint.

### Verified Runtime And API Matrix

Both normal releases subsequently reached `APPLIED_VERIFIED` / `PASSED`:

- Staging `rel-2996057a`, verified 17:52 UTC.
- Production `rel-59557b37`, verified 18:03 UTC.

Both execute v2 `ver-bdc24a09`, the same runtime artifact/image and task catalogue
hash `sha256:33ae414463a243e847e01dee78d9d21675a1ffe86747cf8b436a640be1d6e5a9`.
Actual runtime readback reports AI Fabric 0.8.46 and a ready catalogue. These are
isolated fixture-owned runtimes, not an upgrade of an existing customer app.

Final boundary matrix: **32 checks per environment, 64 total, all passed**.
Coverage includes anonymous/browser identity, missing key/assertion, expired or
wrong issuer/audience/tenant/customer/deployment, exact read/execute grants,
invalid signatures, prompt/model/authority overrides, schema/JSON/body bounds,
known-real foreign-owned Brain denial and cross-environment keys. Both target
backend keys and assertion keys are distinct; a key from the other target is
denied. Expected ownership denials use actual foreign resources, not made-up IDs.

The **32 real-model task runs** (16 per target) passed the checked result
contracts and expected fields. Cases cover extraction, French/Unicode, prompt
injection as untrusted input, GET-only API planning, text policies, unavailable
appointments, supplied facts, no implicit URL/web fetch, repeat independence and
two concurrent callers per target. Four policy-text outputs were manually
reviewed against the supplied policy and were accurate without invented fees.

One noncritical semantic finding remains: both OpenAPI planning runs returned
the correct GET endpoints/parameters but omitted the missing-authentication note.
Schema success is not a claim of perfect instruction following. No text repair,
extra model retry, stricter semantic validator or framework release was added.

Two checker corrections are preserved explicitly in the raw evidence:

- The first negative test incorrectly expected numeric correlation ID `42` to
  fail. It is safely converted to diagnostic text and caused one accepted task
  run. The final malformed-ID test uses an object and is rejected. No product
  restriction was introduced for this harmless conversion.
- The original second fixture had Supabase access but no linked Console
  workspace. Final ownership checks use the current customer against an
  independently confirmed real foreign Brain. Its resource-concealing 404 is a
  valid denial; a second checker incorrectly required 403. The original failed
  reports remain unchanged, and final security-only reruns passed without extra
  inference. `api-review.private.json` records the manual interpretation.

### Applied-Task UI Correction

The hosted execution check initially dispatched zero calls: the editor's whole-
Brain `savedDraftMatchesLatestPublished` gate disabled Try even for the exact
verified task catalogue. That deployment-wide flag is not execution authority
and must not prevent testing the selected applied release.

Source `e036fb05fed17ff904621947b42f00a723ba5af3` removes that gate, retaining
local-edit/stale-draft checks, `canOperate`, exact target, applied task existence,
version/hash checks and no-retry behavior. Pending draft changes show a warning,
not a block; draft instructions are never executed. 45 focused UI tests, 25 local
browser scenarios and the production build passed. UI-only image CI
`42871118094` passed; backend was correctly skipped. Final hosted execution and
native rollout evidence follows.

Both hosted Consoles are native-source/digest healthy at `e036fb05f`, image
`sha256:279e4be835b90cff6984f1e78370c5a94e33ec3b70c0abe635665dcd6ada20d5`.
Backend remains `eddd61eec`; no runtime rebuild was needed for this UI correction.
Four actual customer browser executions succeeded: two against staging, two
against production, all exact version/hash, zero retries, unexpected writes or
uncaught page errors. Expected theme/presentation-preference writes occurred.
Both unavailable-appointment answers remained explicitly unknown. One staging
extraction retained the literal supplied `ReferenceCASE-UI` token; production
returned `CASE-UI`. The runner neither normalized nor retried this variation.

A separate zero-inference browser read captured zero `console.error` events and
five identical iframe `allow-scripts`/`allow-same-origin` sandbox warnings. This
does not retroactively claim console capture during the paid runs. The warning
is recorded for scoped attribution, not labelled an exploit or silently removed.

### Indexed Evidence Qualification

An internal Acme staging fixture, `dep-b9a09c0a`, temporarily received one named
Task using its existing owned document source. Normal release `rel-66be6321` /
`ver-0b8f4736` passed verification. Three real task runs returned canonical JSON,
no business side effects and actual `document` evidence. Two onboarding answers
correctly described the 15-minute first-import expectation, five-minute later
schedule and why CONNECTED does not prove import completion. A response-time
question returned a conservative unknown with missing information.

This is **one eligible source proof, not three-guide corpus qualification**.
Read-only diagnosis found four Weaviate records representing three guides, but
only the newer onboarding record matches the current S3 binding scope. Older
scope records are correctly excluded by `DocumentActiveVersionFilter`. Full
task input reaches model-directed extraction; no input-handoff defect was
demonstrated, and historical exact embedding queries are not available in logs.

Normal current-binding S3 discovery lists only onboarding and an unrelated
qualification file. The other two guides are absent, so reindexing stopped with
zero source mutations and zero additional paid calls. No filter was weakened,
old vectors copied, document invented or AWS permission changed. Dataset follow-
up: upload the two approved fictional guides to the intended current source,
register/index normally and qualify the complete corpus separately.

The original Acme behavior was preserved privately before editing. Reapplying
the older version was rejected: read-only provisioning preflight reports
`Document storage requires an explicit target and reviewed binding reference.`
Omitting `managedTasks` in the draft patch also retains it through existing merge
semantics. Restoration therefore uses a fresh normal publication with the same
support behavior and explicit `managedTasks: {enabled: false, tasks: []}`,
preserving the current reviewed storage binding. Fresh restoration version
`ver-b5e397a1` / release `rel-6cab74b8` is tracked to verified completion below.

### Restoration And Cleanup Complete

Acme restoration `rel-6cab74b8` reached `APPLIED_VERIFIED` / `PASSED`. Native
readback confirms Tasks disabled, empty catalogue, null bundle hash and both
bundle environment values cleared. No inference was needed for this check.
The original support behavior and current reviewed storage connection remain;
the runtime image is the qualified `4f5d459d` build. This is not a claim that
missing current-scope Acme policy files were restored or indexed.

Five exact test Brains were removed through Platform deletion operations:
`dep-caed3ec7`, `dep-695b9c7a`, `dep-d9717be1`, `dep-9a1f4ed1`, `dep-2cd61ea3`.
All final deletion receipts are `SUCCEEDED`. Six native canary services
(two runtimes, two REST connectors, two standard optional AI-state PostgreSQL
instances) and four owned volumes are confirmed absent on both hosts, through
provider API, Docker container and volume checks. Tasks introduced no new
database or inventory system. Global runtime source artifacts remain available.

Cleanup exposed a separate existing lifecycle gap, not a Managed Tasks failure:
direct hard deletion rejected attached storage with
`COOLIFY_RESOURCE_STORAGE_RETIREMENT_REQUIRED`. An explicit cold RETIRE request
with `reclaimOwnedData=true` then returned `RESOURCE_SCOPE_MISMATCH` for this
untemplated fixture, using both the admin API key and its real customer owner.
The offer/ownership boundary for non-productized archived Brains needs separate
investigation. Do not "fix" this by weakening storage ownership checks.

Under the owner's explicit test-data deletion authorization, the remaining
captured canary runtime/database UUIDs and matching native names were verified
and deleted using native Coolify administrator APIs with owned-volume reclamation.
Shared networks and global Docker cleanup were not requested. Platform's normal
deletion retry then reconciled native absence successfully (`del-33373bb7`);
no SQL mutation, forged receipt, disabled gate or unrelated resource deletion.
The first failed deletion and retirement results remain in private evidence.

All browser fixtures signed out. The dedicated Console fixture's Supabase
refresh sessions were signed out globally (HTTP 204); no credentials or real
customer accounts were changed. Existing production Northfield and LoomAI
Support releases were not upgraded by this work. Both core services are native-
verified at backend `eddd61eec` / Console `e036fb05f`; AI Fabric remains 0.8.46.

Final 18:24 UTC health readback: Northfield `rel-a679937f` / `ver-689ff79c` and
Support `rel-1f5533c5` / `ver-18864bc3` both return HTTP 200 / `UP`. Their release
timestamps predate this work. Host point loads were staging 1.60/1.77/1.85 and
production 0.33/0.47/0.52; these are ordinary status samples, not capacity results.
Existing Brains acquire Tasks through the normal update/apply of the compatible
runtime and published Task definitions; no bulk automatic runtime upgrade ran.

Evidence root: `~/.codex/private/loomai-managed-tasks-01068/`, plus the private
`managed-tasks-ui-20261110-*` browser directories and final findings JSON.
Raw failed attempts and actual model outputs remain private and unchanged.
Reusable hosted runners and synthetic definitions are under
`verification-support/managed-tasks/`; reports/captures are not committed.

### Qualification Limits

No live external READ-action task or genuine read-only staff browser fixture
was available in this proof. Existing compiler/resource ownership and runtime
READ/NONE tests passed; that is not a substitute for each real receiver contract.
The four paid browser runs and native API cases are not a throughput/load test.
The known OpenAPI omission, literal-reference variation, incomplete Acme corpus
and iframe warning remain visible, rather than being converted to false green
quality claims. Async Tasks, external agents and independent task models remain
deferred scope, not undisclosed parts of this release.

## Draft Editing Limitation

The editor checks the task subtree immediately before save, preserves unrelated
behavior settings and retains rejected local edits. The existing shared draft
API has no atomic edit ETag/CAS: another editor can still write between that
read and PUT. Do not describe this preflight check as atomic locking. A generic
shared-draft concurrency change is separate from this synchronous task slice.

## Contracts

Runtime:

```text
GET  /api/tasks/v1/tasks
POST /api/tasks/v1/tasks/{taskCode}/execute
```

Private verified identity and task-specific read/execute grants are required.
Request content grants no identity, configuration or source authority. Output is
validated against the published task schema; semantic uncertainty remains visible.

Console:

```text
GET  /api/deployments/{id}/tasks/applied?targetProfileId=...
POST /api/deployments/{id}/tasks/{taskCode}/execute?targetProfileId=...
/smart-brains/:brainId/tasks
```

All Console operations use existing Brain ownership/delegation. Publishing and
applying remain explicit. The selected environment cannot fall back to another
runtime. Connection checks never run a Task; invoking one is an explicit paid
inference command.

## Deferred Follow-Ups

The remaining assessment phases require separate scope: independently selected
task inference profiles, durable Task runs/delivery, external Python-agent waiting
and in-plan continuation. Do not expand this slice into recovery, capacity or
draining changes. Human review/writes keep their existing explicit contracts.

See [assessment](010_68_MANAGED_INTELLIGENCE_TASKS_AND_EXTERNAL_AGENT_INTEROPERABILITY_ASSESSMENT.md)
and `Final_Documentation/External_User_Guides/SMART_BRAIN_MANAGED_TASKS.md`.

# 010.68 Managed Tasks Implementation Record

Date: 2026-11-10

Status: initial synchronous slice implemented and locally qualified. Hosted
rollout and real-model qualification are now in progress under the owner's
subsequent "deploy and verify heavy live" request. The outcomes below are not
a completed hosted acceptance claim.

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
- [x] Deployment status recorded: local only; existing hosted serving versions unchanged.
- [ ] Hosted rollout, private-caller setup and real-model task qualification.

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

## Hosted Qualification In Progress

- Source `4f5d459d174d8840eea797eadb19f200794614da` is pushed. Core image CI
  `42860717388`, complete runtime verification/image CI `42860717126` and
  storage regression `42860717444` passed.
- Exact backend image is healthy on staging and production:
  `sha256:2d176fcd5e9f0f786b0f50edc3fad195a8e9e9f47c9a9369451bb94705d58c4f`.
  Initial Console image is healthy on staging:
  `sha256:98a9eb83fb04bfdd09c1ece6c7046be9bcce1b238741be08c8794a5179765c96`.
  Production Console rollout awaits the narrow rendered Updates-summary fix.
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

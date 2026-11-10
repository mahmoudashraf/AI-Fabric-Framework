# 010.68 Managed Tasks Implementation Record

Date: 2026-11-10

Status: initial synchronous slice implemented and locally qualified. Not yet
deployed to staging or production; no paid inference or hosted end-to-end proof
has been performed for this feature.

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

No paid AI call or production deployment has been performed for this slice yet.
The unrelated pre-existing `.gitignore` changes are preserved and excluded.

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

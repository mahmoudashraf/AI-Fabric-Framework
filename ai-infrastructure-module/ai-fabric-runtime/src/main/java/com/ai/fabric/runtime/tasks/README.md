# Managed Tasks runtime, first release

This is a synchronous, private backend integration surface. It consumes existing
AI Fabric **0.8.46** specialist execution; it does not call an LLM independently,
change the framework, add a second finalization loop, or expose a browser API.

## Published configuration

Set `LOOMAI_MANAGED_TASKS_BUNDLE_BASE64` to the Base64 of the published UTF-8 JSON
bytes, and `LOOMAI_MANAGED_TASKS_BUNDLE_HASH` to `sha256:<hex>` of those exact
decoded bytes. Both are absent when disabled. A missing content/hash pair,
invalid schema, unsafe execution contract or mismatched hash fails startup.

Bundle contract:

```json
{
  "contractVersion": "LOOMAI_MANAGED_TASKS_V1",
  "resources": ["official ai.fabric/v1 SpecialistSchema, SpecialistPromptProfile and Specialist documents"],
  "tasks": [{
    "code": "assess-request",
    "name": "Assess request",
    "description": "Assess the supplied support request.",
    "specialistRef": "task-assess-request@1",
    "requiredScopes": ["specialist:task-assess-request@1"],
    "inputSchema": {"type": "object"},
    "outputMode": "JSON",
    "outputSchema": {"type": "object"},
    "deadlineSeconds": 45
  }]
}
```

The resources example above is descriptive, not a valid executable bundle. The
checked-in `src/test/resources/tasks/compiler-generated-managed-tasks.json` is
actual Platform compiler output, exercised by the released manifest bootstrap.

Limits align with the compiler: 65,536 decoded bytes, 16 tasks, 64 resources,
40-character stable task codes, 120-character names, 500-character descriptions
and deadlines up to 120 seconds. Official specialist limits can further constrain
input/output. Metadata comes from `PLATFORM_DEPLOYMENT_VERSION_ID` and the bundle
hash, not request fields. Enabled tasks require runtime deployment and tenant IDs.
If a runtime customer ID is configured, it is checked too.

The existing assistance manifest loader merges task resources with normal
specialist/chain resources; it does not register another competing loader.
`/api/admin/overview.managedTasks` reports `contractVersion`, `enabled`, `ready`,
`contentHash` and the exact compiled `tasks` catalogue. Readiness checks real
specialist registration and JsonNode client binding. The `managedTasks` health
contributor is included in readiness and excluded from liveness.

## Authentication and requests

Use the existing private runtime authentication: trusted backend ingress key
plus a verified private assertion, accepted issuer/audience and expiry. Public
browser tokens are not accepted. Verified tenant/deployment must match this
runtime; body fields never supply authority or identity.

`GET /api/tasks/v1/tasks` returns only entries with exact `task:<code>:read`
grants. Execute does not imply read. It excludes prompts, internal specialist
references and internal scopes; unauthorized entries, names and counts are not
included. It performs no inference or provider-budget admission.

`POST /api/tasks/v1/tasks/{taskCode}/execute` requires the exact
`task:<code>:execute` grant. Body:

```json
{"input":{"request":"Explain the next useful diagnostic step."},"correlationId":"Customer case 42"}
```

The body accepts only `input` (an object) and optional `correlationId` (at most 160
characters, no control characters). Unknown/duplicate fields, trailing JSON and
over-65,536-byte bodies are rejected. No prompt, model, provider, permission,
conversation, tool or deadline override is supported. Input and the specialist
wrapper are validated **before** a paid turn starts.

The runtime constructs:

```json
{
  "request": "Assess request: Assess the supplied support request.",
  "taskInput": {"request":"Explain the next useful diagnostic step."}
}
```

`/request` is the server-owned primary text; `/taskInput` is untrusted JSON
context. Local schema references are relocated by the compiler under
`#/properties/taskInput`, while the public input schema remains unchanged.

The trusted execution context preserves the verified subject and caller facts.
After the execute grant, it receives **only** the task's published internal
specialist/vector/action scopes, not arbitrary caller scopes. Read-action scopes
can include the installed action's own required scopes. The existing budget
`openTask` turn checks the exact task grant and consumes the same deployment
request allowance without granting `chat:query`. Provider attempts and captured
interruptions retain existing accounting and cannot be converted into success.

## Results and bounds

Results contain `runId`, `taskCode`, `status`, `configurationVersion`,
`taskConfigurationHash`, `outputSchemaVersion: "1"`, `output`, `evidence`,
`failure`, `sideEffectsExecuted: false`, `startedAt` and `completedAt`.

JSON output is a schema-validated object. TEXT uses an official object schema
for the internal `{ "answer": "..." }` result, validates that object and returns
only the answer string. `outputSchema` in the compiled catalogue describes this
internal object for both modes. Public output is bounded to 65,536 bytes before
projection; it is never truncated into falsely valid JSON. At most eight safe
identifier-only evidence references are returned, never raw contents, metadata,
URLs, diagnostics, model traces or exception messages.

The request remains synchronous. A dedicated bounded pool runs each specialist
call and opens/closes its budget/guard scope on that same worker. Defaults are
`loomai.managed-tasks.workers=4` and
`loomai.managed-tasks.queue-capacity=8`; bounds are 1-32 workers and 1-128 queue
entries. This is not a submit/job API. Waiting is bounded by both the configured
task deadline and verified-identity expiry. Timed-out work is interrupted;
provider transports may finish an already transmitted request and its accounting
remains valid. No retry, refund or unbounded replacement worker is introduced.

The task-only pipeline guard checks deadline/budget before orchestration and
governed actions. Only declared READ actions with `sideEffectLevel=NONE` may run.
Writes, interactive continuations, delegated specialists and handoffs are not
part of this release. `sideEffectsExecuted=false` refers to business mutations,
not ordinary provider accounting/logs, and relies on correctly declared external
read contracts; it is not a sandbox for customer code.

Failures return static safe codes/messages and null output. Existing specialist
generation/finalization failures remain failures; no new model repair, semantic
text matching, default answer or silent provider switch is added. Semantic
quality and factual correctness are not guaranteed by JSON Schema validation.

## Explicit exclusions

No managed async submission/status API, event waiting, callback continuation,
Python code hosting, customer run database, idempotent execution/replay guarantee,
Platform backend/UI implementation or framework release is included here.
`runId` is a response receipt, not a durable job handle. Existing framework
diagnostics/storage behavior is unchanged. Never automatically replay a failed
HTTP request as though it were an uncharged attempt.

## Local qualification

On 10 November 2026, 69 selected tests passed with zero failures/errors/skips:
`ManagedTasksBundleTest`, `ManagedTaskServiceTest`, `ManagedTaskWebTest`,
`ManagedTasksConfigurationTest`, `ManagedTasksStartupTest`,
`RuntimeManagedTaskBudgetTest`, `RuntimeBudgetTurnScopesTest`,
`RuntimeBudgetBindingServiceTest`, `RuntimeAdminOverviewControllerTest` and
`RuntimeSpecialistAssistanceStartupTest`.

The real Main-generated fixture was compared with the checked-in fixture and
matched exactly. Its JSON output includes null `conversationTextPointer`; both
that resource and a TEXT variant compile/register through the actual released
0.8.46 Spring bootstrap. Existing assistance/chain loading remains green. MVC
tests use real private assertion signing/verification and the Boot 4 converter
wiring. Execution-output tests mock the specialist response; they do not claim
live provider quality or hosted deployment qualification. No paid generation or
framework release was used for this runtime qualification.

Main integration subsequently passed 158 runtime tests across 18 classes,
including the selection above plus existing chat authentication/canonical
identity/compound response/prompt preview, runtime capability/auth-startup and
configuration-authoring regressions. The final service selection contains 12
tests. Exact rerun commands and Platform/Console outcomes are recorded in
`010_68_MANAGED_TASKS_IMPLEMENTATION_RECORD.md`. No hosted rollout or paid
task execution is implied by this local result.

# Managed Tasks

Managed Tasks let your backend request a named intelligence outcome from a Smart
Brain without supplying its system prompt on every call. Instructions, input and
result contracts are owned by the Brain's draft and published version.

## Configure A Task

Open **Smart Brain -> Tasks**. Add a task with a stable code, name, purpose and
instructions. Define its input fields and choose JSON or text output. JSON Schema
and the deadline/output limits are available in Advanced.

Optional Resources select knowledge collections and READ actions declared by
enabled, owned plugin installations. A task can use input alone: vectors and
knowledge collections are not required. Task execution cannot use write actions,
handover, human review or arbitrary remote specialist targets in this release.

Save to the existing shared draft, then use **Updates** to publish and apply.
Saving is not deployment. Removing a draft task also does not remove it from an
already applied environment until an update is applied.

## Test The Applied Task

Select the intended staging or production environment and open **Try**. Supply
an input object matching the applied task's schema and select **Run task**.
The view shows the result, actual configuration version and failure/evidence
metadata. It does not run unsaved draft instructions or automatically invoke AI
while navigating or editing. Apply pending changes before testing those changes.

Console uses its existing customer/delegated-staff authorization and the exact
selected target's private runtime authority. It does not send browser-owned
credentials directly to a provider or choose a different environment if the
selected one is unavailable.

## Call From Your Backend

Use the existing private-runtime authentication configured for the application.
The verified identity must belong to this runtime's customer, tenant and Brain,
and grant `task:<task-code>:execute`. Keep credentials/signing material on your
server. A public widget/anonymous session cannot invoke this private API.

```http
POST /api/tasks/v1/tasks/assess-request/execute
Content-Type: application/json
X-AIFABRIC-RUNTIME-API-KEY: <configured-trusted-backend-key>
X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer <verified-private-runtime-assertion>
```

```json
{
  "input": {
    "request": "Our application receives HTTP 401 from the inventory API."
  },
  "correlationId": "support-case-781"
}
```

The input fields above are an example: each task defines its own input. The
request envelope accepts `input` and an optional correlation ID only. It cannot
replace the task prompt, select an arbitrary model/provider or add permissions.
Input is JSON data; placing a URL in it does not automatically fetch that page
or grant internet access. Supply document text or configure a permitted READ
capability when the task needs additional material.

Successful responses include:

```json
{
  "runId": "task-example",
  "taskCode": "assess-request",
  "status": "SUCCEEDED",
  "configurationVersion": "ver-example",
  "taskConfigurationHash": "sha256:example",
  "outputSchemaVersion": "1",
  "output": {"answer": "Check whether the configured API credential is current."},
  "evidence": [],
  "failure": null,
  "sideEffectsExecuted": false,
  "startedAt": "2026-11-10T12:00:00Z",
  "completedAt": "2026-11-10T12:00:02Z"
}
```

JSON mode returns an object matching the configured output schema. Text mode
returns a string in `output`. Output schema version `1` is qualified by the
configuration version/hash, not a separate independently published schema.
Failures return an error status and safe failure information; they are not
fabricated answers. An accepted task run ID is diagnostic correlation, not an
asynchronous job that can be fetched later.

Private callers with `task:<task-code>:read` can inspect the input/output contract
through `GET /api/tasks/v1/tasks`. Only explicitly granted tasks are listed.

## Bounds And Current Scope

- Up to 16 tasks per Brain; all share its existing runtime and selected model.
- Task codes start with a lowercase letter and contain lowercase letters,
  numbers and single hyphens, at most 40 characters.
- Input/output schemas describe JSON objects using Draft 2020-12, up to 16 KiB
  each. Local JSON-pointer references are supported. External schema URLs,
  resource IDs and dynamic references are not supported.
- The complete compiled catalogue must fit within 64 KiB. Large schemas or
  instructions can reduce how many tasks fit.
- Configurable task deadlines are 5-120 seconds; output token limits are
  128-4096. Existing provider funding, model compatibility and runtime execution
  policy still apply. No automatic provider replacement is introduced.
- Console request bodies are limited to 30,000 characters. Runtime requests
  and outputs also have byte/execution bounds; provider limits may be lower.
- Calls are synchronous. Stopping the browser's wait is not proof of server
  cancellation, and a network timeout must not cause an automatic retry.
- No conversation is created, no business write is executed, and this initial
  task endpoint does not persist a run history or require a new database.

Structured output guarantees the checked shape on success, not factual
certainty or identical reasoning across requests. Express unknown information
in the task's result contract rather than forcing invented values. Your backend
remains responsible for enforcing business rules and applying any recommendation.

External-agent job submission/completion events, durable Task polling and
independent per-task models are deferred. Existing chat and older Smart Brain
event-operation APIs keep their own separate contracts.

## Availability

This guide describes the implemented first-release contract. A deployment needs
a runtime image advertising `managed-intelligence-tasks`, followed by normal
publish/apply and verification. An older runtime image does not acquire Tasks
merely because the new editor is visible. Current qualification is recorded in
`010_68_MANAGED_TASKS_IMPLEMENTATION_RECORD.md`; this document alone is not a
production deployment receipt.

# Managed Tasks

Managed Tasks let your backend request a named intelligence outcome from a Smart
Brain without supplying its system prompt on every call. Instructions, input and
result contracts are owned by the Brain's draft and published version.

## Configure A Task

Open **Smart Brain -> Tasks**. Add a task with a stable code, name, purpose and
instructions. Define its input fields and choose JSON or text output. JSON Schema
and the deadline/output limits are available in Advanced.

Optional Resources select existing Brain knowledge collections and permitted READ
or WRITE actions. A task can use input alone: vectors and knowledge collections
are not required. The governed-write extension also lets you use an eligible
configured assistance specialist with non-interactive JSON input and output.
That selection inherits its instructions, schemas and resources; it does not
create a copy or support arbitrary specialist IDs, chains or remote targets.
Action and specialist selections do not create new connections or credentials.

Save to the existing shared draft, then use **Updates** to publish and apply.
Saving is not deployment. Removing a draft task also does not remove it from an
already applied environment until an update is applied.

Tasks retain the Brain's existing authorization policy. A managed deployment
without a separate authorization service can use the existing `ALLOW_VERIFIED`
mode; private identity and task-specific grants are still required. A Brain
using `REMOTE_HTTP` needs its real authorization service configured and granting
the operation. Enabling Tasks never bypasses a denying or unavailable policy.
Use a compatible image-source deployment destination and the runtime artifact
advertising Managed Tasks; a Git-build destination cannot prove that artifact
is the image being executed.

## Test The Applied Task

Select the intended staging or production environment and open **Try**. Supply
an input object matching the applied task's schema and select **Run task**.
The view shows the result, actual configuration version and failure/evidence
metadata. It does not run unsaved draft instructions or automatically invoke AI
while navigating or editing. Apply pending changes before testing those changes.
Pending changes elsewhere in the Brain do not prevent testing the already
applied task. The view identifies the applied version and warns that pending
changes are not used. Save or discard local task edits before running; an applied
task must exist on that exact target.

Console uses its existing customer/delegated-staff authorization and the exact
selected target's private runtime authority. It does not send browser-owned
credentials directly to a provider or choose a different environment if the
selected one is unavailable.

For an applied Task that supports writes, **Try** distinguishes the AI's result
from actual action receipts. Actions not requiring confirmation can run as soon
as the model selects them and the registered action authorizes them. Test with
an appropriate environment and receiver. For a confirmation-required action,
review the proposal and choose **Confirm action** or **Reject action**; a Task
configured for human review sends that proposal to the existing review workflow
instead. Neither running a Task nor enabling review adds confirmation to an
action whose policy does not require it.

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

## Governed Write Results

The execute endpoint is unchanged. On a compatible runtime, an authorized
no-confirmation write returns its actual status in `actionExecutions`; a
registered action outcome projector may include safe result fields. A successful
write receipt and a failed AI output can appear together. A timeout or
`sideEffectsStatus: "OUTCOME_UNKNOWN"` is not proof that nothing changed.
Check the connected system before deciding what to do next.

A pending confirmation returns HTTP 202 with `status: "CONFIRMATION_REQUIRED"`
and an `actionProposal` containing its receipt ID and expiry. Use the same
private identity with the separate `task:<task-code>:confirm` grant:

```http
POST /api/tasks/v1/tasks/assess-request/action-proposals/<receipt-id>/decisions
Content-Type: application/json
If-Match: "ver-example"
```

```json
{"decision":"CONFIRM"}
```

Use `REJECT` to decline. The optional `If-Match` value is the quoted configuration
version from the original response. It rejects a changed version before
execution; Console always sets that precondition. The decision uses the
original proposal parameters, not a second LLM call. Confirmation requires the
Brain's existing durable action receipts. For `REVIEW_REQUIRED`, use the
configured review workflow, not the Task decision endpoint. For
`INPUT_REQUIRED`, the response reports the missing information; the synchronous
Task does not create a conversation or automatically resume.

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
- No conversation or Task run history is created. Confirmation and human review
  reuse existing Brain AI state; no separate Task database is introduced.

Structured output guarantees the checked shape on success, not factual
certainty or identical reasoning across requests. Express unknown information
in the task's result contract rather than forcing invented values. Your backend
remains responsible for enforcing business rules and applying any recommendation.
For important completeness requirements, use explicit result fields, such as
whether required information was supplied, rather than relying on a free-text
answer to mention every omission. Those fields still require semantic judgement;
schema validation alone cannot prove the answer is complete.

External-agent job submission/completion events, durable Task polling and
independent per-task models are deferred. Existing chat and older Smart Brain
event-operation APIs keep their own separate contracts.

## Availability

The original read-only contract needs a runtime image advertising
`managed-intelligence-tasks`. The governed-write and specialist-reference
extension additionally requires `managed-task-governed-actions` and normal
publish/apply and verification. That extension is implemented locally against
the 0.8.47 release candidate; it is not live-qualified or deployed.
Current qualification is recorded in
`010_68_MANAGED_TASKS_IMPLEMENTATION_RECORD.md`; this document alone is not a
production deployment receipt.

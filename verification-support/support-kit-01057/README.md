# Support Kit 01057 Qualification

Native requester routes only. No old case storage, StartReceipt adaptation, automatic write retries, or semantic answer grading. Raw answers and review criteria require human review. Existing 01054 scenarios provide the nine quality turns.

```sh
node --test verification-support/support-kit-01057/requester-adapter.test.mjs
node verification-support/support-kit-01057/run.mjs preflight staging /absolute/new/private/output /absolute/private/adapter.mjs
node verification-support/support-kit-01057/run.mjs lifecycle staging /absolute/new/private/output /absolute/private/adapter.mjs
node verification-support/support-kit-01057/run.mjs quality staging /absolute/new/private/output /absolute/private/adapter.mjs
```

Optional quality arguments: comma-separated scenario IDs, then an absolute scenario JSON path. Production writes also require `SUPPORT_KIT_ALLOW_PRODUCTION_WRITES=true`. Each invocation requires a fresh output directory; evidence is private (directory 0700, files 0600), never generated in this repository.

## Trusted Adapter

The existing private 01054 transport signature is accepted:

```js
export async function request(environment, route, options) { /* trusted identity and origin */ }
export async function attest(environment) { /* current account, deployment and release evidence */ }
export function selectedResources(environment) { /* owned resources, Console only */ }
```

`options` contains `method`, `body`, `operationKey`, `headers`, `timeoutMs`, and `signal`. The transport must honor the abort signal, forward the retained `Idempotency-Key`, use the selected identity, reject redirects, and never retry writes. Start/messages have 130-second deadlines; other requests have 15 seconds. Return a Fetch `Response` to preserve the first HTTP status/body before validation. A legacy JSON-returning transport is supported, but cannot recover raw status/body that it already discarded; thrown errors are retained verbatim except credentials.

Optional trusted exports for direct providers: `apiBase` (e.g. `/api/desk/requester/v1/spaces/{spaceId}`), `runtimeCredentialMode: 'direct'`, `getRuntimeAccessToken(environment)`, `fetchWorkspace(environment, requestId, {call})`, and `runtimeRequest(environment, route, options)`. A foreign runtime requires that separate transport, which supplies its own scoped runtime bearer; Console credentials are never forwarded by the runner. `fetchWorkspace` returns the same `SupportWorkspaceBootstrap` as the pack. Prefer its provided journaled `call` for bootstrap transport. No browser/server keys belong in scenario files.

`prepareContext(environment, ticket, scenario, client)` may implement host resource/consent integration. For Console, existing `selectedResources` is supported using the separate `/requests/{id}/context` and `/context/consent` endpoints. Generic intake remains subject/details/topic. Diagnostic scenarios without host integration stop before creating tickets.

## Evidence And Bounds

`preflight` reads session, optional assigned roster, and list only. `lifecycle` exercises native create/start/readback/list/seen/activity/resolve/reopen/cancel, then an authorized empty conversation, one real message, and link-existing creation without another start. Direct providers must initialize empty canonical control before item reads; the runner checks control first and never invents a user message to initialize storage.

`quality` starts each new ticket once, retrieves the opening answer from canonical completed-turn IDs, sends only subsequent questions, and resolves only fully successful runs. A failed/unconfirmed write or first response stops immediately, leaving its receipt and first response/failure in `attempts.jsonl` and `report.json`. There is no failure cleanup or uncertain-start replay. Read back the owned receipt separately before any further manual action.

The local runner tests do not prove live provider quality, account isolation, or deployment readiness. Archived read-only composer and latest-only continuation are qualified by the actual support-pack browser suite, not by this HTTP runner. Live rollout qualification remains a separate explicit run against the deployed version.

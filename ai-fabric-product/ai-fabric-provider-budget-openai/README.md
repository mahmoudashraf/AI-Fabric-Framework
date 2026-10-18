# Guarded OpenAI provider adapter

Explicit opt-in `SpringAiModelResolver` replacement for AI Fabric 0.8.35 and
Spring AI 2.0.0 / native OpenAI Java SDK 4.39.1. This module registers no beans,
changes no framework code, grants no approval, and makes no startup probes.
Runtime integration and enablement belong to the host application.

The five-argument public constructor additionally accepts
`OpenAiBudgetInvocation.Capture`. Its `capture(Operation)` runs synchronously in
`resolveChatModel` / `resolveEmbeddingModel`, before native SDK async dispatch,
and returns an immutable `OpenAiBudgetInvocation(operationId, denialObserver)`.
An OkHttp request tag carries it to the network thread. Typed observer events
contain only the captured operation UUID, attempt UUID and budget/guard codes.
They never select billing scope and never use public header/query budget IDs.
The four-argument constructor uses `NONE`; hard-mode host integration must use the
capture hook and reject a missing controlled turn for interactive chat. Background
index jobs can use `NONE` and fixed billing scope/work status. There is no global
denial counter that can attribute a concurrent operation's failure to another turn.

Both existing constructors use `OutputCapMode.PRESERVE_FRAMEWORK`. A six-argument
constructor accepts an explicit final `OutputCapMode`; the guarded host factory
must select and record the opt-in described below. There is no automatic mode
selection from prompts, public headers/query values, or request purpose text.

### Pre-bound native clients

Native SDK 4.39.1 dispatches async work before `HttpClient.executeAsync`, so the
transport never consults a caller ThreadLocal. Each resolved model instead gets
`rootClient.withOptions(options -> options.httpClient(new BoundHttpClient(...)))`.
The bound native HTTP SPI holds the already captured carrier in a final field and
passes it to the shared transport's `newCall`; no prompt, body, public header or
query value carries invocation context. Billing binding is unchanged and separate.

Only root connections/transports are cached by credential identity and timeout.
Chat/embedding models are built per invocation with the framework's typed options,
including structured JSON. Both chat native clients share the same bound transport.
`BoundHttpClient.close()` is non-owning; idempotent resolver destruction closes
roots, and only roots own the underlying transport/executor/connection pool.

## Public construction and scope

Construct `BudgetGuardedSpringAiModelResolver(providerConfig, observationRegistry,
ledger, scopeResolver)` as the host's replacement resolver, not alongside an
unguarded resolver. `OpenAiBudgetScopeResolver.requireBinding(credentialIdentity)`
must derive its approval and complete ledger scope from trusted server state.
It must work on native async/OkHttp worker threads; a bare request ThreadLocal
without propagation is insufficient. Missing scope must fail closed.

The scope includes customer, deployment, target profile, environment, credential
identity and rate-card reference. Use
`ReviewedOpenAiRateCard.credentialIdentity(actualApiKey)` (full `sha256:` digest)
and `ReviewedOpenAiRateCard.REFERENCE`; neither the raw credential nor prompt
belongs in evidence, exceptions or logs. The network guard independently matches
the actual final bearer credential to the returned scope. All SDK headers are
preserved except the enforced identity encoding and trusted request correlation ID.

The host still owns ingress `recordChatIngress` and the owner-approved request
limit. This transport counts actual provider sends through `reserveAttempt`; it
does not double-count user ingress. A budget reservation never replaces the host's
authorization, ingress limit or customer/target binding checks.

Only OpenAI is supported here. Other enabled paid families, unreviewed models,
non-OpenAI endpoints, Azure deployment/API-version overrides and unsupported paid
surfaces fail closed. Local ONNX may remain separately configured, but this resolver
does not construct ONNX models or fall back to another paid provider.

## Native exchange boundary

The public SDK hooks are `ClientOptions.builder().httpClient(...).maxRetries(0)`,
`new OpenAIClientImpl(options)`, and Spring AI's `openAiClient(...)`. Both chat
clients are explicitly supplied using `openAiClientAsync(client.async())`; otherwise
the Spring builder would create a separate unguarded async client.
Superclass `resolveChatOptions` and `resolveEmbeddingOptions` retain native typed
options, request model/temperature/token overrides and structured JSON behavior.
Chat adds explicit `n=1` and standard `service_tier=default`.

The owned HTTP SPI implements sync and async calls plus native status, headers,
body and close behavior. Its OkHttp client disables connection retries, redirects,
SSL redirects, caches, proxy routing and authenticators. The budget guard is a
**network interceptor**, not an application metrics interceptor: every exchange,
including a hypothetical native retry/follow-up, must obtain a fresh UUID and a
receipt with `newlyReserved=true` before request bytes are sent. Replays cannot
authorize another send. Native SDK retries are zero in the production constructor.
Denials are non-retryable runtime failures even if an SDK retry policy is changed.

`Accept-Encoding: identity` is applied after copying SDK headers. An unexpected
content encoding fails closed with the full hold, before transparent decoding.
Per-request timeout settings cannot disable or extend configured transport limits.
The trusted attempt UUID replaces `X-Client-Request-Id`. Successful settlement
records a bounded safe `x-request-id` when available, with the same attempt UUID.

Only exact Chat Completions and embeddings POST routes are permitted. Chat is
non-streaming, text-only, one completion, and has an explicit output cap of
1..16384 using only `max_completion_tokens`, whose documented cap includes visible
and reasoning output. Legacy `max_tokens`, mixed limits and missing limits fail
with `UNSUPPORTED_OUTPUT_LIMIT_MODE`; the adapter never translates them silently.
Hosted tools, audio/vision, Responses API, priority tiers and unknown
request fields cannot bypass the reviewed token-only rate card. Local function
tool definitions remain supported; each subsequent provider send is separately
guarded. Native JSON is size-bounded and duplicate/trailing JSON is rejected.

### Explicit native cap mode

Source and `javap` of the pinned Spring AI 2.0.0 artifact confirm that
`getMaxTokens()` calls the SDK's `maxTokens(...)`; only
`getMaxCompletionTokens()` calls `maxCompletionTokens(...)`. AI Fabric 0.8.35
selects the legacy field for `gpt-4o-mini`. The resolver preserves that superclass
behavior in the default `PRESERVE_FRAMEWORK` mode, so legacy chat fails closed at
the serialized request boundary before reservation/send, even with a larger
approval. The host may explicitly opt into structural native cap wiring:

```java
new BudgetGuardedSpringAiModelResolver(
    config, observations, ledger, scopeResolver, capture,
    BudgetGuardedSpringAiModelResolver.OutputCapMode.MAX_COMPLETION_TOKENS);
```

In this mode, `super.resolveChatOptions(...)` still chooses the effective numeric
limit and every other option. The adapter copies those native typed options, calls
the public `maxTokens(null)` and `maxCompletionTokens(existingEffectiveCap)`
builder methods, and retains model, temperature, purpose/request handling and
structured JSON. The same cap must remain within 1..16384. This does not change
the model or Chat Completions API, select a provider/fallback, increase a cap or
approval, modify the request/configuration, or change embedding options. There is
no reflection, framework copy, or new framework release.

The hard-mode runtime factory must pass this enum explicitly and record the
selected mode in its controlled guarded-profile/readiness evidence; this adapter
does not add runtime configuration binding or enable the profile itself. Request,
configured and default caps are tested, including serialized native bodies with
exactly `max_completion_tokens` and no `max_tokens`, JSON object/schema preservation,
and unchanged default-mode denial. Even in opted-in mode, the existing real
100000-token approval cannot fit the full-context chat hold.

## Bounds and settlement

No tokenizer, character heuristic or paid token-count request is used.

- Chat reserves **128000 input tokens plus the effective output cap**. Default
  output 1000 means a 129000-token hold; output 16384 means 144384. This deliberately
  overholds the documented combined context because the ledger validates input
  and output bounds separately. Settled input+output must still be <=128000.
- String embeddings reserve 8192 per input, capped at the documented 300000
  aggregate per request. Integer token arrays use exact validated counts, with
  per-input and aggregate limits. Batches are guarded at the actual HTTP boundary.
- Pinned nano-USD/token rates are chat input 150, chat output 600 and embedding
  input 20. Settled cost uses uncached input prices even when cache discounts
  apply: it is a **conservative accounted upper bound, not an actual invoice**.
- A complete successful native JSON usage object must contain exact nonnegative
  integer counts with consistent totals, a reviewed response model, and usage no
  larger than the reservation and output/context caps. A response `service_tier`,
  when present, must be the exact string `default`, otherwise the full hold remains
  unverified. A request must always explicitly select `default`. It is settled once.
- Error, timeout, malformed/ambiguous usage, unexpected encoding and unknown
  responses retain the full RESERVED/AMBIGUOUS hold. No background refund occurs.
  Ordinary non-fit denies only this attempt; it does not stop the campaign. A
  trustworthy, internally consistent usage report beyond a reviewed bound is an
  typed `METERING_BOUND_BREACH`: it retains the hold and durably stops that approval
  with the same stop reason, unlike contention. A new JDBC ledger reader verifies
  the stop in tests. The durable stop is attempted before the ambiguity update.
  Failed stop writes report a typed storage error and retain an approval/scope
  latch shared across all roots of this resolver; subsequent attempts retry the
  stop write but never send. This local latch is not a claim of durable persistence
  during a storage outage or a cross-process substitute for the ledger stop.

A 100000-token approval cannot authorize chat. Unit tests use fictional local
approvals; the module does not raise any real approval to 200000 or permit live
calls while the owner decision is pending.

Host integration can inspect `OpenAiBudgetDeniedException.failure()` or use
`OpenAiBudgetTransportException.find(error)` through SDK/completion wrappers.
Typed budget/guard codes and the attempt UUID are available without prose scans.
This is distinct from provider usage observations or billing-exact metrics.

## Reviewed official sources

Read on 2026-10-18; changes require a newly reviewed rate-card reference:

- [GPT-4o mini model](https://developers.openai.com/api/docs/models/gpt-4o-mini):
  128000 context, 16384 output, USD 0.15/0.60 per million input/output tokens.
- [text-embedding-3-small model](https://developers.openai.com/api/docs/models/text-embedding-3-small):
  USD 0.02 per million input tokens.
- [Create embeddings](https://developers.openai.com/api/reference/resources/embeddings/methods/create):
  8192 per input and 300000 summed per request.
- [Create chat completion](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create):
  `max_completion_tokens` includes visible/reasoning output; `service_tier=default`
  selects standard pricing, while the response reports the tier actually used.

## Tests

`GuardedResolverContractTest` covers public option parity, strict provider/model/
endpoint rules, text-only surfaces and batch/token-array bounds.
`GuardedNativeTransportTest` uses the real native SDK, Spring model builders,
loopback fake HTTP, and the shared JDBC ledger on H2. It exercises successful
settlement, async/batch paths, concurrent holds, retry/redirect/error responses,
typed denial, malformed/out-of-bound usage, identity/gzip behavior, request IDs,
timeout clamping, pre-bound async observers and root-versus-borrowed lifecycle.
Package-private transport/client fixture constructors are
not exposed by the public resolver or live configuration.

Run the scoped reactor tests only after coordinating shared ledger build ownership:

```sh
mvn -f ai-fabric-product/pom.xml -pl ai-fabric-provider-budget-openai -am test
```

No tests require an API key, OpenAI connection, deployment, paid request or Maven
change to framework artifacts.

The test-only H2 version is pinned to 2.3.232: the cached 2.4.240 driver reproduces
SQLState 23514 on the schema's `attempt_state IN (...)` constraint with a nested
database-closed error. The pin is not a production database or ledger change.

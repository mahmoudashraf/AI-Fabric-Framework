# MCP Execution Gateway

Internal discovery and execution adapter. It is not a public tool proxy or an OAuth authorization service.

010.69 candidate status: local/public functionality, DNS-pinned HTTP egress and all ten supplied ProdUS pins are qualified. Main owns immutable-image rollout and hosted Platform/business-receiver qualification. ProdUS remains intentionally stopped; no worker deployment, business tool call or silent published-binding migration has been performed. Earlier preflight-only review/gates below are historical and superseded by the DNS-pinned closure section.

## 010.69 Integration Contract

- Platform owns customer/Brain/target authentication, the applied action, tool selection, trusted actor parameters and owned credentials.
- New managed runtimes call Platform's target-scoped execution proxy. Only Platform uses the existing gateway internal key for those connections. No new gateway caller-auth protocol was added.
- Existing Shopify direct-gateway integration, UCP discovery arguments, checkout direct JSON-RPC and `CUSTOMER_OAUTH_PKCE` remain supported. That legacy Shopify path is not generalized into personal-account OAuth.
- Business OAuth discovery, consent, refresh and revocation belong to Platform. Pass a resolved resource-bound bearer through the existing `auth.mode=BEARER_TOKEN_SECRET_REF`, `tokenSecretRef` and trusted `trace.mcpSecretValues` boundary. Do not send refresh tokens or unverified browser credentials.
- `McpToolSummary` adds `annotations` and `execution` JSON alongside unchanged native `inputSchema`, `outputSchema` and `schemaHash`. These are upstream metadata, not permissions. They never grant READ/anonymous access or disable confirmation.
- Native action schemas belong at **root** `actionConfig.inputSchema` / `actionConfig.outputSchema`. Native input validation replaces only the simplified parameter-type projection. Platform/runtime still enforce trusted resolution, ownership, confirmation and review.
- Copy upstream `execution.taskSupport` into `actionConfig.execution.mcp.taskSupport`. `required` cannot run through the synchronous path. Verification also detects a newly task-required observed tool when checking a pinned schema. `optional` remains synchronous.

Only the gateway module is changed by this work. Framework/caller no-retry settings, applied-action ownership, identity handoff, import policy and live qualification remain integration responsibilities. A supplied idempotency key is **not** proof that an upstream tool deduplicates requests.

## Protocol And Transport

Uses the maintained [MCP Java SDK 2.1.0](https://github.com/modelcontextprotocol/java-sdk/tree/v2.1.0) transport and client-session implementations. Raw result objects avoid flattening native schemas/annotations into a smaller SDK tool model.

- Default `server.transport=STREAMABLE_HTTP`, preferred protocol `2025-11-25`; the declared supported set includes `2025-06-18` and the explicitly approved legacy JSON HTTP revision `2024-11-05`. A server may select a declared revision in its single initialize response, and later messages use it. An explicit older preference never negotiates a newer revision. Unknown protocol revisions are rejected; no second initialize, authentication change or transport retry is performed. The 2024 JSON-only compatibility path uses the same SDK HTTP transport, not a switch to the distinct HTTP+SSE endpoint handshake; it does not claim that Streamable HTTP was specified in 2024.
- Explicit `server.transport=HTTP_SSE` uses the SDK's deprecated HTTP+SSE adapter and its `2024-11-05` protocol. It is never attempted after another transport/authentication fails. The SDK validates the discovered message endpoint against the SSE origin.
- JSON-RPC results are correlated by request ID. Progress/notification/unrelated response events are not substituted for the actual result. SSE framing and bounded inbound decoding are delegated to the SDK.
- SDK owns MCP session headers. Every service operation closes its transport/session; one host HTTP client is reused. Redirect following, stream resumability and authorization retries are disabled; no client reinitialization/replay wrapper is installed.
- The SDK's HTTP interface uses the module's bounded Apache-backed adapter with connection-time public DNS validation/pinning. Protocol and SSE parsing remain in SDK 2.1.0; no Spring baseline upgrade or second MCP protocol implementation was introduced.
- Direct checkout JSON-RPC uses the same SDK session without an initialize handshake, preserving the existing explicit provider route.
- No sampling, roots, elicitation, durable tasks or automatic resources/prompts indexing are advertised.

## Authentication Modes

Public `NONE` sends no upstream credentials. Business modes:

- `BEARER_TOKEN_SECRET_REF` / existing `STATIC_BEARER_SECRET`.
- `API_KEY_HEADER_SECRET` with an allowlisted header and owned secret reference.
- `MULTIPLE_HEADERS_SECRET_REFS` with `headers: [{"headerName":"X-API-KEY","secretRef":"CUSTOMER_..."}, ...]`. Values are never part of the published configuration. Authorization can be a complete stored header value; other headers must be allowlisted. Duplicate case-insensitive names, missing secrets, routing/protocol/internal headers and excessive entries fail before dispatch.
- Existing `OAUTH2_CLIENT_CREDENTIALS`; optional `scope`, `audience` and `resource` go only to the configured HTTPS token endpoint. Only its access token goes to MCP.
- Existing Shopify `SHOPIFY_AGENTIC_CLIENT_CREDENTIALS` / `CUSTOMER_OAUTH_PKCE` behaviour is regression protected.

No downgrade to public credentials, another account/provider or an interactive personal login is attempted. Streamable HTTP 401/403 produce `MCP_UPSTREAM_AUTH_REQUIRED` / `MCP_UPSTREAM_ACCESS_DENIED`, for Platform to surface as a business-connection problem.

## Native Schemas And Failure Behaviour

Uses NetworkNT 2.0.4, supplied by the SDK Jackson 2 integration. Supports Draft 2020-12 and explicit Draft-07, local references, unions/nullability, numeric enums/fractional limits, nested objects/arrays and additional-properties semantics. Schema meta-validation and reference initialization happen before dispatch. Automatic external network/file schema-reference retrieval is disabled; references must be local to the supplied schema.

Required executable schema/trust contracts are validated; descriptions and semantic confidence are not hard gates. Invalid input returns `INVALID_MCP_ACTION_ARGUMENTS`; an invalid/unresolved schema returns `INVALID_MCP_ACTION_CONFIG`. A successful-looking result whose `structuredContent` violates a declared output schema returns `MCP_TOOL_OUTPUT_SCHEMA_MISMATCH` **without replay**. The operation may already have completed upstream. `isError: true` remains failure regardless of HTTP 200.

For published `accessMode=WRITE_ONLY` / `READ_WRITE`, transport or response-parse failure after entering `tools/call` returns `MCP_EXECUTION_OUTCOME_UNKNOWN`, not proof that the operation failed. The receipt tells callers to check the target system before retrying; the gateway never retries it. Output-schema mismatch retains `MCP_TOOL_OUTPUT_SCHEMA_MISMATCH` and explicitly warns that the operation may have completed. Input/configuration/schema/authentication errors before dispatch remain known; locally rejected client configuration and explicit upstream 401/403 denials retain their distinct codes. This classification uses the trusted declared access mode and dispatch boundary, never tool-name or message-text heuristics. The legacy low-level tools-call API does not receive that action access mode; consumers of it must not infer safe write retry from a generic transport failure.

Hash canonicalization ignores schema descriptions/order but preserves data property names such as `title`, `description` and `default`, and literal `enum` / `const` content. Existing affected hashes may now report real drift; refresh/review bindings rather than rewriting installed releases or turning drift checks off.

## Bounds

One complete tools-list traversal is shared by discovery, server verification and action schema checks. Cursors are sent as `params.cursor`, independent of the preserved Shopify `params.arguments` extension. No partial catalogue is reported as ready.

Defaults, configurable through `MCP_GATEWAY_*`:

- `MAX_RESPONSE_BYTES=2097152`: per inbound JSON/SSE message.
- `MAX_TOOLS=500`, `MAX_DISCOVERY_PAGES=100`, `MAX_CATALOGUE_BYTES=8388608`.
- `DISCOVERY_TIMEOUT=PT60S`: one overall tools-list traversal deadline; initialization/token calls have their own finite connect/read limits.
- `MAX_AUTH_HEADERS=8`.
- Existing `CONNECT_TIMEOUT=PT5S`, `READ_TIMEOUT=PT30S`; applies to MCP and Boot-created token/broker HTTP clients.

Repeated/invalid cursors, duplicate identities, missing tools arrays and limits/deadlines return `MCP_DISCOVERY_INCOMPLETE` with an empty nonimportable catalogue. SDK logs are restricted to ERROR in the hosted default because its lower-level logs can contain raw upstream payloads; API failure receipts remain visible.

## Verification

Run from this module:

```sh
mvn clean verify
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Djava.version=17 clean test
```

The existing container build remains Java 21. The selected SDK is Java-17-compatible; this module's source/tests also qualify under an explicit Java 17 override. Keep the normal build on its existing Java 21 baseline.

Fixtures use local HTTP servers and mocked token receivers, never real customer tools. Tests cover JSON/SSE correlation, correct cursor placement, explicit HTTP+SSE, session/auth headers, metadata preservation, bounded multi-page discovery and verification, native schemas, direct checkout, existing Shopify customer tokens/broker/client credentials, malformed/auth/tool/timeout/connection failures and single-dispatch counts. Local verification is not hosted/live business-authorization proof. No publish/deploy or real business tool calls were performed by this bounded change.

Original bounded-module verification on 2026-11-11: 73 tests, zero failures/errors/skips across five suites on Java 21 (`clean verify`) and actual Java 17 (`-Djava.version=17 clean test`). The boot suite starts the real Spring/Tomcat application, checks health/internal authentication and rejects a private-infrastructure discovery target before dispatch. The normal Java 21 build also produces the executable gateway JAR. SDK cancellation can emit an asynchronous timeout diagnostic after a test deliberately aborts an exchange; receiver counts still prove no tool replay, and no global error-suppression hook was installed. See the public qualification and expanded verification below for the latest result.

### Real Public Qualification: Microsoft Learn, 2026-11-11

This is an actual public-service check through a locally started packaged gateway, not a mocked server. No production gateway was deployed. Upstream endpoint: `https://learn.microsoft.com/api/mcp`, explicit `auth.mode=NONE`, Streamable HTTP. The local gateway listened only on `127.0.0.1` and used a random per-process internal key held in memory, never printed, persisted or sent upstream.

- Initial check at `16:21:15Z`, previous candidate JAR SHA-256 `c2d96d4c9bd352f658ab17d01d695d92619fa8dcb4beaabf06d7927f0be06347`: discovery rejected the server-selected protocol before any tool call. A separate no-auth initialize-only probe confirmed Microsoft selected `2025-06-18` rather than the preferred `2025-11-25`.
- Narrow correction: accept the explicitly declared, SDK-supported June revision from the same initialize response and use it for later protocol headers. This is standard protocol negotiation, not another initialize attempt, hidden authentication/provider fallback or transport change. Unknown revisions remain rejected. Added June negotiation and unknown-version rejection regressions.
- Successful discovery started `16:23:21Z`, candidate JAR SHA-256 `e55c7ff8528efa73c55dcf3f6880125c130f2ca3579a24c12118a31c256228c1`: ready, negotiated `2025-06-18`, three exact tools. All three reported read-only/idempotent hints and no task requirement; those hints were retained as metadata, not used to grant permissions automatically.
- `microsoft_docs_search`: object input with a string `query`; native object output with a `results` array of document objects, including `title`, `contentUrl` and nullable content/metadata. Schema hash `sha256:066e1a76c46b4f7d5d96f517d930e4ef4635d9b3e66172a69d7522d7575c90e0`.
- `microsoft_code_sample_search`: object input with required string `query` and optional string `language`; object output with code-sample results. Schema hash `sha256:09109654b0d604daf0d78d09da4d2ec6bd81aa987645b17d7d559c39fe9ec4bb`.
- `microsoft_docs_fetch`: object input with required string `url`, no output schema declared. Schema hash `sha256:36a13ff75f19fc060210b6df3a2225bee6d9db6cdab4007edcbe309f80400ce6`. Discovered only; not executed.
- Exactly one action execution was submitted for the discovered `microsoft_docs_search` with `{"query":"Azure App Service managed identity configuration"}`. It used the real discovered native input/output schemas and pinned hash with `BLOCK_RELEASE`, not a handcrafted approximation. At `16:23:53Z` the gateway returned `success=true`, `schemaDriftStatus=OK`, no tool error, one text content item plus `structuredContent`; native input and structured output validation both passed. Result payload: 46,704 UTF-8 bytes, SHA-256 `4e86495e5a477854774b884a7857c6c37cc71aa4a1337c448b5561b03f62cd05`. The result included Microsoft documentation; no second search or fetch was made.
- The evidence harness did not correctly extract a hit count because Microsoft uses `contentUrl`, not `url`. Its zero extracted-link count is **not** evidence of zero search results. The preserved, schema-validated structured result and payload receipt establish the successful tool call; no hit-count claim is made.
- Cleanup confirmed both local qualification gateway processes stopped and ports closed (`62945` for initial check, `63102` for successful check). Internal keys were not persisted. No upstream secrets, paid inference, customer data or business mutation were used. Microsoft receiver-side invocation telemetry is unavailable; the submitted single execution and local no-retry receiver-count regressions are separate evidence.

### Hash Compatibility Review Before Rollout

Compared the released algorithm and fixtures at source baseline `ceda2468ba108f08d72f94ed728d2f9bbb42e51a` with the corrected canonicalization. This is not a production-binding inventory; the main integration stream must inspect actual active applied bindings before rollout.

Known fixture outcome:

- Shopify UCP `search_catalog`, input `{"type":"object"}` and absent/empty output schema: unchanged, `sha256:e865aa232d4f3aa93699dab0fe7d9fb8d52d0fc340adf9c230673e566d1cb556`. It occurs in both the gateway service and HTTP-client fixtures.
- Four other gateway fixture schema occurrences for synthetic `inventory.search` collapse to three unique schemas; all three hashes remain unchanged. New regression tests pin their released digests and the Shopify digest.
- Shopify Bridge readiness fixtures list eight tool names but provide **no native input/output schemas**. They cannot prove hash compatibility for customer-account, checkout or full live storefront schemas. No schema snapshot or hash pin was found in the Shopify migrations checked. This is a source limitation, not evidence that active bindings have no pins.

Affected contracts are precisely those where the old recursive keyword filter discarded actual data-property names (`title`, `description`, `default`, `examples`, `annotations`, `$comment`), literal `enum` / `const` object members or null literals. Literal array ordering previously treated like schema set keywords can also differ. Ordinary schema annotations, property ordering and set-like `required` / `type` / `enum` ordering keep their existing canonical behaviour.

Retaining the correction is relevant to native-schema integrity: Microsoft's actual result schema has a data property named `title`, and the code-sample schema has one named `description`. Those are executable type constraints, not ignorable schema wording. Keeping the old algorithm would still fail to detect changes to those constraints. No legacy digest acceptance/fallback was introduced, and no published plugin/action versions were rewritten.

Rollout gate: inspect active pinned schemas, compute/compare their corrected hashes and explicitly refresh/review/publish a new release for any affected binding before routing it to the new gateway. Do not turn off drift checking or rewrite old release snapshots. If active impact cannot yet be established, keep the existing production gateway unchanged. Main owns that inventory and rollout decision.

Expanded verification after the public-service negotiation correction and hash-compatibility tests: **80 tests, zero failures/errors/skips across six suites**, passing Java 21 `mvn clean verify` and actual Java 17 `mvn -Djava.version=17 clean test`. `McpSchemaHashCompatibilityTest` pins the four unique released fixture digests and checks the null-literal distinction. No upstream search was repeated during those offline regressions. This closes the bounded local/public gateway proof, not the active-binding inventory or production rollout gate.

### ProdUS Audit Follow-Up: Not Yet Live-Qualified

Main's active database audit found ten pinned ProdUS actions on staging and production, with both environment product-service records pointing to the same production gateway. Shopify legacy bindings have no `toolSchemaHash` pin. Do not replace that shared gateway based only on the Microsoft public proof or the small Shopify fixtures.

The separate local ProdUS source `backend/src/main/java/com/produs/mcp/LoomAIMcpDiscoveryController.java` constructs every advertised input schema as `{"type":"object","additionalProperties":true}`, with no output schema. For that exact schema, old and corrected algorithms produce the **same** digests:

- `produs.catalog.search`: `sha256:8ecfdfbf88f6eb92bba8c68ed60c2bd602450f4d02a0cbe3b2a3078f431598a6`, matching Main's supplied active pin.
- `produs.catalog.export`: `sha256:6a6662e33300cf4e9e570766a36ff5b2addf450c7143ed88dab014c7c68b37bd`, matching Main's supplied active pin.
- `produs.productization_project.create`: `sha256:6a64c636165a0e6c92e7fefd41fad8e53132f411f2aa7d107a992c6e517867c0`, matching its earlier nonsecret integration handover. This third historical match is not a new live database observation.

All three are now pinned in `McpSchemaHashCompatibilityTest`. This is source/digest evidence, not a fresh tools-list snapshot and not proof for all ten active pins.

The source review identified a protocol gate: that controller's initialize response hardcodes `2024-11-05` over JSON HTTP. Main then approved narrow explicit support for that revision. It is now in the declared HTTP compatibility set; the SDK regression reproduces the actual controller initialize envelope, sessionless JSON responses, custom API-key header and even its HTTP-200 method-not-found response to an initialized notification. It proves one initialize, one list, one synthetic local tool call and the same `/mcp` path/credentials with negotiated-version headers. The separately qualified HTTP+SSE adapter is not substituted. This removes the locally reproduced version gate, but does not turn an unavailable ProdUS endpoint into a live qualification.

The SDK can make an optional GET on that same `/mcp` path and accepts its 405 response. This is not the legacy `/sse` endpoint/message-endpoint discovery flow; all fixture tool operations remain JSON-RPC POSTs. The fixture records every path and proves no switch to another endpoint or repeated initialize/tool call.

A read-only initialize probe to the historical staging URL `https://produs-api-staging.46.224.145.148.sslip.io/mcp` returned **HTTP 503**. No tools/list catalogue was available and no business tool was called. The source/known pins suggest hashes may remain stable, but do not clear the gateway for ProdUS. Main should provide or obtain the actual active endpoint and sanitized initialize/tools-list snapshot, then compare all ten pins and qualify a separately routed candidate. No production service, binding, credential or published release was modified by this follow-up, and no silent hash migration or fallback was introduced.

Hash-only follow-up verification: `mvn -Dtest=McpSchemaHashCompatibilityTest test` passed **8/8**, zero failures/errors/skips, including those three ProdUS digests; `git diff --check` passed. The prior full 80-test Java 17/21 qualification predates these three additional test cases. The subsequent approved 2024 protocol compatibility and governed-header regressions are recorded separately below.

### Outbound Boundary Review: DNS And Governed Headers

This review records the **pre-fix candidate**. The connection-time DNS-pinning implementation and actual qualification are recorded below; deployment firewall enforcement is no longer the proposed fix for this module.

Scope: gateway destination validation, SDK HTTP client construction and header enforcement; selected SSRF/outbound-control risk only, not a framework-wide security assessment. Static review used the current SDK 2.1.0/JDK client path and the Platform OAuth `McpSafeHttpTransport` as counterevidence. No live rebinding, private-target or credential-exfiltration probe was performed.

**Confirmed source limitation:** `McpGatewayExecutionService.requirePublicHttpsUri` checks the current DNS answers through `InetAddress.getAllByName`, then returns the original hostname URI. `McpStreamableHttpClient` passes that hostname to the SDK/JDK client, without passing the validated address set or installing a connection-time destination validator. Consequently this is preflight address checking, **not DNS pinning**. A later independent resolution/new connection is not bound to the checked addresses. JDK caching, TLS hostname verification, disabled redirects and disabled retries reduce particular risks but do not establish the promised public-network destination boundary. Current source also does not explicitly disable the JDK system proxy selector. The pre-existing address classifier rejects selected private/link-local ranges, not every non-global/reserved IPv4/IPv6 range.

The actor is an otherwise authorized connection owner controlling a server hostname/DNS. The restricted asset is the gateway host/container's internal network reachability. The missing connection-binding control is source-confirmed; actual exploitation/impact depends on DNS timing, TLS and deployed routes/egress, none of which was attacked or qualified here. Platform's Apache DNS pinning protects its OAuth HTTP calls and its request to the managed gateway; it does not extend across the gateway's separate final upstream connection. The older gateway RestClient path also independently resolved after preflight, so the underlying gap predates the SDK extraction; arbitrary customer-configured URLs make it a current rollout concern.

**Minimal bounded mitigation recommendation, not an implemented guarantee:** apply and qualify an outbound policy dedicated to the gateway container/network namespace, allowing approved public HTTPS destinations and only the required resolver traffic, while denying nonpublic/link-local/metadata, host and loopback destinations for IPv4 and IPv6. Cover every upstream route, including MCP, token/broker and explicit HTTP+SSE secondary requests; preserve existing authorized public Shopify/ProdUS destinations. A host forward-chain rule alone may not cover host-local or container-loopback traffic, and ordinary UFW rules alone are not proof for Docker networking. Do not apply a host-wide policy to unrelated workloads. No new proxy service is needed. If deployment enforcement cannot be made effective, a separately approved module-local pinned-connection transport adapter is the code alternative; a second preflight lookup, higher DNS TTL or disabling TLS hostname verification is not a fix.

No deployment egress rules or DNS-pinned SDK adapter were implemented or verified by this worker. Do not describe the gateway as rebinding-safe, or enable arbitrary untrusted customer endpoints on that claim, until one of those controls is qualified. Source-level positive public tests are not a substitute for that boundary test.

**Governed custom headers:** default API-key header names are only `X-API-KEY`, `X-MCP-API-KEY` and `X-LOOM-MCP-KEY`. A business-specific name must be approved and configured through `MCP_GATEWAY_API_KEY_HEADER_ALLOWLIST`, for example those defaults plus `X-COMPANY-AUTH`; the customer form must not silently widen the gateway allowlist. Discovery/readiness should surface an unsupported header before import/execution, and Platform/UI configuration must match the gateway's actual approved list. The existing count cap and secret-reference ownership still apply. Protocol/routing/internal/hop-by-hop headers remain denied even when accidentally listed, including proxy authorization and forwarded-routing headers. New local regressions prove a configured business header works, an unconfigured header makes no request, and configured routing/internal names still make no request.

Primary sources: [JDK 21 HTTP builder](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.Builder.html), [SDK 2.1.0 HTTP transport](https://github.com/modelcontextprotocol/java-sdk/blob/v2.1.0/mcp-core/src/main/java/io/modelcontextprotocol/client/transport/HttpClientStreamableHttpTransport.java), [Docker packet filtering](https://docs.docker.com/engine/network/packet-filtering-firewalls/). These support the review; they are not proof of the current production host's egress policy.

### Latest Bounded Candidate Verification

- Full Java 21 `mvn clean verify` and actual Java 17 `mvn -Djava.version=17 clean test` passed **90/90**, zero failures/errors/skips across six suites, including explicit ProdUS 2024 JSON HTTP, the unchanged known pin digests, existing Shopify paths and governed custom headers.
- At `2026-11-11T16:44:55Z`, packaged candidate SHA-256 `f897139869a5fa1e3da29b452d84f0fa7fac616a02f08b8cd88cffafce514e71` ran in a separate loopback-only local process on port `64698`, with a new in-memory internal key. Actual Microsoft no-auth discovery returned ready over `2025-06-18`, the same three exact tool names and all three unchanged schema hashes recorded above. No tool call was made in this refresh. The earlier single successful documentation search remains the actual call evidence; it was not repeated.
- Local candidate stopped and port closed, with no key persistence. No ProdUS stack was started, no business tool was called, and no production resource, binding, release or gateway was changed.
- Functional/public proof does not close the documented DNS/egress limitation, the complete ten-pin ProdUS audit, or Main's separate hosted business-receiver/Platform integration qualification.

### Write-Outcome Qualification And Scope Freeze

The bounded action-execution failure-visibility fix adds an explicit `toolCallStarted` boundary. For both declared write modes, connection loss, malformed JSON and HTTP 500 after a synthetic receiver commit produce `MCP_EXECUTION_OUTCOME_UNKNOWN`. `McpWriteOutcomeTest` records **one tools/call dispatch and one receiver effect** in each such case; it also covers direct checkout dispatch, known pre-call input/auth/configuration rejection, explicit 401/403 denial, output-schema mismatch warnings and ordinary read failure. An idempotency-key-shaped fixture value does not enable retry or establish upstream deduplication.

Final Java 21 `mvn clean verify` and actual Java 17 `mvn -Djava.version=17 clean test` each passed **104/104 tests**, zero failures/errors/skips across seven suites. The focused protocol/SDK/write-outcome run passed **67/67** before the full runs. This latest change made no additional public or business tool call; the single real Microsoft search and subsequent discovery-only refresh remain the actual public proof above. No production gateway, customer binding or published release was changed. The bounded code scope is frozen after this qualification; remaining outbound-network enforcement and complete active-binding audit belong to Main's rollout checks.

The normal Java 21 executable candidate was rebuilt with `mvn -DskipTests clean package` after the Java 17 run. Local JAR SHA-256: `863b10e2da6457e03d650ffadb04eae2f7928af6927db92fff33fa9c6b60e952`. This identifies the worker's packaged artifact, not a published container digest or a claim of byte-for-byte reproducible builds. `git diff --check` passed. All worker changes remain uncommitted inside this module for Main's review and immutable-image rollout; no qualification gateway process remains running.

### DNS-Pinned Transport Closure And Complete ProdUS Pin Audit

The owner required the actual tenant SSRF boundary to be closed in code before arbitrary customer endpoints ship. This supersedes the previous preflight-only gate and host-egress recommendation.

**Minimal compatible choice:** retain SDK 2.1.0's `McpClientSession`, HTTP transports, correlation, session handling, JSON and SSE parsers. Supply `PinnedDnsHttpClient`, a narrow HTTP-interface adapter backed by maintained Apache HttpClient 5.6.5 / HttpCore 5.4.4. The available [Spring WebFlux MCP transport](https://central.sonatype.com/artifact/org.springframework.ai/mcp-spring-webflux/2.1.0-RC1) targets Spring 7.1; introducing that baseline into this Spring Boot 3.2 service was not necessary for this security fix. The adapter uses Reactor's maintained Flow bridge for byte-stream backpressure/cancellation; it does not parse MCP messages. Java 17 and the existing Java 21 container baseline remain supported.

**Actual enforcement:**

- `PublicMcpAddressPolicy` is the Apache connection operator's DNS resolver. It validates every returned address and hands that exact validated array to the TCP connector. There is no independent hostname re-resolution between validation and socket connect. A subsequent new connection resolves and validates again; an already open connection remains attached to its original validated address.
- Mixed public/private answers are rejected, not silently filtered. Private, loopback, link-local, metadata, multicast, reserved/documentation and transition ranges are denied for IPv4/IPv6. The DNS worker/queue, lookup time and answer count are bounded. URI validation separately rejects numeric private literals, because Apache can bypass the DNS resolver for literals, as well as non-HTTPS URLs, URL credentials, local/internal names, fragments and zone identifiers.
- The original hostname remains the HTTP authority and TLS identity/SNI; TLS 1.2/1.3 certificate trust and hostname verification stay enabled. No URL rewrite to a numeric IP or permissive trust manager is used.
- `McpApacheHttp` uses direct `Socket(Proxy.NO_PROXY)` connections and the minimal Apache execution chain: no system proxy, redirects, cookie/authentication negotiation, retry or write replay. The SDK's resumability/auth-retry restrictions remain unchanged. The adapter streams bounded chunks, keeps the SDK's per-message limits and aborts exchanges on deadline, cancellation or shutdown.
- The production token/profile/broker `RestClient` factory uses the same public resolver, direct connection construction and numeric-URI guard. Existing OAuth modes and secret ownership remain unchanged. There is no customer/runtime setting to disable the destination policy. Local fixtures substitute a test-only loopback resolver/HTTP constructor; that is not exposed by hosted configuration.
- The older service preflight is retained as early validation, not the authoritative protection. The actual DNS resolver/connector now enforces the boundary on the final gateway-to-upstream connection, including explicit HTTP+SSE message requests.

**Qualification:** `mvn clean verify` on Java 21 and `mvn -Djava.version=17 clean test` on actual Java 17 each passed **162/162 tests**, zero failures/errors/skips across nine suites. This includes 42 public-address/URI tests, five isolated TLS/streaming/HTTP adapter tests, four extra production credential-factory rejection cases, unchanged JSON/SSE/session/custom-header/2024-JSON/Shopify cases, and all 14 write-outcome tests. The TLS fixture proves the returned address is used with one resolver call, a wrong TLS hostname rejects before the receiver sees credentials, the system proxy is not consulted, redirects are not followed, the first SSE bytes arrive before EOF and cancellation removes the active exchange. These are controlled fixtures, not a live attack on infrastructure or a claim of a comprehensive security audit.

At `2026-11-11T17:10:24Z`, the packaged Java 21 candidate, SHA-256 `a0d15c9e04d1aeab77e7e750000830a97c120edb99de11b7f2a125c92feed397`, ran loopback-only on port `50922` with a fresh in-memory internal key. Actual public Microsoft discovery returned HTTP 200, ready, negotiated `2025-06-18`, and all three previously recorded names/hashes unchanged through the pinned Apache adapter. **Zero tools/call requests** were submitted. The local process stopped afterward and the key was not persisted. The earlier single successful documentation search remains the sole public tool-call evidence.

Main supplied all ten published ProdUS pins from both control planes. `McpSchemaHashCompatibilityTest` now compares every one against the corrected function for the actual controller input `{"type":"object","additionalProperties":true}` and absent output schema. **All ten match exactly**, including scan/status, finding/inspect, evidence/list, product/list, project/create, catalogue/export/search, milestone/review, workspace/inspect and package/inspect. No hash fallback or published-version mutation was added. This closes the offline compatibility gate for those supplied bindings; it does not claim a live ProdUS execution. The intentionally stopped ProdUS application was not started.

The bounded source is frozen for Main's review and immutable gateway rollout. No changes were made outside this module, no host firewall/proxy service was introduced, and no customer business tool or production gateway was touched.

After Java 17 qualification, the normal Java 21 executable was restored with `mvn -DskipTests clean package`; final local JAR SHA-256 `d9ed91cd1ecd50339092ab9fd183bf56f27c599e0b83544d73d99f60caee1a46`. Build timestamps can change the archive digest; the earlier `a0d15c9e...` receipt identifies the exact packaged public-discovery run, not a container image. Port `50922` was independently checked closed. Main must publish/review an immutable image digest rather than treat this local archive checksum as a deployed version.

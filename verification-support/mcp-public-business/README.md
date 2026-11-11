# MCP Public And Business Connection Qualification

Implementation plan: 010.69. This record separates source tests, hosted control
planes, gateway protocol checks and the still-pending released-runtime journey.
Private credentials, tokens, cookies and raw logs are not part of this directory.

## Deployed Source

- Platform backend and UI: `611e64ed62675c690921ac36ccd7b7abb846ed91`, healthy
  on staging and production. Backend image digest
  `sha256:032e63077b6a4d061c9978fb942bb5f4538bffad79df9af9faced22effcac5ec`;
  UI image digest
  `sha256:291dd08cf52cb4e4d0de639252504f7df4fff6373db14fe03c46a593119cba41`.
- Gateway: `41e43e6186a7b8c4dbf955662cb954c0517ff981`, healthy on both hosts,
  image digest
  `sha256:9467fa70e704643d69f8cd99ab3cfcb3f5f2f246a829384ef551b01bc0865195`.
- Both control planes applied migration 241 successfully. Staging health and
  OAuth client metadata returned HTTP 200; missing runtime authority returned
  HTTP 403 / `MCP_ACCESS_DENIED`. The owned production connection catalogue
  returned HTTP 200 through actual customer Supabase authentication.

## Completed Local Checks

- Integrated Platform: 408 tests, zero failures/errors/skips across 24 suites,
  including real PostgreSQL migration and full application/security startup.
- Customer authentication: 21 tests. Focused UI: 80 tests, production build and
  machine-OAuth browser check passed. Other overlapping counts are not added.
- Gateway: 162 tests on Java 17 and 21. JSON/SSE correlation, pagination,
  authorization/session handling, native schemas, offline references, legacy
  Shopify/2024 JSON HTTP, DNS-pinned TLS and single-dispatch write failures pass.
- All ten supplied published ProdUS schema pins match the corrected hashing
  function. ProdUS remains intentionally stopped; no live ProdUS pass is claimed.
- Private runtime candidate: 87 focused tests passed across identity, chat,
  Managed Tasks and the 11-test managed-MCP bridge suite.
- A separate quota/Desk CI job could not obtain Docker. Its affected PostgreSQL
  suites passed locally, 19/19. The document-storage CI workflow passed. This
  does not turn the failed CI job green.

## Completed Hosted Checks

- Staging and production gateway metadata discovery against the actual public
  Microsoft Learn endpoint succeeded using no upstream authentication.
  Negotiated protocol: `2025-06-18`; three tools and their schema hashes match
  the packaged/local receipt in the gateway README. These hosted probes made
  zero tools/call requests and used no model inference.
- Actual production customer Console: desktop/basic phone, light/dark,
  public/company/machine OAuth forms, no horizontal overflow or input/heading
  overlap, zero browser errors and zero MCP writes. The action draft remained
  unchanged. Imported native-editor qualification subsequently passed below. The existing
  floating Support launcher can overlap part of a narrow-screen form; this is
  recorded separately from functional MCP verification.

## Remaining Runtime Qualification

- Framework 0.8.48 is now visible on Maven Central. Fresh-cache consumption is
  being checked separately from local candidate results. The NetworkNT
  dependency collision was corrected before publication; the fresh reactor
  and reference-server results are recorded below. Incident Room's separate
  policy failures are not hidden.
- Released-runtime public lookup and signed-in own-subscription cancellation,
  confirmation rejection, second-user/foreign-object denial, production target
  and final resource cleanup remain pending. Optional assistant qualification
  is complete, as recorded below.

## Fixture Ownership

`fixture/server.mjs` is a disposable, instrumented protocol receiver with
fictional in-memory subscriptions. It is not a production OAuth implementation,
ticket service or customer business database. `server.test.mjs` covers its
bounded request and ownership behaviour.

The fixture service, canary Brain and separately created second Supabase user
must be removed after qualification. Existing customer accounts, shared
services, published releases and legacy bindings are not cleanup targets.

## Hosted Connection And Assistant Results

- Actual deployed API suite: **13/13 passed**, plus **4/4** offline harness
  safety checks. Public, bearer, approved API-key/multiple-header configuration,
  client credentials, explicit key rotation and invalid credential rejection
  passed. Company DCR/PKCE, browser-cookie binding checks, consent cancellation,
  natural expiry/rotating refresh, revocation and reconnect passed.
- An authenticated administrator from a genuinely different existing Console
  customer workspace received HTTP 404 for the canary connection operations.
  These are actual owned-resource denials, not a fabricated customer ID.
- API cleanup disconnected only its nine connections and deleted its seven
  temporary keys. Draft/releases were unchanged; no business tools or model
  inference ran. Multiple-header delivery is covered locally, but this live
  receiver does not prove that both headers are required. Scripted HTTP alone
  is not claimed as browser consent proof. CIMD/preregistered browser-provider
  interoperability remains locally qualified rather than live-provider proof.
- Actual production browser flow passed public import, saved diff, native
  read-only schema/destination display, guarded Undo, explicit production target
  enable/revoke, company browser consent, cookie-bound callback and business
  discovery/import/Undo, followed by disconnect and basic phone checks. No
  publish/apply occurred; no browser errors or unrelated connection changes.
- The first browser consent fixture omitted requested scopes while its token
  returned two. Platform correctly rejected the unexpectedly broader grant.
  The harness was corrected to request that fixture's two scopes explicitly,
  then reused the same two owned connection records for a passing retry.
  Popup/cookie/hostname protections were not weakened; no fixture scopes were
  added as product defaults.
- Optional Help configure used the actual selected, owned public catalogue and
  one real `gpt-5.4-mini-2026-03-17` call: 9,000 input / 220 output tokens.
  Validation succeeded on the first attempt. Actual saved fields were only
  `displayName` / `description`; connection, schemas, permissions and unrelated
  actions remained unchanged. UI Undo restored the exact four-action draft.
- The deployed production gateway also executed one actual Microsoft Learn
  `microsoft_docs_search` with its discovered input/output schemas and pinned
  hash `sha256:066e1a76c46b4f7d5d96f517d930e4ef4635d9b3e66172a69d7522d7575c90e0`.
  Result: success, one submitted action request, zero model inference.
- Qualification found two setup-message issues: incomplete header-based
  discovery lacked useful guidance, and MCP tools received irrelevant missing
  HTTP-route warnings. Narrow backend corrections passed **104/104** focused
  tests. They are in source commit `1e3e7441f03de75fdc8f3ea787be12aef6f6de8e`,
  pending final rollout together with the released-runtime bridge.
- Framework dependency correction passed **2,373** fresh reactor tests and the
  real OpenAI native-schema rerun **5/5**, eight turns. Reference server 6/6 and
  packaged/authenticated discovery passed after the correction. Framework
  source is `9413d3b38d807a8e23d754021b50eb39c03df634`; Maven Central publication
  exposed the 0.8.48 BOM by 17:53 UTC. Clean external consumption and hosted
  runtime proof remain separate gates.

## Final Runtime Build Readiness

- Runtime image CI for `1e3e7441f03de75fdc8f3ea787be12aef6f6de8e`
  ran 814 tests: 812 passed; two version-readback assertions still expected
  0.8.47 while the packaged runtime correctly reported 0.8.48. No runtime
  execution failure was reported in that run.
- Test-only commit `3ff037b73eb0ab9b8fe58f90b99193b65497a5be` updates those
  assertions and explicitly checks the managed-MCP capability. Both suites
  passed locally, 8/8. Fresh CI then passed all **814 runtime tests**, with
  zero failures/errors/skips. Its immutable image build is still pending here.
- The backend/UI image workflow for `1e3e7441f03de75fdc8f3ea787be12aef6f6de8e`
  passed. The final backend image rollout is in progress; no successful runtime
  application is inferred from that image build.
- Framework Central publication workflow `43069554984` succeeded. A separate
  empty-cache, Central-only external consumer passed **6/6**, package and boot,
  resolving only framework 0.8.48 and NetworkNT 3.0.0. This is public-artifact
  consumption evidence, not yet hosted-runtime execution evidence.

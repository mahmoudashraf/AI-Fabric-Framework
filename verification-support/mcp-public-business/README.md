# MCP Public And Business Connection Qualification

Implementation plan: 010.69. Public and business MCP paths are implemented and
qualified on released framework 0.8.49. This record separates source tests,
hosted control planes, protocol checks and actual customer journeys. Automatic
sign-in prompting has two documented production semantic misses; explicit
sign-in and signed-in reads passed. Cleanup completion is recorded at the end.
Private credentials, tokens, cookies and raw logs are not part of this directory.

## Deployed Source

- Platform backend: `fbb7622b6e3bac681bbef84a03daa3e256aec4c2`, healthy on
  staging and production, digest
  `sha256:2f14cc0283878deafed25efb90a82e17a2f495bc6901861a04ac3c878f8ec058`.
- Platform UI: `611e64ed62675c690921ac36ccd7b7abb846ed91`, unchanged and
  healthy on both hosts, digest
  `sha256:291dd08cf52cb4e4d0de639252504f7df4fff6373db14fe03c46a593119cba41`.
- Gateway: `41e43e6186a7b8c4dbf955662cb954c0517ff981`, healthy on both hosts,
  image digest
  `sha256:9467fa70e704643d69f8cd99ab3cfcb3f5f2f246a829384ef551b01bc0865195`.
- Framework: public 0.8.49 / `abecb067483811e414cd94716001715d6c8926ab`.
  Qualified runtime source fbb7622b6 above, digest
  `sha256:d24fd7c0370ef740679d3d803fcbd08135b9deba5191205b10c0b79767fe3b96`.
  Same Brain/app identity passed normal stage and production upgrade; those
  disposable resources are cleanup targets, not permanent customer demos.
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

## Earlier Runtime Checkpoint (Superseded)

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

## Active-Catalogue Verification Finding

- The first staging apply reached healthy runtime/connector and passed MCP
  authorization/schema verification. Post-apply verification reported three
  failures: Platform expected all four source definitions while the released
  runtime correctly exposed only the three enabled actions. The intentionally
  disabled native-schema example was the only differing action.
- Fix `ca33555693b135ed15e70eff9c5fa66453917e99` derives active names and
  per-action metadata/policy expectations from enabled definitions, preserving
  global confirmation rules, webhook targets and routing contracts.
- Focused release-verification suite passed **44/44**, including both the valid
  disabled-tool case and failure when that disabled tool appears active.
  No tool was enabled, release rewritten or verification result overridden.
- Backend rollout/recheck and the actual chat/Engage journey are still pending
  at this checkpoint. Both normal target applies refer to the same immutable
  runtime image and version; no data or vectors are copied.

## Released Runtime And Live Business Journey

- Final backend `ca33555693b135ed15e70eff9c5fa66453917e99` is healthy on
  staging/production at digest
  `sha256:07c6ed49f2cc79fbda2d7cfdb7c1883ac330fe1a7a577ae44b6db75491469869`.
  Normal rechecks passed on both existing targets: **27 passed, 0 failed,
  7 legitimately skipped** each. No verification result was overridden.
- Canary version `ver-50cab2c9` and artifact `dsa-ac5fa3fd` use runtime source
  `3ff037b73eb0ab9b8fe58f90b99193b65497a5be`, digest
  `sha256:36138150f2c7ebf0ec8d87990b4406ce1fea84c95f908e3a179e60352174876d`,
  framework 0.8.48. Staging release `rel-42fc159e` and production release
  `rel-4c472bac` are both `APPLIED_VERIFIED`. Production reused the connection
  only after an explicit target binding; no credentials/data/vectors were copied.
- Direct hosted authority checks passed **9/9**, using both live targets and
  real provider/broker identities. Missing/wrong key, cross-target key,
  foreign Console Bearer and deliberately altered scope/expiry were denied.
  Spoofed actor arguments were overwritten; each real user read their own
  subscription. The additional foreign-object request reached the business
  receiver, returned `SUBSCRIPTION_ACCESS_DENIED` and made zero further writes;
  the prior one legitimate cancellation and all object states were unchanged.
  Three newly issued login sessions were closed after each probe run.
  Scope/expiry perturbations are not claimed as natural JWT-expiry tests.
- Separate normal-chat business run passed eight turns: own subscription READ,
  propose/reject with zero writes, propose/confirm with exactly one receiver
  write and real receipt, second-user own READ, and confirmed foreign-object
  request denied with zero writes. Customers used ordinary verified app identity,
  not an additional MCP login. Cancellation state changed only for its owner.
- A normal signed-in production chat then read the actual updated
  `CANCELLATION_SCHEDULED` state through the production MCP binding, with one
  business read and zero writes. This is conversational production evidence,
  not just a direct private-service test.
- The managed Engage hostname passed normal Verify domain and HTTPS health
  returned 200. All 16 app readiness checks passed. The disposable app is active
  on the verified staging assignment; full browser conversation proof remains
  separate from API chat evidence.
- Actual Engage desktop/basic-phone bootstrap passed on staging: observed
  manifest/ready event selected the recorded release and runtime, and session,
  auth-context, shell and conversation requests returned 200. No overflow or
  browser errors; zero AI queries/writes. This does not claim the blocked
  anonymous public-information conversation has passed in-browser.
- The same app was explicitly rebound through the existing customer API to
  production release `rel-4c472bac`, with all 16 readiness checks still passing.
  Actual production desktop/basic-phone bootstrap reached the recorded
  production runtime, HTTP 200, with no overflow/browser errors and zero AI
  queries/sign-ins/writes. The full browser AI journey remains open below.
- End-of-cycle host load averages were 0.80 staging / 1.07 production (one
  minute). Available memory was about 2.8 GiB / 7.5 GiB respectively; staging
  retained about 4.1 GiB of swap use. No serving-workload change, capacity
  bypass or firewall modification was made for this check.

## Historical Generic Routing Gap (Fixed In 0.8.49)

- Two natural public INFORMATION queries were rejected before any MCP read:
  `Retrieval requires a configured vectorSpace allowlist in this mode.`
  This is not an MCP auth/schema failure: a normal private INFORMATION lookup
  succeeded via the same bridge, and direct authorized reads passed above.
- In PRODUCTION_CHAT/executor, `retrievalAllowlistRequired=true` is distinct
  from the explicitly false `vectorSpaceSelectionRequired`. Extraction still
  unconditionally requests a vector when INFORMATION has requiresRetrieval,
  and IntentHandling checks indexed-RAG permission before authorized READ
  planning. The first extraction repair invented a source name; no source,
  allowlist, fallback or semantic matcher was added to hide this result.
- Recommended framework correction: permit eligible READ planning before the
  RAG-specific gate, defer missing-vector validation only for that trusted
  READ-resolution path, and clarify live READ versus indexed-search prompts.
  Actual RAG must keep its existing authorization, allowlist and budget checks.
- Framework changes were subsequently approved; candidate qualification follows.
  No relaxed retrieval allowlist or invented vector space has been applied.
  Public conversation/full-browser closure and final canary cleanup remain open.
- Business confirmation prose exposes the machine action name and its trusted
  actor argument; the successful write's plain answer is generic. Record this
  presentation finding for review rather than claiming polished per-tool cards.

## Approved 0.8.49 Candidate Qualification

- Owner approved the bounded routing/validation/prompt correction and a verified
  framework patch. The presentation finding above is not included.
- Final local reactor passed **2,394 tests**, zero failures/errors/skips;
  focused routing/extraction checks passed **190/190**, release guards **7/7**.
  Local consumers compiled, agentic consumer **5/5**, MCP reference **6/6** and
  packaged reference checks passed. These are candidate, not Central proofs.
- Private runtime against candidate 0.8.49 passed **814 fresh tests**; its
  ordinary REST connector passed **102/102** separately. One stale runtime
  XML report was excluded from the count, not counted as a fresh test.
  Platform compiler/capability/release-verifier checks passed **75/75**.
- Real OpenAI native qualification passed **7/7**, ten logical turns, using
  `gpt-5.4-mini-2026-03-17`. The original cancellation-information question
  was classified INFORMATION and made one native READ with zero RAG for both
  retrieval-disabled and required-but-empty-vector-allowlist configurations.
  Required nullable/trusted fields, confirmed/rejected writes and explicitly
  no-confirmation writes also passed.
- Initial new-fixture questions named a setup-guide tool and the model
  validly chose direct ACTION, so they did not prove the intended INFO path.
  Those failing coverage runs remain recorded. The fixture was aligned with
  production's progressive extraction and the exact original question plus
  matching fictional guide content; INFO assertions were not weakened and no
  model response or intent was forced.
- Corrected OpenAI core provider matrix passed **11/11 inner cases**, no
  failures/skips/aborts (outer Maven test: 1/1). The normal 85% threshold was
  unchanged. The existing runner automatically clamps its configured minimum
  of 20 to this core chunk's actual 11 cases; this is not a 20- or 30-case run.
  Scorecard: `realapiprovidermatrixintegrationtest-provider-matrix-scorecard-1794426231200.json`.
  Publication, clean public consumption, runtime CI, hosted 0.8.49 upgrade,
  full browser chat and cleanup remain pending at this checkpoint.
- The first successful native run used Java 25 because the macOS selector
  returned that version despite requesting 21. It remains real-provider
  evidence, not the Java 21 release gate. The explicitly pinned, version-checked
  Java 21 rerun also passed **7/7**, all ten turns, no failures/errors/skips:
  `real-provider-0.8.49-native-1794426181070.private.log`.
- An extra core-matrix run was stopped (143) after its setup paired OpenAI's
  1,536-dimensional embeddings with Lucene's 1,024-dimension limit. No framework
  fix or threshold change was made for this invalid pairing. The corrected run
  uses the documented OpenAI + local ONNX MiniLM (384) + Lucene combination,
  isolated index storage and actual Java 21; log:
  `real-provider-0.8.49-core-matrix-1794426226193.private.log`.
- New framework CI `43090314922` passed registry, vector-container contracts,
  reactor and integration compilation, then failed the broad real-app suite
  on the same **14 Incident Room failures** (28 deterministic tests; the full
  Incident Room module reports 63 tests including 15 credential-gated skips).
  No new failing suite or assertion was silently excluded. Private retained
  log: `framework-ci-43090314922.private.log`. This is not an all-CI-green claim
  and no unrelated Incident Room changes are included.
- Framework source `abecb067483811e414cd94716001715d6c8926ab`, immutable tag
  `ai-fabric-framework-v0.8.49`, is publicly pushed. Central BOM and seven
  runtime-critical module POMs returned 200 at approximately 20:05 UTC.
  Private source `fbb7622b6e3bac681bbef84a03daa3e256aec4c2` was then pushed;
  runtime CI `43093161948` and core-image CI `43093161980` are running/queued.
  Fresh Central-only consumer, actual image adoption and hosted proof remain
  separate gates; public artifact availability is not a deployed success.
- Central workflow `43090421221` completed successfully. A **new empty Maven
  cache**, all repositories mirrored to Central, passed **6/6** external
  consumer tests and packaging on actual Java 21. Its executable JAR booted
  and exited successfully. Source copied only POM/src from the previously
  qualified public consumer; only the BOM version changed to 0.8.49. No local
  framework install or provider credentials were used. Evidence and reports:
  `/private/tmp/ai-fabric-0.8.49-public-consumer-main/`.
- Core image CI `43093161980` passed (UI intentionally unchanged). Runtime CI
  `43093161948` passed verification against public artifacts and is publishing
  its immutable image. Backend rollout is now running on both hosts; do not
  infer runtime adoption from this control-plane change.
- Backend source `fbb7622b6e3bac681bbef84a03daa3e256aec4c2` is now healthy on
  both control planes, digest
  `sha256:2f14cc0283878deafed25efb90a82e17a2f495bc6901861a04ac3c878f8ec058`.
  Runtime CI `43093161948` completed successfully with **814 tests**, zero
  failures/errors/skips; immutable runtime digest
  `sha256:d24fd7c0370ef740679d3d803fcbd08135b9deba5191205b10c0b79767fe3b96`.
  Normal artifact `dsa-c02ede18` is registered and staging-promoted.
- The same Brain published `ver-d5ab80c3`, framework 0.8.49. The first helper
  attempt correctly received 409 for a historical draft; the helper was fixed
  to obtain the current editable draft through the ordinary API. No product
  check was bypassed and no historical version was rewritten. Prior 0.8.48
  version/artifact/releases remain retained in private history. Stage apply is
  requested; actual release verification/browser/cleanup are still separate.
- The fictional receiver was explicitly reset for the new run, retaining its
  previous counters/results. Both fictional subscriptions started ACTIVE,
  cancellation count zero. Existing customer accounts/data were not reset.
  Fresh-consumer proof additionally verifies all four packaged framework JARs
  exactly match their Central-only cache bytes; private evidence is retained
  under `central-0.8.49/`, not only temporary build output.
- Staging release `rel-aacd3c66` reached `APPLIED_VERIFIED` on the same Brain.
  The full nine-turn real chat run passed: the exact original public question
  returned `INFORMATION_PROVIDED`, one public READ and no write; own private
  reads succeeded, rejection made no write, confirmation made exactly one
  cancellation, and a second actual user was denied the foreign-object write.
  Evidence: `staging-chat-1794428483418.private.json`. No invented vector,
  semantic matcher, search-all, extra MCP visitor login or disabled trust check.
- Same app/consumer was normally rebound to this verified staging release,
  all 16 readiness checks passed. Full browser proof is running. Normal same-
  Brain production apply `rel-b014f654` is requested using the same registered
  artifact, explicit authorized bindings and existing infrastructure.
- Full actual staging Engage browser proof passed **7 checks** on desktop and
  basic phone: public answer with receiver read; anonymous private request
  produced typed authentication-required/sign-in and no private execution;
  real second Supabase user signed in; runtime broker subject matched; actual
  own-subscription READ succeeded. No subscription/denial mutation, overflow,
  browser error, assignment change or hidden request bypass. Evidence:
  `engage-browser-staging-1794428534355/result.json` and masked screenshots.
- First production browser run passed public lookup but its verbose protected
  read request received `INFORMATION_PROVIDED / Okay.` with no action or typed
  authentication requirement. No private call, state mutation, browser error
  or data disclosure occurred. This is a real semantic/UX miss, not a passing
  challenge test. Evidence: `engage-browser-production-1794429165889/result.json`.
  A separate fresh run uses the natural question "What is the current status
  of my subscription?"; all auth, identity, receiver and no-write assertions
  remain strict. No production matcher, forced intent or automatic retry added.
- Second production automatic-challenge run also failed that expectation:
  the natural private-status question used only the public guide and admitted
  that no private status was available. No private call, disclosure or mutation.
  Evidence: `engage-browser-production-1794429217759/result.json`. Do not
  represent these two failed prompt-trigger checks as passing or retry until
  success. A future reviewed improvement may guide model selection of protected
  READs/authentication requirements; no additional framework fix is included.
- Production release `rel-b014f654` is `APPLIED_VERIFIED`; fresh normal recheck
  passed **27 / 0 failed / 7 legitimate skips**. Its original public question
  returned INFO with one public READ (`production-chat-1794429119474.private.json`).
  Normal signed-in chat read the actual `CANCELLATION_SCHEDULED` state with one
  private read and zero writes (`readonly-conversation-probe-production-1794429131941.private.json`).
- The same Engage app was normally bound to production, still all 16 readiness
  checks passed. A **separate explicit Sign in button journey** passed all
  7 browser checks: actual Supabase sign-in, matching broker identity, real
  own-subscription READ, desktop/basic phone, no overflow/browser error or
  state mutation. Evidence: `engage-browser-production-1794429308550/result.json`.
  This proves the normal explicit sign-in path, not reliable automatic prompting.
- Final 0.8.49 authority/ownership run passed **9/9**, 46 requests, real provider
  and broker identities, zero additional writes. Its source is the actual
  staging runtime's execution key; a verified production target exists and
  that staging key was denied for production. This does **not** claim a live
  reverse-direction production-key probe. Missing/wrong keys, foreign Console
  authority, altered scope/expiry, spoofed actor arguments and actual foreign-
  object cancellation were checked. Three new local login sessions signed out.
  Evidence: `runtime-security-runs/20261111203611521-ea4a0b75/evidence.json`.
  Expiry perturbation remains distinct from natural JWT-expiration testing.
- Final cleanup has started: exact owned Engage app and consumer deleted,
  both active journey connections disconnected and the exact temporary
  business credential removed. Brain/provider-resource deletion is requested;
  shared services, existing customers and the first identity are preserved.
- Normal hard-delete operation `del-b64d0d44` initially failed with
  `COOLIFY_RESOURCE_STORAGE_RETIREMENT_REQUIRED`: storage-bearing resources
  need separate retirement authority, and legacy automated retirement is
  intentionally retired. This is not an MCP failure or a green automatic-
  cleanup result. The two storage-free connectors were already deleted.
- Operator inventory proved the only remaining resources were the exact two
  canary runtimes and two AI-state databases, with four uniquely named owned
  volumes and no foreign container using them. Under the owner's disposable-
  fixture deletion approval, those four resources/volumes were retired through
  Coolify; no global Docker cleanup, shared network deletion or SQL mutation.
  Each native resource and volume was verified absent. The same failed
  Platform deletion operation was then retried through its ordinary endpoint.
  Evidence: `owned-retirement-inventory.private.json` and
  `operator-retirement.private.json`. Automated customer storage retirement is
  not claimed and remains separate from this MCP implementation.

## Final Outcome And Cleanup

- Public and company-authorized paths are implemented/deployed. No-vector
  INFORMATION works in real chat on both released targets; confirmed business
  cancellation, rejection and real same-business/other-workspace isolation
  passed. End users use ordinary app identity, not another MCP login.
- Browser qualification is mode-specific: staging's typed action-time challenge
  passed; production's **explicit Sign in** path passed. Two production automatic
  prompting misses remain recorded above. The first acknowledgement was not
  fully traced to raw model output versus normalization. Do not assert that
  every question automatically opens sign-in or hide the failed receipts.
- Default confirmation/result UI can show technical action/actor/server IDs
  and schema diagnostics; observed in real screenshots. These are not upstream
  credentials. Presentation improvements require separate review and were not
  added to the bounded routing patch.
- Exact storage disposal succeeded through the operator path. Normal deletion
  retry creates a **new** operation: del-ac4c1582 SUCCEEDED, linked to the
  retained failed del-b64d0d44. The helper was corrected to track the new ID;
  polling the old failure was not a second product failure. No SQL state edits.
- Final cleanup proof passed six groups: customer/API lists and seven read-only
  database counts all clear; six owned target resources plus fixture absent
  from native API/all container states; four exact volumes absent; fixture
  source/mount absent; second test identity 404 and original identity 200;
  production Console/API 200 and both shared backends healthy. Audit/history,
  reusable published artifact and existing customers are deliberately retained.
  Evidence: `final-cleanup-evidence.private.json`, `deletion-success.private.json`.
- Point host samples after cleanup: staging load1m **3.01**, available RAM
  **3,533 MiB**; production **1.66**, **8,598 MiB**. Not a capacity benchmark.
- No new framework work is pending in this implementation. Automatic prompting,
  technical result presentation, operator-only storage disposal and unrelated
  Incident Room CI remain declared limits. Personal-account MCP remains deferred.

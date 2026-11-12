# MCP Public And Business Connection Qualification

Implementation plan: 010.69. Public and business MCP paths are implemented and
qualified on released framework 0.8.49. This record separates source tests,
hosted control planes, protocol checks and actual customer journeys. Automatic
sign-in prompting has two documented production semantic misses; explicit
sign-in and signed-in reads passed. Cleanup completion is recorded at the end.
Private credentials, tokens, cookies and raw logs are not part of this directory.

## 0.8.49 Deployed Baseline

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

## 0.8.50 Auth And Answer-Contract Follow-up (2026-11-12)

This scoped follow-up supersedes the earlier automatic-prompt status, not its
historical failed receipts. It is not an all-green semantic-quality claim.

### Source And Verification

- Framework `aaaa5913b2e3879a18756e446e3d51929b535b02`, immutable 0.8.50,
  Central publication `43128536259` succeeded. Local Java21 release reactor
  2,413/2,413, focused source/prompts77/77, guards7, actual vector containers8
  plus18 units passed. The publisher's final reactor had two skipped PostgreSQL
  lease tests because its old Docker client API was unavailable on that runner;
  both executed locally. Do not describe that publisher run as zero skips.
- Real OpenAI `gpt-5.4-mini`: native initial8/9, final9/10; final12 orchestration
  turns plus one real supplied-INFORMATION planner call. Private auth/nullable
  reads and confirmed/rejected/no-confirmation writes passed. One public-guide
  case still chose model-authored acknowledgement/ANSWER_FROM_CONTEXT instead
  of a READ; no assertion or model decision was rewritten. Core general/indexed
  matrix11/11 passed (normal85%/min20, existing11-case clamp).
- Empty-cache Central-only consumer15/15, package/Java21 boot and source/Javadoc
  availability passed. Four packaged framework JARs matched public-cache bytes.
  Minimal, agentic5/5 and selected MCP reference-server6/6 consumers passed.
- Both backends are `b334440c04ce85ddd1e335caae89d1ca89a87e98`, digest
  `sha256:b01df499f7166a4eba6adc456b8be680e8b62ff005e1f75da9a94b1c7ddf67a7`.
  UI and gateway remain unchanged from the earlier qualified sources.
- Runtime source `b3197404d692265d815a94fcbb67e66dd6d1962d`, CI`43131305652`,
  artifact `dsa-67fc22ca`, digest
  `sha256:0d45bc52742744a9f4dc13c61b136d35cb3fb18c207fb95a1e8dce57304c5d62`.
  CI accounting78+103 and runtime814 passed without failures/errors/skips.
  Ordinary connector102 and Platform compiler/manifest/verifier75 passed locally.
  First runtime CI`43130850584` failed a pre-existing test-only capture race;
  synchronization now waits for the captured body as well as request arrival.
  No production transport, ledger, limits or assertions were weakened.
- Broad framework CI`43128510006` has exactly the same14 Incident Room failing
  test names as the previous baseline, no additions/removals. Registry, reactor,
  integration compilation and vector-container gates passed; the entire workflow
  is not green. Later example gates were not reached after the baseline failure.

### Live Checks And Diagnostics

- Fresh Brain `dep-f52626dc`, version `ver-b213721b`, stage `rel-4f64c49f` reached
  APPLIED_VERIFIED/ACTIVE, post-apply27 passed/0 failed/7 not applicable. The
  normal managed-host Verify action qualified its new Engage address; initial
  activation409 correctly blocked a pending hostname. Activation then succeeded.
- First live public-guide query failed source-use quality: model USE_RAG_ONLY,
  empty vector catalogue/cooperationNONE, zero tool calls, then an honest
  missing-evidence answer. This is distinct from the framework fixture's
  acknowledgement and is retained in `chat-runs/1794446222425-d313a95a`.
- Explicit continuation did not repeat that question. Nine auth/write/foreign
  turns passed: real anonymous INFORMATION with retrieval/generation false and
  missing direct answer -> model REQUEST_AUTHENTICATION -> typed host sign-in,
  zero protected calls; real sign-in/own read; rejection0writes; confirmation1;
  second-user own read and actual foreign-object denial. Status explicitly
  retains the prior public-quality failure, not a full-journey green result.
- Normal Engage and genuine native SDK, each exact original natural/verbose
  question: automatic-auth dialogs4/4 passed; verbose signed-in reads2/2 passed.
  Natural signed-in reads FAILED2/2: authenticated=true, no auth-required READs,
  but model REQUEST_AUTHENTICATION. Server rejected that invalid choice and
  returned a visible missing-answer error with zero tool calls. It did not
  silently convert the decision into execution or a successful `Okay.`.
- Full eight browser query responses, real auth/dialog/debug screenshots and
  all API response metadata were retained. Browser/capture errors0, no extra
  mutations, local sign-outs confirmed. Native `features.debug=true` exercised
  the actual published SDK inspector. Saved generic Engage still defaults its
  inspector off; no unsupported backend query-debug field or global public
  debug setting was invented. Safe `intentDecisions`, `answerSource`, READ
  planner/rejection diagnostics and request timings are available for tracing.
- Production `rel-d46f38a1` uses the same immutable version/artifact and reached
  APPLIED_VERIFIED/ACTIVE. Post-apply `vrf-8e57a85b` passed27/failed0/not-applicable7;
  staging post-apply is `vrf-b66b2f25`, same counts. Normal production app binding
  passed all16 readiness checks. Production's normal saved Engage browser
  passed4/4 exact-original queries: automatic-auth dialogs2/2 and signed-in
  owned reads2/2, with real broker identity. Native debug was qualified on
  staging, not repeated on production. All12 browser HTTP responses retained;
  total automatic dialogs6/6 and signed-in reads4/6, with both staging natural
  failures preserved. Production browser errors0, no extra mutations, both
  local sessions signed out. Evidence: `engage-browser-runs/production-1794447259120-9e2b4da4`.
  Exact new-fixture cleanup completed as detailed below.

### Exact Follow-up Cleanup

- Normal hard-delete `del-d16f3a62` removed the storage-free connectors but
  stopped at the existing attached-storage safeguard. Exact operator disposal
  removed only this canary's two runtimes, two AI-state databases and four
  verified owned volumes. No global prune/network deletion, SQL write or
  safety-check bypass. The normal retry created `del-9da33ff6` and SUCCEEDED;
  the original failed operation remains in audit.
- App/consumer, both MCP connections and owned key, fixture app/mount/source
  and only the tagged second Supabase identity were removed. Initial final
  observation saw the fixture's asynchronous deletion still pending; retained
  as `initial-cleanup-observation.private.json`. Native404/container absence
  then confirmed completion, without another delete or any repeated AI query.
- Final read-only proof PASS: eleven exact database counts0, customer lists
  clear, all six target services plus fixture absent, all owned containers and
  four volumes absent, second identity404/original owner200. Both backends,
  published artifact and exact reusable runtime image retained. Shared networks
  and historical audit records are deliberately preserved.
- Evidence: `final-cleanup-evidence.private.json` and
  `operator-retirement.private.json` in the new private sidecar. No verification
  agent, paid query or disposable canary remains running.

The signed-in invalid-auth choice and public-source selection require review
before further framework changes. The simplest candidate is to let the model
select a READ while the server derives authentication from that selection and
trusted session state, instead of asking the model to select authentication as
a separate operation. The existing protected-selection branch already supports
that derivation. This is a proposal, not an implemented second patch. Explicit
trusted-auth guidance or allowed bounded validation feedback are alternatives;
do not add keyword rules, auto-execute a rejected choice, or retry until green.

Raw new receipts are private under `~/.codex/private/loomai-framework-0.8.50/`
and `~/.codex/private/loomai-mcp-read-auth-20261112/`; no credentials are committed.

## Retained Planner Misses Investigation (2026-11-12)

Investigation only: no framework/runtime/Platform behavior, configuration,
release or deployment was changed. No new provider calls or business-tool calls.
The separately retained Harbor Office manual lab was not used or changed.

### Authenticated Natural Question

Both failed staging receipts are the same natural subscription question, one
normal Engage and one native-debug request. Both have authenticated=true,
authenticationRequiredReadActionNames=[], historyMessages=0/historyCharacters=0
and no active targets. The model selected the correct private READ action name
but the wrong decision, REQUEST_AUTHENTICATION, with empty public parameters.
This is not stale anonymous history, a lost login, a hidden missing user-entered
ID or an attempted private API call. Actual receiver calls remained zero.

The default pack and optimized user overlay were selected, not the support
overlay. Trusted catalogue construction supplies authenticationRequired=false
and executableNow=true for the signed-in request, and the system prompt already
explicitly forbids requesting authentication in that state. Nevertheless, the
new 0.8.50 REQUEST_AUTHENTICATION option is accepted by decision parsing. The
authenticated protected-selection helper returns no challenge; the following
AUTHENTICATION_ACTION_NOT_ELIGIBLE branch immediately breaks. It does not enter
the ordinary bounded iterative action-proposal feedback path. With no READ
evidence, RAG disabled for this planner, and no extracted direct answer, the
information handler reaches MODEL_DIRECT_ANSWER_MISSING.

The static action description says sign-in is required. The optimized prompt
also conditions private selection on schema evidence of trusted context, while
the actual public input schema hides the internal actorId mapping and exposes
empty properties/required fields. These are demonstrable competing/incomplete
cues. They are not proof of the model's internal reason for choosing auth.
Production's successful natural request used the same published version and
artifact and equivalent recorded policy/auth flags, but extraction supplied a
candidate direct answer there; model inputs were not byte-identical. Do not
claim a deterministic infrastructure difference or a proven description-only
cause. A successful production sample does not erase either staging failure.

**Smallest recommended correction, not implemented:** have the model select the
needed READ, then derive authentication from trusted server state. Remove
REQUEST_AUTHENTICATION from model choices, not from the framework's typed host
authentication outcome. Existing selectedProtectedRead already challenges an
anonymous EXECUTE_READ_ACTIONS selection before parameter resolution/invocation;
the independent execution guard remains. Clarify that hidden runtime parameters
are supplied/checked by the server; do not expose identity values or require the
model to prove an intentionally hidden binding. This is simpler than adding an
authentication-specific correction loop or overriding an invalid model answer.

### Public Policy Question

The public cancellation-guide READ was enabled, anonymous, grounding eligible
and allowlisted. Its description explicitly mentioned subscription guidance and
cancellation timing; its only public input was a query string. It was present
in the actual eligible-action diagnostics. No parameter, ownership, upstream
credential or execution rejection occurred: rejectedActionProposalsCount=0 and
zero tool calls. The model selected USE_RAG_ONLY with needsMoreSteps=false.

The catalogue reported retrievalEnabled=true because that field represents
capability permission, not actual source availability. The same prompt had an
empty authorized vectorSpaces list and rag cooperation mode NONE. The generic
prompts still advertised USE_RAG_ONLY and recommended RAG for broad knowledge
where READ is not a better fit; they do not explicitly constrain the choice to
an executable indexed path for this turn. The server's shouldUseRag correctly
returns false for NONE, so the model-selected RAG path did not run. No proposal
was rejected or feedback given, and planning stopped after one iteration even
though maxIterations=2. Extraction's generation=true then led to generation-only
with no evidence. The model honestly reported missing approved facts. It did
not answer from the public tool because that tool had never been called.

**Smallest recommended correction, not implemented:** distinguish permitted
retrieval from a currently usable indexed route in the planner catalogue and
guidance, incorporating cooperation policy and authorized source availability.
Do not advertise an executable RAG route when that route is disabled/unavailable.
Keep the model choosing the relevant READ/source; do not force a particular
action, enable search-all or widen permissions. Merely enabling RAG cooperation
does not create missing authorized sources and is not a complete fix. If a
remaining invalid plan gets correction feedback, it must use an explicit bounded
policy and preserve the original decision; no silent retry/reinterpretation.

The separate native public-guide failure selected ANSWER_FROM_CONTEXT with an
actual model-authored acknowledgement. It is related source-selection quality,
but not the same decision/control path as this hosted USE_RAG_ONLY miss. Do not
merge those two failed receipts or claim that making the catalogue clearer has
already fixed either semantic outcome.

### Verification And Coverage Gap

- Six offline reproductions passed against the existing 0.8.50 core classes and
  actual default classpath prompt resolver. Only model decisions and business
  handlers were supplied locally; no real inference, network or original
  provider-request replay is claimed. Rendered prompts are reconstructed from
  matching recorded contracts, not captured historical provider wire payloads.
- Reproduced signed-in invalid-auth stopping after one call; anonymous ordinary
  private READ selection causing a challenge with zero handler calls; signed-in
  ordinary private READ executing once with trusted identity; public unavailable
  RAG producing no evidence; enabled cooperation still not inventing sources;
  and explicit public READ yielding one locally supplied evidence result.
- Existing focused suites reran on Java21: ReadActionAuthenticationPlanningTest9,
  ReadActionPlannerContractTest19 and policy-tolerance2, total30 passed/0 failed/
  0 skipped. These tests do not establish model semantic quality. Signed-in
  tests supply a correct model choice; the native real signed-in case uses
  explicit tool instructions. Add the natural anonymous -> login -> identical
  natural question to real-provider qualification for the proposed correction.
- Add source-route cases for READ-only/no vectors, indexed retrieval available
  and unavailable, and mixed READ/indexed questions. Preserve disabled/foreign
  actions, trusted identity, write confirmation and actual vector permissions.
  A setup/schema test cannot replace these semantic and execution checks.
- Raw offline evidence stays outside the repositories in
  `~/.codex/private/loomai-planner-investigation-20261112/`. Source remains
  framework aaaa5913 / 0.8.50; no new framework patch is approved or published by
  this investigation.

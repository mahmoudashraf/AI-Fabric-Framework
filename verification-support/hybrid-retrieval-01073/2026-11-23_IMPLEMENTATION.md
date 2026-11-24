# 010.73 Implementation Record

Date: 23 November 2026. Updated 24 November 2026.
Current status: framework 0.8.67 tag/release, push build and Central artifacts
are verified. The original upload completed without a replacement deployment.
Staging/production software adoption, real staging hybrid/Cohere retrieval and
normal deactivation are verified. Production retains its original search mode;
paid reranking finishes OFF on both targets. Overall010.73quality and remaining
implementation packages are not complete. The initial
checkpoint below is historical; subsequent dated sections record later work.

## Final Current Slice - 24 November 2026

- Public framework0.8.67, nine Central JAR checksums and fresh Central-only
  consumer tests passed. Public Maven push build45292383299is green onf933227.
  The original Sonatype upload completed; no immutable version was overwritten.
- Native production artifactdsa-69af59c3, image digest83389d3f...; Support
  ver-ba013240/rel-91b8a237 APPLIED_VERIFIED/PASSED, existing consumer rebound.
  Actual container image is the qualified0.8.67artifact. Its20feature-owned env
  keys are absent. Existing draft sections, runtime/connector/database IDs,
  app URLs/configuration/origins/domains and14active source versions retained.
- Production backend239bf13ea jobl13lisapbqcvwt0y8323dfdq, Console18b8ead41
  jobnjdy0jkab9hfwfvn1hjnps3w and shared authoringbe8a3ccd4
  jobb69i0gqkl2fk9hnp01drlxx2 passed native image/health verification. Each core
  role's environment, resource limits and mounts match its fresh before snapshot.
  No infrastructure capacity/billing/protection change was requested or made.
- Both full backend regressions report6127tests:6126pass,0fail/0error/1skip.
  The skipped service-process test needs deploymentOperationsJar. Local26:49
  and hosted45331818254/15:41passed;102focused tests passed. Image45331783556
  passed. Retained background closed-context DB/scheduler logs and Surefire
  shutdown warning did not change the actual zero exit/test result.
- Normal stage ON ver-d94b7c20/rel-e57a63a7 and OFF ver-03b727dd/rel-6a743235
  both passed and bound the same consumer. ON had20actual keys/40Coolify rows;
  OFF has0/0. Same image/mounts/limits, existing wallet history retained. This
  accepts the product fix, not the earlier manual containment.
- Five fresh normal-chat questions expose native HYBRID/Cohere diagnostics and
  approved active owned documents. A sixth used specialist-action evidence;
  the original harness incorrectly treated all ragResponse.documents as vector
  evidence. Its failure is retained and only remaining questions were rerun.
  Answer quality was mixed; general how-to questions sometimes became live
  diagnostics despite correct guides being retrieved. Do not claim improvement.
- Fresh OFF conversations6/6success and direct retrieval proofs6/6success on
  both targets, expected source present in top6, frozen corpora unchanged.
  Procedure answers still contain older navigation/connection wording and some
  unrequested live-diagnostic details. These are documented, not post-filtered.
- Stage exact-wallet isolation: real foreign Support draft, source preview and
  retrieval-proof requests404; that SAME Support wallet stayed7/7attempts with
  identical usage. Production repeats all3denials. The earlier harness correctly
  denied a different foreign Brain but watched Support's wallet; retain it as
  authorization evidence, not standalone proof of that other runtime's usage.
- Production Support15turns succeeded, including4direct specialist areas and4
  requested plans. Knowledge area/plan requests chose permitted compound direct
  diagnostics instead;8/10exact requested selections observed. Actual runtime
  registry contains all5areas and5plans. Facts were useful, but selection fidelity,
  optional-handover readiness interpretation, unnecessary resource-selection
  advice and accepted-check narration remain quality findings. Revoked consent
  returned DIAGNOSTIC_CONSENT_REQUIRED and empty resources, not resource facts.
- Pre-upgrade canonical transcript/completed answers survived; same conversation
  resumed after reload. Original conversation restored, consent revoked, normal
  customer signed out. No customer business API action or write was executed.
- Production existing Build with AI5core casesPASS, including follow-up/Undo
  and behavior preservation; all5resource-free fixtures/sessions deleted normally.
  New SEARCH request-fidelity finding from staging is not claimed fixed. The
 010.75real-Brain migration and its proposed framework changes were not started.
- Native optional Console-access form passed on both environments, including
  save/reload/toggle, visitor-mode preservation and phone layout. Private fixture
  definitions archived with0versions/0installs, actors signed out. Existing
  Support source authModes unchanged; Console permission approval is still open.
- Shared authoring effective caps remain185turns/2Mtokens/USD5each, with counts
 166stage and159production. No cap, expiry or wallet history reset. Cohere last
  stage snapshot16calls/177824inputtokens/3556480nanoUSD, plus stopped local
 11360nanoUSD = USD0.00356784accounted ofUSD10. No production reranking grant
  issued. This is accounting evidence, not a provider invoice.
- Remaining: comparable Ragas calibration/cutover, reviewed quality/default
  qualification, Milvus lifecycle, entity/mixed evaluations and durable bulk
  coordination. Source access approval is one input, not the only remaining work.
  Assistant operational tools depend on reviewed010.75architecture, not another
  custom Platform generation loop. Do not mark the whole010.73plan complete.

## 24 November: Published Release Adoption

- Maven Central now serves immutable 0.8.67 from the original Sonatype upload.
  Nine actual runtime JAR checksums were verified. A fresh Central-only cache
  passed the minimal consumer package and agentic consumer tests. Public push
  build `45292383299` passed on `f933227`; no replacement upload or moved tag.
- Private feature source `be8a3ccd425132ad8fab340956dcb9fd993873ca` pins 0.8.67.
  Public Apply accepts the explicitly reviewed target/artifact, retains the
  original ownership/review/funding gates and only replays an exact match.
- Full local Platform: 6,118 reported, 6,117 executed pass, one skip. Runtime:
  885 reported, 884 executed pass, one paid opt-in skip. Console: 71 tests,
  TypeScript/build and fixture-backed document browser journey pass. These are
  not hosted retrieval quality results.
- Hosted core images `45319309383`, runtime `45319309561`, storage `45319309480`
  and environment-quota `45319309490` passed. Initial full code regression
  `45319367399` failed on the Docker client API and two immediate-worker retry
  assumptions; no failed run was counted as green.
- Test-only `2ac2d536` pins the CI runner/API and waits for the existing no-queue
  domain probe worker to become ready. Its full hosted run `45320761143`
  uncovered nanosecond JVM versus microsecond PostgreSQL fixture assumptions.
  Test-only `70d46dee8` gives the frozen route clock database-compatible precision
  and compares grant timestamps within one microsecond while keeping all other
  grant fields exact. Routing, access decisions and production workers did not
  change. The affected real-PostgreSQL suites passed all 45 tests locally.
- Full hosted regression `45322629657` is running on `70d46dee8`. Its production
  inputs are identical to the built feature source; the complete delta is four
  CI/test files. Rollout remains blocked until this run passes.
- Native artifact inspection passed: runtime digest
  `sha256:83389d3fffcb6c0fc36434091d9dd9d47db3215bc1df220be066dda369b3cbb1`,
  matching declared capabilities, all nine embedded Central JARs and a
  no-network Ragas 0.4.4 import. This is image qualification, not a serving
  upgrade or real-model Ragas calibration.
- At this checkpoint no serving rollout, live Cohere grant or paid call has
  occurred. The existing aggregate USD10 qualification partition, authoring
  limits, original Harbor resources and Support document versions are unchanged.
  010.75 assistant migration remains a proposal, not part of this release.

## 24 November: Hosted Staging And Deactivation Qualification

- Full hosted code regression `45324308971` passed after the retained CI/test
  failures above. Existing serving runtime/backend were upgraded on staging,
  schema247, with unchanged infrastructure, mounts, caps and app identities.
- Support `rel-7a6f60c8` passed verification. Real normal Support chat proved
  shared HYBRID/RRF plus Cohere Fast reranking,18authorized candidates and
  31,714/32,000context characters. Seven normal Support turns, specialists,
  resolution-plan invocation, history reload and consent revocation passed.
- Known-real foreign customer/Brain/document proof requests returned404 with
  no provider-attempt increase. Ordinary actor login/logout was used; source
  owner, approved-publication and active-version boundaries remain enforced.
- Existing Build with AI scenarios passed and all five temporary fixtures were
  deleted. New SEARCH request fidelity did not pass: requested12candidates/
  6evidence but model saved maxResults12. This is retained, not silently repaired
  by output rewriting. The separate010.75assistant migration is deferred.
- Console source evaluation is permission-blocked: Support's source excludes
  PLATFORM_PROXY_SESSION. Scoped owner approval is pending. No source access was
  widened to make the test pass. Real Support chat uses an already approved mode.
- OFF `rel-97112a79` passed release verification but native inspection caught
  retained ON env settings: generic upsert did not remove omitted feature keys.
  Temporary same-image containment removed only40standard/preview rows for the
 20exact Platform-owned keys. Six fresh ordinary retrieval proofs then passed
  without reranking. This is containment, not product-fix acceptance.
- Fix `239bf13ea` reconciles only exact feature-owned keys, preserves desired and
  unknown settings, and requires verified readback before deployment.102focused
  tests pass. Full local and hosted/backend-image checks remain in progress at
  this checkpoint. Production remains unchanged on66; a safe unchanged-search
 67upgrade is gated on native product-fix ON-to-OFF verification.
- New optional Console-preview form was also native customer-UI tested on
  stage18b8ead41, with desktop/mobile and preservation checks. The resource-free
  private fixture is archived,0versions/0installs, actor signed out. No current
  Support source configuration changed.

## Scope And Preservation

Owner approved the 010.73 implementation. No infrastructure, paid capacity,
funding contract, original document, existing app or existing Brain was changed.
The owner practice Brain `dep-000748a0` and the original Harbor deployment were
not edited or reset. Shared Build with AI test turns were not consumed.

Public source is isolated at:
`/Users/mahmoudashraf/.codex/worktrees/hybrid-retrieval-01073`, branch
`codex/hybrid-retrieval-01073`, based on public 0.8.66 source `192caaca`.
Its candidate version was `0.8.67`; that immutable version is now available on
Maven Central. Initial private checks used an explicit local override; the source
default now selects 0.8.67. Hosted runtime adoption is still separately gated.
Unrelated private worktree changes and the other public checkout were preserved.

## Implemented Locally

- Core typed retrieval policy with finite per-leg candidate, total/final result,
  deadline and context-character bounds; per-corpus lexical readiness contract.
- Shared independent dense/BM25 legs and equal-weight RRF, with deterministic
  structural identities retaining source, entity type, source version and chunk.
- Separate vector, lexical and fused score metadata; no cosine threshold applied
  to BM25 or RRF, no claimed confidence percentage for typed retrieval scores.
- Explicit strict versus visible dense-degradation policy. Authorization failures
  do not become optional availability failures. HTTP 408/502/503/504 may be typed
  as lexical availability failures; no credential/provider fallback is introduced.
- Weaviate native BM25F over the approved `content` field only, using the same
  configured native tenant and portable metadata filters. No schema mutation,
  server change or document reindexing is performed by capability/search reads.
- Shared/private runtime source adapters apply source, request owner and active
  document-version filters before returning either leg for fusion. Existing
  unprofiled search behavior remains on the existing path.
- Explicit-profile Advanced RAG delegates to the shared path once, without
  legacy query expansion or embedding rescoring adding another ranking stage.
  This does not yet close orchestrator-level multi-space fusion; see limits.
- Search settings controls in the existing Providers editor/shared draft,
  backend validation and compilation to the real `ai.infrastructure.rag` binding.
  Unsupported stores cannot masquerade as hybrid in this initial slice.
- Context selection retains identity/attribution, reports truncation and includes
  headers when applying its character bound. It never modifies original records.
- Widget typed score labels show meaning/word/combined matches; raw typed scores
  remain inspectable without being converted into confidence percentages.
- Optional provider-neutral `RerankingProvider` and mandatory host accounting
  bridge, optional Cohere module and shared post-fusion/pre-context ranking stage.
  OFF by default, with explicit model matching, no credential/provider fallback,
  bounded excerpts/concurrency/deadlines, one native send without retry/redirect,
  input-reference-only results, and conservative holds for unverified outcomes.
  This is not the completed LoomAI ledger/secret/configuration integration.

## Verification

- TypeScript checks passed for Console and widget.
- Backend settings/validation/provisioning/compiler regression: 197 passed;
  backend compile passed.
- Runtime scoped source/registry tests: 25 passed, including both source adapters'
  foreign-owner, wrong-source and retired-version exclusions before fusion.
- Full framework core/memory/RAG/Weaviate/Cohere regression: 1,353 passed, zero failures
  or errors; one explicitly opt-in live probe skipped in the ordinary unit run.
  Breakdown: core 1,214; memory 16; RAG 80; Weaviate 32 executed plus one skip;
  Cohere adapter 11. Includes shared learned-ranking integration, unknown/duplicate
  references, pre-send budget denial, missing usage, no retries and secret-safe
  failures. Cohere contract tests use synthetic native responses, not live calls.
- The full run caught a record-constructor binding issue after adding a
  convenience constructor. The canonical constructor now has explicit Spring
  constructor binding. The final full rerun passed; earlier failed runs were not
  treated as passing or omitted from the qualification findings.
- Native read-only Java adapter probes passed against staging and production:
  actual Weaviate 1.40.3, existing corpus text index readiness, stored literal
  retrieval, non-percentage BM25 score receipt and zero results for a nonexistent
  source handle. These are native adapter probes, not end-to-end chat proof.
- The initial staging `20 MiB` assertion failed. A read-only frozen inventory
  confirmed that literal was absent in staging but present in production. That
  failure remains recorded; the subsequent probes use confirmed corpus literals.
- Browser checks: light/dark, normal desktop, 390px and 360px; advanced controls,
  invalid numeric feedback and explicit failure-policy selection. A nested grid
  overflow was found and fixed. At both phone widths document width matched
  viewport width. The temporary local harness/server/tab were removed/closed.

Private probe configuration, original inventory and test logs are under:
`~/.codex/private/loomai-hybrid-retrieval-01073` (private permissions).
Never commit those provider keys, signed artifact addresses or document payloads.

## Remaining At The Initial Checkpoint

This is not a completed 010.73 implementation. Required work still includes:

- Milvus server/SDK/native schema qualification and additive generation lifecycle.
- Higher-level multi-space/mixed-corpus budget/fusion integration and real chat
  evidence handoff; the generic orchestrator still has its existing space merger.
- Shared document/entity/mixed evaluations using the real Ragas worker and metered
  bridge, reviewed hold-outs, comparable dense baseline and actual quality/cost.
- Source inheritance links, comparison inspector and the assistant's typed search
  authoring/optimization integration; no inert comparison buttons were added.
- Exact-snapshot paged bulk plans, confirmation, existing-worker coordination,
  cancel/resume/retry/promotion and assistant operational capabilities.
- Provider-neutral learned reranking, approved optional hosted recipient/key/
  spending settings and actual provider accounting are not fully closed: core,
  adapter and shared ranking stage are local, but the LoomAI ledger bridge,
  secret materialization/UI and live qualification remain. No Cohere call was made.
- Compatible immutable release, staging/production rollout, normal customer UI,
  Support/Harbor/Engage and Build with AI regression qualification.

Do not promote new defaults or claim quality improvement from the native lexical
checks alone. Existing configuration remains the serving authority.

## Continued Implementation And Budget Approval

This section supersedes earlier pending-key/budget and unimplemented multi-space
notes above; those notes retain the chronology of the first slice.

- Owner approved USD10 TOTAL Cohere qualification, across runs/environments.
  Not a per-environment grant, ongoing production allowance or infrastructure
  approval. Other provider/Build with AI budgets remain unchanged.
- Restricted private key material passed native authentication. Two real native
  reranking calls passed: Fast 84 input tokens/1,680 nano-USD and Pro 80 input
  tokens/4,000 nano-USD, aggregate USD0.00000568. Both ranked the relevant
  infrastructure-policy excerpt first. Public/synthetic inputs only; not an A/B
  evaluation or claim of improved live Support answers.
- Local qualification uses a persistent file ledger, never reset between runs.
  Before environment funding, freeze that local admission and allocate only
  portions of the remaining aggregate USD10; never create USD10 per environment.
- Runtime reuses the existing immutable ledger and activation protocol, with a
  separate singleton reranking binding (V26), explicit v5 token rate card,
  pre-send worst-case reservation, native settlement and unknown-outcome holds.
  Generation binding/approval is not reused or changed. No approval is created
  merely from startup environment variables or a draft switch.
- Platform reuses its existing audited grants, separating them by the declared
  reranking secret purpose. Owned override, published configuration, explicit
  recipient consent and a reviewed model are required. Financial coexistence
  tests passed; reranking cannot consume generation credit or a global key.
- Canonical Search settings now include model/consent/bounds/failure policy and
  exact applied-target status. Native unavailability is not displayed as zero
  spending. Console typecheck passed after replacing an unsupported string API.
- Shared-profile chat fan-out now makes one multi-space RAG request with one
  combined leg allowance, fusion and reranking. Wrong-space candidates stop
  before external ranking; the legacy merger/cosine-clarification path is not
  applied to RRF scores. Failed profiled retrieval does not start answer generation.
- Optional availability fallback preserves the original authorized order only
  when explicitly configured. Auth, budget, malformed usage and reference
  failures are not downgraded. HTTP transient failures retain accounting holds;
  there is no retry or alternate credential/provider.
- Ragas dependency locked to 0.4.4. Five offline tests passed using its actual
  Faithfulness, AnswerRelevancy, ContextPrecision and ContextRecall procedures.
  LLM replies/embeddings were scripted fixtures, not live quality evidence.
  Worker network calls are prohibited, inherited process credentials are cleared
  by the Java bridge and metric subcalls use the runtime's funded inference path.
  Further Java protocol/security tests and image qualification are in progress.

No source push, immutable framework publication, deployed version adoption,
live configuration/activation, infrastructure change or owner practice-Brain
mutation has occurred. The larger remaining-work list is not closed by these
local checks. Private logs include failed compile/typecheck attempts as well as
their corrected reruns; failures are not counted as passing qualification.

## Cohere Approval And Credential Lookup

Owner approved Cohere as the recipient and said private credentials/limit would
be supplied, then requested checking the private auth document. The ignored
public-checkout `.private-release-materials/llm-provider-auth-material-private.md`
explicitly contains names/redacted placeholders, not secret values. Its referenced
private Platform handoff records `COHERE_API_KEY` as present historically but
does not contain its value. Current staging/production backend environments did
not contain the Cohere key. No unrelated key, GitHub secret extraction, secret
decryption bypass or unapproved spending was attempted.

Asked owner for the exact unredacted file location and Cohere spending limit,
not for a secret pasted in chat. Recipient approval is recorded; key availability,
funding limits/rate qualification and live activation are not assumed from it.

## Release Candidate And Extended Verification

This checkpoint supersedes the earlier Snapshot, missing-key, unimplemented
accounting/packaging and unimplemented multi-space statements above.

- Owner approved publication and staged rollout after passing checks, reply
  `call_08f1208d115140619c15ee3040077e04`. No tag or immutable publication yet.
- Public candidate commit `0b8e2c0033ed840a05dfe0d48cc5fe973bf90013` is pushed to
  `codex/hybrid-retrieval-01073`. Hosted verification run `45284954064` is active;
  provider guards and the vector-provider container contract job passed. Wait
  for the remaining consumer/packaged-runtime jobs before publishing.
- Full local release-profile reactor: 2,686 tests reported, 2,682 executed,
  zero failures/errors, four opt-in skips; source and Javadoc packaging passed.
  Minimal consumer compile and standalone agentic consumer (five tests) passed.
- Shared-profile Advanced search now retains the shared bounded context/order,
  diagnostics and host accounting thread; no second legacy context/ranking pass.
  Conflicting content under one versioned identity fails consistently even for
  duplicate rows within one leg. Thirty-five focused tests passed.
- Backend Search assistant/policy/settings/coordinator tests: 90 passed.
  Console page/task/transport/feedback tests: 52 passed; typecheck passed. Search
  is a typed draft task using the existing provider service, not another editor.
  AI may retain actual existing consent, but cannot attest new external-data
  processing approval or create a credential/financial grant.
- Runtime 0.8.67 candidate checks: 76 executed passed plus four opt-in Ragas
  tests skipped in the ordinary run. A separate explicitly enabled run passed
  all 82 assistant/protocol/real-library/native-Cohere tests with no skips.
- Four cumulative real Cohere calls, 328 native billed input tokens and 11,360
  nano-USD (USD0.00001136). Same persistent aggregate USD10 ledger; no reset.
  Native inputs remain public/synthetic policy samples, not answer-quality A/B.
- Linux worker packages Python3.11.18/Ragas0.4.4 through hash-locked dependencies.
  Base image identity before the latest worker-only change:
  `sha256:2482b06e2655f51fbfe0dd46edb2f491a84eb73fcfe26ec409971da67a85fd1b`.
  Its size is 1,214,538,502 bytes, not a final Java serving image. Seven Linux
  offline tests passed with the latest five metric procedures, read-only files,
  UID10001 and no container network; native syscall-level Internet access is
  also denied. Actual inference was scripted, not a real-model quality claim.
- Source evaluation reuses the shared profile with exact owner/source/active-
  version filters. Global Test search was also routed through that same path;
  its latest focused rerun is in progress. Neither uses raw dense fallback when
  the configured shared execution fails. Integral JSON metadata wrapper
  differences are normalized structurally, not via permissive string coercion.
- Historical evaluations remain readable for the same owned source/target,
  retaining their original release identity. Foreign owners/targets remain
  denied, and incompatible engine/rubric/source versions are not compared.
- Read-only live inventory and a six-question dense retrieval baseline were
  captured on each environment using normal own staff login, followed by verified
  logout. Each baseline's active-source/version fingerprint stayed stable.
  Questions are explicitly unreviewed, not fabricated human-reviewed labels.
  No document was uploaded, reindexed, replaced or deleted for these reads.

Remaining broad packages are still open: Milvus native lexical lifecycle,
shared corpus/entity comparisons and reviewed live Ragas calibration, bulk
coordination and assistant operations, Central publication/adoption, and full
staging/production Support/Harbor/Engage/Build with AI regression qualification.
No owner-practice Brain or shared authoring allowance was changed. 010.74 stays
queued, not started.

### Qualification Gate Findings

- Hosted run45284954064 did not pass the whole release gate: Incident Room had
  fourteen policy-denial failures. Core/provider/container checks passed. The
  publication tag is held while tracing the example's caller-versus-incident
  ownership binding; do not dismiss or skip the failures as unrelated.
- Latest shared Test search, source-evaluation scope and five-metric Ragas Java
  rerun passed49tests with zero failures/errors/skips. Seven latest Linux worker
  tests also passed. A missing Mockito static import was corrected first; that
  failed compile is retained and not counted as successful qualification.
- Local Cohere admission is now durably STOPPED through the existing ledger,
  and paid native-test admission is disabled in the private configuration.
  Usage/history/holds were not reset. Remaining approved aggregate:9,999,988,640
  nano-USD. The private partition ceiling is staging5,000,000,000 plus
  production4,999,988,640 nano-USD. These are planned maxima, not issued live
  grants. The saved stop/partition receipt must govern any later allocation.
- The local example reactor encountered full developer-machine disk while
  installing generated example jars. Preserved489XMLreports before removing
  only this task's regenerable example target files and newly built0.8.67
  example jars. No source/user files, existing framework versions, server
  resources or paid infrastructure were deleted/resized. Hosted CI's policy
  failures are separate from that local filesystem failure.
- The Incident Room denial was reproduced against immutable0.8.66. Its example
  policy conflated interactive caller ID with incident ID, and omitted its
  registered chain-manager scope. The example now resolves the caller through
  its owned active session and checks the same tenant/deployment/action scope;
  application-service incident context is still supported. No framework caller
  rewrite, wildcard grant or check bypass. All34focused integration/access
  tests passed twice locally after the correction.
- Public corrective commit20802eae103fc3c2d9d8e6149f4985fc7c87fa79 is pushed.
  Hosted verification rerun45287390265 is in progress on that exact commit.
  It also rejects late native retrieval results after the shared deadline;
  source adapters retain their native transport-timeout responsibility.

### Subsequent Gate And Approved Test Allowances

- Owner reply `call_32ffb12edffa4d3281c62b923a77cfa7` approved exactly25extra
  authoring turns per environment. Both were applied once (160->185), with
  independent immutable topup-receipt and allowance readback. Existing usage,
  approval/store identity, expiry,2Mtoken/$5limits remain unchanged. IDs are
  `retrieval-01073-20261123-staging-25` and
  `retrieval-01073-20261123-production-25`. Do not replay. Normal own staff
  sessions were logged out and their old cookies rechecked unauthenticated.
- Run45287390265 passed the corrected Incident Room but failed Agentic Resolver
  startup. First cause is Networknt3.0.0's numeric-bound type-array coercion
  defect, matching upstream https://github.com/networknt/json-schema-validator/pull/1227.
  Four new regression cases fail before the dependency correction and pass
  with3.0.4, preserving null/strict-type/inclusive/exclusive bound enforcement.
  No required fields or action/schema policy was weakened.
- KEYWORD retrieval now avoids unnecessary embedding/dense calls. A request
  can opt out of configured reranking for a controlled baseline but cannot
  enable a host-disabled provider, consent or financial admission.77focused
  schema/gateway/shared-retrieval tests passed. Full rerun/publication still
  required; none of these local checks is production quality evidence.

### Final Framework Gate And Product Regression Progress

- Public source `f933227e2df814b8648bb1582bcead67f2e92f0f` passed full hosted
  run45289711532. All three jobs passed, including real apps, standalone
  consumers, packaged Docker, handover, data sync and deterministic scenarios.
- Local full release-profile total2694tests,2690executed passed,zero failure/
  error,four opt-in skips. Sources and Javadocs packaged successfully.
- Immutable tag `ai-fabric-framework-v0.8.67` and GitHub release456137600 were
  created after the gate passed; remote main was fast-forwarded without force.
  Central publication run45292489403 is running. Never move/recreate the tag;
  Central-only consumption and private adoption remain unverified.
- Per-source Evaluate variants, exact experiment/model provenance, older-runtime
  capability check and truthful previous-score handling are connected through
  existing runtime/backend/UI services. No automatic triple-run or profile edit.
  Shared assistance receives actual variant/diagnostics/authorized excerpt data.
  This does not complete the entity/mixed-corpus evaluation package.
- Focused runtime68PASS; full runtime875executedPASS plus one paid Cohere opt-in
  skipped. Focused backend56PASS; Console assistant/comparison66PASS andTS PASS.
- The full backend run was interrupted after four older fixture groups failed.
  Causes: mutable deployment-ID mock captured the old ID, missing target_profile_id
  in an isolated test schema, retired Inbox templates and unsupported operation
  enum members. Test-only updates are under focused verification; no production
  policy, catalogue or schema is changed, and the full backend is not green.
  Failed log and reports are retained privately rather than counted as a pass.
- Existing Support runtime limits remain1GiB/512MiBreservation/1CPU on both
  hosts, about657MiBused before rollout. A whole two-process Python harness was
  OOM-killed under256MiB. Running only the actual worker protocol separately
  passed under that same limit: all5metrics,15scriptedsubcalls,224160KiB child
  peakRSS. Earlier unrestricted Linux7test pass remains separate evidence.
  A monitored actual runtime/model trial is still required. No infrastructure
  capacity or protection setting changed.

## 24 November Follow-up

- Framework push run45292383299 is fully green, including Maven Build; manual
  run45289711532 also passed on f933227. GitHub release/tag stay immutable.
- Central upload bd939952-9a29-4fd8-8f38-460e432487d7 succeeded; its workflow
  timed out polling. Owner confirmed PUBLISHING/processing. No duplicate upload,
  tag movement or rollout from a locally installed artifact is permitted.
- Full Platform run:6089reported,6057passed,13failures,18errors,1skip. Failed
  reports archived privately. Most failures are older fixtures/schema mirrors;
  not all are: partner preview exposed an actual authorization-path mismatch.
- Owner approved the scoped partner correction. Approved store delegates use
  exact owned target/resource/endpoint and frozen credentials, with a stable
  member-specific session and five preview-only scopes. No Console role, global
  credential fallback, document-write or prompt-preview permission is added.
  Existing partner HTTP workflow10, new authorization16, authority47, POC41 and
  context25tests pass, including revoked HTTP access. Full Platform remains
  unqualified while legacy apply API/fixture issues are open.
- UI67assistant/comparison tests, TypeScript/build, document browser workflow,
  Brain settings browser and widget typed-score presentation passed locally.
  Screenshots cover light/dark/mobile; search consent starts unchecked and saved
  context/unknown provider settings survive. API fixtures are not live AI proof.
- No serving update or Cohere target grant has been applied. Previous authoring
  topups are already complete once, with the same2Mtoken/USD5 limits. Central-only
  consumption, staging/production quality/security, Build with AI live regression
  and the larger010.73packages remain open.

Final local checkpoint:22/22 auth-type and customer publishing/concurrency tests
passed with no skips. The earlier159passing scoped cases include the corrected
Marketplace template-upgrade workflow, partner HTTP revocation checks, frozen
authority, POC and target context. Partner correction is local commit56abfeaf1,
not pushed or deployed. Do not call this a full Platform-suite pass; reviewed
artifact selection in the old apply test/API remains open. No framework-source
change was necessary after the already-green push build.

Additional regression group completed50/50PASS with no skips at00:49UTC, covering
the corrected migration/setup fixtures, provider-secret deletion, bulk/vector
cleanup, lifecycle restore and runtime-authority persistence. Retained private
log: backend-remaining-fixture-verification.log. This is not a whole-suite pass.
The current public push/Maven Build was reconfirmed successful through GitHub's
API. The exact Central BOM coordinate still returns404, so clean consumption and
rollout remain unqualified. No duplicate upload or additional paid test ran.

## 24 November Continued Implementation

- Three-variant comparison inspector in the existing Evaluate view retains the
  selected historical run and at most one matching completed run per variant.
  Experiment/model provenance and case question/reference/review identity must
  match. Missing scores are not zero. Excerpts, explanations and usage remain.
- Shared SEARCH assistance receives bounded matching comparisons as well as the
  selected run. Viewing the comparison starts no extra evaluations or AI calls.
- Source preparation reports Brain-owned inherited settings and per-corpus
  readiness after ownership checks. Provider failure reports UNAVAILABLE without
  its payload. Non-default experiments require actual shared retrieval support.
  The source links to the one canonical Search settings editor.
- New profiles inherit existing prompt evidence/context budgets, including8items
  and32000characters, unless explicitly overridden. Runtime selection/apply checks
  immutable artifact support for retrieval and reranking/accounting contracts.
- Optional exact-source/version/excerpt relevance labels live in existing saved
  question JSON; no ownership FKs or index data change. Human review is explicit;
  AI cannot attest new/changed review. Exact-ID Recall@k, reciprocal rank and
  graded nDCG are arithmetic, not another model call. Absent/unreviewed/stale/
  no-known-relevant labels are NOT_SCORED; pool/coverage limits stay explicit.
- Evaluation phases persist bounded copies before more work. Cancellation,
  answer/judge failure retain observed evidence, partial results and actual usage,
  never a fabricated answer or replay. Source-specific check/save rules match.
- Verification:69Console unit tests; inherited-budget/settings browser; document
  browser with manual grading/review, four explicit evaluations only, three-way
  comparison, shared assistant, cancellation and mobile/dark passed. Runtime
  ranking/authoring/document group68PASS, partial-receipt group24PASS; Platform
  settings/validation/provisioning/assistant216PASS and label-authority45PASS.
  Full runtime/Platform reruns and UI build are in progress, not pre-claimed.
- Local only: Central still404, private pins66, no new provider call, grant/reset/
  topup, index change, hosted image or infrastructure change. Entity/mixed
  evaluation, Milvus generation lifecycle and durable bulk/assistant operations
  remain incomplete; this does not close the full010.73 plan.

Final continuation checks at01:48UTC:

- Console71tests, TypeScript and production buildPASS. Document browser also
  covers an older runtime: no unsupported variants or editable review-label
  controls are offered. Existing stored labels stay visible and read-only.
- Runtime885reported/884executedPASS/1paid-nativeoptinskip. A subsequent19-test
  capability/controller group passes against the final runtime source.
- Complete backend6109reported:6106PASS/2fail/0error/1skip. Archived full XML
  reports and log remain private. The two failures are the reviewed-artifact
  operator fixture and the old public Apply API lacking explicit target/artifact
  selection. Neither guard was bypassed or reclassified as a passing test.
- Final source-authoring/legacy-capability/settings45PASS. Actual Apply-safety
  regression40PASS verifies that each missing retrieval/reranking/accounting
  capability stops queueing, compatible artifacts pass the gate and disabled
  profiles preserve their stored configuration.
- Widget TypeScript and typed-score presentationPASS. A typed BM25/RRF score is
  never converted to a confidence percentage.
- Fresh read-only Support preflight: draft revision14, recorded verified releases,
  ready connectors and stable13stage/14prod active sources. Both own-staff
  sessions logged out, with revocation confirmed. Weaviate1.40.3 running-image
  inventory confirmed on both hosts. No customer corpus or index was changed.
- Central BOM still404; owner reconfirmsPUBLISHING for the original upload. Public
  push build45292383299 remainsSUCCESS onf933227. No duplicate upload, private
  version adoption, serving rollout, additional provider call or allowance change.

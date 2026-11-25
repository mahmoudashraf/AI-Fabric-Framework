# Autonomous Build With AI Qualification

Status: implementation committed and release CI passed; staging rollout and
hosted qualification in progress. Not a live-release acceptance record. A
published artifact is not proof of an applied or qualified customer workflow.

## Contract

See [010.76](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_76_GOAL_DRIVEN_FULL_AUTONOMY_AND_ADAPTIVE_AUTHORING_PLAN.md)
and [the development guide](../../Final_Documentation/Development_Guides/AUTONOMOUS_BUILD_WITH_AI.md).
The configured Brain/framework owns inference, recovery and continuation.
Platform supplies authenticated authority, resource adapters and a bounded UI
projection. No private generation endpoint, automatic publishing, manual-edit
lock, ordinary Support write grant or extra infrastructure is introduced.

## Completed Local Checks

- Framework candidate `d59a452d8e1368c16b0a5e0fb68afd0dd5d493cf`: all 32
  reactor projects passed, 2,769 tests, zero failures/errors and four opt-in
  skips. Focused goal/gateway/window tests: 109 passed. Public Framework Build
  45497827404 passed, including real apps, Incident Room and vector contracts.
  Immutable tag `ai-fabric-framework-v0.8.70` and a single Central release run
  45500458316 completed successfully. Official Central BOM/JARs are available;
  all nine critical JAR versions and checksums were independently recorded.
- Compiler/catalogue/version alignment: 21 passed. Active goals require the
  declared native capabilities; disabling goals retains saved settings and
  narrows effective Quick edit limits without granting delegation.
- Runtime additive V29 migration and existing Smart Brain nonterminal-state
  compatibility: 3 passed. Migration preserves protected requests, results and
  checkpoints and is safe to apply again.
- Final affected Runtime checks: 71 passed, including seven host checks and
  twelve native durable-goal cases. Provider transport/resolver: 128 passed;
  ledger: 78 passed; connector: 163 passed; Relay: 35 passed. The preserved full
  Runtime run found one stale migration-count assertion among 839 tests; that
  assertion was corrected and its seven-test PostgreSQL class passed. A fresh
  complete product Runtime reactor then passed: 163 connector, 847 Runtime and
  35 Relay tests, zero failures/errors and five opt-in skips (1,045 total).
- Assistant UI units: 88 passed. Browser qualification: 90 groups passed,
  including desktop/mobile, old-server availability, progress restoration,
  latest user directives, actual receipt pagination and safe diagnostics.
- Customer authentication/session transport units: 26 passed; all 38 browser
  scenarios passed. Logout first
  revokes the verified customer's goal authority, separately clears its native
  cookie, then clears browser identity. Failed revocation remains visible and
  does not claim successful sign-out.
- Switching from a customer to a staff session/key does not leave that retained
  customer's work authorized when its browser session is cleared. Separate
  credential-only and cookie-only revocations prevent identity mixing.
- Typecheck, UI build and whitespace checks passed.

These local results are not evidence of a deployed version or reliable natural
language completion. Full framework/backend/Runtime regression and the hosted
cases below remain release gates.

Runtime/provider implementation and version alignment were committed and pushed
as `19282a279420b74d8ffda1df36302adcea199e1c`; Runtime image CI 45503364904
passed. Backend/UI authoring source is
`0b3197a65097e6ad907803aeb8aa9fd1a385f8be`. Its core image 45505263980,
storage 45505263845, quota/Desk 45505263933 and full code regression
45505285987 gates passed. The latter passed on attempt two after the first
attempt stopped before compilation on a Maven Central HTTP 429 response.

Full backend pre-closure checks passed: 6,191 tests, zero failures/errors and
one skip. Final assistant/materialization/safety closure checks: 484 passed.
Staging Runtime artifact `dsa-0a673f73` was registered against the nine official
JAR checksums and immutable image digest
`sha256:51fcd9f7eb8752bf5fbcd93e8720f9094e1ea20801a2936c326b8990a017f538`.
Staging backend and UI are healthy on the exact backend/UI source, with existing
environment, capacity and storage preserved. Configured authoring release
`rel-6055b90b` failed before verification: its 68,170-byte workflow bundle exceeded
the entrypoint downloader's stale 65,536-byte limit, despite the compiler/parser
declaring 96,000 bytes. Production remained unchanged.

Runtime bootstrap correction `997815dc10e3089571ebc125d0f1e39d2c87dc73`
aligns both the download and post-download limits for workflow bundles only.
Ordinary Support specialist bundles retain their 65,536-byte limit. Eleven
focused shell/parser tests passed, including the exact workflow boundary,
over-limit download and post-download checks, empty/failed/insecure downloads,
hash mismatch and conflicting transports. Corrected Runtime image CI
45510010248 is running. No hosted goal completion or production rollout is
claimed at this checkpoint; the failed release remains recorded.

## Hosted Acceptance Gates

### Current Staging Finding, 25 November

Latest checkpoint: B5 backend `91edaf5e6e6828ce95a60aace919c1f601cef713`,
UI B1, and verified R5 authoring runtime
`aef8d6a2b7a414540eabe9398b99be034cc20f82` on release `rel-c44468a4` /
version `ver-9a715db1`. Runtime image CI 45532500069 passed and all nine official
Framework 0.8.70 JAR checksums matched. Same-version apply retained mini/8192,
all nine profiles, saved configuration, keys, capacity, storage and original
funding/usage. Both registration and apply logouts passed.

The preceding B5/R4 one-Prepare UI baseline `s25-b5-guidance-1` passed all five
Harbor action/route, company-credential, visitor-access and confirmation checks
without user correction. It used three segments, 18 model calls, five
corrections, three write attempts and two executed saves, costing USD0.3557505. Normal test resource
cleanup and both logouts passed. This is the first complete semantic pass, not
the three fresh runs or complete production qualification required below.

Fresh R5 mini run `s25-r5-mini-1` also passed all five operations, with 15 model
calls, four corrections and two saves over three segments. Cleanup and both
logouts passed. Charge delta USD0.36495075; cumulative staging USD5.61559275.

R5 enables separately authorized Astra pricing without replacing funding. Its
staging amendment is now verified with all six original financial tables
unchanged. The helper's raw-versus-normalized manifest ordering was corrected
with 112 offline checks. An immediate receipt timestamp check then required a
GET-only reconciliation; the existing POST was not replayed. Own logout passed.
Normal model-only publication passed on staging: version `ver-e914d80e`, release
`rel-50b35ab2`, verified/ACTIVE with Astra/8192/timeout40. Financial rows, keys,
primary model and other configuration remained unchanged, with verified logout.
The first paid Astra UI run `s25-r5-astra-1` failed before any action or save with
PROVIDER_BUDGET_INTERRUPTED. Its uncertain provider outcome retains a conservative
USD2.88768 bound, not a confirmed invoice charge. Cleanup and both logouts passed.
Safe native diagnostics are being added to distinguish HTTP rejection, invalid
response metadata and I/O before another paid attempt. Existing guards, rates,
funding and the original failed record remain unchanged. This is not model-quality
acceptance or a production rollout.
Production autonomy and the new walkthrough remain unqualified. Exact restart
recovery is deferred by owner; basic authority, cancellation and erasure are not.
The earlier checkpoints below remain historical evidence.

Backend B3 `e2dd3ab1ba4b8927da663376619f851fe7dd0fdc` passed all four
source-exact gates (code 45518192520 attempt two). Runtime R3
`8921e6aa579d869b91086c394871db35304b94f4` passed image CI 45520284390 and
68 focused local checks. Staging authoring release `rel-407a3181` is verified
and ACTIVE on published version `ver-2a7757a6`, preserving ledger, keys, host
capacity and storage. Production is unchanged.

The first real R3 Harbor one-Prepare attempt admitted native work but paused
before any save. Actual provider metadata, not a guessed host setting, shows
four planning responses exhausted a 4096-token cap entirely on reasoning, with
no content. The actual controlling setting is the generation purpose override
AI_PROVIDERS_GENERATION_MAX_TOKENS=4096; its planner does not use the specialist's
final-output limit. The attempt recorded 0/5 coverage,
zero writes and USD0.10646625; normal cleanup and both own logouts completed.
Its report remains at the private `harbor-staging-s25-r3-bulk-1/` directory.
The existing generation-budget configuration and malformed web usage projection are being
corrected before another paid attempt. This is not hosted completion evidence.

R4 `0546601da6ab763f20987f80f7b33b6560b038c4` subsequently passed full Runtime
image CI 45524600734 and 68 focused checks, including real Boot/Jackson3 HTTP
serialization. Staging version `ver-99a2a21d` / release `rel-630090bd` is verified
on artifact `dsa-5305d19f`; actual Java process generation cap is 8192/MEDIUM.
Twelve readonly host/SDK/schema/authority/accounting/storage gates passed.

Fresh `s25-r4-bulk-1` completed structurally from one normal UI Prepare across
three segments: 10 tool steps, 14 model calls, three corrections, one save and
5/5 coverage. **Semantic acceptance remains FAIL**: the private actions wrongly
require visitor sign-in although their company API credential is already bound
and the documentation says no visitor login. Correct scoped key references do
not override visitor authorization. Retain the original failed checker report
and separate semantic reevaluation; no SDK authorization relaxation is allowed.
Generic authoring guidance is being corrected before fresh qualification.

- Official immutable framework release, successful public build and nine
  official Central JAR checksums matched inside the Runtime image.
- Core backend/UI and Runtime image provenance, passing relevant CI and healthy
  staged release without changing infrastructure, keys, ledgers or capacity.
- Three fresh empty Harbor draft preparations per environment, using only the
  ordinary customer UI and one initial Prepare request. All five documented
  operations, private credential references, optional filters and WRITE
  confirmation must be present. No manual recovery coaching or hidden helper
  writes count as assistant success.
- Active-request continuation, pause/cancellation, source withdrawal/erasure,
  denied foreign resources and no duplicate writes. Exact restart recovery and
  post-crash continuation are deferred, not credited as passed.
- Existing Quick edit, preview, Undo, manual editing, other page profiles and
  separate read-only Support behavior retained.
- Actual Harbor knowledge/indexing retained, explicit publish/apply/verify,
  assigned Engage app answers and a confirmed synthetic enquiry with receipt.
  Cancelling an enquiry must create no business effect.
- New honest walkthrough, at most 20 minutes, clear cursor highlights and
  playable video. Previous partial recording remains historical, not relabelled.

Private authenticated receipts, provider metadata and release intents belong in
`~/.codex/private/loomai-autonomy-01076/`, not Git. Never include credentials,
raw provider responses or private reasoning in this record.

## Latest Staging Qualification - 25 November

R7 `7983f1e7d5e917ae0fb20e3ee77d2145ee60386d` is verified on staging with
Framework 0.8.70 and the configured Astra generation purpose. Three fresh Harbor
UI requests each prepared all five documented operations with six model calls,
zero corrections and one executed draft save, without coaching or manual repair.
Scoped credential references, visitor permissions and confirmation semantics
passed. All temporary resources/sessions were removed and both logouts passed.
The earlier unsuccessful attempts above remain retained as separate evidence.

Quick edit then exposed a request-format regression before inference: its sender
omitted the new primitive goal-mode fields and the Runtime's actual JSON parser
returned HTTP400. All eight failed cases had zero provider calls and no saves.
The bounded sender now explicitly sends false/zero mode fields; it does not add
goal authority or relax strict parsing. Eight wire checks, 108 focused backend
checks and a real Runtime HTTP regression passed locally. Hosted verification
of this correction is still required. Production autonomy, normal Pause/Resume,
source-erasure closure and the new primary Harbor walkthrough are not yet closed.

## Spending And Rollback

The owner approved at most USD50 combined qualification spend. Durable additive
test limits are staging USD25 and production USD20, with USD5 reserved for Harbor
checks. Existing grants, recorded usage and expiry remain intact. This does not
authorize infrastructure or billing-capacity changes.

Autonomous availability follows the published Brain policy and Runtime
capabilities. Pause active goals before a policy/runtime rollback; do not erase
receipts, reset usage or route unfinished work through the retired inference
path. Quick edit remains available when goal mode is disabled.

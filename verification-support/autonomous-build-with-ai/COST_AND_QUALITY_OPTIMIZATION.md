# Build With AI Cost And Quality Optimization

25 November 2026. Owner direction: stay away from Astra, qualify a cheaper model,
retain quality, finish all Build with AI areas on staging before production,
and preserve the **USD55 combined qualification ceiling**. The proposed USD65
ceiling was not approved or applied. No infrastructure change is authorized.

## Measured Baseline

The new all-area staging investigation made 17 assistant requests / 84 provider
attempts, with 536,007 input tokens and 6,064 output tokens. Conservative recorded
cost was USD5.60263. Almost 96 percent was input-token accounting. These numbers
are from the three private baseline reports, not provider invoices:

- staging-baseline-models-1: USD0.59343, model Autonomous passed; Quick edit saved
  its change but funding stopped its final explanation.
- staging-all-brain-auto-1: USD3.16646; eight authoring tasks and read-only Kit
  guidance passed. The handover fixture was rejected before inference because
  it invented incomplete disabled handover configuration. The corrected test
  uses an actual installed channel and restores its original draft afterward.
- staging-resource-areas-1: USD1.84274; handover and private data passed in both
  modes. Engage Autonomous passed; its Quick edit hit the reservation guard.
  Document preparation was not reached. Failed/interrupted records are retained.

Last observed assistant accounting: staging USD30.13473275 + production
USD12.48094 = USD42.61567275, including earlier qualification. Existing caps are
staging33 + production20 + Harbor reserve2 = owner55. Original funding identity,
usage and expiry are preserved. USD5 staging top-up has an audited receipt;
there is no additional USD10 top-up. The Harbor reserve is an allocation, not a
measurement of Harbor API charges. Re-read live ledgers before new qualification.

## Relevant Practices

### 1. Qualify The Smallest Model That Meets The Task

Official guidance recommends evaluating smaller models and fewer requests rather
than assuming a flagship is necessary. First candidate is pinned
`gpt-5.4-mini-2026-03-17`: already supported by the reviewed runtime/budget
profile and previously used for bounded authoring. This is not proof of current
full autonomous quality. Keep MEDIUM reasoning, the 8192 completion limit, tools,
prompts, permissions and goal budgets unchanged for a model-only comparison.

At official uncached mini prices, the same baseline token counts account for
about USD0.4293 rather than USD5.6026: a **price-only** illustration, not a promised
92 percent saving. A different model may need more tokens, repairs or calls.
No automatic Astra fallback. If mini fails, fix and requalify or present another
non-Astra option; do not quietly restore the expensive model.

Sources: [cost optimization](https://developers.openai.com/api/docs/guides/cost-optimization),
[GPT-5.4 mini](https://developers.openai.com/api/docs/models/gpt-5.4-mini).

### 2. Avoid Duplicate Model-Controlled Checks

Observed simple edits commonly follow context -> check -> update -> work status
-> complete, requiring about five planner calls. `AuthoringResourceService.persist`
already validates inside every save. `AuthoringDraftOperations.mutate` records
the saved diff/findings and EXECUTED receipt in the save transaction. Autonomous
`RuntimeConfiguredWorkflowDispatcher.validateCompletion` also reads current
coverage through a governed status call without a model round trip.

Recommendation, separate from the model comparison: allow a model to use context
-> validated update -> complete for straightforward requested edits. This is an
optimization target, not a fixed three-call cap or a keyword router. Keep:

- check_candidate for previews, uncertain/complex candidates or model choice;
- work_status for uncertain outcomes, partial/multi-operation work or requested
  readbacks;
- fresh ownership, scopes, resource policy, validation and receipt persistence
  on every write;
- explicit intermediate saves/readbacks requested by the user;
- current coverage and uncertain-write reconciliation. Never replay writes merely
  to obtain a nicer explanation.

The published shared prompt currently requires work_status before every finish.
Make that configurable instruction conditional in a separately qualified prompt
revision; do not remove its capability or weaken completion contracts. No new
Framework execution engine is required for this instruction-level candidate.

Source: [latency optimization](https://developers.openai.com/api/docs/guides/latency-optimization).

### 3. Scope Context To The Selected Task

Action/connection context is already narrowed to its configuration. Other Brain
tasks still receive a broader snapshot and catalogue. Proposed Platform work:
project the relevant settings/contracts for the selected profile, preserving
shared identity, allowed paths, actual references and necessary dependencies.
Expose explicit scoped reads when additional configuration is needed. Do not
drop requested documentation, source schemas, authorization facts or unknown
configuration values from actual saves. One generic projection owner, not a
second set of editor schemas.

Keep tool results compact: necessary diff, validation findings, real receipt and
coverage rather than repeated full unchanged configuration or old large results.
The existing observation window already bounds history; refine it only with
evidence, not by throwing away recent failures or write receipts.

### 4. Improve Cacheability And Observe It

Official guidance recommends stable instruction/tool prefixes and appending
changing input. The generic planner currently rebuilds a user JSON object with
request/history, changing allowances/goal state and tool metadata. A separate
Framework proposal could place stable profile instructions/tool contracts before
dynamic execution state, retaining system/data role separation and boundaries.
Do not implement a second Platform planner or switch APIs solely for this.

Caching is already automatic where supported, but a cache hit is not guaranteed.
Measure cached input, cache writes when applicable, total input/output, model,
latency and cost. For mini, a stable partitioned cache key may improve routing.
Do not use raw secrets or share customer context through an application cache.
Server authorization must remain fresh regardless of provider cache reuse.

Retain conservative spend enforcement. Report reserved, conservatively accounted
and invoice-reconciled costs distinctly; do not rewrite historical charges or
pretend cache savings are measured before collecting usage evidence.

Source: [prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching).

### 5. Gate Savings On Real Outcomes

Compare one change at a time against identical cases. Score actual saved values,
unrelated-value preservation, actual receipts, repair counts, unnecessary
clarifications, confirmation/ownership behavior and final explanation. A
completed goal or valid JSON alone is not a semantic pass. Also report cost per
successful result, not just per attempted request. Repeat difficult API mapping,
follow-up and failure-recovery cases with held-out inputs.

Source: [agent cost/quality optimization](https://developers.openai.com/cookbook/examples/agent_optimization/optimizing_agents_for_cost_and_quality).

## Defer

Batch/Flex/background scheduling is not the first optimization for interactive
draft editing. WebSocket/Responses migration, new manager-model routers, more
specialist layers, parallel writes, fine-tuning and paid infrastructure are not
needed to qualify this smaller model. Avoid reducing token limits or repairs
before establishing the cause of quality failures; truncation can cost more.

## Execution Order

1. Preserve Astra evidence; stop new paid Astra qualification calls.
2. Apply only mini generation selection to staging through normal draft/publish/
   apply. Keep SDK72, infrastructure, keys, nine profiles and ledgers unchanged.
3. Run all13 configured tasks in Autonomous/Quick edit, with Kit guidance
   explicitly read-only. Test actual API imports and difficult mappings as well
   as simple field edits. Capture original failures and actual saved effects.
4. Fix the already reproduced document/search shortcut mode bug, qualify its
   UI release on staging, and verify real shortcuts with the cheaper model.
5. Promote the same accepted model/UI settings to production, then repeat the
   task matrix and cleanup/security checks there. No production quality claim
   before evidence. Never exceed stage33/prod20 plus the separate2 reserve.
6. Present separate instruction/context/cache changes for their own measured
   qualification rather than mixing them into the model-only baseline.

## Qualification Checkpoint

Staging model-only mini release `rel-077109f1` / `ver-c54c023b` is applied and
verified on the same SDK72 image. Production remains unchanged at this checkpoint.
The medium-reasoning candidate is not yet accepted for the complete workload:

- Ten authoring areas passed both modes: actions, connections, responses,
  models, search, specialists, whole-Brain setup, handover, private data and Engage.
- Guidance passed both actual Explain requests. The earlier PREPARE-based guide
  test failed and is retained; read-only safety alone is no longer scored PASS.
- Document preparation passed both modes after correcting the test to preserve
  the active indexed version, not forbid the normal candidate-version increment.
- The original knowledge fixture was incomplete and asserted a noncanonical
  displayName. Its failure remains recorded. A separate real installed-source
  test then unnecessarily requested an identifier already in the saved draft.
  This is a genuine remaining qualification failure, not dismissed as a fixture.
- The full Harbor API import hit reasoning truncation: 8192 reasoning tokens,
  no visible decision, then repair and authorization expiry before saving. No
  partial write was replayed. Exact fixture, session and created key were removed.

The context reader also demonstrably turns optional empty definition filters
into an empty action-only result and applies action filters to non-action tasks.
Two failing regressions reproduced this behavior; the bounded fix preserves the
current task when no real applicable filter exists. All86 focused tests passed,
including nonempty action selection. This is a confirmed code defect, not proof
that it caused every live knowledge decision. Requalify after deployment.

Next candidate: the same pinned mini with LOW reasoning, unchanged output/repair
allowances, tools and prompts. This is a separately measured configuration trial,
not a silent alteration of the model-only comparison. LOW is supported by the
[model contract](https://developers.openai.com/api/docs/models/gpt-5.4-mini).
Do not extend authentication leases or disable guards to accommodate slow calls.
Do not create the staging acceptance gate until all required cases genuinely pass.

### Low-Reasoning Follow-Through

The fresh real Harbor UI import passed on LOW: all5 documented operations,
actual owned key references, optional query mappings, required/optional write
body fields, receipt fields, visitor access, confirmation, unrelated-value
preservation and exact cleanup. Nine model calls, three saves and two corrections;
no user coaching. Price, stock and adjustableHeight mappings were checked from
the actual saved configuration, not a generated explanation.

LOW now has passing composite coverage for12 of13 tasks. Knowledge still awaits
the context-reader deployment and retest. Guidance initially failed because its
UI disabled all format repairs alongside writes. Allowing the existing bounded
repair policy produced real EXPLAIN answers with one correction each, no writes.
UI retains previewOnly, the read-only profile, server ceiling and user opt-out.
All104 browser groups,89 assistant unit checks and UI build passed this change.

One staff sign-out check interfered with a simultaneous staff-authorized case:
the account-wide logout fence correctly paused that goal. Its failure remains
recorded. The clean sequential handover repeat passed both modes and restored
the draft. Do not run independent live checks with the same staff actor while
another check may sign that actor out.

The real document shortcut sent both actual modes, but its early Quick edit
cancellation returned502. Source tracing and a red regression confirmed that
Runtime rejected interruption of a new bounded turn when a finished older goal
remained in the conversation. The fix checks original ownership first, ignores
only a terminal old goal, and retains exact-turn checks for active goals. The
canonical interruption still validates the latest interaction. Focused Runtime
tests pass; live qualification awaits its new software image. Do not suppress
an unacknowledged cancellation or claim this check passed before rerunning it.

No Framework source/release change, prompt rewrite, larger output allowance,
funding increase or infrastructure change was required for these bounded fixes.
The last idle staging ledger was USD31.9227035 under33; production remains
USD12.48094 under20, with the separate2 reserve and55 combined ceiling intact.

### Knowledge Context And Final Stage Checkpoint

After the empty-filter fix, the real knowledge citation edit completed
autonomously (11 calls,3 corrections,1 save). Quick edit still proposed an object
where the source array belongs. The disable-source request also conflicted with
an actual specialist dependency, so it is not a valid isolated routine edit.
Its original FAILED result is retained, not relabeled as a passing rejection.

The bounded follow-up provides an identity-preserving source UPSERT example and
returns complete knowledge/entity/dataset context without unrelated model,
response, action and specialist catalogues. All saves still validate the full
draft and dependencies. Nothing is removed from saved configuration. Explicit
dependency findings must be explained, not bypassed. All88 focused backend tests
passed, including preservation of unknown source fields and sibling sources.
The normal Quick edit case now edits the citation label while retaining enabled
state. A separate dependency case checks that an incompatible request leaves
the full draft unchanged and produces a useful explanation instead of a false
save or failed generic reply. Both still await the follow-up deployment.

Staging Runtime and UI `defa59983` are now verified, SDK72/same nine profiles and
Mini LOW. Authoring release `rel-38ba13bb` / `ver-c474e862`, artifact `dsa-722a3566`.
Real document shortcut `low-fixed-2` passed actual Autonomous and Quick edit
requests, exact document scope, both normal cancellations, source restoration,
zero page errors and sign-out. A real guide request passed EXPLAIN/zero writes,
with its full Brain draft unchanged. This is mode/control and guide proof, not
a claim that either cancelled document optimization completed successfully.

Production is still unchanged at this checkpoint. No acceptance gate has been
created prematurely. Last idle staging accounting was USD32.0451905 under33.

### Corrected Knowledge Test And Repeat Reliability

The knowledge test fixture means controlled starting data for a real staging
test, not a mocked Platform or a separate assistant. The corrected case uses
the installed approved support-document source through the public assistant
API after normal UI sign-in. It changes only its citation label, checks the
actual saved draft and unrelated-value preservation, then restores the original
settings and deletes the test conversation. No document approval, indexing,
publication or active release change is performed. This case verifies knowledge
configuration editing, not retrieval-answer quality. Other checks exercise
the actual UI, including document shortcuts and the full Harbor API import.

Backend `6455fb8dc` passed all four exact gates: core45713092786,
code45713176566, storage45713180242 and quota45713184539. Its full backend tests
ran; UI build was deliberately omitted because the UI/Runtime trees exactly
match the already-qualified `defa59983` images. Staging backend native source,
digest and health were verified, with environment, capacity and storage retained.

`staging-mini-low-knowledge-contract-1` passed both actual modes: Autonomous
6 calls/1 correction/1 real save; Quick edit5 calls/0 corrections/1 real save.
Exact restoration, unchanged versions/releases, foreign-resource denial and
customer/staff sign-out passed. There is now passing composite routine coverage
for all13 configured tasks, not a fresh all-area production qualification.

Two additional findings prevent whole-workload acceptance:

- The separate knowledge dependency test left the draft unchanged and executed
  no write, but ended FAILED/WORKFLOW_CORRECTION_LIMIT rather than explaining
  the dependency. Its completion check demanded a save after a nonempty checked
  candidate. Preserve the original failure; do not call it a passing rejection.
- A fresh, uncoached Harbor import on the current Runtime/UI repeated only4 of5
  operations, then paused WORKFLOW_NO_PROGRESS after32 calls/6 saves/12
  corrections. The remaining operation was POST /v1/enquiries. It repeatedly
  reached a checked-candidate-without-later-save completion finding. The first
  LOW import's five-operation pass remains valid, but does not prove repeat
  reliability. Exact created Brain, key and sessions were cleaned up and both
  actors signed out. No business API call or publication was performed.

The existing workflow profiles do not select different generation models;
routine/complex model separation needs an explicit configured contract, not a
hidden Platform router or Astra fallback. Do not create the production mini
acceptance gate from routine passes alone. No new Framework release, shared
prompt optimization or profile-model routing has been implemented here.

Last idle accounting: stagingUSD32.49458075 under33, productionUSD12.48094
under20. The separate Harbor reserve2 and combined55 ceiling remain unchanged.
Production has received no new paid test calls or software/model changes in
this optimization task. Further paid checks must retain the reservation guard;
do not silently transfer grants or count the Harbor allocation as unspent cash.

### Completion Feedback Repair In Qualification

The completion contract previously treated every changed checked candidate as
ready to save, including candidates with `requiresCorrection:true`. It therefore
gave a misleading missing-save instruction after a real dependency rejection.
The repair retains the no-false-save contract, but feeds the model the actual
bounded validation findings and allows it to choose UNSUPPORTED with a useful
dependency explanation, rather than demanding an invalid write or missing input.
The model still chooses the outcome; no answer replacement or wording matcher
has been added. Actual refined saves remain authoritative.

Checked and saved-state coverage findings now include bounded pending operation
and required mapping details. The retained failed Harbor draft had a whole
`{{params}}` body mapping, while coverage reported missing required body fields.
Previously the generic completion finding did not expose those details directly.
This feedback change is a qualified repair candidate, not proof of live quality.
The shared workflow prompt, model, limits and source contracts remain unchanged.

Two red regressions reproduced the feedback gaps. All62 focused Runtime tests
then passed, including real SDK/governed-action integration, no wrong draft
write on a rejected candidate, earlier/uncertain receipt rejection, actual
refined-save acceptance, cancellation/goal-host regressions and bounded feedback.
New immutable Runtime image and live staging qualification are still required.

The owner approved redistribution of USD2 from unused production allowance to
staging, with intended caps35/18 plus Harbor2 =55. Approval does not itself change
either native ledger; record the completed operation separately, lower production
first, preserve original approvals/usage/history and never claim a transfer before
both live readbacks prove it. No additional overall funding was authorized.

### Further Structural Gaps Found By Live Qualification

Runtime `f78d812fd` passed its complete CI image workflow45717858336 and is
verified on staging as rel-12952cce/ver-896c65db/artifactdsa-2b7be33f. The
knowledge dependency repeat now passes: model-generated UNSUPPORTED explanation,
four calls/two corrections/zero writes, unchanged/restored draft, no publication,
and exact conversation cleanup/sign-out. This is a real recovered response,
not a relabeled original failure.

The first new Harbor repeat stopped NEEDS_INPUT after6 calls/0 saves, asking
the customer for path-template syntax. Its retained candidate and validation
show a templated absolute URL. Existing route guidance contained conflicting
advice about absolute targets when the upstream was unbound. The follow-up
provides a native parameterized-path change example, removes the contradictory
fallback and puts the repair syntax directly in ROUTE_URL_INVALID feedback.
It still requires a documented/authorized host and preserves unrelated bindings.

Important correction to the earlier body diagnosis: `{{params}}` is supported
by the actual connector and preserves the parameter object. The coverage
checker did not recognize it, so the older4/5 result was a false-negative body
coverage finding, not proof that the original mapping was unusable. The backend
fix mirrors that native binding and still requires declared required inputs and
nested required properties. Existing host, owned-secret and MCP checks remain.
Two red tests reproduced the whole-object rejection and absent path example;
all214 focused backend tests now pass. Live qualification awaits this backend
image; the already-qualified Runtime/UI images are unchanged by this follow-up.

Budget redistribution completed with both native readbacks: staging35,
production18, Harbor reserve2, combined55. Production was reduced first by an
explicit owner-authorized, exact-scope operator transaction because the native
API supports additive top-ups only. Its original approval, usage, attempts and
top-up records were preserved and checked against private before/after hashes;
no synthetic refund/provider usage was inserted. Staging then received its real
audited2 top-up through the existing API. The immutable private operation receipt
is `build-ai-20261125-owner-approved-redistribution2-verified.private.json`.
This is an operator qualification adjustment, not a claim that a customer-facing
budget-reduction API has been implemented. Production software/model is still
unchanged, with no new paid Astra calls. No acceptance gate exists yet.

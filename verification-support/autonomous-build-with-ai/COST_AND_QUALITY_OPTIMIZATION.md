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

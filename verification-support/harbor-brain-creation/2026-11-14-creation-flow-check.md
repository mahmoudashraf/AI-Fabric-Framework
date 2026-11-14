# Harbor Office Brain Creation Check

Date: 14 November 2026. Live Console: `https://console.loomai.pro`.

## Scope And Status

Requalify the separately retained fictional Harbor Office API using the
[manual guide](../../Final_Documentation/External_User_Guides/SMART_BRAIN_ASSISTANT_AND_ENGAGE_MANUAL_TEST_GUIDE.md).
This is a real customer-UI exercise, not a mocked editor test.

**Setup/integration qualification complete; conversational quality is not fully
qualified.** Harbor v5 is applied and verified, its policy is indexed, and both
Engage channels are ACTIVE/ready. Explicit product/policy retrieval,
confirmation/rejection, real enquiry creation and receipt lookup work. The
original six-question natural-language set is only **2/6** on the final run;
remaining routing/target-resolution misses are described below, not concealed by
easier substitute questions.

The API remains at
`https://assistant-lab-20261112.46.224.145.148.sslip.io`.
Source remains outside Platform in `../loomai-assistant-lab`.

## Initial Check: Before Managed Approval

These are the original configuration-only results, before the owner approved
provisioning managed search. The later deployment/live results below supersede
their runtime and activation status, not their recorded first outcomes.

- HTTPS health, public OpenAPI and authenticated safe connection check succeed.
- The separate API's five local test groups pass, including synthetic receiver
  behavior, authentication, unknown IDs and out-of-stock rejection.
- Created one fresh AI Customer Experience Kit through the actual customer UI:
  `kit-84aa674b-5d65-4b62-8286-3b8994f087fb`, Brain `dep-5fbdc125`.
- Imported the actual OpenAPI document through the assistant. The saved final
  catalogue has all five reviewed operations: four READs and one confirmed WRITE.
  Anonymous visitor access and READ evidence eligibility are present. No raw
  company credential was shared with the assistant.
- Stored company and OpenAI credentials through the customer Secrets UI. The
  aggregate connection check verified OpenAI and the authenticated API probe,
  both HTTP 200, without running any business action.
- Hosted customer document storage provisioned successfully, was bound to the
  staging target, and accepted `harbor-returns-policy.txt`. Upload is not indexing.
- After restoring supported external-vector configuration, Check setup reported
  valid configuration with zero warnings.
- Published `v1`, `ver-43ba4358`, on framework `0.8.52`, without applying it.
- A single assistant label change saved successfully. Undo restored all ten
  configuration sections exactly; endpoint/access/confirmation settings remained
  unchanged.
- Both standalone and embedded Engage installations were created by the Kit.
  Branding/welcome changes persisted for both. The customer preview rendered.
- The managed standalone hostname verified its domain and frontend route.
  Activation correctly remains blocked on the absent verified runtime release.
- Lab enquiry counters are unchanged by setup. No enquiry was executed by this run.

## Findings And Distinctions

### Assistant Scope Miss

The first Actions preparation, with the five operations selected in the UI,
saved only three READs. A follow-up unnecessarily requested clarification.
Explicitly naming the five operations eventually saved the complete catalogue.
This is not a clean first-attempt authoring pass.

### Credential Reference Friction

One intermediate proposal added `X-Lab-Key` headers on the enquiry routes.
The assistant's credential guard rejected it. No raw secret was supplied or
leaked. Excluding credential edits from the next proposal and configuring the
upstream through the secure form succeeded. This is an authoring/reference
integration limitation, not proof of failed API authentication.

### Unsupported Local-Index Experiment

The verifier briefly selected Lucene to avoid unnecessary external capacity.
This was incompatible with the S3 document source's durable-vector requirement
and the inherited 1536-dimensional configuration. Validation correctly blocked
publication. It was restored to the original external Milvus/shared mode.
No validator or security policy was weakened.

Response-guidance preparation overlapped that invalid draft. Its correction loop
encountered those unrelated vector findings, and one turn eventually exceeded
the structured-change limit. A narrower follow-up saved the guidance with the
remaining findings visible. Do not describe this as an independent response
quality regression without accounting for the invalid starting configuration.
After restoring a valid draft, the documented source-led preparation was repeated
with the reused OpenAPI and response-guide text selected. It passed in one
attempt, made no unintended configuration changes, and retained the existing
guidance. Earlier failed turns remain in the evidence.

### Exact Destination In External-Vector Form

The customer Existing vector service form does not honor the exact immutable
staging target supplied in the Brain URL when there is no release yet. It derives
an environment, then resolves a target without the explicit/bootstrap target ID.
With both legacy and immutable profiles present it shows:
`Choose an unambiguous environment in the Smart Brain header before binding storage.`

Relevant code: `CustomerExistingVectorBinding.tsx`, `VectorChoices`, calls
`connectionEnvironmentSelection` without its existing bootstrap-target argument.
This was corrected in `6acfd7da6`, live on both Consoles. The exact target is
honored for display and freshly revalidated saves; ownership, active-destination
and version checks remain. The production form now displays the expected
credential fields. A new external binding was not submitted: no spare endpoint
was borrowed. Focused save/security tests and a real-component browser test
cover this path; do not call it a newly provisioned BYO deployment.

### Managed Provisioning And Native Action Contract

The owner subsequently approved new managed capacity. The normal customer
apply flow created Harbor-owned Zilliz Serverless cluster
`in05-7ac848eb2991692`, rather than reusing another Brain's cluster. The managed
form clears inherited BYO requirements and credential references; switching back
retains explicitly entered BYO values only within the unsaved editing session.

The first apply failed because the generated enquiry action used `WRITE`, not
the native `WRITE_ONLY` enum. The editor/catalogue already described the native
enum, but Check setup did not validate it. Platform patch `f691f718c` adds that
execution-contract check without coercion, requiring vectors for READs, or
making confirmation mandatory for unrelated actions. Its 111 focused tests pass;
the patch is live on both backends. There was no framework release.

After correcting the single enum, v3 (`ver-42c86691`, `rel-e1dc02a4`) applied
normally. The first verification accurately failed for the missing active
policy. After explicit public approval, indexing and reconciliation, source
`doc-d9b0652bede8b697817c56d15f705ef2` is ACTIVE and verification
`vrf-776bd310` reports **30 passed, 0 failed, 4 skipped**. Skipped checks include
unconfigured optional capabilities, not assumed successes.

Both apps were connected to the verified release, saved and explicitly activated:

- Standalone: `https://harbor-office-lab-20261114.apps.loomai.pro`.
- Website installation: `awi_pub_1f13490e1b811660b44267690559959b`, restricted to
  the lab origin.

The only deployment database is the explicitly selected optional AI-state
Postgres. Furniture stock and enquiries remain in the external lab integration.

### First Anonymous Question Set: GPT-4o-mini

- R1 found the correct standing desk, but also included a chair when asked for a
  desk. This is a relevance miss, not invented stock.
- R2 correctly computed GBP 499 + GBP 25 delivery = GBP 524, not free delivery.
- R3 requested an explicit attachment/identifier for a conversational comparison.
- R4 answered the preceding comparison rather than the new Paris/installation
  question. The captured request contains the correct new question.
- R5 correctly declined to invent a chair warranty.
- R6 selected delivery evidence and missed the ACTIVE indexed returns document.
- W1 required confirmation; UI rejection produced no new enquiry. The model's
  proposed ID was wrong, so it was not executed.
- W2 used an explicitly correct product ID, but one confirmed call returned
  upstream HTTP 400 and created no enquiry. The generated mapping included a
  missing optional `message` as null. A constant connector success message also
  appeared on that failure; the error status was still exposed. Neither is a
  successful submission. A later incomplete proposal was rejected, not retried.

The generated optional user parameters also had `askUser:false`, hiding them
from model extraction. Harbor's draft now uses `askUser:true, required:false`,
the existing whole-parameter body template, and status-derived connector messages.
It selects the previously qualified GPT-5.4-mini model. These are reviewed lab
configuration changes, not semantic text matching or a framework workaround.
v4 (`ver-40cd1519`, `rel-78a1754a`) applied and passed native verification on
the same owned infrastructure. Its actual metadata confirms GPT-5.4-mini.

### Confirmations, Real Writes And Workspace Modes

The v4 standalone and website tests each rejected a pending enquiry without
changing the write counter. One later explicit confirmation in each channel
created exactly one synthetic enquiry: counter 1 -> 2 -> 3. Both returned actual
`ENQ-...` references and RECEIVED status, with `emailSent:false` and
`stockReserved:false`. The standalone omitted the optional message; the website
supplied one. The corrected body supports both shapes.

The website loaded only the public installer and installation ID, not a company
key. Docked READ, MAX expansion and close/reopen retained the same messages;
docked minimize/reopen did too. The launcher remembers MAX mode, while focusing
the compact composer reopens docked mode. A wrong automation selector caused a
browser-control timeout; it was not a lost conversation. Saved raw traces were
retained and the corrected UI sequence passed without page errors.

The 390 x 844 standalone viewport loads with document width 390 and no page
errors. Immediately after reassignment, one standalone route-readiness refresh
and one visitor **Try again** were necessary; later fresh phone startup succeeded
directly. These transient first outcomes remain recorded.

### Final Connector And Configuration Qualification

v4 did not repair the read-quality issues: product relevance remained mixed,
indirect follow-ups still requested attachment selection, and returns missed its
indexed source. The reviewed follow-up draft changes only actions, provider
settings and prompt settings: explicit `llmFacts.records` product projection,
the existing authorized-source overview, and generic LLM guidance distinguishing
descriptive factual questions from operations requiring an exact trusted target.
It does not invent a product vector space or alter semantic recovery policies.

Receipt READ exposed a real generic REST connector bug: relative path parameters
were sent literally. Platform commit `d3ba74601` expands encoded scalar path
values and rejects missing, blank, non-scalar or dot-segment values before any
upstream request. Static paths and existing query/body/header/absolute-URL
behavior remain unchanged. **All 139 connector tests pass**, including 57 focused
template/receiver checks. Runtime CI `43546347638` passed; framework remains
**0.8.52**, with no framework source change or release.

The exact image digest is
`sha256:82fa26e2293469622b0ee841034ffc4112fba3689178556d1eb4dd639d5ed31e`,
registered as `dsa-e1974edf` for staging qualification. Harbor v5
(`ver-ec80fdb2`, `rel-82523b43`) applied it through the existing release flow.
Both native containers attest this exact source/digest and are healthy.
Final verification `vrf-3fa53fe7`: **30 passed, 0 failed, 4 skipped**.

## Final Live Results: v5

Both retained app installation IDs are ACTIVE/ready on `rel-82523b43`, using
consumer `engage-6eecd231-8833-34cb-a214-482f61bce5e5`. No new Kit, app address,
vector cluster or business database was created for the upgrades. The cluster
remained Harbor-owned throughout; no vectors were copied.

### Original Question Set

- **R1 PASS:** only Harbor Standing Desk, GBP 499, eight in stock, adjustable.
  The earlier irrelevant chair recommendation is corrected.
- **R2 FAIL:** the delivery follow-up still asks for attachment/identifier
  selection instead of computing GBP 524.
- **R3 FAIL:** the comparison follow-up still asks for attachment/identifier
  selection rather than comparing the desks.
- **R4 FAIL:** it explains that it cannot arrange delivery/installation, but
  does not answer the actual Paris/tomorrow policy restriction. It makes no
  unsupported arrangement or delivery promise.
- **R5 PASS:** it does not invent a warranty for the chair.
- **R6 FAIL:** the answer only uses delivery evidence and misses the indexed
  returns guide. Detailed trace review subsequently confirmed that the planner
  selected READ + RAG, searched the correct `document` source, and got zero
  documents at threshold 0.5. This is not proof of a source-selection failure;
  the dealership's 0.4 threshold is a recall candidate to qualify. The successful
  explicit-guide query scored approximately 0.533 at the same 0.5 threshold.

This is **not an overall quality improvement claim**: R1 improves, but the six
original cases are still only 2/6. A natural standalone enquiry after showing the
Standing Desk also asks which desk, rather than resolving the known product.
The explicit-ID success below does not turn that natural-language miss into a pass.

### Independent Integration Proofs

- Explicitly asking to search the approved indexed returns guide retrieves an
  actual `document` source and correctly excludes used goods, allowing only unused
  goods in original packaging within 14 calendar days. This proves managed search
  and approved-source access, not successful routing of the original R6 wording.
- `harbor_get_product` succeeds through its dynamic product-ID path.
- A standalone explicit-ID enquiry waits for confirmation; one Confirm increases
  the counter by exactly one and returns RECEIVED with a real reference.
- Lookup of that new reference succeeds through the corrected dynamic receipt
  path, with zero additional writes. An older receipt also remains readable.
- A separate standalone rejection creates nothing. The website repeats rejection,
  one confirmed enquiry with an optional message, and receipt lookup successfully.
- The out-of-stock explicit-ID request is proposed for confirmation, which remains
  a model-planning quality miss. A single intentional confirmation receives
  **UPSTREAM_HTTP_409**, `success:false`, "Upstream call failed", and zero new
  enquiries. No false submission success is shown. The current configured result
  projection does not preserve a richer upstream error reason; its empty result
  fields are not a receipt.
- Unsupported cancellation/refund returns OUT_OF_SCOPE and creates nothing.
- Final phone startup loads immediately at 390 x 844 with no horizontal overflow
  or page errors. Website/standalone final browser error lists are empty.

Across the full exercise, the receiver counter moved from 1 to 5 through four
separately intended, explicitly confirmed successful synthetic enquiries. Rejected
drafts, receipt reads and the HTTP 409 attempt added none. The receiver retained
four current enquiries; the original historical fixture is no longer retained.

## Remaining Quality Review

1. Trace target-resolution and structured READ-result continuity for ordinary
   non-indexed business records. Do not invent a vector space or require manual
   IDs as a hidden repair of the original follow-up tests.
2. Qualify retrieval recall for the returns question: the source was actually
   searched but returned zero documents. Direct retrieval succeeding rules out a
   missing upload/index; it does not prove the original query clears threshold.
3. Review whether any framework change is needed with the owner before implementing
   it. No additional framework patch, search-all recovery, keyword routing or
   canned-answer replacement was added to force the quality set green.
4. A freshly bound BYO runtime is not qualified by this run. The exact-destination
   customer form and focused binding/save/security regressions pass; managed
   provisioning is the path proven end to end here. No other customer's provider
   credentials were reused to manufacture a BYO pass.

## Approved Follow-Up: Qualified Generic Defaults

The owner approved configuration-first alignment and promotion of reusable
settings to new-template defaults only if answer quality improves. The trial
keeps the original six questions, existing Brain/apps, provider model and safety
contracts. It adds bounded API-record memory without an invented vector space,
sets retrieval threshold to 0.4, and guides the model to distinguish factual
service questions from requests to execute unavailable mutations. Fan-out is
unchanged because Harbor has only one document space.

Framework 0.8.52 already persists/seeds action targets with no vector space. The
Platform connector's collection mapper is being aligned with that existing
optional field contract; identifiers, field projections, bounds and auth remain.
The authoring catalogue also explains declarative record memory and optional
user-input semantics, so imports can produce the same supported configuration.
No framework release or semantic matcher is introduced. The initial trial and
three later configuration trials completed on the same resources. The latest
`ver-47128d34` / `rel-4f279fec` is APPLIED_VERIFIED/PASSED, and both apps remain
ACTIVE/ready. Final original-six scores improved to **4/6 and 5/6**, but failed
the two-run qualification criterion. Delivery eligibility/totals are still
sometimes wrong or contradictory; one comparison missed its second record, and
an extended question requested a technical domain. **Additional defaults were
not promoted.** Published generic1.4.0 and its selected starter remain unchanged.
Read [the complete quality/defaults record](./2026-11-14-quality-defaults.md) for
all intermediate results, successful retrieval/memory changes and remaining gaps.
Do not interpret the earlier v5-only results above as the latest live score.

A subsequent [stronger-model comparison](./2026-11-14-stronger-model-comparison.md)
also scored4/6 and5/6 on the original set. Explicit boundary questions improved,
but contradictory delivery eligibility persisted. The retained lab is now back
on the exact pre-comparison mini configuration via `ver-a328ccdd` /
`rel-3d2911a9`, APPLIED_VERIFIED/PASSED; native `vrf-6589fdee` is
30 passed/0 failed/4 skipped. Both apps and the original consumer remain active.
No additional template/default promotion was made.

## Final Host Snapshot

14 November 2026, 02:51 UTC. These are instantaneous observations, not capacity
admission measurements. Staging: 8 CPUs, load 3.10/2.48/2.36, 2780 MiB available,
4125 MiB swap in use. Production: 4 CPUs, load 0.37/0.42/0.44, 8537 MiB available,
no swap configured. All image builds ran in CI. Staging remains memory-constrained;
this task did not start a capacity/recovery redesign or remove unrelated apps.

## Evidence And Retention

Authoritative raw traces, intent receipts, authenticated browser state, saved
drafts, screenshots and live read-only vector-handle inventory are private at
`~/.codex/private/loomai-harbor-journey-20261114/`.
Never publish that directory or replay its consumed creation/publication intents.

The new configuration and lab service are retained for review. Existing
Northfield/Support deployments were not modified. The three bounded Platform fixes
above are deployed; no framework product source or release was changed.

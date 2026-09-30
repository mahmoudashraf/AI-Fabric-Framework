# Dealership AI Live Quality Findings

## Decision

The agreed canary order was applied on staging. The UI remains on `executor`
mode at the `search` position, deployment version `ver-5ae2dd59` is live, and
release `rel-5c6ee444` is `APPLIED_VERIFIED` on AI Fabric `0.8.5`.

The canary is operational but not a green quality baseline. It confirmed one
AI Fabric grounding-sufficiency blocker and two deployment-level quality
follow-ups. Prompt enhancement was intentionally not published because the
agreed order requires action/RAG mechanics to be green first.

## Applied canary

- Planning mode: `ITERATIVE`
- Maximum read-action iterations: `2`
- RAG cooperation: `RAG_IF_ACTIONS_INSUFFICIENT`
- Read actions: inventory search, vehicle detail, comparison, and trusted
  vehicle reference resolution
- Write target: `vehicleId` is `INTERNAL`, has `askUser: false`, and resolves
  through the trusted read action
- Runtime replacement: stop-first, preserving the single-writer Lucene volume
- Inventory sync after replacement: `6/6` completed, `0` failed
- Runtime and connector: `running:healthy`

The quality harness itself made no live mutation and never confirmed a write.

## Live scope

- Public route: `https://loomai.pro/demos/dealership-ai`
- Runtime: `https://dep-f023c863.46.224.145.148.sslip.io`
- Integration: `public-runtime-anonymous`
- Vector space: `dealer-vehicle`
- Viewport: `390x844`
- Conversation: one continuous seven-turn session

The final instrumented run completed at `2026-09-30T21:54:55.457Z`. All global
transport, context, conversation, no-confirmation, and no-write assertions
passed. Two scenarios passed fully and five exposed quality findings.

## Confirmed good behavior

1. Exact electric-stock search used authoritative action data and returned the
   correct three vehicles and commercial facts.
2. The towing and poor-weather request combined action and six indexed
   documents and selected the Caldera X6 for AWD and tow preparation.
3. Every request stayed on `executor` + `search`, retained dealership and
   vector-space scope, and reused one runtime conversation.
4. The write-intent test did not confirm or execute a write and issued no
   receipt.
5. `vehicleId` is no longer exposed to the buyer. The trusted resolver endpoint
   maps an unambiguous Aster E1 reference to the active internal vehicle.

## Blocker: empty action still suppresses RAG

Prompt:

> Do you have a diesel SUV under GBP 10,000? If not, use indexed current-stock
> evidence to suggest the closest alternative without claiming it matches.

Observed in two consecutive post-canary runs:

- `dealership_search_inventory` succeeded with `itemsCount=0`;
- AI Fabric marked the result `groundingUsable=true`;
- no read-action iteration or independent RAG retrieval ran;
- the answer generalized the filtered no-match into no dealership inventory;
  and
- six correctly scoped indexed vehicle documents were available to other
  queries in the same run.

Provider request IDs:

- `rag-04a33c82-3dee-4232-a950-a5e14fa2a49c`
- `rag-9d3b1db5-97b0-4d71-9224-60fd7b245386`

This satisfies the agreed framework-escalation condition. The framework change
request is recorded as
`docs/planning/0024-empty-read-action-grounding-sufficiency-regression.md` in
the AI Fabric repository at commit `41ec78e0`. It requires a generic
distinction between action execution success and grounding sufficiency, with
no domain names, field-name heuristics, or text matching.

## Deployment finding: comparison references are unresolved

The named comparison reproducibly invokes `dealership_compare_vehicles` with
buyer-facing references that the current route treats as trusted IDs. The
dealership backend therefore returns `UPSTREAM_HTTP_409` / `409_CONFLICT` with
"One or more selected vehicles are no longer active."

Quality-run provider request:
`rag-6aeed24a-f060-4260-8386-4e4689fae3cd`.

Isolated reproductions:

- `rag-bdb9c175-0e70-4490-be64-057d852cda17`
- `rag-798b405e-0805-450d-af4b-2fc042af0a51`

This is a deployment action-contract problem, not enough evidence for a second
framework issue. The next deployment change should accept buyer-facing vehicle
references and resolve both targets through trusted inventory lookup before
calling the ID-only comparison endpoint.

## Deployment finding: generated facts can drift

The contextual follow-up selected the correct Aster E1, `298` mile range, and
SUV body type, but volunteered a price of GBP 42,000. The authoritative action
result says GBP 31,950. A preceding run produced GBP 45,000, so this is not a
one-off display typo.

After the framework blocker is fixed, the post-action answer contract should
copy commercial facts exactly and omit unrequested fields instead of
reconstructing them. This is the first prompt/evidence-projection enhancement
to canary.

## Remaining UX findings

- The warranty answer correctly says approved warranty evidence is unavailable,
  but then adds unrelated stock recommendations. The answer should stop at the
  evidence boundary or offer dealership contact.
- The governed-write response keeps the internal target hidden and remains
  safe, but currently presents a generic or one-field-at-a-time clarification.
  Product UX must decide whether that is intentional or whether a
  framework-supported grouped clarification contract is needed.

Neither finding justifies weakening confirmation, trusted target resolution,
or action authorization.

## Next order

1. Keep the current UI mode, position, scoped context, and bounded canary
   policy.
2. Fix and release the generic AI Fabric empty-result sufficiency regression.
3. Upgrade/redeploy this runtime and rerun the exact fallback scenario.
4. Resolve comparison targets through trusted deployment-owned lookup.
5. Only then canary prompt/evidence-projection changes for exact commercial
   facts and concise knowledge-boundary answers.
6. Run the full strict matrix; do not mark the dealership quality gate green
   until every scenario passes.

The complete local report is generated at
`Platfrom/loomai-site/test-results/dealership-quality/latest.json`. The compact
checked-in evidence is
`verification-support/autotrader-dealership-demo/evidence/2026-09-30-dealership-live-quality.json`.

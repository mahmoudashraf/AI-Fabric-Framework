# Harbor Selected-Result Follow-ups: Live Investigation

## Verdict

The reported internal-domain clarification still happens on the current Harbor
v7 release `rel-2d36dfcf`, framework 0.8.59, private image source
`e01eadd2866398562985e04a6226a3ba3173421d`. This is not a stale deployment,
credit-exhaustion error or missing customer-supplied knowledge-domain name.

Four internal-domain clarification failures were reproduced in nine recorded
live query cases. There are also two confirmed widget defects. The previous
ten-case evidence qualification remains valid for its recorded cases, but was
insufficient coverage of selected-result and implicit-target follow-ups. Do not
reuse its 9/10 result as a complete customer-journey quality claim.

No product code, deployment configuration, release, assignment or infrastructure
was changed by this investigation. All business operations were READs; the
fictional enquiry counter remained 13. The temporary anonymous browser closed.

## What Was Tested

1. Browse furniture through the actual configured UI button: two READ searches
   returned a standing desk and chair with prices, stock and height adjustment.
2. Attach the standing desk's generic result card and ask `summarize it`:
   CLARIFICATION_REQUIRED, asking which knowledge-base domain to search.
3. Keep that item attached and ask `delivery options`: the same internal-domain
   clarification, despite a configured eligible delivery READ action.
4. Ask the full configured delivery question with the item attached: the delivery
   READ action ran and returned the correct policy. This specific sample passed.
5. Ask the care question after delivery: the planner correctly selected document
   RAG, but search returned zero documents. The older wrong-delivery-source
   answer was not reproduced in this sample; retrieval remains unresolved.
6. Attempt to add the chair as a second generic result card, then compare:
   only the desk was attached. The AI correctly asked for a second item; the
   widget had incorrectly rejected the chair as a duplicate.
7. Start a fresh conversation with no attachments and browse again: both products
   were returned by two READ actions.
8. Ask `compare between them` without attachments: internal-domain clarification.
   The framework had already resolved both returned product targets.
9. Attach both full READ source cards through the Sources panel and compare:
   internal-domain clarification again. Both request attachments retained the
   full product facts, independently disproving missing fields as the only cause.

## Root Cause 1: A Valid Model Decision Is Overridden

The failing requests have these actual diagnostics:

```json
{
  "intent": {"requiresRetrieval": false, "requiresGeneration": true},
  "plannerDecision": "ANSWER_FROM_CONTEXT",
  "proposedActions": [],
  "needsMoreSteps": false,
  "contextOnlyContinuation": false,
  "executedActionsCount": 0,
  "contextGroundingUsable": false,
  "useRag": true,
  "authorizedVectorSpaces": ["document"],
  "fanoutEnabled": false
}
```

The earlier 0.8.54 fix introduced a narrowly defined history-only exception. Its
`isContextOnlyContinuation` predicate rejects any resolved targets, pinned-target
context, attachments, normalized attachments or transient inputs. Therefore a
valid context-only plan falls outside the exception as soon as those inputs are
present. `shouldUseRag` then interprets the absence of newly executed action
evidence as requiring RAG under `RAG_IF_ACTIONS_INSUFFICIENT`.

The model did not request vector retrieval or select a vector namespace. The
subsequent vector router receives no selection and emits the hardcoded question
`Which knowledge base domain should I search?`. The registered, authorized
`document` source is visible in the planner catalogue; this is not the older
missing-catalogue problem. Effective `forceRetrievalWhenTargetsPresent` and
`forceRetrievalConsiderStoredTargets` are both false, so disabling those settings
is not a fix for these samples.

This also affects requests without attachments. In case 8, `targetResolution`
reported `ACTION_RESULT_ITEMS`, count 2. The same blanket target exclusion made
the context-only predicate false, even though the targets came from the preceding
authorized READ results.

Published framework source is `5feab5beaee6b225ea42dcf1c889ab2fa0007ae4`:

- [Context-only exclusions](/Users/mahmoudashraf/.codex/worktrees/automatic-evidence-boundary-0859/ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/intent/orchestration/information/ReadActionResolutionService.java:809).
- [ANSWER_FROM_CONTEXT becomes RAG without fresh action evidence](/Users/mahmoudashraf/.codex/worktrees/automatic-evidence-boundary-0859/ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/intent/orchestration/information/ReadActionResolutionService.java:843).
- [Internal-domain clarification](/Users/mahmoudashraf/.codex/worktrees/automatic-evidence-boundary-0859/ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/intent/orchestration/pipeline/steps/InformationVectorSpaceRoutingSupport.java:93).
- [Existing tests explicitly expect RAG for targets and attachments](/Users/mahmoudashraf/.codex/worktrees/automatic-evidence-boundary-0859/ai-infrastructure-module/ai-fabric-core/src/test/java/ai/fabric/intent/orchestration/information/ReadActionResolutionServiceTest.java:214).

The predicate was introduced in commit `862eefd4`, included in releases
0.8.54 through 0.8.59. Automatic evidence in 0.8.59 did not introduce this bug.

## Root Cause 2: Generic Item Attachment Drops Facts

The visible card contained ID, name, category, `priceGbp: 499`, `stock: 8` and
`adjustableHeight: true`. Its actual outgoing attachment was only:

```json
{
  "id": "prd-desk-01",
  "contentText": "Harbor Standing Desk | Category: desk",
  "metadata": {
    "id": "prd-desk-01",
    "category": "desk",
    "name": "Harbor Standing Desk"
  },
  "source": "item"
}
```

The legacy generic serializer whitelists familiar commerce fields such as
`price`, not arbitrary approved fields. It drops `priceGbp`, `stock` and
`adjustableHeight` from both text and metadata. This happens after the backend
has correctly prepared and returned Automatic evidence. Adding only another
price alias would not solve generic plugin/action output preservation.

Source: [Generic attachment conversion](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/max-mode-widget/src/attachments.ts:33).

## Root Cause 3: Missing SKU Causes False Duplicates

The item callback uses ID equality OR SKU equality without checking that the
identity fields exist. For the desk and chair, the IDs differ but both SKUs are
absent, so `undefined === undefined` makes the second card look duplicated.
The UI still displays its attach button, but the callback refuses to add it.

Live evidence: after clicking the chair's attach button, only the standing-desk
chip remained; the next query contained one attachment. A separate predicate
reproduction confirmed that the current expression returns true for the two
different IDs, while a nonempty-identity comparison returns false.

Source: [Duplicate item check](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/max-mode-widget/src/hooks/useAttachmentsController.ts:87).

## Care Search Qualification

Today's care sample selected `USE_RAG_ONLY`, namespace `document`, with a
care-specific retrieval query. RAG returned success with zero documents and an
empty context; the answer disclosed missing care evidence. It did not answer
with delivery-policy facts. This does not prove the older wrong-source behavior
can never recur, nor explain why the document search returned zero. A separate
indexed-document/filter/search audit is needed; no storage-permission or missing-
document cause is asserted from a zero-result response alone.

## Recommended Bounded Work

1. Correct the framework context-only path first. Honor the completed
   `ANSWER_FROM_CONTEXT` decision, together with `requiresRetrieval=false`, for
   authorized available context including resolved results and attachments.
   Preserve explicit retrieval requests, incomplete/failed action handling,
   conversation ownership and actual search authorization. Context reuse is
   not fresh business evidence and must not acquire new action authority.
2. Repair generic result-selection handoff. Prefer server-owned result/evidence
   references resolved within the authorized conversation and existing prepared
   evidence contract. The UI already has a result-reference shape, but this
   investigation did not establish a complete server resolver for generic cards.
   Reuse conversation/result storage; do not invent a separate result database.
   Do not copy arbitrary raw ActionResult data into model context or bypass
   Selected fields, structural credential exclusions or evidence bounds.
3. Fix item identity comparisons: compare IDs/SKUs only when both values are
   present. Use one consistent identity rule for attachment and reattachment.
4. Qualify the exact selected-card/source journeys, short follow-ups and current
   care-document retrieval. Keep the last item separate from the confirmed
   context-routing defect until its cause is traced.

No phrase-based question detection, dealership-specific prompts, extra model
rounds or stronger model is needed to correct the confirmed routing override.
Enabling fan-out is not the primary fix: it can hide the unnecessary RAG branch
without repairing the decision override, data loss or duplicate selection.

Required tests include context-only requests with two resolved READ targets,
one/two current attachments, full source attachments, ID-only records, empty
Selected projections and selected-field exclusions after reattachment. Actual
document searches must retain their current owner/source filters. Known-real
foreign conversation/result references must remain denied, and no write or
confirmation permission may be widened.

Framework changes are proposed, not implemented or published. Obtain owner
review/approval before the next framework patch.

The owner accepted this direction and requested an initial plan on 2026-11-22.
[010.70 implementation plan](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_70_LLM_CONTEXT_ROUTING_AND_SELECTED_RESULT_RELIABILITY_PLAN.md)
separates framework routing, protected widget evidence handoff and widget-only
identity fixes. Preparing that plan did not implement or deploy the changes.

The owner's subsequent 2026-11-22 clarification supersedes the narrower proposed
handoff policy above: explicit attachment approves the full selected UI item's
data as user-provided AI context, even when fields were omitted from automatic
Selected evidence. Automatic inclusion remains projection-controlled; ownership,
action authority and credential protections are unchanged. The revised 010.70
does not require a server resolver just to include explicit attachment content.
This policy update does not change any of the recorded live findings.

## Evidence And Closure

Private raw requests, responses, diagnostic metadata and screenshots:
`/Users/mahmoudashraf/.codex/private/harbor-followup-20261121/`.
`cases.private.json` contains all nine samples, including passing and failing
ones; `live-version-and-safety.private.json` confirms the current release,
matching assignment, healthy e01 runtime/connector with zero restarts, and no
business writes. The browser was anonymous; no admin session was created.
No user credentials or raw production secrets are included in this document.

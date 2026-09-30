# Dealership AI Live Quality Findings

## Decision

The live dealership experience is operational and already proves useful
action, retrieval, comparison and multi-turn behaviour. It is not yet a green
quality baseline for the desired action-to-RAG fallback or customer-facing
lead clarification.

This is a quality finding, not an availability failure. No deployment,
framework, prompt, provider data or live configuration was changed during the
run.

## Live scope

- Public route: `https://loomai.pro/demos/dealership-ai`
- Runtime: `https://dep-f023c863.46.224.145.148.sslip.io`
- Integration: `public-runtime-anonymous`
- UI mode: `executor`
- UI position: `search`
- Vector space: `dealer-vehicle`
- Viewport: `390x844`
- Conversation: one continuous seven-turn session

The widget's `features.debug` setting only controls whether its inspector is
visible. The quality harness captures the same raw request and response at the
browser network boundary and records policy, action, retrieval, history and
provider-request diagnostics without changing the deployed widget.

## What passed

1. Exact electric-stock search used `dealership_search_inventory` and returned
   the three correct matches.
2. The follow-up retained the conversation, used prior context, and combined
   action data with six independent `dealer-vehicle` retrieval documents.
3. Named comparison used `dealership_compare_vehicles` for the correct two
   vehicles.
4. The towing and poor-weather request combined action and RAG evidence and
   selected the Caldera X6 for its AWD and tow preparation.
5. The warranty request stated that policy evidence was unavailable and did
   not invent warranty duration or coverage.
6. Every request remained on `executor` + `search`, carried the correct
   dealership/vector-space context, and reused one conversation.
7. The lead-intent check did not confirm or execute a write, create a receipt,
   or produce a browser/runtime transport error.

## Finding 1: empty action suppresses RAG

Prompt:

> Do you have a diesel SUV under GBP 10,000? If not, use indexed current-stock
> evidence to suggest the closest alternative without claiming it matches.

Observed:

- `dealership_search_inventory` executed successfully;
- `itemsCount=0`;
- the result was marked `groundingUsable=true`;
- no independent RAG document was returned;
- the response claimed that the dealership had no inventory, despite six
  active demo vehicles; and
- the requested truthful nearest alternative was not supplied.

Final provider request ID:
`rag-495b0755-9819-4247-99fb-420ad8dd23bd`.

The same behaviour reproduced in two earlier sessions with request IDs
`rag-bbcefb1f-635f-4234-a584-4cd9cf00fa2e` and
`rag-b6e241af-62de-429e-b29a-8fa82cf0dd69`.

The effective policy was:

```yaml
planningMode: SINGLE_PASS
maxIterations: 1
ragCooperationMode: RAG_IF_ACTIONS_INSUFFICIENT
```

### Recommended ownership order

1. **Deployment configuration:** keep the public UI on `executor` and
   `search`, but canary this executor policy in a new immutable deployment
   version:

   ```yaml
   ai:
     orchestration:
       modes:
         executor:
           read-action-resolution:
             planning-mode: ITERATIVE
             max-iterations: 2
             rag-cooperation-mode: RAG_IF_ACTIONS_INSUFFICIENT
   ```

2. **Deployment evaluation:** run the same quality matrix and require the
   zero-result action plus at least one independent `dealer-vehicle` document.
   The answer must say that no exact diesel/budget match exists and may present
   Caldera X6 only as a clearly labelled alternative.
3. **Optional separate canary:** evaluate `PARALLEL_ACTIONS_AND_RAG` for broad
   mixed questions. Do not make it the default until latency, token cost,
   duplicate evidence and answer quality are measured.
4. **Framework escalation only if the config canary fails:** report that a
   successful empty collection remains `groundingUsable` and suppresses the
   explicitly configured fallback. Transport success and sufficient grounding
   need separate semantics, backed by a regression test.

The existing deployment answer prompt already requires nearest truthful
alternatives. Prompt changes alone will not solve this turn because the
alternative evidence never reached generation.

## Finding 2: lead clarification exposes an internal target

Prompt:

> I want to book a test drive for the Aster E1. Tell me what details and
> confirmation you need, but do not submit anything.

Observed answer:

> To proceed, please provide: vehicleId, name.

Safety worked: the response was `CLARIFICATION_REQUIRED`, no confirmation was
sent, and no write completed. The customer experience did not:

- `vehicleId` is an internal target and must not be requested from a buyer;
- the response omitted email or phone, consent, preferred date and final
  confirmation; and
- the named Aster E1 was not resolved to a deployment-validated target.

Provider request ID: `rag-526106d5-4271-40e2-b284-5e36288a47ad`.

### Recommended ownership order

1. **Deployment action contract:** set `vehicleId` to `visibility: INTERNAL`
   and `askUser: false`.
2. **Trusted target resolution:** resolve a card-initiated request from a
   deployment-validated vehicle attachment. For free-text vehicle names, use
   an exact read lookup and fail closed when the result is missing or
   ambiguous. The action backend must still revalidate dealership ownership
   and active status.
3. **Customer clarification:** ask only for the buyer-owned fields: name,
   email or phone, optional preferred date, contact consent and explicit final
   confirmation.
4. **Framework change:** none is justified by this result because the current
   action schema already supports hidden `askUser: false` parameters and
   trusted resolver sources.

## Mode and position recommendation

Keep `executor` and `search`. The current UI contract is behaving correctly,
and the browser must not switch orchestration modes based on query wording.

The session is already iterative at the conversation level: one
`conversationId` was retained and history increased from 0 to 12 messages.
That is distinct from read-action planner iteration, which is a bounded,
server-owned deployment policy. The first canary should change only the latter.

## Acceptance gate

The quality baseline becomes green when:

1. the existing five passing scenarios remain green;
2. an empty authoritative action is followed by independent indexed evidence
   and a truthful nearest alternative;
3. a filtered no-match is never described as an empty dealership inventory;
4. lead clarification exposes no internal IDs and lists the human-facing
   requirements;
5. no write occurs before explicit confirmation; and
6. the strict run exits successfully:

   ```bash
   cd Platfrom/loomai-site
   DEALERSHIP_QUALITY_STRICT=true npm run quality:dealership-live
   ```

The full local report is generated at
`Platfrom/loomai-site/test-results/dealership-quality/latest.json`. The compact
checked-in evidence is
`verification-support/autotrader-dealership-demo/evidence/2026-09-30-dealership-live-quality.json`.

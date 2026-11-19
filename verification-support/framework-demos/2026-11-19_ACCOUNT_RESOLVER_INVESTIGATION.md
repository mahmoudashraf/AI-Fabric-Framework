# Account Resolver payment follow-up investigation

## Outcome

The supplied scenario was reproduced on the live demo. Upgrading to published
framework 0.8.55 alone did not fix it in the isolated real-provider comparison.
An existing explicitly configured bounded recovery removed the routing failure,
but one generated answer remained contradictory. No live deployment, source
configuration, original conversation or framework production code was changed.
The separate generic source-handoff correction is proposed for owner review.

## Scope and method

- Live page: https://ai-fabric.dev/demos/ai-fabric-account-resolver.
- Existing backend/app UUID prjnwmfsk6bva2r2lxnq6lwh on staging.
- Public health reported 0.8.42, but the actual executable contained only 0.8.50
  framework jars. The retained environment AI_FABRIC_VERSION=0.8.42 and
  APP_BUILD_COMMIT=HEAD make its diagnostic identity stale.
- Live image source d16c7e40b4a57367e151ce9947aa4734e4fd7460. The example Dockerfile
  still defaults AI_FABRIC_VERSION to 0.8.50 despite the Maven project using 0.8.55.
- Candidate source 45ad0b059729d4dc7118ee5cdef1d7e2be943e81, resolved from the
  isolated Central-only Maven repository. Every packaged framework jar is 0.8.55;
  core SHA-256 matches public Central bytes
  66f162f5e418eea4122dc5f0eef7a65383096b0a58eab02323d3946273688671.
- The real OpenAI gpt-4o-mini model, resolver prompts and base policy settings
  were unchanged between baseline and candidate. Existing simple/hash embeddings
  and Lucene were retained; this is not an embedding-quality benchmark.
- Each comparison used newly created synthetic missing-payment accounts, an
  isolated conversation, form-supplied last4=1234 and an actual pending-action
  confirmation. No original user account/conversation was reused or reset.
- Local example reactor passed 52 Account Resolver tests plus 13 smoke-support
  tests, zero failures/errors/skips. The initial hosted page also loaded through
  an isolated headless browser without application JavaScript errors. The native
  in-app browser was unavailable. Full seven-turn comparisons used the same API
  payloads as the hosted UI; they are not full browser clickthrough proof.

## Reproduction

The seven turns in each comparison were:

1. Why can't I place an order
2. add a payment method
3. Submit the payment form with last4=1234.
4. Yes, confirm and execute the pending account resolver action.
5. can i make an order  ?
6. Check if the account has a verified payment method
7. why i can not make an order  ?

Live 0.8.50 completed the payment action, then turn 5 returned
CLARIFICATION_REQUIRED / VECTOR_SPACE_SELECTION_REQUIRED. The final question
produced a contradictory opening sentence despite all account requirements being
satisfied. Original candidate 0.8.55 again completed payment, but both turns 5
and 7 requested an internal knowledge domain. Failures were retained, not rerun
until passing or relabelled as upgrade success.

## Actual cause

This is not the absent-source catalogue defect fixed in 0.8.55. Both authorized
namespaces, account-resolution-policy and subscription-plan, were present.
It is also not failed payment, missing history or truncated follow-up context.

The failing 0.8.55 trace shows:

1. Extraction chose INFORMATION with retrieval required and no namespace.
   A configured model repair followed an omitted needsAdvancedRAG field; the
   repaired intent still supplied no namespace.
2. Read planning iteration 1 selected get_account_profile, the policy namespace
   and a retrieval query. The actual read succeeded with verified payment,
   active subscription and validated billing address.
3. Iteration 2 selected ANSWER_FROM_CONTEXT and omitted the earlier source/query.
4. ReadActionResolutionService derives preferredVectorSpaces and retrievalQuery
   from lastDecision only. The valid earlier selection was lost.
5. PARALLEL_ACTIONS_AND_RAG still required policy retrieval. The demo enabled
   fan-out capability and a two-source budget, but its semantic missing-source
   policy remained LLM_REPAIR_THEN_CLARIFY. Fan-out capability alone does not
   authorize bounded recovery. With no retained selection, the router asked the
   customer for a knowledge domain.

## Controlled recovery experiment

A separate local 0.8.55 run changed only:

```yaml
ai:
  orchestration:
    modes:
      resolver:
        semantic-recovery:
          missing-vector-space: BOUNDED_ALLOWED_FAN_OUT
```

The existing allowlist and max-spaces=2 remained unchanged. Turns 5 and 7 returned
INFORMATION_PROVIDED, not an internal-domain clarification. Turn 7 recorded
EXPLICIT_BOUNDED_FAN_OUT_RECOVERY and searched exactly the two approved sources.
Payment confirmation and the successful receipt were preserved.

Routing passed, but answer quality was partial: turn 7 opened with a logically
contradictory claim that the user could not order because there were no issues,
then stated that everything was ready. Do not count HTTP success or absence of
domain clarification as a fully correct customer answer.

## Recommended changes for review

- Generic framework correction: retain the latest model-selected retrieval
  source and associated query within the same iterative read-planning turn when
  subsequent steps omit them and the effective policy still needs RAG. A later
  explicit selection supersedes the earlier one. Keep registration, allowlist,
  task-profile, tenant and budget checks; do not guess a source or add hidden
  retries. This needs focused regression and real-provider verification before
  any new framework release. It is not implemented or approved by the earlier
  0.8.55 catalogue authorization.
- Demo configuration: explicitly enable its existing two-source bounded recovery,
  keeping the same approved source ceiling, as already intended by its prompt.
- Demo adoption and diagnostics: update the Dockerfile pin to the qualified
  framework version and remove stale diagnostic environment overrides. Publish
  through the existing CI image workflow; no build on the serving host.
- Qualify answer wording separately. Review the older resolver prompt overlay
  against the current extraction contract and ensure successful account facts
  produce a consistent eligibility conclusion. Do not mask model errors with
  phrase matching, canned answers or rewritten receipts.
- Legacy smart suggestions still generated redundant profile requests and
  displayed retrieval-preview text. The framework upgrade alone does not adopt
  Platform's configured behavior/suggestion profiles. Treat that as a separate
  presentation/configuration qualification, not the source-routing root cause.

## Implementation anchors

- Public framework 0.8.55: ReadActionResolutionService.java, resolve(), especially
  lastDecision and preferredVectorSpaces/retrievalQuery projection.
- InformationVectorSpaceRoutingSupport.java, route(): selected-source validation,
  explicit bounded recovery, then VECTOR_SPACE_SELECTION_REQUIRED.
- Example application.yml and application-prod.yml: resolver mode, required RAG
  cooperation, explicit source allowlist and semantic recovery defaults.
- Example Dockerfile and DeploymentInfoService: build pin and diagnostic overrides.

Raw snapshots, conversations, logs and local harnesses remain outside Git under
~/.codex/private/account-resolver-20261119/. Temporary local JVMs are stopped at
the end; hosted synthetic fixtures follow the demo's existing cleanup lifecycle.

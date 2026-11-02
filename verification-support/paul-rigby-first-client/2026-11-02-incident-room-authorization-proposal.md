# Incident Room Authorization Correction Proposal

Date: 2026-11-02. Status: **REVIEW REQUIRED; NOT IMPLEMENTED**.
Scope: read-only source/log investigation; no framework source, tests, release, API mutation, browser session or provider calls.
Source: clean `/private/tmp/ai-fabric-first-customer-20261102`, commit `c586e0733aacc96cdb24d6c23ae6fd23fc97b6b1` (0.8.45), not the older dirty checkout.

## Finding And CI Classification

[Framework Build 41397841080](https://github.com/Loom-AI-Labs/ai-fabric-framework/actions/runs/41397841080) failed in the real-app suite: `IncidentInvestigationIntegrationTest` ran 28 tests, with 14 assertion failures and no errors/skips in that class.
The [retained log](/Users/mahmoudashraf/.codex/private/loomai-paul-rigby-01055/acceptance-20261102/framework-build-first.log:11622) shows interactive chains/managers returning `FAILED` with `Access denied by policy.` and empty results/timelines before the expected worker behavior.
Framework reactor, integration-suite compilation and standalone-consumer builds succeeded earlier in that log. Later real-app modules and downstream smoke stages were not qualified by this failed run; their skipped status is not a pass.
This is a deterministic **example authorization integration regression**, not a flaky semantic assertion or a failure introduced by the bounded 0.8.45 action-draft patch. Diff against 0.8.44 `c43d5dbee` shows no Incident Room Java/test or auth-resolver change; only example version/docs/build defaults changed.
The caller/resource distinction was introduced by `0a6611e1` (22 October). This explains the mismatch, but this investigation did not rerun that historical commit or a corrected candidate.
Keep broad CI **FAILED / known exception** until fixed and rerun. Successful publication and focused tests do not make the full release checklist green.

## Verified Root Cause

- [IncidentSessionService.java:51](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/IncidentSessionService.java:51) persists a generated `incident-owner-*`, conversation ID and independently selected scenario ID; `active()` rejects missing/expired sessions.
- [IncidentSmartInvestigationService.java:231](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/IncidentSmartInvestigationService.java:231) and [IncidentConversationService.java:81](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/IncidentConversationService.java:81) correctly create INTERACTIVE/END_USER contexts: initiator = session owner; execution subject = incident scenario.
- [OrchestrationAuthContextResolver.java:57](/private/tmp/ai-fabric-first-customer-20261102/ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/intent/orchestration/OrchestrationAuthContextResolver.java:57) intentionally preserves the interactive initiator as `auth.subjectId`; it does not impersonate the incident. [Existing regression tests:11](/private/tmp/ai-fabric-first-customer-20261102/ai-infrastructure-module/ai-fabric-core/src/test/java/ai/fabric/intent/orchestration/OrchestrationAuthContextResolverTest.java:11) explicitly protect this behavior.
- [IncidentAccessControlConfiguration.java:48](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/config/IncidentAccessControlConfiguration.java:48) nevertheless requires `scenario.id == auth.subjectId`; the real owner therefore fails the initial READ policy.
- [IncidentActionScopeResolver.java:55](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/IncidentActionScopeResolver.java:55) repeats the error with `catalog.require(auth.subjectId)`. Fixing only the first gate would leave this second barrier.
- [IncidentExecutionService.java:613](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/IncidentExecutionService.java:613) uses APPLICATION/SERVICE; its scenario-subject mapping still works. Do not break the already-working sequential/parallel application-plan path.

## Proposed Example-Only Contract And Changes

The correct interactive association is **verified caller owner ID -> exactly one active persisted demo session -> stored scenario ID -> catalog scenario/deployment/revision**. It must not come from question text, model action parameters, request-body incident IDs, a deployment-only match, or an ID-prefix guess.
[EntityAccessPolicy.java:21](/private/tmp/ai-fabric-first-customer-20261102/ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/access/policy/EntityAccessPolicy.java:21) receives verified auth plus entity metadata, not `TrustedExecutionContext.subject`; [AIAccessSubjectContext.java:19](/private/tmp/ai-fabric-first-customer-20261102/ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/dto/AIAccessSubjectContext.java:19) has no separate incident-resource field. The persisted association supplies the missing application knowledge without extending the library API.

1. Add an example-local `IncidentAuthorizedScenarioResolver` under `examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/`. Use the repository/catalog/Clock directly, not a request ThreadLocal or `IncidentSessionService` dependency that could introduce chat/policy bean cycles.
2. Extend [IncidentDemoSessionRepository.java:8](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/IncidentDemoSessionRepository.java:8) with an exact owner-and-expiry lookup. INTERACTIVE/END_USER requires exactly one unexpired owner association; absent, expired or ambiguous associations deny. Owners are generated per session today; a real multi-session account would need an explicit verified session binding, not a first-match rule.
3. Retain the existing APPLICATION/SERVICE catalog-subject route as an explicit source contract, not a fallback after interactive lookup fails. Preserve tenant `public-demo`, matching deployment, trusted modes/caller types and declared scopes; never replace the caller with the scenario.
4. Inject the common resolver into `IncidentAccessControlConfiguration` and `IncidentActionScopeResolver`. Keep `rag:intent` + READ + approved specialist checks at the first gate and exact `action:<name>` at execution. Return `AuthorizedIncidentScope` only from the resolved scenario and its revision.
5. In the action path, where [ActionContext.java:74](/private/tmp/ai-fabric-first-customer-20261102/ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/intent/action/ActionContext.java:74) already exposes the pipeline's typed trusted execution context, cross-check any incident subject against the authorized scenario. Do not add an untrusted metadata route for the resource.
6. Leave [IncidentController.java:309](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/web/IncidentController.java:309) session-token enforcement, conversation binding, worker allowlists and [event boundary filtering:118](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/main/java/com/ai/fabric/realapps/incident/service/InMemoryIncidentEventRepository.java:118) intact. Exact ownership/expiry/scope comparisons protect authority; they are not prohibited semantic text matching.

## Narrow Sibling Check

Exact resource-from-`getSubjectId()` catalog/equality searches across real-app Java found only the two Incident Room sites above. Nearby MCP and agentic-resolver access policies do not perform that incident/resource lookup; this is not a global authorization audit.
[IncidentReadActionSupportTest.java:135](/private/tmp/ai-fabric-first-customer-20261102/examples/real-apps/incident-investigation-room/src/test/java/com/ai/fabric/realapps/incident/action/IncidentReadActionSupportTest.java:135) mocks only APPLICATION/SERVICE with the incident ID as subject, explaining why its unit coverage misses interactive owners. Keep those positive tests and add interactive coverage rather than changing fixtures to impersonate incidents.

## Verification Required After Approval

- Add `IncidentAuthorizedScenarioResolverTest`: active owner resolves its scenario; missing/expired/duplicate owner, unknown scenario, wrong tenant/deployment and invalid mode/caller pair deny; APPLICATION/SERVICE still resolves normally; failed interactive lookup never falls through.
- Add `IncidentAccessControlConfigurationTest`: authorized interactive and application contexts pass only approved READ resource/specialist scope; unrelated owners and wrong resource/operation/scope deny.
- Extend `IncidentReadActionSupportTest`: real owner association reaches only its incident/deployment/revision; missing action scope and conflicting trusted incident subject deny with no event read. Forged action parameters must not select a scenario.
- Extend `IncidentInvestigationIntegrationTest`: two sessions with different owners; mismatched session header, expired session and caller/body-supplied authority cannot cross boundaries. Preserve current positive application-plan, replay and history assertions.
- Rerun all 28 existing integration tests, especially all 14 previously failing cases below; rerun the whole Incident Room module offline/smoke suite, existing core `OrchestrationAuthContextResolverTest` and `ActionContextTest`, then normal broad CI and Incident Docker smoke. No skipped assertions or weakened expected outcomes.
- Provider-backed validation is separate and opt-in after approval; no live model calls were made for this proposal. Successful initial authorization may reveal additional failures, so do not promise that these changes alone produce green CI.

The 14 baseline failures are:
```text
smartInvestigationAdaptsAfterHealthResult
conversationManagerUsesBackendHistoryAndReplaysSameTurn
smartInvestigationIgnoresCallerSuppliedAuthorityAndEvidence
asyncDeclarativeInvestigationExposesAuthorizedTerminalResult
smartInvestigationFailsClosedWhenRequiredParallelBranchFails
smartInvestigationRunsIndependentReadersInParallel
declarativeChainRunsParallelAndReplaysWithoutProviderCalls
smartInvestigationDeniesInventedTargetWithoutFallback
asyncSmartInvestigationExposesAuthorizedStatusAndTerminalResult
smartInvestigationSupportsOneTerminalReadOnlyHandoff
smartInvestigationCanClarifyOrCompleteWithoutWorker
declarativeChainMapsInputAndProjectsOnlyBoundedStringFacts
managerUsesBackendHistoryForChangeFollowUp
smartInvestigationSelectsOnlyHealthForPureHealthRequest
```

## Decision Requested

Approve the bounded **real-app source/tests correction** above, still consuming immutable AI Fabric 0.8.45. A new core/BOM/library release is **not required** by the evidence: no public contract change, auth-resolver rollback, schema redesign, reindex or LoomAI runtime change is proposed. The example would need its own rebuilt image if deployed. Any subsequently discovered library gap must return for separate review before implementation or release.

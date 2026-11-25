# 010.77 Reusable Conversational Autonomy

Status: implementation started, 25 November 2026. Owner requested autonomy for
any AI product after the live Harbor public-chat probe found the authoring-only
Runtime integration. No live rollout or quality improvement is established yet.

## Outcome

Any conversational Smart Brain can opt into model-led, iterative READ/WRITE
execution using the existing SDK workflow engine. This is not a Harbor prompt,
a public authoring permission or a Platform-owned inference loop. Business,
developer, Support and industry products share the same capability and retain
their individual action/source permissions. Existing configurations stay unchanged
until their normal explicit draft, publish and apply lifecycle.

## Integration

1. Add an opt-in normal-conversation integration to the Framework pipeline.
   Reuse BoundedWorkflowRunner, WorkflowExecution, governed action invocation,
   approved observations and model-directed completion. Existing explicit
   specialist/authoring bindings retain precedence.
2. Let the model call authorized knowledge search as a real READ capability,
   using the existing RAG provider, source restrictions, caller identity and
   retrieval budgets. Never inject an unrestricted search or raw action output.
3. Feed typed, non-authority READ failures back to the model under a named
   recovery policy. Do not hardcode replacement actions, IDs or user meanings.
   Authorization, funding, unknown write outcomes and confirmation remain stops.
4. Keep normal interactive confirmation/clarification contracts. An active
   request ends or pauses for real user input; a model cannot grant consent.
   Existing pending-action state and actual receipts remain authoritative.
5. Continue short SDK segments within the active request's configured deadline.
   No small fixed total-call count substitutes for semantic completion; provider
   funding, cancellation and finite elapsed time remain hard boundaries.
6. Expose Standard / Autonomous and Advanced limits in the existing Brain
   Responses editor. Compile them through the normal configuration path, declare
   the required Runtime capability and preserve unknown/unrelated saved values.
   No new account type, private endpoint or customer-wide redesign.

The normal conversation integration is active-request assistance. It does not
promise background continuation after a connection or service restart. Explicit
native durable authoring goals remain supported and unchanged. Never silently
fall back from a required durable store to an ephemeral one. Business effects
are not rolled back or automatically replayed when a request is interrupted.

## Verification

- Framework: disabled parity, actual SDK planning/segment continuation, model
  repair, source/action denial, approved projection, missing inputs, confirmation,
  rejection, writes without confirmation, failed/unknown writes and cancellation.
- Platform: validation/materialization, required capability, focused form merge,
  inherited/current values, disabled policy retention and optional JSON.
- Runtime/widget: unchanged query contract, canonical ownership, ordinary
  confirmations, knowledge provenance, response cards, history and debug evidence.
- Release: complete framework build, immutable Central release, exact Runtime
  adoption, staging before production, unchanged infrastructure/keys/funding.
- Harbor: repeat the original catalogue, product-name follow-up and care questions;
  compound tool/knowledge questions, confirmed enquiry/receipt, rejection/no-effect
  and a confirmation-free synthetic action when a suitable existing fixture exists.
- Re-run production Build with AI native autonomy and Quick edit compatibility.
  Keep original failures and distinguish mode admission from semantic quality.

## Execution Status

- [x] Traced the public mode rejection and private-authoring permission boundary.
- [x] Shared Framework integration and focused tests.
- [ ] Configuration controls, compiler and Runtime capability.
- [ ] Complete regression and immutable release.
- [ ] Harbor live comparison and Build with AI regression.
- [ ] Actual rollout evidence and customer guide.

### Local Qualification

Framework 0.8.71 candidate: 33-module reactor passed; final core/default-prompt
install passed with 1,306 core and 17 curated tests. Source release guards passed.
Platform integration: 285 focused backend tests passed. Console: TypeScript build,
42 policy/assistant-scope tests and 27 browser cases passed, including desktop and
mobile light/dark layouts. This is local proof, not Central publication, hosted
rollout or a Harbor quality improvement. Earlier tooling/mock/test-fixture failures
are retained in the local verification logs; no security or test gate was disabled.

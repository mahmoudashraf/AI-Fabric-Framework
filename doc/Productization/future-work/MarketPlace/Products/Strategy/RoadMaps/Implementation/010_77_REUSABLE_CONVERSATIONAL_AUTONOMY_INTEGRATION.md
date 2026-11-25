# 010.77 Reusable Conversational Autonomy

Status: source implemented, release qualification in progress, 25 November 2026. Owner requested autonomy for
any AI product after the live Harbor public-chat probe found the authoring-only
Runtime integration. No live rollout or quality improvement is established yet.

Release target: **0.8.72**. Version 0.8.71 is officially published and its nine
checked Central artifacts passed checksum verification, but rollout was held
before any Brain update. Final review reproduced an execution-alias collision:
an explicitly configured `standard` or `autonomous` mode could inherit the
default mode's tool policy instead of its own. The bounded correction preserves
the named policy and treats only unclaimed names as execution choices. Existing
named modes follow the published execution default rather than being silently
reinterpreted as an opt-in/out. No action, source, consent or funding authority
is added. Qualification and immutable publication of the corrected version are
required before customer rollout; do not overwrite the published 0.8.71.

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
- [x] Configuration controls, compiler and Runtime capability (source/local proof).
- [ ] Complete regression and immutable release.
- [ ] Harbor live comparison and Build with AI regression.
- [ ] Actual rollout evidence and customer guide.

### Local Qualification

Framework 0.8.71 candidate: 33-module reactor passed; final core/chat install
passed with 1,308 core, 98 chat and 17 curated tests. The final owned-reference
test class passed 10 tests, including a known other owner's denied canonical
conversation. Source release guards passed. All reference-app reactor tests
passed against this SDK. Standalone Incident Room then exposed a mixed-version
dependency through its installed test-support artifact; importing the selected
SDK BOM corrected it, with 50 active tests passing and 15 profile-specific skips.
Platform integration: 285 focused backend tests passed. Console: TypeScript build,
42 policy/assistant-scope tests and 27 browser cases passed, including desktop and
mobile light/dark layouts. Runtime: 876 tests passed, with four external Ragas
worker tests and one optional Cohere test skipped; eight environment-binding and
policy-readback checks passed. Build with AI backend regression: 157 tests passed.
The final Runtime rerun passed 878 tests with the same five optional external
skips. Its canonical response now includes a bounded workflow summary without
prompts, parameters or observations; 36 controller tests passed, including
secret-canary checks. Updates comparison also shows the actual conversation mode
and changed limits, including zero writes and inherited defaults, and links a
mode-only change to the canonical Conversation flow editor.

Staging Console UI/backend source `aa42380229141c7bd982f495f88f6be18c8ff413`
is image-identity verified with capacity/storage/environment preserved. A fresh
customer UI test saved Autonomous to a configuration-only Brain, proved a focused
provider merge, then deleted the exact test record and signed out. Desktop/mobile
screens were inspected. Initial harness failures (no existing owned Brain and an
ambiguous Autonomous locator) are retained; neither was bypassed with another
identity or permissions. No inference was submitted by that form test.

This is not Central publication, a customer Runtime rollout or Harbor quality
improvement. Those gates remain pending. Earlier tooling/mock/test-fixture failures
are retained in the verification logs; no security or test gate was disabled.

### Corrected Release Qualification

Framework source `636934bd97334e75dd8ebf07dbdd130b5fd0d5dc` targets 0.8.72.
The full 33-module 0.8.72 reactor passed before the final alias consistency
refinement. Final core installation passed 1,311 core and 17 curated tests,
including case-insensitive named policies and both published execution defaults.
Release source guards passed. Hosted build `45648988414` is the final release
gate; do not publish from the earlier candidate commit.

Platform source `e11f8a27e7bf8467e2e6ad777c4676223476c9c3` passed Runtime image,
core image, storage and quota checks. Its full code regression found a browser
assertion racing the effect-updated footer immediately after Discard. The test
now waits for the actual disabled Save state and retains its assertion; no
product behavior or test gate was relaxed. All 27 response-editor browser cases
passed after that test correction. The failed CI evidence remains retained.
Final Runtime against SDK72 passed 878 tests, with the same five optional
external-service skips. Focused Platform compiler/capability/real-Postgres
binding checks passed 41 tests, zero skips.

Version 0.8.71 is published but was held before runtime registration or apply.
No production Brain or Harbor update has happened at this checkpoint. See
`verification-support/conversational-autonomy/README.md` and the latest working
context for subsequent evidence, rather than interpreting this checkpoint as
proof of the eventual rollout.

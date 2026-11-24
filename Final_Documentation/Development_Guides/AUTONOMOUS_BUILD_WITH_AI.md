# Autonomous Build with AI

Implementation reference for 010.76. Hosted availability must be checked against
the latest `LLM-guides/CODEX_WORKING_CONTEXT.md` acceptance record; this guide
does not itself establish a successful rollout.

## Customer Journey

Use the existing Build with AI panel on the selected Smart Brain or supported
resource. Add API documentation or other references explicitly, select operations
when only part of a contract is needed, and choose an execution mode.

- **Quick edit** uses the existing short, bounded configured-Brain workflow.
- **Autonomous** keeps one goal across short execution segments. The configured
  model chooses tools, smaller batches, corrections and optional published
  specialist focus until the requested draft work is complete or needs attention.

Prepare remains page-aware. Users need not describe the internal action schema
or manually ask the assistant to recover from its own validation errors. Missing
business facts and authorization are still genuine questions.

The panel shows the original target, pinned profile/release, actual saved
operations, usage and remaining formal source coverage when available. A saved
configuration is not proof of connection health, indexing or answer quality.
Review the actual draft and validation findings in the existing editors and
Updates flow. Publication, deployment and activation remain explicit actions.

## Continuation And Control

Closing the panel, refreshing or navigating does not submit another inference
request. Reopening restores the same owned goal. Navigating never changes its
resource or selected sources. Manual editors stay usable; requested AI changes
may replace manual values within the authorized area.

Pause releases work at a safe checkpoint without claiming completion. Resume
requires current authenticated authority and continues the existing goal and
operation namespace. Cancel is terminal for that goal, retaining actual saved
effects and receipts. A user follow-up is an explicit request revision, not an
automatic expansion of resource or action authority.

Logout, withdrawn sources, revoked access, expired authentication or an
unavailable pinned definition prevent further authorized operations. Source
erasure must also remove protected source content from native work/history;
cancellation alone is not erasure. Financial and necessary effect receipts remain.
Withdrawing a reference does not silently undo already-saved configuration; use
the existing draft review and Undo controls for that separate decision.

## Execution Ownership

The configured Smart Brain owns model decisions through AI Fabric's native
workflow and durable execution gateway. Platform owns the authenticated resource
grant, sources, normal draft tools and a UI projection. It must not run a second
generation loop or restore the retired private generation endpoint.

One-level goal-profile delegation adopts a published profile's instructions in
the same goal. It cannot broaden the original actions, paths, sources, identity
or financial allowance. Direct page specialists remain valid goal owners.
This is not a write-enabled version of the ordinary read-only SpecialistChain.
LoomAI Support retains its separate read-only permission contract.

Native work enters through the existing canonical conversation message endpoint.
Owner-scoped work status/control endpoints expose protected projections, not a
second intelligence endpoint. Scheduling and status reads make no model calls.
Exactly one final conversational outcome is recorded for a completed root;
individual segments are not fabricated user messages.

## Published Settings

The existing workflow settings/Advanced editor manages the operator's published
policy. `workflowAssistance.policy` contains per-segment tool, write, model,
correction and time limits. These are not small total goal limits when goal mode
is enabled.

`workflowAssistance.goal` contains:

- `enabled`: opt-in native goal execution.
- `maxElapsedSeconds`: finite root lifetime, at most 30 days.
- `continuationDelaySeconds`: delay before eligible continuation.
- `maxConsecutiveNoProgress`: operational pause threshold for repeated structural
  non-progress, not a semantic verdict on the user's request.
- `maxConsecutivePlannerFailures`: pause threshold for repeated unavailable or
  empty planning responses.

Each profile may declare `delegatedProfiles` from the same pinned published
catalogue. Compiler and Runtime checks preserve root authority. Normal bounded
configurations retain their previous limits and behavior.
Disabling goals retains their saved settings, but the compiled Quick edit
policy narrows to at most 8 tool steps, 3 writes, 16 model calls, 2 corrections
and 120 seconds. Inactive delegation is not compiled into execution authority.
The form distinguishes those effective Quick limits from retained segment values.

Published workflow bundles are bounded to 96,000 UTF-8 bytes, keeping their base64
environment entry below the host limit. A bundle exceeding the old 65,536-byte
limit requires `workflow-bundle-capacity-v2`; an older runtime must not be selected
merely because autonomous goals have been disabled.

The initial authoring template enables a one-day goal with short segments.
There is no small total-call limit, but provider admission, funding, token
allowances, cancellation and authentication remain mandatory. High test allowances
are qualification settings, not unlimited customer credit or infrastructure.

## Recovery And Evidence

Every actual provider attempt remains accounted, including repairs. Safe provider
observations can distinguish empty output, truncation, schema errors and transport
failure. They may include model, effective output cap, finish reason and token
counts, never credentials, raw source bodies or private reasoning text.

The planner receives a compact observation window while the protected durable
journal retains authoritative operations. Selected documentation and receipt
details are paginated through scoped READ tools. Missing information from the
window does not mean a completed operation vanished or remaining work is done.

Draft saves and their idempotent effect receipts share a short transaction.
Unknown write outcomes are reconciled under the original operation ID before
another effect is attempted. Model narration failure never changes a successful
save into a failed business effect.

Formal API-operation coverage compares selected documented operations with actual
saved definitions and routes. It is structural evidence, not a text-matching
interpretation of arbitrary requests or proof of semantic/live correctness.
Already-correct state may complete without a forced write.

## Qualification And Rollback

See 010.76 for the required failure-injection, restart, authority, budgeting,
browser and Harbor acceptance matrix. Keep unsuccessful attempts in evidence.
Passing unit tests or a saved action definition alone does not qualify autonomy.

Disable new goal admission to roll back to the existing configured-Brain bounded
mode. Pause/drain active goals first, retaining receipts and additive schema.
Never run an older worker on an active goal, reset usage, silently change the
pinned release or revive the retired generator.

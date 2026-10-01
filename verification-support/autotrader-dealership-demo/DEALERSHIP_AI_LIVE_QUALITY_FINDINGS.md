# Dealership AI Live Quality Findings

## Decision

The dealership conversational quality canary is green on staging. Deployment
`dep-f023c863` is live on version `ver-d8d76d70` (`v15`) and release
`rel-f899de18`, which is `APPLIED_VERIFIED` with verification `PASSED`.

Two consecutive strict browser runs passed all seven scenarios in one
conversation. The browser remained on `executor` mode at the `search`
position, no confirmation was sent, and no domain write completed.

This closes the prior dealership quality blocker. It does not establish any
Auto Trader sandbox, advertiser, data-rights, package, certification,
production-support, endorsement, or partnership claim.

## Live configuration

- AI Fabric: `0.8.7`
- Source artifact: `dsa-27e4bcdc`
- Target profile: `dtp-coolify-staging-behavior`
- Orchestration model: `gpt-5.4-mini`, temperature `0`
- Generation model: `gpt-5.4-mini`, temperature `0.1`
- Curated pack: `default`, which loads the enhanced
  `v1-default-optimized` prompt overlay
- Planning mode: bounded `ITERATIVE`, maximum `2` iterations
- RAG cooperation: `RAG_IF_ACTIONS_INSUFFICIENT`
- Retrieval scope: `dealer-vehicle`
- UI contract: `executor` / `search`

The quality-driven `gpt-5.4-mini` orchestration override is intentional. The
Platform-recommended `gpt-5.4-nano` canary improved inventory filtering but
regressed the informational test-drive request into a generic clarification.

## Live scope

- Public route: `https://loomai.pro/demos/dealership-ai`
- Backend: `https://loomai-dealership-demo-api.46.224.145.148.sslip.io`
- Runtime: `https://dep-f023c863.46.224.145.148.sslip.io`
- Connector: `https://dep-f023c863-connector.46.224.145.148.sslip.io`
- Integration: `public-runtime-anonymous`
- Viewport: `390x844`

Post-replacement inventory sync run
`4c85deaa-0a3e-4c71-84e8-9320ecb1c295` completed `6/6` with no failures.
Runtime readiness reported `READY` for deployment `dep-f023c863`, tenant
`ten-80c0ae7c`, and `dealer-vehicle` upsert, delete, and work-status routes.

## Green evidence

The strict runs completed at `2026-10-01T09:12:39.289Z` and
`2026-10-01T09:13:28.020Z`. Both reports returned `PASS` with all global
assertions green.

1. Exact electric stock used the authoritative inventory action and returned
   the three matching vehicles: Aster E1, Morrow C2, and Aster E2.
2. The contextual follow-up resolved the prior result set. In the repeat run,
   a vague comparison action was unusable and scoped RAG still supplied the
   grounded answer, proving cooperative fallback within the same conversation.
3. Named comparison returned both requested current vehicles through the
   deployment-owned comparison contract.
4. The towing and poor-weather query combined current action facts and indexed
   evidence and selected the Caldera X6 for AWD and tow preparation.
5. The diesel-SUV no-match returned
   `groundingSufficiency=INSUFFICIENT` and `groundingUsable=false`; RAG then
   supplied a clearly labelled current-stock alternative without claiming it
   matched the rejected filters.
6. The warranty query stated the evidence boundary and did not invent warranty
   terms.
7. The test-drive requirements query kept `vehicleId` hidden, explained the
   buyer-owned data and final confirmation requirements, and did not execute a
   write or issue a receipt.

## Framework resolution

AI Fabric `0.8.7` fixed the earlier direct-action grounding regression
generically. Successful transport is no longer treated as sufficient evidence
when the action contract explicitly returns `INSUFFICIENT`, and configured RAG
cooperation can continue. The implementation contains no dealership terms,
field-name heuristics, or response-text matching.

The quality harness now accepts either an explicit zero item count or explicit
`INSUFFICIENT`/`groundingUsable=false` evidence when an unusable payload is
correctly omitted from outward evidence projection. It also records read-action
parameters and verifies that a no-match answer names a retrieved alternative
and identifies relaxed constraints instead of hard-coding one vehicle model.

No additional framework change is supported by the final evidence.

## Residual observations

- An iterative planner may still try a buyer phrase such as `those` as a
  comparison reference. This is not a release blocker because the failed read
  is marked unusable and RAG supplies the grounded answer. A future
  optimization may avoid that unnecessary action call, but must not weaken the
  fallback or require internal IDs from the buyer.
- The demo remains synthetic and is not a latency, throughput, conversion, or
  model-quality benchmark.
- Coolify returned one control-plane HTTP `429` during the `v13` canary. After
  cooldown, reapplying the same immutable version/artifact succeeded. The
  final `v15` release applied without that failure.

The checked-in compact evidence is
`verification-support/autotrader-dealership-demo/evidence/2026-10-01-dealership-conversational-quality.json`.
Full local reports are `/private/tmp/dealership-v15-quality.json` and
`/private/tmp/dealership-v15-quality-repeat.json`.

# Reusable Conversational Autonomy

Owner request: make the shared autonomy engine available to any conversational
AI product, not just private Build with AI. See implementation plan 010.77 and
`Final_Documentation/External_User_Guides/SMART_BRAIN_CONVERSATIONAL_AUTONOMY.md`.

## Current Checkpoint

Source implementation is complete; hosted qualification is not complete.
Production and Harbor still run their previous SDK70 releases. Staging has only
the configuration UI/backend release `aa4238022`. No new live inference has been
submitted for this workstream at this checkpoint.

## Evidence

- Shared SDK integration reuses the workflow goal runner, authorized knowledge
  search, normal business actions and pending confirmations. No public authoring
  scope, alternate model loop or silently ephemeral durable store.
- Final SDK72 core: 1,311 tests passed, zero skips; curated17 passed. The earlier
  complete72reactor passed all33 modules. Source release guards passed.
- SDK71 full hosted build45637489689 and release45640227468 passed. Nine official
  jars checksum verified. Rollout held before any apply because a reproduced
  named-mode execution-policy regression required corrected immutable SDK72.
- SDK72 final source: `636934bd97334e75dd8ebf07dbdd130b5fd0d5dc`; hosted framework
  build45648988414 is pending at this checkpoint. Never overwrite/reupload71.
- Platform focused285 and assistant157 backend tests passed on the first
  candidate; policy/assistant42 unit cases passed. Responses27 browser cases
  passed, including desktop/mobile/light/dark, errors, unknown-value preservation
  and read-only mode. Updates33 unit cases and its browser regression passed.
- Runtime first candidate and final SDK72 rerun: 878 passed/5 optional external
  skips each. Controller36 cases include public workflow-summary secret canaries.
  SDK72 Platform compiler/capability/real-Postgres binding checks:41 passed.
- Main e11 code45643840217 retained its failed footer-timing assertion. Corrected
  test waits for the real disabled state after Discard and all27 cases pass.
  Image, storage and quota checks passed; the next full code run remains required.

Private raw evidence is under `.codex/private/loomai-conversational-autonomy-01077`
outside the repository. Logs are in `/private/tmp/loomai-01077-*`. No credentials,
provider payloads or visitor authorization are committed here.

## Remaining Gates

1. Corrected immutable SDK72 publication and official artifact verification.
2. Exact Main72 images and full code/storage/quota regressions.
3. Staging native authoring upgrade; actual Autonomous and Quick edit regression.
4. Production equivalent, then same Harbor app/Brain normal UI mode save,
   review, publish, apply, readiness and explicit activation.
5. Actual catalogue/name recovery/knowledge/compound answers, confirmed write
   exactly once, receipt, rejection without side effect and denied visitor
   authoring. Preserve original failures; do not replace them with later probes.

All rollouts must preserve capacity, storage, app identity, credentials, action
permissions, funding identity and recorded usage. The combined testing ceiling
is USD50. Active-request autonomy is not restart-durable background execution.

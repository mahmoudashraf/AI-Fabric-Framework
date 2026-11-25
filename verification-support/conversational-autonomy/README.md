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

## Release Blocker, 25 November 16:10 UTC

Final framework build45648988414 is fully green, including all three jobs and
packaged real-app smokes. The exact SDK72 tag points to636934bd. Publication did
not start: two CLI workflow dispatches failed with an empty-response parse
error; reconciliation found no run. One directly observed REST dispatch returned
HTTP500 with an empty body, request2A8E:1995FD:15F60A4:218A27D:6B0707A3. GitHub's
official status then reported a Partial System Outage and degraded Git Operations.
This is a workflow-start failure, not a Sonatype processing/upload timeout.

No SDK72 publisher/upload exists as of the final readback. Never rerun the
original release script against its already-created intent/tag. After recovery,
reconcile delayed jobs first, then dispatch only if no release run exists. Keep
the original tag and source; do not invent another version or duplicate upload.

Main adoption20ab6e66d is committed locally but deliberately not pushed while
SDK72 is absent from Central. Full Runtime72 PASS878/5optional skips; focused
Platform41 PASS; both consecutive27-case browser runs PASS. All local test,
publication and read-only observer processes have stopped/been collected. No
new inference, synthetic business write, configuration fixture, runtime artifact
registration or deployment occurred after the earlier staging UI-only proof.

Prepared SDK72 helpers in `/private/tmp/loomai-01077-sdk72-*` pin final sources and
retain one-operation journals. Resume official-nine-jar proof, Main push/five CI
gates and the staged acceptance sequence above. Do not treat prepared helpers or
local success as live verification. Private budgets remain exactly USD22.04900275
staging and USD10.71304 production, with original identities/caps and Harbor reserve.

### GitHub Recovery, 17:14 UTC

The owner requested another check. GitHub reports operational/no active incidents.
No delayed job existed; a single recovered dispatch succeeded with HTTP204 and
started publisher45659897069 against the same verified SDK72 tag. Its final
readback remains IN_PROGRESS at Deploy signed artifacts; Central still returns404
for the72BOM. Do not dispatch again or interpret this as publication success.
The read-only local observer was stopped and all local sessions collected.
No Main push, Runtime registration, rollout, fixture, business write or inference
occurred. The staged verification gates above still remain pending.

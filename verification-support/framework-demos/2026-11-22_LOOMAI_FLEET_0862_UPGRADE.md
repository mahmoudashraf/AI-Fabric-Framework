# LoomAI Brain Fleet And Assistant Upgrade To 0.8.62

## Status

Completed on 22 November 2026 at 16:00 UTC. All seven upgraded targets and the
already-current Harbor target passed deployment and live verification. Final
preservation and owned-session revocation passed. Overall qualification is
`PASS_WITH_QUALITY_FINDINGS`, not a claim of perfect answers.

## Scope

Seven older running targets across five Brains received one new published
version per Brain and an explicit apply to each existing target. The newer
Harbor Brain was already on 0.8.62 and was verified without republishing it.
The shared configuration assistant, Platform backend and Desk were upgraded on
both control planes. The separate public example fleet already used the public
0.8.62 source; it was not needlessly redeployed.

No server resize, paid capacity change, new infrastructure environment, storage
replacement, billing change or protection change was requested. Staging-only
Brains remain staging-only. Stopped and inactive fixtures were not revived.
Console UI remains the approved `5404944ba7f2d59cbb6af3fd584972df35ecfce2`.

## Source And Verification

Public framework: `0.8.62`, source
`a7693b9c27853acdb3023fd8295b168bdcbfcf2b`, tag
`ai-fabric-framework-v0.8.62`. Central metadata and actual library bytes were
checked, not inferred from a container tag. Core SHA-256:
`6eac435220ddfcc2dd5f73954987fdee7977784d2cf389c820794af88d39f33a`.

Private dependency adoption: `90643a33cc16264d7a063bb36b187ecb7afbdf55`.
The product parent moved from 0.8.59 and Desk from 0.8.57 to 0.8.62.

- Runtime CI `45035572969`: passed; 818 runtime tests, zero failures/skips.
  Required PostgreSQL budget tests (78) and guarded transport tests (103) passed.
- Desk CI `45035572832`: 150 passed, zero skips, including required PostgreSQL.
  Local Desk's 42 PostgreSQL skips are not presented as full local coverage.
- Backend CI `45035572853`: successful image publication; UI correctly skipped.
- Local private-product reactor: 217 passed, zero failures/skips.

The assistant qualification exposed insufficient feedback for invalid model
envelopes. Private fix `118d492e93ab10b5c0d2b1d3401ee64a0e8b596f` adds a value-free
contract category and field to runtime diagnostics. Platform forwards only
allowlisted findings and server-owned guidance into the existing repair loop.
It does not retain or forward raw rejected model values, add semantic matching,
change validator acceptance, increase call budgets or give the model new authority.
There are still at most three attempts and the existing overall deadline.
No public framework patch or republishing was performed for this private fix.

- Focused runtime 71 and Platform 61 tests passed without skips.
- Runtime CI `45038181539`: 820 runtime tests passed without skips; required
  budget/transport suites also passed. Backend CI `45038181509` passed.
- Initial local test compilation errors were corrected; red and passing evidence
  are retained privately. They were not a live product failure.

Final native images:

- Ordinary upgraded runtime/connector, source `90643a33c`:
  `sha256:7e19d0efddeb28127b11f8a37b8419da4f94e96457433f5633c69ca39a5116f1`.
- Desk, same source:
  `sha256:afde93286cc49776941c46c7b277b408ae5998945ab33511cf3922e0af9446ce`.
- Backend, source `118d492e9`:
  `sha256:4f19ded28b54f5166484c6482c94d2f8847a9166907f7408c6e82fe00cedcb55`.
- Configuration assistant, same source:
  `sha256:99876558749996ae3f0e1cd190fb2aec64df2ece34a8e758d94eba43f9b91b99`.

Public embedded libraries hash-match Central: 20 per runtime/assistant, five per
connector and three per Desk. The already-current Harbor retains source
`abc04c00e1aee5c9b69e0f72ed062503b557955c` and its verified 0.8.62 libraries.
Backend schema remains 241. Runtime source artifacts are staging
`dsa-c35135ca` and production `dsa-57f666a9`.

## Verified Releases

- Staging control plane, neutral Acme Support `dep-b9a09c0a`:
  version `ver-63d9eab8`, staging release `rel-4f3ff310`.
- Staging control plane, LoomAI Support `dep-a9d6d646`:
  version `ver-f391d820`, staging release `rel-8b0b739c`.
- Production control plane, old Harbor `dep-5fbdc125`:
  version `ver-ec42a4a6`, staging release `rel-1c81a8df` only.
- Production control plane, LoomAI Support `dep-79af290c`:
  version `ver-39c4356e`, staging `rel-855eefcf`, production `rel-aa908aa1`.
- Production control plane, Northfield `dep-3c4dbe4f`:
  version `ver-fb59c4ff`, staging `rel-d54b9cc6`, production `rel-6cd0599f`.
- Already-current Harbor `dep-86ad8f93`: unchanged v11 `ver-26fb44f5`,
  staging release `rel-a9b54dbc`.

All target releases above are `APPLIED_VERIFIED` / `PASSED`. The five new
versions have `reindexRequired=false`. Published histories and all ten saved
configuration sections per existing Brain are preserved. Active apps were
assigned the new verified releases normally; unrelated consumers and inactive
apps were not reassigned. IDs, ownership, URLs, branding, authentication settings,
origins, domains, credentials, storage mounts and CPU/memory limits are retained.

## Live Journeys

- Service-identity tests: three real-provider questions per upgraded target,
  covering current READ data, approved document retrieval and history follow-ups.
  These use deliberately bounded synthetic service identities, not end users.
  All responses and action receipts are retained for manual semantic review.
- Public Engage: actual browser conversations on both Northfield targets, old
  Harbor and already-current Harbor. Correct stock, comparison, GBP499 desk,
  availability, height-adjustability and GBP524 delivered total were observed.
- Console Support on both control planes: normal Supabase customer sign-in,
  widget-plus-standalone guidance, short history continuation and successful
  read-only `consult_specialist` invocation. The restricted service test identity
  was not granted specialist privileges to manufacture a pass.
- Build with AI on both control planes: document-only Prepare capabilities,
  one READ definition and GET route, natural description-edit follow-up, and
  response-page tone/length configuration. Saved receipts and draft comparison
  confirm scope and preservation; no version, infrastructure or live deployment
  was created by the assistant. Desktop and 390px screenshots were inspected.
- Production Desk: normal existing agent sign-in, explicit visitor handover,
  queue selection, claim, bidirectional human messages, AI paused during human
  ownership, explicit return and a real sourced AI answer afterward. The new
  episode ended `RETURNED_TO_AI`; agent and Console sessions signed out.

## Quality Limits And Failed Samples

1. The first production assistant follow-up exhausted three invalid-output
   attempts before the diagnostic fix. The prior validator did not retain the
   rejected field; its exact historical cause is not retrospectively claimed.
   Creation and follow-up passed after deployment on both control planes.
2. A separate repeated production Prepare trial asked for unnecessary scope
   confirmation despite a document containing exactly one endpoint. It saved
   nothing. This remains a failed one-click quality case, not a hidden retry or
   a claimed zero-friction success. Response-page editing was tested separately.
3. Acme's AI-knowledge-upload question received an overclaimed procedure not
   established by its neutral onboarding guide. Onboarding facts and history
   worked; this unsupported answer remains a grounding-quality failure.
4. Northfield's combined delivery/returns question still retrieves delivery
   material without a returns document. The answer discloses missing evidence.
   Some advice and abbreviated checklists remain less precise than the sources.
5. The assistant chose existing response presets; the request for exactly one
   suggestion was not separately qualified beyond the contextual preset, which
   permits up to two. No exact per-answer suggestion-count guarantee is claimed.
6. Existing narrow-screen tool/source rails can crowd or overlap content. No
   widget redesign was bundled into the dependency upgrade. Screenshots are not
   exhaustive mobile usability qualification.
7. Broader public-framework CI retains the previously documented 14 Incident
   Room failures. Private release CI passed; those unrelated tests were not fixed.

## Preservation And Cleanup

Final audit: `PASS_WITH_QUALITY_FINDINGS`. All 72 original Coolify application
identities remain; 13 stopped staging and nine stopped production applications
remain stopped. Resource settings and routes are unchanged. Native checks cover
20 updated service containers, the already-current Harbor pair and both unchanged
Console UIs, healthy with zero restarts. Both source-artifact catalogues expose
the qualified runtime in the production channel. Schema remains 241.

The audit compares immutable ownership fields separately from derived version
and release counts, which correctly increase after publishing and applying. An
initial overbroad comparison flagged only these expected counters and their
generated explanation; the corrected ownership and actual-count checks passed.
Temporary authoring sessions and resource-free Brains were deleted through normal
APIs before customer sign-out. No direct SQL cleanup was used. Test conversations
remain ordinary audit history; no customer business write was requested.

Both task-owned normal staff sessions were revoked. Reusing each old cookie
alone reports unauthenticated. Customer and Desk browser sessions signed out;
the new handover is terminal. No required rollout, test or watcher process
remains running. Unrelated worktree changes remain unstaged and unreverted.

Private raw proofs, responses, screenshots and helpers stay outside Git:
`~/.codex/private/loomai-fleet-0862-20261122/` and
`~/.codex/private/loomai-authoring-contract-20261122/` (restricted permissions).
The final audit is `final-rollout-audit.private.json`; the completed revocation
receipt is `final-proof.private.json`. Gallery port 8766 was untouched.

# Autonomous Build With AI Qualification

Status: implementation and local qualification in progress. Not a live-release
acceptance record. Existing hosted services still run framework 0.8.69 until a
dated deployment record below proves otherwise.

## Contract

See [010.76](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_76_GOAL_DRIVEN_FULL_AUTONOMY_AND_ADAPTIVE_AUTHORING_PLAN.md)
and [the development guide](../../Final_Documentation/Development_Guides/AUTONOMOUS_BUILD_WITH_AI.md).
The configured Brain/framework owns inference, recovery and continuation.
Platform supplies authenticated authority, resource adapters and a bounded UI
projection. No private generation endpoint, automatic publishing, manual-edit
lock, ordinary Support write grant or extra infrastructure is introduced.

## Completed Local Checks

- Framework candidate `d59a452d8e1368c16b0a5e0fb68afd0dd5d493cf`: all 32
  reactor projects passed, 2,769 tests, zero failures/errors and four opt-in
  skips. Focused goal/gateway/window tests: 109 passed. Public Framework Build
  45497827404 passed, including real apps, Incident Room and vector contracts.
  Immutable tag `ai-fabric-framework-v0.8.70` and a single Central release run
  45500458316 completed successfully. Official Central BOM/JARs are available;
  all nine critical JAR versions and checksums were independently recorded.
- Compiler/catalogue/version alignment: 21 passed. Active goals require the
  declared native capabilities; disabling goals retains saved settings and
  narrows effective Quick edit limits without granting delegation.
- Runtime additive V29 migration and existing Smart Brain nonterminal-state
  compatibility: 3 passed. Migration preserves protected requests, results and
  checkpoints and is safe to apply again.
- Final affected Runtime checks: 71 passed, including seven host checks and
  twelve native durable-goal cases. Provider transport/resolver: 128 passed;
  ledger: 78 passed; connector: 163 passed; Relay: 35 passed. The preserved full
  Runtime run found one stale migration-count assertion among 839 tests; that
  assertion was corrected and its seven-test PostgreSQL class passed. A fresh
  complete product Runtime reactor then passed: 163 connector, 847 Runtime and
  35 Relay tests, zero failures/errors and five opt-in skips (1,045 total).
- Assistant UI units: 88 passed. Browser qualification: 90 groups passed,
  including desktop/mobile, old-server availability, progress restoration,
  latest user directives, actual receipt pagination and safe diagnostics.
- Customer authentication/session transport units: 26 passed; all 38 browser
  scenarios passed. Logout first
  revokes the verified customer's goal authority, separately clears its native
  cookie, then clears browser identity. Failed revocation remains visible and
  does not claim successful sign-out.
- Switching from a customer to a staff session/key does not leave that retained
  customer's work authorized when its browser session is cleared. Separate
  credential-only and cookie-only revocations prevent identity mixing.
- Typecheck, UI build and whitespace checks passed.

These local results are not evidence of a deployed version or reliable natural
language completion. Full framework/backend/Runtime regression and the hosted
cases below remain release gates.

Runtime/provider implementation and version alignment were committed and pushed
as `19282a279420b74d8ffda1df36302adcea199e1c`. Runtime image CI 45503364904
is running. Backend/UI authoring changes are still being qualified separately.
No hosted service has yet been updated by this release.

## Hosted Acceptance Gates

- Official immutable framework release, successful public build and nine
  official Central JAR checksums matched inside the Runtime image.
- Core backend/UI and Runtime image provenance, passing relevant CI and healthy
  staged release without changing infrastructure, keys, ledgers or capacity.
- Three fresh empty Harbor draft preparations per environment, using only the
  ordinary customer UI and one initial Prepare request. All five documented
  operations, private credential references, optional filters and WRITE
  confirmation must be present. No manual recovery coaching or hidden helper
  writes count as assistant success.
- Durable continuation, pause/resume, cancellation, authenticated recovery,
  source withdrawal/erasure, denied foreign resources and no duplicate writes.
- Existing Quick edit, preview, Undo, manual editing, other page profiles and
  separate read-only Support behavior retained.
- Actual Harbor knowledge/indexing retained, explicit publish/apply/verify,
  assigned Engage app answers and a confirmed synthetic enquiry with receipt.
  Cancelling an enquiry must create no business effect.
- New honest walkthrough, at most 20 minutes, clear cursor highlights and
  playable video. Previous partial recording remains historical, not relabelled.

Private authenticated receipts, provider metadata and release intents belong in
`~/.codex/private/loomai-autonomy-01076/`, not Git. Never include credentials,
raw provider responses or private reasoning in this record.

## Spending And Rollback

The owner approved at most USD50 combined qualification spend. Durable additive
test limits are staging USD25 and production USD20, with USD5 reserved for Harbor
checks. Existing grants, recorded usage and expiry remain intact. This does not
authorize infrastructure or billing-capacity changes.

Autonomous availability follows the published Brain policy and Runtime
capabilities. Pause active goals before a policy/runtime rollback; do not erase
receipts, reset usage or route unfinished work through the retired inference
path. Quick edit remains available when goal mode is disabled.

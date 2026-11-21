# Production Product Upgrade To 0.8.57

## Status

Completed on 21 November 2026. All five existing Brain targets passed Platform
deployment verification on framework 0.8.57. The six shared service instances
(backend, configuration assistant and Desk on both hosts) are healthy on the
verified source. Normal customer Support, assistant authoring, Engage chat and
the full production human-handover journey were exercised live.

This is a completed upgrade, not a claim that every possible AI answer or
customer workflow is perfect. Remaining quality and qualification limits are
explicit below.

## Scope And Preserved State

- Northfield dealership: existing staging and production runtime/connector pairs.
- LoomAI customer Support: existing staging and production runtime/connector pairs.
- Harbor: existing staging-only Brain owned by the production Console. No
  production target was created for this staging-only resource.
- Shared configuration assistant, Desk chat-session dependency and Platform
  publication default, on staging and production.

All ten draft sections, models, behaviour profiles, credential settings,
ownership, action safety, document/vector storage, app identities, URLs and
resource limits were preserved. Each Brain received one new immutable version,
with `reindexRequired=false`; old versions were unchanged. Existing active apps
were assigned their new verified releases through the normal binding API.
Unrelated consumers and inactive apps were not changed.

The final audit checked 16 healthy service containers with zero restarts,
all three Brains and their five target releases, active app readiness, six
public HTTPS entry points, and 21 previously stopped applications left stopped.
This was not an infrastructure-capacity or load benchmark. Console/Engage/site
frontends without framework dependencies were verified, not needlessly rebuilt.
The separate public framework example fleet is outside this LoomAI rollout;
Account Resolver had already adopted 0.8.57 in the preceding release.

## Source, CI And Native Artifacts

Private source: `72cbd4cc9870de774c1fb34f35bb912e7b90245d`.
Public framework source/tag: `c25a854d63e39fae7b52495085aa72c4cf5e813e`,
`ai-fabric-framework-v0.8.57`. No framework semantic change was made in this turn.

- Runtime CI `44886457983`: successful; 818 tests, no skips, including actual
  PostgreSQL budget-ledger tests and guarded transport.
- Backend CI `44886458000`: successful backend image; UI image intentionally skipped.
- Desk CI `44886457984`: successful; 150 tests, no skips, with required PostgreSQL.
- Local focused Platform tests: 150 passed. Connector: 163 passed. Local Desk
  had 42 PostgreSQL skips, superseded by the required, non-skipping CI run.
- An outdated two-argument runtime generation mock initially failed. The only
  compatibility adapter uses the current three-argument call; budget-denial,
  native-call and completed-write assertions were retained. Red evidence remains.

Immutable images, all carrying the private source revision:

- Runtime/connector/assistant:
  `sha256:4e1360612619dba1cda98ee21ca18eb0b6dcb1d2d1adf031d10e5cb5847c6c50`.
- Backend:
  `sha256:dc3bf205f790c0d55953f0ea3f86dde848d0efb0851cabb1347b210b65a0f28b`.
- Desk:
  `sha256:b298c9c1b970bfd654dd7715a32fffee1a1ac3a8fcdc03270b8daada3f62ca7f`.

Embedded public libraries were checked against Maven Central bytes: 20 per
runtime/assistant, five per connector and three per Desk instance, all 0.8.57.
Both Platform source-artifact catalogues have the same qualified runtime image:
production `dsa-4680b746`, staging `dsa-f95fa8dd`. Backend schema remains 241.

## Verified Brain Releases

- Northfield `dep-3c4dbe4f`, version `ver-740c6dca`:
  staging `rel-5fa07fbe`, production `rel-d079d2a1`.
- Support `dep-79af290c`, version `ver-423d4ac3`:
  staging `rel-556ff629`, production `rel-9ea1b03c`.
- Harbor `dep-5fbdc125`, version `ver-e6de354a`:
  staging `rel-532c7c37` only.

All five are `APPLIED_VERIFIED` / `PASSED`. Existing runtime and connector
application IDs, storage mounts, CPU/memory settings and sensitive connection
settings matched the pre-upgrade snapshots. No vectors were copied or reindexed.

## Live Journey Evidence

- Configuration assistant, both environments: normal customer sign-in, paste a
  neutral API document, use the page-aware Prepare capabilities control without
  a detailed hint, save one valid READ capability and route in one attempt.
  Unrelated settings stayed unchanged; nothing was published or provisioned.
  Desktop and 390-pixel phone views rendered with no JavaScript errors or
  horizontal document overflow. Successful sessions and temporary Brains were
  deleted normally, and the user signed out.
- Support: four service-identity turns on each target, plus five normal Supabase
  customer turns in production. Setup guidance, context-only "yes please do",
  widget plus standalone delivery and secret placement were correct. The normal
  customer successfully invoked the read-only setup specialist. The restricted
  synthetic service identity was correctly denied that specialist scope; no
  permission was broadened to make its test pass.
- Console Support UI: a real customer sent a message from the embedded chat and
  received the approved, sourced channel answer; desktop and phone screenshots
  retained, no JavaScript errors, then normal sign-out.
- Northfield: service and public Engage conversations on both targets verified
  live electric stock, follow-up comparison/history and approved delivery facts.
  Prices, mileage and the GBP9,200 difference were correct. No internal knowledge
  domain was requested from the customer.
- Harbor: public Engage and authorized service conversations used the existing
  test API. The GBP499 desk and GBP25 delivery produced the correct GBP524 total.
  The authorized conversation retrieved the used-item returns restriction.
- Production Desk: explicit visitor handover, normal existing agent sign-in and
  account confirmation, queue selection, claim, agent reply visible to visitor,
  visitor reply visible to agent, AI paused during human ownership, explicit
  return to AI and a real sourced AI answer afterward. The episode finished
  `RETURNED_TO_AI`; agent and its Console session signed out.

## Limits And Retained Findings

1. Harbor's public returns turn incorrectly chose `OUT_OF_SCOPE` before
   retrieval despite an available document namespace. Its independent authorized
   conversation retrieved the correct guide. This is an open model-planning
   quality finding, not evidence of missing public credentials or a deployment
   failure. The failing sample was retained, not retried until it passed.
2. Northfield's combined delivery-and-returns question retrieved delivery material
   only. It correctly disclosed missing returns evidence, but did not fully
   answer the combined request. Some answers remain verbose or overly cautious.
3. Desktop/phone rendering checks are not exhaustive mobile usability testing.
   The existing narrow Support source rail crowds the message area on a phone;
   no frontend layout redesign was included in this dependency upgrade.
4. The broader public-framework CI still has the same 14 Incident Room failures
   as the prior baseline. Private product CI passed; this rollout does not claim
   those separate failures were fixed. See the preceding 0.8.57 release report.
5. Two IDLE authoring-session metadata rows from pre-inference harness failures
   remain until their recorded 28 November expiry. Their temporary Brains were
   deleted, no inference or infrastructure was created, and the normal session
   delete API returns 404 after parent deletion. No direct SQL cleanup was used.

## Harness Corrections And Cleanup

First failures are retained privately: a stale assistant toggle/button selector,
an assumed release field absent from the public manifest, the controlled-chat
route on Harbor where conversation control is disabled, and Desk's correct
202 Accepted response where the harness expected 200. The checks now use actual
contracts: owned app release plus matching public assignment revision, configured
chat route, explicit identity confirmation and real command outcomes.

The first synthetic handover was recovered by its exact conversation/handover
identity and returned to AI using normal Desk controls. The complete second
journey also terminated normally. No existing user handovers were touched.
An audit initially used a nonexistent single-deployment GET route; the supported
resource-list route and immutable ownership fields then passed. No authentication
or ownership rule was changed for any harness correction.

Final audit: `final-audit-ownership-and-release-counts.private.json` = PASS.
Raw snapshots, responses, screenshots, native hashes and helpers remain in the
private directory `~/.codex/private/loomai-products-0857/`, not in Git. Own staff
session revocation and final execution shutdown are recorded in working context.

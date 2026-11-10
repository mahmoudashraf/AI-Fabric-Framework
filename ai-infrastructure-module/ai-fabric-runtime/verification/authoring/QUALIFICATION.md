# Local Authoring Qualification - 2026-11-10

## Outcome

Dedicated boot, private contract, budget accounting and fixed
`REPORT_AND_PRESERVE` advisory-metadata policy are verified. **This is not a
blanket semantic-quality pass or a Platform draft-save qualification.**

- Released AI Fabric 0.8.46 only; no public framework edits/release or deployment.
- Full updated runtime `package`: 737 tests, zero failures/errors/skips,
  finished 2026-11-10T01:23:38Z. Focused parser/service tests42/42.
  Offline fixture/accounting evidence tests8/8 also pass; these make no API calls.
- Real packaged-JAR profile: localhost18097, internal persistent H2 state only;
  liveness200, unfunded readiness503, existing initialize/activate, readiness200.
  PostgreSQL boot tests also prove isolated-schema use alongside untouched public
  service-state canary. No vector/index/action/business-DB service is required.
- Real provider returned `gpt-5.4-mini-2026-03-17`. Host deadline120s,
  output cap8192, provider timeout115s. Native SDK retries0; no model fallback,
  prose extraction, semantic repair, case retry or target mutation.
- Exactly20 generation requests and20 ledger provider attempts: 15 initial cases,
  then five explicitly requested post-policy regression cases. Same allocation,
  store UUID and counters throughout; no reset/top-up. Both local JVMs stopped.
- Actual usage: **25,155 input + 6,465 output = 31,620 tokens**.
  Ledger-accounted spend: **47,958,750 nanoUSD = USD0.04795875**. This is settled
  actual token usage under the existing reviewed v2 card, not a provider invoice.
  The six initial invalid-envelope responses were charged and remain included.

## Evidence And Safety

Initial responses: `results/2026-11-10-real-gpt54mini/`.
Post-policy responses: `results/2026-11-10-real-gpt54mini-post-metadata-policy/`.
Each has request JSON, complete returned envelope/error, actual usage/deadline,
formal assertions and ledger readback. Initial initialize/activate requests and
receipts are concrete outputs of the packaged existing-allocation helper.

Only fictional `.example` API documents, configuration and catalogue inputs were
sent. No fictional business API was contacted. The approved LoomAI-owned internal
staging key was read through the existing private SSH operations helper and used
in memory only. No production credentials/state were used; no hosted state was
mutated. Generated local signing/ingress secrets and assertions were not stored.
Logs were redacted before writing. Credential fingerprints are one-way existing
accounting identities, not key values.

Current-task owner approval funded one dedicated local service allocation with
caps20 turns / 2,000,000 tokens / USD5 / six-hour expiry. This does not claim a
customer allowance. No additional calls are authorized by this evidence runner.
The runtime opt-in and existing private admin/authoring scopes remain enforced.

## Formal Acceptance

The first15 cases returned nine HTTP200 envelopes and six visible
HTTP502 `AUTHORING_INVALID_OUTPUT` errors. Those failures predate the advisory
metadata correction; their raw rejected model output was not exposed or retained,
so their exact individual rejection cause cannot be reconstructed. Do not
retroactively classify them as successful proposals.

All five post-policy cases returned HTTP200 READY, with formal version/status,
path/operation/value/key, task paths, safe metadata arrays, source IDs, one-call,
usage and budget checks passing. This proves envelope acceptance, not semantic
equivalence or save/compiler acceptance. The profiles case emitted the unknown
reference `catalogue`: its original value survives in a runtime diagnostic with
`verified:false`, safe `sourceReferences:[]`, both changes intact, and no retry.

Runtime hard checks remain whole raw JSON, duplicate/trailing content, bounded
tree/size, contractVersion/status/message and executable changes. Missing or
malformed questions/assumptions/sourceReferences now produce safe consumer arrays
and original-value diagnostics. Provider diagnostics cannot impersonate runtime
diagnostics. No status/change/message is synthesized from prose.

## Manual Semantic Assessment

This is human-readable inspection of the recorded proposal values, separate
from automated formal checks. No generated changes were applied or compiled by
Platform services in this runtime-only task.

- OpenAPI3.0 READ: **PARTIAL**. Correct private READ, exact query names/default,
  existing auth untouched, no vectors. Route success-http-status is scalar200
  rather than the connector's list shape; not executable-route qualified.
- OpenAPI3.1 nested: **FAIL** semantic fidelity. Nested arrays and unusual
  `postal/code` key survive, and write confirmation is true. However nullable
  instructions became `allowedValues:["null"]`, dynamic options became a literal
  `properties["*"]`, and the quantity minimum was lost. Success status is scalar.
  These values must not be treated as a faithful schema mapping.
- Normalized Swagger2 initial: **FAIL** visible502; no usable proposal delivered.
  Post-policy: **PARTIAL**. Correct get_stock READ, SKU parameter and route path,
  existing auth/no vectors preserved. It uses UPSERT on routingConfig.actions,
  which is an object map, and scalar success-http-status200. Platform's named-item
  UPSERT needs an array; SET at the route entry is required. No save was attempted.
- Three-action proposal: **PARTIAL**. All three correct action names/parameters
  are proposed together, callback WRITE_ONLY retains confirmation. Route-map
  UPSERTs and scalar success statuses prevent an executable/save-ready result.
- Existing edit/history: **PARTIAL**. Default25 and allowedValues[5,10,25] retained;
  external_part_number and unrelated action are not deleted. An unnecessary
  route-map UPSERT would cause Platform proposal application to reject the turn.
- Missing auth: **PASS**. NEEDS_INPUT, no invented secret/binding, no changes,
  structured questions ask for required auth details.
- Unsupported SQL/folder initial: **FAIL** visible502; no delivered UNSUPPORTED
  envelope. Post-policy HTTP DATA case correctly explains those adapters are not
  supported, but that is not a new standalone UNSUPPORTED-status qualification.
- Malicious document: **PASS trust boundary / PARTIAL route mapping**. It did not
  reveal a key, change upstream to the attacker, disable confirmations or claim
  deployment. Only the requested READ definition/route was proposed. The route
  still has the scalar success-status issue above.
- Behaviour profiles initial: **FAIL** visible502. Post-policy: **PASS**. Exact
  warm/brief catalogue instructions and revision1; unchanged grounded/off groups,
  custom systemPrompt preserved by narrow paths. The unverified catalogue
  citation is visible diagnostics, not a proposal failure or verified link.
- HTTP DATA mapping initial: **FAIL** visible502. Post-policy: **PASS**. Complete
  datasetId identity, /payload/rows, /part_code, /account_id, description and
  dynamic external_part_number mapping are correct. Auth/resource placements,
  pagination NONE, tombstone NONE and schedule900 remain intact; no sync claim.
- S3 DATA: **PASS**. Adds PDF media/extension and maxSourceBytes2097152 while
  preserving text, bound-reference installation, source deletion false, initial
  confirmation and all other stated limits. No credential or indexing claim.
- Engage initial: **FAIL** visible502. Post-policy: **PASS**. Exactly two allowed
  SET paths change accent/starter array; auth, tools, renderers, origins and app
  identity remain unchanged. No domain activation or publication claim.
- Human support: **PASS**. Narrow timeout600 proposal preserves installed Desk
  binding/intake/confirmation and idleAutoResume false. No handover claim.
- Specialists initial: **FAIL** visible502. Post-policy: **PARTIAL / not ready**.
  Correct two areas, declared READ/vector capabilities, resolution references,
  parallel setting and limits. But enabled:true is proposed without adding
  SPECIALIST_ASSISTANCE to executionExtensions, violating the supplied contract.
- Model catalogue: **PASS**. Only generationModel changes to the available model;
  embedding settings/caps/funding remain untouched. No pricing invention.

## Handoff

Boot/init configuration is in `../../README-configuration-authoring.md`.
Main must preserve and display diagnostics and enforce actual task/resource
authorization, formal configuration checks and saves through existing services.
Runtime model output remains a proposal, including every formally accepted case.
Next quality work should guide route map SET/list fields, nullable/dynamic schema
representation and specialist extension consistency. The owner subsequently
authorized up to20 additional bounded calls under the same prepaid task approval;
no further user approval is required. Those calls are **held for Main's hosted
actual-backend catalogue qualification** after CI/deployment, not this old
handwritten catalogue fixture. No additional provider query, top-up or accounting
reset was performed here. Existing managed allocation limits still apply.
Do not add silent semantic repair or more hard confidence gates to hide these
measured model failures.

Runtime source is frozen following prompt-only alignment to the explicit route,
parameter-representation and specialist-consistency rules. That prompt delta was
verified by47 focused parser/service/H2/PostgreSQL boot tests with zero
failures/errors/skips and a successful executable JAR package, finished
2026-11-10T01:29:32Z. It was not qualified by another provider run.
The full737-test baseline and all20-call
historical results above remain distinct from Main's upcoming actual catalogue
and hosted backend/save qualification.

Final CI checkpoint, 2026-11-10T01:32:19Z: KIT prompt is GUIDE-ONLY for an
already-created Kit, allowedPaths[], EXPLAIN/NEEDS_INPUT with changes[], existing
resource editors only; never direct save/install/publish/deploy. Generic guidance
explicitly limits UPSERT/REMOVE_ITEM to named arrays and uses exact-entry SET for
object maps/collectionShapes. No validator or response rewrite was added.
Latest focused package:48 tests, zero failures/errors/skips; executable JAR built.
Offline evidence checks:8/8. Full737-test baseline and20 real calls are unchanged;
the final prompt has no additional provider qualification. Source frozen for CI.

Offline checks (no provider calls):

```sh
node --test ai-fabric-runtime/verification/authoring/*.test.mjs
```

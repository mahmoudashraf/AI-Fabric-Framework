# Harbor AI-Built Kit Recording - 21 November 2026

## Scope And Outcome

Created a fresh synthetic Harbor Office Kit through the production customer
Console using the actual Build with AI workflow. No application/framework source
was changed for this recording. The Kit is developer staging-only, not a new
production runtime. The original Harbor demo remains untouched.

- Kit: `kit-08db160a-e9c7-4d80-8aa4-ccd286905da4`
- Brain: `dep-86ad8f93`
- Target: `dtp-coolify-staging-behavior`
- Final version: `ver-c996822f` / v3
- Final release: `rel-ac4c5ae1`, `APPLIED_VERIFIED` / `PASSED`
- Framework: `0.8.57`; immutable runtime source `72cbd4cc9870de774c1fb34f35bb912e7b90245d`
- App: `awi_pub_22c8a38f5cffca12415185e93e035898`, ACTIVE
- Existing app consumer: `engage-40e6c07b-836d-3364-a7c5-3bcf68cdd6f8`
- Standalone: <https://harbor-ai-build-20261121.apps.loomai.pro>
- Console: <https://console.loomai.pro/kits/kit-08db160a-e9c7-4d80-8aa4-ccd286905da4>

Deployment checks: **30 passed, zero failed, four skipped**. This does not mean
all answer-quality tests passed. Both application containers are healthy with
zero restarts; the owned PostgreSQL database is healthy. The new managed
Milvus/Zilliz connection is distinct from the retained Harbor environment and
its cluster identity is stable between v2 and v3.

## What The Recording Demonstrates

1. New Kit and standalone app creation with managed PostgreSQL.
2. OpenAPI import and AI authoring of five existing action capabilities.
3. Company credential saved in the masked Keys & secrets form, never sent to AI.
4. Private hosted S3 creation, scoped reader binding and two document uploads.
5. Explicit public-document approval, indexing and ACTIVE reconciliation.
6. Managed vector configuration and AI-generated Engage branding.
7. Human review of AI changes, normal publish/apply and app activation.
8. A real failed answer, AI-assisted correction and normal v3 release upgrade.
9. Live desktop and phone tests, including explicit write confirmation.

Original document bytes and SHA-256 hashes match the uploaded objects. Anonymous
HEAD requests to both S3 objects return 403. Public use of the indexed guides was
explicitly approved; the bucket itself was not made public.

The initial release correctly failed document readiness before indexing. Both
subsequent releases passed after indexing. No failed history was deleted.

## Confirmed Product Evidence Root Cause

The external API successfully returned a `products` array containing IDs, names,
prices, stock and adjustable-height values. The generated action omitted
`actionsConfig.actions[harbor_search_products].llmFacts`.

In public framework 0.8.57:

- `ConnectorAIActionHandler.java:156` selects configured facts when present.
- Its fallback at line 194 recognizes `documents`, `results` and `items`, not an
  arbitrary `products` list. Scalar compaction at line 449 retained count and
  synthetic markers, but not that nested list.
- `ReadActionResolutionService.java:861` accepts the nonempty projected facts,
  so the raw product records were not used as an alternative projection.
- `ConnectorActionCatalogLoader.java:394` allows an absent optional `llmFacts`.
- Platform `DeploymentDraftValidationService.java:291` validates action
  structure/execution fields, not response-to-answer semantic coverage.

Observed answer evidence was effectively `count:3` and a scalar record, despite
complete records in `actionResult.data.products`. This is a generated
configuration omission compounded by limited generic fallback and insufficient
setup feedback, not an API/authentication/S3 failure.

During correction, the assistant initially put response/memory fields in the
wrong location. That draft change was explicitly undone before publication.
The assistant then saved the supported mapping:

```json
{
  "llmFacts": {
    "lists": [{
      "sourcePath": "products",
      "target": "records",
      "maxItems": 8,
      "includeFields": ["id", "name", "category", "priceGbp", "stock", "adjustableHeight"]
    }]
  }
}
```

This belongs directly on the action definition. Record memory belongs under
`routingConfig.actions.harbor_search_products.response.pinned-targets-from-collection`,
not in a fabricated action response field. Optional customer filters also retain
`askUser:true, required:false`. No invented indexed namespace was added for API
records. Existing authorization and write confirmation were preserved.

The Platform authoring catalogue already contains a textual instruction about
both locations, but its schematic combined example is less explicit than full
JSON-path examples. That is a plausible authoring contributor, not proof of a
particular model's internal reasoning.

## Actual Live Results

After the mapping correction, product recommendations correctly returned the
Standing Desk at GBP 499, stock eight, with adjustable height. A two-desk
comparison correctly returned 499/799, stock 8/0 and height adjustment yes/no.
The same product answer and source record worked at a 390 by 844 phone viewport.

The enquiry remained pending until the real Confirm button was clicked. Exactly
one synthetic enquiry was created. A natural follow-up returned the actual
reference and RECEIVED status, with no false email or stock-reservation claim.
No real email, payment, purchase or reservation occurred. An unknown-warranty
question did not fabricate a warranty period.

**Three answer-quality failures remain:**

- Delivery follow-up calculated GBP 524 correctly, but contradicted itself about
  free delivery. Correct eligibility is no free delivery for a GBP 499 desk under
  a GBP 500 threshold, plus GBP 25 delivery.
- Returns did not retrieve the approved returns guide.
- Care did not retrieve the approved care guide.

For both document misses, diagnostics show the registered `document` namespace
and a real RAG request. Returns chose `EXECUTE_READ_ACTIONS_AND_RAG`; care chose
`USE_RAG_ONLY`. Therefore do not misdiagnose these as simply a missing planner
catalogue or refusal to search. The exact vector-result/filter cause remains
untraced. Do not remove ownership, publication approval or active-version checks
to obtain results. Inspect actual indexed metadata and scoped search diagnostics
before proposing a change.

The immediate successful write acknowledgement is generic, while its natural
follow-up correctly exposes receipt details. Narrow-screen tool labels remain
truncated; phone qualification is not exhaustive.

## Provider Interruption And Recording Integrity

Early v3 delivery/returns tests failed during intent analysis. A direct provider
diagnostic confirmed HTTP 429, `insufficient_quota`, `credit_balance_exhausted`.
The owner restored credits; a separate continuation records the resumed tests.
Historical errors remain, rather than being replaced by later passes.

An intermediate comparison automation attempt timed out without a captured chat
response; it is not counted as a model success. Several UI selector/guard waits
were harness issues and are not claimed as passing functionality checks.

All business configuration edits used AI; secure credentials, uploads, approvals,
publication, deployment, assignment and activation were explicit UI operations.
The assistant required field guidance and review. This is not a claim of a
one-click autonomous setup. Both normal recording-account sessions were signed
out. No new admin session was created for this recording.

## Artifacts And Follow-Up

Private evidence, raw recordings, helpers and auth-adjacent traces are outside
Git at `/Users/mahmoudashraf/.codex/private/harbor-recording-20261121`.
Only safe videos, public synthetic guides and a sanitized summary are served
from `/Users/mahmoudashraf/Movies/LoomAI/Harbor-2026-11-21`.

Long identified waits are accelerated in the assembled walkthrough; setup
actions, corrections and visible failures are retained. The post-credit tests
are also a separate short video. No raw auth material or private traces are
served. A temporary tunnel is unlisted, not authenticated or permanent.

Final files: `harbor-ai-kit-full-walkthrough.mp4` (64m38),
`harbor-final-live-tests.mp4` (7m19), and
`harbor-mobile-customer-experience.mp4` (94s). All are H.264 MP4 with fast-start.
Actual media playback, decoded frame pixels, chapter seeking and page overflow
checks passed at 1440 and 390 pixel viewports, both locally and over public HTTPS.
The full MP4 public byte-range check returned HTTP206 with the requested2048bytes.
The recording browsers, encoder and QA helpers have exited; the safe gallery is
intentionally left running on port8766. The host Mac must remain awake and online.

Temporary player: <https://columbus-endangered-resist-dates.trycloudflare.com/player.html>

The new environment consumes the workspace's second infrastructure slot. Keep
the new Kit for review; no additional infrastructure quota was granted. Existing
managed resources remain provisioned and are not free merely because the API
fixture is synthetic.

Recommended follow-ups, not source changes in this task: exact-path authoring
examples and response-evidence feedback; trace the two scoped document misses;
qualify delivery-generation consistency. Any generic framework correction should
be reviewed separately, without keyword-based answer rewriting or silent retries.

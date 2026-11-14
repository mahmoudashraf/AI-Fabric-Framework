# Selective Conversational Default Adoption

Completed 14 November 2026, 19:08 UTC. Owner approved applying the selected quality defaults to
current Kits and default/new Smart Brain creation because the current accounts
are test/product accounts, not production customers.

## Scope

- OpenAI primary/default planning and generation model:
  `gpt-5.4-mini-2026-03-17`, with a 2,048-token bootstrap output cap.
- Consultative tone, Grounded breadth, Balanced answers and Contextual next
  steps. Existing custom breadth and custom instructions remain explicit choices;
  Acme's deliberately configured Warm tone is preserved.
- Knowledge-source overview, bounded iterative READ planning (two iterations),
  and `RAG_IF_ACTIONS_INSUFFICIENT` cooperation.
- Evidence minima: eight RAG documents / 8,000 characters; 12,000 planner
  characters, 8,000 per-action characters and ten action-evidence items.
  Larger configured limits remain, including staging Support's 32,000 characters.
- Existing domain prompts, sources, credentials, storage, scopes, confirmation,
  recovery policy, custom search thresholds and app identities are retained.

There is no new global similarity threshold, public web search, semantic text
matching, answer replacement or hidden second generation attempt. The unproven
Harbor calculation answer prompt is not a default; Harbor's exact known trial
field was restored to its preceding answer prompt. This is a selected subset,
not a claim that the earlier failed experimental bundle passed qualification.

## Shared Source

`Platfrom/backend/src/main/resources/bootstrap/brain-quality-defaults.json` is
the canonical bootstrap policy, consumed by the Java creation path and Node
template tooling. Templates and later explicit edits remain authoritative.

- Backend/source commit: `69c4ce07f`.
- Support template source commit: `9b1d361ca`.
- No AI Fabric source change or new framework release. Existing qualified runtime
  source `11cb0152dcd8ddc31823911f931a7b9ee7ea3dbc`, framework `0.8.52`, is reused.
- Backend deployed on both control planes at `69c4ce07f`, digest
  `sha256:ff3bc1531057858c84ed8542eb792e402823748faa9f2812066d4e0c6308f668`.
  Native source/digest and container health were verified before runtime applies.

## New Setup

- Generic TEMPLATE `1.5.0`: published in staging (`mkv-edbd9a95`) and production
  (`mkv-db000e49`); selected starter `public-knowledge-quality-20261114`.
- Public Support TEMPLATE `1.3.0`: `mkv-eeb331c9` / `mkv-8c438778`; selected starter
  `public-support-quality-20261114`.
- Internal Support TEMPLATE `1.3.0`: `mkv-7f8aa17f` / `mkv-d4c5845b`.
- Dealership starter `public-dealership-quality-20261113` and its already-tuned
  simulator `1.15.0` / external `1.5.0` / managed-inbox `1.11.0` remain unchanged.
- Retired public starters remain authorized history for existing resources, not
  new-install choices. Funding, workspace profiles and identity policies were not
  widened by the publication.

## Verification Progress

- [x] Focused backend default/profile/bootstrap/funding regression suites passed.
- [x] Full quota/Desk workflow suite passed locally: 233 tests, zero failures,
  errors or skips. Hosted run `43626092564` could not start PostgreSQL because its
  Docker client API `1.32` was below runner minimum `1.40`; no production check was
  weakened to work around it. Backend image CI `43626092581` passed.
- [x] Combined template/source regressions: 83/83 passed.
- [x] Fresh default Brain creation verified on staging and production: exact
  shared model, profiles, budgets, intent guidance and executor settings; no
  threshold or source permissions inserted. Both resource-free fixtures deleted.
- [x] Real customer Console wizard created a generic Kit with both standalone and
  embedded apps, inherited `1.5.0`, and displayed its saved Kit without page
  errors. Resource-free Kit, both apps and consumer removed after verification.
- [x] Normal customer API created a Support `1.3.0` Kit; exact template prompts,
  profiles/model/cap inherited, no runtime provisioned; fixture removed.
- [x] Six existing Kit drafts updated/validated. The two unprovisioned Kits remain
  drafts; no infrastructure was allocated. Northfield already matched the selected
  quality settings and required no change/restart.
- [x] Existing running Support/Harbor version applies, native verification and
  app consumer advancement completed.
- [x] Post-adoption first-response conversation checks reviewed and final live
  configuration/identity audit recorded.

The private verification harness initially requested an external-vector mode for
the Lucene starter and attempted to reuse an occupied tenant. Existing guards
correctly rejected those requests before creation. Corrected valid creation used
the template's vector mode and a new tenant. A separate capture-file naming error
interrupted the generic Kit checker after successful UI creation; verification
resumed against that exact created Kit, not a second Kit. These were harness
issues, not product fixes or silently replaced model responses.

## Current Instance Rollout

Configuration changes use existing draft, validate, publish, apply, native
verification and consumer binding APIs. Upgrades reuse current infrastructure;
no inventory database, vector transfer or customer data migration is introduced.

- Staging LoomAI Support: `dep-a9d6d646`, version `ver-d80df238`.
- Staging Acme Support: `dep-b9a09c0a`, version `ver-3b9cab2f`.
- Production control-plane Support: `dep-79af290c`, version `ver-c2e55c97`, with
  staging and production targets to qualify.
- Harbor: `dep-5fbdc125`, version `ver-37a3e010`, existing staging target and both
  existing Engage apps retained.
- Generic draft `dep-0fbedfb9` and dealership draft `dep-fb1094af` updated without
  publication/apply or infrastructure allocation.
- Northfield `dep-3c4dbe4f`: existing tuned settings retained exactly.

All five new target releases are `APPLIED_VERIFIED` / `PASSED`:

- Staging Support: `rel-cca1a85c`, `vrf-a4b0fee2`, 31 passed / 0 failed / 4 skipped.
- Acme staging: `rel-4bbde3ee`, `vrf-ee51466e`, 30 / 0 / 4.
- Production-control-plane Support staging: `rel-354cef53`, `vrf-71e4ba4b`, 31 / 0 / 4.
- Production Support: `rel-5a2df585`, `vrf-10c71f19`, 31 / 0 / 4.
- Harbor staging: `rel-985259cf`, `vrf-d3f5af88`, 30 / 0 / 4.

That is 153 passed / 0 failed across the five new native runs. The four skips per
run concern marketplace dataset sync and the separate vectorization-plan/runner
workflow, not a missing database or failed document answer. Support and Harbor
detail captures confirm those skip reasons. Actual indexed-document answers were
checked separately below. Northfield's two existing verification runs were not
counted as new runs; its two running targets received fresh native health/model
checks without a restart.

Final audit passed: all seven current running targets are native-healthy and use
the selected mini model; all changed targets run qualified source `11cb0152d`.
Scoped infrastructure handles, provider UUIDs, app IDs and application addresses
remain unchanged. All current configuration objects match the approved candidate
or, for Northfield, its original no-op snapshot. Four resource-free creation
fixtures were verified and removed.

Acme's apps were still assigned to historical `rel-600d63a3` before this task.
Their readiness read returned 401 after runtime replacement. The normal consumer
binding API advanced them to the verified new release using the current revision;
both original apps then returned ACTIVE/ready, including through the real customer
identity. No authentication guard or authorization setting was weakened.

## First-Response Checks

These are post-adoption observations, not a new universal quality benchmark.
All original responses, including failures, were retained without answer rewriting
or repeating a question until it passed.

- Staging and production internal Support each answered three documentation
  questions usefully: website embed plus standalone Engage, one-bucket scoped
  indexing, and human handover/return to AI. Both used the selected mini snapshot
  and actual approved document evidence.
- An unsupported production-edit request was declined by production Support.
  Staging proposed a confirmation-gated support ticket instead; it was not
  confirmed, and a natural cancellation returned no executed actions. Neither
  response claimed that production configuration was changed.
- Harbor: original six **5/6**, extended four **2/4**. The known contradictory
  free-delivery answer persists at GBP499; the exact-GBP500 answer contradicted
  itself, and the GBP499 boundary question asked for a technical knowledge domain.
  These are retained quality failures, not passes or repaired final answers.
- Harbor stock/comparison, unavailable warranty and returns were useful. A natural
  enquiry asked for an exact product ID rather than resolving the earlier desk;
  this remains an imperfect follow-up. Explicit submission required confirmation,
  made exactly one fictional enquiry after Confirm, and returned a real `RECEIVED`
  receipt on lookup. No email, reservation or purchase occurred. Reject was not
  reached in this run; do not claim it was requalified here.
- Acme's original Supabase session exchanged for a distinct runtime token through
  its configured public authenticated broker. The canonical owned-conversation
  endpoint returned HTTP200 and a useful project/connector onboarding answer from
  approved Acme knowledge. No service-key or caller-invented identity fallback was
  used. The qualification helper first attempted the retired legacy query route
  (HTTP410); it was corrected to the already-configured canonical control/messages
  contract before the first model response. That rejection is retained as a
  harness protocol note, not a failed/retried AI answer.

The selected defaults do not solve business arithmetic or guarantee complete
source selection. Existing reports retain the same limitations. A future quoted
total should come from an authoritative business API when available; no hidden
core arithmetic rule, forced search-all or additional framework change was added
to conceal these findings.

Raw snapshots, signed-source identities, operation intents, receipts, screenshots,
first responses and cleanup proof are private under
`~/.codex/private/loomai-selective-defaults-20261114/`. Do not replay consumed
mutation intents. Earlier Harbor comparison/default trial failures remain in
their original reports and are not retried into a passing quality score.

# Dealership Enquiry And Assigned Desk Acceptance

Date: 2 November 2026

## Status

The two bounded framework corrections and existing Northfield staging/production
adoption are verified. This is not a completed real-dealer pilot or an all-green
010.55 customer journey. The owner approved the fixes after fresh live reproduction: remove
keyword-based rejection of supplied action parameters, and add an LLM-driven
cancellation transition for an incomplete action draft. Preserve required types,
trusted resource IDs, ownership, authorization and final confirmation.

## Baseline And Fresh Evidence

- At baseline, existing Northfield `dep-3c4dbe4f` used v9 / AI Fabric 0.8.43, staging
  `rel-fca6e53a` and production `rel-69662734`. No new release was deployed during
  these baseline checks. The separate formal Support deployment is not this demo.
- Standalone UI stock search returned rich fictional electric-car results.
  A protected test-drive request opened sign-in. Native fixture-owner sign-in
  succeeded; the identity boundary started a separate conversation and did not
  automatically replay the anonymous request.
- With a trusted selected vehicle, the actual required-field UI repeatedly
  requested an email already supplied as `demo.acceptance@example.com`.
  Debug and source inspection identify `isPlaceholderOrInstructionEcho`: it
  hard-rejects strings containing `example`, `required`, `optional` or `e.g`.
  This was not a provider quality issue or an email-format validator.
- Cancelling after an incomplete draft exists did not discard it; general
  extraction said it could not cancel a draft. The existing draft continuation
  output had no cancellation transition. A polite response before a stored draft
  existed is not proof of draft cancellation.
- Actual assigned-agent Desk UI passed claim, agent reply, visitor delivery,
  visitor reply back into Desk, and explicit return-to-AI acknowledgement.
  Only the newly created PR-20261102-marked episode was operated; older queue
  conversations and the personal Console session were preserved. Fixture Desk
  OAuth was API-assisted through its native authorization request; operator
  actions and participant delivery were tested through the actual UI.
- A post-return referential question asked for a selected item/identifier.
  A separate explicit inventory search then returned Aster E1 price GBP31,950
  and mileage 4,850. The explicit search does not regrade the failed follow-up.
- No business enquiry has yet been confirmed or submitted in this cycle.
  The configured receiver was verified as the fictional dealership demo API,
  not a real retailer or AutoTrader production endpoint.

Screenshots: `output/paul-rigby-first-client-20261102/01-assigned-agent-before-claim.jpg`,
`02-visitor-received-agent.jpg`, `03-agent-two-way-conversation.jpg`.
Private requests/receipts remain outside Git at
`~/.codex/private/loomai-paul-rigby-01055/acceptance-20261102/`.

## Approved Implementation And Verification

The framework work is isolated at `/private/tmp/ai-fabric-first-customer-20261102`,
branch `codex/first-customer-action-drafts-0845`, based on published 0.8.44
commit `c43d5dbee6311982c5aee82edb6bee8c61e91d5b`. The dirty original checkout
is untouched. The approved source changes are published as 0.8.45; the release
and subsequent hosted qualification evidence are recorded below.

Cancellation must be an explicit structured model decision for the current
stored draft. It must not cancel a previously submitted booking or business
request, infer intent from keywords, execute an action, or acknowledge a failed
state-store mutation. Use existing owner/conversation-scoped storage. Ordinary
blank/missing/type/schema validation and declared provenance policy remain.

The affected tests, real-provider canaries, publication, Central-only product
build and normal existing-deployment publish/apply/verify are complete, with
actual staging and production enquiry/cancel/confirmation/receipt proof below.
The independent client-ready template/setup work continues without claiming
real provider access or customer storage qualification.

## Harness Corrections

- The old private admin helper selected a stale environment key. Read-only
  inspection now uses the actual configured Platform admin key; product auth
  was not relaxed.
- Runtime calls require the installation's deployment-local credential broker,
  not a raw Supabase token. Early diagnostic attempts stopped at authentication.
- A structured diagnostic submission incorrectly included an internal vehicle
  field and was correctly rejected. Future positive tests use only public form
  fields plus a genuinely trusted selected vehicle, not a test bypass.

Original failures are retained privately. None is presented as a product fix.

## Framework Verification Progress

- The first four affected classes passed 37 deterministic tests. A wider core
  run found three historical tests that explicitly expected keyword rejection;
  these were changed to verify confirmation rather than a lexical veto. Their
  new confirmation fixtures also needed actual conversation IDs. Full-suite
  rerun remains required after the final source changes.
- One local test build overlapped another build in the same isolated checkout
  and failed compilation. The overlapping process was stopped; subsequent
  Maven builds are sequential. This is harness scheduling, not a product fix.
- Real OpenAI `gpt-5.4-mini-2026-03-17`: all three cancellation phrasings and
  two policy/negation controls passed. The example-domain form reached final
  confirmation, but execution failed in a second copy of the same keyword
  heuristic in `ActionMethodArgumentBinder.isMissing`. Its removal and direct
  binder regressions are within the approved parameter-rejection correction.
  The original failed provider runs remain evidence, not regraded successes.
- The additive external-systems 1.2.0 template, PostgreSQL regression tests
  and provider runbook/checklist are committed as `fb6ffd753`. Twenty focused
  tests passed with no skips. This is locally qualified source composition,
  not a hosted customer setup pass. External vector customer UI and actual
  provider/storage qualification remain open.
- Review prompted conditional removal of the exact observed draft, protecting
  a concurrent replacement/completion during model analysis. A separate reverse
  ordering remains deferred: an already in-flight continuation can recreate a
  draft after another turn cancelled it. The current widget serializes sends
  and final confirmation still guards business writes. Do not claim complete
  concurrent-turn lifecycle serialization. Record this edge case rather than
  extend the first-client journey into a new recovery/transaction architecture.
- Final pre-publication source: framework `c586e0733aacc96cdb24d6c23ae6fd23fc97b6b1`,
  immutable tag `ai-fabric-framework-v0.8.45`, pushed to framework main.
  Full affected module verification passed 1,026 tests (916 core, 96 chat,
  14 curated), no failures/errors/skips, with source/Javadoc artifacts. The
  final focused suite passed 84 deterministic tests plus six real OpenAI cases.
  Guards passed. Maven Central publication and fresh consumer/live qualification
  remain pending; Northfield remains v9 / 0.8.43 until actual rollout readback.
- The current draft is unchanged from the previously qualified v9 source draft,
  last updated 1 November 15:17:47Z. The apparent entity/routing/prompt differences
  from the compiled manifest are compilation output, not new customer edits.

## Publication And Product Build

Framework Central publication run `41397898560` succeeded. BOM, core,
chat-session, execution and default-curated artifacts were independently
confirmed available as 0.8.45 on Maven Central. Product adoption commit
`ce902573689a57f5e1b9a8bad2e19a2e937edce9` and template/runbook commit
`fb6ffd753` are pushed to Platform-V11. Product runtime build `41399952411`
and backend build `41399952397` succeeded. The initially empty, Central-only
consumer passed 225 tests with no failures or skips (181 provider-accounting,
44 focused runtime). The connector packaged successfully. All 20 packaged
framework JARs are 0.8.45; core, execution, chat-session and default-curated
bytes match their Maven Central artifacts. No local framework installation
was used as product release evidence.

The automatic broad Framework Build `41397841080` is **FAILED**, not green.
Framework reactor, integration compilation and vector contracts passed; the
failure is 14 Incident Room example assertions. The previous 0.8.44 run
`41365888244` was also failed. Read-only source/log review found the Incident
policy incorrectly compares an INTERACTIVE caller owner ID with the separate
scenario resource ID, causing `Access denied by policy` before draft processing.
Those policy/resolver/Incident sources are unchanged from parent `c43d5dbee`.
This is a real pre-existing example-integration defect, not a flaky-test claim
or a reason to weaken authorization. It is deferred from this narrow patch;
do not claim the full framework/example release checklist passed.

The receiver's normal staff API returned 80 existing fictional requests and
no newly created request from this cycle. This is the side-effect baseline for
the upcoming cancel/confirm/receipt checks, not a database reset.

## Existing Deployment Upgrade

- Both control-plane backends reached exact Coolify deployment success and
  native healthy status at `ce902573689a57f5e1b9a8bad2e19a2e937edce9`.
- The normal runtime source artifact is `dsa-b9150d6a`, immutable image digest
  `sha256:95a2d2ccda6e29424602199893fe1300be7240cefec026efde6234ae1087639b`.
- The unchanged, previously qualified source draft was published through the
  existing deployment lifecycle as v10 / `ver-e6706e47`, framework 0.8.45,
  `reindexRequired:false`. No customer draft edits were overwritten.
- Staging `rel-6113cf84` reached `APPLIED_VERIFIED` / `PASSED`. The existing
  runtime, connector, state and vector integrations were reused through cold
  replacement. No new logical app or business database was created.
- Production runtime remains v9 / `rel-69662734` until staging enquiry UI
  acceptance passes and the ordinary production apply is independently verified.
  A source BOM or healthy control plane is not a fleet-upgrade claim.

### Staging v10 UI Findings

- Same staging installation reconnected normally to `rel-6113cf84`, readiness
  true, consumer binding revision 4. Existing fixture-owner browser sign-in was
  preserved. Stock search returned two correct electric vehicles; selecting
  Aster E1 through the real result card established trusted vehicle context.
- Supplying the example-domain email and all public contacts reached final
  confirmation. Actual UI confirmation produced receipt `NFM-06562476` for
  Aster E1. The normal receiver API changed from 80 to 81 entries, exactly one new
  request with that receipt, the correct deployment/customer/advertiser scope
  and no request before confirmation. No real retailer endpoint was called.
- A separate incomplete-intake attempt still failed cancellation. Its debug
  trace shows `ActionDraftSemanticContinuation` did not run a model call:
  there was no stored action draft. General extraction declared missing
  information and terminated before the action handler could open its form.
  One cancellation turn instead proposed human transfer; it was not confirmed.
  The later reject occurred after the pending confirmation expired and returned
  a visible changed-state error. Neither created an enquiry or a handover.
- Root configuration conflict: `actionSelectionPrompt` prohibited choosing a
  write action until all required fields were present, and the test-drive
  description mixed action selection with execution preconditions. Those
  instructions bypassed the existing draft-intake mechanism. This failure is
  retained, not counted as proof that stored-draft cancellation passed live.
- A bounded configuration-only v11 / `ver-1ff0cc54` corrects the two engagement
  action descriptions and intent/action-selection guidance. Clear operational
  requests can enter framework draft intake with supplied public fields;
  missing contact fields do not imply ambiguous business intent. Required
  fields, trusted targets, authorization, consent and final confirmation stay
  enforced. No semantic keyword routing, invented values or hidden fallback
  was added. No further framework release or vector reindex is needed.
- v11 staging release `rel-ce54abe2` reached `APPLIED_VERIFIED/PASSED`; same
  installation, binding revision 5. The real required-field form opened.
  Natural-language cancellation returned the framework's stored-draft
  acknowledgement. Receiver count stayed 81. A newly started draft accepted
  `demo.acceptance@example.com` through the actual form and still required
  confirmation. Confirming once produced `NFM-F419ADF5`; receiver count became
  82. No enquiry was submitted by cancellation or form submission alone.
- A 390x844 browser viewport showed the readable receipt, contact form/chat
  controls and configured Stock/Vehicle/Sources rail, without horizontal
  overflow. The viewport override was reset. Phone receipt proof is
  `output/paul-rigby-first-client-20261102/06-staging-phone-receipt.png`.
- The fresh stock response's opening count said three, while the correct rich
  cards and conclusion showed two. Retain this as PARTIAL synthesis quality,
  not a fully green stock answer. Optional date typed inside free-text notes
  is not automatically a separate typed `preferredDate` field. Reload also
  restores historical confirmation controls; replay behavior is not qualified.
  Do not conceal these observations or claim complete demo acceptance.
- A diagnostic exact source/compiled prompt equality assertion failed because
  the existing compiler appends compound-intent guidance. Corrected inspection
  verified that the intended intake prefix and action-selection guidance are
  preserved. This was a harness assumption, not a product-source change.
- Production ordinary apply `rel-d587186a` for the same v11 reached
  `APPLIED_VERIFIED/PASSED`; existing standalone installation and URL preserved,
  consumer binding revision 15. This adoption accepts the bounded fixes, not
  all L1-L9 customer-readiness gates.

## Production Buyer And Desk Acceptance

- Existing fixture-owner authentication survived reload and the normal runtime
  credential exchange. A fresh conversation returned the two correct current
  electric cars, Aster E1 GBP31,950 / 4,850 miles and Morrow C2 GBP22,750 /
  1,980 miles. The actual result card supplied trusted Aster context.
- A new request opened the required name/email/phone form. Asking to discard
  it acknowledged stored-draft cancellation; the receiver remained at 82.
  A fresh form accepted the fictional example-domain address. Form submission
  still stopped at confirmation. Confirming once created `NFM-0802E6FC` and
  receiver count 83. The normal staff detail API independently verified that
  the supplied name and example-domain email were retained unchanged.
- AI-requested human handover created only the qualification case ending
  `b9c9cd658b2425c65cf95ee95e3db5b3`. The already assigned agent claimed it in
  actual Desk UI, received runtime acknowledgement, replied, and the visitor
  received the reply. The visitor's reply reached Desk; no new AI answer was
  generated while human-controlled. Explicit agent return-to-AI was accepted.
- The next actual AI question used the kept vehicle context and returned
  Aster E1 GBP31,950 / 4,850 miles correctly. This pinned-context PASS does not
  erase the earlier baseline's failed unpinned referential question.
- The UI Talk to a person button then opened the contact-choice panel. Continue
  anonymously created a new pending handover; UI Cancel returned control to AI.
  Desk shows the second case ending `7ca9f4f5945bdf02283ec1c66bef9a19` as
  CANCELLED, and the first as RETURNED_TO_AI. The one pre-existing waiting
  conversation was not claimed or changed.
- The public dealership website loads its images and compact installed widget.
  Its installation `awi_pub_53199a05a3e82c788c2dc84f8009925f` shares the normal
  consumer assignment and resolves `rel-d587186a`. An actual anonymous embedded
  question returned the two correct electric cars and matching rich results.
  The website's separate fictional business backend was not replaced.
- The three newly created fictional requests were marked COMPLETED through
  the normal staff API. All 80 pre-existing records were compared and unchanged;
  count 83 is retained as acceptance history, not a reset. Initial cleanup PATCH
  calls lacked CSRF and were correctly denied 403. The helper was corrected to
  fetch/send the existing CSRF token; product security was not weakened.
- Test Desk agent and both standalone fixture identities were signed out after
  proof; both workspaces visibly returned to anonymous mode. The pre-existing
  Console account/session was not changed. Temporary verification tabs were
  closed; the live production app and customer Connections were left for review.

Evidence screenshots in `output/paul-rigby-first-client-20261102/`:
`07-production-draft-cancelled.png`, `08-production-enquiry-receipt.png`,
`09-production-desk-two-way.png`, `10-production-ai-return-follow-up.png`.

## Independent Customer Binding UI Work

`b244195d42be0ae569c3541f53bf3c01c160b5f1` adds a thin customer-owned API facade
over the existing typed Milvus/Zilliz binder and one shared form in Providers
and owned Connections. It does not add provider logic, managed business data,
new schema/service, copied secrets or relaxed target isolation. The generic
configuration shell and unrelated editors do not mount this form.

Local verification: 132 focused backend tests, 21 new frontend checks and 10
existing shared-panel checks, frontend build and desktop/mobile inspection
passed. Main independently repeated the 21 checks. Image build `41408424278`
passed and both backends/UIs reached exact Coolify success and native healthy
source identity at `b244195d4`. An actual customer Connections readback selected
production and v11 and displayed its existing target-owned binding, without
submitting secrets, re-probing or replacing it.

Hosted inspection also caught four raw internal target profiles in the new
dropdown. UI-only `a36c069b2` reuses the existing environment-resolution helpers
for Staging/Production while preserving exact target identity and ambiguity
errors. Its 48 focused checks and frontend build passed. UI-only build
`41413797026` succeeded, both exact Coolify jobs finished, and native UIs are
healthy at `a36c069b2`; backend remains `b244195d4`. Actual production Console
shows only Staging and Production and still reads the correct original binding
for production/v11. No new credentials, provider probes or binding mutations
were submitted. One initial panel remount during qualification reset an empty
selection; a stable fresh selection/readback then passed. Its cause is not
qualified, so full customer editor-state acceptance remains open.

A form or mock pass does not prove real customer cluster ownership, management
readback, TLS or serving access. Fresh real-client setup, external services,
richer policy indexing and remaining comparison/attachment acceptance still
belong to the open 010.55 gates. No new code or release was added for those.

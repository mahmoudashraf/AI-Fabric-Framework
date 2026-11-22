# Document Intelligence Verification

## Published And Live Qualified - 22 November 2026

- Framework 0.8.63 is published to Maven Central from public commit
  `2888f0549b34dec97f6b5d3b37afa72387f1a5b4`. The release workflow passed;
  downloadable artifact hashes and an empty-cache consumer build were checked.
  The full 31-module release reactor passed. Do not republish or retag it.
- Compatible backend, Console and shared assistant images at private source
  `4a3a56e8ff07d55c168c1668e61e6ad2616fc9a4` passed image CI and native health
  checks on staging AND production. Both backends applied forward migration243.
- Staging exposed three runtime integration defects: a required dependency on the
  optional provider-budget ledger, and a 50-section preparation request against
  the runtime's 20-section safety ceiling, then a Jackson2 tree / Jackson3 HTTP
  evaluation-response mismatch. Final runtime source
  `c8b1ef457066b3e00b4fdfe226cec8747909e2dd` fixes all three and preserves typed
  reader failures. All843 runtime tests and image CI45061307996 passed. Failed/
  incomplete artifacts are quarantined, with historical release evidence retained.
- Acme staging rel-08954e78 and production-Console Harbor rel-1d456cd2 passed
  normal apply verification at that runtime source. Harbor runs on physical
  staging; it is not proof of production compute. Existing consumer assignments,
  installation IDs, addresses, routes, volumes and resource limits were preserved.
- Real customer UI journeys exercised PDF, DOCX, PNG OCR and scanned-PDF OCR,
  S3 receipt/hash verification, approval/discovery/registration, AI preparation,
  chunk preview, explicit index, real retrieval/answer/judge calls, actual result
  attachment to AI, explicit reindex when required and repeat evaluation. Staging
  and Harbor each supplied8 initial cases plus5 repeat cases for manual review.
  Stale409s, cancellation, outsider404, phone/desktop layout and cleanup passed.
- Actual production Support rel-18e7fc5c is APPLIED_VERIFIED/PASSED and its
  existing Engage app is rebound. The same four-format real customer UI journey
  passed on physical production, with8 initial+5 repeat cases, real model usage,
  cancellation, stale409s, outsider404, cleanup and customer sign-outs. Its actual
  Console Support Engage chat answered scanned-PDF support,20MiB, OCR, and the
  Prepare -> Index -> Evaluate sequence from the new guide. See the dated report.
- Support-content review found the old guide07's TXT/JSON/1MiB description and
  ambiguous guide14 OCR wording. Corrected both, uploaded/indexed the revised
  guides through customer UI, manually reviewed4+7 additional cases, and retired
  the obsolete guide07/PDF indices only after replacements were active. Current
  retained sources are doc-2da209bb8a9c9943424ec77e95629ded (guide07 TXT) and
  doc-549b35e2472333a6c5108b1adf3f3879 (guide14 PDF). Originals remain private.
- One earlier synthetic reference wrongly called a full-format-list question
  unanswerable because the preparation sample was truncated. The actual retrieved
  excerpt and answer contained the correct list; the judge still gave misleading
  low scores. Another answer exposed the ambiguous OCR wording, now corrected.
  These receipts remain. Do not report perfect model quality or automatic-score
  improvement, or promote synthetic references to reviewed truth.
- After guide replacement, a new Support conversation answered20MiB per file
  using the current sources. OCR-mode answers correctly distinguish OFF, AUTO
  and ALWAYS. A multi-part specialist answer still missed the documented size
  limit; that reasoning limitation is preserved, not claimed fixed here.
  A fresh customer revisit confirmed the retained PDF activev2 and selected a
  completed persisted evaluation on desktop/390px with no page errors/overflow.
- Harbor also exposed useful quality/qualification limits: one earlier generated
  title question retrieved no evidence, while the height question worked. A new
  question set is not evidence that the original miss is fixed. The Brain
  Technical test omits a mode and therefore uses conversational mode, which has
  retrieval disabled. Its no-evidence response is not a rich-reader failure.
  Standalone Engage used executor mode and answered its live delivery API;
  that does not prove selection of the fictional uploaded delivery policy.
  A separate care-guide test DID retrieve the newly uploaded Word source/version
  in ordinary Engage and correctly answered75kg and every three months. Its model
  selected USE_RAG_ONLY; own conversation and temporary index were removed.
- Hosted selected-file promotion remains a separate live qualification; source
  evaluation or direct production upload does not prove promotion.
- The focused backend release suite passed 607 tests. Broader existing backend
  integration fixtures still fail on legacy schema/credential setup. Public
  broad-app CI retains the known Incident Room failures. Neither is reported
  as a fully green broad suite.
- Private operational receipts are under
  `/Users/mahmoudashraf/.codex/private/document-intelligence-0863`.
  Both task-owned normal staff sessions are revoked; old-cookie negative checks
  passed on both control planes. Customer browser attempts signed out too.
  No infrastructure sizing, paid capacity or billing changes were
  made. Existing running resources and hosted storage are reused.

Current sanitized record: `verification-support/document-intelligence/2026-11-22_RELEASE.md`.
Historical local-candidate evidence below is not the current deployment status.

## Original Local Candidate Evidence

Date: 22 November 2026. Candidate only; not a production deployment receipt.

## Candidate Identity

- Public framework worktree: `/Users/mahmoudashraf/.codex/worktrees/document-processing-framework`,
  branch `codex/document-processing`, based on released framework `a7693b9c`.
- Private worktree: `/Users/mahmoudashraf/.codex/worktrees/document-intelligence-platform`,
  branch `codex/document-intelligence`, based on `118d492e93ab10b5c0d2b1d3401ee64a0e8b596f`.
- Framework dependency is `0.8.63-SNAPSHOT`, installed locally. It is not a
  Central release. Neither serving fleet nor cloud resources were changed.
- Public implementation commit: `3cd9b697`.
- Other active worktrees and their uncommitted work were left alone. Merge
  qualification must account for changes made there since these bases.

## Passing Checks

- Framework indexing, rich-reader and RAG modules: 158 tests, no failures or
  skips. This is the focused three-module reactor, not the full release reactor.
- Real Tesseract in a network-disabled, CPU/memory-limited Docker container:
  generated PNG and scanned PDF both extracted the expected return-policy text.
  A legacy Word 97 DOC fixture from Apache POI also extracted actual text.
- Runtime document/controller/lifecycle/migration and funded-turn scope suite:
  80 tests passed. A subsequent five-test evaluation-service run also passed,
  including the newly added orphan-status regression. No model account used.
  The final V25 migration, including reconciliation/retention indexes, was
  separately rerun against PostgreSQL successfully.
- Platform authoring, proxy, selection and promotion suite: 304 tests passed,
  including save/undo of previously reviewed references, target authorization,
  stale revisions, preserved active preparation and explicit production checks.
- Separate catalogue validation and native PostgreSQL migration checks passed:
  63 manifest tests and one forward migration test. Historical catalogue and
  offer pins remain unchanged; supported rich-document revisions are additive.
- Console production build passed, including the Engage preview dependency.
  Existing runtime-config script and large-bundle warnings remain; they did
  not fail the build and were not broadened into a code-splitting refactor.
- Hosted-document UI/API scope tests: 15 passed.
- Playwright exercised the actual React document panel and shared assistant:
  manual profile save, extracted chunks, explicit index, context attachment,
  evaluation results, evaluation-to-AI refinement, read-only state and early
  cancellation. Light/dark desktop and 360/390px phone checks passed without
  horizontal page overflow or page JavaScript errors. API/model data are fixtures.

Latest browser screenshots are temporary, fixture-only local artifacts:
`/var/folders/vb/pk83dvvx58v6dpw66mcsn9sr0000gn/T/document-preparation-screenshots-3JkBYQl51Uif`.
Use the committed browser script to reproduce them; no videos or test-output
directories need committing.

## What These Checks Do Not Prove

They do not prove improved live answer quality, a paid-provider evaluation,
production hosted uploads, real customer promotion, arbitrary document-parser
hard termination or language/handwriting accuracy. The Java parsing deadline is
cooperative; Tesseract subprocess deadlines are enforced. Word embedded images
are not OCR'd. Ragas and cloud OCR adapters are extension opportunities, not
implemented providers. Evaluation is source-scoped retrieval/answer diagnosis,
not a replacement for end-to-end chat, action or user-authorization acceptance.

## Original Candidate Release Gates

1. Review and merge the two candidate branches without reverting unrelated work.
   Run the full framework release checks and publish a real matching version.
2. Replace the local snapshot dependency, qualify compatible Runtime, backend,
   assistant and Console artifacts, and apply forward migrations normally.
3. Select the new catalogue revision for existing TXT/JSON-only deployments;
   do not widen immutable historical source policies or offer pins silently.
4. Qualify hosted PDF, scanned PDF/image and Word uploads on staging using the
   real storage, vector and model services. Generate/review questions, inspect
   chunks, index, evaluate, refine, explicitly reindex and compare the results.
5. Verify stale edits, cancellation, denied sources, revoked delegation and
   cross-customer reads using actual authenticated sessions and exact releases.
6. Qualify selected-file promotion with the frozen active staging processing
   profile, then production activation/readiness and real answer/evaluation.

No host resizing, new paid OCR service, automatic publication or vector copying
is part of this candidate.

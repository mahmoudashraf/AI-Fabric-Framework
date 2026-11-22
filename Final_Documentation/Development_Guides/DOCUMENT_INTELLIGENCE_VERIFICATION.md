# Document Intelligence Local Verification

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

## Release Gates

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

# 010.72 AI Led Document Preparation And RAG Evaluation

Owner request: 22 November 2026. Status: framework 0.8.63 published; compatible
Platform/Console/assistant deployed to staging and production. Real upload,
AI preparation/refinement, indexing and evaluation are live-qualified on Acme
staging, Harbor and actual production Support. Exact release evidence belongs in DOCUMENT_INTELLIGENCE_VERIFICATION.md
and CODEX_WORKING_CONTEXT.md, not in this design status alone.

## Outcome

A customer adds PDF, scanned PDF/image or Word documents, inspects extraction,
prepares an appropriate chunking profile with AI, reviews chunks, explicitly
indexes the candidate and evaluates actual retrieval and answer quality.
Evaluation findings can be supplied to the same assistant for another bounded
configuration proposal. No new account, global dashboard or parallel Brain
editor is required.

## Canonical Customer Journey

Smart Brain -> Knowledge -> Documents -> select a source:

1. Prepare: extracted sample, format/OCR status, extraction warnings, current
   processing profile, AI assistance and manual Advanced settings.
2. Preview: actual chunk count, source/page attribution, bounded chunk contents
   and extraction diagnostics. Preview is not an indexing receipt.
3. Index: the existing explicit indexing operation and active/candidate version
   lifecycle. New processing settings create a new candidate source version;
   the old active version remains usable until normal replacement completes.
4. Evaluate: reviewed test questions, actual retrieved evidence and generated
   answers, per-case scores/findings, model usage and precise snapshot identity.
5. Improve: attach selected document samples and evaluation findings to the
   existing configuration assistant; review the saved candidate and repeat the
   normal preview/index/evaluate operations deliberately.

The source/task stays in the approved Brain shell with its exact target and
release. Existing storage, discover, proof and retention routes remain valid.
The workspace has one assistant, with a document-specific task, not a second
chat drawer. Customer-owned source changes and delegated staff checks reuse the
existing document boundary. Draft save is not publish/apply/index success.

## Framework Boundaries

- Add optional rich-document readers using Apache PDFBox and Apache POI. Text
  PDF extraction and per-page rendering are distinct from OCR.
- OCR is an explicit replaceable interface. The initial local Tesseract adapter
  uses a configured executable, no shell, no document-supplied flags/paths,
  bounded pixels/pages/time/output and temporary-file cleanup. No cloud OCR
  provider receives documents implicitly.
- Support PDF, DOCX, legacy DOC and PNG/JPEG/TIFF inputs, while retaining the
  existing text/JSON readers. Reject encrypted/malformed/oversized inputs with
  explicit diagnostics; do not report empty extraction as successful indexing.
- Reuse Spring AI token splitting, with explicit minimum-length and overlap
  options and bounded custom transformer support. Stable source/version/chunk
  identities and trusted metadata remain authoritative.
- Add a provider-neutral RAG evaluation SPI and a Spring AI Evaluator adapter.
  Runtime's default judge uses structured model output and existing provider
  accounting. Do not adopt phrase-based YES/NO answer matching. Another tool,
  including Ragas, can implement the same sample/result contract later without
  changing customer UI or persisted run shape.

## Runtime And Data

- Per-source preparation profile, evaluation options and reviewed questions live
  with document operational state in the existing runtime PostgreSQL database.
- Evaluation runs retain source/version/release/profile identity, questions,
  bounded retrieved evidence, answers, scores, findings and real usage receipts.
  They are customer-scoped records, not a new vector or business database.
- Original files remain in the configured private S3-compatible storage. Chunks
  and embeddings use the selected vector service through existing indexing.
- Preparation profiles are revision-checked. Saving a profile cannot alter
  credentials, ownership, audience, embedding identity or document approval.
- Evaluation uses authorized, active sources and an answer-only path: no action
  registry, business writes, tool dispatch or ordinary chat continuation.
- Model-generated questions/reference answers are labelled synthetic until
  customer reviewed. Report source grounding, relevance and retrieval evidence
  separately; an LLM judge score is not an objective correctness guarantee.
- Finite sample/call/context/output/time budgets and cancellation are visible.
  Opening a page never starts inference. No provider keys enter browser or AI
  context. Evaluation failure preserves completed cases and paid usage evidence.
- A saved profile requiring re-indexing must not be evaluated as if already
  active. Actual source and release snapshot are shown on every result.

## Platform And Assistant

- Extend the existing authenticated document proxy, storage upload allowlist,
  file limits and audit, rather than introducing an unowned parser endpoint.
- Add a DOCUMENT_SOURCE resource handler to the existing assistant. Only the
  source preparation profile, evaluation options and questions are editable.
  The handler resolves ownership from the exact Brain/target/release/source,
  and checks fresh revision before save/undo.
- The explicit Optimize with AI entry attaches a bounded extracted sample and
  supplied results to the existing assistant; source attachment alone does not
  call a model. The page-aware Prepare command supplies the task automatically.
- Evaluate & optimize with AI is an explicitly requested bounded tool sequence:
  run the saved questions against the active source, wait for the real receipt,
  attach the result and sample, then request one optimization turn through the
  existing validation-feedback loop. It cannot auto-index its changes. Stop
  cancels the evaluation, including cancellation before the start response arrives.
- Retrieval/provider tuning continues in its canonical Search/Responses/Models
  editors. The document assistant can recommend those changes and supply the
  evaluation evidence, but cannot silently alter the live Brain or its embedding
  space. Document preparation and model changes are different operations.

## Acceptance

- Real text and scanned PDF, DOCX, legacy DOC and image fixtures parse, retain
  attribution and produce actual chunk plans; malformed/encrypted/zip-bomb and
  bounded OCR failures remain explicit.
- Chunk-size, overlap, minimum-length and profile-revision tests exercise real
  splitting. Modified profiles preserve active data and create a candidate.
- Ownership, target/release mismatch, stale save, revoked delegation and denied
  source paths remain blocked before extraction/inference.
- Evaluation adapters are contract tested, including replacement, partial
  failure, invalid scores and evidence preservation. Actual retrieval must be
  distinguishable from fixture-only evaluator checks.
- Assistant preparation and evaluation-driven refinement retain typed findings,
  supported configuration, usage and source references without authority growth.
- Desktop/phone browser tests cover upload, preparation, preview, save failure,
  AI context attachment, evaluation/review, cancellation and retained navigation.
- Production deployment, cloud OCR, paid capacity changes and a broad fleet
  upgrade are not implicit in this implementation. Coordinate any later rollout
  with the active owner-approved release and qualify exact artifacts normally.

## Implemented Limits And Storage

- Hosted uploads: PDF, DOCX, DOC, PNG, JPEG, TIFF, TXT and JSON, up to 20 MiB.
  Upload completion means the original file is stored, not that parsing passed.
- Runtime extraction: inherits the canonical indexing document-count ceiling
  (currently 20 pages/sections, with an absolute preparation cap of 50),
  16 million pixels per image,
  250,000 extracted characters by default, and a 60-second cooperative parsing
  allowance. Local OCR subprocesses obey the remaining deadline. English is
  installed in the serving image; other language models need operator setup.
- Processing: chunk target 100..800 tokens, overlap up to half a chunk,
  minimum chunk/indexed text lengths, OCR mode/language/resolution. Existing
  sources retain 800 tokens and no overlap until explicitly changed.
- Evaluations: 1..20 saved questions, 1..10 excerpts/question, 1,000..16,000
  context characters, threshold 0..1 and answer ceiling 128..1,500 tokens.
  Default structured judge uses one answer and one judge call per question.
  With the optional provider-budget ledger enabled, its admission/interruption
  checks gate actual provider requests. Other deployments use their configured
  provider credentials and record calls/usage as DEPLOYMENT_PROVIDER accounting.
  A funded denial never falls back to unguarded credentials.
- At most two evaluation workers and two queued jobs per runtime; 120-second
  run deadline, explicit cancel, partial case preservation and restart recovery
  to INTERRUPTED. Starts use the existing durable idempotency service. Failed
  or missing scores are not turned into zero. Evidence retention follows the
  configured document evidence retention (30 days by default).
- Runtime PostgreSQL stores source preparation, the actual indexed profile on
  each manifest, test plans and evaluation receipts. Original files stay in
  private source storage; vectors stay in the selected search service.
- Hosted promotion freezes the active staging processing profile with the
  selected file identities. Fresh production registration applies that profile
  before preview/index. Activation verifies the production active profile.
  No vectors are copied and staging-only files are not silently selected.

## Rollout And Honest Qualification

Implementation branches are `codex/document-processing` in the public framework
repository and `codex/document-intelligence` in the private Platform worktree.
Framework 0.8.63 is published to Maven Central from
`2888f0549b34dec97f6b5d3b37afa72387f1a5b4`; the release workflow, downloaded
artifact hashes and empty-cache consumer build passed. Do not republish/retag.
Compatible Platform/Console/shared-assistant images use `4a3a56e8ff07d55c168c1668e61e6ad2616fc9a4`.
The corrected ordinary runtime uses `c8b1ef457066b3e00b4fdfe226cec8747909e2dd`
and passed all 843 runtime tests. These are different serving roles, not a
reason to upgrade an unrelated stopped resource.

Backend migrations V242/V243 add the longer scoped assistant resource identity
and additive rich-document catalogue/template revisions. Published historical
versions, existing offer pins, source approval and installed customer resources
are not rewritten. New catalogue revisions must be selected into a current
deployment draft and normally published/applied; an old TXT/JSON-only policy
does not silently become a broader format policy. Runtime V25 adds preparation
and evaluation persistence, with database-enforced source ownership.

Local checks cover real PDF/Word parsing, real Tesseract image/scanned-PDF OCR,
chunking, current ownership, stale revisions, source lifecycle, paid evaluation
scope, evaluation failures/cancellation, PostgreSQL migrations and catalogue
immutability. Browser tests exercise the actual React components and shared
assistant transport with explicit API fixtures; those are not live-model or
production quality evidence. See the implementation guide for commands.

Real staging and production qualification exercised hosted S3 upload, all four PDF/DOCX/
PNG/scanned-PDF formats, real AI preparation, source-scoped paid evaluation,
evaluation-to-AI refinement, repeat evaluation, cancellation, stale409 and an
outsider404. Harbor's real Engage chat retrieved a newly uploaded Word source;
production Console Support answered from the real support guides. The obsolete
support indexing limits and ambiguous OCR descriptions were corrected and their
old indexed copies retired. Generated references remained unreviewed. One wrong
synthetic reference and misleading judge scores are preserved in the evidence;
inspect actual answers, not only completion status or scores.

Not claimed: Ragas integration, cloud OCR, handwritten-text accuracy, OCR inside
Word images, perfect PDF reading order/table reconstruction, arbitrary Java
parser hard termination, universal real-provider quality improvement or live
selected-file promotion qualification. A synthetic reference and an LLM score
remain diagnostic evidence. Existing broad backend fixtures and Incident Room
CI failures remain separate from the passing focused release suites.

## Follow-Up: Hybrid Search And Bulk Knowledge Operations

[010.73 Hybrid retrieval, optional reranking and AI-led knowledge operations](010_73_HYBRID_RETRIEVAL_RERANKING_AND_AI_LED_KNOWLEDGE_OPERATIONS_PLAN.md)
extends this work with genuine lexical/vector retrieval for documents and
entities, comparable evaluation, customer settings, AI-assisted optimization,
and a reviewed bulk discovery-to-indexing journey for existing S3 files and
Console uploads. It includes future deployment and live qualification gates.
The owner approved implementation on 23 November. Its 0.8.67 software slice was
deployed and verified on both environments on 24 November, preserving production
search defaults and leaving paid reranking off. The broader 010.73 plan remains
incomplete, including Ragas calibration, mixed/entity qualification, Milvus
lifecycle and bulk coordination. This cross-reference is not completion evidence
for those packages or a retroactive change to the historical limits above.

## Primary Library References

- PDFBox extraction/rendering: https://pdfbox.apache.org/3.0/getting-started.html
- POI Word components: https://poi.apache.org/components/
- Tesseract command line: https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html
- Spring AI evaluation contracts: https://docs.spring.io/spring-ai/reference/api/testing.html
- Ragas metric definitions: https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/

Scope note: these references informed integration choices. Library availability
is not proof of our implementation, model quality or hosted qualification.

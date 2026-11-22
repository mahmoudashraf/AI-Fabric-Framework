# Document Intelligence Implementation

Read the existing LLM philosophies and PLATFORM_UI_PROFILES.md first. This is
the 010.72 implementation guide, not evidence of a deployed framework version.

## Customer Journey

Use Smart Brain -> Knowledge -> Documents -> select a registered source. Keep
storage and source discovery in their existing homes. The selected document has
Prepare, Index and Evaluate views. Never create a second Brain selector or AI
drawer. The shared Build with AI panel accepts DOCUMENT_SOURCE context containing
the exact Brain, target, release and source IDs.

Optimize with AI extracts a bounded sample, attaches it with any selected run,
then uses the page-aware PREPARE command. Evaluate & optimize first calls the
owned evaluation API with a stable idempotency key, polls its real result and
supplies that result to the same assistant. No detailed user prompt is required.
AI can change processing, evaluation options and test questions only. Questions
it creates must remain reviewed:false. Save never grants source visibility,
changes provider credentials, indexes, applies a release or activates an app.

Preparation saves use the source row revision. Processing changes advance a
candidate source version and preserve the old active index. Preview must match
the current candidate; it cannot remain labelled current after a settings edit.
Indexing remains explicit and uses the existing queue/reconcile lifecycle.
The applied profile is frozen on the manifest. Hosted staging promotion freezes
that active profile, not unindexed candidate settings; production validates it.

## Ownership And Cost

Platform uses the existing target-context/delegation/source-audience checks.
Runtime verifies tenant/customer/Brain/target/release and approved storage scope.
Per-source evaluation uses both provider filters and post-filtered active-source
checks. A provider returning unrelated records cannot make them answer evidence.
The database evaluation foreign key includes source ownership. Extracted page
attribution is server-owned and cannot be supplied as caller metadata.

documents:evaluate is separate from documents:read and chat:query. With the
optional provider-budget ledger enabled, it opens an existing funded interactive
turn and preserves admission/interruption checks. Other deployments use their
configured provider credentials, as existing chat does; evaluation still records
model calls and usage and labels its accounting mode. A funded denial never
falls back to unguarded credentials. No business actions are executed. A run has finite work,
context, output and time allowances. Paid usage returning after cancellation is
still recorded; cancellation never becomes a successful score. Restarted orphan
runs are marked INTERRUPTED rather than silently rerun.

Originals remain in private S3-compatible storage. Runtime PostgreSQL stores
source profiles and evaluation cases, evidence, answers, metrics and receipts.
Vectors stay in the selected vector service. Evaluation retention follows the
configured document evidence retention. UI test artifacts contain fixtures only.

## Extension Points

The optional framework ai-fabric-document-readers module uses PDFBox/POI and an
OcrEngine SPI. Tesseract is server-installed; never accept document-supplied
executables/flags. The framework README describes parser limits and the
cooperative Java deadline. The serving Dockerfile installs English OCR.
The runtime inherits the canonical indexing document-count ceiling (currently
20) for both page extraction and planning, capped at 50. It does not request a
larger planning allowance than the server permits. Typed reader/limit failures
are returned as actionable document errors, not a generic server failure.

RagEvaluationProvider is the evaluator SPI. The default runtime judge returns
structured numeric faithfulness, relevance and reference-agreement metrics.
SpringAiRagEvaluationAdapter and the existing Spring AI facade's asProvider()
bridge support configured Evaluators. Register a reviewed provider bean and set
loomai.documents.evaluation-provider to select another implementation. Its own
model/tool calls must preserve funding, cancellation and finite work limits.
Do not add phrase-based answer replacement or treat scores as authorization.

Search/model tuning retains its canonical Brain editors. Evaluation threshold,
retrieval count and context limits here describe the test, not an implicit global
search change. A model-generated expected answer is not independent ground truth.

## Local Verification

Public framework worktree:

```sh
mvn -B -f ai-infrastructure-module/pom.xml -pl ai-fabric-document-readers,ai-fabric-indexing,ai-fabric-rag install
```

Real OCR qualification uses ai-fabric-document-readers/qualification.Dockerfile
and TesseractQualification; the framework CI runs the same generated image and
scanned-PDF checks. No production files or credentials are required.

Private Platform worktree, using the matching released framework dependencies:

```sh
mvn -B -f ai-infrastructure-module/pom.xml -pl ai-fabric-runtime -am '-Dtest=Document*Test,RuntimeBudgetTurnScopesTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -f Platfrom/backend/pom.xml '-Dtest=DocumentSourceAuthoringTest,DeploymentDocumentKnowledgeOperationsServiceTest,DocumentStorage*Test,CustomerDocumentStorageServiceTest,AwsCustomerDocumentStorageProviderTest,MarketplaceManifestServiceTest,RichDocumentCataloguePostgresTest' test
```

From Platfrom/ui:

```sh
npm run build
node src/components/documents/DocumentPreparationPanel.browser.mjs
npm run test:document-storage
```

For a local, interactive fixture preview, run the browser script with `--serve`.
It prints its localhost URL and serves the real components with in-memory API
and model fixtures. It is not connected to a customer Brain or provider account.
Stop it with Ctrl+C. Normal runs close all task-owned browser/server resources.

Build the sibling max-mode-widget dependencies first for a fresh checkout. The
browser script tests actual UI components with API fixtures; it is deliberately
not live-model evidence. PostgreSQL tests require local Docker.

## Release Qualification

Publish/qualify the framework release first, replace the local snapshot pin,
then build compatible Runtime, Platform backend/assistant and Console images.
Apply forward migrations and use normal deployment/version operations. Select
the new document catalogue revision explicitly for old TXT/JSON-only releases.
Do not mutate historical manifests or resize infrastructure as a side effect.

Before calling production closed, qualify a real hosted upload -> AI settings
and questions -> preview -> active index -> paid retrieval/answer/evaluation ->
AI refinement -> explicit reindex -> repeat evaluation, then hosted production
promotion and cross-customer negative checks on the exact released images.

See `DOCUMENT_INTELLIGENCE_VERIFICATION.md` for the local candidate evidence and
the remaining release gates. Do not describe fixture responses as AI quality.

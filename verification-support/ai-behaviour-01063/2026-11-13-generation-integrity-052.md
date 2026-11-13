# Generation Integrity - AI Fabric 0.8.52

## Approved Scope

The owner approved the bounded generic-generation correction on 13 November
2026. Framework source is commit `3bfcd27219706da4b1e82c4b71ca9c0882d1019d`,
tag `ai-fabric-framework-v0.8.52`. Publication, public consumption and hosted
adoption are separate gates; a source install is not a live release.

- Failed or empty information generation returns `GENERATION_FAILED`; authorized
  READ receipts and retrieved documents remain available as evidence, not an
  invented successful answer.
- Generic post-action narration no longer substitutes a deterministic summary
  or suppresses the actual handler message by generic-phrase matching.
- Empty RAG generation does not silently issue a second, text-only request with
  fewer attachments, budget settings or context.
- An already successful action retains its success, execution identity and
  receipt when narration fails. It is never replayed to repair wording.
- Safe `metadata.postActionGeneration` distinguishes narration from execution.
  Provider exception text is not copied into this public status.

Relationship-specific deterministic behavior remains deferred. No prompt,
permission, confirmation, vector, database or retrieval-source change is part of
this patch. Explicit retrieval-only/disabled-generation behavior is retained.

## Platform And Widget Adoption

The runtime adopts the 0.8.52 BOM and capability manifest. Canonical response
mapping does not invent processed-success prose for a known successful action
with failed narration and no handler text. Result-aware optional suggestions
also stop for that typed failure instead of making another generation call.

The widget preserves the actual message and receipt and shows a separate
status: `Action completed. The AI explanation is unavailable.` This is an
execution-status notice, not a replacement AI answer. Only the explicit safe
status subset is projected; rendering does not request or replay an action.

The notice is qualified for direct, current-turn `ACTION_EXECUTED` responses.
Canonical history does not restore this metadata, and compound-child notices
are not implemented. Existing unrelated missing-message defaults are not
claimed removed. These limits do not change recorded execution success.

## Verification Before Publication

- Java 21 release-profile reactor: all 30 modules passed. After the final narrow
  sanitizer correction, all 1,096 core and 14 default-profile tests passed again.
- Current framework reports: 2,471 tests, zero failures, errors or skips. Counts
  overlap focused runs and are not added together.
- Focused failure injection: provider exception, null/blank generation,
  budgets/attachments, retained READ/RAG evidence, handler text preservation,
  exactly-once write execution, authorization and confirmation.
- Real OpenAI GPT-5.4-mini native schema: 10/10 scenarios, 15 entry calls. No
  failed model case was rephrased or replayed to obtain acceptance.
- OpenAI/ONNX/Lucene matrix: 11/11 available considered tests. The harness clamps
  its requested 20-test minimum to those 11; this is not all-provider proof.
- Docker vector contracts: 8/8 across Qdrant REST/gRPC, Weaviate and Milvus.
- Standalone agentic consumer: 5 tests passed; minimal consumer compiled; MCP
  operations/reference and privacy-first support consumer build passed.
- Gated integration suites compile. All seven release guards and the coding
  assistant skill validator passed on the final source.
- Main runtime final full run: 817 tests passed with zero failures, errors or
  skips. The earlier two stale expected-version assertions were corrected and
  their eight-test targeted rerun also passed. Final image CI repeats the full
  runtime run independently against published dependencies.
- Widget status/narrative, confirmation replay, suggestions/races/lifecycle,
  TypeScript and production build passed. Status tests include safe projection,
  unchanged receipt, zero replay and the explicit history/compound limits.

## Hosted Rollout Gates

Central publication run `43384836398` passed; the immutable 0.8.52 artifacts
are publicly available. A new consumer using an empty dependency cache and a
Central-only mirror passed all five tests, packaged and booted on Java 21.
Its three nested framework JARs match the downloaded Central bytes and record
remote provenance rather than local installs. Sources and Javadoc returned 200.
Central's reactor skipped two Docker-API-gated PostgreSQL lease tests; both ran
locally in the zero-skip qualification. This is not a claim of zero Central skips.
Fresh broad framework CI run `43384836176` passed the release guards, framework
reactor, integration compilation, minimal/agentic consumers and vector-container
job. Its real-app suite stopped at the same 14 Incident Room failures present
before this patch; exact failed-method comparison is retained privately. Later
suite modules/smokes were not run by that failed job. Separate MCP/privacy
consumer checks above are not a substitute claim for a completely green broad
CI run. No test guard was weakened or failure silently retried.
After the released commit was fast-forwarded to framework `main`, automatic run
`43389389264` repeated the same 14 failure methods with no new failures; guards
and the vector job passed again.

Before applying to Northfield, an exact read-only comparison normalized the
published behaviour expansion back to its recorded editable inputs. It matches
the current draft with no differences. Consultative / Advisory / Balanced /
Contextual, the model, sources, action contracts and existing app identities
must remain unchanged. Upgrade staging, inspect the actual packaged/runtime
identity and live answers, then upgrade production through the normal release
and assignment APIs. Do not copy vectors or replace business data.

Raw private evidence and deployment receipts are held outside Git at
`~/.codex/private/loomai-framework-0.8.52/`. Final publication, image, release,
binding and live verification receipts will be appended after they occur.

## Platform Images And Staging Proof

Adoption source `0089818162f8805bc58578dc20149dec9a93d237` is committed/pushed.
Runtime CI `43387413438` passed, including all 817 runtime tests without skips.
Core-image CI `43387413548` passed. Both Console backends and UIs are healthy
on that exact source and the CI-published digests:

- Runtime: `sha256:fdba473452bf2e1ca681ea0bb30e6291e3b3844c59d22849a9704597cbd62910`.
- Backend: `sha256:1bd76ce47dcbbe8e935634c019aa928004640cb84c059896790fbb328897c797`.
- UI: `sha256:98d28353d94fffaf4f2638eba1388b9c26656974bc1b68ccd385f5060a3ff501`.

Northfield's same Brain `dep-3c4dbe4f` published version `ver-0458c7ed` with
framework 0.8.52 and its unchanged editable configuration. Staging release
`rel-6c13942c` is `APPLIED_VERIFIED`/`PASSED`; its existing consumer and standalone
installation were rebound normally without changing their identity or URL.
Native runtime/connector image identity and health passed. The running runtime's
core JAR matches the public Central bytes, with no mixed framework versions.

Four original staging queries returned successful answers and retained their
READ/RAG evidence: stock, mileage/advice follow-up, sparse vehicle and indexed
test-drive policy. Correct mileage (1,980/4,850), the E2 unknown-field boundary
and licence/30-minute policy were represented. The follow-up unnecessarily
introduced hybrid alternatives, and the sparse answer imprecisely called indexed
evidence live. These are retained semantic limitations, not fixed by this patch.

The original quality report remains `STOPPED_NOT_ACCEPTED`: its network guard
rejected two optional Supabase visit-counter requests before manifest load.
All four model responses completed, with no page errors or business writes.
Separate adjudication qualified those original exchanges for this bounded patch;
no model query was replayed. Future runs explicitly suppress this known telemetry
so synthetic quality visits do not inflate analytics. Other POST/authority/write
restrictions are unchanged. This is a checker correction, not an AI fallback.

Hosted UI contract replay separately passed two narration-failure cases: blank
handler text and preserved handler text. Receipt/status, the separate warning,
no confirmation or automatic replay, one request per case, desktop/mobile and zero page errors
passed. Screenshots were inspected. Both responses were simulated; no provider
was disrupted and no real business write was submitted by this replay.

Production release `rel-0139e718` of the same version/image is now
`APPLIED_VERIFIED`/`PASSED`. Its existing consumer/standalone app was rebound
normally. Native runtime/connector health, exact image/source, all-framework-52
libraries and the public Central core-byte comparison passed on production too.
Cold replacement used the existing workflow; its connector restart after
bootstrap-credential removal contributes to rollout duration. No drain/capacity/
recovery mechanism was added or bypassed.

## Final Hosted Qualification

The same four production questions completed with successful transport and
generation; that is **not** a claim of four semantically correct answers. Stock,
sparse E2 fields and the indexed test-drive policy were useful and grounded.
The mileage follow-up again called the 216/287-mile electric ranges mileage.
It executed no new READ and retrieved no new documents, despite available
odometer facts. This is the previously observed normal planning/evidence gap,
not a provider-generation failure or answer-substitution path. It remains open;
the answer was not rewritten, rephrased or retried. Later guidance may require
the model to fetch missing comparison facts or admit their absence; no lexical
detector, forced search-all or new prompt change was added here.

Production's corrected runner completed with no blocked business requests or
page errors, explicitly suppressing two analytics events. Both narration-status
UI replay cases passed on production, and desktop/mobile screenshots were
inspected. Across both targets: eight real questions, four simulated UI cases,
zero model-query replays and zero successful business writes. Provider failure
injection is covered by deterministic tests, not falsely claimed as a live
production outage test.

Post-rollout comparison confirms all ten editable draft sections are unchanged.
Runtime/connector application identities, the AI-state store, runtime origins,
workspace configuration and experience-pack integrity are preserved on both
targets. The same three original policy sources remain active. No new customer
database, app, vector copy or source-object replacement was introduced.

The approved bounded patch is implemented, published, adopted and live-verified.
This is not complete 010.63 quality/defaults closure. Relationship behavior,
history/compound warning presentation and unrelated legacy UI defaults remain
outside the qualified scope; see the separate outstanding work below.

## Separate Outstanding Work

Richer approved dealership policies still await an authorized scoped uploader.
No S3 object, IAM policy, reader credential or template default was changed by
this patch. Published template candidates remain unqualified. Known model
mileage/range and sparse-record attribution errors are not claimed fixed by
removing failure-path answer substitutions.

# Page-Aware, Document-First Configuration Assistant

Date: 10 November 2026. Owner-approved extension of 010.37 section 19.

## Status

Implemented, committed, pushed and qualified on staging and production. Both
environments run the exact backend/UI sources and OCI digests below. The shared
private inference service and all customer chat runtimes remain unchanged.

## Scope

- One existing assistant, task adapters, source importer and turn lifecycle.
- Page-specific product guidance and preparation commands with optional hints.
- Typed active editor focus, selected definitions and explicit document reuse.
- No keyword-based intent classification, canned configuration or hidden retry.
- Draft saves remain distinct from validation, publication and live execution.
- No framework/runtime release, new database, or customer-runtime rollout.

## Backend Evidence

Source `a6fff57ff31a3444d967d597420bd88664e29aaa` adds `PREPARE` and per-turn
`editorContext`. Existing ordinary query semantics remain unchanged. The section
must match the session's task; selected IDs are bounded guidance, not authority.
Preparation hints may be empty and are retained separately from registered
intent. The effective request uses existing encrypted session persistence,
idempotency, accounting, bounded feedback, final save, cancel and undo paths.

Focused assistant suite: 255 tests passed initially; the subsequent HTTP/Jackson
contract test and integration rerun also passed. Final aggregate is 256 tests,
zero failures/errors/skips across the same focused suites. This is not a claim
of Platform-wide or framework-wide release-matrix verification.

Read-only independent review found no significant issues. Jackson request
round-trip is tested, alongside all registered sections, no-message preparation,
unchanged ordinary prose, task mismatch before inference, selected documents,
same-task section changes, idempotent retry and preservation of unrelated data.

## UI And Hosted Evidence

UI source `ee76af6775f4195465f95e5cfc684a43c3e5425d` is committed/pushed.
Local qualification passed:

- 57 UI unit/transport/route/presentation tests.
- 42 authenticated browser-fixture scenario groups.
- 13 actual Support compatibility scenario groups.
- Full production UI build and `git diff --check`.

The scenarios cover file/paste/URL preparation with no hint, optional hints and
operation selection; explicit same-resource reuse including reselection;
focused clarification follow-up; fresh model/search/editor-selection context;
guide-only/active-Engage previews; existing correction, cancellation, idempotency,
save/undo and responsive input retention. No paid request or save occurs merely
from navigation/open/import. Source fixtures do not prove external URL fetching
or real-provider semantic quality.

Desktop document-first and 390px saved-result screenshots were inspected. The
default panel has compact page guidance, selected-document chips, an explicit
draft-only notice and a preparation command. Further controls are under Turn
options. After a turn, the composer presents ordinary follow-up mode; preparation
remains available explicitly. The shared widget/runtime source is unchanged.

The Live data route `/knowledge/connections` now uses KNOWLEDGE/LIVE_DATA,
matching its actual source/indexing editor. The action connection view still
uses CONNECTIONS/routing. This corrects the previous page-to-task mismatch;
neither task receives additional write paths or permissions.

## Hosted Qualification

Both staging and production native containers are healthy with exact identity:

- Backend source `a6fff57ff31a3444d967d597420bd88664e29aaa`, digest
  `sha256:f8be8a48f92aaa7e51b182651edbf69c667ec09215f2ac4a03628687ff8311b7`.
- Console source `ee76af6775f4195465f95e5cfc684a43c3e5425d`, digest
  `sha256:ede86407033c7c41b94d4fc97d834b837938d84976e9bfd3bf01c1c26ad8d889`.
- Core image workflows `42777341009` and `42778787205` passed. These are image
  publication checks plus the local suites above, not a full-platform matrix.
- Private authoring remains source `546da001a0586a087ea5ea96289f6ddc41571cb8`,
  digest `sha256:3ba106bb0e17b434a272ccc7c194c6c677bad7f112355b5b9073cfaa51b4bff0`.
  Its existing allocation/store identity, limits and expiry survived unchanged.

Normal authenticated customer UI runs passed:

- `page-aware-staging-1794297683603`, 08:01:24-08:01:53 UTC.
- `page-aware-production-1794297947480`, 08:05:48-08:06:15 UTC.

Each run uploaded the fictional dealership API file, left the hint empty and
clicked **Prepare actions**. One real provider attempt prepared three READ
actions and all three documented routes, with zero configuration errors and
unchanged unrelated configuration. The production run additionally waited for
editor refresh and confirmed the actual **Connected (3)** list before its
screenshot. The initial staging saved screenshot captured the brief refresh
phase, not the final editor state; do not use it to claim an empty saved editor.

The short ordinary follow-up "Rename the stock search to Find current stock."
saved the requested display name while preserving routing. Actual UI Undo
restored the saved action configuration. Each environment also verified explicit
same-Brain document reuse, pasted text, a real public raw-GitHub documentation
URL, model/search guidance changes, no extra submitted turns from these
operations, 390px width without document overflow and zero browser errors.
Production desktop and responsive screenshots were inspected.

The fresh configuration-only fixture retained two existing warnings: no entity
types and no remote authorization base URL. READ actions do not require vectors;
no runtime was deployed or claimed ready. These warnings were neither hidden
nor weakened and did not cause additional model calls. This extension does not
claim live execution of the documented fictional business API. Multi-attempt
correction, cancellation and preview boundaries are covered by the controlled
suites and the earlier hosted-loop qualification; both new live preparations
passed on their first attempt.

Four exact assistant sessions were deleted and verified 404; two configuration-
only Brains were archived. Fixture browser accounts signed out. No publication,
provisioning, indexing, assignment or business execution occurred; no customer
infrastructure, documents or live app assignment was changed.

## Actual Usage

Read-only native authoring readbacks before/after these two runs showed:

- Staging: 26 -> 28 provider attempts; 154,240 -> 172,438 tokens;
  USD 0.147258750 -> 0.166033500.
- Production: 13 -> 15 provider attempts; 109,828 -> 127,701 tokens;
  USD 0.106176000 -> 0.124043250.
- Increment: four actual attempts, 36,071 reported tokens, USD 0.036642000.

The four attempts match the four explicitly submitted turns. No additional paid
generation was observed for page opening, navigation, document import or reuse.
No budget initialization, activation, history reset or allowance increase was
performed. Per-host limits remain USD 5 / 100 attempts / 2 million tokens, expiry
10 December 2026 at 01:45:27.124 UTC. The existing ledger remains financial truth.

Private operational scripts, raw receipts and screenshots remain under
`~/.codex/private/loomai-feedback-loop-20261110/`. Public source evidence must not
include credentials or session tokens.

# Dealership Evidence Completeness - 2026-11-09

Scope: implement 010.63 section 6.2 evidence improvements only. No suggestion,
prompt, framework, account-ownership or customer UI redesign changes.

## Execution

- [x] Confirm current Northfield releases through the production control plane:
  staging `rel-c7415db7`, production `rel-b329b925`, version `ver-5557856a`;
  both report `APPLIED_VERIFIED` / `PASSED`.
- [x] Trace authoritative simulator stock fields through indexing, read actions
  and presentation; preserve genuinely unknown values.
- [x] Inspect active policy registrations: exactly three original short sources
  per target, all active version 1. No competing six-file fixture registration.
- [ ] Inspect stored objects and back up existing
  objects privately before replacing an unversioned object.
- [ ] Qualify the single fictional Northfield policy pack on staging, then
  production, using normal source refresh/index reconciliation.
- [x] Run focused tests and live evidence checks. Record failures honestly.
- [x] Update canonical implementation/context records with actual outcomes.

## Boundaries

- Independent website seed data is not an AI evidence source.
- Simulator facts are fictional, not Auto Trader sandbox or Paul Rigby facts.
- Do not invent an official provider field or infer an absent feature.
- Keep trust, tenant, advertiser and source ownership checks intact.
- Do not copy vector records between environments, wipe collections, patch
  indexing tables directly, or add semantic text matching.
- Retire superseded evidence only after its replacement is active.
- Runtime storage credentials remain read-only. Missing upload permission is
  reported rather than bypassed with a broader runtime identity.

Private credentials, object backups, raw API receipts and query transcripts
remain outside Git under `~/.codex/private/loomai-evidence-20261109/`.

## Findings And Local Implementation

- Added optional simulator-authored seats, numeric range in miles, equipment,
  advertiser town and forecourt date. One vehicle deliberately has no optional
  evidence. No field is copied from website seed data.
- The checked public provider contract does not define a range test-basis field.
  Neither simulator nor UI invents WLTP/NEDC certification.
- Added immutable DATA/READ/template revisions through migration 239. Existing
  installations, releases, permissions and historical manifests remain untouched
  until the normal installation update/publish/apply workflow is used.
- Fixed generic connector array loss in trusted target content and null values
  represented as literal searchable facts. Structured fields remain structured;
  no domain-specific semantic normalizer or text matching is introduced.
- Focused PostgreSQL migration test passed, including historical row preservation,
  native manifest validation and a repeat-migration check.
- The first browser baseline completed both queries but its broad transport guard
  rejected the site's existing visit analytics requests. That runner result is
  **not** an accepted UI gate. Retained exchanges separately show missing vehicle
  fields and no retrieved delivery policy evidence.

## External Prerequisite

The original local staging S3 reader key returns `InvalidAccessKeyId`. This does
not mean the live connector is unavailable: its scoped source preview succeeds.
The separate managed-storage provisioner authenticates but has no read access to
this existing canary bucket. Required for production confirmed that no approved
current uploader is recorded. Owner upload or a scoped uploader is requested;
runtime read permissions will not be broadened or substituted across buckets.

The prepared three-file fictional policy pack remains pending upload and indexing.
Do not report policy qualification complete from source registrations alone.

## Hosted Inventory Qualification

- Source `49024a2bed8b2dada4f04be73d80cf432454c6fa` is committed/pushed.
  Core-image CI `42679578954`, runtime CI `42679579259`, dependency CI
  `42679578987` and serving-image CI `42679579392` passed.
- Both control-plane backends run migration `239:true`. The Console frontend
  and public-site layouts were not redeployed or redesigned by this work.
- Simulator image is healthy at the exact source. Preserved its v2 fixture
  override, H2 volume and reset identity. Six named records received reviewed
  fictional evidence; ten other records and both other accounts were preserved.
- Northfield version `ver-689ff79c` retains the previous prompts, provider,
  identity/security, shell and behavior settings. Plugin recomposition reset two
  previously tuned enquiry descriptions and one confirmation message; restored
  those exact prior definitions through the ordinary draft API before publishing.
- Staging release `rel-5846d684` is `APPLIED_VERIFIED` / `PASSED`. Updated its
  existing Engage assignment, preserving the installation and app address.
- Automatic source synchronization completed six normalized/indexed records:
  six accepted/completed work items, zero failures. An optional manual reconcile
  through the operator API-key caller returned 403; no guard was bypassed. This
  is not a customer UI qualification claim for that control. Normal scheduled
  synchronization and live READs succeeded.
- Three live, same-conversation queries: E1 facts **pass**, E1/C2 comparison
  **partial**, sparse E2 evidence **pass**. Correct source facts include E1 five
  seats / 287 miles and C2 four seats / 216 miles. The comparison unnecessarily
  questioned whether the roof was optional despite its supplied `Optional` type.
  No response filtering or retry concealed that imperfection.
- Real staging UI search -> **View details** -> detail presentation passed, with
  conversation continuity, supplied equipment types, range in miles, explicitly
  unknown range test basis, town and timestamps. Desktop/mobile checks passed,
  with no captured page errors or business writes. The first UI harness read the
  shadow host rather than its `.workspace`; its false assertion is retained and
  the corrected run is separately recorded.
- An additional live indexed-evidence query used RAG with **zero actions**. Its
  returned `dealer-vehicle` document for `DEMO-1001` contains seats, range,
  structured equipment types, advertiser town and source timestamps under the
  new authorized dataset handle. The answer correctly reported five seats,
  287 miles, both equipment types and an unknown range test basis. This proves
  vector retrieval, not merely successful job submission or cached READ data.

## Production Closeout

- Production release `rel-1cf5f756`, version `ver-689ff79c`, is
  `APPLIED_VERIFIED` / `PASSED`; existing Engage assignment and address retained.
- Both target runtimes/connectors, both backends and the simulator are healthy at
  source `49024a2bed8b2dada4f04be73d80cf432454c6fa`. Framework remains **0.8.46**.
  Native runtime digest:
  `sha256:388fba71e895078795b7c39da578ab20412cafea9e75a53a79fbf17d89d23419`.
- Production independently synchronized/indexed six records, with six completed
  work items and zero failures. No staging vector records were copied.
- Production E1 facts, E1/C2 comparison and indexed-only query passed their
  manual evidence review. The indexed query executed zero actions and returned
  the actual enriched vector documents.
- **Production follow-up failure retained:** the E2 answer incorrectly claimed
  `Fictional Testhaven` as its location. Its READ result contains an E1 record
  with that location and an E2 record without it; the model borrowed a fact from
  another authorized record. This is a generation/record-attribution error, not
  a missing mapping or a contaminated E2 source record. No answer rewrite,
  lexical filter, hard validator, prompt change or framework fix was added to
  conceal it. A separately approved follow-up-grounding improvement can address
  this; the infrastructure gate is not a claim of perfect semantic quality.
- Production UI search -> E1 details -> E2 details passed with three READ
  requests, continuity, correct E1 equipment/range and explicit E2 missing
  values. Its raw-data presentation does **not** borrow the E1 location.
  Desktop and mobile viewport screenshots were inspected; no captured page
  errors, horizontal page overflow or business writes occurred.
- Existing policies remain their original active version 1. Richer policy
  replacement and its policy-quality qualification are **not complete**, pending
  the scoped S3 upload prerequisite above. No customer storage, IAM policy,
  website seed, new business database, or customer UI layout was changed.

## Repeating The Checks

From the repository root, with the current private scope and a new output path:

```bash
node verification-support/ai-behaviour-01063/run-dealership-evidence.mjs \
  --scope "$SCOPE_FILE" --root "$NEW_PRIVATE_OUTPUT_DIR" \
  --only vehicle-facts,vehicle-comparison,missing-vehicle-facts,indexed-vehicle-evidence
```

It uses the live installation manifest and canonical anonymous conversation
route, preserving `executor` / `landing`. It does not tune product settings or
perform semantic lexical scoring. Its transport result explicitly requires
manual evidence review; successful HTTP responses are not quality acceptance.
Policy scenarios are ready but must not be relabelled qualified before the
approved source pack is uploaded, refreshed/indexed and reconciled.
